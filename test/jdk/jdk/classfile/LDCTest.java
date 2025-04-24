/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342458
 * @library /test/lib
 * @summary Testing ClassFile LDC instructions.
 * @run junit LDCTest
 */

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;

import jdk.test.lib.ByteCodeLoader;
import org.junit.jupiter.api.Test;

import static java.lang.classfile.ClassFile.*;
import static java.lang.classfile.Opcode.*;
import static java.lang.constant.ConstantDescs.*;
import static org.junit.jupiter.api.Assertions.*;

class LDCTest {
    @Test
    void loadConstantGeneralTest() throws Exception {
        var otherCp = ConstantPoolBuilder.of();
        var narrowString131 = otherCp.stringEntry("string131");
        assertTrue(narrowString131.index() <= 0xFF);
        for (int i = 0; i < 0xFF; i++) {
            var unused = otherCp.intEntry(i);
        }
        var wideString0 = otherCp.stringEntry("string0");
        assertTrue(wideString0.index() > 0xFF);

        var cc = ClassFile.of();
        var cd = ClassDesc.of("MyClass");
        MethodTypeDesc bsmType = MethodTypeDesc.of(CD_double, CD_MethodHandles_Lookup, CD_String, CD_Class);
        byte[] bytes = cc.build(cd, cb -> cb
                .withFlags(AccessFlag.PUBLIC)
                .withVersion(JAVA_11_VERSION, 0) // condy support required
                .withMethodBody("bsm", bsmType, ACC_STATIC, cob -> cob
                    .dconst_1()
                    .dreturn())
                .withMethodBody("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                        ACC_PUBLIC | ACC_STATIC, c0 -> {
                            ConstantPoolBuilder cpb = cb.constantPool();
                            LongEntry l42 = cpb.longEntry(42);
                            assertTrue(l42.index() <= 0xFF);
                            for (int i = 0; i <= 256 / 2 + 2; i++) { // two entries per String
                                StringEntry s = cpb.stringEntry("string" + i);
                            }
                            var wideCondy = cpb.constantDynamicEntry(DynamicConstantDesc.of(MethodHandleDesc.ofMethod(
                                    DirectMethodHandleDesc.Kind.STATIC, cd, "bsm", bsmType)));
                            assertTrue(wideCondy.index() > 0xFF);
                            var s0 = cpb.stringEntry("string0");
                            assertTrue(s0.index() <= 0xFF);
                            // use line number to match case numbers; pop ensures verification passes
                            c0.ldc("string0").pop() // regular ldc
                              .ldc(wideString0).pop() // adaption - narrowed
                              .with(ConstantInstruction.ofLoad(LDC, wideString0)).pop() // adaption
                              .with(ConstantInstruction.ofLoad(LDC_W, wideString0)).pop() // adaption - narrowed
                              .with(ConstantInstruction.ofLoad(LDC_W, s0)).pop() // explicit ldc_w - local
                              .ldc("string131").pop() // ldc_w
                              .ldc(narrowString131).pop() // adaption - widened
                              .with(ConstantInstruction.ofLoad(LDC, narrowString131)).pop() // adaption - widened
                              .with(ConstantInstruction.ofLoad(LDC_W, narrowString131)).pop() // adaption
                              .ldc("string50").pop()
                              .ldc(l42).pop2() // long cases
                              .loadConstant(l42.longValue()).pop2()
                              .loadConstant(Long.valueOf(l42.longValue())).pop2()
                              .loadConstant(-0.0f).pop() // floating cases
                              .loadConstant(-0.0d).pop2()
                              .loadConstant(0.0f).pop() // intrinsic cases
                              .loadConstant(0.0d).pop2()
                              .ldc(wideCondy).pop2() // no wrong "widening" of condy
                              .return_();
                        }));

        var cm = cc.parse(bytes);
        var code = cm.elementStream()
                .<CodeAttribute>mapMulti((ce, sink) -> {
                    if (ce instanceof MethodModel mm && mm.methodName().equalsString("main")) {
                        sink.accept(mm.findAttribute(Attributes.code()).orElseThrow());
                    }
                })
                .findFirst()
                .orElseThrow();
        var instructions = code.elementList().stream()
                .<ConstantInstruction>mapMulti((ce, sink) -> {
                    if (ce instanceof ConstantInstruction i) {
                        sink.accept(i);
                    }
                })
                .toList();

        assertIterableEquals(List.of(
                LDC, // string0
                LDC,
                LDC,
                LDC,
                LDC_W,
                LDC_W, // string131
                LDC_W,
                LDC_W,
                LDC_W,
                LDC, // string50
                LDC2_W, // long cases
                LDC2_W,
                LDC2_W,
                LDC_W, // floating cases
                LDC2_W,
                FCONST_0, // intrinsic cases
                DCONST_0,
                LDC2_W // wide condy
        ), instructions.stream().map(Instruction::opcode).toList());

        int longCaseStart = 10;
        for (int longCaseIndex = longCaseStart; longCaseIndex < longCaseStart + 3; longCaseIndex++) {
            var message = "Case " + longCaseIndex;
            assertEquals(42, (long) instructions.get(longCaseIndex).constantValue(), message);
        }

        int floatingCaseStart = longCaseStart + 3;
        assertEquals(
                Float.floatToRawIntBits((float) instructions.get(floatingCaseStart).constantValue()),
                Float.floatToRawIntBits(-0.0f));
        assertEquals(
                Double.doubleToRawLongBits((double) instructions.get(floatingCaseStart + 1).constantValue()),
                Double.doubleToRawLongBits(-0.0d));

        assertDoesNotThrow(() -> ByteCodeLoader.load("MyClass", bytes), "Invalid LDC bytecode generated");
    }
}
