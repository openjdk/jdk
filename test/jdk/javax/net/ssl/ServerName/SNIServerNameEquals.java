/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.util.Objects;
import javax.net.ssl.SNIServerName;

/*
 * @test
 * @bug 8392416
 * @summary SNIServerName.equals() test
 * @library /test/lib
 */

public class SNIServerNameEquals {

    private static final byte[] SNI = new byte[]{1, 2, 3};
    private static final byte[] DIFF_SNI = new byte[]{1, 2, 4};

    public static void main(String[] args) {
        SNIServerName name = new TestServerName(1, SNI);

        assertFalse(name.equals(null), "equals(null) must return false");
        assertFalse(Objects.equals(name, null),
                "Objects.equals(name, null) must return false");

        SNIServerName equalName = new TestServerName(1, SNI);

        assertTrue(name.equals(name), "A server name must equal itself");
        assertTrue(name.equals(equalName), "Identical names must be equal");
        assertTrue(equalName.equals(name), "Equality must be symmetric");
        assertEquals(name.hashCode(), equalName.hashCode(),
                "Equal names must have equal hash codes");

        SNIServerName differentType = new TestServerName(2, SNI);
        SNIServerName differentValue = new TestServerName(1, DIFF_SNI);

        assertFalse(name.equals(differentType),
                "Different name types must not be equal");
        assertFalse(name.equals(differentValue),
                "Different encoded values must not be equal");
        assertFalse(name.equals(new Object()),
                "An unrelated object must not be equal to a server name");

        SNIServerName derivedName = new DerivedServerName(1, SNI);

        assertFalse(name.equals(derivedName),
                "A different class must not compare equal");
        assertFalse(derivedName.equals(name),
                "The class check must be symmetric");
    }

    private static class TestServerName extends SNIServerName {
        TestServerName(int type, byte[] encoded) {
            super(type, encoded);
        }
    }

    private static final class DerivedServerName extends TestServerName {
        DerivedServerName(int type, byte[] encoded) {
            super(type, encoded);
        }
    }
}
