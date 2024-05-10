/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8302976
 * @summary Verify conversion cons between float and the binary16 format
 * @requires (vm.cpu.features ~= ".*avx512vl.*" | vm.cpu.features ~= ".*f16c.*") | os.arch=="aarch64"
 *           | (os.arch == "riscv64" & vm.cpu.features ~= ".*zfh.*")
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @comment default run:
 * @run main TestConstFloat16ToFloat
 * @comment C1 JIT compilation only:
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,TestConstFloat16ToFloat::test* -XX:TieredStopAtLevel=1 TestConstFloat16ToFloat
 * @comment C2 JIT compilation only:
 * @run main/othervm -Xcomp -XX:CompileCommand=compileonly,TestConstFloat16ToFloat::test* -XX:-TieredCompilation TestConstFloat16ToFloat
 */

public class TestConstFloat16ToFloat {

    public static class Binary16 {
        public static final short POSITIVE_INFINITY = (short)0x7c00;
        public static final short MAX_VALUE         = 0x7bff;
        public static final short ONE               = 0x3c00;
        public static final short MIN_NORMAL        = 0x0400;
        public static final short MAX_SUBNORMAL     = 0x03ff;
        public static final short MIN_VALUE         = 0x0001;
        public static final short POSITIVE_ZERO     = 0x0000;
    }

    static final short[] sCon = {
        Short.MIN_VALUE,
        Short.MIN_VALUE + 1,
        -1,
        0,
        +1,
        Short.MAX_VALUE - 1,
        Short.MAX_VALUE,
        Binary16.MIN_VALUE,
        Binary16.MIN_NORMAL,
        Binary16.POSITIVE_ZERO,
        Binary16.ONE,
        Binary16.MAX_VALUE,
        Binary16.MAX_SUBNORMAL,
        Binary16.POSITIVE_INFINITY
    };

    public final static class BinaryF16 {
        public static final float POSITIVE_INFINITY = Float.POSITIVE_INFINITY;
        public static final float MAX_VALUE         = 65504.0f;
        public static final float ONE               = 1.0f;
        public static final float MIN_NORMAL        = 0x1.0p-14f;
        public static final float MAX_SUBNORMAL     = 0x1.ff8p-15f;
        public static final float MIN_VALUE         = 0x1.0p-24f;
        public static final float POSITIVE_ZERO     = +0x0f;
    }

    static float[] fCon = {
        0.0f - BinaryF16.POSITIVE_INFINITY,
        0.0f - BinaryF16.MAX_VALUE,
        0.0f - BinaryF16.MAX_SUBNORMAL,
        0.0f - BinaryF16.MIN_VALUE,
        0.0f - BinaryF16.MIN_NORMAL,
        -1.0f,
        -0.0f,
        BinaryF16.MIN_VALUE,
        BinaryF16.MIN_NORMAL,
        BinaryF16.POSITIVE_ZERO,
        BinaryF16.ONE,
        BinaryF16.MAX_VALUE,
        BinaryF16.MAX_SUBNORMAL,
        BinaryF16.POSITIVE_INFINITY
    };

    // Testing some constant values (optimized by C2).
    public static void testFloat16Const(float[] fRes) {
        fRes[ 0] = Float.float16ToFloat(Short.MIN_VALUE);
        fRes[ 1] = Float.float16ToFloat((short)(Short.MIN_VALUE + 1));
        fRes[ 2] = Float.float16ToFloat((short)-1);
        fRes[ 3] = Float.float16ToFloat((short)0);
        fRes[ 4] = Float.float16ToFloat((short)+1);
        fRes[ 5] = Float.float16ToFloat((short)(Short.MAX_VALUE - 1));
        fRes[ 6] = Float.float16ToFloat(Short.MAX_VALUE);
        fRes[ 7] = Float.float16ToFloat(Binary16.MIN_VALUE);
        fRes[ 8] = Float.float16ToFloat(Binary16.MIN_NORMAL);
        fRes[ 9] = Float.float16ToFloat(Binary16.POSITIVE_ZERO);
        fRes[10] = Float.float16ToFloat(Binary16.ONE);
        fRes[11] = Float.float16ToFloat(Binary16.MAX_VALUE);
        fRes[12] = Float.float16ToFloat(Binary16.MAX_SUBNORMAL);
        fRes[13] = Float.float16ToFloat(Binary16.POSITIVE_INFINITY);
    }

