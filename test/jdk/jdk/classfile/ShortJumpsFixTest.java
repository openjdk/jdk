/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Testing ClassFile short to long jumps extension.
 * @run junit ShortJumpsFixTest
 */
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.LinkedList;
import java.util.List;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import static java.lang.classfile.Opcode.*;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.NopInstruction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ShortJumpsFixTest {

    record Sample(Opcode jumpCode, Opcode... expected) {
        @Override
        public String toString() {
            return jumpCode.name();
        }
    }

    static Sample[] provideFwd()  {
        return new Sample[]{
            //first is transformed opcode, followed by constant instructions and expected output
            new Sample(GOTO, GOTO_W, NOP, ATHROW, RETURN),
            new Sample(IFEQ, ICONST_0, IFNE, GOTO_W, NOP, RETURN),
            new Sample(IFNE, ICONST_0, IFEQ, GOTO_W, NOP, RETURN),
            new Sample(IFLT, ICONST_0, IFGE, GOTO_W, NOP, RETURN),
            new Sample(IFGE, ICONST_0, IFLT, GOTO_W, NOP, RETURN),
            new Sample(IFGT, ICONST_0, IFLE, GOTO_W, NOP, RETURN),
            new Sample(IFLE, ICONST_0, IFGT, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPEQ, ICONST_0, ICONST_1, IF_ICMPNE, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPNE, ICONST_0, ICONST_1, IF_ICMPEQ, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPLT, ICONST_0, ICONST_1, IF_ICMPGE, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPGE, ICONST_0, ICONST_1, IF_ICMPLT, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPGT, ICONST_0, ICONST_1, IF_ICMPLE, GOTO_W, NOP, RETURN),
            new Sample(IF_ICMPLE, ICONST_0, ICONST_1, IF_ICMPGT, GOTO_W, NOP, RETURN),
            new Sample(IF_ACMPEQ, ICONST_0, ICONST_1, IF_ACMPNE, GOTO_W, NOP, RETURN),
            new Sample(IF_ACMPNE, ICONST_0, ICONST_1, IF_ACMPEQ, GOTO_W, NOP, RETURN),
            new Sample(IFNULL, ACONST_NULL, IFNONNULL, GOTO_W, NOP, RETURN),
            new Sample(IFNONNULL, ACONST_NULL, IFNULL, GOTO_W, NOP, RETURN),
        };
    }

    static Sample[] provideBack()  {
        return new Sample[]{
            new Sample(GOTO, GOTO_W, NOP, RETURN, GOTO_W, ATHROW),
            new Sample(IFEQ, GOTO_W, NOP, RETURN, ICONST_0, IFNE, GOTO_W, RETURN),
            new Sample(IFNE, GOTO_W, NOP, RETURN, ICONST_0, IFEQ, GOTO_W, RETURN),
            new Sample(IFLT, GOTO_W, NOP, RETURN, ICONST_0, IFGE, GOTO_W, RETURN),
            new Sample(IFGE, GOTO_W, NOP, RETURN, ICONST_0, IFLT, GOTO_W, RETURN),
            new Sample(IFGT, GOTO_W, NOP, RETURN, ICONST_0, IFLE, GOTO_W, RETURN),
            new Sample(IFLE, GOTO_W, NOP, RETURN, ICONST_0, IFGT, GOTO_W, RETURN),
            new Sample(IF_ICMPEQ, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPNE, GOTO_W, RETURN),
            new Sample(IF_ICMPNE, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPEQ, GOTO_W, RETURN),
            new Sample(IF_ICMPLT, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPGE, GOTO_W, RETURN),
            new Sample(IF_ICMPGE, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPLT, GOTO_W, RETURN),
            new Sample(IF_ICMPGT, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPLE, GOTO_W, RETURN),
            new Sample(IF_ICMPLE, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ICMPGT, GOTO_W, RETURN),
            new Sample(IF_ACMPEQ, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ACMPNE, GOTO_W, RETURN),
            new Sample(IF_ACMPNE, GOTO_W, NOP, RETURN, ICONST_0, ICONST_1, IF_ACMPEQ, GOTO_W, RETURN),
            new Sample(IFNULL, GOTO_W, NOP, RETURN, ACONST_NULL, IFNONNULL, GOTO_W, RETURN),
            new Sample(IFNONNULL, GOTO_W, NOP, RETURN, ACONST_NULL, IFNULL, GOTO_W, RETURN),
        };
    }

    static final ClassFile
            CC_Fixed_Jumps = ClassFile.of(ClassFile.ShortJumpsOption.FIX_SHORT_JUMPS),
            CC_Not_Fixed_Jumps = ClassFile.of(ClassFile.ShortJumpsOption.FAIL_ON_SHORT_JUMPS),
            CC_No_Stack_No_Patch = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS,
                                                        ClassFile.DeadCodeOption.KEEP_DEAD_CODE);

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFixFwdJumpsDirectGen(Sample sample) throws Exception {
        assertFixed(sample,
                    generateFwd(CC_Fixed_Jumps, sample, true));
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFixBackJumpsDirectGen(Sample sample) throws Exception {
        assertFixed(sample,
                    generateBack(CC_Fixed_Jumps, sample, true));
    }

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFailFwdJumpsDirectGen(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     generateFwd(CC_Not_Fixed_Jumps, sample, true));
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFailBackJumpsDirectGen(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     generateBack(CC_Not_Fixed_Jumps, sample, true));
    }

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFixFwdJumpsTransform(Sample sample) throws Exception {
        assertFixed(sample,
                    CC_Fixed_Jumps.transform(
                            generateFwd(CC_No_Stack_No_Patch, sample, false),
                            overflow()));
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFixBackJumpsTransform(Sample sample) throws Exception {
        assertFixed(sample,
                    CC_Fixed_Jumps.transform(
                            generateBack(CC_No_Stack_No_Patch, sample, false),
                            overflow()));
    }

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFailFwdJumpsTransform(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     CC_Not_Fixed_Jumps.transform(
                            generateFwd(CC_No_Stack_No_Patch, sample, false),
                            overflow()));
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFailBackJumpsTransform(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     CC_Not_Fixed_Jumps.transform(
                            generateBack(CC_No_Stack_No_Patch, sample, false),
                            overflow()));
    }

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFixFwdJumpsChainedTransform(Sample sample) throws Exception {
        assertFixed(sample,
                    CC_Fixed_Jumps.transform(
                            generateFwd(CC_No_Stack_No_Patch, sample, false),
                            ClassTransform.ACCEPT_ALL.andThen(overflow()))); //involve BufferedCodeBuilder here
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFixBackJumpsChainedTransform(Sample sample) throws Exception {
        assertFixed(sample,
                    CC_Fixed_Jumps.transform(
                            generateBack(CC_No_Stack_No_Patch, sample, false),
                            ClassTransform.ACCEPT_ALL.andThen(overflow()))); //involve BufferedCodeBuilder here
    }

    @ParameterizedTest
    @MethodSource("provideFwd")
    void testFailFwdJumpsChainedTransform(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     CC_Not_Fixed_Jumps.transform(
                            generateFwd(CC_No_Stack_No_Patch, sample, false),
                            ClassTransform.ACCEPT_ALL.andThen(overflow()))); //involve BufferedCodeBuilder here
    }

    @ParameterizedTest
    @MethodSource("provideBack")
    void testFailBackJumpsChainedTransform(Sample sample) throws Exception {
        assertThrows(IllegalArgumentException.class, () ->
                     CC_Not_Fixed_Jumps.transform(
                            generateBack(CC_No_Stack_No_Patch, sample, false),
                            ClassTransform.ACCEPT_ALL.andThen(overflow()))); //involve BufferedCodeBuilder here
    }

    private static ClassModel generateFwd(ClassFile cc, Sample sample, boolean overflow) {
        return cc.parse(cc.build(ClassDesc.of("WhateverClass"),
                        cb -> cb.withMethod("whateverMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0,
                                mb -> mb.withCode(cob -> {
                                    for (int i = 0; i < sample.expected.length - 4; i++) //cherry-pick XCONST_ instructions from expected output
                                        cob.with(ConstantInstruction.ofIntrinsic(sample.expected[i]));
                                    var target = cob.newLabel();
                                    cob.branchInstruction(sample.jumpCode, target);
                                    for (int i = overflow ? 40000 : 1; i > 0; i--)
                                        cob.nopInstruction();
                                    cob.labelBinding(target);
                                    cob.return_();
                                }))));
    }

    private static ClassModel generateBack(ClassFile cc, Sample sample, boolean overflow) {
        return cc.parse(cc.build(ClassDesc.of("WhateverClass"),
                        cb -> cb.withMethod("whateverMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0,
                                mb -> mb.withCode(cob -> {
                                    var target = cob.newLabel();
                                    var fwd = cob.newLabel();
                                    cob.goto_w(fwd);
                                    cob.labelBinding(target);
                                    for (int i = overflow ? 40000 : 1; i > 0; i--)
                                        cob.nopInstruction();
                                    cob.return_();
                                    cob.labelBinding(fwd);
                                    for (int i = 3; i < sample.expected.length - 3; i++) //cherry-pick XCONST_ instructions from expected output
                                        cob.with(ConstantInstruction.ofIntrinsic(sample.expected[i]));
                                    cob.branchInstruction(sample.jumpCode, target);
                                    cob.return_();
                                }))));
    }

    private static ClassTransform overflow() {
        return ClassTransform.transformingMethods(
                        MethodTransform.transformingCode(
                                (cob, coe) -> {
                                    if (coe instanceof NopInstruction)
                                        for (int i = 0; i < 40000; i++) //cause label overflow during transform
                                            cob.nopInstruction();
                                    cob.with(coe);
                                }));
    }

    private static void assertFixed(Sample sample, byte[] classFile) {
        assertFixed(sample, ClassFile.of().parse(classFile));
    }

    private static void assertFixed(Sample sample, ClassModel clm) {
        var found = new LinkedList<Opcode>();
        for (var e : clm.methods().get(0).code().get())
            if (e instanceof Instruction i && found.peekLast() != i.opcode()) //dedup subsequent (NOPs)
                found.add(i.opcode());
        assertEquals(found, List.of(sample.expected));
    }
}
