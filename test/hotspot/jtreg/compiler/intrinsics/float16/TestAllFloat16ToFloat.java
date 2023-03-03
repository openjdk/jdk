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
 * @summary Verify conversion between float and the binary16 format
 * @requires vm.cpu.features ~= ".*avx512vl.*" | vm.cpu.features ~= ".*f16c.*"
 * @comment default run:
 * @run main TestAllFloat16ToFloat
 * @comment disable intrinsics:
 * @run main/othervm -XX:DisableIntrinsic=_float16ToFloat,_floatToFloat16 TestAllFloat16ToFloat
 * @comment eager JIT compilation:
 * @run main/othervm -XX:CompileCommand=compileonly,TestAllFloat16ToFloat::test* -Xbatch TestAllFloat16ToFloat 
 * @comment C2 JIT compilation only:
 * @run main/othervm -XX:CompileCommand=compileonly,TestAllFloat16ToFloat::test* -Xbatch -XX:-TieredCompilation TestAllFloat16ToFloat
 * @comment C1 JIT compilation only:
 * @run main/othervm -XX:CompileCommand=compileonly,TestAllFloat16ToFloat::test* -Xbatch -XX:TieredStopAtLevel=1 TestAllFloat16ToFloat
 */

public class TestAllFloat16ToFloat {
    public static short testFloatToFloat16(float f) {
        return Float.floatToFloat16(f);
    }

    public static float testFloat16ToFloat(short s) {
        return Float.float16ToFloat(s);
    }

    public static short testRoundTrip(short s) {
        return Float.floatToFloat16(Float.float16ToFloat(s));
    }

    public static void verify(short sVal, float fVal, short sRes) {
        if (sRes != sVal) {
            if (!Float.isNaN(fVal) || ((sRes & ~0x0200) != (sVal & ~0x0200)) ) {
                String fVal_hex = Integer.toHexString(Float.floatToRawIntBits(fVal));
                String sRes_hex = Integer.toHexString(sRes & 0xffff);
                String sVal_hex = Integer.toHexString(sVal & 0xffff);
              throw new RuntimeException("Inconsistent result for Float.floatToFloat16(" + fVal + "/" + fVal_hex + "): " + sRes_hex + " != " + sVal_hex);
           }
        }
    }

    public static void run() {
        // Testing all float16 values.
        for (short sVal = Short.MIN_VALUE; sVal < Short.MAX_VALUE; ++sVal) {
            float fVal = Float.float16ToFloat(sVal);
            short sRes = testFloatToFloat16(fVal);
            verify(sVal, fVal, sRes);
            float fRes = testFloat16ToFloat(sVal);
            if (!Float.isNaN(fRes) && fRes != fVal) {
                String sVal_hex = Integer.toHexString(sVal & 0xffff);
                String fRes_hex = Integer.toHexString(Float.floatToRawIntBits(fRes));
                String fVal_hex = Integer.toHexString(Float.floatToRawIntBits(fVal));
                throw new RuntimeException("Inconsistent result for Float.float16ToFloat(" + sVal_hex + "): " + fRes + "/" + fRes_hex + " != " + fVal + "/" + fVal_hex);
            }
            sRes = testRoundTrip(sVal);
            verify(sVal, fVal, sRes);
            if (Float.floatToFloat16(fRes) != Float.floatToFloat16(fVal)) {
                String sVal_hex = Integer.toHexString(sVal & 0xffff);
                String sfRes_hex = Integer.toHexString(Float.floatToFloat16(fRes) & 0xffff);
                String sfVal_hex = Integer.toHexString(Float.floatToFloat16(fVal)& 0xffff);
                throw new RuntimeException("Inconsistent result for Float.float16ToFloat(" + sVal_hex + "): " + sfRes_hex + " != " + sfVal_hex);
            }
        }
    }

    public static void main(String[] args) {
        // Run twice to trigger compilation
        for (int i = 0; i < 2; i++) {
            run();
        }
    }
}
