/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.template_framework.library;

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.generators.Generators;
import compiler.lib.generators.Generator;
import compiler.lib.generators.RestrictableGenerator;

import compiler.lib.template_framework.DataName;

/**
 * The {@link PrimitiveType} models Java's primitive types, and provides a set
 * of useful methods for code generation, such as the {@link #byteSize} and
 * {@link #boxedTypeName}.
 */
public final class PrimitiveType implements CodeGenerationDataNameType {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();

    private static enum Kind { BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE, BOOLEAN };

    // We have one static instance each, so we do not have duplicated instances.
    static final PrimitiveType BYTES    = new PrimitiveType(Kind.BYTE   );
    static final PrimitiveType SHORTS   = new PrimitiveType(Kind.SHORT  );
    static final PrimitiveType CHARS    = new PrimitiveType(Kind.CHAR   );
    static final PrimitiveType INTS     = new PrimitiveType(Kind.INT    );
    static final PrimitiveType LONGS    = new PrimitiveType(Kind.LONG   );
    static final PrimitiveType FLOATS   = new PrimitiveType(Kind.FLOAT  );
    static final PrimitiveType DOUBLES  = new PrimitiveType(Kind.DOUBLE );
    static final PrimitiveType BOOLEANS = new PrimitiveType(Kind.BOOLEAN);

    final Kind kind;

    // Private constructor so nobody can create duplicate instances.
    private PrimitiveType(Kind kind) {
        this.kind = kind;
    }

    @Override
    public boolean isSubtypeOf(DataName.Type other) {
        return (other instanceof PrimitiveType pt) && pt.kind == kind;
    }

    @Override
    public String name() {
        return switch (kind) {
            case BYTE    -> "byte";
            case SHORT   -> "short";
            case CHAR    -> "char";
            case INT     -> "int";
            case LONG    -> "long";
            case FLOAT   -> "float";
            case DOUBLE  -> "double";
            case BOOLEAN -> "boolean";
        };
    }

    @Override
    public String toString() {
        return name();
    }

    public Object con() {
        return switch (kind) {
            case BYTE    -> "(byte)" + GEN_BYTE.next();
            case SHORT   -> "(short)" + GEN_SHORT.next();
            case CHAR    -> "(char)" + GEN_CHAR.next();
            case INT     -> GEN_INT.next();
            case LONG    -> GEN_LONG.next();
            case FLOAT   -> GEN_FLOAT.next();
            case DOUBLE  -> GEN_DOUBLE.next();
            case BOOLEAN -> RANDOM.nextBoolean();
        };
    }

    /**
     * Provides the size of the type in bytes.
     *
     * @return Size of the type in bytes.
     * @throws UnsupportedOperationException for boolean which has no defined size.
     */
    public int byteSize() {
        return switch (kind) {
            case BYTE    -> 1;
            case SHORT, CHAR -> 2;
            case INT, FLOAT -> 4;
            case LONG, DOUBLE -> 8;
            case BOOLEAN -> { throw new UnsupportedOperationException("boolean does not have a defined 'size'"); }
        };
    }

    /**
     * Provides the name of the boxed type.
     *
     * @return the name of the boxed type.
     */
    public String boxedTypeName() {
        return switch (kind) {
            case BYTE    -> "Byte";
            case SHORT   -> "Short";
            case CHAR    -> "Character";
            case INT     -> "Integer";
            case LONG    -> "Long";
            case FLOAT   -> "Float";
            case DOUBLE  -> "Double";
            case BOOLEAN -> "Boolean";
        };
    }

    /**
     * Indicates if the type is a floating point type.
     *
     * @return true iff the type is a floating point type.
     */
    public boolean isFloating() {
        return switch (kind) {
            case BYTE, SHORT, CHAR, INT, LONG, BOOLEAN -> false;
            case FLOAT, DOUBLE -> true;
        };
    }
}
