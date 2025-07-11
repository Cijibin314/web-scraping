# Web Scraping Project Plan

This document outlines the plan for creating a web scraping program.

## Goal

The program will take a list of websites from `input.txt`, scrape their content, and use an Ollama model to extract information based on the schema in `schema.txt`. The extracted information will be saved to `output.json`.

## Implementation

This program will be written in Java.

## Plan

1.  **Read Input:** Read the list of websites from `input.txt`.
2.  **Scrape Websites:** For each website in the input list, fetch the HTML content.
3.  **Extract Information:** Use an Ollama model to process the scraped content and extract information based on the structure defined in `schema.txt`.
4.  **Write Output:** Save the extracted information in `output.json`.
