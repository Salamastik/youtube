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

import java.io.IOException;
import java.io.InputStream;

/**
 * עוטף שמזריק את ה-Factory לפרסינג של embedded,
 * ומזריק בלוקי XHTML לתוך <body> ממש לפני שסוגרים אותו.
 */
public class ParallelizingParserDecorator extends ParserDecorator {
    public ParallelizingParserDecorator() {
        super(new DefaultParser());
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // ה-Factory שמריץ תמונות במקביל ואוסף Metadata לכל תמונה:
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());

        // עוטפים את ה-handler כדי "להתפרץ" רגע לפני שסוגרים את <body>
        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean injected = false;

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                // לפני שסוגרים את <body> פעם ראשונה – נזריק את הבלוקים
                if (!injected && "body".equals(localName)) {
                    ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                    injected = true;
                }
                super.endElement(uri, localName, qName);
            }
        };

        // להריץ את ה-parser האמיתי עם ה-handler העטוף
        super.parse(stream, injectingHandler, metadata, context);
    }
}

