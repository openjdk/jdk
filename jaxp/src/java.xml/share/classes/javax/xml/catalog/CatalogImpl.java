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
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static javax.xml.catalog.BaseEntry.CatalogEntryType;
import static javax.xml.catalog.CatalogFeatures.DEFER_TRUE;
import javax.xml.catalog.CatalogFeatures.Feature;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.SAXException;

/**
 * Implementation of the Catalog.
 *
 * @since 9
 */
class CatalogImpl extends GroupEntry implements Catalog {

    //Catalog level, 0 means the top catalog
    int level = 0;

    //Value of the defer attribute to determine if alternative catalogs are read
    boolean isDeferred = true;

    //Value of the resolve attribute
    ResolveType resolveType = ResolveType.STRICT;

    //indicate whether the Catalog is empty
    boolean isEmpty;

    //Current parsed Catalog
    int current = 0;

    //System Id for this catalog
    String systemId;

    //The parent
    CatalogImpl parent = null;

    /*
     A list of catalog entry files from the input, excluding the current catalog.
     Paths in the List are normalized.
     */
    List<String> inputFiles;

    //A list of catalogs specified using the nextCatalog element
    List<NextCatalog> nextCatalogs;

    //reuse the parser
    SAXParser parser;

    /**
     * Construct a Catalog with specified path.
     *
     * @param file The path to a catalog file.
     * @throws CatalogException If an error happens while parsing the specified
     * catalog file.
     */
    public CatalogImpl(CatalogFeatures f, String... file) throws CatalogException {
        this(null, f, file);
    }

    /**
     * Construct a Catalog with specified path.
     *
     * @param parent The parent catalog
     * @param file The path to a catalog file.
     * @throws CatalogException If an error happens while parsing the specified
     * catalog file.
     */
    public CatalogImpl(CatalogImpl parent, CatalogFeatures f, String... file) throws CatalogException {
        super(CatalogEntryType.CATALOG);
        this.parent = parent;
        if (parent == null) {
            level = 0;
        } else {
            level = parent.level + 1;
        }
        if (f == null) {
            this.features = CatalogFeatures.defaults();
        } else {
            this.features = f;
        }
        setPrefer(features.get(Feature.PREFER));
        setDeferred(features.get(Feature.DEFER));
        setResolve(features.get(Feature.RESOLVE));

        //Path of catalog files
        String[] catalogFile = file;
        if (level == 0
                && (file == null || (file.length == 0 || file[0] == null))) {
            String files = features.get(Feature.FILES);
            if (files != null) {
                catalogFile = files.split(";[ ]*");
            }
        }

        /*
         In accordance with 8. Resource Failures of the Catalog spec, missing
         Catalog entry files are to be ignored.
         */
        if ((catalogFile != null && catalogFile.length > 0)) {
            int start = 0;
            URI uri = null;
            for (String temp : catalogFile) {
                uri = getSystemId(temp);
                start++;
                if (verifyCatalogFile(uri)) {
                    systemId = uri.toASCIIString();
                    break;
                }
            }

            //Save the rest of input files as alternative catalogs
            if (level == 0 && catalogFile.length > start) {
                inputFiles = new ArrayList<>();
                for (int i = start; i < catalogFile.length; i++) {
                    if (catalogFile[i] != null) {
                        inputFiles.add(catalogFile[i]);
                    }
                }
            }

            if (systemId != null) {
                parse(systemId);
            }
        }
    }

    /**
     * Resets the Catalog instance to its initial state.
     */
    @Override
    public void reset() {
        super.reset();
        current = 0;
        if (level == 0) {
            catalogsSearched.clear();
        }
        entries.stream().filter((entry) -> (entry.type == CatalogEntryType.GROUP)).forEach((entry) -> {
            ((GroupEntry) entry).reset();
        });

        if (parent != null) {
            this.loadedCatalogs = parent.loadedCatalogs;
            this.catalogsSearched = parent.catalogsSearched;
        }
    }

    /**
     * Returns whether this Catalog instance is the top (main) Catalog.
     *
     * @return true if the instance is the top Catalog, false otherwise
     */
    boolean isTop() {
        return level == 0;
    }

    /**
     * Gets the parent of this catalog.
     *
     * @returns The parent catalog
     */
    public Catalog getParent() {
        return this.parent;
    }

    /**
     * Sets the defer property. If the value is null or empty, or any String
     * other than the defined, it will be assumed as the default value.
     *
     * @param value The value of the defer attribute
     */
    public final void setDeferred(String value) {
        isDeferred = DEFER_TRUE.equals(value);
    }

    /**
     * Queries the defer attribute
     *
     * @return true if the prefer attribute is set to system, false if not.
     */
    public boolean isDeferred() {
        return isDeferred;
    }

    /**
     * Sets the resolve property. If the value is null or empty, or any String
     * other than the defined, it will be assumed as the default value.
     *
     * @param value The value of the resolve attribute
     */
    public final void setResolve(String value) {
        resolveType = ResolveType.getType(value);
    }

    /**
     * Gets the value of the resolve attribute
     *
     * @return The value of the resolve attribute
     */
    public final ResolveType getResolve() {
        return resolveType;
    }

    /**
     * Marks the Catalog as being searched already.
     */
    void markAsSearched() {
        catalogsSearched.add(systemId);
    }

    /**
     * Parses the catalog.
     *
     * @param systemId The systemId of the catalog
     * @throws CatalogException if parsing the catalog failed
     */
    private void parse(String systemId) {
        if (parser == null) {
            parser = getParser();
        }

        try {
            CatalogReader reader = new CatalogReader(this, parser);
            parser.parse(systemId, reader);
        } catch (SAXException | IOException ex) {
            CatalogMessages.reportRunTimeError(CatalogMessages.ERR_PARSING_FAILED, ex);
        }
    }

