/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
 * @bug 8307572
 * @summary Verify vector register clobbering in some aarch64 intrinsics
 * @library /compiler/patches /test/lib
 * @build java.base/java.lang.Helper
 * @run main/othervm -Xbatch -XX:CompileThreshold=100 -XX:-TieredCompilation compiler.c2.aarch64.TestIntrinsicsRegStress
 */

package compiler.c2.aarch64;

import java.util.Arrays;

public class TestIntrinsicsRegStress {

    final int LENGTH = 1024;
    final int ITER = 10000;
    final int NUM = 32;

    byte[] ba;
    char[] ca;
    char[] cb;
    float[] fv;

    String str;
    String[] strings;
    String needle = "01234567890123456789";

    public void init() {
        ca = new char[LENGTH];
        fv = new float[NUM];
        strings = new String[NUM];
        for (int i = 0; i < LENGTH; i++) {
            ca[i] = (char) ('a' + i % NUM);
        }
        cb = ca.clone();
        str = new String(ca);
        for (int i = 0; i < NUM; i++) {
            fv[i] = 1;
        }
        for (int i = 0; i < NUM; i++) {
            strings[i] = str.substring(i) + needle;
        }
    }

    public void checkIndexOf(int iter) {
        float t0 = 0;
        float t1 = fv[1] * fv[0];
        float t2 = fv[2] * fv[0];
        float t3 = fv[3] * fv[0];
        float t4 = fv[4] * fv[0];
        float t5 = fv[5] * fv[0];
        float t6 = fv[6] * fv[0];
        float t7 = fv[7] * fv[0];
        float t8 = fv[8] * fv[0];
        float t9 = fv[9] * fv[0];
        float t10 = fv[10] * fv[0];
        float t11 = fv[11] * fv[0];
        float t12 = fv[12] * fv[0];
        float t13 = fv[13] * fv[0];
        float t14 = fv[14] * fv[0];
        float t15 = fv[15] * fv[0];
        float t16 = fv[16] * fv[0];
        float t17 = fv[17] * fv[0];
        float t18 = fv[18] * fv[0];
        float t19 = fv[19] * fv[0];
        float t20 = fv[20] * fv[0];
        float t21 = fv[21] * fv[0];
        float t22 = fv[22] * fv[0];
        float t23 = fv[23] * fv[0];
        float t24 = fv[24] * fv[0];
        float t25 = fv[25] * fv[0];
        float t26 = fv[26] * fv[0];
        float t27 = fv[27] * fv[0];
        float t28 = fv[28] * fv[0];
        float t29 = fv[29] * fv[0];
        float t30 = fv[30] * fv[0];

        int result = strings[iter % NUM].indexOf(needle);

        if (result > LENGTH - NUM / 2) {
            // Use fp registers as many as possible and try to make them
            // live across above intrinsic function.
            t0 += t1 - t2 + t3 - t4 + t5 - t6 + t7 - t8 + t9 - t10 + t11 - t12 + t13 - t14 + t15
                    - t16 + t17 - t18 + t19 - t20 + t21 - t22 + t23 - t24 + t25 - t26 + t27 - t28
                    + t29 - t30; // 0
        }
        fv[31] += t0 + t2 - t11 + t16 - t29;
    }

    public void testIndexOf() {
        for (int i = 0; i < ITER; i++) {
            checkIndexOf(i);
        }
    }

    public void checkArraysEquals() {
        float t0 = 0;
        float t1 = fv[1] * fv[0];
        float t2 = fv[2] * fv[0];
        float t3 = fv[3] * fv[0];
        float t4 = fv[4] * fv[0];
        float t5 = fv[5] * fv[0];
        float t6 = fv[6] * fv[0];
        float t7 = fv[7] * fv[0];
        float t8 = fv[8] * fv[0];
        float t9 = fv[9] * fv[0];
        float t10 = fv[10] * fv[0];
        float t11 = fv[11] * fv[0];
        float t12 = fv[12] * fv[0];
        float t13 = fv[13] * fv[0];
        float t14 = fv[14] * fv[0];
        float t15 = fv[15] * fv[0];
        float t16 = fv[16] * fv[0];
        float t17 = fv[17] * fv[0];
        float t18 = fv[18] * fv[0];
        float t19 = fv[19] * fv[0];
        float t20 = fv[20] * fv[0];
        float t21 = fv[21] * fv[0];
        float t22 = fv[22] * fv[0];
        float t23 = fv[23] * fv[0];
        float t24 = fv[24] * fv[0];
        float t25 = fv[25] * fv[0];
        float t26 = fv[26] * fv[0];
        float t27 = fv[27] * fv[0];
        float t28 = fv[28] * fv[0];
        float t29 = fv[29] * fv[0];
        float t30 = fv[30] * fv[0];

        if (Arrays.equals(ca, cb)) {
            // Use fp registers as many as possible and try to make them
            // live across above intrinsic function.
            t0 += t1 - t2 + t3 - t4 + t5 - t6 + t7 - t8 + t9 - t10 + t11 - t12 + t13 - t14 + t15
                    - t16 + t17 - t18 + t19 - t20 + t21 - t22 + t23 - t24 + t25 - t26 + t27 - t28
                    + t29 - t30; // 0
        }
        fv[31] += t0 + t2 - t11 + t16 - t29;
    }

