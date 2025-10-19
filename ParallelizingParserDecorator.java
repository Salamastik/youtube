// ParallelizingParserDecorator.java
package org.apache.tika.parallel;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * Decorator שמזריק EmbeddedDocumentExtractorFactory מקבילי,
 * ואוסף את תוצאות ניתוח התמונות ומזריק אותן בסוף ה-<body>.
 * בנוסף מזריק טקסט גולמי (header/separators) כדי שייראה גם ב-/tika/text.
 */
public class ParallelizingParserDecorator extends ParserDecorator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParallelizingParserDecorator.class);

    // עוטפים את ה-parser המובנה של השרת לפי ה-config שכבר נטען:
    public ParallelizingParserDecorator() {
        super(new AutoDetectParser(TikaConfig.getDefaultConfig()));
        LOGGER.info("[Decorator] ctor – using AutoDetectParser with server TikaConfig");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // חגורה+שלייקס: נרשום את ה-Factory גם ל-ParseContext
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());
        LOGGER.info("[Decorator] parse() started – factory set on ParseContext");

        // נעטוף handler כדי להזריק רגע לפני </body>; עם fallback ב-endDocument
        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean injected = false;

            private boolean isBody(String localName, String qName) {
                return "body".equals(localName) || "body".equals(qName);
            }

            private void emitPlainText(ContentHandler h, String s) throws SAXException {
                char[] arr = s.toCharArray();
                h.characters(arr, 0, arr.length);
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts)
                    throws SAXException {
                // לא מזריקים בתחילת body כדי לא לפספס תמונות שטרם חולצו
                super.startElement(uri, localName, qName, atts);
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if (!injected && isBody(localName, qName)) {
                    LOGGER.info("[Decorator] injecting at endElement </body> ...");
                    // כותרת טקסטואלית שתופיע גם ב-/tika/text
                    emitPlainText(getContentHandler(),
                            System.lineSeparator() +
                            "=== VLM ANALYSIS (begin) ===" + System.lineSeparator());

                    // הזרקת הבלוקים מה-Factory (כולל XHTML+characters של התוצאות)
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(getContentHandler());

                    emitPlainText(getContentHandler(),
                            System.lineSeparator() +
                            "=== VLM ANALYSIS (end) ===" + System.lineSeparator());

                    injected = true;
                    LOGGER.info("[Decorator] injection done at </body>");
                }
                super.endElement(uri, localName, qName);
            }

            @Override
            public void endDocument() throws SAXException {
                if (!injected) {
                    LOGGER.info("[Decorator] injecting at endDocument (fallback) ...");
                    emitPlainText(getContentHandler(),
                            System.lineSeparator() +
                            "=== VLM ANALYSIS (begin) ===" + System.lineSeparator());

                    ParallelEmbeddedDocumentExtractorFactory.drainTo(getContentHandler());

                    emitPlainText(getContentHandler(),
                            System.lineSeparator() +
                            "=== VLM ANALYSIS (end) ===" + System.lineSeparator());

                    injected = true;
                    LOGGER.info("[Decorator] injection done at endDocument");
                }
                super.endDocument();
            }
        };

        // להריץ את הפרסינג עם ה-handler העטוף
        Parser wrapped = getWrappedParser(); // זה ה-AutoDetectParser מה-ctor
        wrapped.parse(stream, injectingHandler, metadata, context);
        LOGGER.info("[Decorator] parse() finished");
    }
}
