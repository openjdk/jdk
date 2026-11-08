/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8396910
 * @summary Verify that the BigInteger mulAdd intrinsic sign-extends its integer return value
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @modules java.base/java.math:open
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      -XX:+UnlockDiagnosticVMOptions -XX:+UseMulAddIntrinsic
 *      -XX:CompileCommand=dontinline,compiler.c2.riscv64.TestMulAddSignExtension::testCarry
 *      compiler.c2.riscv64.TestMulAddSignExtension
 */

package compiler.c2.riscv64;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.Arrays;

public class TestMulAddSignExtension {
    private static final MethodHandle MUL_ADD;

    static {
        try {
            MUL_ADD = MethodHandles.privateLookupIn(BigInteger.class, MethodHandles.lookup())
                    .findStatic(BigInteger.class, "implMulAdd",
                            MethodType.methodType(int.class, int[].class, int[].class,
                                                  int.class, int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void testCarry(int[] out, int[] in, int len, int k, int expected) throws Throwable {
        int carry = (int) MUL_ADD.invokeExact(out, in, 0, len, k);
        // Compare before a spill or a Java call can normalize the intrinsic result.
        if (carry != expected) {
            throw new RuntimeException("Unexpected carry for length " + len + " and multiplier " + k);
        }
    }

    public static void main(String[] args) throws Throwable {
        int[] lengths = {0, 1, 7, 8, 9, 15, 16, 17};
        int[] in = new int[17];
        int[] out = new int[17];
        Arrays.fill(in, -1);

        for (int i = 0; i < 20_000; i++) {
            for (int len : lengths) {
                Arrays.fill(out, 0);
                testCarry(out, in, len, -1, len == 0 ? 0 : -2);
                Arrays.fill(out, 0);
                testCarry(out, in, len, Integer.MIN_VALUE, len == 0 ? 0 : Integer.MAX_VALUE);
                Arrays.fill(out, -1);
                testCarry(out, in, len, -1, len == 0 ? 0 : -1);
                Arrays.fill(out, 0);
                testCarry(out, in, len, 0, 0);
            }
        }
    }
}
