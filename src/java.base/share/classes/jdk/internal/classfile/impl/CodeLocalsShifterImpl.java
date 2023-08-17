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

import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.Signature;
import jdk.internal.classfile.TypeKind;
import jdk.internal.classfile.components.CodeLocalsShifter;
import jdk.internal.classfile.instruction.IncrementInstruction;
import jdk.internal.classfile.instruction.LoadInstruction;
import jdk.internal.classfile.instruction.LocalVariable;
import jdk.internal.classfile.instruction.LocalVariableType;
import jdk.internal.classfile.instruction.StoreInstruction;

import java.util.Arrays;

public final class CodeLocalsShifterImpl implements CodeLocalsShifter {

    private int[] locals = new int[0];
    private final int fixed;

    public CodeLocalsShifterImpl(int fixed) {
        this.fixed = fixed;
    }

    @Override
    public void accept(CodeBuilder cob, CodeElement coe) {
        switch (coe) {
            case LoadInstruction li ->
                cob.loadInstruction(
                        li.typeKind(),
                        shift(cob, li.slot(), li.typeKind()));
            case StoreInstruction si ->
                cob.storeInstruction(
                        si.typeKind(),
                        shift(cob, si.slot(), si.typeKind()));
            case IncrementInstruction ii ->
                cob.incrementInstruction(
                        shift(cob, ii.slot(), TypeKind.IntType),
                        ii.constant());
            case LocalVariable lv ->
                cob.localVariable(
                        shift(cob, lv.slot(), TypeKind.fromDescriptor(lv.type().stringValue())),
                        lv.name(),
                        lv.type(),
                        lv.startScope(),
                        lv.endScope());
            case LocalVariableType lvt ->
                cob.localVariableType(
                        shift(cob, lvt.slot(),
                                (lvt.signatureSymbol() instanceof Signature.BaseTypeSig bsig)
                                        ? TypeKind.fromDescriptor(bsig.signatureString())
                                        : TypeKind.ReferenceType),
                        lvt.name(),
                        lvt.signature(),
                        lvt.startScope(),
                        lvt.endScope());
            default -> cob.with(coe);
        }
    }

    private int shift(CodeBuilder cob, int slot, TypeKind tk) {
        if (tk == TypeKind.VoidType)  throw new IllegalArgumentException("Illegal local void type");
        if (slot >= fixed) {
            int key = 2*slot - fixed + tk.slotSize() - 1;
            if (key >= locals.length) locals = Arrays.copyOf(locals, key + 20);
            slot = locals[key] - 1;
            if (slot < 0) {
                slot = cob.allocateLocal(tk);
                locals[key] = slot + 1;
                if (tk.slotSize() == 2) locals[key - 1] = slot + 1;
            }
        }
        return slot;
    }
}
