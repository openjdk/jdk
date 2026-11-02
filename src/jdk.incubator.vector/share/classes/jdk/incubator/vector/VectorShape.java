/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.incubator.vector;

import jdk.internal.vm.annotation.ForceInline;

/**
 * A {@code VectorShape} selects a particular implementation of
 * {@link Vector}s.
 *
 * A shape in combination with the element type determines
 * a particular
 * {@linkplain VectorSpecies#of(Class,VectorShape) vector species}
 * object.
 *
 * @apiNote
 * Because not all shapes are supported by all platforms,
 * shape-agnostic code is more portable.
 * User code that selects particular shapes may
 * fail to run, or run slowly, on some platforms.
 * Use
 * {@link VectorShape#preferredShape() VectorShape.preferredShape()}
 * and
 * {@link VectorSpecies#ofPreferred(Class) VectorSpecies.ofPreferred()}
 * to select the shape that is usually preferable for
 * most uses.
 */
public sealed interface VectorShape permits VectorShapeImpl {
    /**
     * Shape of length 64 bits
     */
    VectorShape S_64_BIT = VectorShapeImpl.S_64_BIT;

    /**
     * Shape of length 128 bits
     */
    VectorShape S_128_BIT = VectorShapeImpl.S_128_BIT;

    /**
     * Shape of length 256 bits
     */
    VectorShape S_256_BIT = VectorShapeImpl.S_256_BIT;

    /**
     * Shape of length 512 bits
     */
    VectorShape S_512_BIT = VectorShapeImpl.S_512_BIT;

    /**
     * Shape of maximum length supported on the platform
     */
    VectorShape S_Max_BIT = VectorShapeImpl.S_Max_BIT;

    /**
     * Returns the size, in bits, of vectors of this shape.
     *
     * @return the size, in bits, of vectors of this shape.
     */
    int vectorBitSize();

    /**
     * Finds a vector species with the given element type
     * and the current shape.
     * Returns the same value as
     * {@code VectorSpecies.of(elementType, this)}.

     * @param elementType the required element type
     * @param <E> the boxed element type
     * @return a species for the given element type and this shape
     * @see VectorSpecies#of(Class, VectorShape)
     */
    <E> VectorSpecies<E> withLanes(Class<E> elementType);

    /**
     * Finds an appropriate shape depending on the
     * proposed bit-size of a vector.
     *
     * @param bitSize the proposed vector size in bits
     * @return a shape corresponding to the vector bit-size
     * @throws IllegalArgumentException if no such vector shape exists
     * @see #vectorBitSize()
     */
    @ForceInline
    public static VectorShape forBitSize(int bitSize) {
        switch (bitSize) {
            case 64:
                return VectorShape.S_64_BIT;
            case 128:
                return VectorShape.S_128_BIT;
            case 256:
                return VectorShape.S_256_BIT;
            case 512:
                return VectorShape.S_512_BIT;
            default:
                if ((bitSize > 0) && (bitSize <= VectorShapeImpl.MAX_VECTOR_SIZE) && (bitSize % VectorShapeImpl.INC_VECTOR_SIZE == 0)) {
                    return VectorShape.S_Max_BIT;
                } else {
                    throw new IllegalArgumentException("Bad vector bit-size: " + bitSize);
                }
        }
    }

    /**
     * Finds an appropriate index shape depending on the
     * proposed index-bit-size and element-size of a vector.
     *
     * @param indexBitSize the proposed index vector size in bits
     * @param elementSize the proposed element size in bits
     * @return a shape corresponding to the index vector bit-size
     * and element size.
     * @throws IllegalArgumentException if no such vector shape exists
     * @see #vectorBitSize()
     */
    public static VectorShape forIndexBitSize(int indexBitSize, int elementSize) {
        switch (indexBitSize) {
            case 32:
            case 64:
                return VectorShape.S_64_BIT;
            case 128:
                return VectorShape.S_128_BIT;
            case 256:
                return VectorShape.S_256_BIT;
            case 512:
                return VectorShape.S_512_BIT;
            default:
                // Calculate the maximum of index bit size and step under given element size.
                // For AArch64 SVE, vector size is from 128 to 2048 bits, in increments of 128.
                // For X86, vector size is 64, 128, 256 or 512.
                int maxBitSize = (VectorShapeImpl.MAX_VECTOR_SIZE/elementSize) * Integer.SIZE;
                int step = (VectorShapeImpl.INC_VECTOR_SIZE/elementSize) * Integer.SIZE;
                if ((indexBitSize > 0) && (indexBitSize <= maxBitSize) && (indexBitSize % step == 0)) {
                    return VectorShape.S_Max_BIT;
                } else {
                    throw new IllegalArgumentException("Bad index vector bit-size: " + indexBitSize);
                }
        }
    }

    /**
     * Finds the largest vector shape supported by the current
     * platform for the element type {@code etype}.
     *
     * @param etype the element type
     * @return the largest vector shape supported by the platform
     *         for {@code etype}
     * @throws IllegalArgumentException if no such vector shape exists
     *         for the element type or the type is not a valid {@code ETYPE}.
     */
    public static VectorShape largestShapeFor(Class<?> etype) {
        return VectorShape.forBitSize(VectorShapeImpl.getMaxVectorBitSize(etype));
    }

    // non-public support for computing preferred shapes

    /**
     * Finds the vector shape preferred by the current platform
     * for all vector element types.
     * <p>
     * The preferred shape by the platform has the largest possible
     * bit-size, under the constraint that all lane sizes are
     * supported, from {@code byte} to {@code double}.  Thus, all the
     * {@linkplain VectorSpecies#ofPreferred(Class) preferred vector species}
     * for various lane types will have a common underlying shape.
     *
     * @return a preferred shape for all element types
     * @throws UnsupportedOperationException if no such shape exists
     * @see VectorSpecies#ofPreferred(Class)
     */
    @ForceInline
    public static VectorShape preferredShape() {
        VectorShape shape = VectorShapeImpl.PREFERRED_SHAPE;
        if (shape != null) {
            return shape;
        }
        return VectorShapeImpl.computePreferredShape();
    }
}
