/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test functionality of Verify implementations for vector incubator classes.
 *          All non-incubator cases are tested in TestVerify.java.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver verify.tests.TestVerifyIncubatorVector
 */

package verify.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;

import jdk.incubator.vector.Float16;

import compiler.lib.verify.*;

public class TestVerifyIncubatorVector {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testArrayFloat16();
        testRawFloat16();
    }

    public static void testArrayFloat16() {
        Float16[] a = new Float16[1000];
        Float16[] b = new Float16[1001];
        Float16[] c = new Float16[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        // Size mismatch
        checkNE(a, b);

        c[RANDOM.nextInt(c.length)] = Float16.valueOf(1f);

        // Value mismatch
        checkNE(a, c);
    }

    public static void testRawFloat16() {
        Float16 nan1 =  Float16.valueOf(Float.intBitsToFloat(0x7f800001));
        Float16 nan2 =  Float16.valueOf(Float.intBitsToFloat(0x7fffffff));

        //float[] arrF1 = new float[]{nanF1};
        //float[] arrF2 = new float[]{nanF2};

        //Verify.checkEQ(nanF1, Float.NaN);
        //Verify.checkEQ(nanF1, nanF1);
        //Verify.checkEQWithRawBits(nanF1, nanF1);
        Verify.checkEQ(nan1, nan2);

        //Verify.checkEQ(arrF1, arrF1);
        //Verify.checkEQWithRawBits(arrF1, arrF1);
        //Verify.checkEQ(arrF1, arrF2);

        //checkNEWithRawBits(nanF1, nanF2);

        //checkNEWithRawBits(arrF1, arrF2);
    }

    public static void checkNE(Object a, Object b) {
         try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }

    public static void checkNEWithRawBits(Object a, Object b) {
         try {
            Verify.checkEQWithRawBits(a, b);
            throw new RuntimeException("Should have thrown: " + a + " vs " + b);
        } catch (VerifyException e) {}
    }
}
