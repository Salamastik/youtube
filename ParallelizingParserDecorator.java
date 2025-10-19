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

/**
 * עוטף שמזריק את ה-Factory לפרסינג של embedded,
 * ומזריק בלוקי XHTML ממש בתחילת <body> (עם לוגים).
 */
public class ParallelizingParserDecorator extends ParserDecorator {
    public ParallelizingParserDecorator() {
        super(new DefaultParser());
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // ה-Factory שמריץ embedded במקביל ויאסוף תוצאות
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());

        System.err.println("[Decorator] parse() started – factory set on ParseContext");

        // עוטפים את ה-handler כדי להזריק מיד בתחילת <body> (או לפני <div class="page">)
        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean injected = false;

            private boolean isBody(String localName, String qName) {
                return "body".equals(localName) || "body".equals(qName);
            }

            private boolean isPageDiv(String localName, String qName, Attributes atts) {
                if (!("div".equals(localName) || "div".equals(qName))) return false;
                for (int i = 0; i < atts.getLength(); i++) {
                    String n = atts.getLocalName(i);
                    if (n == null || n.isEmpty()) n = atts.getQName(i);
                    if ("class".equals(n) && atts.getValue(i) != null &&
                        atts.getValue(i).contains("page")) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts)
                    throws SAXException {
                // injection בתחילת ה-body או לפני הדיב של העמוד
                if (!injected && (isBody(localName, qName) || isPageDiv(localName, qName, atts))) {
                    System.err.println("[Decorator] injecting at startElement: <" +
                            (localName != null && !localName.isEmpty() ? localName : qName) + ">");
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                    System.err.println("[Decorator] injection done at startElement");
                }
                super.startElement(uri, localName, qName, atts);
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                // fallback: רגע לפני שסוגרים את ה-body, אם לא הוזרק עדיין
                if (!injected && isBody(localName, qName)) {
                    System.err.println("[Decorator] injecting at endElement before </body>");
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                    System.err.println("[Decorator] injection done at endElement </body>");
                }
                super.endElement(uri, localName, qName);
            }

            @Override
            public void endDocument() throws SAXException {
                // fallback אחרון
                if (!injected) {
                    System.err.println("[Decorator] injecting at endDocument (fallback)");
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                    System.err.println("[Decorator] injection done at endDocument");
                }
                super.endDocument();
            }
        };

        // להריץ את ה-parser עם ה-handler העטוף
        super.parse(stream, injectingHandler, metadata, context);
        System.err.println("[Decorator] parse() finished");
    }
}
