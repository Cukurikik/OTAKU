#!/bin/bash
find app/src/main/java/com/example/ui -name "*.kt" -exec sed -i 's/import androidx.compose.material.icons.automirrored.filled.\*//g' {} +
