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
 * Decorator שמזריק Factory מקבילי ומצמיד ניתוח לכל <img>.
 * בסוף המסמך מנקז כל מה שנותר.
 * עובד גם מול /tika/text (באמצעות characters()).
 */
public class ParallelizingParserDecorator extends ParserDecorator {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ParallelizingParserDecorator.class);

    public ParallelizingParserDecorator() {
        super(new AutoDetectParser(TikaConfig.getDefaultConfig()));
        LOGGER.info("[Decorator] ctor – using AutoDetectParser with server TikaConfig");
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        context.set(org.apache.tika.extractor.EmbeddedDocumentExtractorFactory.class,
                new ParallelEmbeddedDocumentExtractorFactory());
        LOGGER.info("[Decorator] parse() started – factory set on ParseContext");

        ContentHandler injectingHandler = new ContentHandlerDecorator(handler) {
            private boolean drained = false;

            private static String toResourcePath(String srcOrAlt) {
                if (srcOrAlt == null || srcOrAlt.isEmpty()) return null;
                String p = srcOrAlt;
                if (p.startsWith("embedded:")) p = p.substring("embedded:".length());
                if (!p.startsWith("/")) p = "/" + p;
                return p;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts)
                    throws SAXException {
                super.startElement(uri, localName, qName, atts);

                if ("img".equals(localName) || "img".equals(qName)) {
                    String path = toResourcePath(firstNonNull(atts.getValue("src"), atts.getValue("alt")));
                    if (path != null) {
                        LOGGER.info("[Decorator] injecting near <img> path={}", path);
                        ParallelEmbeddedDocumentExtractorFactory.injectFor(this, path);
                    } else {
                        LOGGER.debug("[Decorator] <img> without src/alt – skip injection");
                    }
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                super.endElement(uri, localName, qName);
                if ("body".equals(localName) || "body".equals(qName)) {
                    LOGGER.info("[Decorator] drain remaining at </body>");
                    ParallelEmbeddedDocumentExtractorFactory.drainRemaining(this);
                    drained = true;
                }
            }

            @Override
            public void endDocument() throws SAXException {
                if (!drained) {
                    LOGGER.info("[Decorator] drain remaining at endDocument");
                    ParallelEmbeddedDocumentExtractorFactory.drainRemaining(this);
                }
                super.endDocument();
            }
        };

        Parser wrapped = getWrappedParser();
        wrapped.parse(stream, injectingHandler, metadata, context);
        LOGGER.info("[Decorator] parse() finished");
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }
}
