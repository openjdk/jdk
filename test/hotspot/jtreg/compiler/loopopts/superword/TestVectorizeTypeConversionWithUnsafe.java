/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8288883
 * @summary Tests auto-vectorization of type conversion with unsafe.
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -Xbatch compiler.loopopts.superword.TestVectorizeTypeConversionWithUnsafe
 */

package compiler.loopopts.superword;

import jdk.internal.misc.Unsafe;

public class TestVectorizeTypeConversionWithUnsafe {
    private static final int LENGTH = 1024;
    private static final int BUFFER_SIZE = LENGTH * 4;
    private static final int WARMUP = 10_000;
    private static final Unsafe unsafe;
    private static final long address;
    private static final long base_offset_ints;
    private static int[] srcptrs = new int[LENGTH];

    static {
        unsafe = Unsafe.getUnsafe();
        address = unsafe.allocateMemory(BUFFER_SIZE);
        base_offset_ints = unsafe.arrayBaseOffset(int[].class);
    }

    public static long conv(){
        long res = 0;
        int ecur;
        for (int i = 0; i < LENGTH; i++) {
            ecur = srcptrs[i];
            res += unsafe.getInt(address + ecur);
        }
        return res;
    }

    public static void main(String[] args) {
        for (int i = 0; i < WARMUP; i++) {
            conv();
        }

        for (int i = 0; i < LENGTH; i++) {
            srcptrs[i] = i * 4;
        }
        unsafe.copyMemory(srcptrs, base_offset_ints, null, address, BUFFER_SIZE);
        long res = conv();
        unsafe.freeMemory(address);

        if (res != 2095104) {
            throw new RuntimeException("Wrong result.");
        }
    }

}
