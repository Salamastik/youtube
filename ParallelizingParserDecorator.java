// ParallelizingParserDecorator.java
package org.apache.tika.parallel;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.ParserDecorator;
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

        // מזריקים את ה-Factory שלנו לפרסינג של embedded
        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());

        try {
            super.parse(stream, handler, metadata, context);
        } finally {
            // נקודת הסיום: לכתוב את בלוקי ה-XHTML שסיכמנו מכל התמונות
            ParallelEmbeddedDocumentExtractorFactory.drainTo(handler);
        }
    }
}

