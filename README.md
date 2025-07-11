# Web Scraping with Ollama

This project is a Java-based web scraper that extracts structured data from websites using a large language model (Ollama).

## Overview

The program reads a list of website URLs from `input.txt`, scrapes the content of each site, and then uses a locally running Ollama model (`llama3`) to extract information based on a predefined schema. The final, clean data is saved as a JSON array in `output.json`.

## Features

*   **Bulk Scraping:** Processes multiple websites from a single input file.
*   **Intelligent Extraction:** Uses a large language model to understand and extract data from unstructured HTML.
*   **HTML Cleaning:** Automatically removes irrelevant content (like navigation, footers, and scripts) before analysis.
*   **Robust JSON Parsing:** Handles minor variations and potential errors in the model's output.
*   **Configurable Schema:** The data to be extracted is defined in `schema.txt`.

## How to Use

### 1. Setup

First, run the setup script to ensure you have all the necessary dependencies and to compile the code. The script will check for Java and Ollama and will let you know if they are missing.

1.  **Make the script executable:**
    ```bash
    chmod +x setup.sh run.sh
    ```
2.  **Run the script:**
    ```bash
    ./setup.sh
    ```

### 2. Run

Once the setup is complete, you can run the program.

1.  **Add Websites to `input.txt`:**
    Open the `input.txt` file and add the URLs you want to scrape. The URLs should be in a single line, separated by commas.
    ```
    https://website1.com,https://website2.com
    ```
2.  **Start the Ollama Service:**
    In a separate terminal, make sure the Ollama service is running:
    ```bash
    ollama serve
    ```
3.  **Run the Scraper:**
    Execute the `run.sh` script:
    ```bash
    ./run.sh
    ```

The program will then scrape each website and generate the `output.json` file.

## Output

The extracted data will be saved in `output.json`. The output is a JSON array, where each object represents the data extracted from one of the websites listed in the input file.

**Example `output.json`:**
```json
[
  {
    "Company Name": "Maffei Services",
    "description": "Your trusted local plumbing experts...",
    "website": "https://maffeiservices.com/..."
  },
  {
    "Company Name": "Hair Crafters Ipswich",
    "description": "A friendly and experienced team...",
    "website": "https://www.haircraftersipswich.com/"
  }
]
```
