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
 * The {@link ShortCarriesFloat16Type} is the {@link VectorElementType} that describes
 * the lane type of a {@code Float16Vector}. Its name makes the semantics
 * explicit: a {@code Float16} value carried in a {@code short}.
 *
 * <p>Float16 is <strong>not</strong> a Java primitive type and therefore does
 * not appear in any of the scalar {@link PrimitiveType} lists. As a
 * {@link VectorElementType} it appears in vector-lane-typed lists such as
 * {@link CodeGenerationDataNameType#VECTOR_ELEMENT_TYPES} and
 * {@link CodeGenerationDataNameType#FLOATING_VECTOR_ELEMENT_TYPES}, which are
 * consumed by vector-only generators (e.g. {@code Operations.VECTOR_OPERATIONS}).
 *
 * <p>The carrier type for a {@code Float16Vector} lane is {@code short}, so
 * {@link #name()} (the code-usable type, per the {@code name()} contract)
 * returns {@code "short"}. The logical lane element type token used in
 * {@code VectorOperators.Conversion.of*} expressions and
 * {@code Float16Vector.SPECIES_*} is {@code Float16}, returned by
 * {@link #vectorElementClass()}.
 *
 * <p>NaN handling note: there are multiple bit representations for NaN within
 * {@code short}/{@code Float16}. Consumers comparing {@code short[]} carrier
 * arrays should canonicalize via {@code Float.float16ToFloat} (which returns a
 * canonical NaN) before structural comparison.
 */
public final class ShortCarriesFloat16Type implements VectorElementType {
    private static final Generator<Short> GEN_FLOAT16 = Generators.G.float16s();

    /** The singleton instance. */
    public static final ShortCarriesFloat16Type SHORT_CARRIES_FLOAT16 = new ShortCarriesFloat16Type();

    private ShortCarriesFloat16Type() {}

    @Override
    public boolean isSubtypeOf(DataName.Type other) {
        return other instanceof ShortCarriesFloat16Type;
    }

    @Override
    public String name() {
        return "short";
    }

    @Override
    public String carrierTypeName() {
        return "short";
    }

    @Override
    public String vectorElementClass() {
        return "Float16";
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
        return name();
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
