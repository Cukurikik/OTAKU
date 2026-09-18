with open("app/src/main/java/com/example/data/sources/WebViewFetcher.kt", "r") as f:
    lines = f.readlines()

with open("app/src/main/java/com/example/data/sources/WebViewFetcher.kt", "w") as f:
    f.writelines(lines[:214])
    f.write("}\n")

