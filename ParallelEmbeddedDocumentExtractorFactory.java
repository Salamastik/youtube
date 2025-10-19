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
 * וב-drain כותב בלוקי XHTML למסמך הראשי (ל-/tika).
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

        // שומרים delegate עבור shouldParseEmbedded
        final ParsingEmbeddedDocumentExtractor delegate =
                new ParsingEmbeddedDocumentExtractor(context);

        return new EmbeddedDocumentExtractor() {

            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return delegate.shouldParseEmbedded(metadata);
            }

            @Override
            public void parseEmbedded(InputStream stream,
                                      ContentHandler handler,
                                      Metadata metadata,
                                      boolean outputHtml) throws SAXException {
                // קוראים את הזרם לבייטים כדי לאפשר ריצה מאוחרת/מקבילית
                final byte[] data;
                try (InputStream is = stream) {
                    data = is.readAllBytes();
                } catch (IOException e) {
                    // עדיין נוסיף משימה שמסמנת שגיאה ל-metadata
                    final Metadata mdErr = metadata;
                    Future<Metadata> fut = POOL.submit(() -> {
                        mdErr.add("vlm:error", "read-bytes-failed");
                        return mdErr;
                    });
                    FUTURES.get().add(fut);
                    return;
                }

                // קיבוע כל המשתנים שנכנסים ל-lambda כ-final
                final Parser p = embeddedParser;
                final ParseContext ctx = context;
                final Metadata md = metadata;

                // המשימה: להריץ את ה-parser של ה-embedded (שיקרא ל-VLM ויכתוב ל-Metadata)
                Callable<Metadata> task = () -> {
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                        // שימי לב: handler כאן הוא DefaultHandler – לא כותבים ל-XHTML מתוך ת'רד!
                        p.parse(bais, new DefaultHandler(), md, ctx);
                    } catch (Exception e) {
                        md.add("vlm:error", "parseEmbedded-failed: " + e.getClass().getSimpleName());
                    }
                    return md;
                };

                Future<Metadata> fut = POOL.submit(task);
                FUTURES.get().add(fut);
            }
        };
    }

    /** לקרוא מזה *אחרי* שה-parser הראשי סיים (בת'רד הראשי) */
    public static void drainTo(ContentHandler mainHandler) throws SAXException {
        final List<Future<Metadata>> futures = FUTURES.get();
        for (Future<Metadata> f : futures) {
            final Metadata md;
            try {
                md = f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                continue;
            }
            final String analysis = md.get("vlm:analysis");
            if (analysis == null) continue;

            // כתיבה בטוחה ל-XHTML (בת'רד הראשי בלבד)
            mainHandler.startElement("", "div", "div", attrs("class", "vlm-result"));
            element(mainHandler, "h3", "Vision Language Model Analysis");
            element(mainHandler, "p", analysis);

            final String provider = md.get("vlm:provider");
            final String model = md.get("vlm:model");
            if (provider != null || model != null) {
                element(mainHandler, "p",
                        "provider=" + String.valueOf(provider) + ", model=" + String.valueOf(model));
            }
            mainHandler.endElement("", "div", "div");
        }
        futures.clear();
        FUTURES.remove();
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
