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
 * @summary Test type verification of writes of scalarized object at safepoint
 * @bug 8389342
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -Xbatch -XX:+UnlockDiagnosticVMOptions -XX:RepeatCompilation=100
 *                   -XX:+StressEliminateAllocations -XX:CompileOnly=${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

public class TestReinterpretValueAtSafepoint {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            test(i);
        }
    }

    static V1 test(int i) {
        return new V1(i);
    }

    static value class V1 {
        double d;
        V2 v2;

        V1(int i) {
            // Folding of a reinterpret cast into memory operation would
            // transform the StoreD into a StoreL
            d = Double.longBitsToDouble(i);
            v2 = i == -1 ? null : new V2();
        }
    }

    static value class V2 {}
}
