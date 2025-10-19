// ParallelEmbeddedDocumentExtractorFactory.java
package org.apache.tika.parallel;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentExtractorFactory;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ParallelEmbeddedDocumentExtractorFactory implements EmbeddedDocumentExtractorFactory {
  // נשתמש ב-ThreadLocal כדי שהעוטף שמסיים parse יוכל להגיע ל-extractor ולבצע drain
  public static final ThreadLocal<ParallelEmbeddedDocumentExtractor> CURRENT = new ThreadLocal<>();

  private static final int MAX_PARALLEL = Math.max(8, Runtime.getRuntime().availableProcessors());
  private static final ExecutorService POOL =
      new ThreadPoolExecutor(MAX_PARALLEL, MAX_PARALLEL, 30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(1024), new ThreadPoolExecutor.CallerRunsPolicy());

  @Override
  public EmbeddedDocumentExtractor newInstance(Metadata md, ParseContext context) {
    Parser embeddedParser = context.get(Parser.class);
    if (embeddedParser == null) embeddedParser = new AutoDetectParser(); // fallback

    ParsingEmbeddedDocumentExtractor def = new ParsingEmbeddedDocumentExtractor(context);
    ParallelEmbeddedDocumentExtractor ex = new ParallelEmbeddedDocumentExtractor(embeddedParser, def, context);
    CURRENT.set(ex);
    return ex;
  }

  public static class ParallelEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
    private final Parser embeddedParser;
    private final ParsingEmbeddedDocumentExtractor delegate;
    private final ParseContext context;
    private final List<Future<EmbeddedResult>> tasks = new ArrayList<>();

    public ParallelEmbeddedDocumentExtractor(Parser p, ParsingEmbeddedDocumentExtractor d, ParseContext c) {
      this.embeddedParser = p; this.delegate = d; this.context = c;
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
      return delegate.shouldParseEmbedded(metadata);
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) {
      // אל תכתוב ל-handler כאן! לא thread-safe
      Callable<EmbeddedResult> job = () -> {
        try (InputStream is = stream) {
          // handler זמני פר-תמונה
          org.apache.tika.sax.ToXMLContentHandler tmp = new org.apache.tika.sax.ToXMLContentHandler();
          Metadata m = new Metadata();
          for (String n : metadata.names()) for (String v : metadata.getValues(n)) m.add(n, v);
          embeddedParser.parse(is, tmp, m, context);
          return new EmbeddedResult(m, tmp.toString(), null);
        } catch (Exception e) {
          return new EmbeddedResult(metadata, "", e);
        }
      };
      tasks.add(POOL.submit(job));
    }

    // לקרוא מזה בת׳רד הראשי אחרי שסיימנו super.parse(...)
    public void drainTo(ContentHandler mainHandler, Metadata mainMetadata) throws org.xml.sax.SAXException {
      for (Future<EmbeddedResult> f : tasks) {
        try {
          EmbeddedResult r = f.get(60, TimeUnit.SECONDS);
          // כאן מותר לכתוב ל-mainHandler
          mainHandler.startElement("", "embedded", "embedded", null);
          // אפשר להכניס את ה-XML/JSON/מטאדאטה של r
          mainHandler.characters(r.xml.toCharArray(), 0, r.xml.length());
          mainHandler.endElement("", "embedded", "embedded");
        } catch (TimeoutException te) {
          mainMetadata.add("tika:embeddedTimeout", "true");
        } catch (Exception e) {
          mainMetadata.add("tika:embeddedError", e.getClass().getSimpleName());
        }
      }
      tasks.clear();
    }

    private static class EmbeddedResult {
      final Metadata md; final String xml; final Exception err;
      EmbeddedResult(Metadata md, String xml, Exception err){ this.md=md; this.xml=xml; this.err=err; }
    }
  }
}
