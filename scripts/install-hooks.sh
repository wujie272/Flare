#!/bin/bash
# 安装 git hooks
set -e
HOOKS_DIR="$(cd "$(dirname "$0")/hooks" && pwd)"
GIT_HOOKS_DIR="$(cd "$(dirname "$0")/.." && pwd)/.git/hooks"

echo "Installing git hooks from $HOOKS_DIR to $GIT_HOOKS_DIR"
for hook in "$HOOKS_DIR"/*; do
    if [ -f "$hook" ]; then
        name=$(basename "$hook")
        cp "$hook" "$GIT_HOOKS_DIR/$name"
        chmod +x "$GIT_HOOKS_DIR/$name"
        echo "  ✅ $name"
    fi
done
echo "Done! Hooks installed."
