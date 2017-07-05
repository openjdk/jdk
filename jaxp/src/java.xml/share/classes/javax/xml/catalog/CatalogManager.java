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


/**
 * The Catalog Manager manages the creation of XML Catalogs and Catalog Resolvers.
 *
 * @since 9
 */
public final class CatalogManager {
    /**
     * Creating CatalogManager instance is not allowed.
     */
    private CatalogManager() {
    }

    /**
     * Creates a {@code Catalog} object using the specified feature settings and
     * path to one or more catalog files.
     * <p>
     * If {@code paths} is empty, system property {@code javax.xml.catalog.files}
     * will be read to locate the initial list of catalog files.
     * <p>
     * If more than one catalog files are specified through the paths argument or
     * {@code javax.xml.catalog.files} property, the first entry is considered
     * the main catalog, while others are treated as alternative catalogs after
     * those referenced by the {@code nextCatalog} elements in the main catalog.
     * <p>
     * As specified in
     * <a href="https://www.oasis-open.org/committees/download.php/14809/xml-catalogs.html#s.res.fail">
     * XML Catalogs, OASIS Standard V1.1</a>, invalid path entries will be ignored.
     * No error will be reported. In case all entries are invalid, the resolver
     * will return as no mapping is found.
     *
     * @param features the catalog features
     * @param paths path(s) to one or more catalogs.
     *
     * @return an instance of a {@code Catalog}
     * @throws CatalogException If an error occurs while parsing the catalog
     */
    public static Catalog catalog(CatalogFeatures features, String... paths) {
        return new CatalogImpl(features, paths);
    }

    /**
     * Creates an instance of a {@code CatalogResolver} using the specified catalog.
     *
     * @param catalog the catalog instance
     * @return an instance of a {@code CatalogResolver}
     */
    public static CatalogResolver catalogResolver(Catalog catalog) {
        if (catalog == null) CatalogMessages.reportNPEOnNull("catalog", null);
        return new CatalogResolverImpl(catalog);
    }

    /**
     * Creates an instance of a {@code CatalogUriResolver} using the specified catalog.
     *
     * @param catalog the catalog instance
     * @return an instance of a {@code CatalogResolver}
     */
    public static CatalogUriResolver catalogUriResolver(Catalog catalog) {
        if (catalog == null) CatalogMessages.reportNPEOnNull("catalog", null);
        return new CatalogUriResolverImpl(catalog);
    }

    /**
     * Creates an instance of a {@code CatalogResolver} using the specified feature
     * settings and path to one or more catalog files.
     * <p>
     * If {@code paths} is empty, system property {@code javax.xml.catalog.files}
     * will be read to locate the initial list of catalog files.
     * <p>
     * If more than one catalog files are specified through the paths argument or
     * {@code javax.xml.catalog.files} property, the first entry is considered
     * the main catalog, while others are treated as alternative catalogs after
     * those referenced by the {@code nextCatalog} elements in the main catalog.
     * <p>
     * As specified in
     * <a href="https://www.oasis-open.org/committees/download.php/14809/xml-catalogs.html#s.res.fail">
     * XML Catalogs, OASIS Standard V1.1</a>, invalid path entries will be ignored.
     * No error will be reported. In case all entries are invalid, the resolver
     * will return as no mapping is found.
     *
     * @param features the catalog features
     * @param paths the path(s) to one or more catalogs
     *
     * @return an instance of a {@code CatalogResolver}
     * @throws CatalogException If an error occurs while parsing the catalog
     */
    public static CatalogResolver catalogResolver(CatalogFeatures features, String... paths) {
        Catalog catalog = catalog(features, paths);
        return new CatalogResolverImpl(catalog);
    }

    /**
     * Creates an instance of a {@code CatalogUriResolver} using the specified
     * feature settings and path to one or more catalog files.
     * <p>
     * If {@code paths} is empty, system property {@code javax.xml.catalog.files}
     * will be read to locate the initial list of catalog files.
     * <p>
     * If more than one catalog files are specified through the paths argument or
     * {@code javax.xml.catalog.files} property, the first entry is considered
     * the main catalog, while others are treated as alternative catalogs after
     * those referenced by the {@code nextCatalog} elements in the main catalog.
     * <p>
     * As specified in
     * <a href="https://www.oasis-open.org/committees/download.php/14809/xml-catalogs.html#s.res.fail">
     * XML Catalogs, OASIS Standard V1.1</a>, invalid path entries will be ignored.
     * No error will be reported. In case all entries are invalid, the resolver
     * will return as no mapping is found.
     *
     * @param features the catalog features
     * @param paths the path(s) to one or more catalogs
     *
     * @return an instance of a {@code CatalogUriResolver}
     * @throws CatalogException If an error occurs while parsing the catalog
     */
    public static CatalogUriResolver catalogUriResolver(CatalogFeatures features, String... paths) {
        Catalog catalog = catalog(features, paths);
        return new CatalogUriResolverImpl(catalog);
    }
}
