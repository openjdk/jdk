/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import javax.xml.transform.Source;
import java.net.URI;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8215330
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit catalog.GroupTest
 * @summary Tests catalog with Group entries.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class GroupTest extends CatalogSupportBase {

    String catalogGroup;
    /*
     * Initializing fields
     */
    @BeforeAll
    public void setUpClass() throws Exception {
        super.setUp();
        catalogGroup = Paths.get(filepath + "GroupTest.xml").toUri().toASCIIString();
    }

    /**
     * Tests catalog resolution with entries in a group.
     *
     * @param catalog the catalog to be used
     * @param uri an URI to be resolved by the catalog
     * @param expected the expected result string
     * @throws Exception
     */
    @ParameterizedTest
    @MethodSource("getDataDOM")
    public void testGroup(String catalog, String uri, String expected) throws Exception {
        CatalogResolver resolver = CatalogManager.catalogResolver(
                CatalogFeatures.defaults(), URI.create(catalog));

        Source src = resolver.resolve(uri, null);
        assertTrue(src.getSystemId().endsWith(expected), "uriSuffix match");
    }


    /*
       DataProvider: for testing catalogs with group entries
       Data: catalog file, uri, expected result string
     */
    public Object[][] getDataDOM() {
        return new Object[][]{
            {catalogGroup, "http://openjdk_java_net/xml/catalog/A/CommonFileA1.xml", "LocalFileA1.xml"},
            {catalogGroup, "http://openjdk_java_net/xml/catalog/B/CommonFileB1.xml", "LocalFileB1.xml"},
            {catalogGroup, "http://openjdk_java_net/xml/catalog/C/CommonFileC1.xml", "LocalFileC1.xml"},
        };
    }
}
