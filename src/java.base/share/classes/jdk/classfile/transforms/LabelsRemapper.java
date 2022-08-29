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
 *
 */
package jdk.classfile.transforms;

import java.util.IdentityHashMap;
import java.util.Map;
import jdk.classfile.CodeBuilder;
import jdk.classfile.CodeElement;
import jdk.classfile.CodeTransform;
import jdk.classfile.instruction.BranchInstruction;
import jdk.classfile.instruction.LookupSwitchInstruction;
import jdk.classfile.instruction.SwitchCase;
import jdk.classfile.instruction.TableSwitchInstruction;
import jdk.classfile.Label;
import jdk.classfile.instruction.ExceptionCatch;
import jdk.classfile.instruction.LabelTarget;
import jdk.classfile.instruction.LocalVariable;
import jdk.classfile.instruction.LocalVariableType;

/**
 *
 */
public final class LabelsRemapper {

    private LabelsRemapper() {
    }

    public static CodeTransform remapLabels() {
        var map = new IdentityHashMap<Label, Label>();
        return (CodeBuilder cob, CodeElement coe) -> {
            switch (coe) {
                case BranchInstruction bi ->
                    cob.branchInstruction(
                            bi.opcode(),
                            remap(map, bi.target(), cob));
                case LookupSwitchInstruction lsi ->
                    cob.lookupSwitchInstruction(
                            remap(map, lsi.defaultTarget(), cob),
                            lsi.cases().stream().map(c ->
                                    SwitchCase.of(
                                            c.caseValue(),
                                            remap(map, c.target(), cob))).toList());
                case TableSwitchInstruction tsi ->
                    cob.tableSwitchInstruction(
                            tsi.lowValue(),
                            tsi.highValue(),
                            remap(map, tsi.defaultTarget(), cob),
                            tsi.cases().stream().map(c ->
                                    SwitchCase.of(
                                            c.caseValue(),
                                            remap(map, c.target(), cob))).toList());
                case LabelTarget lt ->
                    cob.labelBinding(
                            remap(map, lt.label(), cob));
                case ExceptionCatch ec ->
                    cob.exceptionCatch(
                            remap(map, ec.tryStart(), cob),
                            remap(map, ec.tryEnd(), cob),
                            remap(map, ec.handler(), cob),
                            ec.catchType());
                case LocalVariable lv ->
                    cob.localVariable(
                            lv.slot(),
                            lv.name().stringValue(),
                            lv.typeSymbol(),
                            remap(map, lv.startScope(), cob),
                            remap(map, lv.endScope(), cob));
                case LocalVariableType lvt ->
                    cob.localVariableType(
                            lvt.slot(),
                            lvt.name().stringValue(),
                            lvt.signatureSymbol(),
                            remap(map, lvt.startScope(), cob),
                            remap(map, lvt.endScope(), cob));
                default ->
                    cob.with(coe);
            }
        };
    }

    private static Label remap(Map<Label, Label> map, Label l, CodeBuilder cob) {
        return map.computeIfAbsent(l, ll -> cob.newLabel());
    }
}
