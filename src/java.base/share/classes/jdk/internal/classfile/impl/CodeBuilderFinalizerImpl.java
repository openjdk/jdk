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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.Instruction;
import jdk.internal.classfile.Label;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.instruction.BranchInstruction;
import jdk.internal.classfile.instruction.ReturnInstruction;

public final class CodeBuilderFinalizerImpl {

    public static void buildWithFinalizer(CodeBuilder parent,
                                          Consumer<CodeBuilder> tryHandler,
                                          Consumer<CodeBuilder> finalizerHandler,
                                          boolean compactForm,
                                          Label... externalLabels) {

        var mapping = HashMap.<Label, Label>newHashMap(externalLabels.length + 1);
        for (var extLabel : externalLabels) {
            mapping.put(extLabel, null);
        }

        var endLabel = parent.newLabel();
        BlockCodeBuilderImpl topBlock = new BlockCodeBuilderImpl(parent, endLabel);
        topBlock.start();
        var excTable = new ArrayList<Label>();
        topBlock.transforming(
                (cob, coe) -> {
                    switch (coe) {
                        case BranchInstruction bi -> {
                            if (mapping.containsKey(bi.target())) { //call finalizer for this label
                                mapping.compute(bi.target(), (l, finalizerLabel) -> {
                                    if (finalizerLabel == null || !compactForm) { //finalizer not built yet or expanded form
                                        if (bi.opcode().isUnconditionalBranch()) {
                                            finalizerLabel = cob.newBoundLabel();
                                            if ((excTable.size() & 1) != 0) excTable.add(finalizerLabel); //try block ends here
                                            finalizerHandler.accept(cob); //unconditional branch finalizer
                                            if (topBlock.reachable()) cob.with(bi);
                                        } else {
                                            var bypass = cob.newLabel();
                                            cob.branchInstruction(BytecodeHelpers.reverseBranchOpcode(bi.opcode()), bypass);
                                            finalizerLabel = cob.newBoundLabel();
                                            if ((excTable.size() & 1) != 0) excTable.add(finalizerLabel); //try block ends here
                                            finalizerHandler.accept(cob); //conditional branch finalizer
                                            if (topBlock.reachable()) cob.goto_(bi.target());
                                            cob.labelBinding(bypass);
                                        }
                                    } else {
                                        cob.branchInstruction(bi.opcode(), finalizerLabel);
                                    }
                                    return finalizerLabel;
                                });
                            } else {
                                cob.with(bi); //bypass without finalizer
                            }
                        }
                        case ReturnInstruction ri -> {
                            mapping.compute(null, (l, finalizerLabel) -> {
                                if (finalizerLabel == null || !compactForm) { //finalizer not built yet or expanded form
                                    finalizerLabel = cob.newBoundLabel();
                                    if ((excTable.size() & 1) != 0) excTable.add(finalizerLabel); //try block ends here
                                    if (ri.typeKind() == TypeKind.VoidType) {
                                        finalizerHandler.accept(cob); //void return finalizer
                                        if (topBlock.reachable()) cob.with(ri);
                                    } else {
                                        var retSlot = cob.allocateLocal(ri.typeKind());
                                        cob.storeInstruction(ri.typeKind(), retSlot);
                                        finalizerHandler.accept(cob); //non-void return finalizer
                                        if (topBlock.reachable()) {
                                            cob.loadInstruction(ri.typeKind(), retSlot)
                                               .with(ri);
                                        }
                                    }
                                } else {
                                    cob.goto_(finalizerLabel);
                                }
                                return finalizerLabel;
                            });
                        }
                        case Instruction i -> {
                            if ((excTable.size() & 1) == 0) excTable.add(cob.newBoundLabel()); //try block re-starts here
                            cob.with(coe);
                        }
                        default -> cob.with(coe);
                    }
                },
                tryHandler::accept);
        if (topBlock.isEmpty()) {
            throw new IllegalStateException("The body of the try block is empty");
        }
        if (topBlock.reachable()) {
            if ((excTable.size() & 1) != 0) excTable.add(topBlock.newBoundLabel());  //try block ends here
            finalizerHandler.accept(topBlock); //pass-through finalizer
            topBlock.goto_(topBlock.endLabel());
        } else {
            if ((excTable.size() & 1) != 0) excTable.add(topBlock.newBoundLabel());  //try block ends here
        }
        if (!excTable.isEmpty()) {
            var handlerLabel = topBlock.newBoundLabel();
            for (int i = 0; i < excTable.size(); i += 2) {
                topBlock.exceptionCatchAll(excTable.get(i), excTable.get(i + 1), handlerLabel);
            }
            var excSlot = topBlock.allocateLocal(TypeKind.ReferenceType);
            topBlock.astore(excSlot);
            finalizerHandler.accept(topBlock); //exception handler finalizer
            if (topBlock.reachable()) {
                topBlock.aload(excSlot)
                        .athrow();
            }
        }
        topBlock.end();
        parent.labelBinding(endLabel);
    }
}
