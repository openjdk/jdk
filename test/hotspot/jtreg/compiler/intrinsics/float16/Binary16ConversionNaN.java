/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289551 8302976
 * @summary Verify NaN sign and significand bits are preserved across conversions
 * @requires (vm.cpu.features ~= ".*avx512vl.*" | vm.cpu.features ~= ".*f16c.*") | os.arch=="aarch64"
 *           | (os.arch == "riscv64" & vm.cpu.features ~= ".*zfh,.*")
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xmixed -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileThresholdScaling=1000.0 Binary16ConversionNaN
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

public class Binary16ConversionNaN {

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    /*
     * Put all 16-bit NaN values through a conversion loop and make
     * sure the significand, sign, and exponent are all preserved.
     */
    public static void main(String... argv) throws NoSuchMethodException {
        int errors = 0;
        final int NAN_EXPONENT = 0x7c00;
        final int SIGN_BIT     = 0x8000;

        // First, run with Interpreter only to  collect "gold" data.
        // Glags -Xmixed -XX:CompileThresholdScaling=1000.0 are used
        // to prevent compilation during this phase.
        short[] pVal = new short[1024];
        short[] pRes = new short[1024];
        short[] nVal = new short[1024];
        short[] nRes = new short[1024];

        // A NaN has a nonzero significand
        for (int i = 1; i <= 0x3ff; i++) {
            short binary16NaN = (short)(NAN_EXPONENT | i);
            assert isNaN(binary16NaN);
            short s1 = testRoundTrip(binary16NaN);
            errors  += verify(binary16NaN, s1);
            pVal[i] = binary16NaN;
            pRes[i] = s1;

            short binary16NegNaN = (short)(SIGN_BIT | binary16NaN);
            short s2 = testRoundTrip(binary16NegNaN);
            errors  += verify(binary16NegNaN, s2);
            nVal[i] = binary16NegNaN;
            nRes[i] = s2;
        }
        if (errors > 0) { // Exit if Interpreter failed
            throw new RuntimeException(errors + " errors");
        }

        Method test_method = Binary16ConversionNaN.class.getDeclaredMethod("testRoundTrip", short.class);

        // Compile with C1 and compare results
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
        if (!WHITE_BOX.isMethodCompiled(test_method)) {
            throw new RuntimeException("test is not compiled by C1");
        }
        for (int i = 1; i <= 0x3ff; i++) {
            short s1 = testRoundTrip(pVal[i]);
            errors  += verifyCompiler(pRes[i], s1, "C1");
            short s2 = testRoundTrip(nVal[i]);
            errors  += verifyCompiler(nRes[i], s2, "C1");
        }

        WHITE_BOX.deoptimizeMethod(test_method);

        // Compile with C2 and compare results
        WHITE_BOX.enqueueMethodForCompilation(test_method, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        if (!WHITE_BOX.isMethodCompiled(test_method)) {
            throw new RuntimeException("test is not compiled by C2");
        }
        for (int i = 1; i <= 0x3ff; i++) {
            short s1 = testRoundTrip(pVal[i]);
            errors  += verifyCompiler(pRes[i], s1, "C2");
            short s2 = testRoundTrip(nVal[i]);
            errors  += verifyCompiler(nRes[i], s2, "C2");
        }

        if (errors > 0) {
            throw new RuntimeException(errors + " errors");
        }
    }

    private static boolean isNaN(short binary16) {
        return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and...
            && ((binary16 & 0x03ff) != 0 );    // significand nonzero.
    }

    private static short testRoundTrip(short i) {
        float f =  Float.float16ToFloat(i);
        return Float.floatToFloat16(f);
    }

    private static int verify(short s, short s2) {
        int errors = 0;
        if ((s & ~0x0200) != (s2 & ~0x0200)) { // ignore QNaN bit
            errors++;
            System.out.println("Roundtrip failure on NaN value " +
                               Integer.toHexString(0xFFFF & (int)s) +
                               "\t got back " + Integer.toHexString(0xFFFF & (int)s2));
        }
        return errors;
    }

    private static int verifyCompiler(short s, short s2, String name) {
        int errors = 0;
        if (s != s2) {
            errors++;
            System.out.println("Roundtrip failure on NaN value " +
                               Integer.toHexString(0xFFFF & (int)s) +
                               "\t got back " + Integer.toHexString(0xFFFF & (int)s2) +
                               "\t from " + name + " code");
        }
        return errors;
    }
}
