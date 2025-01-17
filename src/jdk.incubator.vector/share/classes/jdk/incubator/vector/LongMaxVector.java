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
final class LongMaxVector extends LongVector {
    static final LongSpecies VSPECIES =
        (LongSpecies) LongVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<LongMaxVector> VCLASS = LongMaxVector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Long> ETYPE = long.class; // used by the JVM

    LongMaxVector(long[] v) {
        super(v);
    }

    // For compatibility as LongMaxVector::new,
    // stored into species.vectorFactory.
    LongMaxVector(Object v) {
        this((long[]) v);
    }

    static final LongMaxVector ZERO = new LongMaxVector(new long[VLENGTH]);
    static final LongMaxVector IOTA = new LongMaxVector(VSPECIES.iotaArray());

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
    public final LongMaxVector broadcast(long e) {
        return (LongMaxVector) super.broadcastTemplate(e);  // specialize
    }


    @Override
    @ForceInline
    LongMaxMask maskFromArray(boolean[] bits) {
        return new LongMaxMask(bits);
    }

    @Override
    @ForceInline
    LongMaxShuffle iotaShuffle() { return LongMaxShuffle.IOTA; }

    @Override
    @ForceInline
    LongMaxShuffle iotaShuffle(int start, int step, boolean wrap) {
        return (LongMaxShuffle) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    LongMaxShuffle shuffleFromArray(int[] indices, int i) { return new LongMaxShuffle(indices, i); }

    @Override
    @ForceInline
    LongMaxShuffle shuffleFromOp(IntUnaryOperator fn) { return new LongMaxShuffle(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    LongMaxVector vectorFactory(long[] vec) {
        return new LongMaxVector(vec);
    }

    @ForceInline
    final @Override
    ByteMaxVector asByteVectorRaw() {
        return (ByteMaxVector) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    LongMaxVector uOp(FUnOp f) {
        return (LongMaxVector) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    LongMaxVector uOp(VectorMask<Long> m, FUnOp f) {
        return (LongMaxVector)
            super.uOpTemplate((LongMaxMask)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    LongMaxVector bOp(Vector<Long> v, FBinOp f) {
        return (LongMaxVector) super.bOpTemplate((LongMaxVector)v, f);  // specialize
    }

    @ForceInline
    final @Override
    LongMaxVector bOp(Vector<Long> v,
                     VectorMask<Long> m, FBinOp f) {
        return (LongMaxVector)
            super.bOpTemplate((LongMaxVector)v, (LongMaxMask)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    LongMaxVector tOp(Vector<Long> v1, Vector<Long> v2, FTriOp f) {
        return (LongMaxVector)
            super.tOpTemplate((LongMaxVector)v1, (LongMaxVector)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    LongMaxVector tOp(Vector<Long> v1, Vector<Long> v2,
                     VectorMask<Long> m, FTriOp f) {
        return (LongMaxVector)
            super.tOpTemplate((LongMaxVector)v1, (LongMaxVector)v2,
                              (LongMaxMask)m, f);  // specialize
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
    public LongMaxVector lanewise(Unary op) {
        return (LongMaxVector) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector lanewise(Unary op, VectorMask<Long> m) {
        return (LongMaxVector) super.lanewiseTemplate(op, LongMaxMask.class, (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector lanewise(Binary op, Vector<Long> v) {
        return (LongMaxVector) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector lanewise(Binary op, Vector<Long> v, VectorMask<Long> m) {
        return (LongMaxVector) super.lanewiseTemplate(op, LongMaxMask.class, v, (LongMaxMask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongMaxVector
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (LongMaxVector) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline LongMaxVector
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Long> m) {
        return (LongMaxVector) super.lanewiseShiftTemplate(op, LongMaxMask.class, e, (LongMaxMask) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    LongMaxVector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2) {
        return (LongMaxVector) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongMaxVector
    lanewise(Ternary op, Vector<Long> v1, Vector<Long> v2, VectorMask<Long> m) {
        return (LongMaxVector) super.lanewiseTemplate(op, LongMaxMask.class, v1, v2, (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    LongMaxVector addIndex(int scale) {
        return (LongMaxVector) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, LongMaxMask.class, (LongMaxMask) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, LongMaxMask.class, (LongMaxMask) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final LongMaxShuffle toShuffle() {
        return (LongMaxShuffle) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final LongMaxMask test(Test op) {
        return super.testTemplate(LongMaxMask.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final LongMaxMask test(Test op, VectorMask<Long> m) {
        return super.testTemplate(LongMaxMask.class, op, (LongMaxMask) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final LongMaxMask compare(Comparison op, Vector<Long> v) {
        return super.compareTemplate(LongMaxMask.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final LongMaxMask compare(Comparison op, long s) {
        return super.compareTemplate(LongMaxMask.class, op, s);  // specialize
    }


    @Override
    @ForceInline
    public final LongMaxMask compare(Comparison op, Vector<Long> v, VectorMask<Long> m) {
        return super.compareTemplate(LongMaxMask.class, op, v, (LongMaxMask) m);
    }


    @Override
    @ForceInline
    public LongMaxVector blend(Vector<Long> v, VectorMask<Long> m) {
        return (LongMaxVector)
            super.blendTemplate(LongMaxMask.class,
                                (LongMaxVector) v,
                                (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector slice(int origin, Vector<Long> v) {
        return (LongMaxVector) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector slice(int origin) {
        return (LongMaxVector) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector unslice(int origin, Vector<Long> w, int part) {
        return (LongMaxVector) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector unslice(int origin, Vector<Long> w, int part, VectorMask<Long> m) {
        return (LongMaxVector)
            super.unsliceTemplate(LongMaxMask.class,
                                  origin, w, part,
                                  (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector unslice(int origin) {
        return (LongMaxVector) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector rearrange(VectorShuffle<Long> s) {
        return (LongMaxVector)
            super.rearrangeTemplate(LongMaxShuffle.class,
                                    (LongMaxShuffle) s);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector rearrange(VectorShuffle<Long> shuffle,
                                  VectorMask<Long> m) {
        return (LongMaxVector)
            super.rearrangeTemplate(LongMaxShuffle.class,
                                    LongMaxMask.class,
                                    (LongMaxShuffle) shuffle,
                                    (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector rearrange(VectorShuffle<Long> s,
                                  Vector<Long> v) {
        return (LongMaxVector)
            super.rearrangeTemplate(LongMaxShuffle.class,
                                    (LongMaxShuffle) s,
                                    (LongMaxVector) v);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector compress(VectorMask<Long> m) {
        return (LongMaxVector)
            super.compressTemplate(LongMaxMask.class,
                                   (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector expand(VectorMask<Long> m) {
        return (LongMaxVector)
            super.expandTemplate(LongMaxMask.class,
                                   (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector selectFrom(Vector<Long> v) {
        return (LongMaxVector)
            super.selectFromTemplate((LongMaxVector) v);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector selectFrom(Vector<Long> v,
                                   VectorMask<Long> m) {
        return (LongMaxVector)
            super.selectFromTemplate((LongMaxVector) v,
                                     LongMaxMask.class, (LongMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public LongMaxVector selectFrom(Vector<Long> v1,
                                   Vector<Long> v2) {
        return (LongMaxVector)
            super.selectFromTemplate((LongMaxVector) v1, (LongMaxVector) v2);  // specialize
    }

    @ForceInline
    @Override
    public long lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return laneHelper(i);
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
    public LongMaxVector withLane(int i, long e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    public LongMaxVector withLaneHelper(int i, long e) {
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

    static final class LongMaxMask extends AbstractMask<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMaxMask(boolean[] bits) {
            this(bits, 0);
        }

        LongMaxMask(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        LongMaxMask(boolean val) {
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
        LongMaxMask uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new LongMaxMask(res);
        }

        @Override
        LongMaxMask bOp(VectorMask<Long> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((LongMaxMask)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new LongMaxMask(res);
        }

        @ForceInline
        @Override
        public final
        LongMaxVector toVector() {
            return (LongMaxVector) super.toVectorTemplate();  // specialize
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
        LongMaxMask indexPartiallyInUpperRange(long offset, long limit) {
            return (LongMaxMask) VectorSupport.indexPartiallyInUpperRange(
                LongMaxMask.class, long.class, VLENGTH, offset, limit,
                (o, l) -> (LongMaxMask) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public LongMaxMask not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public LongMaxMask compress() {
            return (LongMaxMask)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                LongMaxVector.class, LongMaxMask.class, ETYPE, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public LongMaxMask and(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaxMask m = (LongMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, LongMaxMask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public LongMaxMask or(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaxMask m = (LongMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, LongMaxMask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public LongMaxMask xor(VectorMask<Long> mask) {
            Objects.requireNonNull(mask);
            LongMaxMask m = (LongMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, LongMaxMask.class, null, long.class, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, LongMaxMask.class, long.class, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, LongMaxMask.class, long.class, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, LongMaxMask.class, long.class, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, LongMaxMask.class, long.class, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(LongMaxMask.class, long.class, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, LongMaxMask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((LongMaxMask)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, LongMaxMask.class, long.class, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((LongMaxMask)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static LongMaxMask maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(LongMaxMask.class, long.class, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final LongMaxMask  TRUE_MASK = new LongMaxMask(true);
        private static final LongMaxMask FALSE_MASK = new LongMaxMask(false);

    }

    // Shuffle

    static final class LongMaxShuffle extends AbstractShuffle<Long> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        LongMaxShuffle(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        LongMaxShuffle(int[] indices, int i) {
            this(prepare(indices, i));
        }

        LongMaxShuffle(IntUnaryOperator fn) {
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
        static final LongMaxShuffle IOTA = new LongMaxShuffle(IDENTITY);

        @Override
        @ForceInline
        public LongMaxVector toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        LongMaxVector toBitsVector() {
            return (LongMaxVector) super.toBitsVectorTemplate();
        }

        @Override
        LongMaxVector toBitsVector0() {
            return ((LongMaxVector) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final LongMaxMask laneIsValid() {
            return (LongMaxMask) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final LongMaxShuffle rearrange(VectorShuffle<Long> shuffle) {
            LongMaxShuffle concreteShuffle = (LongMaxShuffle) shuffle;
            return (LongMaxShuffle) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final LongMaxShuffle wrapIndexes() {
            LongMaxVector v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongMaxVector) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongMaxVector) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (LongMaxShuffle) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(LongMaxMask.class, a, offset, (LongMaxMask) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    LongVector fromArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        return super.fromArray0Template(LongMaxMask.class, a, offset, indexMap, mapOffset, (LongMaxMask) m);
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
        return super.fromMemorySegment0Template(LongMaxMask.class, ms, offset, (LongMaxMask) m, offsetInRange);  // specialize
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
        super.intoArray0Template(LongMaxMask.class, a, offset, (LongMaxMask) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(long[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Long> m) {
        super.intoArray0Template(LongMaxMask.class, a, offset, indexMap, mapOffset, (LongMaxMask) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Long> m) {
        super.intoMemorySegment0Template(LongMaxMask.class, ms, offset, (LongMaxMask) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

