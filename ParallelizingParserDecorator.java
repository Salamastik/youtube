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
 * Decorator שמזריק EmbeddedDocumentExtractorFactory מקבילי
 * ומזריק את תוצאות ה-VLM בסוף <body>. בנוסף מזריק טקסט "גולמי"
 * (characters) כדי שיופיע גם ב-/tika/text.
 */
public class ParallelizingParserDecorator extends ParserDecorator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParallelizingParserDecorator.class);

    public ParallelizingParserDecorator() {
        // להשתמש ב-AutoDetectParser של השרת כדי לרשת את ה-config הטעון
        super(new AutoDetectParser(TikaConfig.getDefaultConfig()));
        LOGGER.info("[Decorator] ctor – using AutoDetectParser with server TikaConfig");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // חגורה+שלייקס: גם דרך ה-ParseContext
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());
        LOGGER.info("[Decorator] parse() started – factory set on ParseContext");

        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean injected = false;

            private boolean isBody(String localName, String qName) {
                return "body".equals(localName) || "body".equals(qName);
            }

            private void emitPlainText(String s) throws SAXException {
                char[] arr = s.toCharArray();
                // 'this' הוא עצמו ContentHandler, זה יזרום קדימה לכל handler, כולל TextContentHandler
                this.characters(arr, 0, arr.length);
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

                    // כותרות טקסטואליות שייראו גם ב-/tika/text
                    emitPlainText(System.lineSeparator() +
                                  "=== VLM ANALYSIS (begin) ===" + System.lineSeparator());

                    // להעביר את ה-Decorated handler עצמו (this)
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);

                    emitPlainText(System.lineSeparator() +
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

                    emitPlainText(System.lineSeparator() +
                                  "=== VLM ANALYSIS (begin) ===" + System.lineSeparator());

                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);

                    emitPlainText(System.lineSeparator() +
                                  "=== VLM ANALYSIS (end) ===" + System.lineSeparator());

                    injected = true;
                    LOGGER.info("[Decorator] injection done at endDocument]");
                }
                super.endDocument();
            }
        };

        Parser wrapped = getWrappedParser(); // AutoDetectParser מה-ctor
        wrapped.parse(stream, injectingHandler, metadata, context);
        LOGGER.info("[Decorator] parse() finished");
    }
}
