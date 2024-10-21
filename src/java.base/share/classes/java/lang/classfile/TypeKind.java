/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.classfile;

import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.TypeDescriptor;

import jdk.internal.vm.annotation.Stable;

/**
 * Describes the data types Java Virtual Machine operates on.
 * This omits {@code returnAddress} (JVMS {@jvms 2.3.3}),
 * which is only used by discontinued {@link
 * DiscontinuedInstruction.JsrInstruction jsr} and {@link
 * DiscontinuedInstruction.RetInstruction ret} instructions,
 * and includes {@link #VOID void} (JVMS {@jvms 4.3.3}), which
 * appears as a method return type.
 *
 * <h2 id="computational-type">Computational Type</h2>
 * In the {@code class} file format, local variables (JVMS {@jvms 2.6.1}),
 * and the operand stack (JVMS {@jvms 2.6.2}) of the Java Virtual Machine,
 * {@link #BOOLEAN boolean}, {@link #BYTE byte}, {@link #CHAR char},
 * {@link #SHORT short} types do not exist and are {@linkplain
 * #asLoadable() represented} by the {@link #INT int} computational type.
 * {@link #INT int}, {@link #FLOAT float}, {@link #REFERENCE reference},
 * {@code returnAddress}, {@link #LONG long}, and {@link #DOUBLE doule}
 * are the computational types of the Java Virtual Machine.
 *
 * @jvms 2.2 Data Types
 * @jvms 2.11.1 Types and the Java Virtual Machine
 * @since 24
 */
public enum TypeKind {
    // Elements are grouped so frequently used switch ranges such as
    // primitives (boolean - double) and computational (int - void) are together.
    // This also follows the order of typed opcodes
    // Begin primitive types
    /**
     * The primitive type {@code boolean}. Its {@linkplain ##computational-type
     * computational type} is {@link #INT int}. {@code 0} represents {@code false},
     * and {@code 1} represents {@code true}. It is zero-extended to an {@code int}
     * when loaded onto the operand stack and narrowed by taking the bitwise AND
     * with {@code 1} when stored.
     *
     * @jvms 2.3.4 The {@code boolean} Type
     */
    BOOLEAN(1, 4),
    /**
     * The primitive type {@code byte}. Its {@linkplain ##computational-type
     * computational type} is {@link #INT int}. It is sign-extended to an
     * {@code int} when loaded onto the operand stack and truncated when
     * stored.
     */
    BYTE(1, 8),
    /**
     * The primitive type {@code char}. Its {@linkplain ##computational-type
     * computational type} is {@link #INT int}. It is zero-extended to an
     * {@code int} when loaded onto the operand stack and truncated when
     * stored.
     */
    CHAR(1, 5),
    /**
     * The primitive type {@code short}. Its {@linkplain ##computational-type
     * computational type} is {@link #INT int}. It is sign-extended to an
     * {@code int} when loaded onto the operand stack and truncated when
     * stored.
     */
    SHORT(1, 9),
    // Begin computational types
    /**
     * The primitive type {@code int}.
     */
    INT(1, 10),
    /**
     * The primitive type {@code long}. It is of {@linkplain #slotSize() category} 2.
     */
    LONG(2, 11),
    /**
     * The primitive type {@code float}.
     */
    FLOAT(1, 6),
    /**
     * The primitive type {@code double}. It is of {@linkplain #slotSize() category} 2.
     */
    DOUBLE(2, 7),
    // End primitive types
    /**
     * A reference type.
     * @jvms 2.4 Reference Types and Values
     */
    REFERENCE(1, -1),
    /**
     * The {@code void} type, for absence of a value. While this is not a data type,
     * this can be a method return type indicating no change in {@linkplain #slotSize()
     * operand stack depth}.
     *
     * @jvms 4.3.3 Method Descriptors
     */
    VOID(0, -1);
    // End computational types

    private @Stable ClassDesc upperBound;
    private final int slots;
    private final int newarrayCode;

    TypeKind(int slots, int newarrayCode) {
        this.slots = slots;
        this.newarrayCode = newarrayCode;
    }

