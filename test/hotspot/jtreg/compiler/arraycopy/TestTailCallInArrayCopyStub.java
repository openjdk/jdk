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
 * @key stress randomness
 * @bug 8329258
 * @summary Test correct execution of the tail call at the end of the arraycopy stub.
 * @requires vm.compiler2.enabled
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-TieredCompilation
 *                   -XX:+StressGCM -XX:+StressLCM
 *                   -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,*::test
 *                   compiler.arraycopy.TestTailCallInArrayCopyStub
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -Xbatch -XX:-TieredCompilation
 *                   -XX:+StressGCM -XX:+StressLCM -XX:StressSeed=75451718
 *                   -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,*::test
 *                   compiler.arraycopy.TestTailCallInArrayCopyStub
 */

package compiler.arraycopy;

public class TestTailCallInArrayCopyStub {

    public static void test(byte[] src, byte[] dst) {
        try {
            System.arraycopy(src, -1, dst, 0, src.length);
        } catch (Exception e) {
            // Expected
        }
    }

    public static void main(String[] args) {
        byte[] array = new byte[5];
        for (int i = 0; i < 10_000; ++i) {
            test(array, array);
        }
    }
}
