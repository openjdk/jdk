/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8349545
 * @summary Test verifies that calling ClassLoader.definePackage yields the same Package
 *          object for identical package definitions.
 * @run testng PackageDefineTest
 */


import java.net.URL;
import java.util.Iterator;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PackageDefineTest {

    @Test(dataProvider = "goodRedefinitions")
    public static void testGoodRedefs(Pkg[] pkgs) {
        var d1 = pkgs[0];
        var d2 = pkgs[1];

        TestClassLoader loader = new TestClassLoader();

        Package p1 = loader.definePackage("pkg",
                d1.specTitle, d1.specVersion, d1.specVendor,
                d1.implTitle, d1.implVersion, d1.implVendor,
                d1.sealBase);
        Package p2 = loader.definePackage("pkg",
                d2.specTitle, d2.specVersion, d2.specVendor,
                d2.implTitle, d2.implVersion, d2.implVendor,
                d2.sealBase);
        Assert.assertSame(p1, p2);
    }

    @Test(
            dataProvider = "badRedefinitions",
            expectedExceptions = IllegalArgumentException.class,
            expectedExceptionsMessageRegExp = "Incompatible redefinition of package pkg"
    )
    public static void testBadRedefs(Pkg[] pkgs) {
        var d1 = pkgs[0];
        var d2 = pkgs[1];

        TestClassLoader loader = new TestClassLoader();

        Package p1 = loader.definePackage("pkg",
                d1.specTitle, d1.specVersion, d1.specVendor,
                d1.implTitle, d1.implVersion, d1.implVendor,
                d1.sealBase);
        loader.definePackage("pkg",
                d2.specTitle, d2.specVersion, d2.specVendor,
                d2.implTitle, d2.implVersion, d2.implVendor,
                d2.sealBase);
    }

    @DataProvider(name = "goodRedefinitions")
    Pkg[][] goodRedefinitions() throws Exception {
        return new Pkg[][] {
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", new URL("file:///fooo")),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", new URL("file:///fooo"))
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", null),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", null)
            },
            new Pkg[]{
                new Pkg(null, null, null, null, null, null, null),
                new Pkg(null, null, null, null, null, null, null)
            }
        };
    }

    @DataProvider(name = "badRedefinitions")
    Pkg[][] badRedefinitions() throws Exception {
        return new Pkg[][] {
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", new URL("file:///fooo1")),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", new URL("file:///fooo2"))
            },
            new Pkg[]{
                new Pkg("specTitle1", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", null),
                new Pkg("specTitle2", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor", null)
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion1", "specVendor", "implTitle", "implVersion", "implVendor", null),
                new Pkg("specTitle", "specVersion2", "specVendor", "implTitle", "implVersion", "implVendor", null)
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor1", "implTitle", "implVersion", "implVendor", null),
                new Pkg("specTitle", "specVersion", "specVendor2", "implTitle", "implVersion", "implVendor", null)
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle1", "implVersion", "implVendor", null),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle2", "implVersion", "implVendor", null)
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion1", "implVendor", null),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion2", "implVendor", null)
            },
            new Pkg[]{
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor1", null),
                new Pkg("specTitle", "specVersion", "specVendor", "implTitle", "implVersion", "implVendor2", null)
            },
            new Pkg[]{
                new Pkg(null, null, null, null, null, null, new URL("file:///fooo1")),
                new Pkg(null, null, null, null, null, null, new URL("file:///fooo2"))
            }
        };
    }

    static class Pkg {
        final String specTitle;
        final String specVersion;
        final String specVendor;
        final String implTitle;
        final String implVersion;
        final String implVendor;
        final URL sealBase;

        Pkg(String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) {
            this.specTitle = specTitle;
            this.specVersion = specVersion;
            this.specVendor = specVendor;
            this.implTitle = implTitle;
            this.implVersion = implVersion;
            this.implVendor = implVendor;
            this.sealBase = sealBase;
        }
    }
}
