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

/**
 * A {@link VectorElementType} describes a single lane-element of a Vector API
 * vector ({@link VectorType.Vector}). It abstracts over:
 * <ul>
 *   <li>{@link PrimitiveType} - the standard Java primitive lane types
 *       (byte, short, int, long, float, double, and char/boolean where
 *       applicable). For these the carrier and element-class names coincide
 *       with the primitive keyword (e.g. {@code int} / {@code Integer.class}).</li>
 *   <li>{@link Float16VectorType} - the {@code Float16Vector} lane type. Float16
 *       has no Java primitive keyword; its lanes are stored in a {@code short[]}
 *       carrier and identified by {@code Float16.class} in
 *       {@code VectorOperators.Conversion.ofCast}/{@code ofReinterpret}
 *       expressions.</li>
 * </ul>
 *
 * <p>This interface intentionally lives <strong>outside</strong> the scalar
 * {@link PrimitiveType} type lists (e.g. {@code PRIMITIVE_TYPES},
 * {@code FLOATING_TYPES}). Those lists model Java scalar types and are consumed
 * by scalar fuzzers. Vector-lane lists (e.g. {@code VECTOR_ELEMENT_TYPES},
 * {@code FLOATING_VECTOR_ELEMENT_TYPES}) are typed as {@code List<VectorElementType>}
 * and may include {@link Float16VectorType#FLOAT16}.
 */
public interface VectorElementType extends CodeGenerationDataNameType {

    /**
     * @return The logical name of the lane type (e.g. {@code "int"},
     *         {@code "float16"}).
     */
    @Override
    String name();

    /**
     * @return The element type of the Java carrier array used to hold these
     *         lanes when calling {@code fromArray}/{@code intoArray}. For most
     *         lane types this is the same as {@link #name()}; for
     *         {@code float16} it is {@code "short"}.
     */
    String carrierTypeName();

    /**
     * @return The element type token used as a {@code .class} literal in
     *         {@code VectorOperators.Conversion.ofCast}/{@code ofReinterpret}
     *         expressions. For Java primitives this is the primitive keyword
     *         (e.g. {@code "int"}). For {@code float16} this is
     *         {@code "Float16"}.
     */
    String elementTypeName();

    /**
     * @return The boxed type name used to parameterize generic types such as
     *         {@code VectorMask<T>} and {@code VectorShuffle<T>}
     *         (e.g. {@code "Integer"}, {@code "Float16"}).
     */
    String boxedTypeName();

    /**
     * @return Size of the lane type in bytes.
     */
    int byteSize();

    /**
     * @return {@code true} iff the lane type is a floating point type.
     */
    boolean isFloating();

    /**
     * @return A token representing a call to the corresponding pseudo random
     *         number generator from {@link PrimitiveType#generateLibraryRNG()}.
     */
    Object callLibraryRNG();
}
