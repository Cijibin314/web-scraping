import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final int MAX_RETRIES = 2;
    private static final int THREAD_POOL_SIZE = 5; // Number of concurrent threads
    private static final List<String> USER_AGENTS = Arrays.asList(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:89.0) Gecko/20100101 Firefox/89.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.1 Safari/605.1.15"
    );
    private static final Random RANDOM = new Random();
    private static final Map<String, List<String>> ROBOTS_CACHE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        List<String> initialUrls = readUrlsFromFile("input.txt");
        String schema = new String(Files.readAllBytes(Paths.get("schema.txt")));
        List<JsonObject> extractedData = Collections.synchronizedList(new ArrayList<>());
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        for (String url : initialUrls) {
            String trimmedUrl = url.trim();
            if (trimmedUrl.isEmpty()) {
                continue;
            }
            executor.submit(() -> {
                try {
                    scrapeSite(trimmedUrl, schema, extractedData);
                } catch (IOException e) {
                    System.err.println("Error scraping site: " + trimmedUrl);
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);

        writeOutputToFile("output.json", extractedData);
    }

    private static void scrapeSite(String initialUrl, String schema, List<JsonObject> extractedData) throws IOException {
        Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
        List<String> urlsToScrape = Collections.synchronizedList(new ArrayList<>());
        urlsToScrape.add(initialUrl);
        
        JsonObject result = new JsonObject();
        JsonParser parser = new JsonParser();
        Set<String> schemaKeys = parser.parse(schema).getAsJsonObject().keySet();

        while (!urlsToScrape.isEmpty() && !isComplete(result, schemaKeys)) {
            String currentUrl = urlsToScrape.remove(0);
            if (!isValidUrl(currentUrl)) {
                System.out.println("Skipping invalid URL: " + currentUrl);
                continue;
            }
            if (!visitedUrls.add(currentUrl)) {
                continue;
            }
            if (!isAllowedByRobots(currentUrl)) {
                System.out.println("Skipping URL disallowed by robots.txt: " + currentUrl);
                continue;
            }

            System.out.println("Scraping: " + currentUrl);

            try {
                Document doc = Jsoup.connect(currentUrl)
                                    .userAgent(getRandomUserAgent())
                                    .get();

                String textContent = extractMainContent(doc);
                
                for (int i = 0; i < MAX_RETRIES; i++) {
                    String rawResponse = OllamaExtractor.extractInfo(textContent, schema, currentUrl);
                    String cleanJson = extractJson(rawResponse);

                    try {
                        JsonObject partialData = parser.parse(cleanJson).getAsJsonObject();
                        updateResult(result, partialData, schemaKeys);
                        if (isComplete(result, schemaKeys)) break;
                    } catch (JsonSyntaxException e) {
                        System.err.println("Failed to parse JSON from " + currentUrl + ". Retrying...");
                    }
                }

                // Add other details
                if (!result.has("Oppertunity Name") || result.get("Oppertunity Name").getAsString().isEmpty()) {
                    result.addProperty("Oppertunity Name", doc.title());
                }
                if (!result.has("image_url") || result.get("image_url").getAsString().isEmpty()) {
                    result.addProperty("image_url", findImageUrl(doc));
                }
                

                if (result.has("description")) {
                    String description = result.get("description").getAsString();
                    if (description.length() < 100) { // If description is too short, try to find a better one
                        result.addProperty("description", getCollegeDescription(doc));
                    }
                }
                Elements links = doc.select("a[href]");
                String currentDomain = getDomainName(initialUrl);
                for (Element link : links) {
                    String absUrl = link.absUrl("href");
                    if (getDomainName(absUrl).equals(currentDomain) && !visitedUrls.contains(absUrl)) {
                        urlsToScrape.add(absUrl);
                    }
                }

            } catch (IOException e) {
                System.err.println("Error processing URL " + currentUrl + ": " + e.getMessage());
            }
        }
        if (!result.entrySet().isEmpty()) {
            extractedData.add(result);
        }
    }

    private static String getCollegeDescription(Document doc) throws IOException {
        // 1. Look for an "About Us" page
        Elements aboutLinks = doc.select("a[href*=about]");
        if (!aboutLinks.isEmpty()) {
            try {
                Document aboutDoc = Jsoup.connect(aboutLinks.first().absUrl("href")).get();
                return extractMainContent(aboutDoc);
            } catch (IOException e) {
                System.err.println("Could not fetch About Us page.");
            }
        }

        // 2. If no "About Us" page, get the main content of the current page
        String mainContent = extractMainContent(doc);
        if (mainContent.length() > 200) { // If the content is long enough, return it
            return mainContent;
        }

        // 3. If the main content is too short, try to find a more descriptive element
        Elements descriptionElements = doc.select("[class*=description], [class*=summary]");
        if (!descriptionElements.isEmpty()) {
            return descriptionElements.first().text();
        }

        // 4. If all else fails, return the original main content
        return mainContent;
    }

    

    

    

    

    

    

    

    private static String extractMainContent(Document doc) {
        Elements mainContent = doc.select("main, article, #content, #main-content");
        if (!mainContent.isEmpty()) {
            return mainContent.first().text();
        }
        return doc.body().text();
    }

    private static boolean isAllowedByRobots(String url) {
        try {
            String domain = getDomainName(url);
            if (domain.isEmpty()) return false;

            List<String> disallowedPaths = ROBOTS_CACHE.computeIfAbsent(domain, k -> {
                List<String> paths = new ArrayList<>();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(new URL("http://" + domain + "/robots.txt").openStream()))) {
                    String line;
                    boolean ourUserAgent = false;
                    while ((line = in.readLine()) != null) {
                        if (line.toLowerCase().startsWith("user-agent:")) {
                            ourUserAgent = line.substring(11).trim().equals("*");
                        }
                        if (ourUserAgent && line.toLowerCase().startsWith("disallow:")) {
                            paths.add(line.substring(9).trim());
                        }
                    }
                } catch (IOException e) {
                    // Assume allowed if robots.txt not found
                }
                return paths;
            });

            String path = new URL(url).getPath();
            return disallowedPaths.stream().noneMatch(path::startsWith);
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static String getRandomUserAgent() {
        return USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size()));
    }

    private static synchronized void updateResult(JsonObject main, JsonObject partial, Set<String> schemaKeys) {
        for (String key : schemaKeys) {
            if ((!main.has(key) || main.get(key).getAsString().isEmpty()) && partial.has(key) && !partial.get(key).getAsString().isEmpty()) {
                main.add(key, partial.get(key));
            }
        }
    }

    private static boolean isComplete(JsonObject data, Set<String> schemaKeys) {
        for (String key : schemaKeys) {
            if (!data.has(key) || data.get(key).getAsString().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String getDomainName(String url) {
        try {
            URL netUrl = new URL(url);
            String host = netUrl.getHost();
            if (host.startsWith("www.")) {
                return host.substring(4);
            }
            return host;
        } catch (MalformedURLException e) {
            return "";
        }
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
        Elements logos = doc.select("img[src*=logo i], img[class*=logo i], img[id*=logo i]");
        if (!logos.isEmpty()) return logos.first().absUrl("src");

        Elements aboutLinks = doc.select("a[href*=about i], a[href*=contact i]");
        if (!aboutLinks.isEmpty()) {
            try {
                Document secondaryDoc = Jsoup.connect(aboutLinks.first().absUrl("href")).get();
                Elements secondaryLogos = secondaryDoc.select("img[src*=logo i], img[class*=logo i], img[id*=logo i]");
                if (!secondaryLogos.isEmpty()) return secondaryLogos.first().absUrl("src");
            } catch (IOException e) {
                System.err.println("Could not fetch secondary page for logo search.");
            }
        }

        Elements headerImages = doc.select("header img");
        if (!headerImages.isEmpty()) return headerImages.first().absUrl("src");

        for (Element img : doc.select("img")) {
            try {
                if (Integer.parseInt(img.attr("width")) > 100 && Integer.parseInt(img.attr("height")) > 100) {
                    return img.absUrl("src");
                }
            } catch (NumberFormatException e) { /* Ignore */ }
        }
        return "";
    }

    private static List<String> readUrlsFromFile(String filePath) throws IOException {
        if (Files.exists(Paths.get(filePath)) && Files.size(Paths.get(filePath)) > 0) {
            return new ArrayList<>(Arrays.asList(new String(Files.readAllBytes(Paths.get(filePath))).split(",")));
        }
        return new ArrayList<>();
    }

    private static String extractJson(String text) {
        Pattern pattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : "{}";
    }

    private static void writeOutputToFile(String filePath, List<JsonObject> data) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(filePath)) {
            gson.toJson(data, writer);
        }
    }
}
