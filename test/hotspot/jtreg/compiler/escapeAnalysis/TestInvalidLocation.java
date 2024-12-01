/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8323190
 * @summary C2 Segfaults during code generation because of unhandled SafePointScalarMerge monitor debug info.
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xcomp -XX:+ReduceAllocationMerges TestInvalidLocation
 */

public class TestInvalidLocation {
    static boolean var2 = true;
    static double[] var4 = new double[1];

    public static void main(String[] args) {
        for (int i = 0; i < 10; i++) {
            System.out.println(test());
        }
    }

    static Class0 test() {
        double[] var14;
        double var3;
        StringBuilder var1 = new StringBuilder();
        Class0 var0 = Class1.Class1_sfield0;
        synchronized (var2 ? new StringBuilder() : var1) {
            var14 = var4;
            for (int i0 = 0; i0 < var0.Class0_field0.length && i0 < var14.length; i0 = 1) {
                var3 = var14[i0];
            }
        }
        return var0;
    }

    static class Class0 {
        double[] Class0_field0;
        Class0() {
            Class0_field0 = new double[] { 85.42200639495138 };
        }
    }

    class Class1 {
        static Class0 Class1_sfield0 = new Class0();
    }
}
