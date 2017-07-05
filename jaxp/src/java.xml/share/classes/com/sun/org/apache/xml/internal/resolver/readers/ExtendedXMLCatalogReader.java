/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.util.Vector;
import com.sun.org.apache.xml.internal.resolver.Catalog;
import com.sun.org.apache.xml.internal.resolver.Resolver;
import com.sun.org.apache.xml.internal.resolver.CatalogEntry;
import com.sun.org.apache.xml.internal.resolver.CatalogException;

import org.xml.sax.*;
import org.w3c.dom.*;

/**
 * Parse Extended OASIS Entity Resolution Technical Committee
 * XML Catalog files.
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public class ExtendedXMLCatalogReader extends OASISXMLCatalogReader {
  /** The namespace name of extended catalog elements */
  public static final String extendedNamespaceName = "http://nwalsh.com/xcatalog/1.0";

  /**
   * The SAX <code>startElement</code> method recognizes elements
   * from the plain catalog format and instantiates CatalogEntry
   * objects for them.
   *
   * @param namespaceURI The namespace name of the element.
   * @param localName The local name of the element.
   * @param qName The QName of the element.
   * @param atts The list of attributes on the element.
   *
   * @see CatalogEntry
   */
  public void startElement (String namespaceURI,
                            String localName,
                            String qName,
                            Attributes atts)
    throws SAXException {

    // Check before calling the super because super will report our
    // namespace as an extension namespace, but that doesn't count
    // for this element.
    boolean inExtension = inExtensionNamespace();

    super.startElement(namespaceURI, localName, qName, atts);

    int entryType = -1;
    Vector entryArgs = new Vector();

    if (namespaceURI != null && extendedNamespaceName.equals(namespaceURI)
        && !inExtension) {
      // This is an Extended XML Catalog entry

      if (atts.getValue("xml:base") != null) {
        String baseURI = atts.getValue("xml:base");
        entryType = Catalog.BASE;
        entryArgs.add(baseURI);
        baseURIStack.push(baseURI);

        debug.message(4, "xml:base", baseURI);

        try {
          CatalogEntry ce = new CatalogEntry(entryType, entryArgs);
          catalog.addEntry(ce);
        } catch (CatalogException cex) {
          if (cex.getExceptionType() == CatalogException.INVALID_ENTRY_TYPE) {
            debug.message(1, "Invalid catalog entry type", localName);
          } else if (cex.getExceptionType() == CatalogException.INVALID_ENTRY) {
            debug.message(1, "Invalid catalog entry (base)", localName);
          }
        }

        entryType = -1;
        entryArgs = new Vector();
      } else {
        baseURIStack.push(baseURIStack.peek());
      }

      if (localName.equals("uriSuffix")) {
        if (checkAttributes(atts, "suffix", "uri")) {
          entryType = Resolver.URISUFFIX;
          entryArgs.add(atts.getValue("suffix"));
          entryArgs.add(atts.getValue("uri"));

          debug.message(4, "uriSuffix",
                        atts.getValue("suffix"),
                        atts.getValue("uri"));
        }
      } else if (localName.equals("systemSuffix")) {
        if (checkAttributes(atts, "suffix", "uri")) {
          entryType = Resolver.SYSTEMSUFFIX;
          entryArgs.add(atts.getValue("suffix"));
          entryArgs.add(atts.getValue("uri"));

          debug.message(4, "systemSuffix",
                        atts.getValue("suffix"),
                        atts.getValue("uri"));
        }
      } else {
        // This is equivalent to an invalid catalog entry type
        debug.message(1, "Invalid catalog entry type", localName);
      }

      if (entryType >= 0) {
        try {
          CatalogEntry ce = new CatalogEntry(entryType, entryArgs);
          catalog.addEntry(ce);
        } catch (CatalogException cex) {
          if (cex.getExceptionType() == CatalogException.INVALID_ENTRY_TYPE) {
            debug.message(1, "Invalid catalog entry type", localName);
          } else if (cex.getExceptionType() == CatalogException.INVALID_ENTRY) {
            debug.message(1, "Invalid catalog entry", localName);
          }
        }
      }
    }
  }

  /** The SAX <code>endElement</code> method does nothing. */
  public void endElement (String namespaceURI,
                          String localName,
                          String qName)
    throws SAXException {

    super.endElement(namespaceURI, localName, qName);

    // Check after popping the stack so we don't erroneously think we
    // are our own extension namespace...
    boolean inExtension = inExtensionNamespace();

    int entryType = -1;
    Vector entryArgs = new Vector();

    if (namespaceURI != null
        && (extendedNamespaceName.equals(namespaceURI))
        && !inExtension) {

      String popURI = (String) baseURIStack.pop();
      String baseURI = (String) baseURIStack.peek();

      if (!baseURI.equals(popURI)) {
        entryType = Catalog.BASE;
        entryArgs.add(baseURI);

        debug.message(4, "(reset) xml:base", baseURI);

        try {
          CatalogEntry ce = new CatalogEntry(entryType, entryArgs);
          catalog.addEntry(ce);
        } catch (CatalogException cex) {
          if (cex.getExceptionType() == CatalogException.INVALID_ENTRY_TYPE) {
            debug.message(1, "Invalid catalog entry type", localName);
          } else if (cex.getExceptionType() == CatalogException.INVALID_ENTRY) {
            debug.message(1, "Invalid catalog entry (rbase)", localName);
          }
        }
      }
    }
  }
}
