/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
 * @bug 8365206
 * @summary Verify NaN sign and significand bits are preserved across conversions,
 *          float -> float16 -> float
 * @requires (os.arch == "riscv64" & vm.cpu.features ~= ".*zfh.*")
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xmixed -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileThresholdScaling=1000.0 Binary16ConversionNaN_2
 */

/*
 * The behavior tested below is an implementation property not
 * required by the specification. It would be acceptable for this
 * information to not be preserved (as long as a NaN is returned) if,
 * say, a intrinsified version using native hardware instructions
 * behaved differently.
 *
 * If that is the case, this test should be modified to disable
 * intrinsics or to otherwise not run on platforms with an differently
 * behaving intrinsic.
 */

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.whitebox.WhiteBox;
import java.lang.reflect.Method;
import java.util.Random;

public class Binary16ConversionNaN_2 {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    /*
     * Put all 16-bit NaN values through a conversion loop and make
     * sure the significand, sign, and exponent are all preserved.
     */
    public static void main(String... argv) throws NoSuchMethodException {
        int errors = 0;
        final int NAN_EXPONENT = 0x7f80_0000;
        final int SIGN_BIT     = 0x8000_0000;

        // First, run with Interpreter only to  collect "gold" data.
        // Glags -Xmixed -XX:CompileThresholdScaling=1000.0 are used
        // to prevent compilation during this phase.
        float[] pVal = new float[1024];
        float[] pRes = new float[1024];
        float[] nVal = new float[1024];
        float[] nRes = new float[1024];

        Random rand = new Random();

        // A NaN has a nonzero significand
        for (int i = 1; i <= 0x3ff; i++) {
            int shift = rand.nextInt(13+1);
            int binaryNaN = (NAN_EXPONENT | (i << shift));
            assert isNaN(binaryNaN);
            // the payloads of non-canonical NaNs are preserved.
            float f1 = Float.intBitsToFloat(binaryNaN);
            float f2 = testRoundTrip(f1);
            errors  += verify(f1, f2);
            pVal[i] = f1;
            pRes[i] = f2;

            int binaryNegNaN = (SIGN_BIT | binaryNaN);
            float f3 = Float.intBitsToFloat(binaryNegNaN);
            float f4 = testRoundTrip(f3);
            errors  += verify(f3, f4);
            nVal[i] = f3;
            nRes[i] = f4;
        }
        if (errors > 0) { // Exit if Interpreter failed
            throw new RuntimeException(errors + " errors");
        }

        Method test_method = Binary16ConversionNaN_2.class.getDeclaredMethod("testRoundTrip", float.class);

        // Compile with C1 and compare results
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
        if (!WHITE_BOX.isMethodCompiled(test_method)) {
            throw new RuntimeException("test is not compiled by C1");
        }
        for (int i = 1; i <= 0x3ff; i++) {
            float f1 = testRoundTrip(pVal[i]);
            errors  += verifyCompiler(pRes[i], f1, "C1");
            float f2 = testRoundTrip(nVal[i]);
            errors  += verifyCompiler(nRes[i], f2, "C1");
        }

        WHITE_BOX.deoptimizeMethod(test_method);

        // Compile with C2 and compare results
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(test_method)) {
            throw new RuntimeException("test is not compiled by C2");
        }
        for (int i = 1; i <= 0x3ff; i++) {
            float f1 = testRoundTrip(pVal[i]);
            errors  += verifyCompiler(pRes[i], f1, "C2");
            float f2 = testRoundTrip(nVal[i]);
            errors  += verifyCompiler(nRes[i], f2, "C2");
        }

        if (errors > 0) {
            throw new RuntimeException(errors + " errors");
        }
    }

    private static boolean isNaN(int binary) {
        return ((binary & 0x7f80_0000) == 0x7f80_0000) // Max exponent and...
            && ((binary & 0x007f_ffff) != 0 );         // significand nonzero.
    }

    private static float testRoundTrip(float f) {
        short s = Float.floatToFloat16(f);
        return Float.float16ToFloat(s);
    }

    private static int verify(float f1, float f2) {
        int errors = 0;
        int i1 = Float.floatToRawIntBits(f1);
        int i2 = Float.floatToRawIntBits(f2);
        assert Float.isNaN(f1);
        if (!Float.isNaN(f2) ||
            ((i1 & 0x8000_0000) != (i2 & 0x8000_0000))) {
            errors++;
            System.out.println("Roundtrip failure on NaN value " +
                               Integer.toHexString(i1) +
                               "\t got back " + Integer.toHexString(i2));
        }
        return errors;
    }

    private static int verifyCompiler(float f1, float f2, String name) {
        int errors = 0;
        int i1 = Float.floatToRawIntBits(f1);
        int i2 = Float.floatToRawIntBits(f2);
        assert Float.isNaN(f1);
        if (!Float.isNaN(f2) ||
            ((i1 & 0x8000_0000) != (i2 & 0x8000_0000))) {
            errors++;
            System.out.println("Roundtrip failure on NaN value " +
                               Integer.toHexString(i1) +
                               "\t got back " + Integer.toHexString(i2) +
                               "\t from " + name + " code");
        }
        return errors;
    }
}
