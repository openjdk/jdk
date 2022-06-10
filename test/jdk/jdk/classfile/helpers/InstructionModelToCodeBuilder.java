/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package helpers;

import java.lang.constant.ConstantDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodTypeDesc;

import jdk.classfile.CodeBuilder;
import jdk.classfile.CodeElement;
import jdk.classfile.instruction.ArrayLoadInstruction;
import jdk.classfile.instruction.ArrayStoreInstruction;
import jdk.classfile.instruction.BranchInstruction;
import jdk.classfile.instruction.ConstantInstruction;
import jdk.classfile.instruction.ConvertInstruction;
import jdk.classfile.instruction.ExceptionCatch;
import jdk.classfile.instruction.FieldInstruction;
import jdk.classfile.instruction.IncrementInstruction;
import jdk.classfile.instruction.InvokeDynamicInstruction;
import jdk.classfile.instruction.InvokeInstruction;
import jdk.classfile.instruction.LabelTarget;
import jdk.classfile.instruction.LoadInstruction;
import jdk.classfile.instruction.LookupSwitchInstruction;
import jdk.classfile.instruction.MonitorInstruction;
import jdk.classfile.instruction.NewMultiArrayInstruction;
import jdk.classfile.instruction.NewObjectInstruction;
import jdk.classfile.instruction.NewPrimitiveArrayInstruction;
import jdk.classfile.instruction.NewReferenceArrayInstruction;
import jdk.classfile.instruction.OperatorInstruction;
import jdk.classfile.instruction.ReturnInstruction;
import jdk.classfile.instruction.StackInstruction;
import jdk.classfile.instruction.StoreInstruction;
import jdk.classfile.instruction.TableSwitchInstruction;
import jdk.classfile.instruction.TypeCheckInstruction;

public class InstructionModelToCodeBuilder {

    public static void toBuilder(CodeElement model, CodeBuilder cb) {
        switch (model.codeKind()) {
            case LOAD: {
                LoadInstruction im = (LoadInstruction) model;
                cb.loadInstruction(im.typeKind(), im.slot());
                return;
            }
            case STORE: {
                StoreInstruction im = (StoreInstruction) model;
                cb.storeInstruction(im.typeKind(), im.slot());
                return;
            }
            case INCREMENT: {
                IncrementInstruction im = (IncrementInstruction) model;
                cb.incrementInstruction(im.slot(), im.constant());
                return;
            }
            case BRANCH: {
                BranchInstruction im = (BranchInstruction) model;
                cb.branchInstruction(im.opcode(), im.target());
                return;
            }
            case LOOKUP_SWITCH: {
                LookupSwitchInstruction im = (LookupSwitchInstruction) model;
                cb.lookupSwitchInstruction(im.defaultTarget(), im.cases());
                return;
            }
            case TABLE_SWITCH: {
                TableSwitchInstruction im = (TableSwitchInstruction) model;
                cb.tableSwitchInstruction(im.lowValue(), im.highValue(), im.defaultTarget(), im.cases());
                return;
            }
            case RETURN: {
                ReturnInstruction im = (ReturnInstruction) model;
                cb.returnInstruction(im.typeKind());
                return;
            }
            case THROW_EXCEPTION: {
                cb.throwInstruction();
                return;
            }
            case FIELD_ACCESS: {
                FieldInstruction im = (FieldInstruction) model;
                cb.fieldInstruction(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol());
                return;
            }
            case INVOKE: {
                InvokeInstruction im = (InvokeInstruction) model;
                cb.invokeInstruction(im.opcode(), im.owner().asSymbol(), im.name().stringValue(), im.typeSymbol(), im.isInterface());
                return;
            }
            case INVOKE_DYNAMIC: {
                InvokeDynamicInstruction im = (InvokeDynamicInstruction) model;
                cb.invokeDynamicInstruction(DynamicCallSiteDesc.of(im.bootstrapMethod(), im.name().stringValue(), MethodTypeDesc.ofDescriptor(im.type().stringValue()), im.bootstrapArgs().toArray(ConstantDesc[]::new)));
                return;
            }
            case NEW_OBJECT: {
                NewObjectInstruction im = (NewObjectInstruction) model;
                cb.newObjectInstruction(im.className().asSymbol());
                return;
            }
            case NEW_PRIMITIVE_ARRAY:
                cb.newPrimitiveArrayInstruction(((NewPrimitiveArrayInstruction) model).typeKind());
                return;

            case NEW_REF_ARRAY:
                cb.newReferenceArrayInstruction(((NewReferenceArrayInstruction) model).componentType());
                return;

            case NEW_MULTI_ARRAY: {
                NewMultiArrayInstruction im = (NewMultiArrayInstruction) model;
                cb.newMultidimensionalArrayInstruction(im.dimensions(), im.arrayType());
                return;
            }

            case TYPE_CHECK: {
                TypeCheckInstruction im = (TypeCheckInstruction) model;
                cb.typeCheckInstruction(im.opcode(), im.type().asSymbol());
                return;
            }
            case ARRAY_LOAD: {
                ArrayLoadInstruction im = (ArrayLoadInstruction) model;
                cb.arrayLoadInstruction(im.typeKind());
                return;
            }
            case ARRAY_STORE: {
                ArrayStoreInstruction im = (ArrayStoreInstruction) model;
                cb.arrayStoreInstruction(im.typeKind());
                return;
            }
            case STACK: {
                StackInstruction im = (StackInstruction) model;
                cb.stackInstruction(im.opcode());
                return;
            }
            case CONVERT: {
                ConvertInstruction im = (ConvertInstruction) model;
                cb.convertInstruction(im.fromType(), im.toType());
                return;
            }
            case OPERATOR: {
                OperatorInstruction im = (OperatorInstruction) model;
                cb.operatorInstruction(im.opcode());
                return;
            }
            case CONSTANT: {
                ConstantInstruction im = (ConstantInstruction) model;
                cb.constantInstruction(im.opcode(), im.constantValue());
                return;
            }
            case MONITOR: {
                MonitorInstruction im = (MonitorInstruction) model;
                cb.monitorInstruction(im.opcode());
                return;
            }
            case NOP: {
                cb.nopInstruction();
                return;
            }
            case LABEL_TARGET: {
                LabelTarget im = (LabelTarget) model;
                cb.labelBinding(im.label());
                return;
            }
            case EXCEPTION_CATCH: {
                ExceptionCatch im = (ExceptionCatch) model;
                cb.exceptionCatch(im.tryStart(), im.tryEnd(), im.handler(), im.catchType());
                return;
            }
            case LOCAL_VARIABLE: {
                throw new IllegalArgumentException("not yet implemented: " + model);
            }
            case LINE_NUMBER: {
                throw new IllegalArgumentException("not yet implemented: " + model);
            }
        }
    }
}
