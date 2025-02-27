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

abstract class PrimitiveType extends Type {
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

    static final class ByteType extends PrimitiveType {
        public static final ByteType INSTANCE = new ByteType();

        @Override
        public final String name() { return "byte"; }

        @Override
        public final Object con() {
            return "(byte)" + GEN_BYTE.next();
        }
    }

    static final class CharType extends PrimitiveType {
        public static final CharType INSTANCE = new CharType();

        @Override
        public final String name() { return "char"; }

        @Override
        public final Object con() {
            return "(char)" + GEN_CHAR.next();
        }
    }

    static final class ShortType extends PrimitiveType {
        public static final ShortType INSTANCE = new ShortType();

        @Override
        public final String name() { return "short"; }

        @Override
        public final Object con() {
            return "(short)" + GEN_SHORT.next();
        }
    }

    static final class IntType extends PrimitiveType {
        public static final IntType INSTANCE = new IntType();

        @Override
        public final String name() { return "int"; }

        @Override
        public final Object con() {
            return GEN_INT.next();
        }
    }

    static final class LongType extends PrimitiveType {
        public static final LongType INSTANCE = new LongType();

        @Override
        public final String name() { return "long"; }

        @Override
        public final Object con() {
            return GEN_LONG.next();
        }
    }

    static final class FloatType extends PrimitiveType {
        public static final FloatType INSTANCE = new FloatType();

        @Override
        public final String name() { return "float"; }

        @Override
        public final Object con() {
            return GEN_FLOAT.next();
        }
    }

    static final class DoubleType extends PrimitiveType {
        public static final DoubleType INSTANCE = new DoubleType();

        @Override
        public final String name() { return "double"; }

        @Override
        public final Object con() {
            return GEN_DOUBLE.next();
        }
    }

    static final class BooleanType extends PrimitiveType {
        public static final BooleanType INSTANCE = new BooleanType();

        @Override
        public final String name() { return "boolean"; }

        @Override
        public final Object con() {
            // TODO: generator for boolean? Could have different probabilities!
            return RANDOM.nextInt() % 2 == 0;
        }
    }
}