    /**
     * {@return the most specific upper bound field descriptor that can store any value
     * of this type} This is the primitive class descriptor for primitive types and
     * {@link #VOID void} and {@link ConstantDescs#CD_Object Object} descriptor for
     * {@link #REFERENCE reference}.
     */
    public ClassDesc upperBound() {
        var upper = this.upperBound;
        if (upper == null)
            return this.upperBound = fetchUpperBound();
        return upper;
    }

    private ClassDesc fetchUpperBound() {
        return switch (this) {
            case BOOLEAN -> ConstantDescs.CD_boolean;
            case BYTE -> ConstantDescs.CD_byte;
            case CHAR -> ConstantDescs.CD_char;
            case SHORT -> ConstantDescs.CD_short;
            case INT -> ConstantDescs.CD_int;
            case FLOAT -> ConstantDescs.CD_float;
            case LONG -> ConstantDescs.CD_long;
            case DOUBLE -> ConstantDescs.CD_double;
            case REFERENCE -> ConstantDescs.CD_Object;
            case VOID -> ConstantDescs.CD_void;
        };
    }

    /**
     * {@return the code used by the {@link Opcode#NEWARRAY newarray} instruction to create an array
     * of this component type, or {@code -1} if this type is not supported by {@code newarray}}
     * @jvms 6.5.newarray <i>newarray</i>
     */
    public int newarrayCode() {
        return newarrayCode;
    }

    /**
     * {@return the number of local variable index or operand stack depth consumed by this type}
     * This is also the category of this type for instructions operating on the operand stack without
     * regard to type (JVMS {@jvms 2.11.1}), such as {@link Opcode#POP pop} versus {@link Opcode#POP2
     * pop2}.
     * @jvms 2.6.1 Local Variables
     * @jvms 2.6.2 Operand Stacks
     */
    public int slotSize() {
        return this.slots;
    }

    /**
     * {@return the {@linkplain ##computational-type computational type} for this type, or {@link #VOID void}
     * for {@code void}}
     */
    public TypeKind asLoadable() {
        return ordinal() < 4 ? INT : this;
    }

    /**
     * {@return the component type described by the array code used as an operand to {@link Opcode#NEWARRAY
     * newarray}}
     * @param newarrayCode the operand of the {@code newarray} instruction
     * @throws IllegalArgumentException if the code is invalid
     * @jvms 6.5.newarray <i>newarray</i>
     */
    public static TypeKind fromNewarrayCode(int newarrayCode) {
        return switch (newarrayCode) {
            case 4 -> BOOLEAN;
            case 5 -> CHAR;
            case 6 -> FLOAT;
            case 7 -> DOUBLE;
            case 8 -> BYTE;
            case 9 -> SHORT;
            case 10 -> INT;
            case 11 -> LONG;
            default -> throw new IllegalArgumentException("Bad newarray code: " + newarrayCode);
        };
    }

    /**
     * {@return the type associated with the specified field descriptor}
     * @param s the field descriptor
     * @throws IllegalArgumentException only if the descriptor is not valid
     */
    public static TypeKind fromDescriptor(CharSequence s) {
        if (s.isEmpty()) { // implicit null check
            throw new IllegalArgumentException("Empty descriptor");
        }
        return switch (s.charAt(0)) {
            case '[', 'L' -> REFERENCE;
            case 'B' -> BYTE;
            case 'C' -> CHAR;
            case 'Z' -> BOOLEAN;
            case 'S' -> SHORT;
            case 'I' -> INT;
            case 'F' -> FLOAT;
            case 'J' -> LONG;
            case 'D' -> DOUBLE;
            case 'V' -> VOID;
            default -> throw new IllegalArgumentException("Bad type: " + s);
        };
    }

    /**
     * {@return the type associated with the specified field descriptor}
     * @param descriptor the field descriptor
     */
    public static TypeKind from(TypeDescriptor.OfField<?> descriptor) {
        return descriptor.isPrimitive() // implicit null check
                ? fromDescriptor(descriptor.descriptorString())
                : REFERENCE;
    }
}
