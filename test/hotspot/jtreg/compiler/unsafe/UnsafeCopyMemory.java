/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 *
 * @modules java.base/jdk.internal.misc
 *
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,compiler.unsafe.UnsafeCopyMemory::test*
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+StressGCM -XX:+StressLCM
 *                   compiler.unsafe.UnsafeCopyMemory
 */

package compiler.unsafe;

import jdk.internal.misc.Unsafe;
import static jdk.test.lib.Asserts.assertEQ;

public class UnsafeCopyMemory {
    static private Unsafe UNSAFE = Unsafe.getUnsafe();

    // On-heap arrays
    static int[] srcArr = new int[1];
    static int[] dstArr = new int[1];

    static int[] resArr = dstArr;

    static Object srcObj = srcArr;
    static Object dstObj = dstArr;

    static long[] dstArrL = new long[1];
    static long[] resArrL = dstArrL;

    // Native

    static long SRC_BASE = UNSAFE.allocateMemory(4);
    static long DST_BASE = UNSAFE.allocateMemory(4);

    static long RES_BASE = DST_BASE;

    static void reset() {
        resArr[0] = 0;
        UNSAFE.putInt(null, RES_BASE, 0);
    }

    /* ================================================================ */

    // Heap-to-heap

    static int testHeapToHeap(int v1, int v2, int v3, int v4, int readIdx, int writeIdx) {
        // assert(readIdx == writeIdx == 0);
        srcArr[readIdx]  = v1;
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(srcArr, Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstArr, Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        int r = resArr[0]; // snapshot

        srcArr[readIdx]  = v3;
        dstArr[writeIdx] = v4;

        return r;
    }

    static int testHeapToHeapMixed(int v1, int v2, int v3, int v4, int readIdx, int writeIdx) {
        // assert(readIdx == writeIdx == 0);
        srcArr[readIdx]  = v1;
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(srcObj, Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstObj, Unsafe.ARRAY_INT_BASE_OFFSET, 4); // mixed
        int r = resArr[0]; // snapshot

        srcArr[readIdx]  = v3;
        dstArr[writeIdx] = v4;

        return r;
    }

    static long testHeapToHeapMismatched(int v1, int v2, int v3, int v4, int readIdx, int writeIdx) {
        // assert(readIdx == writeIdx == 0);
        srcArr [readIdx]  = v1;
        dstArrL[writeIdx] = v2;

        UNSAFE.copyMemory(srcArr,  Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstArrL, Unsafe.ARRAY_LONG_BASE_OFFSET, 4); // mismatched
        long r = resArrL[0]; // snapshot

        srcArr[readIdx]  = v3;
        dstArrL[writeIdx] = v4;

        return r;
    }

    static int testHeapToHeapLocalSrc(int v1, int v2, int v3, int v4, int writeIdx) {
        // assert(writeIdx == 0);
        int[] srcArrLocal = new int[1];

        srcArrLocal[0] = v1;
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(srcArrLocal, Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstArr,      Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        int r = resArr[0]; // snapshot

        srcArrLocal[0] = v3;
        dstArr[writeIdx] = v4;

        return r;
    }

    static int testHeapToHeapLocalDst(int v1, int v2, int v3, int v4, int readIdx) {
        // assert(readIdx == 0);
        int[] dstArrLocal = new int[1];

        srcArr[readIdx] = v1;
        dstArrLocal[0] = v2;

        UNSAFE.copyMemory(srcArr,      Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstArrLocal, Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        int r = dstArrLocal[0]; // snapshot

        srcArr[readIdx] = v3;
        dstArrLocal[0] = v4;

        return r;
    }

    static int testHeapToHeapLocalSrcMismatched(int v1, int v2, int v3, int v4, int writeIdx, boolean flag) {
        // assert(writeIdx == 0);
        // assert(b == true);
        int[]  srcArrIntLocal  = new int[1];
        long[] srcArrLongLocal = new long[1];

        Object srcArrLocal = (flag ? srcArrIntLocal               : srcArrLongLocal);
        long   srcOffset   = (flag ? Unsafe.ARRAY_INT_BASE_OFFSET : Unsafe.ARRAY_LONG_BASE_OFFSET);

        srcArrIntLocal[0]  = v1;
        srcArrLongLocal[0] = v1;
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(srcArrLocal, srcOffset,
                          dstArr,      Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        int r = resArr[0]; // snapshot

        srcArrIntLocal[0]  = v3;
        srcArrLongLocal[0] = v3;
        dstArr[writeIdx] = v4;

        return r;
    }

    static int testHeapToHeapLocalDstMismatched(int v1, int v2, int v3, int v4, int readIdx, boolean flag) {
        // assert(readIdx == 0);
        int[]  dstArrIntLocal  = new int[1];
        long[] dstArrLongLocal = new long[1];

        Object dstArrLocal = (flag ? dstArrIntLocal               : dstArrLongLocal);
        long   dstOffset   = (flag ? Unsafe.ARRAY_INT_BASE_OFFSET : Unsafe.ARRAY_LONG_BASE_OFFSET);

        srcArr[readIdx] = v1;
        dstArrIntLocal[0]  = v2;
        dstArrLongLocal[0] = v2;

        UNSAFE.copyMemory(srcArr,     Unsafe.ARRAY_INT_BASE_OFFSET,
                          dstArrLocal, dstOffset, 4);
        int r = UNSAFE.getInt(dstArrLocal, dstOffset); // snapshot

        srcArr[readIdx] = v3;
        dstArrIntLocal[0]  = v4;
        dstArrLongLocal[0] = v4;

        return r;
    }

    /* ================================================================ */

    // Heap-to-native

    static int testHeapToNative(int v1, int v2, int v3, int v4, int readIdx) {
        // assert(readIdx == 0);
        srcArr[readIdx]  = v1;
        UNSAFE.putInt(null, DST_BASE, v2);

        UNSAFE.copyMemory(srcArr, Unsafe.ARRAY_INT_BASE_OFFSET,
                          null, DST_BASE, 4);
        int r = UNSAFE.getInt(RES_BASE); // snapshot

        srcArr[readIdx]  = v3;
        UNSAFE.putInt(null, DST_BASE, v4);

        return r;
    }

    static int testHeapToNativeMixed(int v1, int v2, int v3, int v4, int readIdx) {
        // assert(readIdx == 0);
        srcArr[readIdx]  = v1;
        UNSAFE.putInt(null, DST_BASE, v2);

        UNSAFE.copyMemory(srcObj, Unsafe.ARRAY_INT_BASE_OFFSET,
                          null, DST_BASE, 4); // mixed
        int r = UNSAFE.getInt(RES_BASE); // snapshot

        srcArr[readIdx]  = v3;
        UNSAFE.putInt(null, DST_BASE, v4);

        return r;
    }

    /* ================================================================ */

    // Native-to-heap

    static int testNativeToHeap(int v1, int v2, int v3, int v4, int writeIdx) {
        // assert(writeIdx == 0);
        UNSAFE.putInt(null, SRC_BASE, v1);
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(null, SRC_BASE,
                          dstArr, Unsafe.ARRAY_INT_BASE_OFFSET, 4);
        int r = resArr[0]; // snapshot

        UNSAFE.putInt(null, SRC_BASE, v3);
        dstArr[writeIdx] = v4;

        return r;
    }

    static int testNativeToHeapArrMixed(int v1, int v2, int v3, int v4, int writeIdx) {
        // assert(writeIdx == 0);
        UNSAFE.putInt(null, SRC_BASE, v1);
        dstArr[writeIdx] = v2;

        UNSAFE.copyMemory(null, SRC_BASE,
                          dstObj, Unsafe.ARRAY_INT_BASE_OFFSET, 4); // mixed dst
        int r = resArr[0]; // snapshot

        UNSAFE.putInt(null, SRC_BASE, v3);
        dstArr[writeIdx] = v4;

        return r;
    }

    /* ================================================================ */

    // Native-to-native

    static int testNativeToNative(int v1, int v2, int v3, int v4) {
        UNSAFE.putInt(null, SRC_BASE, v1);
        UNSAFE.putInt(null, DST_BASE, v2);

        UNSAFE.copyMemory(null, SRC_BASE,
                          null, DST_BASE, 4);
        int r = UNSAFE.getInt(RES_BASE); // snapshot

        UNSAFE.putInt(null, SRC_BASE, v3);
        UNSAFE.putInt(null, DST_BASE, v4);

        return r;
    }

    static int testNativeToNativeMixed(int v1, int v2, int v3, int v4, Object base) {
        // assert(base == null);
        UNSAFE.putInt(null, SRC_BASE, v1);
        UNSAFE.putInt(null, DST_BASE, v2);

        UNSAFE.copyMemory(base, SRC_BASE,
                          base, DST_BASE, 4); // mixed
        int r = UNSAFE.getInt(RES_BASE); // snapshot

        UNSAFE.putInt(null, SRC_BASE, v3);
        UNSAFE.putInt(null, DST_BASE, v4);

        return r;
    }

    /* ================================================================ */

    static int v1 = 1;
    static int v2 = 2;
    static int v3 = 3;
    static int v4 = 4;

    static int  readIdx0 = 0;
    static int writeIdx0 = 0;
    static Object nullBase = null;

    static void runTests(String msg) {
        boolean print = (msg != null);
        if (print) {
            System.out.println(msg);
        }
        {
            reset();
            int r1 = testHeapToHeap(v1, v2, v3, v4, readIdx0, writeIdx0);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testHeapToHeap: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r1 = testHeapToHeapMixed(v1, v2, v3, v4, readIdx0, writeIdx0);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testHeapToHeapMixed: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            long r1 = testHeapToHeapMismatched(v1, v2, v3, v4, readIdx0, writeIdx0);
            long r2 = resArrL[0];
            if (print) {
                System.out.println("testHeapToHeapMismatched: " + r1 + " " + r2);
            }
            assertEQ(r1, (long)v1); assertEQ(r2, (long)v4);
        }
        {
            reset();
            int r1 = testHeapToHeapLocalSrc(v1, v2, v3, v4, writeIdx0);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testHeapToHeapLocalSrc: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r = testHeapToHeapLocalDst(v1, v2, v3, v4, readIdx0);
            if (print) {
                System.out.println("testHeapToHeapLocalDst: " + r);
            }
            assertEQ(r, v1);
        }
        {
            reset();
            int r1 = testHeapToHeapLocalSrcMismatched(v1, v2, v3, v4, writeIdx0, flag);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testHeapToHeapLocalSrcMismatched: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r = testHeapToHeapLocalDstMismatched(v1, v2, v3, v4, readIdx0, flag);
            if (print) {
                System.out.println("testHeapToHeapLocalDstMismatched: " + r);
            }
            assertEQ(r, v1);
        }
        {
            reset();
            int r1 = testHeapToNative(v1, v2, v3, v4, readIdx0);
            int r2 = UNSAFE.getInt(null, RES_BASE);
            if (print) {
                System.out.println("testHeapToNative: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r1 = testHeapToNativeMixed(v1, v2, v3, v4, readIdx0);
            int r2 = UNSAFE.getInt(null, RES_BASE);
            if (print) {
                System.out.println("testHeapToNativeMixed: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }

        // Native-to-*
        {
            reset();
            int r1 = testNativeToHeap(v1, v2, v3, v4, writeIdx0);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testNativeToHeap: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r1 = testNativeToHeapArrMixed(v1, v2, v3, v4, writeIdx0);
            int r2 = resArr[0];
            if (print) {
                System.out.println("testNativeToHeapMixed: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r1 = testNativeToNative(v1, v2, v3, v4);
            int r2 = UNSAFE.getInt(null, RES_BASE);
            if (print) {
                System.out.println("testNativeToNative: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
        {
            reset();
            int r1 = testNativeToNativeMixed(v1, v2, v3, v4, nullBase);
            int r2 = UNSAFE.getInt(null, RES_BASE);
            if (print) {
                System.out.println("testNativeToNativeMixed: " + r1 + " " + r2);
            }
            assertEQ(r1, v1); assertEQ(r2, v4);
        }
    }

    static boolean flag = false;

    public static void main(String[] args) {
        runTests("INTERPRETED");
        for (int i = 0; i < 20_000; i++) {
            flag = (i % 2 == 0);
            runTests(null);
        }
        runTests("COMPILED");
    }
}
