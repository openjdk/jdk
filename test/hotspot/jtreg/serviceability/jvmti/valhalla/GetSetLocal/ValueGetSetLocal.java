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
 * @summary Sanity tests for GetLocalObject/SetLocalObject/GetLocalInstance with value classes.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueGetSetLocal ValueGetSetLocal
 */

import java.util.Objects;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

public class ValueGetSetLocal {

    private static final String agentLib = "ValueGetSetLocal";

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

        // slot 0 is "this"
        public void meth(ValueClass obj1,       // slot 1
                         ValueHolder obj2) {    // slot 2
            Object obj3 = obj2;                 // slot 3
            // SetLocalObject can only set locals for top frame of virtual threads.
            boolean testSetLocal = !Thread.currentThread().isVirtual();
            if (!nTestLocals(Thread.currentThread(), testSetLocal)) {
                throw new RuntimeException("ERROR: nTestLocals failed");
            }
            // nTestLocals sets obj3 = obj1
            if (testSetLocal && !Objects.equals(obj3, obj1)) {
                throw new RuntimeException("ERROR: obj3 != obj1" + " (obj3 = " + obj3 + ")");
            }
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

        ValueClass testObj1 = new ValueClass(7, 8);
        ValueHolder testObj2 = new ValueHolder(9);
        testObj2.meth(testObj1, testObj2);
    }

    private static native boolean nTestLocals(Thread thread, boolean testSetLocal);
}
