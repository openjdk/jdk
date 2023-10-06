/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Paths;
import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogException;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;

/*
 * @test
 * @bug 8316996
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm catalog.CatalogResolverTest
 * @summary Tests CatalogResolver functions. See CatalogTest for existing basic
 * functional tests.
 */
@Listeners({jaxp.library.FilePolicy.class})
public class CatalogResolverTest extends CatalogSupportBase {
    static final String KEY_FILES = "javax.xml.catalog.files";
    static final String SYSTEM_ID = "http://openjdk_java_net/xml/catalog/dtd/system.dtd";

    /*
     * Initializing fields
     */
    @BeforeClass
    public void setUpClass() throws Exception {
        super.setUp();
    }

    /*
       DataProvider: data used to verify the RESOLVE property, including the valid
                     values and the effect of overriding that on the Catalog.
        Data columns:
        resolve property for the Catalog, resolve property for the CatalogResolver,
        system ID to be resolved, expected result, expected exception
     */
    @DataProvider(name = "factoryMethodInput")
    public Object[][] getInputs() throws Exception {

        return new Object[][]{
            // Valid values and overriding verification
            // RESOLVE=strict but expected match
            {"continue", "strict", SYSTEM_ID, "system.dtd", null},
            // RESOLVE=strict plus no match: expect exception
            {"continue", "strict", "bogusID", "", CatalogException.class},
            // RESOLVE=ignore, continue: expect no match but without an exception
            // Note that these tests do not differentiate empty InputSource from
            // null, in both cases, the returned ID is null
            {"strict", "ignore", "bogusID", null, null},
            {"strict", "continue", "bogusID", null, null},
            // null indicates not explicitly set
            {"continue", null, "bogusID", null, null},

            // invalid values, expect IAE
            {"continue", "invalidValue", "bogusID", "", IllegalArgumentException.class},
            {"continue", "", "bogusID", "", IllegalArgumentException.class},
         };
    }

    /**
     * Tests the factory method for creating CatalogResolver with a RESOLVE property.
     * The 2-arg {@link javax.xml.catalog.CatalogManager#catalogResolver(javax.xml.catalog.Catalog, java.lang.String) catalogResolver}
     * method adds the RESOLVE property on top of the single arg
     * {@link javax.xml.catalog.CatalogManager#catalogResolver(javax.xml.catalog.Catalog) catalogResolver}
     * method.
     *
     * @param cResolve the resolve property set on the Catalog object
     * @param crResolve the resolve property set on the CatalogResolver to override
     *                  that of the Catalog
     * @param systemId the system ID to be resolved
     * @param expectedResult the expected result
     * @param expectedThrow the expected exception
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "factoryMethodInput")
    public void testResolveProperty(String cResolve, String crResolve, String systemId, String expectedResult, Class<Throwable> expectedThrow) throws Exception {
        URI catalogFile = getClass().getResource("catalog.xml").toURI();
        Catalog c = CatalogManager.catalog(
                CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, cResolve).build(),
                catalogFile);

        if (expectedThrow != null) {
            Assert.assertThrows(expectedThrow,
                () -> resolveRef(c, crResolve, systemId));
        } else {

            String sysId = resolveRef(c, crResolve, systemId);
            System.out.println(sysId);
            Assert.assertEquals(sysId,
                    (expectedResult == null) ? null : Paths.get(filepath + expectedResult).toUri().toString().replace("///", "/"),
                    "System ID match not right");
        }

    }

    /**
     * Verifies that the catalogResolver method throws NullPointerException if
     * the {@code catalog} parameter is null. Note that the {@code resolve} parameter
     * is tested with {@code testResolveProperty}.
     */
    @Test(expectedExceptions = NullPointerException.class)
    public void testCatalogProperty() {
        CatalogManager.catalogResolver((Catalog)null, (String)null);
    }

    private String resolveRef(Catalog c, String crResolve, String systemId) throws Exception {
        CatalogResolver cr = CatalogManager.catalogResolver(c, crResolve);
        InputSource is = cr.resolveEntity("", systemId);
        return is == null ? null : is.getSystemId();
    }
}
