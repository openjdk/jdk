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
final class LongVectorMax extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<LongVectorMax> VCLASS = LongVectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    LongVectorMax(long[] v) {
        super(v);
    }

    // For compatibility as LongVectorMax::new,
    // stored into species.vectorFactory.
    LongVectorMax(Object v) {
        this((long[]) v);
    }

    static final LongVectorMax ZERO = new LongVectorMax(new long[VLENGTH]);
    static final LongVectorMax IOTA = new LongVectorMax(VSPECIES.iotaArray());

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
    public LongSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Long> elementType() { return long.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Long.SIZE; }

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
    long[] vec() {
        return (long[])getPayload();
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
    public final LongVectorMax broadcast(long e) {
        return (LongVectorMax) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    LongMaskMax maskFromArray(boolean[] bits) {
        return new LongMaskMax(bits);
    }

    @Override
    @ForceInline
    LongShuffleMax iotaShuffle() { return LongShuffleMax.IOTA; }

    @Override
    @ForceInline
    LongShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (LongShuffleMax) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    LongShuffleMax shuffleFromArray(int[] indices, int i) { return new LongShuffleMax(indices, i); }

    @Override
    @ForceInline
    LongShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new LongShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    LongVectorMax vectorFactory(long[] vec) {
        return new LongVectorMax(vec);
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
    LongVectorMax uOp(FUnOp f) {
        return (LongVectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    LongVectorMax uOp(VectorMask<Long> m, FUnOp f) {
        return (LongVectorMax)
            super.uOpTemplate((LongMaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    LongVectorMax bOp(Vector<Long> v, FBinOp f) {
        return (LongVectorMax) super.bOpTemplate((LongVectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    LongVectorMax bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (LongVectorMax)
            super.bOpTemplate((LongVectorMax)v, (LongMaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    LongVectorMax tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (LongVectorMax)
            super.tOpTemplate((LongVectorMax)v1, (LongVectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    LongVectorMax tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (LongVectorMax)
            super.tOpTemplate((LongVectorMax)v1, (LongVectorMax)v2,
                              (LongMaskMax)m, f);  // specialize
    }

    @ForceInline
    final @Override
    long rOp(long v, VectorMask<Long> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Long,F> conv,
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
    public LongVectorMax lanewise(Unary op) {
        return (LongVectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax lanewise(Unary op, VectorMask<Long> m) {
        return (LongVectorMax) super.lanewiseTemplate(op, LongMaskMax.class, (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax lanewise(Binary op, Vector<Long> v) {
        return (LongVectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (LongVectorMax) super.lanewiseTemplate(op, LongMaskMax.class, v, (LongMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVectorMax
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (LongVectorMax) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVectorMax
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (LongVectorMax) super.lanewiseShiftTemplate(op, LongMaskMax.class, e, (LongMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    LongVectorMax
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (LongVectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVectorMax
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (LongVectorMax) super.lanewiseTemplate(op, LongMaskMax.class, v1, v2, (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVectorMax addIndex(int scale) {
        return (LongVectorMax) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final long reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Long> m) {
        return super.reduceLanesTemplate(op, LongMaskMax.class, (LongMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Long> m) {
        return (long) super.reduceLanesTemplate(op, LongMaskMax.class, (LongMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final LongShuffleMax toShuffle() {
        return (LongShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final LongMaskMax test(Test op) {
        return super.testTemplate(LongMaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final LongMaskMax test(Test op, VectorMask<Long> m) {
        return super.testTemplate(LongMaskMax.class, op, (LongMaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final LongMaskMax compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(LongMaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final LongMaskMax compare(Comparison op, long s) {
        return super.compareTemplate(LongMaskMax.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final LongMaskMax compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(LongMaskMax.class, op, v, (LongMaskMax) m);
    }


    @Override
    @ForceInline
    public LongVectorMax blend(Vector<Long> v, VectorMask<Long> m) {
        return (LongVectorMax)
            super.blendTemplate(LongMaskMax.class,
                                (LongVectorMax) v,
                                (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax slice(int origin, Vector<Long> v) {
        return (LongVectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax slice(int origin) {
        return (LongVectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax unslice(int origin, Vector<Long> w, int part) {
        return (LongVectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (LongVectorMax)
            super.unsliceTemplate(LongMaskMax.class,
                                  origin, w, part,
                                  (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax unslice(int origin) {
        return (LongVectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax rearrange(VectorShuffle<Long> s) {
        return (LongVectorMax)
            super.rearrangeTemplate(LongShuffleMax.class,
                                    (LongShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (LongVectorMax)
            super.rearrangeTemplate(LongShuffleMax.class,
                                    LongMaskMax.class,
                                    (LongShuffleMax) shuffle,
                                    (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (LongVectorMax)
            super.rearrangeTemplate(LongShuffleMax.class,
                                    (LongShuffleMax) s,
                                    (LongVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax compress(VectorMask<Long> m) {
        return (LongVectorMax)
            super.compressTemplate(LongMaskMax.class,
                                   (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax expand(VectorMask<Long> m) {
        return (LongVectorMax)
            super.expandTemplate(LongMaskMax.class,
                                   (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax selectFrom(Vector<Long> v) {
        return (LongVectorMax)
            super.selectFromTemplate((LongVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (LongVectorMax)
            super.selectFromTemplate((LongVectorMax) v,
                                     LongMaskMax.class, (LongMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVectorMax selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (LongVectorMax)
            super.selectFromTemplate((LongVectorMax) v1, (LongVectorMax) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return laneHelper(i);
    }

    @ForceInline
    public long laneHelper(int i) {
        return (long) VectorSupport.extract(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    long[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public LongVectorMax withLane(int i, long e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public LongVectorMax withLaneHelper(int i, long e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    long[] res = v.vec().clone();
                                    res[ix] = (long)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class LongMaskMax extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMaskMax(boolean[] bits) {
            this(bits, 0);
        }

        LongMaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        LongMaskMax(boolean val) {
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
        public LongSpecies vspecies() {
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
        LongMaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMaskMax(res);
        }

        @Override
        LongMaskMax bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMaskMax(res);
        }

        @ForceInline
        @Override
        public final
        LongVectorMax toVector() {
            return (LongVectorMax) super.toVectorTemplate();  // specialize
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
        LongMaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (LongMaskMax) VectorSupport.indexPartiallyInUpperRange(
                LongMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (LongMaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public LongMaskMax compress() {
            return (LongMaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                LongVectorMax.class, LongMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public LongMaskMax and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaskMax m = (LongMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, LongMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMaskMax or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaskMax m = (LongMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, LongMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public LongMaskMax xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaskMax m = (LongMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, LongMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(LongMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((LongMaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((LongMaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static LongMaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(LongMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final LongMaskMax  TRUE_MASK = new LongMaskMax(true);
        private static final LongMaskMax FALSE_MASK = new LongMaskMax(false);

    }

    // Shuffle

    static final class LongShuffleMax extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongShuffleMax(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        LongShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        LongShuffleMax(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        long[] indices() {
            return (long[])getPayload();
        }

        @Override
        @ForceInline
        public LongSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Long.MAX_VALUE);
            assert(Long.MIN_VALUE <= -VLENGTH);
        }
        static final LongShuffleMax IOTA = new LongShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public LongVectorMax toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        LongVectorMax toBitsVector() {
            return (LongVectorMax) super.toBitsVectorTemplate();
        }

        @Override
        LongVectorMax toBitsVector0() {
            return ((LongVectorMax) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            switch (length()) {
                case 1 -> a[offset] = laneSource(0);
                case 2 -> toBitsVector()
                        .convertShape(VectorOperators.L2I, IntVector.SPECIES_64, 0)
                        .reinterpretAsInts()
                        .intoArray(a, offset);
                case 4 -> toBitsVector()
                        .convertShape(VectorOperators.L2I, IntVector.SPECIES_128, 0)
                        .reinterpretAsInts()
                        .intoArray(a, offset);
                case 8 -> toBitsVector()
                        .convertShape(VectorOperators.L2I, IntVector.SPECIES_256, 0)
                        .reinterpretAsInts()
                        .intoArray(a, offset);
                case 16 -> toBitsVector()
                        .convertShape(VectorOperators.L2I, IntVector.SPECIES_512, 0)
                        .reinterpretAsInts()
                        .intoArray(a, offset);
                default -> {
                    VectorIntrinsics.checkFromIndexSize(offset, length(), a.length);
                    for (int i = 0; i < length(); i++) {
                        a[offset + i] = laneSource(i);
                    }
                }
           }

        }

        @Override
        @ForceInline
        public void intoMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            switch (length()) {
                case 1 -> ms.set(ValueLayout.OfInt.JAVA_INT_UNALIGNED, offset, laneSource(0));
                case 2 -> toBitsVector()
                       .convertShape(VectorOperators.L2I, IntVector.SPECIES_64, 0)
                       .reinterpretAsInts()
                       .intoMemorySegment(ms, offset, bo);
                case 4 -> toBitsVector()
                       .convertShape(VectorOperators.L2I, IntVector.SPECIES_128, 0)
                       .reinterpretAsInts()
                       .intoMemorySegment(ms, offset, bo);
                case 8 -> toBitsVector()
                       .convertShape(VectorOperators.L2I, IntVector.SPECIES_256, 0)
                       .reinterpretAsInts()
                       .intoMemorySegment(ms, offset, bo);
                case 16 -> toBitsVector()
                        .convertShape(VectorOperators.L2I, IntVector.SPECIES_512, 0)
                        .reinterpretAsInts()
                        .intoMemorySegment(ms, offset, bo);
                default -> {
                    VectorIntrinsics.checkFromIndexSize(offset, length(), ms.byteSize() / 4);
                    for (int i = 0; i < length(); i++) {
                        ms.setAtIndex(ValueLayout.JAVA_INT_UNALIGNED, offset + (i << 2), laneSource(i));
                    }
                }
            }
         }

        @Override
        @ForceInline
        public final LongMaskMax laneIsValid() {
            return (LongMaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final LongShuffleMax rearrange(VectorShuffle<Long> shuffle) {
            LongShuffleMax concreteShuffle = (LongShuffleMax) shuffle;
            return (LongShuffleMax) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final LongShuffleMax wrapIndexes() {
            LongVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (LongShuffleMax) v.toShuffle(vspecies(), false);
        }

        private static long[] prepare(int[] indices, int offset) {
            long[] a = new long[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = indices[offset + i];
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (long)si;
            }
            return a;
        }

        private static long[] prepare(IntUnaryOperator f) {
            long[] a = new long[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = f.applyAsInt(i);
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (long)si;
            }
            return a;
        }

        private static boolean indicesInRange(long[] indices) {
            int length = indices.length;
            for (long si : indices) {
                if (si >= (long)length || si < (long)(-length)) {
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
    LongVector fromArray0(long[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, VectorMask<Long> m, int offsetInRange) {
        return super.fromArray0Template(LongMaskMax.class, a, offset, (LongMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(LongMaskMax.class, a, offset, indexMap, mapOffset, (LongMaskMax) m);
    }



    @ForceInline
    @Override
    final
    LongVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m, int offsetInRange) {
        return super.fromMemorySegment0Template(LongMaskMax.class, ms, offset, (LongMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, VectorMask<Long> m) {
        super.intoArray0Template(LongMaskMax.class, a, offset, (LongMaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(LongMaskMax.class, a, offset, indexMap, mapOffset, (LongMaskMax) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(LongMaskMax.class, ms, offset, (LongMaskMax) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

