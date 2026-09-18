import re

with open('app/src/main/java/com/example/data/SettingsManager.kt', 'r') as f:
    content = f.read()

new_props = '''
    var orientationLock: Int
        get() = prefs.getInt("orientation_lock", -1) // -1 = Unspecified, 1 = Portrait, 0 = Landscape
        set(value) = prefs.edit().putInt("orientation_lock", value).apply()

    var cropBorders: Boolean
        get() = prefs.getBoolean("crop_borders", false)
        set(value) = prefs.edit().putBoolean("crop_borders", value).apply()
'''

content = content.replace(
    'var syncEnabled: Boolean\n        get() = prefs.getBoolean("sync_enabled", false)\n        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()',
    'var syncEnabled: Boolean\n        get() = prefs.getBoolean("sync_enabled", false)\n        set(value) = prefs.edit().putBoolean("sync_enabled", value).apply()' + new_props
)

with open('app/src/main/java/com/example/data/SettingsManager.kt', 'w') as f:
    f.write(content)
