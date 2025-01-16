/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import static jdk.internal.vm.vector.VectorSupport.*;

import static jdk.incubator.vector.VectorOperators.*;

// -- This file was mechanically generated: Do not edit! -- //

@SuppressWarnings("cast")  // warning: redundant cast
final class Long512Vector extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Long512Vector> VCLASS = Long512Vector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    Long512Vector(long[] v) {
        super(v);
    }

    // For compatibility as Long512Vector::new,
    // stored into species.vectorFactory.
    Long512Vector(Object v) {
        this((long[]) v);
    }

    static final Long512Vector ZERO = new Long512Vector(new long[VLENGTH]);
    static final Long512Vector IOTA = new Long512Vector(VSPECIES.iotaArray());

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
    public final Long512Vector broadcast(long e) {
        return (Long512Vector) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    Long512Mask maskFromArray(boolean[] bits) {
        return new Long512Mask(bits);
    }

    @Override
    @ForceInline
    Long512Shuffle iotaShuffle() { return Long512Shuffle.IOTA; }

    @Override
    @ForceInline
    Long512Shuffle iotaShuffle(int start, int step, boolean wrap) {
        return (Long512Shuffle) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    Long512Shuffle shuffleFromArray(int[] indices, int i) { return new Long512Shuffle(indices, i); }

    @Override
    @ForceInline
    Long512Shuffle shuffleFromOp(IntUnaryOperator fn) { return new Long512Shuffle(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Long512Vector vectorFactory(long[] vec) {
        return new Long512Vector(vec);
    }

    @ForceInline
    final @Override
    Byte512Vector asByteVectorRaw() {
        return (Byte512Vector) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    Long512Vector uOp(FUnOp f) {
        return (Long512Vector) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Long512Vector uOp(VectorMask<Long> m, FUnOp f) {
        return (Long512Vector)
            super.uOpTemplate((Long512Mask)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Long512Vector bOp(Vector<Long> v, FBinOp f) {
        return (Long512Vector) super.bOpTemplate((Long512Vector)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Long512Vector bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (Long512Vector)
            super.bOpTemplate((Long512Vector)v, (Long512Mask)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Long512Vector tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (Long512Vector)
            super.tOpTemplate((Long512Vector)v1, (Long512Vector)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Long512Vector tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (Long512Vector)
            super.tOpTemplate((Long512Vector)v1, (Long512Vector)v2,
                              (Long512Mask)m, f);  // specialize
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
    public Long512Vector lanewise(Unary op) {
        return (Long512Vector) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector lanewise(Unary op, VectorMask<Long> m) {
        return (Long512Vector) super.lanewiseTemplate(op, Long512Mask.class, (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector lanewise(Binary op, Vector<Long> v) {
        return (Long512Vector) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (Long512Vector) super.lanewiseTemplate(op, Long512Mask.class, v, (Long512Mask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline Long512Vector
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (Long512Vector) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline Long512Vector
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (Long512Vector) super.lanewiseShiftTemplate(op, Long512Mask.class, e, (Long512Mask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    Long512Vector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (Long512Vector) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    Long512Vector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (Long512Vector) super.lanewiseTemplate(op, Long512Mask.class, v1, v2, (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    Long512Vector addIndex(int scale) {
        return (Long512Vector) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, Long512Mask.class, (Long512Mask) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, Long512Mask.class, (Long512Mask) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final Long512Shuffle toShuffle() {
        return (Long512Shuffle) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final Long512Mask test(Test op) {
        return super.testTemplate(Long512Mask.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final Long512Mask test(Test op, VectorMask<Long> m) {
        return super.testTemplate(Long512Mask.class, op, (Long512Mask) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final Long512Mask compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(Long512Mask.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final Long512Mask compare(Comparison op, long s) {
        return super.compareTemplate(Long512Mask.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final Long512Mask compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(Long512Mask.class, op, v, (Long512Mask) m);
    }


    @Override
    @ForceInline
    public Long512Vector blend(Vector<Long> v, VectorMask<Long> m) {
        return (Long512Vector)
            super.blendTemplate(Long512Mask.class,
                                (Long512Vector) v,
                                (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector slice(int origin, Vector<Long> v) {
        return (Long512Vector) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector slice(int origin) {
        return (Long512Vector) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector unslice(int origin, Vector<Long> w, int part) {
        return (Long512Vector) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (Long512Vector)
            super.unsliceTemplate(Long512Mask.class,
                                  origin, w, part,
                                  (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector unslice(int origin) {
        return (Long512Vector) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector rearrange(VectorShuffle<Long> s) {
        return (Long512Vector)
            super.rearrangeTemplate(Long512Shuffle.class,
                                    (Long512Shuffle) s);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (Long512Vector)
            super.rearrangeTemplate(Long512Shuffle.class,
                                    Long512Mask.class,
                                    (Long512Shuffle) shuffle,
                                    (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (Long512Vector)
            super.rearrangeTemplate(Long512Shuffle.class,
                                    (Long512Shuffle) s,
                                    (Long512Vector) v);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector compress(VectorMask<Long> m) {
        return (Long512Vector)
            super.compressTemplate(Long512Mask.class,
                                   (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector expand(VectorMask<Long> m) {
        return (Long512Vector)
            super.expandTemplate(Long512Mask.class,
                                   (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector selectFrom(Vector<Long> v) {
        return (Long512Vector)
            super.selectFromTemplate((Long512Vector) v);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (Long512Vector)
            super.selectFromTemplate((Long512Vector) v,
                                     Long512Mask.class, (Long512Mask) m);  // specialize
    }

    @Override
    @ForceInline
    public Long512Vector selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (Long512Vector)
            super.selectFromTemplate((Long512Vector) v1, (Long512Vector) v2);  // specialize
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
    public Long512Vector withLane(int i, long e) {
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

    public Long512Vector withLaneHelper(int i, long e) {
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

    static final class Long512Mask extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        Long512Mask(boolean[] bits) {
            this(bits, 0);
        }

        Long512Mask(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Long512Mask(boolean val) {
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
        Long512Mask uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Long512Mask(res);
        }

        @Override
        Long512Mask bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Long512Mask)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Long512Mask(res);
        }

        @ForceInline
        @Override
        public final
        Long512Vector toVector() {
            return (Long512Vector) super.toVectorTemplate();  // specialize
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
        Long512Mask indexPartiallyInUpperRange(long offset, long limit) {
            return (Long512Mask) VectorSupport.indexPartiallyInUpperRange(
                Long512Mask.class, long.class, VLENGTH, offset, limit,
                (o, l) -> (Long512Mask) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public Long512Mask not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Long512Mask compress() {
            return (Long512Mask)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Long512Vector.class, Long512Mask.class, ETYPE, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public Long512Mask and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long512Mask m = (Long512Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Long512Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Long512Mask or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long512Mask m = (Long512Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Long512Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Long512Mask xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            Long512Mask m = (Long512Mask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Long512Mask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Long512Mask.class, long.class, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Long512Mask.class, long.class, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Long512Mask.class, long.class, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Long512Mask.class, long.class, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Long512Mask.class, long.class, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Long512Mask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((Long512Mask)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Long512Mask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((Long512Mask)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Long512Mask maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Long512Mask.class, long.class, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Long512Mask  TRUE_MASK = new Long512Mask(true);
        private static final Long512Mask FALSE_MASK = new Long512Mask(false);

    }

    // Shuffle

    static final class Long512Shuffle extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        Long512Shuffle(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        Long512Shuffle(int[] indices, int i) {
            this(prepare(indices, i));
        }

        Long512Shuffle(IntUnaryOperator fn) {
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
        static final Long512Shuffle IOTA = new Long512Shuffle(IDENTITY);

        @Override
        @ForceInline
        public Long512Vector toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        Long512Vector toBitsVector() {
            return (Long512Vector) super.toBitsVectorTemplate();
        }

        @Override
        Long512Vector toBitsVector0() {
            return ((Long512Vector) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final Long512Mask laneIsValid() {
            return (Long512Mask) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final Long512Shuffle rearrange(VectorShuffle<Long> shuffle) {
            Long512Shuffle concreteShuffle = (Long512Shuffle) shuffle;
            return (Long512Shuffle) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final Long512Shuffle wrapIndexes() {
            Long512Vector v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (Long512Vector) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (Long512Vector) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (Long512Shuffle) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(Long512Mask.class, a, offset, (Long512Mask) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(Long512Mask.class, a, offset, indexMap, mapOffset, (Long512Mask) m);
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
        return super.fromMemorySegment0Template(Long512Mask.class, ms, offset, (Long512Mask) m, offsetInRange);  // specialize
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
        super.intoArray0Template(Long512Mask.class, a, offset, (Long512Mask) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(Long512Mask.class, a, offset, indexMap, mapOffset, (Long512Mask) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(Long512Mask.class, ms, offset, (Long512Mask) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

