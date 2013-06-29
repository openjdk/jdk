/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.code;

import com.sun.source.tree.Tree.Kind;

import javax.lang.model.type.TypeKind;

import static com.sun.tools.javac.code.TypeTag.NumericClasses.*;

/** An interface for type tag values, which distinguish between different
 *  sorts of types.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public enum TypeTag {
    /** The tag of the basic type `byte'.
     */
    BYTE(BYTE_CLASS, BYTE_SUPERCLASSES,
            TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `char'.
     */
    CHAR(CHAR_CLASS, CHAR_SUPERCLASSES,
            TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `short'.
     */
    SHORT(SHORT_CLASS, SHORT_SUPERCLASSES,
            TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `int'.
     */
    INT(INT_CLASS, INT_SUPERCLASSES,
            TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `long'.
     */
    LONG(LONG_CLASS, LONG_SUPERCLASSES, TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `float'.
     */
    FLOAT(FLOAT_CLASS, FLOAT_SUPERCLASSES, TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `double'.
     */
    DOUBLE(DOUBLE_CLASS, DOUBLE_CLASS, TypeTagKind.PRIMITIVE | TypeTagKind.NUMERIC),

    /** The tag of the basic type `boolean'.
     */
    BOOLEAN(TypeTagKind.PRIMITIVE),

    /** The tag of the type `void'.
     */
    VOID(TypeTagKind.VOID),

    /** The tag of all class and interface types.
     */
    CLASS(TypeTagKind.REFERENCE),

    /** The tag of all array types.
     */
    ARRAY(TypeTagKind.REFERENCE),

    /** The tag of all (monomorphic) method types.
     */
    METHOD(TypeTagKind.OTHER),

    /** The tag of all package "types".
     */
    PACKAGE(TypeTagKind.OTHER),

    /** The tag of all (source-level) type variables.
     */
    TYPEVAR(TypeTagKind.REFERENCE),

    /** The tag of all type arguments.
     */
    WILDCARD(TypeTagKind.REFERENCE),

    /** The tag of all polymorphic (method-) types.
     */
    FORALL(TypeTagKind.OTHER),

    /** The tag of deferred expression types in method context
     */
    DEFERRED(TypeTagKind.OTHER),

    /** The tag of the bottom type {@code <null>}.
     */
    BOT(TypeTagKind.OTHER),

    /** The tag of a missing type.
     */
    NONE(TypeTagKind.OTHER),

    /** The tag of the error type.
     */
    ERROR(TypeTagKind.REFERENCE | TypeTagKind.PARTIAL),

    /** The tag of an unknown type
     */
    UNKNOWN(TypeTagKind.PARTIAL),

    /** The tag of all instantiatable type variables.
     */
    UNDETVAR(TypeTagKind.PARTIAL),

    /** Pseudo-types, these are special tags
     */
    UNINITIALIZED_THIS(TypeTagKind.OTHER),

    UNINITIALIZED_OBJECT(TypeTagKind.OTHER);

    final boolean isPrimitive;
    final boolean isNumeric;
    final boolean isPartial;
    final boolean isReference;
    final boolean isPrimitiveOrVoid;
    final int superClasses;
    final int numericClass;

    private TypeTag(int kind) {
        this(0, 0, kind);
    }

    private TypeTag(int numericClass, int superClasses, int kind) {
         isPrimitive = (kind & TypeTagKind.PRIMITIVE) != 0;
         isNumeric = (kind & TypeTagKind.NUMERIC) != 0;
         isPartial = (kind & TypeTagKind.PARTIAL) != 0;
         isReference = (kind & TypeTagKind.REFERENCE) != 0;
         isPrimitiveOrVoid = ((kind & TypeTagKind.PRIMITIVE) != 0) ||
                 ((kind & TypeTagKind.VOID) != 0);
         this.superClasses = superClasses;
         this.numericClass = numericClass;
     }

    static class TypeTagKind {
        static final int PRIMITIVE = 1;
        static final int NUMERIC = 2;
        static final int REFERENCE = 4;
        static final int PARTIAL = 8;
        static final int OTHER = 16;
        static final int VOID = 32;
    }

    public static class NumericClasses {
        public static final int BYTE_CLASS = 1;
        public static final int CHAR_CLASS = 2;
        public static final int SHORT_CLASS = 4;
        public static final int INT_CLASS = 8;
        public static final int LONG_CLASS = 16;
        public static final int FLOAT_CLASS = 32;
        public static final int DOUBLE_CLASS = 64;

        static final int BYTE_SUPERCLASSES = BYTE_CLASS | SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int CHAR_SUPERCLASSES = CHAR_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int SHORT_SUPERCLASSES = SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int INT_SUPERCLASSES = INT_CLASS | LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int LONG_SUPERCLASSES = LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;

        static final int FLOAT_SUPERCLASSES = FLOAT_CLASS | DOUBLE_CLASS;
    }

    public boolean isStrictSubRangeOf(TypeTag tag) {
        /*  Please don't change the implementation of this method to call method
         *  isSubRangeOf. Both methods are called from hotspot code, the current
         *  implementation is better performance-wise than the commented modification.
         */
        return (this.superClasses & tag.numericClass) != 0 && this != tag;
    }

    public boolean isSubRangeOf(TypeTag tag) {
        return (this.superClasses & tag.numericClass) != 0;
    }

    /** Returns the number of type tags.
     */
    public static int getTypeTagCount() {
        // last two tags are not included in the total as long as they are pseudo-types
        return (UNDETVAR.ordinal() + 1);
    }

    public Kind getKindLiteral() {
        switch (this) {
        case INT:
            return Kind.INT_LITERAL;
        case LONG:
            return Kind.LONG_LITERAL;
        case FLOAT:
            return Kind.FLOAT_LITERAL;
        case DOUBLE:
            return Kind.DOUBLE_LITERAL;
        case BOOLEAN:
            return Kind.BOOLEAN_LITERAL;
        case CHAR:
            return Kind.CHAR_LITERAL;
        case CLASS:
            return Kind.STRING_LITERAL;
        case BOT:
            return Kind.NULL_LITERAL;
        default:
            throw new AssertionError("unknown literal kind " + this);
        }
    }

    public TypeKind getPrimitiveTypeKind() {
        switch (this) {
        case BOOLEAN:
            return TypeKind.BOOLEAN;
        case BYTE:
            return TypeKind.BYTE;
        case SHORT:
            return TypeKind.SHORT;
        case INT:
            return TypeKind.INT;
        case LONG:
            return TypeKind.LONG;
        case CHAR:
            return TypeKind.CHAR;
        case FLOAT:
            return TypeKind.FLOAT;
        case DOUBLE:
            return TypeKind.DOUBLE;
        case VOID:
            return TypeKind.VOID;
        default:
            throw new AssertionError("unknown primitive type " + this);
        }
    }
}
