/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393488
 * @summary Verify float16ToFloat ignores bits above the low 16 bits of its argument
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @run main/othervm -Xverify:all -Xint -XX:+UnlockDiagnosticVMOptions
 *                   -XX:DisableIntrinsic=_float16ToFloat TestFloat16ToFloatUnnormalized
 * @run main/othervm -Xverify:all -Xint TestFloat16ToFloatUnnormalized
 * @run main/othervm -Xverify:all -Xcomp -Xbatch -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,UnnormalizedFloat16::convert TestFloat16ToFloatUnnormalized
 * @run main/othervm -Xverify:all -Xcomp -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,UnnormalizedFloat16::convert TestFloat16ToFloatUnnormalized
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_short;

public class TestFloat16ToFloatUnnormalized {
    private static final int[] HIGH_BITS = {
        0x0000_0000, 0xffff_0000, 0x8000_0000,
        0x0004_0000, 0x5555_0000, 0xaaaa_0000
    };

    public static void main(String[] args) throws Throwable {
        byte[] bytes = ClassFile.of().build(ClassDesc.of("UnnormalizedFloat16"), cb ->
            cb.withMethodBody("convert", MethodTypeDesc.of(CD_float, CD_int),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    // Unlike Java source, bytecode accepts int for short parameters; omit i2s to keep upper bits.
                    code.iload(0)
                        .invokestatic(ClassDesc.of("java.lang.Float"), "float16ToFloat",
                                      MethodTypeDesc.of(CD_float, CD_short))
                        .freturn();
                }));
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> generatedClass = lookup.defineClass(bytes);
        MethodHandle convert = lookup.findStatic(generatedClass, "convert",
                                                 MethodType.methodType(float.class, int.class));

        for (int lowBits = 0; lowBits <= 0xffff; lowBits++) {
            int expected = Float.floatToIntBits(Float.float16ToFloat((short) lowBits));
            for (int highBits : HIGH_BITS) {
                int input = highBits | lowBits;
                int actual = Float.floatToIntBits((float) convert.invokeExact(input));
                if (actual != expected) {
                    throw new AssertionError("input=0x%08x: expected=0x%08x, actual=0x%08x"
                                             .formatted(input, expected, actual));
                }
            }
        }
    }
}
