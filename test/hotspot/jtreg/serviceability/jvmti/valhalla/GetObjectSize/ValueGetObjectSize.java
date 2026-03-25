/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Sanity test for GetObjectSize with values classes.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueGetObjectSize ValueGetObjectSize
 */

import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class ValueGetObjectSize {

    private static final String agentLib = "ValueGetObjectSize";

    @LooselyConsistentValue
    private static value class ValueClass {
        public int f1;
        public int f2;

        public ValueClass(int v1, int v2) { f1 = v1; f2 = v2; }
    }

    private static value class ValueHolder {
        public ValueClass f1;
        @NullRestricted
        public ValueClass f2;

        public static ValueClass s1 = new ValueClass(0, 1);

        public ValueHolder(int v) {
            f1 = new ValueClass(v, v + 100);
            f2 = new ValueClass(v + 1, v + 200);
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(agentLib);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + agentLib + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }

        ValueClass testObj1 = new ValueClass(4, 5);
        ValueHolder testObj2 = new ValueHolder(6);

        testGetObjectSize(testObj1);
        testGetObjectSize(testObj2);
    }

    private static void testGetObjectSize(Object testObj) {
        String className = testObj.getClass().getName();
        log(">> Testing GetObjectSize for " + className);
        if (!nTestGetObjectSize(testObj)) {
            throw new RuntimeException("ERROR: " + className);
        }
        log("<< Testing " + className + " - OK");
        log("");
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    private static native boolean nTestGetObjectSize(Object obj);
}
