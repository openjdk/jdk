/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.IntUnaryOperator;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

abstract sealed class AbstractShuffle<E> extends VectorShuffle<E>
        permits ByteVector64.ByteShuffle64, ByteVector128.ByteShuffle128, ByteVector256.ByteShuffle256, ByteVector512.ByteShuffle512, ByteVectorMax.ByteShuffleMax,
        DoubleVector64.DoubleShuffle64, DoubleVector128.DoubleShuffle128, DoubleVector256.DoubleShuffle256, DoubleVector512.DoubleShuffle512, DoubleVectorMax.DoubleShuffleMax,
        FloatVector64.FloatShuffle64, FloatVector128.FloatShuffle128, FloatVector256.FloatShuffle256, FloatVector512.FloatShuffle512, FloatVectorMax.FloatShuffleMax,
        IntVector64.IntShuffle64, IntVector128.IntShuffle128, IntVector256.IntShuffle256, IntVector512.IntShuffle512, IntVectorMax.IntShuffleMax,
        LongVector64.LongShuffle64, LongVector128.LongShuffle128, LongVector256.LongShuffle256, LongVector512.LongShuffle512, LongVectorMax.LongShuffleMax,
        ShortVector64.ShortShuffle64, ShortVector128.ShortShuffle128, ShortVector256.ShortShuffle256, ShortVector512.ShortShuffle512, ShortVectorMax.ShortShuffleMax {
    static final IntUnaryOperator IDENTITY = i -> i;

    // Internal representation allows for a maximum index of E.MAX_VALUE - 1
    // Values are clipped to [-VLENGTH..VLENGTH-1].

    AbstractShuffle(Object indices) {
        super(indices);
    }

    /*package-private*/
    abstract AbstractSpecies<E> vspecies();

    @Override
    @ForceInline
    public final VectorSpecies<E> vectorSpecies() {
        return vspecies();
    }

    /*package-private*/
    abstract AbstractVector<?> toBitsVector();

    @ForceInline
    final AbstractVector<?> toBitsVectorTemplate() {
        AbstractSpecies<?> dsp = vspecies().asIntegral();
        int etype = dsp.laneTypeOrdinal();
        Class<?> rvtype = dsp.dummyVector().getClass();
        return VectorSupport.convert(VectorSupport.VECTOR_OP_REINTERPRET,
                                     getClass(), etype, length(),
                                     rvtype, etype, length(),
                                     this, dsp,
                                     (v, s) -> v.toBitsVector0());
    }

    abstract AbstractVector<?> toBitsVector0();

    @Override
    @ForceInline
    public final int[] toArray() {
        int[] res = new int[length()];
        intoArray(res, 0);
        return res;
    }

    @Override
    @ForceInline
    public final <F> VectorShuffle<F> cast(VectorSpecies<F> s) {
        if (length() != s.length()) {
            throw new IllegalArgumentException("VectorShuffle length and species length differ");
        }
        return toBitsVector().bitsToShuffle((AbstractSpecies<F>) s);
    }

    @Override
    @ForceInline
    public final VectorShuffle<E> checkIndexes() {
        if (VectorIntrinsics.VECTOR_ACCESS_OOB_CHECK == 0) {
            return this;
        }
        Vector<?> shufvec = this.toBitsVector();
        VectorMask<?> vecmask = shufvec.compare(VectorOperators.LT, 0);
        if (vecmask.anyTrue()) {
            int[] indices = toArray();
            throw checkIndexFailed(indices[vecmask.firstTrue()], length());
        }
        return this;
    }

    @Override
    @ForceInline
    @SuppressWarnings("unchecked")
    public final
    <F> VectorShuffle<F> check(VectorSpecies<F> species) {
        if (species != vectorSpecies()) {
            throw AbstractSpecies.checkFailed(this, species);
        }
        return (VectorShuffle<F>) this;
    }

    @Override
    @ForceInline
    public final int checkIndex(int index) {
        return checkIndex0(index, length(), (byte)1);
    }

    @Override
    @ForceInline
    public final int wrapIndex(int index) {
        return checkIndex0(index, length(), (byte)0);
    }

    /** Return invalid indexes partially wrapped
     * mod VLENGTH to negative values.
     */
    /*package-private*/
    @ForceInline
    static
    int partiallyWrapIndex(int index, int laneCount) {
        return checkIndex0(index, laneCount, (byte)-1);
    }

    /*package-private*/
    @ForceInline
    static int checkIndex0(int index, int laneCount, byte mode) {
        int wrapped = VectorIntrinsics.wrapToRange(index, laneCount);
        if (mode == 0 || wrapped == index) {
            return wrapped;
        }
        if (mode < 0) {
            return wrapped - laneCount;  // special mode for internal storage
        }
        throw checkIndexFailed(index, laneCount);
    }

    private static IndexOutOfBoundsException checkIndexFailed(int index, int laneCount) {
        int max = laneCount - 1;
        String msg = "required an index in [0.."+max+"] but found "+index;
        return new IndexOutOfBoundsException(msg);
    }
}
