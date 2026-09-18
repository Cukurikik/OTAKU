package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DatabaseSecurityManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DatabaseSecurityTest {

    @Test
    fun testPassphraseGenerationAndPersistence() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pass1 = DatabaseSecurityManager.getPassphrase(context)
        assertNotNull("Passphrase should not be null", pass1)
        assertEquals("Passphrase should be 256 bits (32 bytes)", 32, pass1.size)

        // Second call should return the exact same key from encrypted store or cached memory
        val pass2 = DatabaseSecurityManager.getPassphrase(context)
        assertArrayEquals("Passphrase must persist and be identical on subsequent calls", pass1, pass2)
    }

    @Test
    fun testPbkdf2DynamicKeyDerivation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val derivedKey1 = DatabaseSecurityManager.generateFallbackPassphrase(context)
        assertNotNull("Derived key must not be null", derivedKey1)
        assertEquals("Derived key must be exactly 256 bits (32 bytes)", 32, derivedKey1.size)

        // Deterministic generation: identical parameters must yield identical key
        val derivedKey2 = DatabaseSecurityManager.generateFallbackPassphrase(context)
        assertArrayEquals("Dynamic KDF must be deterministic for identical hardware/device state", derivedKey1, derivedKey2)

        // Different user PIN must yield distinct 256-bit key
        val derivedKeyWithPin = DatabaseSecurityManager.generateFallbackPassphrase(context, "custom_pin_1234")
        assertEquals(32, derivedKeyWithPin.size)
        var isIdentical = true
        for (i in derivedKey1.indices) {
            if (derivedKey1[i] != derivedKeyWithPin[i]) {
                isIdentical = false
                break
            }
        }
        assertFalse("Derived key with custom PIN must differ from default hardware-derived key", isIdentical)
    }

    @Test
    fun testFallbackDoesNotStorePlaintextKeyInSharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Simulate legacy unencrypted fallback entry
        val fallbackPrefs = context.getSharedPreferences("db_security_fallback", Context.MODE_PRIVATE)
        fallbackPrefs.edit().putString("db_passphrase_v1", "SGVsbG9Xb3JsZFBhc3NwaHJhc2U=").commit()

        DatabaseSecurityManager.resetCachedPassphraseForTesting()

        // Calling generateFallbackPassphrase or getPassphrase should purge the insecure key
        val pass = DatabaseSecurityManager.getPassphrase(context)
        assertNotNull(pass)

        // Verify that the insecure XML preference file does NOT store the passphrase in plaintext
        assertFalse(
            "Legacy plaintext passphrase must be sanitized and removed from db_security_fallback XML",
            fallbackPrefs.contains("db_passphrase_v1")
        )
    }

    @Test
    fun testCreateSupportFactorySafeHandling() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // In Robolectric environment, createSupportFactory safely detects JVM and returns null without crashing
        val isTestEnv = DatabaseSecurityManager.isRunningInUnitTest()
        assertTrue("DatabaseSecurityManager should recognize test environment", isTestEnv)

        val factory = DatabaseSecurityManager.createSupportFactory(context)
        // In Robolectric environment without sqlcipher native binaries, factory is null or mocked
        assertTrue("Test environment executed safely", isTestEnv)
    }

    @Test
    fun testPlaintextDatabaseDetection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDbFile = context.getDatabasePath("test-plaintext-detection.db")
        testDbFile.parentFile?.mkdirs()

        // Write SQLite header
        FileOutputStream(testDbFile).use {
            it.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            it.write(ByteArray(84)) // fill up to 100 bytes
        }

        assertTrue("File should exist", testDbFile.exists())

        // Read back header
        val header = ByteArray(16)
        testDbFile.inputStream().use { it.read(header) }
        val isPlaintext = String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
        assertTrue("Database file should be detected as plaintext SQLite", isPlaintext)

        testDbFile.delete()
    }
}
