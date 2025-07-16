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
 * @bug 8341277 8361102
 * @summary Testing ClassFile instruction argument validation.
 * @run junit InstructionValidationTest
 */

import java.lang.classfile.*;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.instruction.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;
import java.util.stream.Stream;

import helpers.TestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static java.lang.constant.ConstantDescs.*;
import static helpers.TestConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static java.lang.classfile.Opcode.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstructionValidationTest {

    @Test
    void testOpcodeInCodeBuilder() {
        TestUtil.runCodeHandler(cob -> {
            var mref = cob.constantPool().methodRefEntry(CD_System, "exit", MTD_INT_VOID);
            var fref = cob.constantPool().fieldRefEntry(CD_System, "out", CD_PrintStream);
            var label = cob.newLabel();

            // Sanity
            cob.iconst_0();
            assertDoesNotThrow(() -> cob.invoke(INVOKESTATIC, mref));
            assertDoesNotThrow(() -> cob.fieldAccess(GETSTATIC, fref));
            cob.pop();
            assertDoesNotThrow(() -> cob.branch(GOTO, label));

            // Opcode NPE
            assertThrows(NullPointerException.class, () -> cob.invoke(null, mref));
            assertThrows(NullPointerException.class, () -> cob.fieldAccess(null, fref));
            assertThrows(NullPointerException.class, () -> cob.branch(null, label));

            // Opcode IAE
            assertThrows(IllegalArgumentException.class, () -> cob.invoke(IFNE, mref));
            assertThrows(IllegalArgumentException.class, () -> cob.fieldAccess(JSR, fref));
            assertThrows(IllegalArgumentException.class, () -> cob.branch(CHECKCAST, label));

            // Wrap up
            cob.labelBinding(label);
            cob.return_();
        });
    }

    @Test
    void testLongJump() {
        TestUtil.runCodeHandler(cob -> {
            assertThrows(NullPointerException.class, () -> cob.goto_w(null));
            // Ensures nothing redundant is written in case of failure
            cob.return_();
        });
    }

    @Test
    void testSwitch() {
        TestUtil.runCodeHandler(cob -> {
            assertThrows(NullPointerException.class, () -> cob.tableswitch(-1, 1, cob.startLabel(), null));
            assertThrows(NullPointerException.class, () -> cob.lookupswitch(cob.startLabel(), null));
            assertThrows(NullPointerException.class, () -> cob.tableswitch(-1, 1, cob.startLabel(), Collections.singletonList(null)));
            assertThrows(NullPointerException.class, () -> cob.lookupswitch(cob.startLabel(), Collections.singletonList(null)));
            assertThrows(NullPointerException.class, () -> cob.tableswitch(-1, 1, null, List.of()));
            assertThrows(NullPointerException.class, () -> cob.lookupswitch(null, List.of()));
            // Ensures nothing redundant is written in case of failure
            cob.return_();
        });
    }

    @Test
    void testArgumentConstant() {
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, 0));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, Short.MIN_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(SIPUSH, Short.MAX_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, 0));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, Byte.MIN_VALUE));
        assertDoesNotThrow(() -> ConstantInstruction.ofArgument(BIPUSH, Byte.MAX_VALUE));

        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(SIPUSH, (int) Short.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(SIPUSH, (int) Short.MAX_VALUE + 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(BIPUSH, (int) Byte.MIN_VALUE - 1));
        assertThrows(IllegalArgumentException.class, () -> ConstantInstruction.ofArgument(BIPUSH, (int) Byte.MAX_VALUE + 1));

        TestUtil.runCodeHandler(cob -> {
            assertThrows(IllegalArgumentException.class, () -> cob.sipush((int) Short.MIN_VALUE - 1));
            assertThrows(IllegalArgumentException.class, () -> cob.sipush((int) Short.MAX_VALUE + 1));
            assertThrows(IllegalArgumentException.class, () -> cob.bipush((int) Byte.MIN_VALUE - 1));
            assertThrows(IllegalArgumentException.class, () -> cob.bipush((int) Byte.MAX_VALUE + 1));
            cob.return_();
        });
    }

    /**
     * Tests the bad slot argument IAE for load, store, increment, and ret.
     */
    @Test
    void testSlots() {
        record Result(boolean shouldFail, int slot) {
        }

        List<Integer> badSlots = List.of(-1, 72694, -42, 0x10000, Integer.MIN_VALUE, Integer.MAX_VALUE);
        List<Integer> u2OnlySlots = List.of(0x100, 1000, 0xFFFF);
        List<Integer> u1Slots = List.of(0, 2, 15, 0xFF);

        List<Integer> badU1Slots = Stream.concat(badSlots.stream(), u2OnlySlots.stream()).toList();
        List<Integer> u2Slots = Stream.concat(u1Slots.stream(), u2OnlySlots.stream()).toList();
        List<Result> u2Cases = Stream.concat(
                badSlots.stream().map(i -> new Result(true, i)),
                u2Slots.stream().map(i -> new Result(false, i))
        ).toList();
        List<Result> u1Cases = Stream.concat(
                badU1Slots.stream().map(i -> new Result(true, i)),
                u1Slots.stream().map(i -> new Result(false, i))
        ).toList();
        List<Integer> nonIntrinsicValues = Stream.of(badSlots, u2Slots, u1Slots).<Integer>mapMulti(List::forEach)
                .filter(i -> i < 0 || i > 3).toList();

        Label[] capture = new Label[1];
        ClassFile.of().build(CD_Object, clb -> clb.withMethodBody("test", MTD_void, 0, cob -> {
            capture[0] = cob.startLabel();
            cob.return_();
        }));
        Label dummyLabel = capture[0];

        List<ObjIntConsumer<CodeBuilder>> cbFactories = List.of(
                CodeBuilder::aload,
                CodeBuilder::iload,
                CodeBuilder::lload,
                CodeBuilder::dload,
                CodeBuilder::fload,
                CodeBuilder::astore,
                CodeBuilder::istore,
                CodeBuilder::lstore,
                CodeBuilder::dstore,
                CodeBuilder::fstore
        );

        for (var r : u2Cases) {
            var fails = r.shouldFail;
            var i = r.slot;
            for (var fac : cbFactories) {
                if (fails) {
                    ensureFailFast(i, cob -> fac.accept(cob, i));
                }
            }
            for (TypeKind tk : TypeKind.values()) {
                if (tk == TypeKind.VOID)
                    continue;
                if (fails) {
                    ensureFailFast(i, cob -> cob.loadLocal(tk, i));
                    ensureFailFast(i, cob -> cob.storeLocal(tk, i));
                }
                check(fails, () -> LoadInstruction.of(tk, i));
                check(fails, () -> StoreInstruction.of(tk, i));
            }
            if (fails) {
                ensureFailFast(i, cob -> cob.iinc(i, 1));
            }
            check(fails, () -> IncrementInstruction.of(i, 1));
            check(fails, () -> DiscontinuedInstruction.RetInstruction.of(i));
            check(fails, () -> DiscontinuedInstruction.RetInstruction.of(RET_W, i));
            check(fails, () -> LocalVariable.of(i, "test", CD_Object, dummyLabel, dummyLabel));
            check(fails, () -> LocalVariableType.of(i, "test", Signature.of(CD_Object), dummyLabel, dummyLabel));
        }

        for (var r : u1Cases) {
            var fails = r.shouldFail;
            var i = r.slot;
            for (var u1Op : List.of(ALOAD, ILOAD, LLOAD, FLOAD, DLOAD))
                check(fails, () -> LoadInstruction.of(u1Op, i));
            for (var u1Op : List.of(ASTORE, ISTORE, LSTORE, FSTORE, DSTORE))
                check(fails, () -> StoreInstruction.of(u1Op, i));
            check(fails, () -> DiscontinuedInstruction.RetInstruction.of(RET, i));
        }

        for (var i : nonIntrinsicValues) {
            for (var intrinsicOp : List.of(ALOAD_0, ILOAD_0, LLOAD_0, FLOAD_0, DLOAD_0, ALOAD_1, ILOAD_1, LLOAD_1, FLOAD_1, DLOAD_1,
                    ALOAD_2, ILOAD_2, LLOAD_2, FLOAD_2, DLOAD_2, ALOAD_3, ILOAD_3, LLOAD_3, FLOAD_3, DLOAD_3)) {
                assertThrows(IllegalArgumentException.class, () -> LoadInstruction.of(intrinsicOp, i));
            }
            for (var intrinsicOp : List.of(ASTORE_0, ISTORE_0, LSTORE_0, FSTORE_0, DSTORE_0, ASTORE_1, ISTORE_1, LSTORE_1, FSTORE_1, DSTORE_1,
                    ASTORE_2, ISTORE_2, LSTORE_2, FSTORE_2, DSTORE_2, ASTORE_3, ISTORE_3, LSTORE_3, FSTORE_3, DSTORE_3)) {
                assertThrows(IllegalArgumentException.class, () -> StoreInstruction.of(intrinsicOp, i));
            }
        }
    }

    // CodeBuilder can fail with IAE due to other reasons, so we cannot check
    // "success" but can ensure things fail fast
    static void ensureFailFast(int value, Consumer<CodeBuilder> action) {
        Consumer<CodeBuilder> checkedAction = cob -> {
            assertThrows(IllegalArgumentException.class, () -> action.accept(cob));
            cob.return_();
        };
        try {
            TestUtil.runCodeHandler(checkedAction);
        } catch (Throwable _) {
            System.out.printf("Erroneous value %d%n", value);
        }
    }

    static void check(boolean fails, Executable exec) {
        if (fails) {
            assertThrows(IllegalArgumentException.class, exec);
        } else {
            assertDoesNotThrow(exec);
        }
    }

    @Test
    void testIincConstant() {
        IncrementInstruction.of(0, 2);
        IncrementInstruction.of(0, Short.MAX_VALUE);
        IncrementInstruction.of(0, Short.MIN_VALUE);
        for (int i : new int[] {Short.MIN_VALUE - 1, Short.MAX_VALUE + 1}) {
            assertThrows(IllegalArgumentException.class, () -> IncrementInstruction.of(0, i));
            TestUtil.runCodeHandler(cob -> {
                assertThrows(IllegalArgumentException.class, () -> cob.iinc(0, i));
                cob.return_();
            });
        }
    }

    @Test
    void testNewMultiArrayDimension() {
        ClassEntry ce = ConstantPoolBuilder.of().classEntry(CD_Class);
        NewMultiArrayInstruction.of(ce, 1);
        NewMultiArrayInstruction.of(ce, 13);
        NewMultiArrayInstruction.of(ce, 0xFF);
        assertThrows(IllegalArgumentException.class, () -> NewMultiArrayInstruction.of(ce, 0));
        assertThrows(IllegalArgumentException.class, () -> NewMultiArrayInstruction.of(ce, 0x100));
        assertThrows(IllegalArgumentException.class, () -> NewMultiArrayInstruction.of(ce, -1));
        assertThrows(IllegalArgumentException.class, () -> NewMultiArrayInstruction.of(ce, Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> NewMultiArrayInstruction.of(ce, Integer.MAX_VALUE));

        TestUtil.runCodeHandler(cob -> {
            assertThrows(IllegalArgumentException.class, () -> cob.multianewarray(ce, 0));
            assertThrows(IllegalArgumentException.class, () -> cob.multianewarray(ce, 0x100));
            assertThrows(IllegalArgumentException.class, () -> cob.multianewarray(ce, -1));
            assertThrows(IllegalArgumentException.class, () -> cob.multianewarray(ce, Integer.MIN_VALUE));
            assertThrows(IllegalArgumentException.class, () -> cob.multianewarray(ce, Integer.MAX_VALUE));
            cob.return_();
        });
    }
}
