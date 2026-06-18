#!/bin/bash

# Script to find Java 21 and set JAVA_HOME.
# Usage: source java_home.sh

OS="$(uname -s)"
FOUND_JAVA_HOME=""

if [ "$OS" = "Darwin" ]; then
    # macOS
    FOUND_JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
elif [ "$OS" = "Linux" ]; then
    # Linux
    if command -v update-alternatives &> /dev/null; then
        JAVA_BIN=$(update-alternatives --list java 2>/dev/null | grep -E 'java-21|jdk-21' | head -n 1)
        if [ -n "$JAVA_BIN" ]; then
            # Resolve symlinks to get the actual installation path
            REAL_JAVA=$(readlink -f "$JAVA_BIN" 2>/dev/null || realpath "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")
            FOUND_JAVA_HOME=$(dirname $(dirname "$REAL_JAVA"))
        fi
    fi
    
    if [ -z "$FOUND_JAVA_HOME" ] || [ ! -d "$FOUND_JAVA_HOME" ]; then
        # Check common paths
        for path in /usr/lib/jvm/java-21-* /usr/lib/jvm/jdk-21*; do
            if [ -d "$path" ]; then
                FOUND_JAVA_HOME="$path"
                break
            fi
        done
    fi
fi

if [ -n "$FOUND_JAVA_HOME" ] && [ -d "$FOUND_JAVA_HOME" ]; then
    export JAVA_HOME="$FOUND_JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "JAVA_HOME has been set to: $JAVA_HOME"
else
    echo "Error: Java 21 not found on this system." >&2
    # Use return instead of exit so it doesn't close the terminal if sourced
    return 1 2>/dev/null || exit 1
fi
