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

import static catalog.CatalogTestUtils.DEFER_FALSE;
import static catalog.CatalogTestUtils.FEATURE_DEFER;
import static catalog.CatalogTestUtils.FEATURE_FILES;
import static catalog.CatalogTestUtils.FEATURE_PREFER;
import static catalog.CatalogTestUtils.FEATURE_RESOLVE;
import static catalog.CatalogTestUtils.PREFER_SYSTEM;
import static catalog.CatalogTestUtils.RESOLVE_CONTINUE;
import static catalog.CatalogTestUtils.catalogResolver;
import static catalog.CatalogTestUtils.catalogUriResolver;
import static catalog.CatalogTestUtils.createPropsContent;
import static catalog.CatalogTestUtils.deleteJAXPProps;
import static catalog.CatalogTestUtils.generateJAXPProps;
import static catalog.CatalogTestUtils.getCatalogPath;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.catalog.CatalogResolver;
import javax.xml.catalog.CatalogUriResolver;

/*
 * This case tests if the properties FILES, DEFER, PREFER, RESOLVE in
 * jaxp.properties and system properties could be cared.
 */
public class PropertiesTest {

    private static final String CATALOG_PROPERTIES = "properties.xml";

    public static void main(String[] args) throws Exception {
        System.out.println("testJAXPProperties started");
        testJAXPProperties();
        System.out.println("testJAXPProperties ended");

        System.out.println("testSystemProperties started");
        testSystemProperties();
        System.out.println("testSystemProperties ended");

        System.out.println("Test passed");
    }

    /*
     * Tests how does jaxp.properties affects the resolution.
     */
    private static void testJAXPProperties() throws IOException {
        generateJAXPProps(createJAXPPropsContent());
        testProperties();
        deleteJAXPProps();
    }

    /*
     * Tests how does system properties affects the resolution.
     */
    private static void testSystemProperties() {
        setSystemProperties();
        testProperties();
    }

    private static void testProperties() {
        testPropertiesOnEntityResolver();
        testPropertiesOnUriResolver();
    }

    private static void testPropertiesOnEntityResolver() {
        CatalogResolver entityResolver = catalogResolver((String[]) null);
        entityResolver.resolveEntity("-//REMOTE//DTD DOCDUMMY XML//EN",
                "http://remote/sys/dtd/docDummy.dtd");
        "http://local/base/dtd/docSys.dtd".equals(
                entityResolver.resolveEntity("-//REMOTE//DTD DOC XML//EN",
                        "http://remote/dtd/doc.dtd").getSystemId());
    }

    private static void testPropertiesOnUriResolver() {
        CatalogUriResolver uriResolver = catalogUriResolver((String[]) null);
        uriResolver.resolve("http://remote/uri/dtd/docDummy.dtd", null);
        "http://local/base/dtd/docURI.dtd".equals(uriResolver.resolve(
                "http://remote/dtd/doc.dtd", null).getSystemId());
    }

    // The properties in jaxp.properties don't use default values
    private static String createJAXPPropsContent() {
        Map<String, String> props = new HashMap<>();
        props.put(FEATURE_FILES, getCatalogPath(CATALOG_PROPERTIES));
        props.put(FEATURE_DEFER, DEFER_FALSE);
        props.put(FEATURE_PREFER, PREFER_SYSTEM);
        props.put(FEATURE_RESOLVE, RESOLVE_CONTINUE);
        return createPropsContent(props);
    }

    // The system properties don't use default values
    private static void setSystemProperties() {
        System.setProperty(FEATURE_FILES, getCatalogPath(CATALOG_PROPERTIES));
        System.setProperty(FEATURE_DEFER, DEFER_FALSE);
        System.setProperty(FEATURE_PREFER, PREFER_SYSTEM);
        System.setProperty(FEATURE_RESOLVE, RESOLVE_CONTINUE);
    }
}
