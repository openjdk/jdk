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
package jdk.classfile;

import jdk.classfile.attribute.RuntimeInvisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import jdk.classfile.attribute.StackMapTableAttribute;
import jdk.classfile.impl.AbstractInstruction;

/**
 * A {@link ClassfileElement} that can appear when traversing the elements
 * of a {@link CodeModel} or be presented to a {@link CodeBuilder}.  Code elements
 * are either an {@link Instruction}, which models an instruction in the body
 * of a method, or a {@link PseudoInstruction}, which models metadata from
 * the code attribute, such as line number metadata, local variable metadata,
 * exception metadata, label target metadata, etc.
 */
public sealed interface CodeElement extends ClassfileElement
        permits Instruction, PseudoInstruction, AbstractInstruction,
                CustomAttribute, RuntimeVisibleTypeAnnotationsAttribute, RuntimeInvisibleTypeAnnotationsAttribute,
                StackMapTableAttribute {
    /**
     * {@return the kind of this instruction}
     */
    Kind codeKind();

    /**
     * {@return the opcode of this instruction}
     */
    Opcode opcode();

    /**
     * {@return the size in bytes of this instruction}
     */
    int sizeInBytes();

    /**
     * Kinds of instructions.
     */
    enum Kind {
        LOAD, STORE, INCREMENT, BRANCH, LOOKUP_SWITCH, TABLE_SWITCH, RETURN, THROW_EXCEPTION,
        FIELD_ACCESS, INVOKE, INVOKE_DYNAMIC,
        NEW_OBJECT, NEW_PRIMITIVE_ARRAY, NEW_REF_ARRAY, NEW_MULTI_ARRAY,
        TYPE_CHECK, ARRAY_LOAD, ARRAY_STORE, STACK, CONVERT, OPERATOR, CONSTANT,
        MONITOR, NOP, UNSUPPORTED,
        LABEL_TARGET, EXCEPTION_CATCH, CHARACTER_RANGE, LOCAL_VARIABLE, LOCAL_VARIABLE_TYPE, LINE_NUMBER,
        PARAMETER_ANNOTATION, STACK_MAP, TYPE_ANNOTATION, END;
    }

}
