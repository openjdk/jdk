/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/*
 * @test
 * @compile TestKit.java
 * @run junit TestKitTest
 */
public final class TestKitTest {

    @Test
    public void testAssertNotThrows() {
        Integer integer = TestKit.assertNotThrows(
                () -> TestKit.assertNotThrows(() -> 1)
        );
        assertEquals(Integer.valueOf(1), integer);

        RuntimeException re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertNotThrows(() -> { throw new IOException(); })
        );
        assertEquals("Expected to run normally, but threw "
                        + "java.io.IOException", re.getMessage());

        TestKit.assertNotThrows(
                () -> TestKit.assertNotThrows(() -> { })
        );

        re = TestKit.assertThrows(
                RuntimeException.class,
                () ->  TestKit.assertNotThrows((TestKit.ThrowingProcedure) () -> { throw new IOException(); })
        );
        assertEquals("Expected to run normally, but threw "
                        + "java.io.IOException", re.getMessage());
    }

    @Test
    public void testAssertThrows() {
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
        assertEquals("code", npe.getMessage());

        npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> TestKit.assertThrows(null, () -> { })
        );
        assertEquals("clazz", npe.getMessage());

        npe = TestKit.assertThrows(
                NullPointerException.class,
                () -> { throw new NullPointerException(); }
        );
        assertNotNull(npe);
        assertNull(npe.getMessage());
        assertEquals(NullPointerException.class, npe.getClass());

        RuntimeException re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertThrows(NullPointerException.class, () -> { })
        );
        assertEquals(RuntimeException.class, re.getClass());
        assertEquals("Expected to catch an exception of type "
                        + "java.lang.NullPointerException, but caught nothing", re.getMessage());

        re = TestKit.assertThrows(
                RuntimeException.class,
                () -> { throw new NullPointerException(); }
        );
        assertNotNull(re);
        assertNull(re.getMessage());
        assertEquals(NullPointerException.class, re.getClass());

        re = TestKit.assertThrows(
                RuntimeException.class,
                () -> TestKit.assertThrows(
                        IllegalFormatException.class,
                        () -> { throw new IndexOutOfBoundsException(); }
                ));
        assertNotNull(re);
        assertEquals(RuntimeException.class, re.getClass());
        assertEquals("Expected to catch an exception of type java.util.IllegalFormatException"
                        + ", but caught java.lang.IndexOutOfBoundsException", re.getMessage());
    }

    @Test
    public void testAssertUnmodifiable() {
        TestKit.assertUnmodifiableList(
                Collections.unmodifiableList(
                        new ArrayList<>(Arrays.asList(1, 2, 3))));
        TestKit.assertThrows(RuntimeException.class,
                () -> TestKit.assertUnmodifiableList(new ArrayList<>()));
        TestKit.assertThrows(RuntimeException.class,
                () -> TestKit.assertUnmodifiableList(new LinkedList<>()));
        TestKit.assertThrows(RuntimeException.class,
                () -> TestKit.assertUnmodifiableList(
                        new ArrayList<>(Arrays.asList(1, 2, 3))));
        TestKit.assertUnmodifiableMap(Collections.unmodifiableMap(Map.of()));
        TestKit.assertThrows(RuntimeException.class,
                () -> TestKit.assertUnmodifiableMap(new HashMap<>()));
    }
}
