#!/bin/bash
find app/src/main/java/com/example/ui/ -type f -name "*.kt" -exec sed -i 's/Icons.AutoMirrored.Filled/Icons.Filled/g' {} +
find app/src/main/java/com/example/ui/ -type f -name "*.kt" -exec sed -i 's/Icons.AutoMirrored.Outlined/Icons.Outlined/g' {} +
