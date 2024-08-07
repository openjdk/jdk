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
 * @bug 8320360 8330684 8331320 8331655 8331940 8332486 8335820 8336833
 * @summary Testing ClassFile limits.
 * @run junit LimitsTest
 */
import java.lang.classfile.Attributes;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LineNumberInfo;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.ConstantPoolException;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.instruction.LocalVariable;
import java.util.List;

import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.DirectCodeBuilder;
import jdk.internal.classfile.impl.DirectMethodBuilder;
import jdk.internal.classfile.impl.LabelContext;
import jdk.internal.classfile.impl.UnboundAttribute;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitsTest {

    @Test
    void testCPSizeLimit() {
        ClassFile.of().build(ClassDesc.of("BigClass"), cb -> {
            for (int i = 1; i < 65000; i++) {
                cb.withField("field" + i, ConstantDescs.CD_int, fb -> {});
            }
        });
    }

    @Test
    void testCPOverLimit() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(ClassDesc.of("BigClass"), cb -> {
            for (int i = 1; i < 66000; i++) {
                cb.withField("field" + i, ConstantDescs.CD_int, fb -> {});
            }
        }));
    }

    @Test
    void testCodeOverLimit() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(ClassDesc.of("BigClass"), cb -> cb.withMethodBody(
                "bigMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> {
                    for (int i = 0; i < 65535; i++) {
                        cob.nop();
                    }
                    cob.return_();
                })));
    }

    @Test
    void testEmptyCode() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(ClassDesc.of("EmptyClass"), cb -> cb.withMethodBody(
                "emptyMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> {})));
    }

    @Test
    void testCodeRange() {
        var cf = ClassFile.of();
        var lc = (LabelContext)cf.parse(cf.build(ClassDesc.of("EmptyClass"), cb -> cb.withMethodBody(
                "aMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> cob.return_()))).methods().get(0).code().get();
        assertThrows(IllegalArgumentException.class, () -> lc.getLabel(-1));
        assertThrows(IllegalArgumentException.class, () -> lc.getLabel(10));
    }

    @Test
    void testSupportedClassVersion() {
        var cf = ClassFile.of();
        assertThrows(IllegalArgumentException.class, () -> cf.parse(cf.build(ClassDesc.of("ClassFromFuture"), cb -> cb.withVersion(ClassFile.latestMajorVersion() + 1, 0))));
    }

    @Test
    void testReadingOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE}), "reading magic only");
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE, 0, 0, 0, 0, 0, 2}), "reading invalid CP size");
    }

    @Test
    void testInvalidClassEntry() {
        assertThrows(ConstantPoolException.class, () -> ClassFile.of().parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
            0, 0, 0, 0, 0, 2, ClassFile.TAG_METHODREF, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}).thisClass());
    }

    @Test
    void testInvalidUtf8Entry() {
        var cp = ClassFile.of().parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
            0, 0, 0, 0, 0, 3, ClassFile.TAG_INTEGER, 0, 0, 0, 0, ClassFile.TAG_NAMEANDTYPE, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}).constantPool();
        assertTrue(cp.entryByIndex(1) instanceof IntegerEntry); //parse valid int entry first
        assertThrows(ConstantPoolException.class, () -> cp.entryByIndex(2));
    }

    @Test
    void testInvalidLookupSwitch() {
        assertThrows(IllegalArgumentException.class, () ->
                ClassFile.of().parse(ClassFile.of().build(ClassDesc.of("LookupSwitchClass"), cb -> cb.withMethod(
                "lookupSwitchMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, mb ->
                        ((DirectMethodBuilder)mb).writeAttribute(new UnboundAttribute.AdHocAttribute<CodeAttribute>(Attributes.code()) {
                                @Override
                                public void writeBody(BufWriterImpl b) {
                                    b.writeU2(-1);//max stack
                                    b.writeU2(-1);//max locals
                                    b.writeInt(16);
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.LOOKUPSWITCH.bytecode());
                                    b.writeU1(0); //padding
                                    b.writeU2(0); //padding
                                    b.writeInt(0); //default
                                    b.writeInt(-2); //npairs to jump back and cause OOME if not checked
                                    b.writeU2(0);//exception handlers
                                    b.writeU2(0);//attributes
                                }})))).methods().get(0).code().get().elementList());
    }

    @Test
    void testInvalidTableSwitch() {
        assertThrows(IllegalArgumentException.class, () ->
                ClassFile.of().parse(ClassFile.of().build(ClassDesc.of("TableSwitchClass"), cb -> cb.withMethod(
                "tableSwitchMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, mb ->
                        ((DirectMethodBuilder)mb).writeAttribute(new UnboundAttribute.AdHocAttribute<CodeAttribute>(Attributes.code()) {
                                @Override
                                public void writeBody(BufWriterImpl b) {
                                    b.writeU2(-1);//max stack
                                    b.writeU2(-1);//max locals
                                    b.writeInt(16);
                                    b.writeU1(Opcode.TABLESWITCH.bytecode());
                                    b.writeU1(0); //padding
                                    b.writeU2(0); //padding
                                    b.writeInt(0); //default
                                    b.writeInt(0); //low
                                    b.writeInt(-5); //high to jump back and cause OOME if not checked
                                    b.writeU2(0);//exception handlers
                                    b.writeU2(0);//attributes
                                }})))).methods().get(0).code().get().elementList());
        assertThrows(IllegalArgumentException.class, () ->
                ClassFile.of().parse(ClassFile.of().build(ClassDesc.of("TableSwitchClass"), cb -> cb.withMethod(
                "tableSwitchMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, mb ->
                        ((DirectMethodBuilder)mb).writeAttribute(new UnboundAttribute.AdHocAttribute<CodeAttribute>(Attributes.code()) {
                                @Override
                                public void writeBody(BufWriterImpl b) {
                                    b.writeU2(-1);//max stack
                                    b.writeU2(-1);//max locals
                                    b.writeInt(20);
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.NOP.bytecode());
                                    b.writeU1(Opcode.TABLESWITCH.bytecode());
                                    b.writeU1(0); //padding
                                    b.writeU2(0); //padding
                                    b.writeInt(0); //default
                                    b.writeInt(Integer.MIN_VALUE); //low
                                    b.writeInt(Integer.MAX_VALUE - 4); //high to jump back and cause infinite loop
                                    b.writeU2(0);//exception handlers
                                    b.writeU2(0);//attributes
                                }})))).methods().get(0).code().get().elementList());
    }

    @Test
    void testLineNumberOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
                ClassFile.of().parse(ClassFile.of().build(ClassDesc.of("LineNumberClass"), cb -> cb.withMethodBody(
                "lineNumberMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> ((DirectCodeBuilder)cob
                        .return_())
                        .writeAttribute(LineNumberTableAttribute.of(List.of(LineNumberInfo.of(500, 0))))
                ))).methods().get(0).code().get().elementList());
    }

    @Test
    void testLocalVariableOutOfBounds() {
        assertThrows(IllegalArgumentException.class, () ->
                ClassFile.of().parse(ClassFile.of().build(ClassDesc.of("LocalVariableClass"), cb -> cb.withMethodBody(
                "localVariableMethod", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> ((DirectCodeBuilder)cob
                        .return_())
                        .writeAttribute(LocalVariableTableAttribute.of(List.of(
                                new UnboundAttribute.UnboundLocalVariableInfo(0, 200,
                                        cob.constantPool().utf8Entry("a"), cob.constantPool().utf8Entry("A"), 0))))
                ))).methods().get(0).code().get().elementList());
    }

    @Test
    void testZeroHashCPEntry() {
        var cpb = ConstantPoolBuilder.of();
        cpb.intEntry(-cpb.intEntry(0).hashCode());
    }
}
