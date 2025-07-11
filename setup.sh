#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# --- Check for Dependencies ---

echo "Checking for required dependencies..."

# Check for Java
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed. Please install Java and try again."
    exit 1
fi

# Check for Ollama
if ! command -v ollama &> /dev/null; then
    echo "Error: Ollama is not installed. Please install it from https://ollama.com and try again."
    exit 1
fi

echo "Dependencies found."

# --- Check for Ollama Model ---

echo "Checking for Ollama model (llama3)..."

# Use `ollama list` and `grep` to check if the model exists.
# The `-q` flag for grep suppresses output.
if ! ollama list | grep -q "llama3"; then
    echo "Model 'llama3' not found. Pulling from Ollama..."
    ollama pull llama3
else
    echo "Model 'llama3' is already installed."
fi

# --- Download Project Libraries ---

LIB_DIR="lib"
JSOUP_JAR="$LIB_DIR/jsoup-1.14.3.jar"
GSON_JAR="$LIB_DIR/gson-2.8.8.jar"

JSOUP_URL="https://repo1.maven.org/maven2/org/jsoup/jsoup/1.14.3/jsoup-1.14.3.jar"
GSON_URL="https://repo1.maven.org/maven2/com/google/code/gson/gson/2.8.8/gson-2.8.8.jar"

# Create lib directory if it doesn't exist
if [ ! -d "$LIB_DIR" ]; then
    echo "Creating directory: $LIB_DIR"
    mkdir "$LIB_DIR"
fi

# Download JSoup if it's missing
if [ ! -f "$JSOUP_JAR" ]; then
    echo "Downloading JSoup..."
    curl -o "$JSOUP_JAR" "$JSOUP_URL"
else
    echo "JSoup library already exists."
fi

# Download Gson if it's missing
if [ ! -f "$GSON_JAR" ]; then
    echo "Downloading Gson..."
    curl -o "$GSON_JAR" "$GSON_URL"
else
    echo "Gson library already exists."
fi

# --- Compile the Code ---

echo "Compiling the Java source code..."
javac -cp "$LIB_DIR/*" src/main/java/*.java

echo "\nSetup complete! You can now run the program using ./run.sh"

