/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Collections;

/**
 * @test
 * @bug 8143850
 * @summary Verify ArrayDeque indexed get() and set()
 * @run testng IndexedAccess
 */

import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class IndexedAccess {

    @Test
    public void testIndexedAccess() {
        ArrayDeque<String> a = new ArrayDeque<>();
        Collections.addAll(a, "aaa", "bbb", "ccc", "ddd", "eee");

        checkSize(a, 5);
        assertEquals(a.get(0), "aaa");
        assertEquals(a.get(1), "bbb");
        assertEquals(a.get(2), "ccc");
        assertEquals(a.get(3), "ddd");
        assertEquals(a.get(4), "eee");

        a.removeFirst();
        checkSize(a, 4);
        assertEquals(a.get(0), "bbb");
        assertEquals(a.get(1), "ccc");
        assertEquals(a.get(2), "ddd");
        assertEquals(a.get(3), "eee");

        assertEquals(a.set(2, "xxx"), "ddd");
        checkSize(a, 4);
        assertEquals(a.get(0), "bbb");
        assertEquals(a.get(1), "ccc");
        assertEquals(a.get(2), "xxx");
        assertEquals(a.get(3), "eee");

        a.addFirst("yyy");
        checkSize(a, 5);
        assertEquals(a.set(2, "zzz"), "ccc");
        assertEquals(a.get(0), "yyy");
        assertEquals(a.get(1), "bbb");
        assertEquals(a.get(2), "zzz");
        assertEquals(a.get(3), "xxx");
        assertEquals(a.get(4), "eee");

        try {
            a.set(0, null);
        } catch (NullPointerException e) {
            // expected
        }
    }

    private void checkSize(ArrayDeque<String> a, int size) {
        assertEquals(a.size(), size);
        for (int index = 0; index < size; index++) {
            a.set(index, a.get(index));
        }
        for (int index : new int[] { Integer.MIN_VALUE, -1, size, Integer.MAX_VALUE }) {
            try {
                a.get(index);
                throw new AssertionError();
            } catch (IndexOutOfBoundsException e) {
                // expected
            }
            try {
                a.set(index, "sss");
                throw new AssertionError();
            } catch (IndexOutOfBoundsException e) {
                // expected
            }
        }
    }
}
