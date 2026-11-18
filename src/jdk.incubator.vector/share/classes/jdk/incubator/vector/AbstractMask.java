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

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.incubator.vector.VectorOperators.*;

abstract sealed class AbstractMask<E> extends VectorMask<E>
        permits ByteVector64.ByteMask64, ByteVector128.ByteMask128, ByteVector256.ByteMask256, ByteVector512.ByteMask512, ByteVectorMax.ByteMaskMax,
        DoubleVector64.DoubleMask64, DoubleVector128.DoubleMask128, DoubleVector256.DoubleMask256, DoubleVector512.DoubleMask512, DoubleVectorMax.DoubleMaskMax,
        FloatVector64.FloatMask64, FloatVector128.FloatMask128, FloatVector256.FloatMask256, FloatVector512.FloatMask512, FloatVectorMax.FloatMaskMax,
        IntVector64.IntMask64, IntVector128.IntMask128, IntVector256.IntMask256, IntVector512.IntMask512, IntVectorMax.IntMaskMax,
        LongVector64.LongMask64, LongVector128.LongMask128, LongVector256.LongMask256, LongVector512.LongMask512, LongVectorMax.LongMaskMax,
        ShortVector64.ShortMask64, ShortVector128.ShortMask128, ShortVector256.ShortMask256, ShortVector512.ShortMask512, ShortVectorMax.ShortMaskMax,
        Float16Vector64.Float16Mask64, Float16Vector128.Float16Mask128, Float16Vector256.Float16Mask256, Float16Vector512.Float16Mask512,
        Float16VectorMax.Float16MaskMax {
    AbstractMask(boolean[] bits) {
        super(bits);
    }

    /*package-private*/
    abstract boolean[] getBits();

    // Unary operator

    interface MUnOp {
        boolean apply(int i, boolean a);
    }

    abstract AbstractMask<E> uOp(MUnOp f);

    // Binary operator

    interface MBinOp {
        boolean apply(int i, boolean a, boolean b);
    }

    abstract AbstractMask<E> bOp(VectorMask<E> o, MBinOp f);

    /*package-private*/
    abstract AbstractSpecies<E> vspecies();

    @Override
    @ForceInline
    public final VectorSpecies<E> vectorSpecies() {
        return vspecies();
    }

    @Override
    @ForceInline
    public void intoArray(boolean[] bits, int i) {
        AbstractSpecies<E> vsp = (AbstractSpecies<E>) vectorSpecies();
        int laneCount = vsp.laneCount();
        i = VectorIntrinsics.checkFromIndexSize(i, laneCount, bits.length);
        VectorSupport.store(
            vsp.maskType(), vsp.laneTypeOrdinal(), laneCount,
            bits, (long) i + Unsafe.ARRAY_BOOLEAN_BASE_OFFSET, false,
            this, bits, i,
            (c, idx, s) -> System.arraycopy(s.getBits(), 0, c, (int) idx, s.length()));

    }

    @Override
    public boolean[] toArray() {
        return getBits().clone();
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public
    <F> VectorMask<F> check(Class<F> elementType) {
        if (vectorSpecies().elementType() != elementType) {
            throw AbstractSpecies.checkFailed(this, elementType);
        }
        return (VectorMask<F>) this;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public
    <F> VectorMask<F> check(VectorSpecies<F> species) {
        if (species != vectorSpecies()) {
            throw AbstractSpecies.checkFailed(this, species);
        }
        return (VectorMask<F>) this;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    <F> VectorMask<F> check(Class<? extends VectorMask<F>> maskClass, Vector<F> vector) {
        if (!sameSpecies(maskClass, vector)) {
            throw AbstractSpecies.checkFailed(this, vector);
        }
        return (VectorMask<F>) this;
    }

    @ForceInline
    private <F> boolean sameSpecies(Class<? extends VectorMask<F>> maskClass, Vector<F> vector) {
        boolean same = getClass() == maskClass;
        assert (same == (vectorSpecies() == vector.species())) : same;
        return same;
    }

    @Override
    @ForceInline
    public final VectorMask<E> andNot(VectorMask<E> m) {
        return and(m.not());
    }

    @Override
    @ForceInline
    public final VectorMask<E> eq(VectorMask<E> m) {
        return xor(m.not());
    }

    /*package-private*/
    static boolean anyTrueHelper(boolean[] bits) {
        // FIXME: Maybe use toLong() != 0 here.
        for (boolean i : bits) {
            if (i) return true;
        }
        return false;
    }

    /*package-private*/
    static boolean allTrueHelper(boolean[] bits) {
        // FIXME: Maybe use not().toLong() == 0 here.
        for (boolean i : bits) {
            if (!i) return false;
        }
        return true;
    }

    /*package-private*/
    static int trueCountHelper(boolean[] bits) {
        int c = 0;
        for (boolean i : bits) {
            if (i) c++;
        }
        return c;
    }

    /*package-private*/
    static int firstTrueHelper(boolean[] bits) {
        for (int i = 0; i < bits.length; i++) {
            if (bits[i])  return i;
        }
        return bits.length;
    }

    /*package-private*/
    static int lastTrueHelper(boolean[] bits) {
        for (int i = bits.length-1; i >= 0; i--) {
            if (bits[i])  return i;
        }
        return -1;
    }

    /*package-private*/
    static long toLongHelper(boolean[] bits) {
        long res = 0;
        long set = 1;
        for (int i = 0; i < bits.length; i++) {
            res = bits[i] ? res | set : res;
            set = set << 1;
        }
        return res;
    }

    /*package-private*/
    @ForceInline
    VectorMask<E> indexPartiallyInRange(int offset, int limit) {
        int vlength = length();
        Vector<E> iota = vectorSpecies().zero().addIndex(1);
        VectorMask<E> badMask = checkIndex0(offset, limit, iota, vlength);
        return badMask.not();
    }

    /*package-private*/
    @ForceInline
    VectorMask<E> indexPartiallyInRange(long offset, long limit) {
        int vlength = length();
        Vector<E> iota = vectorSpecies().zero().addIndex(1);
        VectorMask<E> badMask = checkIndex0(offset, limit, iota, vlength);
        return badMask.not();
    }

    @Override
    @ForceInline
    public VectorMask<E> indexInRange(int offset, int limit) {
        if (offset < 0) {
            return this.and(indexPartiallyInRange(offset, limit));
        } else if (offset >= limit) {
            return vectorSpecies().maskAll(false);
        } else if (limit - offset >= length()) {
            return this;
        }
        return this.and(indexPartiallyInUpperRange(offset, limit));
    }

    @ForceInline
    public VectorMask<E> indexInRange(long offset, long limit) {
        if (offset < 0) {
            return this.and(indexPartiallyInRange(offset, limit));
        } else if (offset >= limit) {
            return vectorSpecies().maskAll(false);
        } else if (limit - offset >= length()) {
            return this;
        }
        return this.and(indexPartiallyInUpperRange(offset, limit));
    }

    abstract VectorMask<E> indexPartiallyInUpperRange(long offset, long limit);

    /*package-private*/
    @ForceInline
    AbstractVector<E>
    toVectorTemplate() {
        AbstractSpecies<E> vsp = vspecies();
        Vector<E> zero = vsp.broadcast(0);
        Vector<E> mone = vsp.broadcast(-1);
        // -1 will result in the most significant bit being set in
        // addition to some or all other lane bits.
        // For integral types, *all* lane bits will be set.
        // The bits for -1.0 are like {0b10111*0000*}.
        // FIXME: Use a conversion intrinsic for this operation.
        // https://bugs.openjdk.org/browse/JDK-8225740
        return (AbstractVector<E>) zero.blend(mone, this);
    }

    /**
     * Test if a masked memory access at a given offset into an array
     * of the given length will stay within the array.
     * The per-lane offsets are iota*esize.
     */
    /*package-private*/
    @ForceInline
    void checkIndexByLane(long offset, long length,
                          Vector<E> iota,
                          int esize) {
        if (VectorIntrinsics.VECTOR_ACCESS_OOB_CHECK == 0) {
            return;
        }
        assert length >= 0;
        int vlength = length();
        // badMask marks lanes that are not valid, i.e. fall outside
        // the segment.

        // The implementation assumes that iota*esize will not overflow.
        VectorMask<E> badMask = checkIndex0(offset, length - esize + 1,
                iota.lanewise(VectorOperators.MUL, esize),
                vlength * esize);
        badMask = badMask.and(this);
        // if any active lanes fall outside, throw.
        if (badMask.anyTrue()) {
            int badLane = badMask.firstTrue();
            throw ((AbstractMask<E>)badMask)
                    .checkIndexFailed(offset, badLane, length, esize);
        }
    }

    private
    @ForceInline
    VectorMask<E> checkIndex0(long offset, long length,
                              Vector<E> iotaScaled, int vlengthScaled) {
        // This method takes:
        // - vector length scaled by esize (vlengthScaled)
        // - vector of lane offsets for the given vector type
        //   with values (0, esize, 2 * esize, ..., vlengthScaled - esize) (iotaScaled)
        // - an offset into the segment at which the access
        //   will take place (offset)
        // - the length of the segment, reduced by esize - 1 (length)
        // Returns a mask where bad lanes are set to true
        // and good lanes are set to false.
        // Bad lanes are defined as lanes whose offset falls below 0
        // or above length.
        // Technically, the algorithm is equivalent to:
        // offsets = iotaScaled + offset
        // mask = !(0 <= offsets < length)
        // except it's trying hard to avoid overflows.

        // iotaScaled is scaled by esize, it is the caller's responsibility
        // to adjust the length down by esize - 1 to ensure that the lanes
        // completely fit in bounds. If esize = 1 no adjustment is required.

        // The algorithm assumes that:
        // - values in iotaScaled are between [0, vlengthScaled)
        // - vector type can represent values in [0, vlengthScaled) range with full precision
        //   (this will not work for float8)
        assert vlengthScaled > 0;
        if (length < offset || length < 0 || offset <= -vlengthScaled) {
            return vectorSpecies().maskAll(true);
        }

        // For each index i, the access at i is in-bounds if and only if
        // 0 <= offset + i < length
        if (offset < 0) {
            // 0 <= offset + i is equivalent to E(-offset) < i;
            // E(-offset) will be precise because -vlengthScaled < offset < 0
            VectorMask<E> lowMask =
                    iotaScaled.compare(LT, iotaScaled.broadcast(-offset));
            // check if we need to mask lanes above length.
            // offset < 0 and vlengthScaled > 0, so offset + vlengthScaled will not overflow
            if (offset + vlengthScaled > length) {
                // offset + i < length is equivalent to i < E(length - offset)
                // E(length - offset) will be precise because 0 <= length - offset < vlengthScaled
                VectorMask<E> highMask =
                        iotaScaled.compare(GE, iotaScaled.broadcast(length - offset));
                return highMask.or(lowMask);

            } else {
                return lowMask;
            }
        } else {
            // 0 <= offset + i is guaranteed here because offset >= 0 and i >= 0
            // offset + i < length is equivalent to i < E(min(vlengthScaled, length - offset))
            // length - offset will not underflow because 0 <= offset <= length,
            // length - offset can be clamped to vlengthScaled because i < vlengthScaled.

            // Alternatively, we could directly return an all-false mask if length - offset >= vlengthScaled.
            int indexLimit = Math.clamp(length - offset, 0, vlengthScaled);
            VectorMask<E> highMask =
                    iotaScaled.compare(GE, iotaScaled.broadcast(indexLimit));
            return highMask;
        }
    }

    private IndexOutOfBoundsException checkIndexFailed(long offset, int lane,
                                                       long length, int esize) {
        String msg = String.format("Masked range check failed: "+
                                   "vector mask %s out of bounds at "+
                                   "index %d+%d for length %d",
                                   this, offset, lane * esize, length);
        if (esize != 1) {
            msg += String.format(" (each lane spans %d elements)", esize);
        }
        throw new IndexOutOfBoundsException(msg);
    }

}
