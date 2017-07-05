/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package catalog;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import javax.xml.catalog.CatalogUriResolver;

/*
 * Utilities for testing XML Catalog API.
 */
final class CatalogTestUtils {

    /* catalog files */
    static final String CATALOG_PUBLIC = "public.xml";
    static final String CATALOG_SYSTEM = "system.xml";
    static final String CATALOG_URI = "uri.xml";

    /* features */
    static final String FEATURE_FILES = "javax.xml.catalog.files";
    static final String FEATURE_PREFER = "javax.xml.catalog.prefer";
    static final String FEATURE_DEFER = "javax.xml.catalog.defer";
    static final String FEATURE_RESOLVE = "javax.xml.catalog.resolve";

    /* values of prefer feature */
    static final String PREFER_SYSTEM = "system";
    static final String PREFER_PUBLIC = "public";

    /* values of defer feature */
    static final String DEFER_TRUE = "true";
    static final String DEFER_FALSE = "false";

    /* values of resolve feature */
    static final String RESOLVE_STRICT = "strict";
    static final String RESOLVE_CONTINUE = "continue";
    static final String RESOLVE_IGNORE = "ignore";

    private static final String JAXP_PROPS = "jaxp.properties";
    private static final String JAXP_PROPS_BAK = JAXP_PROPS + ".bak";

    /*
     * Force using slash as File separator as we always use cygwin to test in
     * Windows platform.
     */
    private static final String FILE_SEP = "/";

    private CatalogTestUtils() { }

    /* ********** create resolver ********** */

    /*
     * Creates CatalogResolver with a set of catalogs.
     */
    static CatalogResolver catalogResolver(String... catalogName) {
        return catalogResolver(CatalogFeatures.defaults(), catalogName);
    }

    /*
     * Creates CatalogResolver with a feature and a set of catalogs.
     */
    static CatalogResolver catalogResolver(CatalogFeatures features,
            String... catalogName) {
        return (catalogName == null) ?
                CatalogManager.catalogResolver(features) :
                CatalogManager.catalogResolver(features, getCatalogPaths(catalogName));
    }

    /*
     * Creates catalogUriResolver with a set of catalogs.
     */
    static CatalogUriResolver catalogUriResolver(String... catalogName) {
        return catalogUriResolver(CatalogFeatures.defaults(), catalogName);
    }

    /*
     * Creates catalogUriResolver with a feature and a set of catalogs.
     */
    static CatalogUriResolver catalogUriResolver(
            CatalogFeatures features, String... catalogName) {
        return (catalogName == null) ?
                CatalogManager.catalogUriResolver(features) :
                CatalogManager.catalogUriResolver(features, getCatalogPaths(catalogName));
    }

    // Gets the paths of the specified catalogs.
    private static String[] getCatalogPaths(String... catalogNames) {
        return catalogNames == null
                ? null
                : Stream.of(catalogNames).map(
                        catalogName -> getCatalogPath(catalogName)).collect(
                                Collectors.toList()).toArray(new String[0]);
    }

    // Gets the paths of the specified catalogs.
    static String getCatalogPath(String catalogName) {
        return catalogName == null
                ? null
                : getPathByClassName(CatalogTestUtils.class, "catalogFiles")
                        + catalogName;
    }

    /*
     * Acquire a full path string by given class name and relative path string.
     */
    private static String getPathByClassName(Class<?> clazz,
            String relativeDir) {
        String packageName = FILE_SEP
                + clazz.getPackage().getName().replaceAll("[.]", FILE_SEP);
        String javaSourcePath = System.getProperty("test.src").replaceAll(
                "\\" + File.separator, FILE_SEP) + packageName + FILE_SEP;
        String normalizedPath = Paths.get(javaSourcePath,
                relativeDir).normalize().toAbsolutePath().toString();
        return normalizedPath.replace("\\", FILE_SEP) + FILE_SEP;
    }

    /* ********** jaxp.properties ********** */

    /*
     * Generates the jaxp.properties with the specified content.
     */
    static void generateJAXPProps(String content) throws IOException {
        Path filePath = getJAXPPropsPath();
        Path bakPath = filePath.resolveSibling(JAXP_PROPS_BAK);
        if (Files.exists(filePath) && !Files.exists(bakPath)) {
            Files.move(filePath, bakPath);
        }

        Files.write(filePath, content.getBytes());
    }

    /*
     * Deletes the jaxp.properties.
     */
    static void deleteJAXPProps() throws IOException {
        Path filePath = getJAXPPropsPath();
        Files.delete(filePath);
        Path bakFilePath = filePath.resolveSibling(JAXP_PROPS_BAK);
        if (Files.exists(bakFilePath)) {
            Files.move(bakFilePath, filePath);
        }
    }

    /*
     * Gets the path of jaxp.properties.
     */
    private static Path getJAXPPropsPath() {
        return Paths.get(System.getProperty("java.home") + File.separator
                + "conf" + File.separator + JAXP_PROPS);
    }

    /*
     * Creates the content of properties file with the specified
     * property-value pairs.
     */
    static String createPropsContent(Map<String, String> props) {
        return props.entrySet().stream().map(
                entry -> String.format("%s=%s%n", entry.getKey(),
                        entry.getValue())).reduce(
                                (line1, line2) -> line1 + line2).get();
    }
}
