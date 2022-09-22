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
package jdk.classfile.components;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiFunction;
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
 * CodeRelabeler is a {@link jdk.classfile.CodeTransform} replacing all occurences
 * of {@link jdk.classfile.Label} in the transformed code with new instances.
 * All {@link jdk.classfile.instruction.LabelTarget} instructions are adjusted accordingly.
 * Relabeled code graph is identical to the original.
 * <p>
 * Primary purpose of CodeRelabeler is for repeated injections of the same code blocks.
 * Repeated injection of the same code block must be relabeled, so each instance of
 * {@link jdk.classfile.Label} is bound in the target bytecode exactly once.
 */
public sealed interface CodeRelabeler extends CodeTransform {

    /**
     * Creates new instance of CodeRelabeler
     * @return new instance of CodeRelabeler
     */
    static CodeRelabeler of() {
        return of(new IdentityHashMap<>());
    }

    /**
     * Creates new instance of CodeRelabeler storing the label mapping into the provided map
     * @param map label map actively used for relabeling
     * @return new instance of CodeRelabeler
     */
    static CodeRelabeler of(Map<Label, Label> map) {
        return of((l, cob) -> map.computeIfAbsent(l, ll -> cob.newLabel()));
    }

    /**
     * Creates new instance of CodeRelabeler using provided {@link java.util.function.BiFunction}
     * to re-label the code.
     * @param mapFunction
     * @return
     */
    static CodeRelabeler of(BiFunction<Label, CodeBuilder, Label> mapFunction) {
        return new CodeRelabelerImpl(mapFunction);
    }

    /**
     * Access method to internal re-labeling function.
     * @param label source label
     * @param codeBuilder builder to create new labels
     * @return target label
     */
    Label relabel(Label label, CodeBuilder codeBuilder);

    record CodeRelabelerImpl(BiFunction<Label, CodeBuilder, Label> mapFunction) implements  CodeRelabeler {

        @Override
        public Label relabel(Label label, CodeBuilder cob) {
            return mapFunction.apply(label, cob);
        }

        @Override
        public void accept(CodeBuilder cob, CodeElement coe) {
            switch (coe) {
                case BranchInstruction bi ->
                    cob.branchInstruction(
                            bi.opcode(),
                            relabel(bi.target(), cob));
                case LookupSwitchInstruction lsi ->
                    cob.lookupSwitchInstruction(
                            relabel(lsi.defaultTarget(), cob),
                            lsi.cases().stream().map(c ->
                                    SwitchCase.of(
                                            c.caseValue(),
                                            relabel(c.target(), cob))).toList());
                case TableSwitchInstruction tsi ->
                    cob.tableSwitchInstruction(
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
                default ->
                    cob.with(coe);
            }
        }
    }
}
