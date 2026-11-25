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
 * @test id=default
 * @bug 8389460
 * @summary JVMTI IterateThroughHeap class filter should not skip flattened value objects
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires vm.jvmti
 * @enablePreview
 * @run main/othervm/native -agentlib:FlatClassFilterTest
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+UnlockExperimentalVMOptions
 *                          -XX:+UseArrayFlattening
 *                          -XX:+UseFieldFlattening
 *                          -XX:+UseNullFreeAtomicValueFlattening
 *                          -XX:+UseNullableAtomicValueFlattening
 *                          FlatClassFilterTest
 */

/*
 * @test id=class_untagged
 * @bug 8389460
 * @summary JVMTI IterateThroughHeap class filter should not skip flattened value objects
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @requires vm.jvmti
 * @enablePreview
 * @run main/othervm/native -agentlib:FlatClassFilterTest=heap_filter_class_untagged
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+UnlockExperimentalVMOptions
 *                          -XX:+UseArrayFlattening
 *                          -XX:+UseFieldFlattening
 *                          -XX:+UseNullFreeAtomicValueFlattening
 *                          -XX:+UseNullableAtomicValueFlattening
 *                          FlatClassFilterTest
 */

import java.lang.ref.Reference;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

public class FlatClassFilterTest {
    private static void log(String msg) {
        System.out.println(msg);
        System.out.flush();
    }

    static Value[] array;
    static Value heap = new Value(999);

    static value class Value {
        int x;
        Value(int x) { this.x = x; }
    }

    static value class ValueHolder {
        @NullRestricted
        public Value v1;

        public Value vn;
        public Value[] v_arr;

        public ValueHolder(int seed) throws Exception {
            v1 = new Value(seed);
            vn = null;
            v_arr = createValueArray(seed);
        }
    }

    static Value[] createValueArray(int seed) throws Exception {
        Value[] arr = (Value[])ValueClass.newNullableAtomicArray(Value.class, 5);
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (i == 2 ? null : new Value(seed + 10 + i));
        }
        return arr;
    }

    static final long CLASS_TAG = 71;
    static final Unsafe U = Unsafe.getUnsafe();

    static native int count(Class<?> filter, Class<?> taggedClass, long classTag);

    public static void main(String[] args) throws Exception {
        System.loadLibrary("FlatClassFilterTest");
        log("FlatClassFilterTest: Started");

        ValueHolder holder = new ValueHolder(10);
        array = createValueArray(100);

        // Use Unsafe because there is no non-unsafe API to check the field flatness.
        boolean isFlatV1 = U.isFlatField(ValueHolder.class.getDeclaredField("v1"));
        boolean isFlatArr = ValueClass.isFlatArray(array);
        int all = count(null, Value.class, CLASS_TAG);
        int filtered = count(Value.class, Value.class, CLASS_TAG);

        log("v1 is flat: " + isFlatV1);
        log("array is flat: " + isFlatArr);
        log("all: " + all);
        log("filtered: " + filtered);

        if (!isFlatV1) {
            throw new AssertionError("The field holder.v1 is expected to be flat");
        }
        if (!isFlatArr) {
            throw new AssertionError("The array is expected to be flat");
        }
        if (filtered != all) {
            throw new AssertionError("V.class filter skipped flat V[] element");
        }
        Reference.reachabilityFence(holder);
        log("FlatClassFilterTest: PASSED");
    }
}
