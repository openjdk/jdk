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

package compiler.lib.template_library;

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.generators.Generators;
import compiler.lib.generators.Generator;
import compiler.lib.generators.RestrictableGenerator;

import compiler.lib.template_framework.Name;

public abstract class PrimitiveType extends Type {
    private static final Random RANDOM = Utils.getRandomInstance();
    private static final RestrictableGenerator<Integer> GEN_BYTE = Generators.G.safeRestrict(Generators.G.ints(), Byte.MIN_VALUE, Byte.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_CHAR = Generators.G.safeRestrict(Generators.G.ints(), Character.MIN_VALUE, Character.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_SHORT = Generators.G.safeRestrict(Generators.G.ints(), Short.MIN_VALUE, Short.MAX_VALUE);
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();
    private static final Generator<Double> GEN_DOUBLE = Generators.G.doubles();
    private static final Generator<Float> GEN_FLOAT = Generators.G.floats();

    @Override
    public boolean isSubtypeOf(Name.Type other) {
        // Primitives are only subtypes of their own Primitive type.
        return this.getClass() == other.getClass();
    }

    public abstract int sizeInBits();
    public abstract String boxedTypeName();
    public abstract String vectorAPITypeName();
    public abstract boolean isFloating();

    static final class ByteType extends PrimitiveType {
        public static final ByteType INSTANCE = new ByteType();

        @Override
        public final String name() { return "byte"; }

        @Override
        public String boxedTypeName() { return "Byte"; }

        @Override
        public String vectorAPITypeName() { return "ByteVector"; }

        @Override
        public final Object con() {
            return "(byte)" + GEN_BYTE.next();
        }

        @Override
        public int sizeInBits() { return 8; }

        @Override
        public final boolean isFloating() { return false; }
    }

    static final class CharType extends PrimitiveType {
        public static final CharType INSTANCE = new CharType();

        @Override
        public final String name() { return "char"; }

        @Override
        public String boxedTypeName() { return "Character"; }

        @Override
        public String vectorAPITypeName() { throw new UnsupportedOperationException("VectorAPI has no char vector type."); }

        @Override
        public final Object con() {
            return "(char)" + GEN_CHAR.next();
        }

        @Override
        public int sizeInBits() { return 16; }

        @Override
        public final boolean isFloating() { return false; }
    }

    static final class ShortType extends PrimitiveType {
        public static final ShortType INSTANCE = new ShortType();

        @Override
        public final String name() { return "short"; }

        @Override
        public String boxedTypeName() { return "Short"; }

        @Override
        public String vectorAPITypeName() { return "ShortVector"; }

        @Override
        public final Object con() {
            return "(short)" + GEN_SHORT.next();
        }

        @Override
        public int sizeInBits() { return 16; }

        @Override
        public final boolean isFloating() { return false; }
    }

    static final class IntType extends PrimitiveType {
        public static final IntType INSTANCE = new IntType();

        @Override
        public final String name() { return "int"; }

        @Override
        public String boxedTypeName() { return "Integer"; }

        @Override
        public String vectorAPITypeName() { return "IntVector"; }

        @Override
        public final Object con() {
            return GEN_INT.next();
        }

        @Override
        public int sizeInBits() { return 32; }

        @Override
        public final boolean isFloating() { return false; }
    }

    static final class LongType extends PrimitiveType {
        public static final LongType INSTANCE = new LongType();

        @Override
        public final String name() { return "long"; }

        @Override
        public String boxedTypeName() { return "Long"; }

        @Override
        public String vectorAPITypeName() { return "LongVector"; }

        @Override
        public final Object con() {
            return GEN_LONG.next();
        }

        @Override
        public int sizeInBits() { return 64; }

        @Override
        public final boolean isFloating() { return false; }
    }

    static final class FloatType extends PrimitiveType {
        public static final FloatType INSTANCE = new FloatType();

        @Override
        public final String name() { return "float"; }

        @Override
        public String boxedTypeName() { return "Float"; }

        @Override
        public String vectorAPITypeName() { return "FloatVector"; }

        @Override
        public final Object con() {
            return GEN_FLOAT.next();
        }

        @Override
        public int sizeInBits() { return 32; }

        @Override
        public final boolean isFloating() { return true; }
    }

    static final class DoubleType extends PrimitiveType {
        public static final DoubleType INSTANCE = new DoubleType();

        @Override
        public final String name() { return "double"; }

        @Override
        public String boxedTypeName() { return "Double"; }

        @Override
        public String vectorAPITypeName() { return "DoubleVector"; }

        @Override
        public final Object con() {
            return GEN_DOUBLE.next();
        }

        @Override
        public int sizeInBits() { return 64; }

        @Override
        public final boolean isFloating() { return true; }
    }

    static final class BooleanType extends PrimitiveType {
        public static final BooleanType INSTANCE = new BooleanType();

        @Override
        public final String name() { return "boolean"; }

        @Override
        public String boxedTypeName() { return "Boolean"; }

        @Override
        public String vectorAPITypeName() { throw new UnsupportedOperationException("VectorAPI has no boolean vector type."); }

        @Override
        public final Object con() {
            // TODO: generator for boolean? Could have different probabilities!
            return RANDOM.nextInt() % 2 == 0;
        }

        @Override
        public int sizeInBits() { throw new UnsupportedOperationException("boolean does not have number of bits"); }

        @Override
        public final boolean isFloating() { return false; }
    }
}
