/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.template_framework.DataName;
import compiler.lib.template_framework.Template;

/**
 * The {@link CodeGenerationDataNameType} extends the {@link DataName.Type} with
 * additional functionality for code generation. These types with their extended
 * functionality can be used with many other code generation facilities in the
 * library, such as generating random {@code Expression}s.
 *
 * <p>This module distinguishes <strong>scalar</strong> Java types and
 * <strong>Vector API lane-element</strong> types:
 * <ul>
 *   <li>Scalar {@code PRIMITIVE_TYPES}/{@code FLOATING_TYPES}/etc. enumerate
 *       only Java primitive types ({@code byte}, {@code short}, ...).
 *       These lists are typed as {@code List<PrimitiveType>} and are consumed
 *       by scalar fuzzers / scalar code generation. {@link Float16Type} (the
 *       scalar {@code Float16} logical type) is included in
 *       {@link #SCALAR_NUMERIC_TYPES}.</li>
 *   <li>Vector-lane lists ({@code VECTOR_ELEMENT_TYPES},
 *       {@code FLOATING_VECTOR_ELEMENT_TYPES}, ...) enumerate the lane types
 *       valid for {@code VectorType.Vector}. These are typed as
 *       {@code List<VectorElementType>} and additionally include
 *       {@link ShortCarriesFloat16Type#SHORT_CARRIES_FLOAT16} since {@code Float16Vector} is a real
 *       Vector API type whose lanes happen to have no Java primitive
 *       keyword.</li>
 * </ul>
 * Vector generators (e.g. {@code Operations.VECTOR_OPERATIONS}) consume the
 * vector-lane lists; scalar generators (e.g.
 * {@code Operations.PRIMITIVE_OPERATIONS}) consume the scalar lists.
 */
public interface CodeGenerationDataNameType extends DataName.Type {

    /**
     * This method provides a random constant value for the type, which can
     * be used as a token inside a {@link Template}.
     *
     * @return A random constant value.
     */
    Object con();

    /**
     * The byte {@link PrimitiveType}.
     *
     * @return The byte {@link PrimitiveType}.
     */
    static PrimitiveType bytes()    { return PrimitiveType.BYTES; }

    /**
     * The short {@link PrimitiveType}.
     *
     * @return The short {@link PrimitiveType}.
     */
    static PrimitiveType shorts()   { return PrimitiveType.SHORTS; }

    /**
     * The char {@link PrimitiveType}.
     *
     * @return The char {@link PrimitiveType}.
     */
    static PrimitiveType chars()    { return PrimitiveType.CHARS; }

    /**
     * The int {@link PrimitiveType}.
     *
     * @return The int {@link PrimitiveType}.
     */
    static PrimitiveType ints()     { return PrimitiveType.INTS; }

    /**
     * The long {@link PrimitiveType}.
     *
     * @return The long {@link PrimitiveType}.
     */
    static PrimitiveType longs()    { return PrimitiveType.LONGS; }

    /**
     * The float {@link PrimitiveType}.
     *
     * @return The float {@link PrimitiveType}.
     */
    static PrimitiveType floats()   { return PrimitiveType.FLOATS; }

    /**
     * The double {@link PrimitiveType}.
     *
     * @return The double {@link PrimitiveType}.
     */
    static PrimitiveType doubles()  { return PrimitiveType.DOUBLES; }

    /**
     * The boolean {@link PrimitiveType}.
     *
     * @return The boolean {@link PrimitiveType}.
     */
    static PrimitiveType booleans() { return PrimitiveType.BOOLEANS; }

    /**
     * The {@code short}-carried {@code Float16} lane-element type used by
     * {@code Float16Vector}. This is a {@link VectorElementType},
     * <strong>not</strong> a Java {@link PrimitiveType}; it appears in
     * vector-lane lists but never in the scalar
     * {@code PRIMITIVE_TYPES}/{@code FLOATING_TYPES} lists. Its lanes carry the
     * raw bits of the {@code Float16} value in a {@code short}, hence the
     * explicit {@code shortCarriesFloat16} naming.
     *
     * @return The {@code Float16Vector} {@link VectorElementType}.
     */
    static ShortCarriesFloat16Type shortCarriesFloat16() { return ShortCarriesFloat16Type.SHORT_CARRIES_FLOAT16; }

    /**
     * The {@code Float16} scalar (boxed) type.
     *
     * @return The scalar {@code Float16} type.
     */
    static CodeGenerationDataNameType float16() { return Float16Type.FLOAT16; }

