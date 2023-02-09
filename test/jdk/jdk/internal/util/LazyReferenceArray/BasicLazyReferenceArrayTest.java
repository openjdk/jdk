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

/*
 * @test
 * @modules java.base/jdk.internal.util
 * @summary Verify basic LazyReferenceArray operations
 * @run junit BasicLazyReferenceArrayTest
 */

import jdk.internal.util.LazyReferenceArray;
import org.junit.jupiter.api.*;

import java.util.NoSuchElementException;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyReferenceArrayTest {

    private static final int LENGTH = 10;

    LazyReferenceArray<Integer> instance;

    @BeforeEach
    void setup() {
        instance = LazyReferenceArray.create(LENGTH);
    }

    @Test
    void length() {
        assertEquals(LENGTH, instance.length());
    }

    @Test
    void emptyGet() {
        for (int i = 0; i < LENGTH; i++) {
            assertNull(instance.getOrNull(i));
        }
    }

    @Test
    void emptyGetOrThrow() {
        for (int i = 0; i < LENGTH; i++) {
            final int val = i;
            assertThrows(NoSuchElementException.class,
                    () -> instance.getOrThrow(val));
        }
    }

    @Test
    void compute() {
        for (int i = 0; i < LENGTH; i++) {
            Integer val = instance.computeIfAbsent(i, intIdentity());
            assertEquals(i, val);
        }
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> instance.computeIfAbsent(0, null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> instance.computeIfAbsent(0, i -> null));
    }

    @Test
    void indexes() {
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.computeIfAbsent(-1, intIdentity()));
        assertThrows(IndexOutOfBoundsException.class,
                () -> instance.computeIfAbsent(LENGTH, intIdentity()));
    }

    @Test
    void get() {
        for (int i = 0; i < LENGTH; i++) {
            Integer val = instance.computeIfAbsent(i, intIdentity());
            assertEquals(i, instance.getOrNull(i));
        }

    }

    private static IntFunction<Integer> intIdentity() {
        return i -> i;
    }

}
