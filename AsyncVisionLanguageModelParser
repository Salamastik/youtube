package org.apache.tika.parser.vision;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.ProxySelector;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Async Vision Language Model Parser that integrates with VLMs
 * Supports batch processing and concurrent API calls
 */
public class AsyncVisionLanguageModelParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    
    // Configuration properties
    private String apiEndpoint;
    private String apiKey;
    private String modelName;
    private String provider; // "openai", "anthropic", "custom"
    private String prompt;
    private int maxImageSize = 20 * 1024 * 1024; // 20MB default
    private int timeout = 30; // seconds
    private int maxConcurrentRequests = 5; // Max concurrent API calls
    private int retryAttempts = 3; // Number of retry attempts
    private long retryDelayMs = 1000; // Delay between retries
    
    // Thread pool for async operations
    private final ExecutorService executorService;
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final ConcurrentHashMap<String, CompletableFuture<String>> requestCache = new ConcurrentHashMap<>();
    
    // Default prompt if none specified
    private static final String DEFAULT_PROMPT = "Please analyze this image and provide a detailed description " +
                                               "including: 1) Main subjects and objects, 2) Text content if any, " +
                                               "3) Scene/setting, 4) Colors and composition, 5) Any notable details. " +
                                               "Format the response as structured text.";
    
    private static final Set<MediaType> SUPPORTED_TYPES = 
        Collections.unmodifiableSet(new HashSet<MediaType>() {{
            add(MediaType.image("jpeg"));
            add(MediaType.image("jpg"));
            add(MediaType.image("png"));
            add(MediaType.image("gif"));
            add(MediaType.image("bmp"));
            add(MediaType.image("webp"));
            add(MediaType.image("tiff"));
        }});
        
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;
    private HttpClient unsafeHttpClient;

    public AsyncVisionLanguageModelParser() {
        this(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    }

    public AsyncVisionLanguageModelParser(ExecutorService executorService) {
        this.executorService = executorService;
        
        // Load configuration from environment variables or system properties
        this.provider = System.getProperty("tika.vlm.provider", 
                       System.getenv("TIKA_VLM_PROVIDER") != null ? 
                       System.getenv("TIKA_VLM_PROVIDER") : "openai");
        
        this.apiKey = System.getProperty("tika.vlm.apikey", 
                     System.getenv("TIKA_VLM_API_KEY"));
        
        this.modelName = System.getProperty("tika.vlm.model", 
                        System.getenv("TIKA_VLM_MODEL") != null ? 
                        System.getenv("TIKA_VLM_MODEL") : "gpt-4-vision-preview");
        
        this.prompt = System.getProperty("tika.vlm.prompt", 
                     System.getenv("TIKA_VLM_PROMPT") != null ? 
                     System.getenv("TIKA_VLM_PROMPT") : DEFAULT_PROMPT);
        
        // Set endpoint based on provider
        if ("openai".equalsIgnoreCase(provider)) {
            this.apiEndpoint = System.getProperty("tika.vlm.endpoint", 
                              "https://api.openai.com/v1/chat/completions");
        } else if ("anthropic".equalsIgnoreCase(provider)) {
            this.apiEndpoint = System.getProperty("tika.vlm.endpoint", 
                              "https://api.anthropic.com/v1/messages");
        } else {
            this.apiEndpoint = System.getProperty("tika.vlm.endpoint", 
                              System.getenv("TIKA_VLM_ENDPOINT"));
        }
        
        String timeoutStr = System.getProperty("tika.vlm.timeout", 
                           System.getenv("TIKA_VLM_TIMEOUT"));
        if (timeoutStr != null) {
            try {
                this.timeout = Integer.parseInt(timeoutStr);
            } catch (NumberFormatException e) {
                // Keep default
            }
        }
        
        String maxConcurrentStr = System.getProperty("tika.vlm.maxConcurrent", 
                                  System.getenv("TIKA_VLM_MAX_CONCURRENT"));
        if (maxConcurrentStr != null) {
            try {
                this.maxConcurrentRequests = Integer.parseInt(maxConcurrentStr);
            } catch (NumberFormatException e) {
                // Keep default
            }
        }
        
        this.httpClient = createHttpClient();
    }

    private HttpClient createHttpClient() {
        try {
            HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2)
                .executor(executorService); // Use our executor service
            
            // Check for proxy settings
            String proxyHost = System.getProperty("https.proxyHost");
            String proxyPort = System.getProperty("https.proxyPort");
            if (proxyHost != null && proxyPort != null) {
                builder.proxy(ProxySelector.of(
                    new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))
                ));
            }
            
            // Configure SSL Context for better compatibility
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, new SecureRandom());
            builder.sslContext(sslContext);
            
            return builder.build();
            
        } catch (Exception e) {
            System.err.println("Warning: Failed to configure SSL context: " + e.getMessage());
            return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executorService)
                .build();
        }
    }

    private HttpClient getUnsafeHttpClient() {
        if (unsafeHttpClient == null) {
            unsafeHttpClient = UnsafeHttpClient.createUnsafeClient(executorService);
        }
        return unsafeHttpClient;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                     Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        
        // Check if API is configured
        if (apiKey == null || apiEndpoint == null) {
            throw new TikaException("VLM API not configured. Set TIKA_VLM_API_KEY and TIKA_VLM_ENDPOINT");
        }

        // Read image data
        byte[] imageData = readInputStream(stream);
        
        // Check image size
        if (imageData.length > maxImageSize) {
            throw new TikaException("Image size exceeds maximum allowed size of " + maxImageSize + " bytes");
        }

        // Process asynchronously and wait for result
        try {
            CompletableFuture<String> analysisFuture = analyzeImageAsync(imageData, metadata);
            
            // Wait for the result with timeout
            String analysis = analysisFuture.get(timeout, TimeUnit.SECONDS);
            
            // Parse and output results
            writeOutput(handler, metadata, analysis);
            
        } catch (TimeoutException e) {
            throw new TikaException("VLM API call timed out after " + timeout + " seconds", e);
        } catch (Exception e) {
            throw new TikaException("Failed to analyze image with VLM", e);
        }
    }

    /**
     * Asynchronously analyze an image
     */
    public CompletableFuture<String> analyzeImageAsync(byte[] imageData, Metadata metadata) {
        String base64Image = Base64.getEncoder().encodeToString(imageData);
        String mimeType = metadata.get(Metadata.CONTENT_TYPE);
        if (mimeType == null) {
            mimeType = "image/jpeg";
        }
        
        // Create cache key
        String cacheKey = base64Image.substring(0, Math.min(100, base64Image.length())) + 
                         "_" + mimeType + "_" + prompt.hashCode();
        
        // Check cache
        CompletableFuture<String> cachedFuture = requestCache.get(cacheKey);
        if (cachedFuture != null && !cachedFuture.isCompletedExceptionally()) {
            return cachedFuture;
        }
        
        // Wait if we're at max concurrent requests
        while (activeRequests.get() >= maxConcurrentRequests) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CompletableFuture.failedFuture(e);
            }
        }
        
        activeRequests.incrementAndGet();
        
        CompletableFuture<String> future = callVisionAPIAsync(base64Image, mimeType)
            .handle((result, error) -> {
                activeRequests.decrementAndGet();
                if (error != null) {
                    requestCache.remove(cacheKey);
                    throw new CompletionException(error);
                }
                return result;
            });
        
        requestCache.put(cacheKey, future);
        return future;
    }

    /**
     * Batch analyze multiple images asynchronously
     */
    public CompletableFuture<List<String>> analyzeImagesAsync(List<byte[]> images, List<Metadata> metadataList) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (int i = 0; i < images.size(); i++) {
            Metadata metadata = (metadataList != null && i < metadataList.size()) 
                ? metadataList.get(i) : new Metadata();
            futures.add(analyzeImageAsync(images.get(i), metadata));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    private CompletableFuture<String> callVisionAPIAsync(String base64Image, String mimeType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return callVisionAPIWithRetry(base64Image, mimeType, 0);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    private String callVisionAPIWithRetry(String base64Image, String mimeType, int attempt) 
            throws Exception {
        try {
            HttpRequest request;
            String requestBody;
            
            if ("openai".equalsIgnoreCase(provider)) {
                requestBody = buildOpenAIRequest(base64Image, mimeType);
                request = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                    
            } else if ("anthropic".equalsIgnoreCase(provider)) {
                requestBody = buildAnthropicRequest(base64Image, mimeType);
                request = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
                    
            } else {
                requestBody = buildOpenAIRequest(base64Image, mimeType);
                request = HttpRequest.newBuilder()
                    .uri(URI.create(apiEndpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(timeout))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            }

            // Try async call with regular HTTP client first
            CompletableFuture<HttpResponse<String>> responseFuture = 
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            
            HttpResponse<String> response;
            try {
                response = responseFuture.get(timeout, TimeUnit.SECONDS);
                System.out.println("Successfully connected with secure HTTP client (async)");
            } catch (Exception e) {
                System.err.println("Secure HTTP client failed: " + e.getMessage());
                System.err.println("Falling back to unsafe HTTP client...");
                
                // Try with unsafe HTTP client
                responseFuture = getUnsafeHttpClient()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString());
                response = responseFuture.get(timeout, TimeUnit.SECONDS);
                System.out.println("Successfully connected with unsafe HTTP client (async)");
            }
            
            // Check for rate limiting or temporary errors
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                if (attempt < retryAttempts) {
                    long delay = retryDelayMs * (attempt + 1);
                    System.out.println("Rate limited or service unavailable. Retrying in " + delay + "ms...");
                    Thread.sleep(delay);
                    return callVisionAPIWithRetry(base64Image, mimeType, attempt + 1);
                }
            }
            
            if (response.statusCode() != 200) {
                throw new IOException("VLM API returned status " + response.statusCode() + 
                                    ": " + response.body());
            }
    
            return parseAPIResponse(response.body());
            
        } catch (Exception e) {
            if (attempt < retryAttempts && !(e instanceof TikaException)) {
                long delay = retryDelayMs * (attempt + 1);
                System.err.println("API call failed: " + e.getMessage() + ". Retrying in " + delay + "ms...");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new TikaException("Interrupted during retry", ie);
                }
                return callVisionAPIWithRetry(base64Image, mimeType, attempt + 1);
            }
            throw new TikaException("Failed to call VLM API after " + (attempt + 1) + " attempts", e);
        }
    }

    private void writeOutput(ContentHandler handler, Metadata metadata, String analysis) 
            throws SAXException {
        // Parse and output results
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        
        // Add VLM analysis as structured content
        xhtml.startElement("div", "class", "vlm-analysis");
        xhtml.startElement("h2");
        xhtml.characters("Vision Language Model Analysis");
        xhtml.endElement("h2");
        
        // Add the analysis text
        xhtml.startElement("p");
        xhtml.characters(analysis);
        xhtml.endElement("p");
        
        // Add metadata
        metadata.add("vlm_provider", provider);
        metadata.add("vlm_model", modelName);
        metadata.add("vlm_prompt", prompt);
        metadata.add("vlm_analysis", analysis);
        
        // Extract and add specific elements if found
        extractAndAddEntities(analysis, metadata, xhtml);
        
        xhtml.endElement("div");
        xhtml.endDocument();
    }

    private byte[] readInputStream(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = stream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private String buildOpenAIRequest(String base64Image, String mimeType) 
            throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        
        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        
        ArrayNode content = message.putArray("content");
        
        // Add text prompt
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        
        // Add image
        ObjectNode imageContent = content.addObject();
        imageContent.put("type", "image_url");
        ObjectNode imageUrl = imageContent.putObject("image_url");
        imageUrl.put("url", "data:" + mimeType + ";base64," + base64Image);
        
        root.put("max_tokens", 1000);
        root.put("temperature", 0.5);
        
        return objectMapper.writeValueAsString(root);
    }

    private String buildAnthropicRequest(String base64Image, String mimeType) 
            throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        
        ArrayNode messages = root.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        
        ArrayNode content = message.putArray("content");
        
        // Add image
        ObjectNode imageContent = content.addObject();
        imageContent.put("type", "image");
        ObjectNode source = imageContent.putObject("source");
        source.put("type", "base64");
        source.put("media_type", mimeType);
        source.put("data", base64Image);
        
        // Add text prompt
        ObjectNode textContent = content.addObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        
        root.put("max_tokens", 1000);
        
        return objectMapper.writeValueAsString(root);
    }

    private String parseAPIResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        
        if ("openai".equalsIgnoreCase(provider)) {
            JsonNode choices = root.get("choices");
            if (choices != null && choices.size() > 0) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode content = message.get("content");
                    if (content != null) {
                        return content.asText();
                    }
                }
            }
        } else if ("anthropic".equalsIgnoreCase(provider)) {
            JsonNode content = root.get("content");
            if (content != null && content.size() > 0) {
                JsonNode text = content.get(0).get("text");
                if (text != null) {
                    return text.asText();
                }
            }
        } else {
            // Try OpenAI format first, then Anthropic
            String result = null;
            try {
                JsonNode choices = root.get("choices");
                if (choices != null && choices.size() > 0) {
                    result = choices.get(0).get("message").get("content").asText();
                }
            } catch (Exception e) {
                // Try Anthropic format
                JsonNode content = root.get("content");
                if (content != null && content.size() > 0) {
                    result = content.get(0).get("text").asText();
                }
            }
            if (result != null) return result;
        }
        
        throw new IOException("Unable to parse VLM API response");
    }

    private void extractAndAddEntities(String analysis, Metadata metadata, 
                                      XHTMLContentHandler xhtml) 
            throws SAXException {
        // Extract potential text found in image
        if (analysis.toLowerCase().contains("text:") || 
            analysis.toLowerCase().contains("writing:")) {
            metadata.add("extracted_text_from_vision", "true");
        }
        
        // Extract object detection results
        if (analysis.toLowerCase().contains("object") || 
            analysis.toLowerCase().contains("person") ||
            analysis.toLowerCase().contains("animal")) {
            metadata.add("objects_detected", "true");
        }
        
        // Add structured sections if identifiable
        String[] lines = analysis.split("\n");
        boolean inList = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.matches("^\\d+[)\\.].*") || line.startsWith("-") || line.startsWith("*")) {
                if (!inList) {
                    xhtml.startElement("ul");
                    inList = true;
                }
                xhtml.startElement("li");
                xhtml.characters(line.replaceFirst("^[\\d)\\.*-]+\\s*", ""));
                xhtml.endElement("li");
            } else {
                if (inList) {
                    xhtml.endElement("ul");
                    inList = false;
                }
            }
        }
        
        if (inList) {
            xhtml.endElement("ul");
        }
    }

    /**
     * Cleanup method to shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Getters and setters for configuration
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public void clearCache() {
        requestCache.clear();
    }
}

class UnsafeHttpClient {
    public static HttpClient createUnsafeClient(ExecutorService executor) {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            HttpClient.Builder builder = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(10));
            
            if (executor != null) {
                builder.executor(executor);
            }
            
            return builder.build();
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to create unsafe HTTP client", e);
        }
    }
    
    public static HttpClient createUnsafeClient() {
        return createUnsafeClient(null);
    }
}
