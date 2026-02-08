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
final class FloatVector64 extends FloatVector {
    static final FloatSpecies VSPECIES =
        (FloatSpecies) FloatVector.SPECIES_64;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<FloatVector64> VCLASS = FloatVector64.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Float> ETYPE = float.class; // used by the JVM

    FloatVector64(float[] v) {
        super(v);
    }

    // For compatibility as FloatVector64::new,
    // stored into species.vectorFactory.
    FloatVector64(Object v) {
        this((float[]) v);
    }

    static final FloatVector64 ZERO = new FloatVector64(new float[VLENGTH]);
    static final FloatVector64 IOTA = new FloatVector64(VSPECIES.iotaArray());

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
    public final FloatVector64 broadcast(float e) {
        return (FloatVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final FloatVector64 broadcast(long e) {
        return (FloatVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    FloatMask64 maskFromArray(boolean[] bits) {
        return new FloatMask64(bits);
    }

    @Override
    @ForceInline
    FloatShuffle64 iotaShuffle() { return FloatShuffle64.IOTA; }

    @Override
    @ForceInline
    FloatShuffle64 iotaShuffle(int start, int step, boolean wrap) {
        return (FloatShuffle64) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    FloatShuffle64 shuffleFromArray(int[] indices, int i) { return new FloatShuffle64(indices, i); }

    @Override
    @ForceInline
    FloatShuffle64 shuffleFromOp(IntUnaryOperator fn) { return new FloatShuffle64(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    FloatVector64 vectorFactory(float[] vec) {
        return new FloatVector64(vec);
    }

    @ForceInline
    final @Override
    ByteVector64 asByteVectorRaw() {
        return (ByteVector64) super.asByteVectorRawTemplate();  // specialize
    }

    @ForceInline
    final @Override
    AbstractVector<?> asVectorRaw(LaneType laneType) {
        return super.asVectorRawTemplate(laneType);  // specialize
    }

    // Unary operator

    @ForceInline
    final @Override
    FloatVector64 uOp(FUnOp f) {
        return (FloatVector64) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector64 uOp(VectorMask<Float> m, FUnOp f) {
        return (FloatVector64)
            super.uOpTemplate((FloatMask64)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    FloatVector64 bOp(Vector<Float> v, FBinOp f) {
        return (FloatVector64) super.bOpTemplate((FloatVector64)v, f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector64 bOp(Vector<Float> v,
                     VectorMask<Float> m, FBinOp f) {
        return (FloatVector64)
            super.bOpTemplate((FloatVector64)v, (FloatMask64)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    FloatVector64 tOp(Vector<Float> v1, Vector<Float> v2, FTriOp f) {
        return (FloatVector64)
            super.tOpTemplate((FloatVector64)v1, (FloatVector64)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector64 tOp(Vector<Float> v1, Vector<Float> v2,
                     VectorMask<Float> m, FTriOp f) {
        return (FloatVector64)
            super.tOpTemplate((FloatVector64)v1, (FloatVector64)v2,
                              (FloatMask64)m, f);  // specialize
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
    public FloatVector64 lanewise(Unary op) {
        return (FloatVector64) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 lanewise(Unary op, VectorMask<Float> m) {
        return (FloatVector64) super.lanewiseTemplate(op, FloatMask64.class, (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 lanewise(Binary op, Vector<Float> v) {
        return (FloatVector64) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 lanewise(Binary op, Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector64) super.lanewiseTemplate(op, FloatMask64.class, v, (FloatMask64) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    FloatVector64
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2) {
        return (FloatVector64) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector64
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2, VectorMask<Float> m) {
        return (FloatVector64) super.lanewiseTemplate(op, FloatMask64.class, v1, v2, (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector64 addIndex(int scale) {
        return (FloatVector64) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, FloatMask64.class, (FloatMask64) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, FloatMask64.class, (FloatMask64) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final FloatShuffle64 toShuffle() {
        return (FloatShuffle64) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final FloatMask64 test(Test op) {
        return super.testTemplate(FloatMask64.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask64 test(Test op, VectorMask<Float> m) {
        return super.testTemplate(FloatMask64.class, op, (FloatMask64) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final FloatMask64 compare(Comparison op, Vector<Float> v) {
        return super.compareTemplate(FloatMask64.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask64 compare(Comparison op, float s) {
        return super.compareTemplate(FloatMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask64 compare(Comparison op, long s) {
        return super.compareTemplate(FloatMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask64 compare(Comparison op, Vector<Float> v, VectorMask<Float> m) {
        return super.compareTemplate(FloatMask64.class, op, v, (FloatMask64) m);
    }


    @Override
    @ForceInline
    public FloatVector64 blend(Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector64)
            super.blendTemplate(FloatMask64.class,
                                (FloatVector64) v,
                                (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 slice(int origin, Vector<Float> v) {
        return (FloatVector64) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 slice(int origin) {
        return (FloatVector64) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 unslice(int origin, Vector<Float> w, int part) {
        return (FloatVector64) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 unslice(int origin, Vector<Float> w, int part, VectorMask<Float> m) {
        return (FloatVector64)
            super.unsliceTemplate(FloatMask64.class,
                                  origin, w, part,
                                  (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 unslice(int origin) {
        return (FloatVector64) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 rearrange(VectorShuffle<Float> s) {
        return (FloatVector64)
            super.rearrangeTemplate(FloatShuffle64.class,
                                    (FloatShuffle64) s);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 rearrange(VectorShuffle<Float> shuffle,
                                  VectorMask<Float> m) {
        return (FloatVector64)
            super.rearrangeTemplate(FloatShuffle64.class,
                                    FloatMask64.class,
                                    (FloatShuffle64) shuffle,
                                    (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 rearrange(VectorShuffle<Float> s,
                                  Vector<Float> v) {
        return (FloatVector64)
            super.rearrangeTemplate(FloatShuffle64.class,
                                    (FloatShuffle64) s,
                                    (FloatVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 compress(VectorMask<Float> m) {
        return (FloatVector64)
            super.compressTemplate(FloatMask64.class,
                                   (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 expand(VectorMask<Float> m) {
        return (FloatVector64)
            super.expandTemplate(FloatMask64.class,
                                   (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 selectFrom(Vector<Float> v) {
        return (FloatVector64)
            super.selectFromTemplate((FloatVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 selectFrom(Vector<Float> v,
                                   VectorMask<Float> m) {
        return (FloatVector64)
            super.selectFromTemplate((FloatVector64) v,
                                     FloatMask64.class, (FloatMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector64 selectFrom(Vector<Float> v1,
                                   Vector<Float> v2) {
        return (FloatVector64)
            super.selectFromTemplate((FloatVector64) v1, (FloatVector64) v2);  // specialize
    }

    @ForceInline
    @Override
    public float lane(int i) {
        int bits;
        switch(i) {
            case 0: bits = laneHelper(0); break;
            case 1: bits = laneHelper(1); break;
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
    public FloatVector64 withLane(int i, float e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public FloatVector64 withLaneHelper(int i, float e) {
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

    static final class FloatMask64 extends AbstractMask<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Float> ETYPE = float.class; // used by the JVM

        FloatMask64(boolean[] bits) {
            this(bits, 0);
        }

        FloatMask64(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        FloatMask64(boolean val) {
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
        FloatMask64 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new FloatMask64(res);
        }

        @Override
        FloatMask64 bOp(VectorMask<Float> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((FloatMask64)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new FloatMask64(res);
        }

        @ForceInline
        @Override
        public final
        FloatVector64 toVector() {
            return (FloatVector64) super.toVectorTemplate();  // specialize
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
        FloatMask64 indexPartiallyInUpperRange(long offset, long limit) {
            return (FloatMask64) VectorSupport.indexPartiallyInUpperRange(
                FloatMask64.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (FloatMask64) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public FloatMask64 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public FloatMask64 compress() {
            return (FloatMask64)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                FloatVector64.class, FloatMask64.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public FloatMask64 and(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask64 m = (FloatMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, FloatMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public FloatMask64 or(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask64 m = (FloatMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, FloatMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public FloatMask64 xor(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask64 m = (FloatMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, FloatMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(FloatMask64.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((FloatMask64)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((FloatMask64)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static FloatMask64 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(FloatMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final FloatMask64  TRUE_MASK = new FloatMask64(true);
        private static final FloatMask64 FALSE_MASK = new FloatMask64(false);

    }

    // Shuffle

    static final class FloatShuffle64 extends AbstractShuffle<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        FloatShuffle64(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        FloatShuffle64(int[] indices, int i) {
            this(prepare(indices, i));
        }

        FloatShuffle64(IntUnaryOperator fn) {
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
        static final FloatShuffle64 IOTA = new FloatShuffle64(IDENTITY);

        @Override
        @ForceInline
        public FloatVector64 toVector() {
            return (FloatVector64) toBitsVector().castShape(vspecies(), 0);
        }

        @Override
        @ForceInline
        IntVector64 toBitsVector() {
            return (IntVector64) super.toBitsVectorTemplate();
        }

        @Override
        IntVector64 toBitsVector0() {
            return ((IntVector64) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final FloatMask64 laneIsValid() {
            return (FloatMask64) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final FloatShuffle64 rearrange(VectorShuffle<Float> shuffle) {
            FloatShuffle64 concreteShuffle = (FloatShuffle64) shuffle;
            return (FloatShuffle64) toBitsVector().rearrange(concreteShuffle.cast(IntVector.SPECIES_64))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final FloatShuffle64 wrapIndexes() {
            IntVector64 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVector64) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVector64) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (FloatShuffle64) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(FloatMask64.class, a, offset, (FloatMask64) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        return super.fromArray0Template(FloatMask64.class, a, offset, indexMap, mapOffset, (FloatMask64) m);
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
        return super.fromMemorySegment0Template(FloatMask64.class, ms, offset, (FloatMask64) m, offsetInRange);  // specialize
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
        super.intoArray0Template(FloatMask64.class, a, offset, (FloatMask64) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        super.intoArray0Template(FloatMask64.class, a, offset, indexMap, mapOffset, (FloatMask64) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m) {
        super.intoMemorySegment0Template(FloatMask64.class, ms, offset, (FloatMask64) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

