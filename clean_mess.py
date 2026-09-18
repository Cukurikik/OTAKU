import os, re

def clean_file(path):
    with open(path, 'r') as f:
        c = f.read()
    orig = c

    # Strip out the messed up arguments
    c = c.replace('profileId = com.example.data.ProfileManager.currentProfileIdValue, com.example.data.ProfileManager.currentProfileIdValue, ', 'com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('com.example.data.ProfileManager.currentProfileIdValue, com.example.data.ProfileManager.currentProfileIdValue, ', 'com.example.data.ProfileManager.currentProfileIdValue, ')
    c = c.replace('.copy(profileId = com.example.data.ProfileManager.currentProfileIdValue)', '')
    c = c.replace('profileId = com.example.data.ProfileManager.currentProfileIdValue, ', '')
    c = c.replace('com.example.data.ProfileManager.currentProfileIdValue, ', '')

    if orig != c:
        with open(path, 'w') as f:
            f.write(c)

for root, _, files in os.walk('app/src/main/java/com/example'):
    for file in files:
        if file.endswith('.kt'):
            clean_file(os.path.join(root, file))
