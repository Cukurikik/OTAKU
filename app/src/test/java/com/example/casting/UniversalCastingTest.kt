package com.example.casting

import com.example.network.server.LocalMediaServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UniversalCastingTest {

    @Test
    fun testLocalMediaServerRegistrationAndUrlGeneration() {
        val server = LocalMediaServer(8080)
        val dummyFile = File.createTempFile("test_video", ".mp4").apply {
            writeText("dummy content for mp4")
            deleteOnExit()
        }

        // Test register file
        val fileId = server.registerLocalFile(dummyFile, "video/mp4")
        assertNotNull(fileId)
        val fileUrl = server.getMediaUrl(null, fileId)
        assertTrue(fileUrl.contains(":8080/media/$fileId.mp4"))

        // Test register remote relay
        val relayId = server.registerRemoteRelay(
            "https://cdn.example.com/stream.m3u8",
            "application/x-mpegURL",
            mapOf("Referer" to "https://anime.com")
        )
        assertNotNull(relayId)
        val relayUrl = server.getMediaUrl(null, relayId)
        assertTrue(relayUrl.contains(":8080/media/$relayId.m3u8"))

        // Test unregister
        server.unregisterMedia(fileId)
    }

    @Test
    fun testLocalMediaServerCbzComicRegistration() {
        val server = LocalMediaServer(8080)
        // Buat file zip/cbz sementara
        val cbzFile = File.createTempFile("comic_test", ".cbz").apply { deleteOnExit() }
        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            zos.putNextEntry(ZipEntry("001.jpg"))
            zos.write("page1".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("002.jpg"))
            zos.write("page2".toByteArray())
            zos.closeEntry()
        }

        val cbzId = server.registerComicPage(cbzFile, 0)
        val cbzUrl = server.getMediaUrl(null, cbzId)
        assertTrue(cbzUrl.contains(":8080/media/$cbzId.jpg"))
    }

    @Test
    fun testSsdpXmlDeviceDescriptionParser() {
        val sampleXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
                <device>
                    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                    <friendlyName>[TV] Samsung QLED 65</friendlyName>
                    <manufacturer>Samsung Electronics</manufacturer>
                    <modelName>QN65Q80A</modelName>
                    <serviceList>
                        <service>
                            <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                            <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                            <controlURL>/smp_4_</controlURL>
                            <eventSubURL>/smp_5_</eventSubURL>
                            <SCPDURL>/smp_6_</SCPDURL>
                        </service>
                        <service>
                            <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                            <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                            <controlURL>/smp_7_</controlURL>
                            <eventSubURL>/smp_8_</eventSubURL>
                            <SCPDURL>/smp_9_</SCPDURL>
                        </service>
                    </serviceList>
                </device>
            </root>
        """.trimIndent()

        // Test parser
        val device = SsdpDiscoveryManager.parseDeviceXml(
            deviceId = "uuid:samsung-tv-12345",
            locationUrl = "http://192.168.1.50:7676/smp_2_",
            xmlContent = sampleXml
        )

        assertNotNull(device)
        assertEquals("[TV] Samsung QLED 65", device?.name)
        assertEquals("Samsung Electronics", device?.manufacturer)
        assertEquals("QN65Q80A", device?.modelName)
        assertEquals("http://192.168.1.50:7676/smp_7_", device?.controlUrl)
        assertEquals("http://192.168.1.50:7676/smp_4_", device?.renderingControlUrl)
    }

    @Test
    fun testDlnaDidlMetadataBuilder() {
        val didl = DlnaCastManager.buildDidlMetadata(
            mediaUrl = "http://192.168.1.10:8080/media/123.mp4",
            title = "Solo Leveling Episode 1 & Special",
            mimeType = "video/mp4"
        )

        assertNotNull(didl)
        assertTrue(didl.contains("&lt;DIDL-Lite"))
        assertTrue(didl.contains("Solo Leveling Episode 1"))
        assertTrue(didl.contains("http://192.168.1.10:8080/media/123.mp4"))
        assertTrue(didl.contains("object.item.videoItem.movie"))
        assertTrue(didl.contains("video/mp4"))
    }

    @Test
    fun testDlnaDurationFormattingAndParsing() {
        // Format
        val formatted1 = DlnaCastManager.formatDuration(3665000L) // 1h 1m 5s
        assertEquals("01:01:05", formatted1)

        val formatted2 = DlnaCastManager.formatDuration(125000L) // 0h 2m 5s
        assertEquals("00:02:05", formatted2)

        // Parse
        val parsed1 = DlnaCastManager.parseDuration("01:01:05")
        assertEquals(3665000L, parsed1)

        val parsed2 = DlnaCastManager.parseDuration("00:02:05")
        assertEquals(125000L, parsed2)

        val parsed3 = DlnaCastManager.parseDuration("02:05")
        assertEquals(125000L, parsed3)
    }
}
