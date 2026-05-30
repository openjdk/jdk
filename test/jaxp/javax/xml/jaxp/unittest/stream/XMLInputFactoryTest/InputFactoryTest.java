/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package stream.XMLInputFactoryTest;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8276050
 * @library /javax/xml/jaxp/unittest
 * @run junit/othervm stream.XMLInputFactoryTest.InputFactoryTest
 * @summary Test XMLInputFactory functionalities.
 */
public class InputFactoryTest {
    public static Object[][] getAEProperties() {
        return new Object[][] {
                { XMLConstants.ACCESS_EXTERNAL_DTD, "all", "all" },
                { XMLConstants.ACCESS_EXTERNAL_SCHEMA, "all", "all" },
                { XMLConstants.ACCESS_EXTERNAL_DTD, "", "" },
                { XMLConstants.ACCESS_EXTERNAL_SCHEMA, "", "" },
        };
    }

    /*
     * Verifies that the XMLInputFactory returns security properties correctly.
     */
    @ParameterizedTest
    @MethodSource("getAEProperties")
    public void testProperty(String name, String value, String expected) {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        xif.setProperty(name, value);
        assertEquals(expected, xif.getProperty(name));
    }
}
