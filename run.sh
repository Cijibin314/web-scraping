#!/bin/bash

# --- Check for Ollama Service ---
echo "Checking if Ollama service is running..."

# Use curl to check if the Ollama API is responsive.
if ! curl -s http://localhost:11434/ > /dev/null; then
    echo "Error: Ollama service is not running. Please start it in a separate terminal with 'ollama serve' and try again."
    exit 1
fi

echo "Ollama service is running."

# --- Run the Program ---

echo "Running the web scraper..."

java -cp "src/main/java:lib/*" Main

echo "\nProgram finished. Check output.json for the results."

