/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.generators.Generators;
import compiler.lib.generators.Generator;

import compiler.lib.template_framework.DataName;

/**
 * The {@link Float16VectorType} is the {@link VectorElementType} that describes
 * the lane type of a {@code Float16Vector}.
 *
 * <p>Float16 is <strong>not</strong> a Java primitive type and therefore does
 * not appear in any of the scalar {@link PrimitiveType} lists. As a
 * {@link VectorElementType} it appears in vector-lane-typed lists such as
 * {@link CodeGenerationDataNameType#VECTOR_ELEMENT_TYPES} and
 * {@link CodeGenerationDataNameType#FLOATING_VECTOR_ELEMENT_TYPES}, which are
 * consumed by vector-only generators (e.g. {@code Operations.VECTOR_OPERATIONS}).
 *
 * <p>The carrier type for a {@code Float16Vector} lane is {@code short}; the
 * element type token used in {@code VectorOperators.Conversion.of*} expressions
 * and {@code Float16Vector.SPECIES_*} is {@code Float16}.
 *
 * <p>NaN handling note: there are multiple bit representations for NaN within
 * {@code short}/{@code Float16}. Consumers comparing {@code short[]} carrier
 * arrays should canonicalize via {@code Float.float16ToFloat} (which returns a
 * canonical NaN) before structural comparison.
 */
public final class Float16VectorType implements VectorElementType {
    private static final Generator<Short> GEN_FLOAT16 = Generators.G.float16s();

    /** The singleton instance. */
    public static final Float16VectorType FLOAT16 = new Float16VectorType();

    private Float16VectorType() {}

    @Override
    public boolean isSubtypeOf(DataName.Type other) {
        return other instanceof Float16VectorType;
    }

    @Override
    public String name() {
        return "Float16";
    }

    @Override
    public String carrierTypeName() {
        return "short";
    }

    @Override
    public String boxedTypeName() {
        return "Float16";
    }

    @Override
    public int byteSize() {
        return 2;
    }

    @Override
    public boolean isFloating() {
        return true;
    }

    @Override
    public String toString() {
        // Used by Template `let(...)` hashtag substitution as a Java scalar
        // type for a single lane. Float16 has no Java keyword, so we return
        // the carrier ("short"), which is what Float16Vector.lane(int) returns
        // and what Float16Vector.broadcast(SPECIES, ...) accepts.
        return carrierTypeName();
    }

    @Override
    public Object con() {
        return "(short)" + GEN_FLOAT16.next();
    }

    @Override
    public Object callLibraryRNG() {
        return "LibraryRNG.nextFloat16()";
    }
}
