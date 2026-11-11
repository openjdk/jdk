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

/**
 * @test
 * @bug 8381063
 * @summary Verify that null is passed for jobject parameter to JVMTI SampledObjectAlloc event callbacks.
 * @requires vm.jvmti
 *
 * @enablePreview
 * @run main/othervm/native -agentlib:SampledObjectAllocValue SampledObjectAllocValue
 * @run main/othervm/native -agentlib:SampledObjectAllocValue=can_support_value_objects SampledObjectAllocValue
 */

public class SampledObjectAllocValue {

    private static value class ValueClass {
        public long x;
        public long y;

        public ValueClass(long arg1, long arg2) {
          x = arg1; y = arg2;
        }
    }

    private static ValueClass[] tmp = new ValueClass[100];

    private static native void enableEvents(Thread thread, Class testedClass);

    public static void main(String[] args) throws Exception {
        enableEvents(Thread.currentThread(), ValueClass.class);
        // Allocate value objects to trigger JVMTI SampledObjectAlloc events.
        // Here we assume that flat array layout is not used for ValueClass objects.
        for (int i = 0; i < tmp.length; i++) {
            tmp[i] = new ValueClass(i, i + 100);
        }
    }
}
