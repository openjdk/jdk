/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// ResolvingParser.java - An interface for reading catalog files

/*
 * Copyright 2001-2004 The Apache Software Foundation or its licensors,
 * as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xml.internal.resolver.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Locale;

import org.xml.sax.Parser;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.DocumentHandler;
import org.xml.sax.AttributeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

import com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl;
import com.sun.org.apache.xml.internal.resolver.Catalog;
import com.sun.org.apache.xml.internal.resolver.CatalogManager;
import com.sun.org.apache.xml.internal.resolver.helpers.FileURL;

/**
 * A SAX Parser that performs catalog-based entity resolution.
 *
 * <p>This class implements a SAX Parser that performs entity resolution
 * using the CatalogResolver. The actual, underlying parser is obtained
 * from a SAXParserFactory.</p>
 * </p>
 *
 * @deprecated This interface has been replaced by the
 *             {@link com.sun.org.apache.xml.internal.resolver.tools.ResolvingXMLReader} for SAX2.
 * @see CatalogResolver
 * @see org.xml.sax.Parser
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 * @version 1.0
 */
public class ResolvingParser
  implements Parser, DTDHandler, DocumentHandler, EntityResolver {
  /** Make the parser Namespace aware? */
  public static boolean namespaceAware = true;

  /** Make the parser validating? */
  public static boolean validating = false;

  /** Suppress explanatory message?
   *
   * @see #parse(InputSource)
   */
  public static boolean suppressExplanation = false;

  /** The underlying parser. */
  private SAXParser saxParser = null;

  /** The underlying reader. */
  private Parser parser = null;

  /** The underlying DocumentHandler. */
  private DocumentHandler documentHandler = null;

  /** The underlying DTDHandler. */
  private DTDHandler dtdHandler = null;

  /** The manager for the underlying resolver. */
  private CatalogManager catalogManager = CatalogManager.getStaticManager();

  /** The underlying catalog resolver. */
  private CatalogResolver catalogResolver = null;

  /** A separate resolver for oasis-xml-pi catalogs. */
  private CatalogResolver piCatalogResolver = null;

  /** Are we in the prolog? Is an oasis-xml-catalog PI valid now? */
  private boolean allowXMLCatalogPI = false;

  /** Has an oasis-xml-catalog PI been seen? */
  private boolean oasisXMLCatalogPI = false;

  /** The base URI of the input document, if known. */
  private URL baseURL = null;

  /** Constructor. */
  public ResolvingParser() {
    initParser();
  }

  /** Constructor. */
  public ResolvingParser(CatalogManager manager) {
    catalogManager = manager;
    initParser();
  }

  /** Initialize the parser. */
  private void initParser() {
    catalogResolver = new CatalogResolver(catalogManager);
    SAXParserFactory spf = catalogManager.useServicesMechanism() ?
                    SAXParserFactory.newInstance() : new SAXParserFactoryImpl();
    spf.setNamespaceAware(namespaceAware);
    spf.setValidating(validating);

    try {
      saxParser = spf.newSAXParser();
      parser = saxParser.getParser();
      documentHandler = null;
      dtdHandler = null;
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  /** Return the Catalog being used. */
  public Catalog getCatalog() {
    return catalogResolver.getCatalog();
  }

  /**
   * SAX Parser API.
   *
   * <p>Note that the JAXP 1.1ea2 parser crashes with an InternalError if
   * it encounters a system identifier that appears to be a relative URI
   * that begins with a slash. For example, the declaration:</p>
   *
   * <pre>
   * &lt;!DOCTYPE book SYSTEM "/path/to/dtd/on/my/system/docbookx.dtd">
   * </pre>
   *
   * <p>would cause such an error. As a convenience, this method catches
   * that error and prints an explanation. (Unfortunately, it's not possible
   * to identify the particular system identifier that causes the problem.)
   * </p>
   *
   * <p>The underlying error is forwarded after printing the explanatory
   * message. The message is only every printed once and if
   * <code>suppressExplanation</code> is set to <code>false</code> before
   * parsing, it will never be printed.</p>
   */
  public void parse(InputSource input)
    throws IOException,
           SAXException {
    setupParse(input.getSystemId());
    try {
      parser.parse(input);
    } catch (InternalError ie) {
      explain(input.getSystemId());
      throw ie;
    }
  }

  /** SAX Parser API.
   *
   * @see #parse(InputSource)
   */
  public void parse(String systemId)
    throws IOException,
           SAXException {
    setupParse(systemId);
    try {
      parser.parse(systemId);
    } catch (InternalError ie) {
      explain(systemId);
      throw ie;
    }
  }

  /** SAX Parser API. */
  public void setDocumentHandler(DocumentHandler handler) {
    documentHandler = handler;
  }

  /** SAX Parser API. */
  public void setDTDHandler(DTDHandler handler) {
    dtdHandler = handler;
  }

  /**
   * SAX Parser API.
   *
   * <p>The purpose of this class is to implement an entity resolver.
   * Attempting to set a different one is pointless (and ignored).</p>
   */
  public void setEntityResolver(EntityResolver resolver) {
    // nop
  }

  /** SAX Parser API. */
  public void setErrorHandler(ErrorHandler handler) {
    parser.setErrorHandler(handler);
  }

  /** SAX Parser API. */
  public void setLocale(Locale locale) throws SAXException {
    parser.setLocale(locale);
  }

  /** SAX DocumentHandler API. */
  public void characters(char[] ch, int start, int length)
    throws SAXException {
    if (documentHandler != null) {
      documentHandler.characters(ch,start,length);
    }
  }

  /** SAX DocumentHandler API. */
  public void endDocument() throws SAXException {
    if (documentHandler != null) {
      documentHandler.endDocument();
    }
  }

  /** SAX DocumentHandler API. */
  public void endElement(String name) throws SAXException {
    if (documentHandler != null) {
      documentHandler.endElement(name);
    }
  }

  /** SAX DocumentHandler API. */
  public void ignorableWhitespace(char[] ch, int start, int length)
    throws SAXException {
    if (documentHandler != null) {
      documentHandler.ignorableWhitespace(ch,start,length);
    }
  }

  /** SAX DocumentHandler API. */
  public void processingInstruction(String target, String pidata)
    throws SAXException {

    if (target.equals("oasis-xml-catalog")) {
      URL catalog = null;
      String data = pidata;

      int pos = data.indexOf("catalog=");
      if (pos >= 0) {
        data = data.substring(pos+8);
        if (data.length() > 1) {
          String quote = data.substring(0,1);
          data = data.substring(1);
          pos = data.indexOf(quote);
          if (pos >= 0) {
            data = data.substring(0, pos);
            try {
              if (baseURL != null) {
                catalog = new URL(baseURL, data);
              } else {
                catalog = new URL(data);
              }
            } catch (MalformedURLException mue) {
              // nevermind
            }
          }
        }
      }

      if (allowXMLCatalogPI) {
        if (catalogManager.getAllowOasisXMLCatalogPI()) {
          catalogManager.debug.message(4,"oasis-xml-catalog PI", pidata);

          if (catalog != null) {
            try {
              catalogManager.debug.message(4,"oasis-xml-catalog", catalog.toString());
              oasisXMLCatalogPI = true;

              if (piCatalogResolver == null) {
                piCatalogResolver = new CatalogResolver(true);
              }

              piCatalogResolver.getCatalog().parseCatalog(catalog.toString());
            } catch (Exception e) {
              catalogManager.debug.message(3, "Exception parsing oasis-xml-catalog: "
                            + catalog.toString());
            }
          } else {
            catalogManager.debug.message(3, "PI oasis-xml-catalog unparseable: " + pidata);
          }
        } else {
          catalogManager.debug.message(4,"PI oasis-xml-catalog ignored: " + pidata);
        }
      } else {
        catalogManager.debug.message(3, "PI oasis-xml-catalog occurred in an invalid place: "
                      + pidata);
      }
    } else {
      if (documentHandler != null) {
        documentHandler.processingInstruction(target, pidata);
      }
    }
  }

  /** SAX DocumentHandler API. */
  public void setDocumentLocator(Locator locator) {
    if (documentHandler != null) {
      documentHandler.setDocumentLocator(locator);
    }
  }

  /** SAX DocumentHandler API. */
  public void startDocument() throws SAXException {
    if (documentHandler != null) {
      documentHandler.startDocument();
    }
  }

  /** SAX DocumentHandler API. */
  public void startElement(String name, AttributeList atts)
    throws SAXException {
    allowXMLCatalogPI = false;
    if (documentHandler != null) {
      documentHandler.startElement(name,atts);
    }
  }

  /** SAX DTDHandler API. */
  public void notationDecl (String name, String publicId, String systemId)
    throws SAXException {
    allowXMLCatalogPI = false;
    if (dtdHandler != null) {
      dtdHandler.notationDecl(name,publicId,systemId);
    }
  }

  /** SAX DTDHandler API. */
  public void unparsedEntityDecl (String name,
                                  String publicId,
                                  String systemId,
                                  String notationName)
    throws SAXException {
    allowXMLCatalogPI = false;
    if (dtdHandler != null) {
      dtdHandler.unparsedEntityDecl (name, publicId, systemId, notationName);
    }
  }

  /**
   * Implements the <code>resolveEntity</code> method
   * for the SAX interface, using an underlying CatalogResolver
   * to do the real work.
   */
  public InputSource resolveEntity (String publicId, String systemId) {
    allowXMLCatalogPI = false;
    String resolved = catalogResolver.getResolvedEntity(publicId, systemId);

    if (resolved == null && piCatalogResolver != null) {
      resolved = piCatalogResolver.getResolvedEntity(publicId, systemId);
    }

    if (resolved != null) {
      try {
        InputSource iSource = new InputSource(resolved);
        iSource.setPublicId(publicId);

        // Ideally this method would not attempt to open the
        // InputStream, but there is a bug (in Xerces, at least)
        // that causes the parser to mistakenly open the wrong
        // system identifier if the returned InputSource does
        // not have a byteStream.
        //
        // It could be argued that we still shouldn't do this here,
        // but since the purpose of calling the entityResolver is
        // almost certainly to open the input stream, it seems to
        // do little harm.
        //
        URL url = new URL(resolved);
        InputStream iStream = url.openStream();
        iSource.setByteStream(iStream);

        return iSource;
      } catch (Exception e) {
        catalogManager.debug.message(1, "Failed to create InputSource", resolved);
        return null;
      }
    } else {
      return null;
    }
  }

  /** Setup for parsing. */
  private void setupParse(String systemId) {
    allowXMLCatalogPI = true;
    parser.setEntityResolver(this);
    parser.setDocumentHandler(this);
    parser.setDTDHandler(this);

    URL cwd = null;

    try {
      cwd = FileURL.makeURL("basename");
    } catch (MalformedURLException mue) {
      cwd = null;
    }

    try {
      baseURL = new URL(systemId);
    } catch (MalformedURLException mue) {
      if (cwd != null) {
        try {
          baseURL = new URL(cwd, systemId);
        } catch (MalformedURLException mue2) {
          // give up
          baseURL = null;
        }
      } else {
        // give up
        baseURL = null;
      }
    }
  }

  /** Provide one possible explanation for an InternalError. */
  private void explain(String systemId) {
    if (!suppressExplanation) {
      System.out.println("Parser probably encountered bad URI in " + systemId);
      System.out.println("For example, replace '/some/uri' with 'file:/some/uri'.");
    }
  }
}