    /**
     * List of all {@link PrimitiveType}s.
     */
    List<PrimitiveType> PRIMITIVE_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs(),
        floats(),
        doubles(),
        booleans()
    );

    /**
     * List of all integral {@link PrimitiveType}s (byte, char, short, int, long).
     */
    List<PrimitiveType> INTEGRAL_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs()
    );

    /**
     * List of {@link PrimitiveType}s (int, long).
     */
    List<PrimitiveType> INT_LONG_TYPES = List.of(
        ints(),
        longs()
    );

    /**
     * List of all subword {@link PrimitiveType}s (byte, char, short).
     */
    List<PrimitiveType> SUBWORD_TYPES = List.of(
        bytes(),
        chars(),
        shorts()
    );

    /**
     * List of all floating {@link PrimitiveType}s (float, double).
     */
    List<PrimitiveType> FLOATING_TYPES = List.of(
        floats(),
        doubles()
    );

    /**
     * List of all integral and floating {@link PrimitiveType}s.
     */
    List<PrimitiveType> INTEGRAL_AND_FLOATING_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs(),
        floats(),
        doubles()
    );

    /**
     * List of all scalar numeric types.
     */
    List<CodeGenerationDataNameType> SCALAR_NUMERIC_TYPES = List.of(
        bytes(),
        chars(),
        shorts(),
        ints(),
        longs(),
        floats(),
        doubles(),
        booleans(),
        float16()
    );

    // --------------------------------------------------------------------
    // Vector API lane-element type lists.
    //
    // These are typed as List<VectorElementType> and may include
    // ShortCarriesFloat16Type.SHORT_CARRIES_FLOAT16 in addition to the Java
    // primitive lane carriers. Vector generators (e.g. Operations.VECTOR_OPS)
    // iterate over these lists to enumerate the lane types they support.
    // --------------------------------------------------------------------

    /**
     * All Vector API lane-element types: every Java numeric primitive lane
     * carrier plus {@link ShortCarriesFloat16Type#SHORT_CARRIES_FLOAT16}.
     */
    List<VectorElementType> VECTOR_ELEMENT_TYPES = List.of(
        bytes(),
        shorts(),
        shortCarriesFloat16(),
        ints(),
        longs(),
        floats(),
        doubles()
    );

    /**
     * Integral Vector API lane-element types (byte, short, int, long).
     */
    List<VectorElementType> INTEGRAL_VECTOR_ELEMENT_TYPES = List.of(
        bytes(),
        shorts(),
        ints(),
        longs()
    );

    /**
     * Floating Vector API lane-element types (float16, float, double).
     */
    List<VectorElementType> FLOATING_VECTOR_ELEMENT_TYPES = List.of(
        shortCarriesFloat16(),
        floats(),
        doubles()
    );

    /**
     * Vector API lane-element types whose lanes are 32/64 bits and integral
     * (int, long).
     */
    List<VectorElementType> INT_LONG_VECTOR_ELEMENT_TYPES = List.of(
        ints(),
        longs()
    );

    // --------------------------------------------------------------------
    // Concrete VectorType lists (typed as the concrete Vector subclasses).
    // --------------------------------------------------------------------

    List<VectorType.Vector> VECTOR_BYTE_VECTOR_TYPES = List.of(
        VectorType.BYTE_64,
        VectorType.BYTE_128,
        VectorType.BYTE_256,
        VectorType.BYTE_512
    );

    List<VectorType.Vector> VECTOR_SHORT_VECTOR_TYPES = List.of(
        VectorType.SHORT_64,
        VectorType.SHORT_128,
        VectorType.SHORT_256,
        VectorType.SHORT_512
    );

    List<VectorType.Vector> VECTOR_FLOAT16_VECTOR_TYPES = List.of(
        VectorType.FLOAT16_64,
        VectorType.FLOAT16_128,
        VectorType.FLOAT16_256,
        VectorType.FLOAT16_512
    );

    List<VectorType.Vector> VECTOR_INT_VECTOR_TYPES = List.of(
        VectorType.INT_64,
        VectorType.INT_128,
        VectorType.INT_256,
        VectorType.INT_512
    );

    List<VectorType.Vector> VECTOR_LONG_VECTOR_TYPES = List.of(
        VectorType.LONG_64,
        VectorType.LONG_128,
        VectorType.LONG_256,
        VectorType.LONG_512
    );

    List<VectorType.Vector> VECTOR_FLOAT_VECTOR_TYPES = List.of(
        VectorType.FLOAT_64,
        VectorType.FLOAT_128,
        VectorType.FLOAT_256,
        VectorType.FLOAT_512
    );

    List<VectorType.Vector> VECTOR_DOUBLE_VECTOR_TYPES = List.of(
        VectorType.DOUBLE_64,
        VectorType.DOUBLE_128,
        VectorType.DOUBLE_256,
        VectorType.DOUBLE_512
    );

    List<VectorType.Vector> VECTOR_VECTOR_TYPES = Utils.concat(
        VECTOR_BYTE_VECTOR_TYPES,
        VECTOR_SHORT_VECTOR_TYPES,
        VECTOR_FLOAT16_VECTOR_TYPES,
        VECTOR_INT_VECTOR_TYPES,
        VECTOR_LONG_VECTOR_TYPES,
        VECTOR_FLOAT_VECTOR_TYPES,
        VECTOR_DOUBLE_VECTOR_TYPES
    );

    List<VectorType.Mask> VECTOR_MASK_TYPES =
        VECTOR_VECTOR_TYPES.stream().map(t -> t.maskType).toList();

    List<VectorType.Shuffle> VECTOR_SHUFFLE_TYPES =
        VECTOR_VECTOR_TYPES.stream().map(t -> t.shuffleType).toList();

    List<VectorType> VECTOR_TYPES = Utils.concat(
        VECTOR_VECTOR_TYPES,
        VECTOR_MASK_TYPES,
        VECTOR_SHUFFLE_TYPES
    );

    List<CodeGenerationDataNameType> ALL_TYPES = Utils.concat(
        SCALAR_NUMERIC_TYPES,
        VECTOR_TYPES
    );
}
