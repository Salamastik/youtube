// ParallelEmbeddedDocumentExtractorFactory.java
package org.apache.tika.parallel;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;

public class ParallelEmbeddedDocumentExtractorFactory implements EmbeddedDocumentExtractorFactory {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParallelEmbeddedDocumentExtractorFactory.class);

    // Thread pool (configurable via TIKA_VLM_THREADS or -Dtika.vlm.threads)
    private static final int CONCURRENCY = Integer.parseInt(
            System.getProperty("tika.vlm.threads",
                    System.getenv().getOrDefault("TIKA_VLM_THREADS", "6"))
    );

    private static final ExecutorService EXEC =
            new ThreadPoolExecutor(
                    CONCURRENCY, CONCURRENCY,
                    30L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024),
                    r -> {
                        Thread t = new Thread(r, "vlm-worker-" + System.nanoTime());
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

    // Futures per embedded resource path (e.g. "/image3.jpg")
    private static final ConcurrentMap<String, CompletableFuture<Metadata>> FUTURES = new ConcurrentHashMap<>();
    // Tracks which results were already injected (avoid duplicates)
    private static final ConcurrentMap<String, Boolean> INJECTED = new ConcurrentHashMap<>();

    @Override
    public EmbeddedDocumentExtractor newInstance(Metadata parentMd, ParseContext context) {
        final Parser embeddedParser = Objects.requireNonNullElseGet(
                context.get(Parser.class), AutoDetectParser::new);

        final ParsingEmbeddedDocumentExtractor delegate =
                new ParsingEmbeddedDocumentExtractor(context);

        LOGGER.info("[Factory] newInstance – embeddedParser={}", embeddedParser.getClass().getName());

        return new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                boolean should = delegate.shouldParseEmbedded(metadata);
                if (!should) {
                    LOGGER.debug("[Factory] shouldParseEmbedded=false resource={}", metadata.get("resourceName"));
                }
                return should;
            }

            @Override
            public void parseEmbedded(InputStream stream,
                                      ContentHandler handler,
                                      Metadata metadata,
                                      boolean outputHtml) throws SAXException {

                final byte[] data;
                try (InputStream is = stream) {
                    data = is.readAllBytes();
                } catch (IOException e) {
                    LOGGER.warn("[Factory] readAllBytes FAILED for {}", metadata.get("resourceName"), e);
                    // Put a completed future to keep drain stable
                    CompletableFuture<Metadata> failed = CompletableFuture.completedFuture(metadata);
                    FUTURES.putIfAbsent(normalizePath(metadata), failed);
                    return;
                }

                // Independent metadata copy for the async task
                final Metadata mdCopy = copyMetadata(metadata);
                final String path = normalizePath(mdCopy);

                CompletableFuture<Metadata> fut = CompletableFuture.supplyAsync(() -> {
                    LOGGER.info("[Factory] task START {} (thread={})",
                            path, Thread.currentThread().getName());
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        embeddedParser.parse(bais, new DefaultHandler(), mdCopy, context);
                    } catch (Exception e) {
                        mdCopy.add("vlm:error", "parseEmbedded-failed:" + e.getClass().getSimpleName());
                        LOGGER.warn("[Factory] task ERROR {} – {}", path, e.toString());
                    }
                    LOGGER.info("[Factory] task END {} (analysis={})",
                            path, mdCopy.get("vlm:analysis"));
                    return mdCopy;
                }, EXEC);

                FUTURES.put(path, fut);
                LOGGER.info("[Factory] scheduled {}", path);
            }
        };
    }

    /** Inject the result right after the corresponding <img>. */
    public static void injectFor(ContentHandler h, String resourcePath) throws SAXException {
        if (resourcePath == null) return;
        CompletableFuture<Metadata> fut = FUTURES.get(resourcePath);
        if (fut == null) {
            LOGGER.debug("[Factory] injectFor – no future for {}", resourcePath);
            return;
        }
        if (INJECTED.putIfAbsent(resourcePath, Boolean.TRUE) != null) {
            return; // Already injected
        }
        Metadata md = fut.join(); // Wait only for this image
        writeBlock(h, resourcePath, md);
        LOGGER.info("[Factory] injected {}", resourcePath);
    }

    /** Drain any remaining results at the end of the document. */
    public static void drainRemaining(ContentHandler h) throws SAXException {
        for (Map.Entry<String, CompletableFuture<Metadata>> e : FUTURES.entrySet()) {
            final String path = e.getKey();
            if (INJECTED.putIfAbsent(path, Boolean.TRUE) == null) {
                Metadata md = e.getValue().join();
                writeBlock(h, path, md);
                LOGGER.info("[Factory] injected (drain) {}", path);
            }
        }
    }

    // ===== helpers =====

    private static Metadata copyMetadata(Metadata src) {
        Metadata dst = new Metadata();
        for (String n : src.names()) {
            dst.set(n, src.get(n));
        }
        return dst;
    }

    /** Normalizes to "/imageN.ext" using available metadata hints. */
    private static String normalizePath(Metadata md) {
        String path = firstNonNull(
                md.get("X-TIKA:final_embedded_resource_path"),
                md.get("X-TIKA:embedded_resource_path"),
                md.get("resourceName")
        );
        if (path == null) {
            path = "__unknown_" + System.nanoTime();
        }
        if (path.startsWith("embedded:")) {
            path = path.substring("embedded:".length());
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return path;
    }

    private static String firstNonNull(String a, String b, String c) {
        return a != null ? a : (b != null ? b : c);
    }

    /** Writes both plain text (visible in /tika/text) and a small XHTML block (visible in /tika). */
    private static void writeBlock(ContentHandler h, String path, Metadata md) throws SAXException {
        String analysis = md.get("vlm:analysis");
        String provider = md.get("vlm:provider");
        String model = md.get("vlm:model");

        // Plain text – ensures visibility in /tika/text:
        String nl = System.lineSeparator();
        writeChars(h, nl + "=== VLM Analysis for " + path + " ===" + nl);
        if (analysis != null) writeChars(h, analysis);
        writeChars(h, nl + "[provider=" + String.valueOf(provider) +
                ", model=" + String.valueOf(model) + "]" + nl);

        // Minimal XHTML for /tika:
        h.startElement("", "div", "div", attrs("class", "vlm-result"));
        element(h, "h3", "Vision Language Model Analysis");
        element(h, "p", "image=" + path);
        element(h, "p", analysis);
        element(h, "p", "provider=" + String.valueOf(provider) + ", model=" + String.valueOf(model));
        h.endElement("", "div", "div");
    }

    private static void writeChars(ContentHandler h, String s) throws SAXException {
        if (s == null || s.isEmpty()) return;
        char[] c = s.toCharArray();
        h.characters(c, 0, c.length);
    }

    private static AttributesImpl attrs(String... kv) {
        AttributesImpl a = new AttributesImpl();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            a.addAttribute("", kv[i], kv[i], "CDATA", kv[i + 1]);
        }
        return a;
    }

    private static void element(ContentHandler h, String tag, String text) throws SAXException {
        if (text == null) return;
        h.startElement("", tag, tag, new AttributesImpl());
        writeChars(h, text);
        h.endElement("", tag, tag);
    }
}