    public static void testFloatConst(short[] sRes) {
        sRes[ 0] = Float.floatToFloat16(0.0f - BinaryF16.POSITIVE_INFINITY);
        sRes[ 1] = Float.floatToFloat16(0.0f - BinaryF16.MAX_VALUE);
        sRes[ 2] = Float.floatToFloat16(0.0f - BinaryF16.MAX_SUBNORMAL);
        sRes[ 3] = Float.floatToFloat16(0.0f - BinaryF16.MIN_VALUE);
        sRes[ 4] = Float.floatToFloat16(0.0f - BinaryF16.MIN_NORMAL);
        sRes[ 5] = Float.floatToFloat16(-1.0f);
        sRes[ 6] = Float.floatToFloat16(-0.0f);
        sRes[ 7] = Float.floatToFloat16(BinaryF16.MIN_VALUE);
        sRes[ 8] = Float.floatToFloat16(BinaryF16.MIN_NORMAL);
        sRes[ 9] = Float.floatToFloat16(BinaryF16.POSITIVE_ZERO);
        sRes[10] = Float.floatToFloat16(BinaryF16.ONE);
        sRes[11] = Float.floatToFloat16(BinaryF16.MAX_VALUE);
        sRes[12] = Float.floatToFloat16(BinaryF16.MAX_SUBNORMAL);
        sRes[13] = Float.floatToFloat16(BinaryF16.POSITIVE_INFINITY);
    }

    public static int run() {
        int errors = 0;
        short s = Float.floatToFloat16(0.0f); // Load Float class
        // Testing constant float16 values.
        float[] fRes = new float[sCon.length];
        testFloat16Const(fRes);
        for (int i = 0; i < sCon.length; i++) {
            float fVal = Float.float16ToFloat(sCon[i]);
            if (Float.floatToRawIntBits(fRes[i]) != Float.floatToRawIntBits(fVal)) {
                errors++;
                String cVal_hex = Integer.toHexString(sCon[i] & 0xffff);
                String fRes_hex = Integer.toHexString(Float.floatToRawIntBits(fRes[i]));
                String fVal_hex = Integer.toHexString(Float.floatToRawIntBits(fVal));
                System.out.println("Inconsistent result for Float.float16ToFloat(" + cVal_hex + "): " +
                                    fRes[i] + "/" + fRes_hex + " != " + fVal + "/" + fVal_hex);
            }
        }

        // Testing constant float values.
        short[] sRes = new short[fCon.length];
        testFloatConst(sRes);
        for (int i = 0; i < fCon.length; i++) {
            short sVal = Float.floatToFloat16(fCon[i]);
            if (sRes[i] != sVal) {
                errors++;
                String cVal_hex = Integer.toHexString(Float.floatToRawIntBits(fCon[i]));
                String sRes_hex = Integer.toHexString(sRes[i] & 0xffff);
                String sVal_hex = Integer.toHexString(sVal & 0xffff);
                System.out.println("Inconsistent result for Float.floatToFloat16(" + fCon[i] + "/" + cVal_hex + "): " +
                                    sRes_hex + "(" + sRes + ")" + " != " + sVal_hex + "(" + sVal + ")");
            }
        }
        return errors;

    }

    public static void main(String[] args) {
        int errors = 0;
        // Run twice to trigger compilation
        for (int i = 0; i < 2; i++) {
            errors += run();
        }
        if (errors > 0) {
            throw new RuntimeException(errors + " errors");
        }
    }
}
