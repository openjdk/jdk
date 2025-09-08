/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

abstract class AbstractShuffle<E> extends VectorShuffle<E> {
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
        Class<?> etype = dsp.elementType();
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
