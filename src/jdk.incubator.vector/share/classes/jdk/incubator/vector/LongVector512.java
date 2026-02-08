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
final class LongVector512 extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<LongVector512> VCLASS = LongVector512.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    LongVector512(long[] v) {
        super(v);
    }

    // For compatibility as LongVector512::new,
    // stored into species.vectorFactory.
    LongVector512(Object v) {
        this((long[]) v);
    }

    static final LongVector512 ZERO = new LongVector512(new long[VLENGTH]);
    static final LongVector512 IOTA = new LongVector512(VSPECIES.iotaArray());

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
    public final LongVector512 broadcast(long e) {
        return (LongVector512) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    LongMask512 maskFromArray(boolean[] bits) {
        return new LongMask512(bits);
    }

    @Override
    @ForceInline
    LongShuffle512 iotaShuffle() { return LongShuffle512.IOTA; }

    @Override
    @ForceInline
    LongShuffle512 iotaShuffle(int start, int step, boolean wrap) {
        return (LongShuffle512) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    LongShuffle512 shuffleFromArray(int[] indices, int i) { return new LongShuffle512(indices, i); }

    @Override
    @ForceInline
    LongShuffle512 shuffleFromOp(IntUnaryOperator fn) { return new LongShuffle512(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    LongVector512 vectorFactory(long[] vec) {
        return new LongVector512(vec);
    }

    @ForceInline
    final @Override
    ByteVector512 asByteVectorRaw() {
        return (ByteVector512) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    LongVector512 uOp(FUnOp f) {
        return (LongVector512) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector512 uOp(VectorMask<Long> m, FUnOp f) {
        return (LongVector512)
            super.uOpTemplate((LongMask512)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    LongVector512 bOp(Vector<Long> v, FBinOp f) {
        return (LongVector512) super.bOpTemplate((LongVector512)v, f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector512 bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (LongVector512)
            super.bOpTemplate((LongVector512)v, (LongMask512)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    LongVector512 tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (LongVector512)
            super.tOpTemplate((LongVector512)v1, (LongVector512)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    LongVector512 tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (LongVector512)
            super.tOpTemplate((LongVector512)v1, (LongVector512)v2,
                              (LongMask512)m, f);  // specialize
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
    public LongVector512 lanewise(Unary op) {
        return (LongVector512) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 lanewise(Unary op, VectorMask<Long> m) {
        return (LongVector512) super.lanewiseTemplate(op, LongMask512.class, (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 lanewise(Binary op, Vector<Long> v) {
        return (LongVector512) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (LongVector512) super.lanewiseTemplate(op, LongMask512.class, v, (LongMask512) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector512
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (LongVector512) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongVector512
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (LongVector512) super.lanewiseShiftTemplate(op, LongMask512.class, e, (LongMask512) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    LongVector512
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (LongVector512) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector512
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (LongVector512) super.lanewiseTemplate(op, LongMask512.class, v1, v2, (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongVector512 addIndex(int scale) {
        return (LongVector512) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, LongMask512.class, (LongMask512) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, LongMask512.class, (LongMask512) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final LongShuffle512 toShuffle() {
        return (LongShuffle512) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final LongMask512 test(Test op) {
        return super.testTemplate(LongMask512.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask512 test(Test op, VectorMask<Long> m) {
        return super.testTemplate(LongMask512.class, op, (LongMask512) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final LongMask512 compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(LongMask512.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final LongMask512 compare(Comparison op, long s) {
        return super.compareTemplate(LongMask512.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final LongMask512 compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(LongMask512.class, op, v, (LongMask512) m);
    }


    @Override
    @ForceInline
    public LongVector512 blend(Vector<Long> v, VectorMask<Long> m) {
        return (LongVector512)
            super.blendTemplate(LongMask512.class,
                                (LongVector512) v,
                                (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 slice(int origin, Vector<Long> v) {
        return (LongVector512) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 slice(int origin) {
        return (LongVector512) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 unslice(int origin, Vector<Long> w, int part) {
        return (LongVector512) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (LongVector512)
            super.unsliceTemplate(LongMask512.class,
                                  origin, w, part,
                                  (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 unslice(int origin) {
        return (LongVector512) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 rearrange(VectorShuffle<Long> s) {
        return (LongVector512)
            super.rearrangeTemplate(LongShuffle512.class,
                                    (LongShuffle512) s);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (LongVector512)
            super.rearrangeTemplate(LongShuffle512.class,
                                    LongMask512.class,
                                    (LongShuffle512) shuffle,
                                    (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (LongVector512)
            super.rearrangeTemplate(LongShuffle512.class,
                                    (LongShuffle512) s,
                                    (LongVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 compress(VectorMask<Long> m) {
        return (LongVector512)
            super.compressTemplate(LongMask512.class,
                                   (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 expand(VectorMask<Long> m) {
        return (LongVector512)
            super.expandTemplate(LongMask512.class,
                                   (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 selectFrom(Vector<Long> v) {
        return (LongVector512)
            super.selectFromTemplate((LongVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (LongVector512)
            super.selectFromTemplate((LongVector512) v,
                                     LongMask512.class, (LongMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public LongVector512 selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (LongVector512)
            super.selectFromTemplate((LongVector512) v1, (LongVector512) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            case 2: return laneHelper(2);
            case 3: return laneHelper(3);
            case 4: return laneHelper(4);
            case 5: return laneHelper(5);
            case 6: return laneHelper(6);
            case 7: return laneHelper(7);
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
    public LongVector512 withLane(int i, long e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            case 4: return withLaneHelper(4, e);
            case 5: return withLaneHelper(5, e);
            case 6: return withLaneHelper(6, e);
            case 7: return withLaneHelper(7, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public LongVector512 withLaneHelper(int i, long e) {
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

    static final class LongMask512 extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMask512(boolean[] bits) {
            this(bits, 0);
        }

        LongMask512(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        LongMask512(boolean val) {
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
        LongMask512 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMask512(res);
        }

        @Override
        LongMask512 bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMask512)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMask512(res);
        }

        @ForceInline
        @Override
        public final
        LongVector512 toVector() {
            return (LongVector512) super.toVectorTemplate();  // specialize
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
        LongMask512 indexPartiallyInUpperRange(long offset, long limit) {
            return (LongMask512) VectorSupport.indexPartiallyInUpperRange(
                LongMask512.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (LongMask512) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMask512 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public LongMask512 compress() {
            return (LongMask512)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                LongVector512.class, LongMask512.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public LongMask512 and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask512 m = (LongMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, LongMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMask512 or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask512 m = (LongMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, LongMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public LongMask512 xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMask512 m = (LongMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, LongMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(LongMask512.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((LongMask512)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((LongMask512)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static LongMask512 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(LongMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final LongMask512  TRUE_MASK = new LongMask512(true);
        private static final LongMask512 FALSE_MASK = new LongMask512(false);

    }

    // Shuffle

    static final class LongShuffle512 extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongShuffle512(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        LongShuffle512(int[] indices, int i) {
            this(prepare(indices, i));
        }

        LongShuffle512(IntUnaryOperator fn) {
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
        static final LongShuffle512 IOTA = new LongShuffle512(IDENTITY);

        @Override
        @ForceInline
        public LongVector512 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        LongVector512 toBitsVector() {
            return (LongVector512) super.toBitsVectorTemplate();
        }

        @Override
        LongVector512 toBitsVector0() {
            return ((LongVector512) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final LongMask512 laneIsValid() {
            return (LongMask512) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final LongShuffle512 rearrange(VectorShuffle<Long> shuffle) {
            LongShuffle512 concreteShuffle = (LongShuffle512) shuffle;
            return (LongShuffle512) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final LongShuffle512 wrapIndexes() {
            LongVector512 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongVector512) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongVector512) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (LongShuffle512) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(LongMask512.class, a, offset, (LongMask512) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(LongMask512.class, a, offset, indexMap, mapOffset, (LongMask512) m);
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
        return super.fromMemorySegment0Template(LongMask512.class, ms, offset, (LongMask512) m, offsetInRange);  // specialize
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
        super.intoArray0Template(LongMask512.class, a, offset, (LongMask512) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(LongMask512.class, a, offset, indexMap, mapOffset, (LongMask512) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(LongMask512.class, ms, offset, (LongMask512) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

