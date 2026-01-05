/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
final class Long64Vector extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_64;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Long64Vector> VCLASS = Long64Vector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    Long64Vector(long[] v) {
        super(v);
    }

    // For compatibility as Long64Vector::new,
    // stored into species.vectorFactory.
    Long64Vector(Object v) {
        this((long[]) v);
    }

    static final Long64Vector ZERO = new Long64Vector(new long[VLENGTH]);
    static final Long64Vector IOTA = new Long64Vector(VSPECIES.iotaArray());

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

    // Virtualized constructors

    @Override
    @ForceInline
    public final Long64Vector broadcast(long e) {
        return (Long64Vector) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    Long64Mask maskFromArray(boolean[] bits) {
        return new Long64Mask(bits);
    }

    @Override
    @ForceInline
    Long64Shuffle iotaShuffle() { return Long64Shuffle.IOTA; }

    @Override
    @ForceInline
    Long64Shuffle iotaShuffle(int start, int step, boolean wrap) {
        return (Long64Shuffle) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    Long64Shuffle shuffleFromArray(int[] indices, int i) { return new Long64Shuffle(indices, i); }

    @Override
    @ForceInline
    Long64Shuffle shuffleFromOp(IntUnaryOperator fn) { return new Long64Shuffle(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Long64Vector vectorFactory(long[] vec) {
        return new Long64Vector(vec);
    }

    @ForceInline
    final @Override
    Byte64Vector asByteVectorRaw() {
        return (Byte64Vector) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    Long64Vector uOp(FUnOp f) {
        return (Long64Vector) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Long64Vector uOp(VectorMask<Long> m, FUnOp f) {
        return (Long64Vector)
            super.uOpTemplate((Long64Mask)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Long64Vector bOp(Vector<Long> v, FBinOp f) {
        return (Long64Vector) super.bOpTemplate((Long64Vector)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Long64Vector bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (Long64Vector)
            super.bOpTemplate((Long64Vector)v, (Long64Mask)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Long64Vector tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (Long64Vector)
            super.tOpTemplate((Long64Vector)v1, (Long64Vector)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Long64Vector tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (Long64Vector)
            super.tOpTemplate((Long64Vector)v1, (Long64Vector)v2,
                              (Long64Mask)m, f);  // specialize
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
    public Long64Vector lanewise(Unary op) {
        return (Long64Vector) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector lanewise(Unary op, VectorMask<Long> m) {
        return (Long64Vector) super.lanewiseTemplate(op, Long64Mask.class, (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector lanewise(Binary op, Vector<Long> v) {
        return (Long64Vector) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (Long64Vector) super.lanewiseTemplate(op, Long64Mask.class, v, (Long64Mask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline Long64Vector
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (Long64Vector) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline Long64Vector
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (Long64Vector) super.lanewiseShiftTemplate(op, Long64Mask.class, e, (Long64Mask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    Long64Vector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (Long64Vector) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    Long64Vector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (Long64Vector) super.lanewiseTemplate(op, Long64Mask.class, v1, v2, (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    Long64Vector addIndex(int scale) {
        return (Long64Vector) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, Long64Mask.class, (Long64Mask) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, Long64Mask.class, (Long64Mask) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final Long64Shuffle toShuffle() {
        return (Long64Shuffle) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final Long64Mask test(Test op) {
        return super.testTemplate(Long64Mask.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final Long64Mask test(Test op, VectorMask<Long> m) {
        return super.testTemplate(Long64Mask.class, op, (Long64Mask) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final Long64Mask compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(Long64Mask.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final Long64Mask compare(Comparison op, long s) {
        return super.compareTemplate(Long64Mask.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final Long64Mask compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(Long64Mask.class, op, v, (Long64Mask) m);
    }


    @Override
    @ForceInline
    public Long64Vector blend(Vector<Long> v, VectorMask<Long> m) {
        return (Long64Vector)
            super.blendTemplate(Long64Mask.class,
                                (Long64Vector) v,
                                (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector slice(int origin, Vector<Long> v) {
        return (Long64Vector) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector slice(int origin) {
        return (Long64Vector) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector unslice(int origin, Vector<Long> w, int part) {
        return (Long64Vector) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (Long64Vector)
            super.unsliceTemplate(Long64Mask.class,
                                  origin, w, part,
                                  (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector unslice(int origin) {
        return (Long64Vector) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector rearrange(VectorShuffle<Long> s) {
        return (Long64Vector)
            super.rearrangeTemplate(Long64Shuffle.class,
                                    (Long64Shuffle) s);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (Long64Vector)
            super.rearrangeTemplate(Long64Shuffle.class,
                                    Long64Mask.class,
                                    (Long64Shuffle) shuffle,
                                    (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (Long64Vector)
            super.rearrangeTemplate(Long64Shuffle.class,
                                    (Long64Shuffle) s,
                                    (Long64Vector) v);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector compress(VectorMask<Long> m) {
        return (Long64Vector)
            super.compressTemplate(Long64Mask.class,
                                   (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector expand(VectorMask<Long> m) {
        return (Long64Vector)
            super.expandTemplate(Long64Mask.class,
                                   (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector selectFrom(Vector<Long> v) {
        return (Long64Vector)
            super.selectFromTemplate((Long64Vector) v);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (Long64Vector)
            super.selectFromTemplate((Long64Vector) v,
                                     Long64Mask.class, (Long64Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long64Vector selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (Long64Vector)
            super.selectFromTemplate((Long64Vector) v1, (Long64Vector) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public long laneHelper(int i) {
        return (long) VectorSupport.extract(
                                VCLASS, ETYPE, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    long[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public Long64Vector withLane(int i, long e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public Long64Vector withLaneHelper(int i, long e) {
        return VectorSupport.insert(
                                VCLASS, ETYPE, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    long[] res = v.vec().clone();
                                    res[ix] = (long)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class Long64Mask extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        Long64Mask(boolean[] bits) {
            this(bits, 0);
        }

        Long64Mask(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Long64Mask(boolean val) {
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
        Long64Mask uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Long64Mask(res);
        }

        @Override
        Long64Mask bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Long64Mask)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Long64Mask(res);
        }

        @ForceInline
        @Override
        public final
        Long64Vector toVector() {
            return (Long64Vector) super.toVectorTemplate();  // specialize
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
                this.getClass(), ETYPE, VLENGTH,
                species.maskType(), species.elementType(), VLENGTH,
                this, species,
                (m, s) -> s.maskFactory(m.toArray()).check(s));
        }

        @Override
        @ForceInline
        /*package-private*/
        Long64Mask indexPartiallyInUpperRange(long offset, long limit) {
            return (Long64Mask) VectorSupport.indexPartiallyInUpperRange(
                Long64Mask.class, long.class, VLENGTH, offset, limit,
                (o, l) -> (Long64Mask) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public Long64Mask not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Long64Mask compress() {
            return (Long64Mask)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Long64Vector.class, Long64Mask.class, ETYPE, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public Long64Mask and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long64Mask m = (Long64Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Long64Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Long64Mask or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long64Mask m = (Long64Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Long64Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Long64Mask xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long64Mask m = (Long64Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Long64Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Long64Mask.class, long.class, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Long64Mask.class, long.class, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Long64Mask.class, long.class, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Long64Mask.class, long.class, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Long64Mask.class, long.class, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Long64Mask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((Long64Mask)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Long64Mask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((Long64Mask)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Long64Mask maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Long64Mask.class, long.class, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Long64Mask  TRUE_MASK = new Long64Mask(true);
        private static final Long64Mask FALSE_MASK = new Long64Mask(false);

    }

    // Shuffle

    static final class Long64Shuffle extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        Long64Shuffle(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        Long64Shuffle(int[] indices, int i) {
            this(prepare(indices, i));
        }

        Long64Shuffle(IntUnaryOperator fn) {
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
        static final Long64Shuffle IOTA = new Long64Shuffle(IDENTITY);

        @Override
        @ForceInline
        public Long64Vector toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        Long64Vector toBitsVector() {
            return (Long64Vector) super.toBitsVectorTemplate();
        }

        @Override
        Long64Vector toBitsVector0() {
            return ((Long64Vector) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final Long64Mask laneIsValid() {
            return (Long64Mask) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final Long64Shuffle rearrange(VectorShuffle<Long> shuffle) {
            Long64Shuffle concreteShuffle = (Long64Shuffle) shuffle;
            return (Long64Shuffle) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final Long64Shuffle wrapIndexes() {
            Long64Vector v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (Long64Vector) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (Long64Vector) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (Long64Shuffle) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(Long64Mask.class, a, offset, (Long64Mask) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(Long64Mask.class, a, offset, indexMap, mapOffset, (Long64Mask) m);
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
        return super.fromMemorySegment0Template(Long64Mask.class, ms, offset, (Long64Mask) m, offsetInRange);  // specialize
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
        super.intoArray0Template(Long64Mask.class, a, offset, (Long64Mask) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(Long64Mask.class, a, offset, indexMap, mapOffset, (Long64Mask) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(Long64Mask.class, ms, offset, (Long64Mask) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

