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

import java.util.List;
import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.template_framework.Name;

public final class VectorAPIType extends Type {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static final VectorAPIType BYTE_64  = new VectorAPIType(Type.bytes(), 8);
    public static final VectorAPIType BYTE_128 = new VectorAPIType(Type.bytes(), 16);
    public static final VectorAPIType BYTE_256 = new VectorAPIType(Type.bytes(), 32);
    public static final VectorAPIType BYTE_512 = new VectorAPIType(Type.bytes(), 64);

    public static final VectorAPIType SHORT_64  = new VectorAPIType(Type.shorts(), 4);
    public static final VectorAPIType SHORT_128 = new VectorAPIType(Type.shorts(), 8);
    public static final VectorAPIType SHORT_256 = new VectorAPIType(Type.shorts(), 16);
    public static final VectorAPIType SHORT_512 = new VectorAPIType(Type.shorts(), 32);

    public static final VectorAPIType INT_64  = new VectorAPIType(Type.ints(), 2);
    public static final VectorAPIType INT_128 = new VectorAPIType(Type.ints(), 4);
    public static final VectorAPIType INT_256 = new VectorAPIType(Type.ints(), 8);
    public static final VectorAPIType INT_512 = new VectorAPIType(Type.ints(), 16);

    public static final VectorAPIType LONG_64  = new VectorAPIType(Type.longs(), 1);
    public static final VectorAPIType LONG_128 = new VectorAPIType(Type.longs(), 2);
    public static final VectorAPIType LONG_256 = new VectorAPIType(Type.longs(), 4);
    public static final VectorAPIType LONG_512 = new VectorAPIType(Type.longs(), 8);

    public static final VectorAPIType FLOAT_64  = new VectorAPIType(Type.floats(), 2);
    public static final VectorAPIType FLOAT_128 = new VectorAPIType(Type.floats(), 4);
    public static final VectorAPIType FLOAT_256 = new VectorAPIType(Type.floats(), 8);
    public static final VectorAPIType FLOAT_512 = new VectorAPIType(Type.floats(), 16);

    public static final VectorAPIType DOUBLE_64  = new VectorAPIType(Type.doubles(), 1);
    public static final VectorAPIType DOUBLE_128 = new VectorAPIType(Type.doubles(), 2);
    public static final VectorAPIType DOUBLE_256 = new VectorAPIType(Type.doubles(), 4);
    public static final VectorAPIType DOUBLE_512 = new VectorAPIType(Type.doubles(), 8);

    public final PrimitiveType elementType;
    public final int length; // number of lanes (i.e. elements)
    public final String vectorType;
    public final String species;

    public final MaskType maskType;

    private VectorAPIType(PrimitiveType elementType, int length) {
        this.elementType = elementType;
        this.length = length;
        this.vectorType = elementType.vectorAPITypeName();
        this.species = vectorType + ".SPECIES_" + (elementType.sizeInBits() * length);
        this.maskType = new MaskType(this);
    }

    @Override
    public boolean isSubtypeOf(Name.Type other) {
        // TODO: re-evaluate
        return this == other;
    }

    @Override
    public final String name() { return vectorType; }

    @Override
    public final Object con() {
        int r = RANDOM.nextInt(64);
        if (r == 0) { return vectorType + ".zero(" + species + ")"; }
        return List.of(vectorType, ".broadcast(", species, ", ", elementType.con(), ")");
    }

    public final int sizeInBits() { return length * elementType.sizeInBits(); }

    public final class MaskType extends Type {
        public final VectorAPIType vectorType;
        public final String vectorMaskTypeName;

        MaskType(VectorAPIType vectorType) {
            this.vectorType = vectorType;
            this.vectorMaskTypeName = "VectorMask<" + vectorType.elementType.boxedTypeName() + ">";
        }

        @Override
        public boolean isSubtypeOf(Name.Type other) {
            // TODO: re-evaluate
            return this == other;
        }

        @Override
        public final String name() { return vectorMaskTypeName; }

        @Override
        public final Object con() {
            // TODO:
            return vectorMaskTypeName + ".allTrue()";
        }

        public final int sizeInBits() { return vectorType.sizeInBits(); }
    }
}
