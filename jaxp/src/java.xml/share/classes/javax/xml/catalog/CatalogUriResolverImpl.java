/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package javax.xml.catalog;

import com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl;
import java.io.StringReader;
import java.net.URL;
import java.util.Iterator;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * A SAX EntityResolver/JAXP URIResolver that uses catalogs.
 * <p>
 * This class implements both a SAX EntityResolver and a JAXP URIResolver.
 *
 *
 * @since 9
 */
final class CatalogUriResolverImpl implements CatalogUriResolver {

    Catalog catalog;
    CatalogResolverImpl entityResolver;

    /**
     * Construct an instance of the CatalogResolver from a Catalog.
     *
     * @param catalog A Catalog.
     */
    public CatalogUriResolverImpl(Catalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Source resolve(String href, String base) {
        if (href == null) return null;

        CatalogImpl c = (CatalogImpl)catalog;
        String uri = Normalizer.normalizeURI(href);
        String result;

        //remove fragment if any.
        int hashPos = uri.indexOf("#");
        if (hashPos >= 0) {
            uri = uri.substring(0, hashPos);
        }

        //search the current catalog
        result = resolve(c, uri);
        if (result == null) {
            GroupEntry.ResolveType resolveType = c.getResolve();
            switch (resolveType) {
                case IGNORE:
                    return new SAXSource(new InputSource(new StringReader("")));
                case STRICT:
                    CatalogMessages.reportError(CatalogMessages.ERR_NO_URI_MATCH,
                            new Object[]{href, base});
            }
            try {
                URL url = null;

                if (base == null) {
                    url = new URL(uri);
                    result = url.toString();
                } else {
                    URL baseURL = new URL(base);
                    url = (href.length() == 0 ? baseURL : new URL(baseURL, uri));
                    result = url.toString();
                }
            } catch (java.net.MalformedURLException mue) {
                    CatalogMessages.reportError(CatalogMessages.ERR_CREATING_URI,
                            new Object[]{href, base});
            }
        }

        SAXSource source = new SAXSource();
        source.setInputSource(new InputSource(result));
        setEntityResolver(source);
        return source;
    }

    /**
     * Resolves the publicId or systemId to one specified in the catalog.
     * @param catalog the catalog
     * @param href an href attribute, which may be relative or absolute
     * @return the resolved systemId if a match is found, null otherwise
     */
    String resolve(CatalogImpl catalog, String href) {
        String result = null;

        //search the current catalog
        catalog.reset();
        if (href != null) {
            result = catalog.matchURI(href);
        }

        //mark the catalog as having been searched before trying alternatives
        catalog.markAsSearched();

        //search alternative catalogs
        if (result == null) {
            Iterator<Catalog> iter = catalog.catalogs().iterator();
            while (iter.hasNext()) {
                result = resolve((CatalogImpl)iter.next(), href);
                if (result != null) {
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Establish an entityResolver for newly resolved URIs.
     * <p>
     * This is called from the URIResolver to set an EntityResolver on the SAX
     * parser to be used for new XML documents that are encountered as a result
     * of the document() function, xsl:import, or xsl:include. This is done
     * because the XSLT processor calls out to the SAXParserFactory itself to
     * create a new SAXParser to parse the new document. The new parser does not
     * automatically inherit the EntityResolver of the original (although
     * arguably it should). Quote from JAXP specification on Class
     * SAXTransformerFactory:
     * <p>
     * {@code If an application wants to set the ErrorHandler or EntityResolver
     * for an XMLReader used during a transformation, it should use a URIResolver
     * to return the SAXSource which provides (with getXMLReader) a reference to
     * the XMLReader}
     *
     */
    private void setEntityResolver(SAXSource source) {
        XMLReader reader = source.getXMLReader();
        if (reader == null) {
            SAXParserFactory spFactory = new SAXParserFactoryImpl();
            spFactory.setNamespaceAware(true);
            try {
                reader = spFactory.newSAXParser().getXMLReader();
            } catch (ParserConfigurationException | SAXException ex) {
                CatalogMessages.reportRunTimeError(CatalogMessages.ERR_PARSER_CONF, ex);
            }
        }
        if (entityResolver != null) {
            entityResolver = new CatalogResolverImpl(catalog);
        }
        reader.setEntityResolver(entityResolver);
        source.setXMLReader(reader);
    }
}
