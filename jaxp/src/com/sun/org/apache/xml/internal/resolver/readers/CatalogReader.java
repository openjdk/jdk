/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
// CatalogReader.java - An interface for reading catalog files

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

import java.io.IOException;
import java.net.MalformedURLException;
import com.sun.org.apache.xml.internal.resolver.CatalogException;

import java.io.InputStream;
import com.sun.org.apache.xml.internal.resolver.Catalog;

/**
 * The CatalogReader interface.
 *
 * <p>The Catalog class requires that classes implement this interface
 * in order to be used to read catalogs. Examples of CatalogReaders
 * include the TextCatalogReader, the SAXCatalogReader, and the
 * DOMCatalogReader.</p>
 *
 * @see Catalog
 *
 * @author Norman Walsh
 * <a href="mailto:Norman.Walsh@Sun.COM">Norman.Walsh@Sun.COM</a>
 *
 */
public interface CatalogReader {
    /**
     * Read a catalog from a file.
     *
     * <p>This class reads a catalog from a URL.</p>
     *
     * @param catalog The catalog for which this reader is called.
     * @param fileUrl The URL of a document to be read.
     * @throws MalformedURLException if the specified URL cannot be
     * turned into a URL object.
     * @throws IOException if the URL cannot be read.
     * @throws UnknownCatalogFormatException if the catalog format is
     * not recognized.
     * @throws UnparseableCatalogException if the catalog cannot be parsed.
     * (For example, if it is supposed to be XML and isn't well-formed.)
     */
    public void readCatalog(Catalog catalog, String fileUrl)
      throws MalformedURLException, IOException, CatalogException;

    /**
     * Read a catalog from an input stream.
     *
     * <p>This class reads a catalog from an input stream.</p>
     *
     * @param catalog The catalog for which this reader is called.
     * @param is The input stream that is to be read.
     * @throws IOException if the URL cannot be read.
     * @throws UnknownCatalogFormatException if the catalog format is
     * not recognized.
     * @throws UnparseableCatalogException if the catalog cannot be parsed.
     * (For example, if it is supposed to be XML and isn't well-formed.)
     */
    public void readCatalog(Catalog catalog, InputStream is)
        throws IOException, CatalogException;
}
