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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Factory שמריץ embedded parsing במקביל,
 * אוסף את ה-Metadata של כל תמונה, וב-drain כותב XHTML לסיכום ל-handler הראשי.
 */
public class ParallelEmbeddedDocumentExtractorFactory implements EmbeddedDocumentExtractorFactory {

    // futures פר-מסמך (ThreadLocal) כדי לאסוף משימות
    private static final ThreadLocal<List<Future<Metadata>>> FUTURES =
            ThreadLocal.withInitial(ArrayList::new);

    // ברירת מחדל: 8 ת'רדים (או CPU)
    private static final int MAX_PARALLEL =
            Math.max(8, Runtime.getRuntime().availableProcessors());
    private static final ExecutorService POOL =
            new ThreadPoolExecutor(MAX_PARALLEL, MAX_PARALLEL,
                    30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new ThreadPoolExecutor.CallerRunsPolicy());

    @Override
    public EmbeddedDocumentExtractor newInstance(Metadata parentMd, ParseContext context) {
        Parser embeddedParser = context.get(Parser.class);
        if (embeddedParser == null) embeddedParser = new AutoDetectParser();
        ParsingEmbeddedDocumentExtractor delegate = new ParsingEmbeddedDocumentExtractor(context);

        return new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return delegate.shouldParseEmbedded(metadata);
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler,
                                      Metadata metadata, boolean outputHtml)
                    throws SAXException {

                // קוראים את הזרם לבייטים כדי לאפשר ריצה מאוחרת/מקבילית
                byte[] bytes;
                try (InputStream is = stream) {
                    bytes = is.readAllBytes();
                } catch (IOException e) {
                    bytes = new byte[0];
                }

                // משימה שמריצה את ה-parser של ה-embedded (זה יקרא ל-VLM שלך
                // ששמנו לכתוב ל-Metadata בלבד)
                Callable<Metadata> task = () -> {
                    try (InputStream is = new ByteArrayInputStream(bytes)) {
                        embeddedParser.parse(is, new org.xml.sax.helpers.DefaultHandler(), metadata, context);
                    } catch (Exception ignore) {
                        metadata.add("vlm:error", "parseEmbedded-failed");
                    }
                    return metadata; // ה-Metadata מכיל vlm:analysis וכו'
                };

                FUTURES.get().add(POOL.submit(task));
            }
        };
    }

    /** לקרוא מזה אחרי שה-parser הראשי סיים (בת'רד הראשי) */
    public static void drainTo(ContentHandler mainHandler) throws SAXException {
        List<Future<Metadata>> futures = FUTURES.get();
        for (Future<Metadata> f : futures) {
            Metadata md;
            try {
                md = f.get(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                continue;
            }
            String analysis = md.get("vlm:analysis");
            if (analysis == null) continue;

            // כותבים בלוק XHTML לתוצאה (בטוח-Thread, בת'רד הראשי)
            mainHandler.startElement("", "div", "div", attrs("class", "vlm-result"));
            element(mainHandler, "h3", "Vision Language Model Analysis");
            element(mainHandler, "p", analysis);
            // אופציונלי: ספק/מודל/פרומפט
            String provider = md.get("vlm:provider");
            String model = md.get("vlm:model");
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


