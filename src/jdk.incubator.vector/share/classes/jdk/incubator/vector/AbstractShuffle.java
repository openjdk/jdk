/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

abstract class AbstractShuffle<E> extends VectorShuffle<E> {
    static final IntUnaryOperator IDENTITY = i -> i;

    // Internal representation allows for a maximum index of 256
    // Values are clipped to [-VLENGTH..VLENGTH-1].

    AbstractShuffle(int length, byte[] reorder) {
        super(reorder);
        assert(length == reorder.length);
        assert(indexesInRange(reorder));
    }

    AbstractShuffle(int length, int[] reorder, boolean partialWrap) {
        this(length, reorder, 0, partialWrap);
    }

    AbstractShuffle(int length, int[] reorder, int offset, boolean partialWrap) {
        super(prepare(length, reorder, offset, partialWrap));
    }

    AbstractShuffle(int length, IntUnaryOperator f, boolean partialWrap) {
        super(prepare(length, f, partialWrap));
    }

    private static byte[] prepare(int length, int[] reorder, int offset, boolean partialWrap) {
        byte[] a = new byte[length];
        for (int i = 0; i < length; i++) {
            int si = reorder[offset + i];
            if (partialWrap) {
                si = partiallyWrapIndex(si, length);
            }
            a[i] = (byte) si;
        }
        return a;
    }

    private static byte[] prepare(int length, IntUnaryOperator f, boolean partialWrap) {
        byte[] a = new byte[length];
        for (int i = 0; i < a.length; i++) {
            int si = f.applyAsInt(i);
            if (partialWrap) {
                si = partiallyWrapIndex(si, length);
            }
            a[i] = (byte) si;
        }
        return a;
    }

    byte[] reorder() {
        return (byte[])getPayload();
    }

    /*package-private*/
    abstract AbstractSpecies<E> vspecies();

    @Override
    @ForceInline
    public final VectorSpecies<E> vectorSpecies() {
        return vspecies();
    }

    @Override
    @ForceInline
    public void intoArray(int[] a, int offset) {
        byte[] reorder = reorder();
        int vlen = reorder.length;
        for (int i = 0; i < vlen; i++) {
            int sourceIndex = reorder[i];
            a[offset + i] = sourceIndex;
        }
    }

    @Override
    @ForceInline
    public int[] toArray() {
        byte[] reorder = reorder();
        int[] a = new int[reorder.length];
        intoArray(a, 0);
        return a;
    }

    /*package-private*/
    @ForceInline
    final
    AbstractVector<E>
    toVectorTemplate() {
        // Note that the values produced by laneSource
        // are already clipped.  At this point we convert
        // them from internal ints (or bytes) into the ETYPE.
        // FIXME: Use a conversion intrinsic for this operation.
        // https://bugs.openjdk.org/browse/JDK-8225740
        return (AbstractVector<E>) vspecies().fromIntValues(toArray());
    }

    @ForceInline
    public final VectorShuffle<E> checkIndexes() {
        if (VectorIntrinsics.VECTOR_ACCESS_OOB_CHECK == 0) {
            return this;
        }
        Vector<E> shufvec = this.toVector();
        VectorMask<E> vecmask = shufvec.compare(VectorOperators.LT, vspecies().zero());
        if (vecmask.anyTrue()) {
            byte[] reorder = reorder();
            throw checkIndexFailed(reorder[vecmask.firstTrue()], length());
        }
        return this;
    }

    @ForceInline
    final VectorShuffle<E> wrapIndexesTemplate() {
        Vector<E> shufvec = this.toVector();
        VectorMask<E> vecmasklt = shufvec.compare(VectorOperators.LT, vspecies().zero());
        VectorMask<E> vecmaskge = shufvec.compare(VectorOperators.GE, vspecies().length());
        VectorMask<E> vecmask = vecmasklt.or(vecmaskge);
        if (vecmask.anyTrue()) {
            // FIXME: vectorize this
            byte[] reorder = reorder();
            return wrapAndRebuild(reorder, vspecies().length());
        }
        return this;
    }

    @ForceInline
    public final VectorShuffle<E> wrapAndRebuild(byte[] oldReorder, int length) {
        int len = oldReorder.length;
        byte[] reorder = new byte[len];
        for (int i = 0; i < len; i++) {
            int si = oldReorder[i];
            si = VectorIntrinsics.wrapToRange(si, length);
            reorder[i] = (byte) si;
        }
        return vspecies().dummyVector().shuffleFromBytes(reorder);
    }

    @ForceInline
    public final VectorMask<E> laneIsValid() {
        Vector<E> shufvec = this.toVector();
        return shufvec.compare(VectorOperators.GE, vspecies().zero());
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

    static boolean indexesInRange(byte[] reorder) {
        int length = reorder.length;
        for (byte si : reorder) {
            if (si >= length || si < -length) {
                boolean assertsEnabled = false;
                assert(assertsEnabled = true);
                if (assertsEnabled) {
                    String msg = ("index "+si+"out of range ["+length+"] in "+
                                  java.util.Arrays.toString(reorder));
                    throw new AssertionError(msg);
                }
                return false;
            }
        }
        return true;
    }
}
