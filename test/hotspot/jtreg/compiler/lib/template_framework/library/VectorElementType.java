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
 *       (byte, short, int, long, float, double). For these {@link #name()} is
 *       the primitive keyword
 *       (e.g. {@code "int"}) and {@link #vectorElementClass()} is the same
 *       token, so {@code vectorElementClass() + ".class"} yields the primitive
 *       {@code Class<?>} literal ({@code int.class}).</li>
 *   <li>{@link ShortCarriesFloat16Type} - the {@code Float16Vector} lane type. Float16
 *       has no Java primitive keyword; its lanes are stored in a {@code short[]}
 *       carrier, so {@link #name()} returns the code-usable carrier keyword
 *       {@code "short"} (consistent with {@link #toString()}), while
 *       {@link #vectorElementClass()} returns {@code "Float16"} so that
 *       {@code vectorElementClass() + ".class"} ({@code Float16.class}) is the
 *       token expected by
 *       {@code VectorOperators.Conversion.ofCast}/{@code ofReinterpret}.</li>
 * </ul>
 *
 * <p>This interface lives <strong>outside</strong> the scalar
 * {@link PrimitiveType} type lists (e.g. {@code PRIMITIVE_TYPES},
 * {@code FLOATING_TYPES}). Those lists model Java scalar types and are consumed
 * by scalar fuzzers. Vector-lane lists (e.g. {@code VECTOR_ELEMENT_TYPES},
 * {@code FLOATING_VECTOR_ELEMENT_TYPES}) are typed as {@code List<VectorElementType>}
 * and may include {@link ShortCarriesFloat16Type#SHORT_CARRIES_FLOAT16}.
 */
public interface VectorElementType extends CodeGenerationDataNameType {

    /**
     * The string whose {@code + ".class"} form is the lane element
     * {@code Class<?>} literal expected by the Vector API conversion factories
     * ({@code VectorOperators.Conversion.ofCast}/{@code ofReinterpret}) and by
     * {@code Float16Vector.SPECIES_*}/{@code IntVector.SPECIES_*} lookups.
     *
     * <p>Unlike {@link #name()} (which must always be a Java type usable
     * directly in code, e.g. for variable declarations and casts), this token
     * is the <em>logical</em> lane element type. For Java primitive lanes the
     * two coincide ({@code "int"} -&gt; {@code int.class}); for {@code Float16}
     * lanes {@link #name()} is the carrier {@code "short"} while this returns
     * {@code "Float16"} ({@code Float16.class}).
     *
     * @return The logical lane element type token (e.g. {@code "int"},
     *         {@code "float"}, {@code "Float16"}).
     */
    String vectorElementClass();

    /**
     * @return The element type of the Java carrier array used to hold these
     *         lanes when calling {@code fromArray}/{@code intoArray}. For most
     *         lane types this is the same as {@link #name()}; for
     *         {@code Float16} it is {@code "short"}.
     */
    String carrierTypeName();

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
