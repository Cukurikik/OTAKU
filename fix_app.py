path = 'app/src/main/java/com/example/OtakuHubApp.kt'
with open(path, 'r') as f:
    c = f.read()
c = c.replace('lateinit var profileManager: com.example.data.ProfileManager', '')
c = c.replace('profileManager = com.example.data.ProfileManager(this)', 'com.example.data.ProfileManager.init(this)')
with open(path, 'w') as f:
    f.write(c)
