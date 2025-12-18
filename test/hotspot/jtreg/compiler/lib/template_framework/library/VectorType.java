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

import java.util.List;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.DataName;

/**
 * The {@link VectorType} models the Vector API types.
 */
public abstract class VectorType implements CodeGenerationDataNameType {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static final VectorType.Vector BYTE_64  = new VectorType.Vector(CodeGenerationDataNameType.bytes(), 8);
    public static final VectorType.Vector BYTE_128 = new VectorType.Vector(CodeGenerationDataNameType.bytes(), 16);
    public static final VectorType.Vector BYTE_256 = new VectorType.Vector(CodeGenerationDataNameType.bytes(), 32);
    public static final VectorType.Vector BYTE_512 = new VectorType.Vector(CodeGenerationDataNameType.bytes(), 64);

    public static final VectorType.Vector SHORT_64  = new VectorType.Vector(CodeGenerationDataNameType.shorts(), 4);
    public static final VectorType.Vector SHORT_128 = new VectorType.Vector(CodeGenerationDataNameType.shorts(), 8);
    public static final VectorType.Vector SHORT_256 = new VectorType.Vector(CodeGenerationDataNameType.shorts(), 16);
    public static final VectorType.Vector SHORT_512 = new VectorType.Vector(CodeGenerationDataNameType.shorts(), 32);

    public static final VectorType.Vector INT_64  = new VectorType.Vector(CodeGenerationDataNameType.ints(), 2);
    public static final VectorType.Vector INT_128 = new VectorType.Vector(CodeGenerationDataNameType.ints(), 4);
    public static final VectorType.Vector INT_256 = new VectorType.Vector(CodeGenerationDataNameType.ints(), 8);
    public static final VectorType.Vector INT_512 = new VectorType.Vector(CodeGenerationDataNameType.ints(), 16);

    public static final VectorType.Vector LONG_64  = new VectorType.Vector(CodeGenerationDataNameType.longs(), 1);
    public static final VectorType.Vector LONG_128 = new VectorType.Vector(CodeGenerationDataNameType.longs(), 2);
    public static final VectorType.Vector LONG_256 = new VectorType.Vector(CodeGenerationDataNameType.longs(), 4);
    public static final VectorType.Vector LONG_512 = new VectorType.Vector(CodeGenerationDataNameType.longs(), 8);

    public static final VectorType.Vector FLOAT_64  = new VectorType.Vector(CodeGenerationDataNameType.floats(), 2);
    public static final VectorType.Vector FLOAT_128 = new VectorType.Vector(CodeGenerationDataNameType.floats(), 4);
    public static final VectorType.Vector FLOAT_256 = new VectorType.Vector(CodeGenerationDataNameType.floats(), 8);
    public static final VectorType.Vector FLOAT_512 = new VectorType.Vector(CodeGenerationDataNameType.floats(), 16);

    public static final VectorType.Vector DOUBLE_64  = new VectorType.Vector(CodeGenerationDataNameType.doubles(), 1);
    public static final VectorType.Vector DOUBLE_128 = new VectorType.Vector(CodeGenerationDataNameType.doubles(), 2);
    public static final VectorType.Vector DOUBLE_256 = new VectorType.Vector(CodeGenerationDataNameType.doubles(), 4);
    public static final VectorType.Vector DOUBLE_512 = new VectorType.Vector(CodeGenerationDataNameType.doubles(), 8);

    private final String vectorTypeName;

    private VectorType(String vectorTypeName) {
        this.vectorTypeName = vectorTypeName;
    }

    @Override
    public final String name() {
        return vectorTypeName;
    }

    @Override
    public String toString() {
        return name();
    }

    @Override
    public boolean isSubtypeOf(DataName.Type other) {
        // Each type has a unique instance.
        return this == other;
    }

    private static final String vectorTypeName(PrimitiveType elementType) {
        return switch(elementType.name()) {
            case "byte"   -> "ByteVector";
            case "short"  -> "ShortVector";
            case "char"   -> throw new UnsupportedOperationException("VectorAPI has no char vector type");
            case "int"    -> "IntVector";
            case "long"   -> "LongVector";
            case "float"  -> "FloatVector";
            case "double" -> "DoubleVector";
            default       -> throw new UnsupportedOperationException("Not supported: " + elementType.name());
        };
    }

    public static final class Vector extends VectorType {
        public final PrimitiveType elementType;
        public final int length; // lane count
        public final String speciesName;

        public final Mask maskType;
        public final Shuffle shuffleType;

        private Vector(PrimitiveType elementType, int length) {
            super(vectorTypeName(elementType));
            this.elementType = elementType;
            this.length = length;
            this.speciesName = name() + ".SPECIES_" + (byteSize() * 8);
            this.maskType = new Mask(this);
            this.shuffleType = new Shuffle(this);
        }

        @Override
        public final Object con() {
            int r = RANDOM.nextInt(64);
            if (r == 0) { return name() + ".zero(" + speciesName + ")"; }
            return List.of(name(), ".broadcast(", speciesName, ", ", elementType.con(), ")");
        }

        public int byteSize() {
            return elementType.byteSize() * length;
        }
    }

    public static final class Mask extends VectorType {
        public final Vector vectorType;

        private Mask(Vector vectorType) {
            super("VectorMask<" + vectorType.elementType.boxedTypeName() + ">");
            this.vectorType = vectorType;
        }

        @Override
        public final Object con() {
            // TODO: more options?
            return List.of("VectorMask.fromLong(", vectorType.speciesName, ", ",
                           CodeGenerationDataNameType.longs().con(), ")");
        }
    }

    public static final class Shuffle extends VectorType {
        public final Vector vectorType;

        private Shuffle(Vector vectorType) {
            super("VectorShuffle<" + vectorType.elementType.boxedTypeName() + ">");
            this.vectorType = vectorType;
        }

        @Override
        public final Object con() {
            // TODO: more options?
            return List.of("VectorShuffle.iota(", vectorType.speciesName, ", ",
                           CodeGenerationDataNameType.ints().con(), ", ",
                           CodeGenerationDataNameType.ints().con(), ", true)");
        }
    }
}
