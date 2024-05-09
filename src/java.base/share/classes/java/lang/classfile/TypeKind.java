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

import java.lang.invoke.TypeDescriptor;
import jdk.internal.javac.PreviewFeature;

/**
 * Describes the types that can be part of a field or method descriptor.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public enum TypeKind {
    /** the primitive type byte */
    ByteType("byte", "B", 8),
    /** the primitive type short */
    ShortType("short", "S", 9),
    /** the primitive type int */
    IntType("int", "I", 10),
    /** the primitive type float */
    FloatType("float", "F", 6),
    /** the primitive type long */
    LongType("long", "J", 11),
    /** the primitive type double */
    DoubleType("double", "D", 7),
    /** a reference type */
    ReferenceType("reference type", "L", -1),
    /** the primitive type char */
    CharType("char", "C", 5),
    /** the primitive type boolean */
    BooleanType("boolean", "Z", 4),
    /** void */
    VoidType("void", "V", -1);

    private final String name;
    private final String descriptor;
    private final int newarrayCode;

    /** {@return the human-readable name corresponding to this type} */
    public String typeName() { return name; }

    /** {@return the field descriptor character corresponding to this type} */
    public String descriptor() { return descriptor; }

    /**
     * {@return the code used by the {@code newarray} opcode corresponding to this type}
     * @since 23
     */
    public int newarrayCode() {
        return newarrayCode;
    }

    /**
     * {@return the number of local variable slots consumed by this type}
     */
    public int slotSize() {
        return switch (this) {
            case VoidType -> 0;
            case LongType, DoubleType -> 2;
            default -> 1;
        };
    }

    /**
     * Erase this type kind to the type which will be used for xLOAD, xSTORE,
     * and xRETURN bytecodes
     * @return the erased type kind
     */
    public TypeKind asLoadable() {
        return switch (this) {
            case BooleanType, ByteType, CharType, ShortType -> TypeKind.IntType;
            default -> this;
        };
    }

    TypeKind(String name, String descriptor, int newarrayCode) {
        this.name = name;
        this.descriptor = descriptor;
        this.newarrayCode = newarrayCode;
    }

    /**
     * {@return the type kind associated with the array type described by the
     * array code used as an operand to {@code newarray}}
     * @param newarrayCode the operand of the {@code newarray} instruction
     * @throws IllegalArgumentException if the code is invalid
     * @since 23
     */
    public static TypeKind fromNewarrayCode(int newarrayCode) {
        return switch (newarrayCode) {
            case 4 -> TypeKind.BooleanType;
            case 5 -> TypeKind.CharType;
            case 6 -> TypeKind.FloatType;
            case 7 -> TypeKind.DoubleType;
            case 8 -> TypeKind.ByteType;
            case 9 -> TypeKind.ShortType;
            case 10 -> TypeKind.IntType;
            case 11 -> TypeKind.LongType;
            default -> throw new IllegalArgumentException("Bad newarray code: " + newarrayCode);
        };
    }

    /**
     * {@return the type kind associated with the specified field descriptor}
     * @param s the field descriptor
     * @throws IllegalArgumentException only if the descriptor is not valid
     */
    public static TypeKind fromDescriptor(CharSequence s) {
        if (s.isEmpty()) { // implicit null check
            throw new IllegalArgumentException("Empty descriptor");
        }
        return switch (s.charAt(0)) {
            case '[', 'L' -> TypeKind.ReferenceType;
            case 'B' -> TypeKind.ByteType;
            case 'C' -> TypeKind.CharType;
            case 'Z' -> TypeKind.BooleanType;
            case 'S' -> TypeKind.ShortType;
            case 'I' -> TypeKind.IntType;
            case 'F' -> TypeKind.FloatType;
            case 'J' -> TypeKind.LongType;
            case 'D' -> TypeKind.DoubleType;
            case 'V' -> TypeKind.VoidType;
            default -> throw new IllegalArgumentException("Bad type: " + s);
        };
    }

    /**
     * {@return the type kind associated with the specified field descriptor}
     * @param descriptor the field descriptor
     */
    public static TypeKind from(TypeDescriptor.OfField<?> descriptor) {
        return descriptor.isPrimitive() // implicit null check
                ? fromDescriptor(descriptor.descriptorString())
                : TypeKind.ReferenceType;
    }
}
