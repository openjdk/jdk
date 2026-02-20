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

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.internal.vm.vector.VectorSupport.*;

import static jdk.incubator.vector.VectorOperators.*;

// -- This file was mechanically generated: Do not edit! -- //

@SuppressWarnings("cast")  // warning: redundant cast
final class ShortVectorMax extends ShortVector {
    static final ShortSpecies VSPECIES =
        (ShortSpecies) ShortVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ShortVectorMax> VCLASS = ShortVectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> ETYPE = short.class; // used by the JVM

    ShortVectorMax(short[] v) {
        super(v);
    }

    // For compatibility as ShortVectorMax::new,
    // stored into species.vectorFactory.
    ShortVectorMax(Object v) {
        this((short[]) v);
    }

    static final ShortVectorMax ZERO = new ShortVectorMax(new short[VLENGTH]);
    static final ShortVectorMax IOTA = new ShortVectorMax(VSPECIES.iotaArray());

    static {
        // Warm up a few species caches.
        // If we do this too much we will
        // get NPEs from bootstrap circularity.
        VSPECIES.dummyVector();
        VSPECIES.withLanes(LaneType.BYTE);
    }

    // Specialized extractors

    @ForceInline
    final @Override
    public ShortSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Short> elementType() { return short.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Short.SIZE; }

    @ForceInline
    @Override
    public final VectorShape shape() { return VSHAPE; }

    @ForceInline
    @Override
    public final int length() { return VLENGTH; }

    @ForceInline
    @Override
    public final int bitSize() { return VSIZE; }

    @ForceInline
    @Override
    public final int byteSize() { return VSIZE / Byte.SIZE; }

    /*package-private*/
    @ForceInline
    final @Override
    short[] vec() {
        return (short[])getPayload();
    }

    /*package-private*/
    @ForceInline
    final @Override
    int laneTypeOrdinal() {
        return LANE_TYPE_ORDINAL;
    }

    // Virtualized constructors

