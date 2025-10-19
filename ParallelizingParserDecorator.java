// ParallelizingParserDecorator.java
package org.apache.tika.parallel;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.*;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

public class ParallelizingParserDecorator extends ParserDecorator {

  public ParallelizingParserDecorator() {
    super(new DefaultParser());
  }

  @Override
  public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
      throws IOException, SAXException, TikaException {

    // הזרקת ה-Factory כך שכל embedded ילך דרך המקבילי
    context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new com.example.tika.parallel.ParallelEmbeddedDocumentExtractorFactory());

    try {
      super.parse(stream, handler, metadata, context);
    } finally {
      // ניקוז התוצאות ל-handler הראשי (בטוח-Thread)
      var ex = com.example.tika.parallel.ParallelEmbeddedDocumentExtractorFactory.CURRENT.get();
      if (ex != null) {
        ex.drainTo(handler, metadata);
        com.example.tika.parallel.ParallelEmbeddedDocumentExtractorFactory.CURRENT.remove();
      }
    }
  }
}

