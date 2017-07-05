/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.catalog.CatalogFeatures;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8151154
 * @run testng/othervm catalog.CatalogInvalidPathTest
 * @summary Verifies that the CatalogFeatures' builder throws
 *          IllegalArgumentException on invalid file inputs.
 *          This test was splitted from CatalogTest.java due to
 *          JDK-8168968, it has to only run without SecurityManager
 *          because an ACE will be thrown for invalid path.
 */
public class CatalogInvalidPathTest {
    /*
       DataProvider: for testing the verification of file paths by
                     the CatalogFeatures builder
     */
    @DataProvider(name = "invalidPaths")
    public Object[][] getFiles() {
        return new Object[][]{
            {null},
            {""},
            {"file:a/b\\c"},
            {"file:/../../.."},
            {"c:/te:t"},
            {"c:/te?t"},
            {"c/te*t"},
            {"in|valid.txt"},
            {"shema:invalid.txt"},
        };
    }

    @Test(dataProvider = "invalidPaths", expectedExceptions = IllegalArgumentException.class)
    public void testFileInput(String file) {
        CatalogFeatures features = CatalogFeatures.builder()
            .with(CatalogFeatures.Feature.FILES, file)
            .build();
    }
}
