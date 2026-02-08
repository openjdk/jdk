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
final class FloatVectorMax extends FloatVector {
    static final FloatSpecies VSPECIES =
        (FloatSpecies) FloatVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<FloatVectorMax> VCLASS = FloatVectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Float> ETYPE = float.class; // used by the JVM

    FloatVectorMax(float[] v) {
        super(v);
    }

    // For compatibility as FloatVectorMax::new,
    // stored into species.vectorFactory.
    FloatVectorMax(Object v) {
        this((float[]) v);
    }

    static final FloatVectorMax ZERO = new FloatVectorMax(new float[VLENGTH]);
    static final FloatVectorMax IOTA = new FloatVectorMax(VSPECIES.iotaArray());

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
    public FloatSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Float> elementType() { return float.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Float.SIZE; }

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
    float[] vec() {
        return (float[])getPayload();
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
    public final FloatVectorMax broadcast(float e) {
        return (FloatVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final FloatVectorMax broadcast(long e) {
        return (FloatVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    FloatMaskMax maskFromArray(boolean[] bits) {
        return new FloatMaskMax(bits);
    }

    @Override
    @ForceInline
    FloatShuffleMax iotaShuffle() { return FloatShuffleMax.IOTA; }

    @Override
    @ForceInline
    FloatShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (FloatShuffleMax) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    FloatShuffleMax shuffleFromArray(int[] indices, int i) { return new FloatShuffleMax(indices, i); }

    @Override
    @ForceInline
    FloatShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new FloatShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    FloatVectorMax vectorFactory(float[] vec) {
        return new FloatVectorMax(vec);
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
    FloatVectorMax uOp(FUnOp f) {
        return (FloatVectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVectorMax uOp(VectorMask<Float> m, FUnOp f) {
        return (FloatVectorMax)
            super.uOpTemplate((FloatMaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    FloatVectorMax bOp(Vector<Float> v, FBinOp f) {
        return (FloatVectorMax) super.bOpTemplate((FloatVectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVectorMax bOp(Vector<Float> v,
                     VectorMask<Float> m, FBinOp f) {
        return (FloatVectorMax)
            super.bOpTemplate((FloatVectorMax)v, (FloatMaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    FloatVectorMax tOp(Vector<Float> v1, Vector<Float> v2, FTriOp f) {
        return (FloatVectorMax)
            super.tOpTemplate((FloatVectorMax)v1, (FloatVectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVectorMax tOp(Vector<Float> v1, Vector<Float> v2,
                     VectorMask<Float> m, FTriOp f) {
        return (FloatVectorMax)
            super.tOpTemplate((FloatVectorMax)v1, (FloatVectorMax)v2,
                              (FloatMaskMax)m, f);  // specialize
    }

    @ForceInline
    final @Override
    float rOp(float v, VectorMask<Float> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Float,F> conv,
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
    public FloatVectorMax lanewise(Unary op) {
        return (FloatVectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax lanewise(Unary op, VectorMask<Float> m) {
        return (FloatVectorMax) super.lanewiseTemplate(op, FloatMaskMax.class, (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax lanewise(Binary op, Vector<Float> v) {
        return (FloatVectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax lanewise(Binary op, Vector<Float> v, VectorMask<Float> m) {
        return (FloatVectorMax) super.lanewiseTemplate(op, FloatMaskMax.class, v, (FloatMaskMax) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    FloatVectorMax
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2) {
        return (FloatVectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVectorMax
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2, VectorMask<Float> m) {
        return (FloatVectorMax) super.lanewiseTemplate(op, FloatMaskMax.class, v1, v2, (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVectorMax addIndex(int scale) {
        return (FloatVectorMax) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final float reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final float reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Float> m) {
        return super.reduceLanesTemplate(op, FloatMaskMax.class, (FloatMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Float> m) {
        return (long) super.reduceLanesTemplate(op, FloatMaskMax.class, (FloatMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final FloatShuffleMax toShuffle() {
        return (FloatShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final FloatMaskMax test(Test op) {
        return super.testTemplate(FloatMaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMaskMax test(Test op, VectorMask<Float> m) {
        return super.testTemplate(FloatMaskMax.class, op, (FloatMaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final FloatMaskMax compare(Comparison op, Vector<Float> v) {
        return super.compareTemplate(FloatMaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMaskMax compare(Comparison op, float s) {
        return super.compareTemplate(FloatMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMaskMax compare(Comparison op, long s) {
        return super.compareTemplate(FloatMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMaskMax compare(Comparison op, Vector<Float> v, VectorMask<Float> m) {
        return super.compareTemplate(FloatMaskMax.class, op, v, (FloatMaskMax) m);
    }


    @Override
    @ForceInline
    public FloatVectorMax blend(Vector<Float> v, VectorMask<Float> m) {
        return (FloatVectorMax)
            super.blendTemplate(FloatMaskMax.class,
                                (FloatVectorMax) v,
                                (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax slice(int origin, Vector<Float> v) {
        return (FloatVectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax slice(int origin) {
        return (FloatVectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax unslice(int origin, Vector<Float> w, int part) {
        return (FloatVectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax unslice(int origin, Vector<Float> w, int part, VectorMask<Float> m) {
        return (FloatVectorMax)
            super.unsliceTemplate(FloatMaskMax.class,
                                  origin, w, part,
                                  (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax unslice(int origin) {
        return (FloatVectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax rearrange(VectorShuffle<Float> s) {
        return (FloatVectorMax)
            super.rearrangeTemplate(FloatShuffleMax.class,
                                    (FloatShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax rearrange(VectorShuffle<Float> shuffle,
                                  VectorMask<Float> m) {
        return (FloatVectorMax)
            super.rearrangeTemplate(FloatShuffleMax.class,
                                    FloatMaskMax.class,
                                    (FloatShuffleMax) shuffle,
                                    (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax rearrange(VectorShuffle<Float> s,
                                  Vector<Float> v) {
        return (FloatVectorMax)
            super.rearrangeTemplate(FloatShuffleMax.class,
                                    (FloatShuffleMax) s,
                                    (FloatVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax compress(VectorMask<Float> m) {
        return (FloatVectorMax)
            super.compressTemplate(FloatMaskMax.class,
                                   (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax expand(VectorMask<Float> m) {
        return (FloatVectorMax)
            super.expandTemplate(FloatMaskMax.class,
                                   (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax selectFrom(Vector<Float> v) {
        return (FloatVectorMax)
            super.selectFromTemplate((FloatVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax selectFrom(Vector<Float> v,
                                   VectorMask<Float> m) {
        return (FloatVectorMax)
            super.selectFromTemplate((FloatVectorMax) v,
                                     FloatMaskMax.class, (FloatMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVectorMax selectFrom(Vector<Float> v1,
                                   Vector<Float> v2) {
        return (FloatVectorMax)
            super.selectFromTemplate((FloatVectorMax) v1, (FloatVectorMax) v2);  // specialize
    }

    @ForceInline
    @Override
    public float lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        int bits = laneHelper(i);
        return Float.intBitsToFloat(bits);
    }

    @ForceInline
    public int laneHelper(int i) {
        return (int) VectorSupport.extract(
                     VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                     this, i,
                     (vec, ix) -> {
                     float[] vecarr = vec.vec();
                     return (long)Float.floatToRawIntBits(vecarr[ix]);
                     });
    }

    @ForceInline
    @Override
    public FloatVectorMax withLane(int i, float e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public FloatVectorMax withLaneHelper(int i, float e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)Float.floatToRawIntBits(e),
                                (v, ix, bits) -> {
                                    float[] res = v.vec().clone();
                                    res[ix] = Float.intBitsToFloat((int)bits);
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class FloatMaskMax extends AbstractMask<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Float> ETYPE = float.class; // used by the JVM

        FloatMaskMax(boolean[] bits) {
            this(bits, 0);
        }

        FloatMaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        FloatMaskMax(boolean val) {
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
        public FloatSpecies vspecies() {
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
        FloatMaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new FloatMaskMax(res);
        }

        @Override
        FloatMaskMax bOp(VectorMask<Float> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((FloatMaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new FloatMaskMax(res);
        }

        @ForceInline
        @Override
        public final
        FloatVectorMax toVector() {
            return (FloatVectorMax) super.toVectorTemplate();  // specialize
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
        FloatMaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (FloatMaskMax) VectorSupport.indexPartiallyInUpperRange(
                FloatMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (FloatMaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public FloatMaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public FloatMaskMax compress() {
            return (FloatMaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                FloatVectorMax.class, FloatMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public FloatMaskMax and(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMaskMax m = (FloatMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, FloatMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public FloatMaskMax or(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMaskMax m = (FloatMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, FloatMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public FloatMaskMax xor(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMaskMax m = (FloatMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, FloatMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(FloatMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((FloatMaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((FloatMaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static FloatMaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(FloatMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final FloatMaskMax  TRUE_MASK = new FloatMaskMax(true);
        private static final FloatMaskMax FALSE_MASK = new FloatMaskMax(false);

    }

    // Shuffle

    static final class FloatShuffleMax extends AbstractShuffle<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        FloatShuffleMax(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        FloatShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        FloatShuffleMax(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        int[] indices() {
            return (int[])getPayload();
        }

        @Override
        @ForceInline
        public FloatSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Integer.MAX_VALUE);
            assert(Integer.MIN_VALUE <= -VLENGTH);
        }
        static final FloatShuffleMax IOTA = new FloatShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public FloatVectorMax toVector() {
            return (FloatVectorMax) toBitsVector().castShape(vspecies(), 0);
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
        public final FloatMaskMax laneIsValid() {
            return (FloatMaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final FloatShuffleMax rearrange(VectorShuffle<Float> shuffle) {
            FloatShuffleMax concreteShuffle = (FloatShuffleMax) shuffle;
            return (FloatShuffleMax) toBitsVector().rearrange(concreteShuffle.cast(IntVector.SPECIES_MAX))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final FloatShuffleMax wrapIndexes() {
            IntVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (FloatShuffleMax) v.toShuffle(vspecies(), false);
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
    FloatVector fromArray0(float[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, VectorMask<Float> m, int offsetInRange) {
        return super.fromArray0Template(FloatMaskMax.class, a, offset, (FloatMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        return super.fromArray0Template(FloatMaskMax.class, a, offset, indexMap, mapOffset, (FloatMaskMax) m);
    }



    @ForceInline
    @Override
    final
    FloatVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m, int offsetInRange) {
        return super.fromMemorySegment0Template(FloatMaskMax.class, ms, offset, (FloatMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, VectorMask<Float> m) {
        super.intoArray0Template(FloatMaskMax.class, a, offset, (FloatMaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        super.intoArray0Template(FloatMaskMax.class, a, offset, indexMap, mapOffset, (FloatMaskMax) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m) {
        super.intoMemorySegment0Template(FloatMaskMax.class, ms, offset, (FloatMaskMax) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

