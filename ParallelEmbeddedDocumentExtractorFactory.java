// ParallelEmbeddedDocumentExtractorFactory.java
package org.apache.tika.parallel;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * מריץ embedded parsing במקביל, אוסף Metadata של כל תמונה,
 * וב-drain כותב בלוקי XHTML למסמך הראשי (ל-/tika) – עם לוגים.
 */
public class ParallelEmbeddedDocumentExtractorFactory implements EmbeddedDocumentExtractorFactory {

    private static final ThreadLocal<List<Future<Metadata>>> FUTURES =
            ThreadLocal.withInitial(ArrayList::new);

    private static final int MAX_PARALLEL =
            Math.max(8, Runtime.getRuntime().availableProcessors());

    private static final ExecutorService POOL =
            new ThreadPoolExecutor(
                    MAX_PARALLEL, MAX_PARALLEL,
                    30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

    @Override
    public EmbeddedDocumentExtractor newInstance(Metadata parentMd, ParseContext context) {
        final Parser embeddedParser = context.get(Parser.class) != null
                ? context.get(Parser.class)
                : new AutoDetectParser();

        final ParsingEmbeddedDocumentExtractor delegate =
                new ParsingEmbeddedDocumentExtractor(context);

        System.err.println("[Factory] newInstance() – parser=" + embeddedParser.getClass().getName());

        return new EmbeddedDocumentExtractor() {

            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                boolean should = delegate.shouldParseEmbedded(metadata);
                if (!should) {
                    System.err.println("[Factory] shouldParseEmbedded=false for " + metadata.get("resourceName"));
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
                    final Metadata mdErr = metadata;
                    Future<Metadata> fut = POOL.submit(() -> {
                        mdErr.add("vlm:error", "read-bytes-failed");
                        return mdErr;
                    });
                    FUTURES.get().add(fut);
                    System.err.println("[Factory] parseEmbedded: readAllBytes() FAILED for " +
                            metadata.get("resourceName"));
                    return;
                }

                final Parser p = embeddedParser;
                final ParseContext ctx = context;
                final Metadata md = metadata;

                Callable<Metadata> task = () -> {
                    String name = md.get("resourceName");
                    if (name == null) name = md.get("X-TIKA:embedded_resource_path");
                    System.err.println("[Factory] task START for " + name +
                            " (thread=" + Thread.currentThread().getName() + ")");
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        p.parse(bais, new DefaultHandler(), md, ctx);
                    } catch (Exception e) {
                        md.add("vlm:error", "parseEmbedded-failed: " + e.getClass().getSimpleName());
                        System.err.println("[Factory] task ERROR for " + name + " : " + e.getMessage());
                    }
                    System.err.println("[Factory] task END for " + name +
                            ", vlm:analysis=" + md.get("vlm:analysis"));
                    return md;
                };

                Future<Metadata> fut = POOL.submit(task);
                FUTURES.get().add(fut);
            }
        };
    }

    /** לקרוא מזה *אחרי* שה-parser הראשי התחיל לייצר HTML; העוטף שלנו קורא בזמן startElement/body */
    public static void drainTo(ContentHandler mainHandler) throws SAXException {
        final List<Future<Metadata>> futures = FUTURES.get();
        System.err.println("[Factory] drainTo() – futures.size=" + futures.size());

        for (Future<Metadata> f : futures) {
            final Metadata md;
            try {
                md = f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[Factory] drainTo() – future get() FAILED: " + e.getMessage());
                continue;
            }

            final String analysis = md.get("vlm:analysis");
            final String provider = md.get("vlm:provider");
            final String model = md.get("vlm:model");

            String name = md.get("resourceName");
            if (name == null) name = md.get("X-TIKA:embedded_resource_path");

            if (analysis == null) {
                System.err.println("[Factory] drainTo() – no vlm:analysis for " + name);
                continue;
            }

            // כתיבה בטוחה ל-XHTML (בת'רד הראשי)
            mainHandler.startElement("", "div", "div", attrs("class", "vlm-result"));
            element(mainHandler, "h3", "Vision Language Model Analysis");
            if (name != null) {
                element(mainHandler, "p", "image=" + name);
            }
            element(mainHandler, "p", analysis);
            if (provider != null || model != null) {
                element(mainHandler, "p",
                        "provider=" + String.valueOf(provider) + ", model=" + String.valueOf(model));
            }
            mainHandler.endElement("", "div", "div");

            System.err.println("[Factory] drainTo() – injected block for " + name);
        }

        futures.clear();
        FUTURES.remove();
        System.err.println("[Factory] drainTo() – done");
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
        char[] c = text.toCharArray();
        h.characters(c, 0, c.length);
        h.endElement("", tag, tag);
    }
}
