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
import static catalog.CatalogTestUtils.DEFER_TRUE;
import static catalog.CatalogTestUtils.getCatalogPath;
import static javax.xml.catalog.CatalogFeatures.Feature.DEFER;
import static javax.xml.catalog.CatalogManager.catalog;

import java.lang.reflect.Method;

import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8077931
 * @summary This case tests whether the catalogs specified in delegateSystem,
 *          delegatePublic, delegateURI and nextCatalog entries are used lazily
 *          in resolution via defer feature.
 * @compile ../../libs/catalog/CatalogTestUtils.java
 */
public class DeferFeatureTest {

    @Test(dataProvider = "catalog-countOfLoadedCatalogFile")
    public void testDeferFeature(Catalog catalog, int catalogCount)
            throws Exception {
        Assert.assertEquals(loadedCatalogCount(catalog), catalogCount);
    }

    @DataProvider(name = "catalog-countOfLoadedCatalogFile")
    private Object[][] data() {
        return new Object[][] {
                // This catalog specifies null catalog explicitly,
                // and the count of loaded catalogs should be 0.
                { createCatalog(null), 0 },

                // This catalog specifies null catalog implicitly,
                // and the count of loaded catalogs should be 0.
                { createCatalog(CatalogFeatures.defaults()), 0 },

                // This catalog loads null catalog with true DEFER,
                // and the count of loaded catalogs should be 0.
                { createCatalog(createDeferFeature(DEFER_TRUE)), 0 },

                // This catalog loads null catalog with false DEFER.
                // It should load all of none-current catalogs and the
                // count of loaded catalogs should be 3.
                { createCatalog(createDeferFeature(DEFER_FALSE)), 3 } };
    }

    private CatalogFeatures createDeferFeature(String defer) {
        return CatalogFeatures.builder().with(DEFER, defer).build();
    }

    private Catalog createCatalog(CatalogFeatures feature) {
        return catalog(feature, getCatalogPath("deferFeature.xml"));
    }

    private int loadedCatalogCount(Catalog catalog) throws Exception {
        Method method = catalog.getClass().getDeclaredMethod(
                "loadedCatalogCount");
        method.setAccessible(true);
        return (int) method.invoke(catalog);
    }
}
