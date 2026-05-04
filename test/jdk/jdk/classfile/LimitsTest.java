/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320360 8330684 8331320 8331655 8331940 8332486 8335820 8336833 8361635
 *      8367585
 * @summary Testing ClassFile limits.
 * @run junit LimitsTest
 */

import java.lang.classfile.AttributeMapper;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.BufWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassReader;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CustomAttribute;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.Signature;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.attribute.LineNumberInfo;
import java.lang.classfile.attribute.LineNumberTableAttribute;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.ConstantPoolException;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import jdk.internal.classfile.impl.BufWriterImpl;
import jdk.internal.classfile.impl.DirectCodeBuilder;
import jdk.internal.classfile.impl.DirectMethodBuilder;
import jdk.internal.classfile.impl.LabelContext;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.*;
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
    void testBsmOverLimit() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> {
            var cp = clb.constantPool();
            var mhe = cp.methodHandleEntry(BSM_GET_STATIC_FINAL);
            var digits = new IntegerEntry[10];
            for (int i = 0; i < 10; i++) {
                digits[i] = cp.intEntry(i);
            }
            int lastIndex = -1;
            for (int i = 0; i < 66000; i++) {
                lastIndex = cp.bsmEntry(mhe, List.of(
                        digits[i / 10000 % 10],
                        digits[i / 1000 % 10],
                        digits[i / 100 % 10],
                        digits[i / 10 % 10],
                        digits[i / 1 % 10])).bsmIndex();
            }
            assertEquals(65999, lastIndex);
            reached.set(true);
        }));
        assertTrue(reached.get());
    }

    @Test
    void testTooManyFields() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> {
            for (int i = 1; i < 66000; i++) {
                clb.withField("f", CD_int, 0);
            }
            reached.set(true);
        }));
        assertTrue(reached.get());
    }

    @Test
    void testTooManyMethods() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> {
            for (int i = 1; i < 66000; i++) {
                clb.withMethodBody("m", MTD_void, 0, CodeBuilder::return_);
            }
            reached.set(true);
        }));
        assertTrue(reached.get());
    }

    static final class MyAttribute extends CustomAttribute<MyAttribute> {
        static final MyAttribute INSTANCE = new MyAttribute();

        private enum Mapper implements AttributeMapper<MyAttribute> {
            INSTANCE;

            @Override
            public MyAttribute readAttribute(AttributedElement enclosing, ClassReader cf, int pos) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void writeAttribute(BufWriter buf, MyAttribute attr) {
                buf.writeIndex(buf.constantPool().utf8Entry("MyAttribute"));
                buf.writeInt(0);
            }

            @Override
            public boolean allowMultiple() {
                return true;
            }

            @Override
            public AttributeStability stability() {
                return AttributeStability.STATELESS;
            }


        }

        private MyAttribute() {
            super(Mapper.INSTANCE);
        }
    }

    @Test
    void testTooManyClassAttributes() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> {
            for (int i = 1; i < 66000; i++) {
                clb.with(MyAttribute.INSTANCE);
            }
            reached.set(true);
        }));
        assertTrue(reached.get());
    }

    @Test
    void testTooManyFieldAttributes() {
        AtomicBoolean reached = new AtomicBoolean();
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> clb.withField("f", CD_int, fb -> {
            for (int i = 1; i < 66000; i++) {
                fb.with(MyAttribute.INSTANCE);
            }
            reached.set(true);
        })));
        assertTrue(reached.get());
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

    private static void testPseudoOverflow(BiConsumer<CodeBuilder, Label> handler) {
        ClassFile cf = ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS);
        AtomicBoolean reached = new AtomicBoolean(false);
        assertDoesNotThrow(() -> cf.build(CD_Void, cb -> cb.withMethodBody("test", MTD_void, ACC_STATIC, cob -> {
            cob.nop();
            var label = cob.newLabel();
            for (int i = 0; i < 65535; i++) {
                handler.accept(cob, label);
            }
            cob.labelBinding(label);
            cob.return_();
            reached.set(true);
        })));
        assertTrue(reached.get());

        reached.set(false);
        assertThrows(IllegalArgumentException.class, () -> cf.build(CD_Void, cb -> cb.withMethodBody("test", MTD_void, ACC_STATIC, cob -> {
            cob.nop();
            var label = cob.newLabel();
            for (int i = 0; i < 65536; i++) {
                handler.accept(cob, label);
            }
            cob.labelBinding(label);
            cob.return_();
            reached.set(true);
        })));
        assertTrue(reached.get());
    }

    @Test
    void testExceptionCatchOverflow() {
        testPseudoOverflow((cob, label) -> cob.exceptionCatch(cob.startLabel(), label, label, CD_Throwable));
    }

    @Test
    void testLocalVariableOverflow() {
        testPseudoOverflow((cob, label) -> cob.localVariable(0, "fake", CD_int, cob.startLabel(), label));
    }

    @Test
    void testLocalVariableTypeOverflow() {
        testPseudoOverflow((cob, label) -> cob.localVariableType(0, "fake", Signature.of(CD_int), cob.startLabel(), label));
    }

    @Test
    void testCharacterRangeOverflow() {
        testPseudoOverflow((cob, label) -> cob.characterRange(cob.startLabel(), label, 0, 0, 0));
    }

    // LineNumber deduplicates so cannot really overflow

    @Test
    void testHugeLookupswitch() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> clb.withMethodBody("test", MTD_void, ACC_STATIC, cob -> {
            var l = cob.newLabel();
            // 10000 * 8 > 65535
            var cases = new ArrayList<SwitchCase>(10000);
            for (int i = 0; i < 10000; i++) {
                cases.add(SwitchCase.of(i, l));
            }
            cob.lookupswitch(l, cases);
            cob.labelBinding(l);
            cob.return_();
        })));
    }

    @Test
    void testHugeTableswitch() {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> clb.withMethodBody("test", MTD_void, ACC_STATIC, cob -> {
            var l = cob.newLabel();
            // 20000 * 4 > 65535
            cob.tableswitch(-10000, 10000, l, List.of());
            cob.labelBinding(l);
            cob.return_();
        })));
    }

    @Test
    void testHugeUtf8Entry() {
        var longString = String.valueOf((char) 0x800).repeat(22000);
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(CD_Void, clb -> {
            clb.constantPool().utf8Entry(longString);
        }));
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
            0, 0, 0, 0, 0, 2, PoolEntry.TAG_METHODREF, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}).thisClass());
    }

    @Test
    void testInvalidUtf8Entry() {
        var cp = ClassFile.of().parse(new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE,
            0, 0, 0, 0, 0, 3, PoolEntry.TAG_INTEGER, 0, 0, 0, 0, PoolEntry.TAG_NAME_AND_TYPE, 0, 1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}).constantPool();
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
                                }

                                @Override
                                public Utf8Entry attributeName() {
                                    return mb.constantPool().utf8Entry(Attributes.NAME_CODE);
                                }
                        })))).methods().get(0).code().get().elementList());
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
                                }

                                @Override
                                public Utf8Entry attributeName() {
                                    return mb.constantPool().utf8Entry(Attributes.NAME_CODE);
                                }
                        })))).methods().get(0).code().get().elementList());
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
                                }

                                @Override
                                public Utf8Entry attributeName() {
                                    return mb.constantPool().utf8Entry(Attributes.NAME_CODE);
                                }
                        })))).methods().get(0).code().get().elementList());
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

    static List<String> legalStrings() {
        var empty = "";
        var allAscii = "e".repeat(0xFFFF);
        // 3-byte utf8 characters
        var largeChars = String.valueOf((char) 0x800).repeat(0xFFFF / 3);
        return List.of(empty, allAscii, largeChars);
    }

    @ParameterizedTest
    @MethodSource("legalStrings")
    void testStringLengthInLimit(String st) {
        TemporaryConstantPool.INSTANCE.utf8Entry(st);
        ConstantPoolBuilder.of().utf8Entry(st);
    }

    static List<String> oversizedStrings() {
        var allAscii = "e".repeat(0x10000);
        // 3-byte utf8 characters
        var largeChars = String.valueOf((char) 0x800).repeat(0xFFFF / 3 + 1);
        return List.of(allAscii, largeChars);
    }

    @ParameterizedTest
    @MethodSource("oversizedStrings")
    void testStringLengthOverLimit(String st) {
        assertThrows(IllegalArgumentException.class, () -> TemporaryConstantPool.INSTANCE.utf8Entry(st));
        assertThrows(IllegalArgumentException.class, () -> ConstantPoolBuilder.of().utf8Entry(st));
    }

    static Stream<ConstantPoolBuilder> pools() {
        return Stream.of(ConstantPoolBuilder.of(), TemporaryConstantPool.INSTANCE);
    }

    @ParameterizedTest
    @MethodSource("pools")
    void testSingleReferenceNominalDescriptorOverLimit(ConstantPoolBuilder cpb) {
        var fittingName = "A" + "a".repeat(65532); // fits "enveloped" L ;
        var borderName = "B" + "b".repeat(65534); // fits only "not enveloped"
        var overflowName = "C" + "b".repeat(65535); // nothing fits

        var fittingClassDesc = ClassDesc.of(fittingName);
        var borderClassDesc = ClassDesc.of(borderName);
        var overflowClassDesc = ClassDesc.of(overflowName);
        cpb.classEntry(fittingClassDesc);
        cpb.utf8Entry(fittingClassDesc);
        cpb.classEntry(borderClassDesc);
        assertThrows(IllegalArgumentException.class, () -> cpb.utf8Entry(borderClassDesc));
        assertThrows(IllegalArgumentException.class, () -> cpb.classEntry(overflowClassDesc));
        assertThrows(IllegalArgumentException.class, () -> cpb.utf8Entry(overflowClassDesc));

        cpb.packageEntry(PackageDesc.of(borderName));
        assertThrows(IllegalArgumentException.class, () -> cpb.packageEntry(PackageDesc.of(overflowName)));
        cpb.moduleEntry(ModuleDesc.of(borderName));
        assertThrows(IllegalArgumentException.class, () -> cpb.moduleEntry(ModuleDesc.of(overflowName)));
    }

    @ParameterizedTest
    @MethodSource("pools")
    void testMethodTypeDescOverLimit(ConstantPoolBuilder cpb) {
        var borderReturnMtd = MethodTypeDesc.of(ClassDesc.of("R" + "r".repeat(65530)));
        var overflowReturnMtd = MethodTypeDesc.of(ClassDesc.of("R" + "r".repeat(65531)));
        var borderParamMtd = MethodTypeDesc.of(CD_void, ClassDesc.of("P" + "p".repeat(65529)));
        var overflowParamMtd = MethodTypeDesc.of(CD_void, ClassDesc.of("P" + "p".repeat(65530)));
        cpb.utf8Entry(borderParamMtd);
        cpb.utf8Entry(borderReturnMtd);
        assertThrows(IllegalArgumentException.class, () -> cpb.utf8Entry(overflowReturnMtd));
        assertThrows(IllegalArgumentException.class, () -> cpb.utf8Entry(overflowParamMtd));
    }
}
