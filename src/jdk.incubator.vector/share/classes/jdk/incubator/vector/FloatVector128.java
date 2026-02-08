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
final class FloatVector128 extends FloatVector {
    static final FloatSpecies VSPECIES =
        (FloatSpecies) FloatVector.SPECIES_128;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<FloatVector128> VCLASS = FloatVector128.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Float> ETYPE = float.class; // used by the JVM

    FloatVector128(float[] v) {
        super(v);
    }

    // For compatibility as FloatVector128::new,
    // stored into species.vectorFactory.
    FloatVector128(Object v) {
        this((float[]) v);
    }

    static final FloatVector128 ZERO = new FloatVector128(new float[VLENGTH]);
    static final FloatVector128 IOTA = new FloatVector128(VSPECIES.iotaArray());

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
    public final FloatVector128 broadcast(float e) {
        return (FloatVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final FloatVector128 broadcast(long e) {
        return (FloatVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    FloatMask128 maskFromArray(boolean[] bits) {
        return new FloatMask128(bits);
    }

    @Override
    @ForceInline
    FloatShuffle128 iotaShuffle() { return FloatShuffle128.IOTA; }

    @Override
    @ForceInline
    FloatShuffle128 iotaShuffle(int start, int step, boolean wrap) {
        return (FloatShuffle128) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    FloatShuffle128 shuffleFromArray(int[] indices, int i) { return new FloatShuffle128(indices, i); }

    @Override
    @ForceInline
    FloatShuffle128 shuffleFromOp(IntUnaryOperator fn) { return new FloatShuffle128(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    FloatVector128 vectorFactory(float[] vec) {
        return new FloatVector128(vec);
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
    FloatVector128 uOp(FUnOp f) {
        return (FloatVector128) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector128 uOp(VectorMask<Float> m, FUnOp f) {
        return (FloatVector128)
            super.uOpTemplate((FloatMask128)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    FloatVector128 bOp(Vector<Float> v, FBinOp f) {
        return (FloatVector128) super.bOpTemplate((FloatVector128)v, f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector128 bOp(Vector<Float> v,
                     VectorMask<Float> m, FBinOp f) {
        return (FloatVector128)
            super.bOpTemplate((FloatVector128)v, (FloatMask128)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    FloatVector128 tOp(Vector<Float> v1, Vector<Float> v2, FTriOp f) {
        return (FloatVector128)
            super.tOpTemplate((FloatVector128)v1, (FloatVector128)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector128 tOp(Vector<Float> v1, Vector<Float> v2,
                     VectorMask<Float> m, FTriOp f) {
        return (FloatVector128)
            super.tOpTemplate((FloatVector128)v1, (FloatVector128)v2,
                              (FloatMask128)m, f);  // specialize
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
    public FloatVector128 lanewise(Unary op) {
        return (FloatVector128) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 lanewise(Unary op, VectorMask<Float> m) {
        return (FloatVector128) super.lanewiseTemplate(op, FloatMask128.class, (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 lanewise(Binary op, Vector<Float> v) {
        return (FloatVector128) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 lanewise(Binary op, Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector128) super.lanewiseTemplate(op, FloatMask128.class, v, (FloatMask128) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    FloatVector128
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2) {
        return (FloatVector128) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector128
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2, VectorMask<Float> m) {
        return (FloatVector128) super.lanewiseTemplate(op, FloatMask128.class, v1, v2, (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector128 addIndex(int scale) {
        return (FloatVector128) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, FloatMask128.class, (FloatMask128) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, FloatMask128.class, (FloatMask128) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final FloatShuffle128 toShuffle() {
        return (FloatShuffle128) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final FloatMask128 test(Test op) {
        return super.testTemplate(FloatMask128.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask128 test(Test op, VectorMask<Float> m) {
        return super.testTemplate(FloatMask128.class, op, (FloatMask128) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final FloatMask128 compare(Comparison op, Vector<Float> v) {
        return super.compareTemplate(FloatMask128.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask128 compare(Comparison op, float s) {
        return super.compareTemplate(FloatMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask128 compare(Comparison op, long s) {
        return super.compareTemplate(FloatMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask128 compare(Comparison op, Vector<Float> v, VectorMask<Float> m) {
        return super.compareTemplate(FloatMask128.class, op, v, (FloatMask128) m);
    }


    @Override
    @ForceInline
    public FloatVector128 blend(Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector128)
            super.blendTemplate(FloatMask128.class,
                                (FloatVector128) v,
                                (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 slice(int origin, Vector<Float> v) {
        return (FloatVector128) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 slice(int origin) {
        return (FloatVector128) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 unslice(int origin, Vector<Float> w, int part) {
        return (FloatVector128) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 unslice(int origin, Vector<Float> w, int part, VectorMask<Float> m) {
        return (FloatVector128)
            super.unsliceTemplate(FloatMask128.class,
                                  origin, w, part,
                                  (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 unslice(int origin) {
        return (FloatVector128) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 rearrange(VectorShuffle<Float> s) {
        return (FloatVector128)
            super.rearrangeTemplate(FloatShuffle128.class,
                                    (FloatShuffle128) s);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 rearrange(VectorShuffle<Float> shuffle,
                                  VectorMask<Float> m) {
        return (FloatVector128)
            super.rearrangeTemplate(FloatShuffle128.class,
                                    FloatMask128.class,
                                    (FloatShuffle128) shuffle,
                                    (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 rearrange(VectorShuffle<Float> s,
                                  Vector<Float> v) {
        return (FloatVector128)
            super.rearrangeTemplate(FloatShuffle128.class,
                                    (FloatShuffle128) s,
                                    (FloatVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 compress(VectorMask<Float> m) {
        return (FloatVector128)
            super.compressTemplate(FloatMask128.class,
                                   (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 expand(VectorMask<Float> m) {
        return (FloatVector128)
            super.expandTemplate(FloatMask128.class,
                                   (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 selectFrom(Vector<Float> v) {
        return (FloatVector128)
            super.selectFromTemplate((FloatVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 selectFrom(Vector<Float> v,
                                   VectorMask<Float> m) {
        return (FloatVector128)
            super.selectFromTemplate((FloatVector128) v,
                                     FloatMask128.class, (FloatMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector128 selectFrom(Vector<Float> v1,
                                   Vector<Float> v2) {
        return (FloatVector128)
            super.selectFromTemplate((FloatVector128) v1, (FloatVector128) v2);  // specialize
    }

    @ForceInline
    @Override
    public float lane(int i) {
        int bits;
        switch(i) {
            case 0: bits = laneHelper(0); break;
            case 1: bits = laneHelper(1); break;
            case 2: bits = laneHelper(2); break;
            case 3: bits = laneHelper(3); break;
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
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
    public FloatVector128 withLane(int i, float e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public FloatVector128 withLaneHelper(int i, float e) {
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

    static final class FloatMask128 extends AbstractMask<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Float> ETYPE = float.class; // used by the JVM

        FloatMask128(boolean[] bits) {
            this(bits, 0);
        }

        FloatMask128(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        FloatMask128(boolean val) {
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
        FloatMask128 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new FloatMask128(res);
        }

        @Override
        FloatMask128 bOp(VectorMask<Float> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((FloatMask128)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new FloatMask128(res);
        }

        @ForceInline
        @Override
        public final
        FloatVector128 toVector() {
            return (FloatVector128) super.toVectorTemplate();  // specialize
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
        FloatMask128 indexPartiallyInUpperRange(long offset, long limit) {
            return (FloatMask128) VectorSupport.indexPartiallyInUpperRange(
                FloatMask128.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (FloatMask128) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public FloatMask128 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public FloatMask128 compress() {
            return (FloatMask128)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                FloatVector128.class, FloatMask128.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public FloatMask128 and(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask128 m = (FloatMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, FloatMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public FloatMask128 or(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask128 m = (FloatMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, FloatMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public FloatMask128 xor(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask128 m = (FloatMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, FloatMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(FloatMask128.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((FloatMask128)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((FloatMask128)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static FloatMask128 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(FloatMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final FloatMask128  TRUE_MASK = new FloatMask128(true);
        private static final FloatMask128 FALSE_MASK = new FloatMask128(false);

    }

    // Shuffle

    static final class FloatShuffle128 extends AbstractShuffle<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        FloatShuffle128(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        FloatShuffle128(int[] indices, int i) {
            this(prepare(indices, i));
        }

        FloatShuffle128(IntUnaryOperator fn) {
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
        static final FloatShuffle128 IOTA = new FloatShuffle128(IDENTITY);

        @Override
        @ForceInline
        public FloatVector128 toVector() {
            return (FloatVector128) toBitsVector().castShape(vspecies(), 0);
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
        public final FloatMask128 laneIsValid() {
            return (FloatMask128) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final FloatShuffle128 rearrange(VectorShuffle<Float> shuffle) {
            FloatShuffle128 concreteShuffle = (FloatShuffle128) shuffle;
            return (FloatShuffle128) toBitsVector().rearrange(concreteShuffle.cast(IntVector.SPECIES_128))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final FloatShuffle128 wrapIndexes() {
            IntVector128 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVector128) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVector128) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (FloatShuffle128) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(FloatMask128.class, a, offset, (FloatMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        return super.fromArray0Template(FloatMask128.class, a, offset, indexMap, mapOffset, (FloatMask128) m);
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
        return super.fromMemorySegment0Template(FloatMask128.class, ms, offset, (FloatMask128) m, offsetInRange);  // specialize
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
        super.intoArray0Template(FloatMask128.class, a, offset, (FloatMask128) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        super.intoArray0Template(FloatMask128.class, a, offset, indexMap, mapOffset, (FloatMask128) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m) {
        super.intoMemorySegment0Template(FloatMask128.class, ms, offset, (FloatMask128) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

