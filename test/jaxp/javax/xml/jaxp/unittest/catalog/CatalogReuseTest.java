/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8253569
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng catalog.CatalogReuseTest
 * @summary Verifies that a catalog can be reused.
 */
public class CatalogReuseTest extends CatalogSupportBase {
    static final CatalogFeatures FEATURES_STRICT = CatalogFeatures.builder().
            with(CatalogFeatures.Feature.RESOLVE, "strict").build();

    /*
        DataProvider: reuses a catalog. The length of the URIs is in descending order.
        Data columns: catalog, uri, expected
     */
    @DataProvider(name = "dataWithCatalogD")
    public Object[][] dataWithCatalogD() {
        Catalog c = getCatalog();
        return new Object[][]{
            {c, "http://entailments/example.org/A/B/derived.ttl", "derived/A/B/derived.ttl"},
            {c, "http://example.org/A/B.owl", "sources/A/B.owl"},
         };
    }

    /*
        DataProvider: reuses a catalog. The length of the URIs is in ascending order.
        Data columns: catalog, uri, expected
     */
    @DataProvider(name = "dataWithCatalogA")
    public Object[][] dataWithCatalogA() {
        Catalog c = getCatalog();
        return new Object[][]{
            {c, "http://example.org/A/B.owl", "sources/A/B.owl"},
            {c, "http://entailments/example.org/A/B/derived.ttl", "derived/A/B/derived.ttl"},
         };
    }

    /*
        DataProvider: provides no catalog. A new catalog will be created for each test.
        Data columns: uri, expected
     */
    @DataProvider(name = "dataWithoutCatalog")
    public Object[][] dataWithoutCatalog() {
        return new Object[][]{
            {"http://entailments/example.org/A/B/derived.ttl", "derived/A/B/derived.ttl"},
            {"http://example.org/A/B.owl", "sources/A/B.owl"},
         };
    }

    /*
     * Initializing fields
     */
    @BeforeClass
    public void setUpClass() throws Exception {
        super.setUp();
    }

    /*
     * Verifies that a Catalog object can be reused, that no state data are
     * in the way of a subsequent matching attempt.
    */
    @Test(dataProvider = "dataWithCatalogD")
    public void testD(Catalog c, String uri, String expected) throws Exception {
        String m = c.matchURI(uri);
        Assert.assertTrue(m.endsWith(expected), "Expected: " + expected);
    }

    /*
     * Verifies that a Catalog object can be reused.
    */
    @Test(dataProvider = "dataWithCatalogA")
    public void testA(Catalog c, String uri, String expected) throws Exception {
        String m = c.matchURI(uri);
        Assert.assertTrue(m.endsWith(expected), "Expected: " + expected);
    }

    /*
     * Verifies that a match is found in a newly created Catalog.
    */
    @Test(dataProvider = "dataWithoutCatalog")
    public void testNew(String uri, String expected) throws Exception {
        Catalog c = getCatalog();
        String m = c.matchURI(uri);
        Assert.assertTrue(m.endsWith(expected), "Expected: " + expected);

    }

    private Catalog getCatalog() {
        String uri = "file://" + slash + filepath + "/catalogReuse.xml";
        Catalog c = CatalogManager.catalog(FEATURES_STRICT, uri != null? URI.create(uri) : null);
        return c;
    }
}
