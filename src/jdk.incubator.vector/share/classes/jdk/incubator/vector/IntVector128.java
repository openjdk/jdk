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
final class IntVector128 extends IntVector {
    static final IntSpecies VSPECIES =
        (IntSpecies) IntVector.SPECIES_128;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<IntVector128> VCLASS = IntVector128.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Integer> ETYPE = int.class; // used by the JVM

    IntVector128(int[] v) {
        super(v);
    }

    // For compatibility as IntVector128::new,
    // stored into species.vectorFactory.
    IntVector128(Object v) {
        this((int[]) v);
    }

    static final IntVector128 ZERO = new IntVector128(new int[VLENGTH]);
    static final IntVector128 IOTA = new IntVector128(VSPECIES.iotaArray());

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
    public final IntVector128 broadcast(int e) {
        return (IntVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final IntVector128 broadcast(long e) {
        return (IntVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    IntMask128 maskFromArray(boolean[] bits) {
        return new IntMask128(bits);
    }

    @Override
    @ForceInline
    IntShuffle128 iotaShuffle() { return IntShuffle128.IOTA; }

    @Override
    @ForceInline
    IntShuffle128 iotaShuffle(int start, int step, boolean wrap) {
        return (IntShuffle128) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    IntShuffle128 shuffleFromArray(int[] indices, int i) { return new IntShuffle128(indices, i); }

    @Override
    @ForceInline
    IntShuffle128 shuffleFromOp(IntUnaryOperator fn) { return new IntShuffle128(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    IntVector128 vectorFactory(int[] vec) {
        return new IntVector128(vec);
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
    IntVector128 uOp(FUnOp f) {
        return (IntVector128) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    IntVector128 uOp(VectorMask<Integer> m, FUnOp f) {
        return (IntVector128)
            super.uOpTemplate((IntMask128)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    IntVector128 bOp(Vector<Integer> v, FBinOp f) {
        return (IntVector128) super.bOpTemplate((IntVector128)v, f);  // specialize
    }

    @ForceInline
    final @Override
    IntVector128 bOp(Vector<Integer> v,
                     VectorMask<Integer> m, FBinOp f) {
        return (IntVector128)
            super.bOpTemplate((IntVector128)v, (IntMask128)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    IntVector128 tOp(Vector<Integer> v1, Vector<Integer> v2, FTriOp f) {
        return (IntVector128)
            super.tOpTemplate((IntVector128)v1, (IntVector128)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    IntVector128 tOp(Vector<Integer> v1, Vector<Integer> v2,
                     VectorMask<Integer> m, FTriOp f) {
        return (IntVector128)
            super.tOpTemplate((IntVector128)v1, (IntVector128)v2,
                              (IntMask128)m, f);  // specialize
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
    public IntVector128 lanewise(Unary op) {
        return (IntVector128) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 lanewise(Unary op, VectorMask<Integer> m) {
        return (IntVector128) super.lanewiseTemplate(op, IntMask128.class, (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 lanewise(Binary op, Vector<Integer> v) {
        return (IntVector128) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 lanewise(Binary op, Vector<Integer> v, VectorMask<Integer> m) {
        return (IntVector128) super.lanewiseTemplate(op, IntMask128.class, v, (IntMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline IntVector128
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (IntVector128) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline IntVector128
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Integer> m) {
        return (IntVector128) super.lanewiseShiftTemplate(op, IntMask128.class, e, (IntMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    IntVector128
    lanewise(Ternary op, Vector<Integer> v1, Vector<Integer> v2) {
        return (IntVector128) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    IntVector128
    lanewise(Ternary op, Vector<Integer> v1, Vector<Integer> v2, VectorMask<Integer> m) {
        return (IntVector128) super.lanewiseTemplate(op, IntMask128.class, v1, v2, (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    IntVector128 addIndex(int scale) {
        return (IntVector128) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, IntMask128.class, (IntMask128) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, IntMask128.class, (IntMask128) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final IntShuffle128 toShuffle() {
        return (IntShuffle128) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final IntMask128 test(Test op) {
        return super.testTemplate(IntMask128.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final IntMask128 test(Test op, VectorMask<Integer> m) {
        return super.testTemplate(IntMask128.class, op, (IntMask128) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final IntMask128 compare(Comparison op, Vector<Integer> v) {
        return super.compareTemplate(IntMask128.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final IntMask128 compare(Comparison op, int s) {
        return super.compareTemplate(IntMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final IntMask128 compare(Comparison op, long s) {
        return super.compareTemplate(IntMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final IntMask128 compare(Comparison op, Vector<Integer> v, VectorMask<Integer> m) {
        return super.compareTemplate(IntMask128.class, op, v, (IntMask128) m);
    }


    @Override
    @ForceInline
    public IntVector128 blend(Vector<Integer> v, VectorMask<Integer> m) {
        return (IntVector128)
            super.blendTemplate(IntMask128.class,
                                (IntVector128) v,
                                (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 slice(int origin, Vector<Integer> v) {
        return (IntVector128) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 slice(int origin) {
        return (IntVector128) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 unslice(int origin, Vector<Integer> w, int part) {
        return (IntVector128) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 unslice(int origin, Vector<Integer> w, int part, VectorMask<Integer> m) {
        return (IntVector128)
            super.unsliceTemplate(IntMask128.class,
                                  origin, w, part,
                                  (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 unslice(int origin) {
        return (IntVector128) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 rearrange(VectorShuffle<Integer> s) {
        return (IntVector128)
            super.rearrangeTemplate(IntShuffle128.class,
                                    (IntShuffle128) s);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 rearrange(VectorShuffle<Integer> shuffle,
                                  VectorMask<Integer> m) {
        return (IntVector128)
            super.rearrangeTemplate(IntShuffle128.class,
                                    IntMask128.class,
                                    (IntShuffle128) shuffle,
                                    (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 rearrange(VectorShuffle<Integer> s,
                                  Vector<Integer> v) {
        return (IntVector128)
            super.rearrangeTemplate(IntShuffle128.class,
                                    (IntShuffle128) s,
                                    (IntVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 compress(VectorMask<Integer> m) {
        return (IntVector128)
            super.compressTemplate(IntMask128.class,
                                   (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 expand(VectorMask<Integer> m) {
        return (IntVector128)
            super.expandTemplate(IntMask128.class,
                                   (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 selectFrom(Vector<Integer> v) {
        return (IntVector128)
            super.selectFromTemplate((IntVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 selectFrom(Vector<Integer> v,
                                   VectorMask<Integer> m) {
        return (IntVector128)
            super.selectFromTemplate((IntVector128) v,
                                     IntMask128.class, (IntMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public IntVector128 selectFrom(Vector<Integer> v1,
                                   Vector<Integer> v2) {
        return (IntVector128)
            super.selectFromTemplate((IntVector128) v1, (IntVector128) v2);  // specialize
    }

    @ForceInline
    @Override
    public int lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            case 2: return laneHelper(2);
            case 3: return laneHelper(3);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
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
    public IntVector128 withLane(int i, int e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public IntVector128 withLaneHelper(int i, int e) {
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

    static final class IntMask128 extends AbstractMask<Integer> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        IntMask128(boolean[] bits) {
            this(bits, 0);
        }

        IntMask128(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        IntMask128(boolean val) {
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
        IntMask128 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new IntMask128(res);
        }

        @Override
        IntMask128 bOp(VectorMask<Integer> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((IntMask128)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new IntMask128(res);
        }

        @ForceInline
        @Override
        public final
        IntVector128 toVector() {
            return (IntVector128) super.toVectorTemplate();  // specialize
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
        IntMask128 indexPartiallyInUpperRange(long offset, long limit) {
            return (IntMask128) VectorSupport.indexPartiallyInUpperRange(
                IntMask128.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (IntMask128) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public IntMask128 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public IntMask128 compress() {
            return (IntMask128)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                IntVector128.class, IntMask128.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public IntMask128 and(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMask128 m = (IntMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, IntMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public IntMask128 or(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMask128 m = (IntMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, IntMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public IntMask128 xor(VectorMask<Integer> mask) {
            Objects.requireNonNull(mask);
            IntMask128 m = (IntMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, IntMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(IntMask128.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((IntMask128)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((IntMask128)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static IntMask128 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(IntMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final IntMask128  TRUE_MASK = new IntMask128(true);
        private static final IntMask128 FALSE_MASK = new IntMask128(false);

    }

    // Shuffle

    static final class IntShuffle128 extends AbstractShuffle<Integer> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        IntShuffle128(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        IntShuffle128(int[] indices, int i) {
            this(prepare(indices, i));
        }

        IntShuffle128(IntUnaryOperator fn) {
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
        static final IntShuffle128 IOTA = new IntShuffle128(IDENTITY);

        @Override
        @ForceInline
        public IntVector128 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        IntVector128 toBitsVector() {
            return (IntVector128) super.toBitsVectorTemplate();
        }

        @Override
        IntVector128 toBitsVector0() {
            return ((IntVector128) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final IntMask128 laneIsValid() {
            return (IntMask128) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final IntShuffle128 rearrange(VectorShuffle<Integer> shuffle) {
            IntShuffle128 concreteShuffle = (IntShuffle128) shuffle;
            return (IntShuffle128) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final IntShuffle128 wrapIndexes() {
            IntVector128 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVector128) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVector128) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (IntShuffle128) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(IntMask128.class, a, offset, (IntMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    IntVector fromArray0(int[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Integer> m) {
        return super.fromArray0Template(IntMask128.class, a, offset, indexMap, mapOffset, (IntMask128) m);
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
        return super.fromMemorySegment0Template(IntMask128.class, ms, offset, (IntMask128) m, offsetInRange);  // specialize
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
        super.intoArray0Template(IntMask128.class, a, offset, (IntMask128) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(int[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Integer> m) {
        super.intoArray0Template(IntMask128.class, a, offset, indexMap, mapOffset, (IntMask128) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Integer> m) {
        super.intoMemorySegment0Template(IntMask128.class, ms, offset, (IntMask128) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