    public void testArraysEquals() {
        for (int i = 0; i < ITER; i++) {
            checkArraysEquals();
        }
    }

    public void checkCompress(int iter) {
        float t0 = 0;
        float t1 = fv[1] * fv[0];
        float t2 = fv[2] * fv[0];
        float t3 = fv[3] * fv[0];
        float t4 = fv[4] * fv[0];
        float t5 = fv[5] * fv[0];
        float t6 = fv[6] * fv[0];
        float t7 = fv[7] * fv[0];
        float t8 = fv[8] * fv[0];
        float t9 = fv[9] * fv[0];
        float t10 = fv[10] * fv[0];
        float t11 = fv[11] * fv[0];
        float t12 = fv[12] * fv[0];
        float t13 = fv[13] * fv[0];
        float t14 = fv[14] * fv[0];
        float t15 = fv[15] * fv[0];
        float t16 = fv[16] * fv[0];
        float t17 = fv[17] * fv[0];
        float t18 = fv[18] * fv[0];
        float t19 = fv[19] * fv[0];
        float t20 = fv[20] * fv[0];
        float t21 = fv[21] * fv[0];
        float t22 = fv[22] * fv[0];
        float t23 = fv[23] * fv[0];
        float t24 = fv[24] * fv[0];
        float t25 = fv[25] * fv[0];
        float t26 = fv[26] * fv[0];
        float t27 = fv[27] * fv[0];
        float t28 = fv[28] * fv[0];
        float t29 = fv[29] * fv[0];
        float t30 = fv[30] * fv[0];

        ba = Helper.compressChar(ca, 0, LENGTH, 0, LENGTH);

        if (ba[iter % LENGTH] > (byte) ('a' + 5)) {
            // Use fp registers as many as possible and try to make them
            // live across above intrinsic function.
            t0 += t1 - t2 + t3 - t4 + t5 - t6 + t7 - t8 + t9 - t10 + t11 - t12 + t13 - t14 + t15
                    - t16 + t17 - t18 + t19 - t20 + t21 - t22 + t23 - t24 + t25 - t26 + t27 - t28
                    + t29 - t30; // 0
        }
        fv[31] += t0 + t2 - t11 + t16 - t29;
    }

    public void testCompress() {
        for (int i = 0; i < ITER; i++) {
            checkCompress(i);
        }
    }

    public void checkInflate(int iter) {
        float t0 = 0;
        float t1 = fv[1] * fv[0];
        float t2 = fv[2] * fv[0];
        float t3 = fv[3] * fv[0];
        float t4 = fv[4] * fv[0];
        float t5 = fv[5] * fv[0];
        float t6 = fv[6] * fv[0];
        float t7 = fv[7] * fv[0];
        float t8 = fv[8] * fv[0];
        float t9 = fv[9] * fv[0];
        float t10 = fv[10] * fv[0];
        float t11 = fv[11] * fv[0];
        float t12 = fv[12] * fv[0];
        float t13 = fv[13] * fv[0];
        float t14 = fv[14] * fv[0];
        float t15 = fv[15] * fv[0];
        float t16 = fv[16] * fv[0];
        float t17 = fv[17] * fv[0];
        float t18 = fv[18] * fv[0];
        float t19 = fv[19] * fv[0];
        float t20 = fv[20] * fv[0];
        float t21 = fv[21] * fv[0];
        float t22 = fv[22] * fv[0];
        float t23 = fv[23] * fv[0];
        float t24 = fv[24] * fv[0];
        float t25 = fv[25] * fv[0];
        float t26 = fv[26] * fv[0];
        float t27 = fv[27] * fv[0];
        float t28 = fv[28] * fv[0];
        float t29 = fv[29] * fv[0];
        float t30 = fv[30] * fv[0];

        str.getChars(0, LENGTH, ca, 0);

        if (ca[iter % LENGTH] > (byte) ('a' + NUM / 2)) {
            // Use fp registers as many as possible and try to make them
            // live across above intrinsic function.
            t0 += t1 - t2 + t3 - t4 + t5 - t6 + t7 - t8 + t9 - t10 + t11 - t12 + t13 - t14 + t15
                    - t16 + t17 - t18 + t19 - t20 + t21 - t22 + t23 - t24 + t25 - t26 + t27 - t28
                    + t29 - t30; // 0
        }
        fv[31] += t0 + t2 - t11 + t16 - t29;
    }

    public void testInflate() {
        for (int i = 0; i < ITER; i++) {
            checkInflate(i);
        }
    }

    public void verifyAndReset() {
        if (fv[31] != 1.0) {
            throw new RuntimeException("Failed with " + Float.toString(fv[31]));
        } else {
            System.out.println("Success!");
        }
        fv[31] = 1.0f;
    }

    public static void main(String[] args) {
        TestIntrinsicsRegStress t = new TestIntrinsicsRegStress();
        t.init();

        t.testIndexOf();
        t.verifyAndReset();

        t.testArraysEquals();
        t.verifyAndReset();

        t.testCompress();
        t.verifyAndReset();

        t.testInflate();
        t.verifyAndReset();
    }
}
