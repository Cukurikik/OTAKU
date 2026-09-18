#!/bin/bash
find app/src/main/java/com/example/ui/ -type f -name "*.kt" -exec sed -i 's/import androidx.compose.material.icons.automirrored.filled.*/import androidx.compose.material.icons.filled.*/g' {} +
