#!/bin/bash
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/Icons.Filled.ArrowBack/Icons.AutoMirrored.Filled.ArrowBack/g' {} +
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/Icons.Filled.MenuBook/Icons.AutoMirrored.Filled.MenuBook/g' {} +
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/Icons.Filled.List/Icons.AutoMirrored.Filled.List/g' {} +
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/Icons.Outlined.List/Icons.AutoMirrored.Outlined.List/g' {} +
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/import androidx.compose.material.icons.filled.*/import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.automirrored.filled.*/g' {} +
find app/src/main/java/com/example/ui/ -name "*.kt" -exec sed -i 's/import androidx.compose.material.icons.outlined.*/import androidx.compose.material.icons.outlined.*\nimport androidx.compose.material.icons.automirrored.outlined.*/g' {} +
