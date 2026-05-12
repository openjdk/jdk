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

/*
 * @test
 * @bug 8377541
 * @summary Test that membars are eliminated when loading from a stable array.
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 */

package compiler.stable;

import java.util.Objects;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import compiler.lib.ir_framework.*;

public class TestStableArrayMembars {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
        tf.addFlags( "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        tf.start();
    }

    static final class LazyIntArray {
        @Stable
        private final Integer[] arr;

        LazyIntArray() {
            this.arr = new Integer[10];
        }

        @ForceInline
        Integer get(int idx) {
            Integer i = contentsAcquire(offsetFor(idx));
            return i == null ? slowPath(arr, idx) : i;
        }

        @ForceInline
        private Integer contentsAcquire(long offset) {
            return (Integer) UNSAFE.getReferenceAcquire(arr, offset);
        }

        @ForceInline
        private static long offsetFor(long index) {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
        }

        static Integer slowPath(final Integer[] array, final int index) {
            final long offset = offsetFor(index);
            final Integer t = array[index];
            if (t == null) {
                final Integer newValue = Integer.valueOf(42);
                Objects.requireNonNull(newValue);
                set(array, index, newValue);

                return newValue;
            }
            return t;
        }

        static void set(Integer[] array, int index, Integer newValue) {
            if (array[index] == null) {
                UNSAFE.putReferenceRelease(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index, newValue);
            }
        }
    }

    static final LazyIntArray la = new LazyIntArray();

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Integer test() {
        return la.get(0);
    }
}
