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
 * @modules java.base/jdk.internal.value
 * @modules java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 */

package compiler.stable;

import java.util.Objects;
import java.util.Set;

import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.Stable;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestStableArrayMembars {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int THE_VALUE = 42;

    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
        tf.addFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED");
        if (Integer.class.isValue()) {
            tf.addCrossProductScenarios(Set.of("", "-XX:-UseArrayFlattening",
                                               "-XX:-UseArrayFlattening -XX:-InlineTypePassFieldsAsArgs",
                                               "-XX:-UseArrayFlattening -XX:-InlineTypeReturnedAsFields",
                                               "-XX:-UseArrayFlattening -XX:-InlineTypePassFieldsAsArgs -XX:-InlineTypeReturnedAsFields"));
        }
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
            Integer i = contentsAcquire(offsetFor(arr, idx));
            return i == null ? slowPath(arr, idx) : i;
        }

        @ForceInline
        private Integer contentsAcquire(long offset) {
            return (Integer) (ValueClass.isFlatArray(arr) ?
                UNSAFE.getFlatValueAcquire(arr, offset, UNSAFE.arrayLayout(arr), Integer.class) :
                UNSAFE.getReferenceAcquire(arr, offset));
        }

        @ForceInline
        private static long offsetFor(Integer[] arr, long index) {
            return UNSAFE.arrayInstanceBaseOffset(arr) + UNSAFE.arrayInstanceIndexScale(arr) * index;
        }

        static Integer slowPath(final Integer[] array, final int index) {
            final long offset = offsetFor(array, index);
            final Integer t = array[index];
            if (t == null) {
                final Integer newValue = Integer.valueOf(THE_VALUE);
                Objects.requireNonNull(newValue);
                set(array, index, offset, newValue);

                return newValue;
            }
            return t;
        }

        static void set(Integer[] array, int index, long offset, Integer newValue) {
            if (array[index] == null) {
                if (!ValueClass.isFlatArray(array)) {
                    UNSAFE.putReferenceRelease(array, offset, newValue);
                } else {
                    UNSAFE.putFlatValueRelease(array, offset, UNSAFE.arrayLayout(array), Integer.class, newValue);
                }
            }
        }
    }

    static final LazyIntArray la = new LazyIntArray();

    @Test
    // We cannot eliminate all barriers with flat arrays, because Unsafe.getFlatValueAcquire()
    // explicitly adds a loadFence() in Java. Hence, there is no way for C2 to correlate those
    // barriers to an eliminated memory access and thus no way to remove them.
    // The barrier elimination only works with tiered compilation as the profiling information
    // is nessecary to inline the low-frequency Unsafe.put* methods.
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR },
        applyIfAnd = {"enable-valhalla", "false",
                      "TieredCompilation", "true"})
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR },
        applyIfAnd = {"UseArrayFlattening", "false",
                      "TieredCompilation", "true"})
    @IR(counts = { IRNode.MEMBAR, ">0",
                   IRNode.LOAD, "=1"}, // There is exactly one load fence, but no load
        applyIfAnd = {"enable-valhalla", "true",
                      "UseArrayFlattening", "true",
                      "TieredCompilation", "true"})
    static Integer test() {
        return la.get(0);
    }

    @Check(test = "test")
    static void check(Integer testResult) {
        Asserts.assertEQ(THE_VALUE, testResult.intValue(), "Incorrect result from LazyIntArray");
    }
}
