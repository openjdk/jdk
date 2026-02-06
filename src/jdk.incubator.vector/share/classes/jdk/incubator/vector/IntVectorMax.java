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
final class IntVectorMax extends IntVector {
    static final IntSpecies VSPECIES =
        (IntSpecies) IntVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<IntVectorMax> VCLASS = IntVectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Integer> ETYPE = int.class; // used by the JVM

    IntVectorMax(int[] v) {
        super(v);
    }

    // For compatibility as IntVectorMax::new,
    // stored into species.vectorFactory.
    IntVectorMax(Object v) {
        this((int[]) v);
    }

    static final IntVectorMax ZERO = new IntVectorMax(new int[VLENGTH]);
    static final IntVectorMax IOTA = new IntVectorMax(VSPECIES.iotaArray());

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
    public IntSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Integer> elementType() { return int.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Integer.SIZE; }

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
    int[] vec() {
        return (int[])getPayload();
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
    public final IntVectorMax broadcast(int e) {
        return (IntVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final IntVectorMax broadcast(long e) {
        return (IntVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    IntMaskMax maskFromArray(boolean[] bits) {
        return new IntMaskMax(bits);
    }

    @Override
    @ForceInline
    IntShuffleMax iotaShuffle() { return IntShuffleMax.IOTA; }

    @Override
    @ForceInline
    IntShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (IntShuffleMax) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    IntShuffleMax shuffleFromArray(int[] indices, int i) { return new IntShuffleMax(indices, i); }

    @Override
    @ForceInline
    IntShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new IntShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    IntVectorMax vectorFactory(int[] vec) {
        return new IntVectorMax(vec);
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
    IntVectorMax uOp(FUnOp f) {
        return (IntVectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    IntVectorMax uOp(VectorMask<Integer> m, FUnOp f) {
        return (IntVectorMax)
            super.uOpTemplate((IntMaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    IntVectorMax bOp(Vector<Integer> v, FBinOp f) {
        return (IntVectorMax) super.bOpTemplate((IntVectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    IntVectorMax bOp(Vector<Integer> v,
                     VectorMask<Integer> m, FBinOp f) {
        return (IntVectorMax)
            super.bOpTemplate((IntVectorMax)v, (IntMaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    IntVectorMax tOp(Vector<Integer> v1, Vector<Integer> v2, FTriOp f) {
        return (IntVectorMax)
            super.tOpTemplate((IntVectorMax)v1, (IntVectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    IntVectorMax tOp(Vector<Integer> v1, Vector<Integer> v2,
                     VectorMask<Integer> m, FTriOp f) {
        return (IntVectorMax)
            super.tOpTemplate((IntVectorMax)v1, (IntVectorMax)v2,
                              (IntMaskMax)m, f);  // specialize
    }

    @ForceInline
    final @Override
    int rOp(int v, VectorMask<Integer> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Integer,F> conv,
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
    public IntVectorMax lanewise(Unary op) {
        return (IntVectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax lanewise(Unary op, VectorMask<Integer> m) {
        return (IntVectorMax) super.lanewiseTemplate(op, IntMaskMax.class, (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax lanewise(Binary op, Vector<Integer> v) {
        return (IntVectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax lanewise(Binary op, Vector<Integer> v, VectorMask<Integer> m) {
        return (IntVectorMax) super.lanewiseTemplate(op, IntMaskMax.class, v, (IntMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline IntVectorMax
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (IntVectorMax) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline IntVectorMax
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Integer> m) {
        return (IntVectorMax) super.lanewiseShiftTemplate(op, IntMaskMax.class, e, (IntMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    IntVectorMax
    lanewise(Ternary op, Vector<Integer> v1, Vector<Integer> v2) {
        return (IntVectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    IntVectorMax
    lanewise(Ternary op, Vector<Integer> v1, Vector<Integer> v2, VectorMask<Integer> m) {
        return (IntVectorMax) super.lanewiseTemplate(op, IntMaskMax.class, v1, v2, (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    IntVectorMax addIndex(int scale) {
        return (IntVectorMax) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final int reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final int reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Integer> m) {
        return super.reduceLanesTemplate(op, IntMaskMax.class, (IntMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Integer> m) {
        return (long) super.reduceLanesTemplate(op, IntMaskMax.class, (IntMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final IntShuffleMax toShuffle() {
        return (IntShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final IntMaskMax test(Test op) {
        return super.testTemplate(IntMaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final IntMaskMax test(Test op, VectorMask<Integer> m) {
        return super.testTemplate(IntMaskMax.class, op, (IntMaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final IntMaskMax compare(Comparison op, Vector<Integer> v) {
        return super.compareTemplate(IntMaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final IntMaskMax compare(Comparison op, int s) {
        return super.compareTemplate(IntMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final IntMaskMax compare(Comparison op, long s) {
        return super.compareTemplate(IntMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final IntMaskMax compare(Comparison op, Vector<Integer> v, VectorMask<Integer> m) {
        return super.compareTemplate(IntMaskMax.class, op, v, (IntMaskMax) m);
    }


    @Override
    @ForceInline
    public IntVectorMax blend(Vector<Integer> v, VectorMask<Integer> m) {
        return (IntVectorMax)
            super.blendTemplate(IntMaskMax.class,
                                (IntVectorMax) v,
                                (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax slice(int origin, Vector<Integer> v) {
        return (IntVectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax slice(int origin) {
        return (IntVectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax unslice(int origin, Vector<Integer> w, int part) {
        return (IntVectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax unslice(int origin, Vector<Integer> w, int part, VectorMask<Integer> m) {
        return (IntVectorMax)
            super.unsliceTemplate(IntMaskMax.class,
                                  origin, w, part,
                                  (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax unslice(int origin) {
        return (IntVectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax rearrange(VectorShuffle<Integer> s) {
        return (IntVectorMax)
            super.rearrangeTemplate(IntShuffleMax.class,
                                    (IntShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax rearrange(VectorShuffle<Integer> shuffle,
                                  VectorMask<Integer> m) {
        return (IntVectorMax)
            super.rearrangeTemplate(IntShuffleMax.class,
                                    IntMaskMax.class,
                                    (IntShuffleMax) shuffle,
                                    (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax rearrange(VectorShuffle<Integer> s,
                                  Vector<Integer> v) {
        return (IntVectorMax)
            super.rearrangeTemplate(IntShuffleMax.class,
                                    (IntShuffleMax) s,
                                    (IntVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax compress(VectorMask<Integer> m) {
        return (IntVectorMax)
            super.compressTemplate(IntMaskMax.class,
                                   (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax expand(VectorMask<Integer> m) {
        return (IntVectorMax)
            super.expandTemplate(IntMaskMax.class,
                                   (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax selectFrom(Vector<Integer> v) {
        return (IntVectorMax)
            super.selectFromTemplate((IntVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax selectFrom(Vector<Integer> v,
                                   VectorMask<Integer> m) {
        return (IntVectorMax)
            super.selectFromTemplate((IntVectorMax) v,
                                     IntMaskMax.class, (IntMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVectorMax selectFrom(Vector<Integer> v1,
                                   Vector<Integer> v2) {
        return (IntVectorMax)
            super.selectFromTemplate((IntVectorMax) v1, (IntVectorMax) v2);  // specialize
    }

    @ForceInline
    @Override
    public int lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return laneHelper(i);
    }

    @ForceInline
    public int laneHelper(int i) {
        return (int) VectorSupport.extract(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    int[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public IntVectorMax withLane(int i, int e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public IntVectorMax withLaneHelper(int i, int e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    int[] res = v.vec().clone();
                                    res[ix] = (int)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class IntMaskMax extends AbstractMask<Integer> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        IntMaskMax(boolean[] bits) {
            this(bits, 0);
        }

        IntMaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        IntMaskMax(boolean val) {
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
        public IntSpecies vspecies() {
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
        IntMaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new IntMaskMax(res);
        }

        @Override
        IntMaskMax bOp(VectorMask<Integer> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((IntMaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new IntMaskMax(res);
        }

        @ForceInline
        @Override
        public final
        IntVectorMax toVector() {
            return (IntVectorMax) super.toVectorTemplate();  // specialize
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
        IntMaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (IntMaskMax) VectorSupport.indexPartiallyInUpperRange(
                IntMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (IntMaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public IntMaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public IntMaskMax compress() {
            return (IntMaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                IntVectorMax.class, IntMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public IntMaskMax and(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMaskMax m = (IntMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, IntMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public IntMaskMax or(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMaskMax m = (IntMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, IntMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public IntMaskMax xor(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMaskMax m = (IntMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, IntMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(IntMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((IntMaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((IntMaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static IntMaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(IntMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final IntMaskMax  TRUE_MASK = new IntMaskMax(true);
        private static final IntMaskMax FALSE_MASK = new IntMaskMax(false);


        static boolean[] maskLowerHalf() {
            boolean[] a = new boolean[VLENGTH];
            int len = a.length >> 1;
            for (int i = 0; i < len; i++) {
                a[i] = true;
            }
            return a;
        }

        static final IntMaskMax LOWER_HALF_TRUE_MASK = new IntMaskMax(maskLowerHalf());
    }

    // Shuffle

    static final class IntShuffleMax extends AbstractShuffle<Integer> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        IntShuffleMax(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        IntShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        IntShuffleMax(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        int[] indices() {
            return (int[])getPayload();
        }

        @Override
        @ForceInline
        public IntSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Integer.MAX_VALUE);
            assert(Integer.MIN_VALUE <= -VLENGTH);
        }
        static final IntShuffleMax IOTA = new IntShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public IntVectorMax toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        IntVectorMax toBitsVector() {
            return (IntVectorMax) super.toBitsVectorTemplate();
        }

        @Override
        IntVectorMax toBitsVector0() {
            return ((IntVectorMax) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            toBitsVector().intoArray(a, offset);
        }

        @Override
        @ForceInline
        public void intoMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            toBitsVector().intoMemorySegment(ms, offset, bo);
         }

        @Override
        @ForceInline
        public final IntMaskMax laneIsValid() {
            return (IntMaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final IntShuffleMax rearrange(VectorShuffle<Integer> shuffle) {
            IntShuffleMax concreteShuffle = (IntShuffleMax) shuffle;
            return (IntShuffleMax) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final IntShuffleMax wrapIndexes() {
            IntVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (IntShuffleMax) v.toShuffle(vspecies(), false);
        }

        private static int[] prepare(int[] indices, int offset) {
            int[] a = new int[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = indices[offset + i];
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (int)si;
            }
            return a;
        }

        private static int[] prepare(IntUnaryOperator f) {
            int[] a = new int[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = f.applyAsInt(i);
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (int)si;
            }
            return a;
        }

        private static boolean indicesInRange(int[] indices) {
            int length = indices.length;
            for (int si : indices) {
                if (si >= (int)length || si < (int)(-length)) {
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
    IntVector fromArray0(int[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    IntVector fromArray0(int[] a, int offset, VectorMask<Integer> m, int offsetInRange) {
        return super.fromArray0Template(IntMaskMax.class, a, offset, (IntMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    IntVector fromArray0(int[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Integer> m) {
        return super.fromArray0Template(IntMaskMax.class, a, offset, indexMap, mapOffset, (IntMaskMax) m);
    }



    @ForceInline
    @Override
    final
    IntVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    IntVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Integer> m, int offsetInRange) {
        return super.fromMemorySegment0Template(IntMaskMax.class, ms, offset, (IntMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(int[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(int[] a, int offset, VectorMask<Integer> m) {
        super.intoArray0Template(IntMaskMax.class, a, offset, (IntMaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(int[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Integer> m) {
        super.intoArray0Template(IntMaskMax.class, a, offset, indexMap, mapOffset, (IntMaskMax) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Integer> m) {
        super.intoMemorySegment0Template(IntMaskMax.class, ms, offset, (IntMaskMax) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

