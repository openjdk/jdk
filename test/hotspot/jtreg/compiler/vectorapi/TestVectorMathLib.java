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

package compiler.vectorapi;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8367333
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @library /test/lib
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+StressIncrementalInlining
 *                   -XX:CompileCommand=quiet
 *                   -XX:CompileCommand=compileonly,compiler.vectorapi.TestVectorMathLib::test*
 *                   compiler.vectorapi.TestVectorMathLib
 */

public class TestVectorMathLib {
    private static final VectorSpecies SPECIES = FloatVector.SPECIES_PREFERRED;

    static FloatVector testTAN(FloatVector fv) {
        return fv.lanewise(VectorOperators.TAN);
    }
    static FloatVector testTANH(FloatVector fv) {
        return fv.lanewise(VectorOperators.TANH);
    }
    static FloatVector testSIN(FloatVector fv) {
        return fv.lanewise(VectorOperators.SIN);
    }
    static FloatVector testSINH(FloatVector fv) {
        return fv.lanewise(VectorOperators.SINH);
    }
    static FloatVector testCOS(FloatVector fv) {
        return fv.lanewise(VectorOperators.COS);
    }
    static FloatVector testCOSH(FloatVector fv) {
        return fv.lanewise(VectorOperators.COSH);
    }
    static FloatVector testASIN(FloatVector fv) {
        return fv.lanewise(VectorOperators.ASIN);
    }
    static FloatVector testACOS(FloatVector fv) {
        return fv.lanewise(VectorOperators.ACOS);
    }
    static FloatVector testATAN(FloatVector fv) {
        return fv.lanewise(VectorOperators.ATAN);
    }
    static FloatVector testATAN2(FloatVector fv) {
        return fv.lanewise(VectorOperators.ATAN2, fv);
    }
    static FloatVector testCBRT(FloatVector fv) {
        return fv.lanewise(VectorOperators.CBRT);
    }
    static FloatVector testLOG(FloatVector fv) {
        return fv.lanewise(VectorOperators.LOG);
    }
    static FloatVector testLOG10(FloatVector fv) {
        return fv.lanewise(VectorOperators.LOG10);
    }
    static FloatVector testLOG1P(FloatVector fv) {
        return fv.lanewise(VectorOperators.LOG1P);
    }
    static FloatVector testPOW(FloatVector fv) {
        return fv.lanewise(VectorOperators.POW, fv);
    }
    static FloatVector testEXP(FloatVector fv) {
        return fv.lanewise(VectorOperators.EXP);
    }
    static FloatVector testEXPM1(FloatVector fv) {
        return fv.lanewise(VectorOperators.EXPM1);
    }
    static FloatVector testHYPOT(FloatVector fv) {
        return fv.lanewise(VectorOperators.HYPOT, fv);
    }

    public static void main(String[] args) {
        FloatVector z = FloatVector.zero(SPECIES);
        for (int i = 0; i < 20_000; i++) {
            z.neg();  // unary
            z.add(z); // binary

            testTAN(z);
            testTANH(z);
            testSIN(z);
            testSINH(z);
            testCOS(z);
            testCOSH(z);
            testASIN(z);
            testACOS(z);
            testATAN(z);
            testATAN2(z);
            testCBRT(z);
            testLOG(z);
            testLOG10(z);
            testLOG1P(z);
            testPOW(z);
            testEXP(z);
            testEXPM1(z);
            testHYPOT(z);
        }

        System.out.println("TEST PASSED");
    }
}

