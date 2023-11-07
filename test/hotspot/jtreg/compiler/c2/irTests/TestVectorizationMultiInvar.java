/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;
import jdk.internal.misc.Unsafe;
import java.util.Objects;
import java.util.Random;

/*
 * @test
 * @bug 8300257
 * @requires (os.simpleArch == "x64") | (os.simpleArch == "aarch64")
 * @summary C2: vectorization fails on some simple Memory Segment loops
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.TestVectorizationMultiInvar
 */

public class TestVectorizationMultiInvar {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();
    private final static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        Object alignVector = wb.getVMFlag("AlignVector");
        if (alignVector != null && !((Boolean)alignVector)) {
            TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");
        }
    }

    static int size = 1024;
    static byte[] byteArray = new byte[size * 8];
    static int[] intArray = new int[size];
    static long[] longArray = new long[size];
    static long baseOffset = 0;

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_L, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testByteLong1(byte[] dest, long[] src) {
        for (int i = 0; i < src.length; i++) {
            long j = Objects.checkIndex(i * 8, (long)(src.length * 8));
            UNSAFE.putLongUnaligned(dest, baseOffset + j, src[i]);
        }
    }

    @Run(test = "testByteLong1")
    public static void testByteLong1_runner() {
        baseOffset = UNSAFE.ARRAY_BYTE_BASE_OFFSET;
        testByteLong1(byteArray, longArray);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_B, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testLoopNest1(byte[] dest, byte[] src,
                                     long start1, long stop1,
                                     long start2, long stop2,
                                     long start3, long stop3,
                                     long start4, long stop4,
                                     long start5, long stop5) {
        if (src == null || dest == null) {
        }
        for (long i = start1; i < stop1; i++) {
            for (long j = start2; j < stop2; j++) {
                for (long k = start3; k < stop3; k++) {
                    for (long l = start4; l < stop4; l++) {
                        for (long m = start5; m < stop5; m++) {
                            long invar = i + j + k + l + m;
                            for (int n = 0; n < src.length - (int)invar; n++) {
                                UNSAFE.putByte(dest, UNSAFE.ARRAY_BYTE_BASE_OFFSET + n + invar, UNSAFE.getByte(src, UNSAFE.ARRAY_BYTE_BASE_OFFSET + n + invar));
                            }
                        }
                    }
                }
            }
        }
    }

    @Run(test = "testLoopNest1")
    public static void testLoopNest1_runner() {
        testLoopNest1(byteArray, byteArray, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2);
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_I, ">=1", IRNode.STORE_VECTOR, ">=1" })
    public static void testLoopNest2(int[] dest, int[] src,
                                     long start1, long stop1,
                                     long start2, long stop2,
                                     long start3, long stop3,
                                     long start4, long stop4,
                                     long start5, long stop5) {
        if (src == null || dest == null) {
        }
        for (long i = start1; i < stop1; i++) {
            for (long j = start2; j < stop2; j++) {
                for (long k = start3; k < stop3; k++) {
                    for (long l = start4; l < stop4; l++) {
                        for (long m = start5; m < stop5; m++) {
                            long invar = i + j + k + l + m;
                            for (int n = 0; n < src.length - (int)invar; n++) {
                                UNSAFE.putInt(dest, UNSAFE.ARRAY_INT_BASE_OFFSET + (n + invar) * UNSAFE.ARRAY_INT_INDEX_SCALE, UNSAFE.getInt(src, UNSAFE.ARRAY_INT_BASE_OFFSET + (n + invar) * UNSAFE.ARRAY_INT_INDEX_SCALE));
                            }
                        }
                    }
                }
            }
        }
    }

    @Run(test = "testLoopNest2")
    public static void testLoopNest2_runner() {
        testLoopNest2(intArray, intArray, 0, 2, 0, 2, 0, 2, 0, 2, 0, 2);
    }
}
