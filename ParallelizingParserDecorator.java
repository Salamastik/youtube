// ParallelizingParserDecorator.java
package org.apache.tika.parallel;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import java.io.IOException;
import java.io.InputStream;

public class ParallelizingParserDecorator extends ParserDecorator {
    public ParallelizingParserDecorator() {
        super(new DefaultParser());
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // מפעיל את ה-Factory שמריץ embedded במקביל ואוסף תוצאות
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());
        System.err.println("[Decorator] parse() started – factory set on ParseContext");

        // עוטף את ה-handler: מזריק בלוקים ממש לפני </body>; עם fallback ב-endDocument
        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean injected = false;

            private boolean isBody(String localName, String qName) {
                return "body".equals(localName) || "body".equals(qName);
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
                    System.err.println("[Decorator] injecting at endElement </body> ...");
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                    System.err.println("[Decorator] injection done at </body>");
                }
                super.endElement(uri, localName, qName);
            }

            @Override
            public void endDocument() throws SAXException {
                if (!injected) {
                    System.err.println("[Decorator] injecting at endDocument (fallback) ...");
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                    System.err.println("[Decorator] injection done at endDocument");
                }
                super.endDocument();
            }
        };

        // להריץ את הפרסינג עם ה-handler העטוף
        super.parse(stream, injectingHandler, metadata, context);
        System.err.println("[Decorator] parse() finished");
    }
}
