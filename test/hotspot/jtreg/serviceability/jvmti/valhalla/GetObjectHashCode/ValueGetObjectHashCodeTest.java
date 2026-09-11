/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8348743
 * @summary Tests ValueGetObjectHashCodeTest functionality for value objects.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native -agentlib:ValueGetObjectHashCodeTest
 *                          -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlineLayout
 *                          ValueGetObjectHashCodeTest
 */

import jdk.internal.vm.annotation.NullRestricted;

public class ValueGetObjectHashCodeTest {

    private static value class ValueClass {
        public int f1;
        public ValueClass(int v1) { f1 = v1; }
        public String toString() {
            return "value(" + String.valueOf(f1) + ")";
        }
    }

    private static value class ValueHolder {
        public ValueClass f1;
        @NullRestricted
        public ValueClass f2;

        public ValueHolder(int v1, int v2) {
            f1 = new ValueClass(v1);
            f2 = new ValueClass(v2);
        }
        public String toString() {
            return "holder{" + f1 + ", " + f2 + "}";
        }
    }

    private static native int getHash0(Object object);

    private static int getHash(Object object) {
        int hash = getHash0(object);
        System.out.println("hash (" + object + "): " + hash);
        return hash;
    }

    public static void main(String[] args) {
        System.loadLibrary("ValueGetObjectHashCodeTest");
        ValueClass v1 = new ValueClass(8);
        ValueHolder h1 = new ValueHolder(8, 8);

        // expect the same hash code for equal value objects
        int hash1 = getHash(v1);
        int hash2 = getHash(h1.f1);
        int hash3 = getHash(h1.f2);

        if (hash1 != hash2 || hash2 != hash3) {
            throw new RuntimeException("Hash should be equal");
        }
    }
}