    @Override
    @ForceInline
    public final ShortVectorMax broadcast(short e) {
        return (ShortVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ShortVectorMax broadcast(long e) {
        return (ShortVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ShortMaskMax maskFromArray(boolean[] bits) {
        return new ShortMaskMax(bits);
    }

    @Override
    @ForceInline
    ShortShuffleMax iotaShuffle() { return ShortShuffleMax.IOTA; }

    @Override
    @ForceInline
    ShortShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (ShortShuffleMax) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    ShortShuffleMax shuffleFromArray(int[] indices, int i) { return new ShortShuffleMax(indices, i); }

    @Override
    @ForceInline
    ShortShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new ShortShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ShortVectorMax vectorFactory(short[] vec) {
        return new ShortVectorMax(vec);
    }

    @ForceInline
    final @Override
    ByteVectorMax asByteVectorRaw() {
        return (ByteVectorMax) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    ShortVectorMax uOp(FUnOp f) {
        return (ShortVectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVectorMax uOp(VectorMask<Short> m, FUnOp f) {
        return (ShortVectorMax)
            super.uOpTemplate((ShortMaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ShortVectorMax bOp(Vector<Short> v, FBinOp f) {
        return (ShortVectorMax) super.bOpTemplate((ShortVectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVectorMax bOp(Vector<Short> v,
                     VectorMask<Short> m, FBinOp f) {
        return (ShortVectorMax)
            super.bOpTemplate((ShortVectorMax)v, (ShortMaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ShortVectorMax tOp(Vector<Short> v1, Vector<Short> v2, FTriOp f) {
        return (ShortVectorMax)
            super.tOpTemplate((ShortVectorMax)v1, (ShortVectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVectorMax tOp(Vector<Short> v1, Vector<Short> v2,
                     VectorMask<Short> m, FTriOp f) {
        return (ShortVectorMax)
            super.tOpTemplate((ShortVectorMax)v1, (ShortVectorMax)v2,
                              (ShortMaskMax)m, f);  // specialize
    }

    @ForceInline
    final @Override
    short rOp(short v, VectorMask<Short> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Short,F> conv,
                           VectorSpecies<F> rsp, int part) {
        return super.convertShapeTemplate(conv, rsp, part);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> reinterpretShape(VectorSpecies<F> toSpecies, int part) {
        return super.reinterpretShapeTemplate(toSpecies, part);  // specialize
    }

    // Specialized algebraic operations:

    // The following definition forces a specialized version of this
    // crucial method into the v-table of this class.  A call to add()
    // will inline to a call to lanewise(ADD,), at which point the JIT
    // intrinsic will have the opcode of ADD, plus all the metadata
    // for this particular class, enabling it to generate precise
    // code.
    //
    // There is probably no benefit to the JIT to specialize the
    // masked or broadcast versions of the lanewise method.

    @Override
    @ForceInline
    public ShortVectorMax lanewise(Unary op) {
        return (ShortVectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax lanewise(Unary op, VectorMask<Short> m) {
        return (ShortVectorMax) super.lanewiseTemplate(op, ShortMaskMax.class, (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax lanewise(Binary op, Vector<Short> v) {
        return (ShortVectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax lanewise(Binary op, Vector<Short> v, VectorMask<Short> m) {
        return (ShortVectorMax) super.lanewiseTemplate(op, ShortMaskMax.class, v, (ShortMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVectorMax
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ShortVectorMax) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVectorMax
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Short> m) {
        return (ShortVectorMax) super.lanewiseShiftTemplate(op, ShortMaskMax.class, e, (ShortMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ShortVectorMax
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2) {
        return (ShortVectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVectorMax
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2, VectorMask<Short> m) {
        return (ShortVectorMax) super.lanewiseTemplate(op, ShortMaskMax.class, v1, v2, (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVectorMax addIndex(int scale) {
        return (ShortVectorMax) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final short reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final short reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Short> m) {
        return super.reduceLanesTemplate(op, ShortMaskMax.class, (ShortMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Short> m) {
        return (long) super.reduceLanesTemplate(op, ShortMaskMax.class, (ShortMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ShortShuffleMax toShuffle() {
        return (ShortShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ShortMaskMax test(Test op) {
        return super.testTemplate(ShortMaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMaskMax test(Test op, VectorMask<Short> m) {
        return super.testTemplate(ShortMaskMax.class, op, (ShortMaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ShortMaskMax compare(Comparison op, Vector<Short> v) {
        return super.compareTemplate(ShortMaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMaskMax compare(Comparison op, short s) {
        return super.compareTemplate(ShortMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMaskMax compare(Comparison op, long s) {
        return super.compareTemplate(ShortMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMaskMax compare(Comparison op, Vector<Short> v, VectorMask<Short> m) {
        return super.compareTemplate(ShortMaskMax.class, op, v, (ShortMaskMax) m);
    }


    @Override
    @ForceInline
    public ShortVectorMax blend(Vector<Short> v, VectorMask<Short> m) {
        return (ShortVectorMax)
            super.blendTemplate(ShortMaskMax.class,
                                (ShortVectorMax) v,
                                (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax slice(int origin, Vector<Short> v) {
        return (ShortVectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax slice(int origin) {
        return (ShortVectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax unslice(int origin, Vector<Short> w, int part) {
        return (ShortVectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax unslice(int origin, Vector<Short> w, int part, VectorMask<Short> m) {
        return (ShortVectorMax)
            super.unsliceTemplate(ShortMaskMax.class,
                                  origin, w, part,
                                  (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax unslice(int origin) {
        return (ShortVectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax rearrange(VectorShuffle<Short> s) {
        return (ShortVectorMax)
            super.rearrangeTemplate(ShortShuffleMax.class,
                                    (ShortShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax rearrange(VectorShuffle<Short> shuffle,
                                  VectorMask<Short> m) {
        return (ShortVectorMax)
            super.rearrangeTemplate(ShortShuffleMax.class,
                                    ShortMaskMax.class,
                                    (ShortShuffleMax) shuffle,
                                    (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax rearrange(VectorShuffle<Short> s,
                                  Vector<Short> v) {
        return (ShortVectorMax)
            super.rearrangeTemplate(ShortShuffleMax.class,
                                    (ShortShuffleMax) s,
                                    (ShortVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax compress(VectorMask<Short> m) {
        return (ShortVectorMax)
            super.compressTemplate(ShortMaskMax.class,
                                   (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax expand(VectorMask<Short> m) {
        return (ShortVectorMax)
            super.expandTemplate(ShortMaskMax.class,
                                   (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax selectFrom(Vector<Short> v) {
        return (ShortVectorMax)
            super.selectFromTemplate((ShortVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax selectFrom(Vector<Short> v,
                                   VectorMask<Short> m) {
        return (ShortVectorMax)
            super.selectFromTemplate((ShortVectorMax) v,
                                     ShortMaskMax.class, (ShortMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVectorMax selectFrom(Vector<Short> v1,
                                   Vector<Short> v2) {
        return (ShortVectorMax)
            super.selectFromTemplate((ShortVectorMax) v1, (ShortVectorMax) v2);  // specialize
    }

    @ForceInline
    @Override
    public short lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return laneHelper(i);
    }

    @ForceInline
    public short laneHelper(int i) {
        return (short) VectorSupport.extract(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    short[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public ShortVectorMax withLane(int i, short e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public ShortVectorMax withLaneHelper(int i, short e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    short[] res = v.vec().clone();
                                    res[ix] = (short)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class ShortMaskMax extends AbstractMask<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortMaskMax(boolean[] bits) {
            this(bits, 0);
        }

        ShortMaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ShortMaskMax(boolean val) {
            super(prepare(val));
        }

        private static boolean[] prepare(boolean[] bits, int offset) {
            boolean[] newBits = new boolean[VSPECIES.laneCount()];
            for (int i = 0; i < newBits.length; i++) {
                newBits[i] = bits[offset + i];
            }
            return newBits;
        }

        private static boolean[] prepare(boolean val) {
            boolean[] bits = new boolean[VSPECIES.laneCount()];
            Arrays.fill(bits, val);
            return bits;
        }

        @ForceInline
        final @Override
        public ShortSpecies vspecies() {
            // ISSUE:  This should probably be a @Stable
            // field inside AbstractMask, rather than
            // a megamorphic method.
            return VSPECIES;
        }

        @ForceInline
        boolean[] getBits() {
            return (boolean[])getPayload();
        }

        @Override
        ShortMaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ShortMaskMax(res);
        }

        @Override
        ShortMaskMax bOp(VectorMask<Short> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ShortMaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ShortMaskMax(res);
        }

        @ForceInline
        @Override
        public final
        ShortVectorMax toVector() {
            return (ShortVectorMax) super.toVectorTemplate();  // specialize
        }

        /**
         * Helper function for lane-wise mask conversions.
         * This function kicks in after intrinsic failure.
         */
        @ForceInline
        private final <E>
        VectorMask<E> defaultMaskCast(AbstractSpecies<E> dsp) {
            if (length() != dsp.laneCount())
                throw new IllegalArgumentException("VectorMask length and species length differ");
            boolean[] maskArray = toArray();
            return  dsp.maskFactory(maskArray).check(dsp);
        }

        @Override
        @ForceInline
        public <E> VectorMask<E> cast(VectorSpecies<E> dsp) {
            AbstractSpecies<E> species = (AbstractSpecies<E>) dsp;
            if (length() != species.laneCount())
                throw new IllegalArgumentException("VectorMask length and species length differ");

            return VectorSupport.convert(VectorSupport.VECTOR_OP_CAST,
                this.getClass(), LANE_TYPE_ORDINAL, VLENGTH,
                species.maskType(), species.laneTypeOrdinal(), VLENGTH,
                this, species,
                (m, s) -> s.maskFactory(m.toArray()).check(s));
        }

        @Override
        @ForceInline
        /*package-private*/
        ShortMaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (ShortMaskMax) VectorSupport.indexPartiallyInUpperRange(
                ShortMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ShortMaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ShortMaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ShortMaskMax compress() {
            return (ShortMaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ShortVectorMax.class, ShortMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ShortMaskMax and(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMaskMax m = (ShortMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ShortMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ShortMaskMax or(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMaskMax m = (ShortMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ShortMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ShortMaskMax xor(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMaskMax m = (ShortMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ShortMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ShortMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ShortMaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ShortMaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ShortMaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ShortMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ShortMaskMax  TRUE_MASK = new ShortMaskMax(true);
        private static final ShortMaskMax FALSE_MASK = new ShortMaskMax(false);

    }

    // Shuffle

    static final class ShortShuffleMax extends AbstractShuffle<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortShuffleMax(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ShortShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ShortShuffleMax(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        short[] indices() {
            return (short[])getPayload();
        }

        @Override
        @ForceInline
        public ShortSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Short.MAX_VALUE);
            assert(Short.MIN_VALUE <= -VLENGTH);
        }
        static final ShortShuffleMax IOTA = new ShortShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public ShortVectorMax toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ShortVectorMax toBitsVector() {
            return (ShortVectorMax) super.toBitsVectorTemplate();
        }

        @Override
        ShortVectorMax toBitsVector0() {
            return ((ShortVectorMax) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            VectorSpecies<Integer> species = IntVector.SPECIES_MAX;
            Vector<Short> v = toBitsVector();
            v.convertShape(VectorOperators.S2I, species, 0)
                    .reinterpretAsInts()
                    .intoArray(a, offset);
            v.convertShape(VectorOperators.S2I, species, 1)
                    .reinterpretAsInts()
                    .intoArray(a, offset + species.length());
        }

        @Override
        @ForceInline
        public void intoMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            VectorSpecies<Integer> species = IntVector.SPECIES_MAX;
            Vector<Short> v = toBitsVector();
            v.convertShape(VectorOperators.S2I, species, 0)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset, bo);
            v.convertShape(VectorOperators.S2I, species, 1)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset + species.vectorByteSize(), bo);
         }

        @Override
        @ForceInline
        public final ShortMaskMax laneIsValid() {
            return (ShortMaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ShortShuffleMax rearrange(VectorShuffle<Short> shuffle) {
            ShortShuffleMax concreteShuffle = (ShortShuffleMax) shuffle;
            return (ShortShuffleMax) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ShortShuffleMax wrapIndexes() {
            ShortVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ShortShuffleMax) v.toShuffle(vspecies(), false);
        }

        private static short[] prepare(int[] indices, int offset) {
            short[] a = new short[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = indices[offset + i];
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (short)si;
            }
            return a;
        }

        private static short[] prepare(IntUnaryOperator f) {
            short[] a = new short[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = f.applyAsInt(i);
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (short)si;
            }
            return a;
        }

        private static boolean indicesInRange(short[] indices) {
            int length = indices.length;
            for (short si : indices) {
                if (si >= (short)length || si < (short)(-length)) {
                    String msg = ("index "+si+"out of range ["+length+"] in "+
                                  java.util.Arrays.toString(indices));
                    throw new AssertionError(msg);
                }
            }
            return true;
        }
    }

    // ================================================

    // Specialized low-level memory operations.

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromArray0Template(ShortMaskMax.class, a, offset, (ShortMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Short> m) {
        return super.fromArray0Template(ShortMaskMax.class, a, offset, indexMap, mapOffset, (ShortMaskMax) m);
    }

    @ForceInline
    @Override
    final
    ShortVector fromCharArray0(char[] a, int offset) {
        return super.fromCharArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromCharArray0(char[] a, int offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromCharArray0Template(ShortMaskMax.class, a, offset, (ShortMaskMax) m, offsetInRange);  // specialize
    }


    @ForceInline
    @Override
    final
    ShortVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromMemorySegment0Template(ShortMaskMax.class, ms, offset, (ShortMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(short[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(short[] a, int offset, VectorMask<Short> m) {
        super.intoArray0Template(ShortMaskMax.class, a, offset, (ShortMaskMax) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Short> m) {
        super.intoMemorySegment0Template(ShortMaskMax.class, ms, offset, (ShortMaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Short> m) {
        super.intoCharArray0Template(ShortMaskMax.class, a, offset, (ShortMaskMax) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

