#!/bin/bash
find app/src/main/java/com/example/ui -name "*.kt" -exec sed -i 's/Icons.AutoMirrored.Filled.ArrowBack/Icons.Filled.ArrowBack/g' {} +
find app/src/main/java/com/example/ui -name "*.kt" -exec sed -i 's/Icons.AutoMirrored.Filled.MenuBook/Icons.Filled.MenuBook/g' {} +
find app/src/main/java/com/example/ui -name "*.kt" -exec sed -i 's/Icons.AutoMirrored.Filled.List/Icons.Filled.List/g' {} +
