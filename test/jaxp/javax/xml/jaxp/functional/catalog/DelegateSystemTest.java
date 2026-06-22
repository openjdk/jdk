/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.catalog.CatalogException;
import javax.xml.catalog.CatalogResolver;

import static catalog.CatalogTestUtils.catalogResolver;
import static catalog.ResolutionChecker.checkSysIdResolution;
import static catalog.ResolutionChecker.expectExceptionOnSysId;

/*
 * @test
 * @bug 8077931
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm catalog.DelegateSystemTest
 * @summary Get matched URIs from delegateSystem entries.
 */
public class DelegateSystemTest {

    @ParameterizedTest
    @MethodSource("dataOnMatch")
    public void testMatch(String systemId, String matchedUri) {
        checkSysIdResolution(createResolver(), systemId, matchedUri);
    }

    public static Object[][] dataOnMatch() {
        return new Object[][] {
                // The matched URI of the specified system id is defined in
                // a delegate catalog file of the current catalog file.
                { "http://remote/dtd/alice/docAlice.dtd",
                        "http://local/base/dtd/alice/docAliceDS.dtd" },

                // The current catalog file defines two delegateSystem entries
                // with the same systemIdStartString, and both of them match the
                // specified system id. But the matched URI should be in the
                // delegate catalog file, which is defined in the upper
                // delegateSystem entry.
                { "http://remote/dtd/bob/docBob.dtd",
                        "http://local/base/dtd/bob/docBobDS.dtd" },

                // The current catalog file defines two delegateSystem entries,
                // and both of them match the specified system id. But the
                // matched URI should be in the delegate catalog file, which is
                // defined in the longest matched delegateSystem entry.
                { "http://remote/dtd/carl/docCarl.dtd",
                        "http://local/base/dtd/carl/docCarlDS.dtd"} };
    }

    @ParameterizedTest
    @MethodSource("dataOnException")
    public void testException(String systemId,
            Class<? extends Throwable> expectedExceptionClass) {
        expectExceptionOnSysId(createResolver(), systemId,
                expectedExceptionClass);
    }

    public static Object[][] dataOnException() {
        return new Object[][] {
                // The matched delegateSystem entry of the specified system id
                // defines a non-existing delegate catalog file. That should
                // raise a RuntimeException.
                { "http://remote/dtd/david/docDavidDS.dtd",
                        RuntimeException.class },

                // There's no match of the specified system id in the catalog
                // structure. That should raise a CatalogException.
                { "http://ghost/xml/dtd/ghost/docGhostDS.dtd",
                        CatalogException.class } };
    }

    private static CatalogResolver createResolver() {
        return catalogResolver("delegateSystem.xml");
    }
}
