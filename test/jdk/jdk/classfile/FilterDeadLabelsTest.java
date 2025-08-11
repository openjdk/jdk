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
 * @bug 8361908
 * @summary Testing filtering of dead labels.
 * @run junit FilterDeadLabelsTest
 */

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.lang.classfile.Attributes;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Signature;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.lang.constant.ConstantDescs.*;

class FilterDeadLabelsTest {

    static List<Consumer<CodeBuilder>> deadLabelFragments() {
        return List.of(
                cob -> cob.exceptionCatchAll(cob.newLabel(), cob.startLabel(), cob.endLabel()),
                cob -> cob.exceptionCatchAll(cob.startLabel(), cob.newLabel(), cob.endLabel()),
                cob -> cob.exceptionCatchAll(cob.startLabel(), cob.endLabel(), cob.newLabel()),
                cob -> cob.localVariable(0, "v", ConstantDescs.CD_int, cob.startLabel(), cob.newLabel()),
                cob -> cob.localVariable(0, "v", ConstantDescs.CD_int, cob.newLabel(), cob.endLabel()),
                cob -> cob.localVariableType(0, "v", Signature.of(ConstantDescs.CD_int), cob.startLabel(), cob.newLabel()),
                cob -> cob.localVariableType(0, "v", Signature.of(ConstantDescs.CD_int), cob.newLabel(), cob.endLabel()),
                cob -> cob.characterRange(cob.startLabel(), cob.newLabel(), 0, 0, 0),
                cob -> cob.characterRange(cob.newLabel(), cob.endLabel(), 0, 0, 0));
    }

    @Test
    void testFilterDeadLabels() {
        var cc = ClassFile.of(ClassFile.DeadLabelsOption.DROP_DEAD_LABELS);
        var code = cc.parse(cc.build(ClassDesc.of("cls"), clb ->
                clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> {
                    cob.return_();
                    deadLabelFragments().forEach(f -> f.accept(cob));
                }))).methods().get(0).code().get();

        assertTrue(code.exceptionHandlers().isEmpty());
        code.findAttribute(Attributes.localVariableTable()).ifPresent(a -> assertTrue(a.localVariables().isEmpty()));
        code.findAttribute(Attributes.localVariableTypeTable()).ifPresent(a -> assertTrue(a.localVariableTypes().isEmpty()));
        code.findAttribute(Attributes.characterRangeTable()).ifPresent(a -> assertTrue(a.characterRangeTable().isEmpty()));
    }

    @Test // JDK-8361908
    void testFilterMixedExceptionCatch() {
        var cc = ClassFile.of(ClassFile.DeadLabelsOption.DROP_DEAD_LABELS);
        var code = cc.parse(cc.build(CD_Void, clb ->
                clb.withMethodBody("m", MTD_void, 0, cob -> {
                    cob.return_();
                    var l = cob.newBoundLabel();
                    cob.pop().return_();
                    cob.exceptionCatch(cob.startLabel(), l, l, Optional.empty());
                    cob.exceptionCatch(cob.newLabel(), l, l, CD_Exception);
                }))).methods().get(0).code().get();
        assertEquals(1, code.exceptionHandlers().size(), () -> code.exceptionHandlers().toString());
        assertEquals(Optional.empty(), code.exceptionHandlers().getFirst().catchType());
    }

    @ParameterizedTest
    @MethodSource("deadLabelFragments")
    void testThrowOnDeadLabels(Consumer<CodeBuilder> fragment) {
        assertThrows(IllegalArgumentException.class, () -> ClassFile.of().build(ClassDesc.of("cls"), clb ->
                clb.withMethodBody("m", MethodTypeDesc.of(ConstantDescs.CD_void), 0, cob -> {
                    cob.return_();
                    fragment.accept(cob);
                })));
    }

}
