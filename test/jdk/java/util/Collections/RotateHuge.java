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
 * @bug 8314236
 * @summary Overflow in Collections.rotate
 */

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

public class RotateHuge {

    private static final class MockList extends AbstractList<Object>
            implements RandomAccess {
        private final int size;

        public MockList(final int size) {
            if (size < 0)
                throw new IllegalArgumentException("Illegal size: " + size);
            this.size = size;
        }

        @Override
        public Object get(final int index) {
            Objects.checkIndex(index, size);
            return null;
        }

        @Override
        public Object set(final int index, final Object element) {
            Objects.checkIndex(index, size);
            return null;
        }

        @Override
        public int size() {
            return size;
        }
    }

    public static void main(final String[] args) {
        testRotate((1 << 30) + 1, -(1 << 30) - 2);
        testRotate((1 << 30) + 1, 1 << 30);
        testRotate(Integer.MAX_VALUE, Integer.MIN_VALUE);
        testRotate(Integer.MAX_VALUE, Integer.MIN_VALUE + 3);
        testRotate(Integer.MAX_VALUE, 2);
        testRotate(Integer.MAX_VALUE, Integer.MAX_VALUE - 1);
    }

    /*
     * This test covers only index computations.
     * Correctness of elements rotation is not checked.
     */
    private static void testRotate(final int size, final int distance) {
        final List<Object> list = new MockList(size);
        Collections.rotate(list, distance);
    }
}