    /**
     * Resolves the specified file path to an absolute systemId. If it is
     * relative, it shall be resolved using the base or user.dir property if
     * base is not specified.
     *
     * @param file The specified file path
     * @return The systemId of the file
     * @throws CatalogException if the specified file path can not be converted
     * to a system id
     */
    private URI getSystemId(String file) {
        URL filepath;
        if (file != null && file.length() > 0) {
            try {
                File f = new File(file);
                if (baseURI != null && !f.isAbsolute()) {
                    filepath = new URL(baseURI, fixSlashes(file));
                    return filepath.toURI();
                } else {
                    return resolveURI(file);
                }
            } catch (MalformedURLException | URISyntaxException e) {
                CatalogMessages.reportRunTimeError(CatalogMessages.ERR_INVALID_PATH,
                        new Object[]{file}, e);
            }
        }
        return null;
    }

    /**
     * Resolves the specified uri. If the uri is relative, makes it absolute by
     * the user.dir directory.
     *
     * @param uri The specified URI.
     * @return The resolved URI
     */
    private URI resolveURI(String uri) throws MalformedURLException {
        if (uri == null) {
            uri = "";
        }

        URI temp = toURI(uri);
        String str = temp.toASCIIString();
        String base = str.substring(0, str.lastIndexOf('/') + 1);
        baseURI = new URL(str);

        return temp;
    }

    /**
     * Converts an URI string or file path to URI.
     *
     * @param uri an URI string or file path
     * @return an URI
     */
    private URI toURI(String uri) {
        URI temp = null;
        try {
            URL url = new URL(uri);
            temp = url.toURI();
        } catch (MalformedURLException | URISyntaxException mue) {
            File file = new File(uri);
            temp = file.toURI();
        }
        return temp;
    }

    /**
     * Returns a SAXParser instance
     * @return a SAXParser instance
     * @throws CatalogException if constructing a SAXParser failed
     */
    private SAXParser getParser() {
        SAXParser p = null;
        try {
            SAXParserFactory spf = new SAXParserFactoryImpl();
            spf.setNamespaceAware(true);
            spf.setValidating(false);
            spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            p = spf.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            CatalogMessages.reportRunTimeError(CatalogMessages.ERR_PARSING_FAILED, e);
        }
        return p;
    }

    /**
     * Indicate that the catalog is empty
     *
     * @return True if the catalog is empty; False otherwise.
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public Stream<Catalog> catalogs() {
        Iterator<Catalog> iter = new Iterator<Catalog>() {
            Catalog nextCatalog = null;

            //Current index of the input files
            int inputFilesIndex = 0;

            //Next catalog
            int nextCatalogIndex = 0;

            @Override
            public boolean hasNext() {
                if (nextCatalog != null) {
                    return true;
                } else {
                    nextCatalog = nextCatalog();
                    return (nextCatalog != null);
                }
            }

            @Override
            public Catalog next() {
                if (nextCatalog != null || hasNext()) {
                    Catalog catalog = nextCatalog;
                    nextCatalog = null;
                    return catalog;
                } else {
                    throw new NoSuchElementException();
                }
            }

            /**
             * Returns the next alternative catalog.
             *
             * @return the next catalog if any
             */
            private Catalog nextCatalog() {
                Catalog c = null;

                //Check those specified in nextCatalogs
                if (nextCatalogs != null) {
                    while (c == null && nextCatalogIndex < nextCatalogs.size()) {
                        c = getCatalog(nextCatalogs.get(nextCatalogIndex++).getCatalogURI());
                    }
                }

                //Check the input list
                if (c == null && inputFiles != null) {
                    while (c == null && inputFilesIndex < inputFiles.size()) {
                        c = getCatalog(getSystemId(inputFiles.get(inputFilesIndex++)));
                    }
                }

                return c;
            }
        };

        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iter, Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /**
     * Adds the catalog to the nextCatalog list
     *
     * @param catalog a catalog specified in a nextCatalog entry
     */
    void addNextCatalog(NextCatalog catalog) {
        if (catalog == null) {
            return;
        }

        if (nextCatalogs == null) {
            nextCatalogs = new ArrayList<>();
        }

        nextCatalogs.add(catalog);
    }

    /**
     * Loads all alternative catalogs.
     */
    void loadNextCatalogs() {
        //loads catalogs specified in nextCatalogs
        if (nextCatalogs != null) {
            for (NextCatalog next : nextCatalogs) {
                getCatalog(next.getCatalogURI());
            }
        }

        //loads catalogs from the input list
        if (inputFiles != null) {
            for (String file : inputFiles) {
                getCatalog(getSystemId(file));
            }
        }
    }

    /**
     * Returns a Catalog object by the specified path.
     *
     * @param path the path to a catalog
     * @return a Catalog object
     */
    Catalog getCatalog(URI uri) {
        if (uri == null) {
            return null;
        }

        Catalog c = null;
        String path = uri.toASCIIString();

        if (verifyCatalogFile(uri)) {
            c = getLoadedCatalog(path);
            if (c == null) {
                c = new CatalogImpl(this, features, path);
                saveLoadedCatalog(path, c);
            }
        }
        return c;
    }

    /**
     * Saves a loaded Catalog.
     *
     * @param catalogId the catalogId associated with the Catalog object
     * @param c the Catalog to be saved
     */
    void saveLoadedCatalog(String catalogId, Catalog c) {
        loadedCatalogs.put(catalogId, c);
    }

    /**
     * Returns a count of all loaded catalogs, including delegate catalogs.
     *
     * @return a count of all loaded catalogs
     */
    int loadedCatalogCount() {
        return loadedCatalogs.size() + delegateCatalogs.size();
    }
}
