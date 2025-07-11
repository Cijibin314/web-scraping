import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final int MAX_RETRIES = 2; // Initial try + 1 retry

    public static void main(String[] args) throws IOException {
        List<String> urls = readUrlsFromFile("input.txt");
        String schema = new String(Files.readAllBytes(Paths.get("schema.txt")));
        List<JsonObject> extractedData = new ArrayList<>();

        for (String urlStr : urls) {
            String trimmedUrl = urlStr.trim();
            if (!isValidUrl(trimmedUrl)) {
                System.err.println("Skipping invalid URL: " + trimmedUrl);
                continue;
            }

            System.out.println("Scraping: " + trimmedUrl);
            JsonObject finalJsonObject = null;

            try {
                Document doc = Jsoup.connect(trimmedUrl)
                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                    .get();

                String title = doc.title();
                String imageUrl = findImageUrl(doc);
                String textContent = doc.body().text();

                for (int i = 0; i < MAX_RETRIES; i++) {
                    String rawResponse = OllamaExtractor.extractInfo(textContent, schema, trimmedUrl);
                    String cleanJson = extractJson(rawResponse);

                    Gson gson = new GsonBuilder().setLenient().create();
                    JsonObject jsonObject = gson.fromJson(cleanJson, JsonObject.class);

                    if (validateData(jsonObject)) {
                        finalJsonObject = jsonObject;
                        break; // Data is valid, exit retry loop
                    }
                    System.err.println("Validation failed for " + trimmedUrl + ". Retrying... (" + (i + 1) + "/" + MAX_RETRIES + ")");
                }

                // If still null after retries, create a clean object
                if (finalJsonObject == null) {
                    System.err.println("Could not get valid data for " + trimmedUrl + " after retries. Saving partial data.");
                    finalJsonObject = new JsonObject();
                    finalJsonObject.addProperty("Company Name", "");
                    finalJsonObject.addProperty("description", "");
                }

                // Add the reliably extracted data
                finalJsonObject.addProperty("Oppertunity Name", title);
                finalJsonObject.addProperty("image_url", imageUrl);
                finalJsonObject.addProperty("website", trimmedUrl);
                extractedData.add(finalJsonObject);

            } catch (IOException e) {
                System.err.println("Error processing URL " + trimmedUrl + ": " + e.getMessage());
            }
        }

        writeOutputToFile("output.json", extractedData);
    }

    private static boolean validateData(JsonObject data) {
        // Check for required fields and ensure they are plain strings without HTML.
        String companyName = data.has("Company Name") ? data.get("Company Name").getAsString() : "";
        String description = data.has("description") ? data.get("description").getAsString() : "";

        if (companyName.isEmpty() || description.isEmpty()) {
            return false; // Basic check: we must have a company name and description.
        }

        // Check for HTML tags
        if (companyName.matches(".*<[^>]+>.*") || description.matches(".*<[^>]+>.*")) {
            return false;
        }

        return true;
    }

    private static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String findImageUrl(Document doc) {
        // 1. Search for logo on the current page first (case-insensitive).
        Elements logos = doc.select("img[src*=logo i], img[class*=logo i], img[id*=logo i]");
        if (!logos.isEmpty()) {
            return logos.first().absUrl("src");
        }

        // 2. If no logo, check for an "About" or "Contact" page and search there.
        Elements aboutLinks = doc.select("a[href*=about i], a[href*=contact i]");
        if (!aboutLinks.isEmpty()) {
            String secondaryUrl = aboutLinks.first().absUrl("href");
            try {
                Document secondaryDoc = Jsoup.connect(secondaryUrl).get();
                Elements secondaryLogos = secondaryDoc.select("img[src*=logo i], img[class*=logo i], img[id*=logo i]");
                if (!secondaryLogos.isEmpty()) {
                    return secondaryLogos.first().absUrl("src");
                }
            } catch (IOException e) {
                // Couldn't fetch the secondary page, so just continue.
                System.err.println("Could not fetch secondary page for logo search: " + secondaryUrl);
            }
        }

        // 3. If still no logo, fall back to finding a prominent image on the *original* page.
        Elements headerImages = doc.select("header img");
        if (!headerImages.isEmpty()) {
            return headerImages.first().absUrl("src");
        }

        // 4. Final fallback: get the first large image on the *original* page.
        Elements allImages = doc.select("img");
        for (Element img : allImages) {
            try {
                int width = Integer.parseInt(img.attr("width"));
                int height = Integer.parseInt(img.attr("height"));
                if (width > 100 && height > 100) {
                    return img.absUrl("src");
                }
            } catch (NumberFormatException e) {
                // Ignore images without explicit dimensions
            }
        }

        return ""; // Return empty if no suitable image is found
    }

    private static List<String> readUrlsFromFile(String filePath) throws IOException {
        if (Files.exists(Paths.get(filePath)) && Files.size(Paths.get(filePath)) > 0) {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            return new ArrayList<>(Arrays.asList(content.split(",")));
        } else {
            return new ArrayList<>();
        }
    }

    private static String extractJson(String text) {
        // This pattern finds a JSON object within the text, even if it's in a markdown block
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "{}"; // Return empty JSON if not found
    }

    private static void writeOutputToFile(String filePath, List<JsonObject> data) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(data, writer);
        }
    }
}