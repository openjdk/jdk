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

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.instruction.*;

public class InstructionModelToCodeBuilder {

    public static void toBuilder(CodeElement model, CodeBuilder cb) {
        switch (model) {
            case LoadInstruction im ->
                cb.loadLocal(im.typeKind(), im.slot());
            case StoreInstruction im ->
                cb.storeLocal(im.typeKind(), im.slot());
            case IncrementInstruction im ->
                cb.iinc(im.slot(), im.constant());
            case BranchInstruction im ->
                cb.branch(im.opcode(), im.target());
            case LookupSwitchInstruction im ->
                cb.lookupswitch(im.defaultTarget(), im.cases());
            case TableSwitchInstruction im ->
                cb.tableswitch(im.lowValue(), im.highValue(), im.defaultTarget(), im.cases());
            case ReturnInstruction im ->
                cb.return_(im.typeKind());
            case ThrowInstruction im ->
                cb.athrow();
            case FieldInstruction im ->
                cb.fieldAccess(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol());
            case InvokeInstruction im ->
                cb.invoke(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol(), im.isInterface());
            case InvokeDynamicInstruction im ->
                cb.invokedynamic(DynamicCallSiteDesc.of(im.bootstrapMethod(), im.name().stringValue(), MethodTypeDesc.ofDescriptor(im.type().stringValue()), im.bootstrapArgs().toArray(ConstantDesc[]::new)));
            case NewObjectInstruction im ->
                cb.new_(im.className().asSymbol());
            case NewPrimitiveArrayInstruction im ->
                cb.newarray(im.typeKind());
            case NewReferenceArrayInstruction im ->
                cb.anewarray(im.componentType());
            case NewMultiArrayInstruction im ->
                cb.multianewarray(im.arrayType(), im.dimensions());
            case TypeCheckInstruction im ->
                cb.with(TypeCheckInstruction.of(im.opcode(), im.type().asSymbol()));
            case ArrayLoadInstruction im ->
                cb.arrayLoad(im.typeKind());
            case ArrayStoreInstruction im ->
                cb.arrayStore(im.typeKind());
            case StackInstruction im ->
                cb.with(StackInstruction.of(im.opcode()));
            case ConvertInstruction im ->
                cb.conversion(im.fromType(), im.toType());
            case OperatorInstruction im ->
                cb.with(OperatorInstruction.of(im.opcode()));
            case ConstantInstruction im ->
                cb.loadConstant(im.opcode(), im.constantValue());
            case MonitorInstruction im ->
                cb.with(MonitorInstruction.of(im.opcode()));
            case NopInstruction im ->
                cb.nop();
            case LabelTarget im ->
                cb.labelBinding(im.label());
            case ExceptionCatch im ->
                cb.exceptionCatch(im.tryStart(), im.tryEnd(), im.handler(), im.catchType());
            default ->
                throw new IllegalArgumentException("not yet implemented: " + model);
        }
    }
}
