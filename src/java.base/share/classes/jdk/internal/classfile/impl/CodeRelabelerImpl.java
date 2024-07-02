/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.classfile.impl;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.components.CodeRelabeler;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.CharacterRange;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;

import java.util.function.BiFunction;

public record CodeRelabelerImpl(BiFunction<Label, CodeBuilder, Label> mapFunction) implements CodeRelabeler {

    @Override
    public Label relabel(Label label, CodeBuilder cob) {
        return mapFunction.apply(label, cob);
    }

    @Override
    public void accept(CodeBuilder cob, CodeElement coe) {
        switch (coe) {
            case BranchInstruction bi ->
                cob.branch(
                        bi.opcode(),
                        relabel(bi.target(), cob));
            case LookupSwitchInstruction lsi ->
                cob.lookupswitch(
                        relabel(lsi.defaultTarget(), cob),
                        lsi.cases().stream().map(c ->
                                SwitchCase.of(
                                        c.caseValue(),
                                        relabel(c.target(), cob))).toList());
            case TableSwitchInstruction tsi ->
                cob.tableswitch(
                        tsi.lowValue(),
                        tsi.highValue(),
                        relabel(tsi.defaultTarget(), cob),
                        tsi.cases().stream().map(c ->
                                SwitchCase.of(
                                        c.caseValue(),
                                        relabel(c.target(), cob))).toList());
            case LabelTarget lt ->
                cob.labelBinding(
                        relabel(lt.label(), cob));
            case ExceptionCatch ec ->
                cob.exceptionCatch(
                        relabel(ec.tryStart(), cob),
                        relabel(ec.tryEnd(), cob),
                        relabel(ec.handler(), cob),
                        ec.catchType());
            case LocalVariable lv ->
                cob.localVariable(
                        lv.slot(),
                        lv.name().stringValue(),
                        lv.typeSymbol(),
                        relabel(lv.startScope(), cob),
                        relabel(lv.endScope(), cob));
            case LocalVariableType lvt ->
                cob.localVariableType(
                        lvt.slot(),
                        lvt.name().stringValue(),
                        lvt.signatureSymbol(),
                        relabel(lvt.startScope(), cob),
                        relabel(lvt.endScope(), cob));
            case CharacterRange chr ->
                cob.characterRange(
                        relabel(chr.startScope(), cob),
                        relabel(chr.endScope(), cob),
                        chr.characterRangeStart(),
                        chr.characterRangeEnd(),
                        chr.flags());
            default ->
                cob.with(coe);
        }
    }

}
