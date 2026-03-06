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

/*
 * @test
 * @bug 8155600
 * @summary Tests for Arrays.asList()
 * @run junit AsList
 */

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AsList {
    /*
     * Iterator contract test
     */
    @ParameterizedTest
    @MethodSource("arrays")
    public void testIterator(Object[] array) {
        Iterator<Object> itr = Arrays.asList(array).iterator();
        for (Object o : array) {
            assertTrue(itr.hasNext());
            assertTrue(itr.hasNext()); // must be idempotent
            assertSame(o, itr.next());
            assertThrows(UnsupportedOperationException.class, itr::remove);
        }
        for (int i = 0; i < 3; i++) {
            assertFalse(itr.hasNext());
            assertThrows(NoSuchElementException.class, itr::next);
        }
    }

    public static Object[][] arrays() {
        return new Object[][] {
            { new Object[] { } },
            { new Object[] { 1 } },
            { new Object[] { null } },
            { new Object[] { null, 1 } },
            { new Object[] { 1, null } },
            { new Object[] { null, null } },
            { new Object[] { null, 1, 2 } },
            { new Object[] { 1, null, 2 } },
            { new Object[] { 1, 2, null } },
            { new Object[] { null, null, null } },
            { new Object[] { 1, 2, 3, null, 4 } },
            { new Object[] { "a", "a", "a", "a" } },
            { IntStream.range(0, 100).boxed().toArray() }
        };
    }
}
