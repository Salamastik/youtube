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

public class ParallelizingParserDecorator extends ParserDecorator {
    public ParallelizingParserDecorator() {
        super(new DefaultParser());
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        // מזריקים את ה-Factory שיריץ embedded בפרלליות ויצבור תוצאות
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());

        // עוטפים את ה-handler כדי להזריק פלט רגע לפני endDocument()
        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            @Override
            public void endDocument() throws SAXException {
                // נכתוב את בלוקי ה-XHTML לפני הסגירה
                // שימי לב: מעבירים את ה-decorator עצמו, לא getContentHandler()
                ParallelEmbeddedDocumentExtractorFactory.drainTo(this);
                super.endDocument();
            }
        };

        // פרסינג עם ה-handler העטוף
        super.parse(stream, injectingHandler, metadata, context);
    }
}
