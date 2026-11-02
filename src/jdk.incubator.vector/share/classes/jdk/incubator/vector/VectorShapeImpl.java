/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.Stable;
import jdk.internal.vm.vector.VectorSupport;

final class VectorShapeImpl implements VectorShape {

    static final VectorShapeImpl S_64_BIT = new VectorShapeImpl(0, 64);
    static final VectorShapeImpl S_128_BIT = new VectorShapeImpl(1, 128);
    static final VectorShapeImpl S_256_BIT = new VectorShapeImpl(2, 256);
    static final VectorShapeImpl S_512_BIT = new VectorShapeImpl(3, 512);
    static final VectorShapeImpl S_Max_BIT;

    static {
        int maxBits = getMaxVectorBitSize(byte.class);
        S_Max_BIT = switch (maxBits) {
            case 64 -> S_64_BIT;
            case 128 -> S_128_BIT;
            case 256 -> S_256_BIT;
            case 512 -> S_512_BIT;
            default -> new VectorShapeImpl(4, maxBits);
        };
    }


    final int vectorBitSize;
    final int vectorBitSizeLog2;
    final int switchKey;

    VectorShapeImpl(int switchKey, int vectorBitSize) {
        this.switchKey = switchKey;
        this.vectorBitSize = vectorBitSize;
        this.vectorBitSizeLog2 = Integer.numberOfTrailingZeros(vectorBitSize);
    }

    @ForceInline
    @Override
    public int vectorBitSize() {
        return vectorBitSize;
    }

    @Override
    public <E> VectorSpecies<E> withLanes(Class<E> elementType) {
        return VectorSpecies.of(elementType, this);
    }

    @Override
    public String toString() {
        // Currently a test dependency; might change to be more descriptive later.
        return "S_" + vectorBitSize + "_BIT";
    }

    // Max vector size and increments for local use.
    static final int
            MAX_VECTOR_SIZE = 2048,
            INC_VECTOR_SIZE = 128;

    // Switch keys for local use.
    // We need these because switches keyed on enums
    // don't optimize properly; see JDK-8161245
    static final int
            SK_64_BIT = 0,
            SK_128_BIT = 1,
            SK_256_BIT = 2,
            SK_512_BIT = 3,
            SK_Max_BIT = 4,
            SK_LIMIT = 5;

    /*package-private*/
    static VectorShape ofSwitchKey(int sk) {
        switch (sk) {
            case SK_64_BIT:
                return S_64_BIT;
            case SK_128_BIT:
                return S_128_BIT;
            case SK_256_BIT:
                return S_256_BIT;
            case SK_512_BIT:
                return S_512_BIT;
            case SK_Max_BIT:
                return S_Max_BIT;
        }
        throw new AssertionError();
    }

    static VectorShapeImpl computePreferredShape() {
        int prefBitSize = Integer.MAX_VALUE;
        for (LaneType type : LaneType.values()) {
            Class<?> ctype = type.carrierType;
            prefBitSize = Math.min(prefBitSize, getMaxVectorBitSize(ctype));
        }
        // If these assertions fail, we must reconsider our API portability assumptions.
        assert (prefBitSize >= Double.SIZE && prefBitSize < Integer.MAX_VALUE / Long.SIZE);
        assert (prefBitSize == getMaxVectorBitSize(byte.class));
        var shape = (VectorShapeImpl) VectorShape.forBitSize(prefBitSize);
        PREFERRED_SHAPE = shape;
        return shape;
    }

    /**
     * Returns the maximum vector bit size for a given element type.
     *
     * @param ctype the carrier type.
     * @return the maximum vector bit.
     */
    /*package-private*/
    static int getMaxVectorBitSize(Class<?> ctype) {
        // VectorSupport.getMaxLaneCount may return -1 if C2 is not enabled,
        // or a value smaller than the S_64_BIT.vectorBitSize / elementSizeInBits if MaxVectorSize < 16
        // If so default to S_64_BIT
        int maxLaneCount = VectorSupport.getMaxLaneCount(ctype);
        int elementSizeInBits = LaneType.of(ctype).elementSize;
        return Math.max(maxLaneCount * elementSizeInBits, S_64_BIT.vectorBitSize);
    }

    static @Stable VectorShapeImpl PREFERRED_SHAPE;
}
