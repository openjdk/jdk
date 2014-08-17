/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// DOMCatalogReader.java - Read XML Catalog files

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

package com.sun.org.apache.xml.internal.resolver.readers;

import com.sun.org.apache.xml.internal.resolver.Catalog;
import com.sun.org.apache.xml.internal.resolver.CatalogException;
import com.sun.org.apache.xml.internal.resolver.helpers.Namespaces;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import sun.reflect.misc.ReflectUtil;

/**
 * A DOM-based CatalogReader.
 *
 * <p>This class is used to read XML Catalogs using the DOM. This reader
 * has an advantage over the SAX-based reader that it can analyze the
 * DOM tree rather than simply a series of SAX events. It has the disadvantage
 * that it requires all of the code necessary to build and walk a DOM
 * tree.</p>
 *
 * <p>Since the choice of CatalogReaders (in the InputStream case) can only
 * be made on the basis of MIME type, the following problem occurs: only
 * one CatalogReader can exist for all XML mime types. In order to get
 * around this problem, the DOMCatalogReader relies on a set of external
 * CatalogParsers to actually build the catalog.</p>
 *
 * <p>The selection of CatalogParsers is made on the basis of the QName
 * of the root element of the document.</p>
 *
 *
 * @see Catalog
 * @see CatalogReader
 * @see SAXCatalogReader
 * @see TextCatalogReader
 * @see DOMCatalogParser
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class DOMCatalogReader implements CatalogReader {
  /**
   * Mapping table from QNames to CatalogParser classes.
   *
   * <p>Each key in this hash table has the form "elementname"
   * or "{namespaceuri}elementname". The former is used if the
   * namespace URI is null.</p>
   */
  protected Hashtable namespaceMap = new Hashtable();

  /**
   * Add a new parser to the reader.
   *
   * <p>This method associates the specified parserClass with the
   * namespaceURI/rootElement names specified.</p>
   *
   * @param namespaceURI The namespace URI. <em>Not</em> the prefix.
   * @param rootElement The name of the root element.
   * @param parserClass The name of the parserClass to instantiate
   * for this kind of catalog.
   */
  public void setCatalogParser(String namespaceURI,
                               String rootElement,
                               String parserClass) {
    if (namespaceURI == null) {
      namespaceMap.put(rootElement, parserClass);
    } else {
      namespaceMap.put("{"+namespaceURI+"}"+rootElement, parserClass);
    }
  }

  /**
   * Get the name of the parser class for a given catalog type.
   *
   * <p>This method returns the parserClass associated with the
   * namespaceURI/rootElement names specified.</p>
   *
   * @param namespaceURI The namespace URI. <em>Not</em> the prefix.
   * @param rootElement The name of the root element.
   * @return The parser class.
   */
  public String getCatalogParser(String namespaceURI,
                                 String rootElement) {
    if (namespaceURI == null) {
      return (String) namespaceMap.get(rootElement);
    } else {
      return (String) namespaceMap.get("{"+namespaceURI+"}"+rootElement);
    }
  }

  /**
   * Null constructor; something for subclasses to call.
   */
  public DOMCatalogReader() { }

  /**
   * Read a catalog from an input stream.
   *
   * <p>This class reads a catalog from an input stream:</p>
   *
   * <ul>
   * <li>Based on the QName of the root element, it determines which
   * parser to instantiate for this catalog.</li>
   * <li>It constructs a DOM Document from the catalog and</li>
   * <li>For each child of the root node, it calls the parser's
   * parseCatalogEntry method. This method is expected to make
   * appropriate calls back into the catalog to add entries for the
   * entries in the catalog. It is free to do this in whatever manner
   * is appropriate (perhaps using just the node passed in, perhaps
   * wandering arbitrarily throughout the tree).</li>
   * </ul>
   *
   * @param catalog The catalog for which this reader is called.
   * @param is The input stream that is to be read.
   * @throws IOException if the URL cannot be read.
   * @throws UnknownCatalogFormatException if the catalog format is
   * not recognized.
   * @throws UnparseableCatalogException if the catalog cannot be parsed.
   * (For example, if it is supposed to be XML and isn't well-formed or
   * if the parser class cannot be instantiated.)
   */
  public void readCatalog(Catalog catalog, InputStream is)
    throws IOException, CatalogException {

    DocumentBuilderFactory factory = null;
    DocumentBuilder builder = null;

    factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setValidating(false);
    try {
      builder = factory.newDocumentBuilder();
    } catch (ParserConfigurationException pce) {
      throw new CatalogException(CatalogException.UNPARSEABLE);
    }

    Document doc = null;

    try {
      doc = builder.parse(is);
    } catch (SAXException se) {
      throw new CatalogException(CatalogException.UNKNOWN_FORMAT);
    }

    Element root = doc.getDocumentElement();

    String namespaceURI = Namespaces.getNamespaceURI(root);
    String localName    = Namespaces.getLocalName(root);

    String domParserClass = getCatalogParser(namespaceURI,
                                             localName);

    if (domParserClass == null) {
      if (namespaceURI == null) {
        catalog.getCatalogManager().debug.message(1, "No Catalog parser for "
                                                  + localName);
      } else {
        catalog.getCatalogManager().debug.message(1, "No Catalog parser for "
                                                  + "{" + namespaceURI + "}"
                                                  + localName);
      }
      return;
    }

    DOMCatalogParser domParser = null;

    try {
      domParser = (DOMCatalogParser) ReflectUtil.forName(domParserClass).newInstance();
    } catch (ClassNotFoundException cnfe) {
      catalog.getCatalogManager().debug.message(1, "Cannot load XML Catalog Parser class", domParserClass);
      throw new CatalogException(CatalogException.UNPARSEABLE);
    } catch (InstantiationException ie) {
      catalog.getCatalogManager().debug.message(1, "Cannot instantiate XML Catalog Parser class", domParserClass);
      throw new CatalogException(CatalogException.UNPARSEABLE);
    } catch (IllegalAccessException iae) {
      catalog.getCatalogManager().debug.message(1, "Cannot access XML Catalog Parser class", domParserClass);
      throw new CatalogException(CatalogException.UNPARSEABLE);
    } catch (ClassCastException cce ) {
      catalog.getCatalogManager().debug.message(1, "Cannot cast XML Catalog Parser class", domParserClass);
      throw new CatalogException(CatalogException.UNPARSEABLE);
    }

    Node node = root.getFirstChild();
    while (node != null) {
      domParser.parseCatalogEntry(catalog, node);
      node = node.getNextSibling();
    }
  }

  /**
   * Read the catalog behind the specified URL.
   *
   * @see #readCatalog(Catalog, InputStream)
   *
   * @param catalog The catalog for which we are reading.
   * @param fileUrl The URL of the document that should be read.
   *
   * @throws MalformedURLException if the specified URL cannot be
   * turned into a URL object.
   * @throws IOException if the URL cannot be read.
   * @throws UnknownCatalogFormatException if the catalog format is
   * not recognized.
   * @throws UnparseableCatalogException if the catalog cannot be parsed.
   * (For example, if it is supposed to be XML and isn't well-formed.)
   */
  public void readCatalog(Catalog catalog, String fileUrl)
    throws MalformedURLException, IOException, CatalogException {
    URL url = new URL(fileUrl);
    URLConnection urlCon = url.openConnection();
    readCatalog(catalog, urlCon.getInputStream());
  }
}
