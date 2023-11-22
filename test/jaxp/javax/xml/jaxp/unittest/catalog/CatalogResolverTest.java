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
import javax.xml.catalog.CatalogResolver.NotFoundAction;
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
            {"continue", NotFoundAction.STRICT, SYSTEM_ID, "system.dtd", null},
            // RESOLVE=strict plus no match: expect exception
            {"continue", NotFoundAction.STRICT, "bogusID", "", CatalogException.class},
            // RESOLVE=ignore, continue: expect no match but without an exception
            // Note that these tests do not differentiate empty InputSource from
            // null, in both cases, the returned ID is null
            {"strict", NotFoundAction.IGNORE, "bogusID", null, null},
            {"strict", NotFoundAction.CONTINUE, "bogusID", null, null},
         };
    }

    @DataProvider(name = "NPETest")
    public Object[][] getNPETest() throws Exception {
        return new Object[][]{
            {null, null},
            {getCatalog("ignore"), null},
         };
    }

    /**
     * Tests the factory method for creating CatalogResolver with an
     * {@link javax.xml.catalog.CatalogResolver.NotFoundAction action} type.
     * The 2-arg {@link javax.xml.catalog.CatalogManager#catalogResolver(
     * javax.xml.catalog.Catalog, javax.xml.catalog.CatalogResolver.NotFoundAction)
     * catalogResolver} method adds the action type to be used for determining
     * the behavior instead of relying on the underlying catalog.
     *
     * @param cResolve the resolve property set on the Catalog object
     * @param action the resolve property set on the CatalogResolver to override
     *                  that of the Catalog
     * @param systemId the system ID to be resolved
     * @param expectedResult the expected result
     * @param expectedThrow the expected exception
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "factoryMethodInput")
    public void testResolveProperty(String cResolve, NotFoundAction action,
            String systemId, String expectedResult, Class<Throwable> expectedThrow)
            throws Exception {
        Catalog c = getCatalog(cResolve);

        if (expectedThrow != null) {
            Assert.assertThrows(expectedThrow,
                () -> resolveRef(c, action, systemId));
        } else {

            String sysId = resolveRef(c, action, systemId);
            System.out.println(sysId);
            Assert.assertEquals(sysId,
                    (expectedResult == null) ? null : Paths.get(filepath + expectedResult).toUri().toString().replace("///", "/"),
                    "System ID match not right");
        }
    }

    /**
     * Verifies that the catalogResolver method throws NullPointerException if
     * any of the parameters is null.
     */
    @Test(dataProvider = "NPETest", expectedExceptions = NullPointerException.class)
    public void testCatalogProperty(Catalog c, NotFoundAction action) {
        CatalogManager.catalogResolver(c, action);
    }

    private String resolveRef(Catalog c, NotFoundAction action, String systemId) throws Exception {
        CatalogResolver cr = CatalogManager.catalogResolver(c, action);
        InputSource is = cr.resolveEntity("", systemId);
        return is == null ? null : is.getSystemId();
    }

    private Catalog getCatalog(String cResolve) throws Exception {
        URI catalogFile = getClass().getResource("catalog.xml").toURI();
        Catalog c = CatalogManager.catalog(
                CatalogFeatures.builder().with(CatalogFeatures.Feature.RESOLVE, cResolve).build(),
                catalogFile);
        return c;
    }
}
