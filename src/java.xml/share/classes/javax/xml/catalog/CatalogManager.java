/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;

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
     * uri(s) to one or more catalog files.
     * <p>
     * If {@code uris} is empty, system property {@code javax.xml.catalog.files},
     * as defined in {@link CatalogFeatures}, will be read to locate the initial
     * list of catalog files.
     * <p>
     * If multiple catalog files are specified through the {@code uris} argument or
     * {@code javax.xml.catalog.files} property, the first entry is considered
     * the main catalog, while others are treated as alternative catalogs after
     * those referenced by the {@code nextCatalog} elements in the main catalog.
     * <p>
     * As specified in
     * <a href="https://www.oasis-open.org/committees/download.php/14809/xml-catalogs.html#s.res.fail">
     * XML Catalogs, OASIS Standard V1.1</a>, if a catalog entry is invalid, it
     * is ignored. In case all entries are invalid, the resulting Catalog object
     * will contain no Catalog elements. Any matching operation using the Catalog
     * will return null.
     *
     * @param features the catalog features
     * @param uris uri(s) to one or more catalogs.
     *
     * @return an instance of a {@code Catalog}
     * @throws IllegalArgumentException if either the URIs are not absolute
     * or do not have a URL protocol handler for the URI scheme
     * @throws CatalogException If an error occurs while parsing the catalog
     */
    public static Catalog catalog(CatalogFeatures features, URI... uris) {
        Util.validateUrisSyntax(uris);
        CatalogImpl catalog = new CatalogImpl(features, uris);
        catalog.load();
        return catalog;
    }

    /**
     * Creates an instance of a {@code CatalogResolver} using the specified catalog.
     *
     * @apiNote The {@code CatalogResolver} created by this method delegates to
     * the underlying {@code catalog}'s RESOLVE property. The {@code CatalogResolver}
     * created by {@link #catalogResolver(Catalog, CatalogResolver.NotFoundAction)
     * catalogResover(Catalog, CatalogResolver.NotFoundAction)} is based on the
     * specified action type when it is unable to resolve a reference.
     *
     * @param catalog the catalog instance
     * @return an instance of a {@code CatalogResolver}
     */
    public static CatalogResolver catalogResolver(Catalog catalog) {
        if (catalog == null) CatalogMessages.reportNPEOnNull("catalog", null);
        return new CatalogResolverImpl(catalog);
    }

    /**
     * Creates a {@code CatalogResolver} that resolves external references with the given
     * {@code catalog} and {@link CatalogResolver.NotFoundAction action} type
     * that determines the behavior when unable to resolve a reference.
     * <p>
     * The {@link CatalogResolver.NotFoundAction action} types are mapped to the values
     * of the {@link CatalogFeatures.Feature#RESOLVE RESOLVE} property.
     *
     * @param catalog the catalog instance
     * @param action the action to be taken when unable to resolve a reference
     *
     * @return a {@code CatalogResolver} with the {@code catalog} and {@code action} type
     *
     * @since 22
     */
    public static CatalogResolver catalogResolver(Catalog catalog, CatalogResolver.NotFoundAction action) {
        if (catalog == null) CatalogMessages.reportNPEOnNull("catalog", null);
        if (action == null) CatalogMessages.reportNPEOnNull("action", null);

        return new CatalogResolverImpl(catalog, action);
    }

    /**
     * Creates an instance of a {@code CatalogResolver} using the specified feature
     * settings and uri(s) to one or more catalog files.
     * <p>
     * If {@code uris} is empty, system property {@code javax.xml.catalog.files},
     * as defined in {@link CatalogFeatures}, will be read to locate the initial
     * list of catalog files.
     * <p>
     * If multiple catalog files are specified through the {@code uris} argument or
     * {@code javax.xml.catalog.files} property, the first entry is considered
     * the main catalog, while others are treated as alternative catalogs after
     * those referenced by the {@code nextCatalog} elements in the main catalog.
     * <p>
     * As specified in
     * <a href="https://www.oasis-open.org/committees/download.php/14809/xml-catalogs.html#s.res.fail">
     * XML Catalogs, OASIS Standard V1.1</a>, if a catalog entry is invalid, it
     * is ignored. In case all entries are invalid, the resulting CatalogResolver
     * object will contain no valid catalog. Any resolution operation using the
     * resolver therefore will return as no mapping is found. See {@link CatalogResolver}
     * for the behavior when no mapping is found.
     *
     * @param features the catalog features
     * @param uris the uri(s) to one or more catalogs
     *
     * @return an instance of a {@code CatalogResolver}
     * @throws IllegalArgumentException if either the URIs are not absolute
     * or do not have a URL protocol handler for the URI scheme
     * @throws CatalogException If an error occurs while parsing the catalog
     */
    public static CatalogResolver catalogResolver(CatalogFeatures features, URI... uris) {
        Catalog catalog = catalog(features, uris);
        return new CatalogResolverImpl(catalog);
    }
}
