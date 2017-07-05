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

import java.io.IOException;
import java.util.IllegalFormatException;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/*
 * @test
 * @compile TestKit.java
 * @run testng TestKitTest
 */
public final class TestKitTest {

    public static void main(String[] args) {
        testAssertNotThrows();
        testAssertThrows();
    }

    private static void testAssertNotThrows() {
        Integer integer = TestKit.assertNotThrows(
                () -> TestKit.assertNotThrows(() -> 1)
        );
        assertEquals(integer, Integer.valueOf(1));

        RuntimeException re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertNotThrows(() -> { throw new IOException(); })
        );
        assertEquals(re.getMessage(),
                "Expected to run normally, but threw "
                        + "java.io.IOException");

        TestKit.assertNotThrows(
                () -> TestKit.assertNotThrows(() -> { })
        );

        re = TestKit.assertThrows(
                RuntimeException.class,
                () ->  TestKit.assertNotThrows((TestKit.ThrowingProcedure) () -> { throw new IOException(); })
        );
        assertEquals(re.getMessage(),
                "Expected to run normally, but threw "
                        + "java.io.IOException");
    }

    private static void testAssertThrows() {
        NullPointerException npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> TestKit.assertThrows(null, null)
        );
        assertNotNull(npe);
        assertTrue(Set.of("clazz", "code").contains(npe.getMessage()), npe.getMessage());

        npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> TestKit.assertThrows(IOException.class, null)
        );
        assertNotNull(npe);
        assertEquals(npe.getMessage(), "code");

        npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> TestKit.assertThrows(null, () -> { })
        );
        assertEquals(npe.getMessage(), "clazz");

        npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> { throw new NullPointerException(); }
        );
        assertNotNull(npe);
        assertNull(npe.getMessage());
        assertEquals(npe.getClass(), NullPointerException.class);

        RuntimeException re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertThrows(NullPointerException.class, () -> { })
        );
        assertEquals(re.getClass(), RuntimeException.class);
        assertEquals(re.getMessage(),
                "Expected to catch an exception of type "
                        + "java.lang.NullPointerException, but caught nothing");

        re = TestKit.assertThrows(
                RuntimeException.class,
                () -> { throw new NullPointerException(); }
        );
        assertNotNull(re);
        assertNull(re.getMessage());
        assertEquals(re.getClass(), NullPointerException.class);

        re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertThrows(
                        IllegalFormatException.class,
                        () -> { throw new IndexOutOfBoundsException(); }
                ));
        assertNotNull(re);
        assertEquals(re.getClass(), RuntimeException.class);
        assertEquals(re.getMessage(),
                "Expected to catch an exception of type java.util.IllegalFormatException"
                        + ", but caught java.lang.IndexOutOfBoundsException");
    }
}
