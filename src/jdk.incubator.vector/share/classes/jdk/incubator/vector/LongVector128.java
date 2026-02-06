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
final class LongVector128 extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_128;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<LongVector128> VCLASS = LongVector128.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    LongVector128(long[] v) {
        super(v);
    }

    // For compatibility as LongVector128::new,
    // stored into species.vectorFactory.
    LongVector128(Object v) {
        this((long[]) v);
    }

    static final LongVector128 ZERO = new LongVector128(new long[VLENGTH]);
    static final LongVector128 IOTA = new LongVector128(VSPECIES.iotaArray());

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
    public final LongVector128 broadcast(long e) {
        return (LongVector128) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    LongMask128 maskFromArray(boolean[] bits) {
        return new LongMask128(bits);
    }

    @Override
    @ForceInline
    LongShuffle128 iotaShuffle() { return LongShuffle128.IOTA; }

    @Override
    @ForceInline
    LongShuffle128 iotaShuffle(int start, int step, boolean wrap) {
        return (LongShuffle128) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    LongShuffle128 shuffleFromArray(int[] indices, int i) { return new LongShuffle128(indices, i); }

    @Override
    @ForceInline
    LongShuffle128 shuffleFromOp(IntUnaryOperator fn) { return new LongShuffle128(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    LongVector128 vectorFactory(long[] vec) {
        return new LongVector128(vec);
    }

    @ForceInline
    final @Override
    ByteVector128 asByteVectorRaw() {
        return (ByteVector128) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    LongVector128 uOp(FUnOp f) {
        return (LongVector128) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector128 uOp(VectorMask<Long> m, FUnOp f) {
        return (LongVector128)
            super.uOpTemplate((LongMask128)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    LongVector128 bOp(Vector<Long> v, FBinOp f) {
        return (LongVector128) super.bOpTemplate((LongVector128)v, f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector128 bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (LongVector128)
            super.bOpTemplate((LongVector128)v, (LongMask128)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    LongVector128 tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (LongVector128)
            super.tOpTemplate((LongVector128)v1, (LongVector128)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector128 tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (LongVector128)
            super.tOpTemplate((LongVector128)v1, (LongVector128)v2,
                              (LongMask128)m, f);  // specialize
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
    public LongVector128 lanewise(Unary op) {
        return (LongVector128) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 lanewise(Unary op, VectorMask<Long> m) {
        return (LongVector128) super.lanewiseTemplate(op, LongMask128.class, (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 lanewise(Binary op, Vector<Long> v) {
        return (LongVector128) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (LongVector128) super.lanewiseTemplate(op, LongMask128.class, v, (LongMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector128
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (LongVector128) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector128
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (LongVector128) super.lanewiseShiftTemplate(op, LongMask128.class, e, (LongMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    LongVector128
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (LongVector128) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector128
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (LongVector128) super.lanewiseTemplate(op, LongMask128.class, v1, v2, (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector128 addIndex(int scale) {
        return (LongVector128) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, LongMask128.class, (LongMask128) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, LongMask128.class, (LongMask128) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final LongShuffle128 toShuffle() {
        return (LongShuffle128) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final LongMask128 test(Test op) {
        return super.testTemplate(LongMask128.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask128 test(Test op, VectorMask<Long> m) {
        return super.testTemplate(LongMask128.class, op, (LongMask128) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final LongMask128 compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(LongMask128.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask128 compare(Comparison op, long s) {
        return super.compareTemplate(LongMask128.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final LongMask128 compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(LongMask128.class, op, v, (LongMask128) m);
    }


    @Override
    @ForceInline
    public LongVector128 blend(Vector<Long> v, VectorMask<Long> m) {
        return (LongVector128)
            super.blendTemplate(LongMask128.class,
                                (LongVector128) v,
                                (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 slice(int origin, Vector<Long> v) {
        return (LongVector128) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 slice(int origin) {
        return (LongVector128) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 unslice(int origin, Vector<Long> w, int part) {
        return (LongVector128) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (LongVector128)
            super.unsliceTemplate(LongMask128.class,
                                  origin, w, part,
                                  (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 unslice(int origin) {
        return (LongVector128) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 rearrange(VectorShuffle<Long> s) {
        return (LongVector128)
            super.rearrangeTemplate(LongShuffle128.class,
                                    (LongShuffle128) s);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (LongVector128)
            super.rearrangeTemplate(LongShuffle128.class,
                                    LongMask128.class,
                                    (LongShuffle128) shuffle,
                                    (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (LongVector128)
            super.rearrangeTemplate(LongShuffle128.class,
                                    (LongShuffle128) s,
                                    (LongVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 compress(VectorMask<Long> m) {
        return (LongVector128)
            super.compressTemplate(LongMask128.class,
                                   (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 expand(VectorMask<Long> m) {
        return (LongVector128)
            super.expandTemplate(LongMask128.class,
                                   (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 selectFrom(Vector<Long> v) {
        return (LongVector128)
            super.selectFromTemplate((LongVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (LongVector128)
            super.selectFromTemplate((LongVector128) v,
                                     LongMask128.class, (LongMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector128 selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (LongVector128)
            super.selectFromTemplate((LongVector128) v1, (LongVector128) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
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
    public LongVector128 withLane(int i, long e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public LongVector128 withLaneHelper(int i, long e) {
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

    static final class LongMask128 extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMask128(boolean[] bits) {
            this(bits, 0);
        }

        LongMask128(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        LongMask128(boolean val) {
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
        LongMask128 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMask128(res);
        }

        @Override
        LongMask128 bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMask128)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMask128(res);
        }

        @ForceInline
        @Override
        public final
        LongVector128 toVector() {
            return (LongVector128) super.toVectorTemplate();  // specialize
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
        LongMask128 indexPartiallyInUpperRange(long offset, long limit) {
            return (LongMask128) VectorSupport.indexPartiallyInUpperRange(
                LongMask128.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (LongMask128) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMask128 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public LongMask128 compress() {
            return (LongMask128)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                LongVector128.class, LongMask128.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public LongMask128 and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask128 m = (LongMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, LongMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMask128 or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask128 m = (LongMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, LongMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public LongMask128 xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask128 m = (LongMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, LongMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(LongMask128.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((LongMask128)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((LongMask128)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static LongMask128 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(LongMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final LongMask128  TRUE_MASK = new LongMask128(true);
        private static final LongMask128 FALSE_MASK = new LongMask128(false);

    }

    // Shuffle

    static final class LongShuffle128 extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongShuffle128(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        LongShuffle128(int[] indices, int i) {
            this(prepare(indices, i));
        }

        LongShuffle128(IntUnaryOperator fn) {
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
        static final LongShuffle128 IOTA = new LongShuffle128(IDENTITY);

        @Override
        @ForceInline
        public LongVector128 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        LongVector128 toBitsVector() {
            return (LongVector128) super.toBitsVectorTemplate();
        }

        @Override
        LongVector128 toBitsVector0() {
            return ((LongVector128) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final LongMask128 laneIsValid() {
            return (LongMask128) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final LongShuffle128 rearrange(VectorShuffle<Long> shuffle) {
            LongShuffle128 concreteShuffle = (LongShuffle128) shuffle;
            return (LongShuffle128) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final LongShuffle128 wrapIndexes() {
            LongVector128 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongVector128) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongVector128) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (LongShuffle128) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(LongMask128.class, a, offset, (LongMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(LongMask128.class, a, offset, indexMap, mapOffset, (LongMask128) m);
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
        return super.fromMemorySegment0Template(LongMask128.class, ms, offset, (LongMask128) m, offsetInRange);  // specialize
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
        super.intoArray0Template(LongMask128.class, a, offset, (LongMask128) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(LongMask128.class, a, offset, indexMap, mapOffset, (LongMask128) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(LongMask128.class, ms, offset, (LongMask128) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

