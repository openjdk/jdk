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
package helpers;

import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.internal.classfile.CodeBuilder;
import jdk.internal.classfile.CodeElement;
import jdk.internal.classfile.instruction.*;

public class InstructionModelToCodeBuilder {

    public static void toBuilder(CodeElement model, CodeBuilder cb) {
        switch (model) {
            case LoadInstruction im ->
                cb.loadInstruction(im.typeKind(), im.slot());
            case StoreInstruction im ->
                cb.storeInstruction(im.typeKind(), im.slot());
            case IncrementInstruction im ->
                cb.incrementInstruction(im.slot(), im.constant());
            case BranchInstruction im ->
                cb.branchInstruction(im.opcode(), im.target());
            case LookupSwitchInstruction im ->
                cb.lookupSwitchInstruction(im.defaultTarget(), im.cases());
            case TableSwitchInstruction im ->
                cb.tableSwitchInstruction(im.lowValue(), im.highValue(), im.defaultTarget(), im.cases());
            case ReturnInstruction im ->
                cb.returnInstruction(im.typeKind());
            case ThrowInstruction im ->
                cb.throwInstruction();
            case FieldInstruction im ->
                cb.fieldInstruction(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol());
            case InvokeInstruction im ->
                cb.invokeInstruction(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol(), im.isInterface());
            case InvokeDynamicInstruction im ->
                cb.invokeDynamicInstruction(DynamicCallSiteDesc.of(im.bootstrapMethod(), im.name().stringValue(), MethodTypeDesc.ofDescriptor(im.type().stringValue()), im.bootstrapArgs().toArray(ConstantDesc[]::new)));
            case NewObjectInstruction im ->
                cb.newObjectInstruction(im.className().asSymbol());
            case NewPrimitiveArrayInstruction im ->
                cb.newPrimitiveArrayInstruction(im.typeKind());
            case NewReferenceArrayInstruction im ->
                cb.newReferenceArrayInstruction(im.componentType());
            case NewMultiArrayInstruction im ->
                cb.newMultidimensionalArrayInstruction(im.dimensions(), im.arrayType());
            case TypeCheckInstruction im ->
                cb.typeCheckInstruction(im.opcode(), im.type().asSymbol());
            case ArrayLoadInstruction im ->
                cb.arrayLoadInstruction(im.typeKind());
            case ArrayStoreInstruction im ->
                cb.arrayStoreInstruction(im.typeKind());
            case StackInstruction im ->
                cb.stackInstruction(im.opcode());
            case ConvertInstruction im ->
                cb.convertInstruction(im.fromType(), im.toType());
            case OperatorInstruction im ->
                cb.operatorInstruction(im.opcode());
            case ConstantInstruction im ->
                cb.constantInstruction(im.opcode(), im.constantValue());
            case MonitorInstruction im ->
                cb.monitorInstruction(im.opcode());
            case NopInstruction im ->
                cb.nopInstruction();
            case LabelTarget im ->
                cb.labelBinding(im.label());
            case ExceptionCatch im ->
                cb.exceptionCatch(im.tryStart(), im.tryEnd(), im.handler(), im.catchType());
            default ->
                throw new IllegalArgumentException("not yet implemented: " + model);
        }
    }
}
