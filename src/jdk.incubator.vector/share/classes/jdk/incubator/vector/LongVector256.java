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
final class LongVector256 extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_256;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<LongVector256> VCLASS = LongVector256.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    LongVector256(long[] v) {
        super(v);
    }

    // For compatibility as LongVector256::new,
    // stored into species.vectorFactory.
    LongVector256(Object v) {
        this((long[]) v);
    }

    static final LongVector256 ZERO = new LongVector256(new long[VLENGTH]);
    static final LongVector256 IOTA = new LongVector256(VSPECIES.iotaArray());

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
    public final LongVector256 broadcast(long e) {
        return (LongVector256) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    LongMask256 maskFromArray(boolean[] bits) {
        return new LongMask256(bits);
    }

    @Override
    @ForceInline
    LongShuffle256 iotaShuffle() { return LongShuffle256.IOTA; }

    @Override
    @ForceInline
    LongShuffle256 iotaShuffle(int start, int step, boolean wrap) {
        return (LongShuffle256) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    LongShuffle256 shuffleFromArray(int[] indices, int i) { return new LongShuffle256(indices, i); }

    @Override
    @ForceInline
    LongShuffle256 shuffleFromOp(IntUnaryOperator fn) { return new LongShuffle256(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    LongVector256 vectorFactory(long[] vec) {
        return new LongVector256(vec);
    }

    @ForceInline
    final @Override
    ByteVector256 asByteVectorRaw() {
        return (ByteVector256) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    LongVector256 uOp(FUnOp f) {
        return (LongVector256) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector256 uOp(VectorMask<Long> m, FUnOp f) {
        return (LongVector256)
            super.uOpTemplate((LongMask256)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    LongVector256 bOp(Vector<Long> v, FBinOp f) {
        return (LongVector256) super.bOpTemplate((LongVector256)v, f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector256 bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (LongVector256)
            super.bOpTemplate((LongVector256)v, (LongMask256)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    LongVector256 tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (LongVector256)
            super.tOpTemplate((LongVector256)v1, (LongVector256)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector256 tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (LongVector256)
            super.tOpTemplate((LongVector256)v1, (LongVector256)v2,
                              (LongMask256)m, f);  // specialize
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
    public LongVector256 lanewise(Unary op) {
        return (LongVector256) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 lanewise(Unary op, VectorMask<Long> m) {
        return (LongVector256) super.lanewiseTemplate(op, LongMask256.class, (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 lanewise(Binary op, Vector<Long> v) {
        return (LongVector256) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (LongVector256) super.lanewiseTemplate(op, LongMask256.class, v, (LongMask256) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector256
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (LongVector256) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector256
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (LongVector256) super.lanewiseShiftTemplate(op, LongMask256.class, e, (LongMask256) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    LongVector256
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (LongVector256) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector256
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (LongVector256) super.lanewiseTemplate(op, LongMask256.class, v1, v2, (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector256 addIndex(int scale) {
        return (LongVector256) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, LongMask256.class, (LongMask256) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, LongMask256.class, (LongMask256) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final LongShuffle256 toShuffle() {
        return (LongShuffle256) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final LongMask256 test(Test op) {
        return super.testTemplate(LongMask256.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask256 test(Test op, VectorMask<Long> m) {
        return super.testTemplate(LongMask256.class, op, (LongMask256) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final LongMask256 compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(LongMask256.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask256 compare(Comparison op, long s) {
        return super.compareTemplate(LongMask256.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final LongMask256 compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(LongMask256.class, op, v, (LongMask256) m);
    }


    @Override
    @ForceInline
    public LongVector256 blend(Vector<Long> v, VectorMask<Long> m) {
        return (LongVector256)
            super.blendTemplate(LongMask256.class,
                                (LongVector256) v,
                                (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 slice(int origin, Vector<Long> v) {
        return (LongVector256) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 slice(int origin) {
        return (LongVector256) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 unslice(int origin, Vector<Long> w, int part) {
        return (LongVector256) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (LongVector256)
            super.unsliceTemplate(LongMask256.class,
                                  origin, w, part,
                                  (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 unslice(int origin) {
        return (LongVector256) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 rearrange(VectorShuffle<Long> s) {
        return (LongVector256)
            super.rearrangeTemplate(LongShuffle256.class,
                                    (LongShuffle256) s);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (LongVector256)
            super.rearrangeTemplate(LongShuffle256.class,
                                    LongMask256.class,
                                    (LongShuffle256) shuffle,
                                    (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (LongVector256)
            super.rearrangeTemplate(LongShuffle256.class,
                                    (LongShuffle256) s,
                                    (LongVector256) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 compress(VectorMask<Long> m) {
        return (LongVector256)
            super.compressTemplate(LongMask256.class,
                                   (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 expand(VectorMask<Long> m) {
        return (LongVector256)
            super.expandTemplate(LongMask256.class,
                                   (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 selectFrom(Vector<Long> v) {
        return (LongVector256)
            super.selectFromTemplate((LongVector256) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (LongVector256)
            super.selectFromTemplate((LongVector256) v,
                                     LongMask256.class, (LongMask256) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector256 selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (LongVector256)
            super.selectFromTemplate((LongVector256) v1, (LongVector256) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            case 2: return laneHelper(2);
            case 3: return laneHelper(3);
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
    public LongVector256 withLane(int i, long e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public LongVector256 withLaneHelper(int i, long e) {
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

    static final class LongMask256 extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMask256(boolean[] bits) {
            this(bits, 0);
        }

        LongMask256(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        LongMask256(boolean val) {
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
        LongMask256 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMask256(res);
        }

        @Override
        LongMask256 bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMask256)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMask256(res);
        }

        @ForceInline
        @Override
        public final
        LongVector256 toVector() {
            return (LongVector256) super.toVectorTemplate();  // specialize
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
        LongMask256 indexPartiallyInUpperRange(long offset, long limit) {
            return (LongMask256) VectorSupport.indexPartiallyInUpperRange(
                LongMask256.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (LongMask256) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMask256 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public LongMask256 compress() {
            return (LongMask256)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                LongVector256.class, LongMask256.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public LongMask256 and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask256 m = (LongMask256)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, LongMask256.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMask256 or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask256 m = (LongMask256)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, LongMask256.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public LongMask256 xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask256 m = (LongMask256)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, LongMask256.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(LongMask256.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((LongMask256)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((LongMask256)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static LongMask256 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(LongMask256.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final LongMask256  TRUE_MASK = new LongMask256(true);
        private static final LongMask256 FALSE_MASK = new LongMask256(false);

    }

    // Shuffle

    static final class LongShuffle256 extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongShuffle256(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        LongShuffle256(int[] indices, int i) {
            this(prepare(indices, i));
        }

        LongShuffle256(IntUnaryOperator fn) {
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
        static final LongShuffle256 IOTA = new LongShuffle256(IDENTITY);

        @Override
        @ForceInline
        public LongVector256 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        LongVector256 toBitsVector() {
            return (LongVector256) super.toBitsVectorTemplate();
        }

        @Override
        LongVector256 toBitsVector0() {
            return ((LongVector256) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final LongMask256 laneIsValid() {
            return (LongMask256) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final LongShuffle256 rearrange(VectorShuffle<Long> shuffle) {
            LongShuffle256 concreteShuffle = (LongShuffle256) shuffle;
            return (LongShuffle256) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final LongShuffle256 wrapIndexes() {
            LongVector256 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongVector256) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongVector256) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (LongShuffle256) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(LongMask256.class, a, offset, (LongMask256) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(LongMask256.class, a, offset, indexMap, mapOffset, (LongMask256) m);
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
        return super.fromMemorySegment0Template(LongMask256.class, ms, offset, (LongMask256) m, offsetInRange);  // specialize
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
        super.intoArray0Template(LongMask256.class, a, offset, (LongMask256) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(LongMask256.class, a, offset, indexMap, mapOffset, (LongMask256) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(LongMask256.class, ms, offset, (LongMask256) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

