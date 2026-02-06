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
final class FloatVector512 extends FloatVector {
    static final FloatSpecies VSPECIES =
        (FloatSpecies) FloatVector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<FloatVector512> VCLASS = FloatVector512.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Float> ETYPE = float.class; // used by the JVM

    FloatVector512(float[] v) {
        super(v);
    }

    // For compatibility as FloatVector512::new,
    // stored into species.vectorFactory.
    FloatVector512(Object v) {
        this((float[]) v);
    }

    static final FloatVector512 ZERO = new FloatVector512(new float[VLENGTH]);
    static final FloatVector512 IOTA = new FloatVector512(VSPECIES.iotaArray());

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
    public final FloatVector512 broadcast(float e) {
        return (FloatVector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final FloatVector512 broadcast(long e) {
        return (FloatVector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    FloatMask512 maskFromArray(boolean[] bits) {
        return new FloatMask512(bits);
    }

    @Override
    @ForceInline
    FloatShuffle512 iotaShuffle() { return FloatShuffle512.IOTA; }

    @Override
    @ForceInline
    FloatShuffle512 iotaShuffle(int start, int step, boolean wrap) {
        return (FloatShuffle512) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    FloatShuffle512 shuffleFromArray(int[] indices, int i) { return new FloatShuffle512(indices, i); }

    @Override
    @ForceInline
    FloatShuffle512 shuffleFromOp(IntUnaryOperator fn) { return new FloatShuffle512(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    FloatVector512 vectorFactory(float[] vec) {
        return new FloatVector512(vec);
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
    FloatVector512 uOp(FUnOp f) {
        return (FloatVector512) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector512 uOp(VectorMask<Float> m, FUnOp f) {
        return (FloatVector512)
            super.uOpTemplate((FloatMask512)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    FloatVector512 bOp(Vector<Float> v, FBinOp f) {
        return (FloatVector512) super.bOpTemplate((FloatVector512)v, f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector512 bOp(Vector<Float> v,
                     VectorMask<Float> m, FBinOp f) {
        return (FloatVector512)
            super.bOpTemplate((FloatVector512)v, (FloatMask512)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    FloatVector512 tOp(Vector<Float> v1, Vector<Float> v2, FTriOp f) {
        return (FloatVector512)
            super.tOpTemplate((FloatVector512)v1, (FloatVector512)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    FloatVector512 tOp(Vector<Float> v1, Vector<Float> v2,
                     VectorMask<Float> m, FTriOp f) {
        return (FloatVector512)
            super.tOpTemplate((FloatVector512)v1, (FloatVector512)v2,
                              (FloatMask512)m, f);  // specialize
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
    public FloatVector512 lanewise(Unary op) {
        return (FloatVector512) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 lanewise(Unary op, VectorMask<Float> m) {
        return (FloatVector512) super.lanewiseTemplate(op, FloatMask512.class, (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 lanewise(Binary op, Vector<Float> v) {
        return (FloatVector512) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 lanewise(Binary op, Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector512) super.lanewiseTemplate(op, FloatMask512.class, v, (FloatMask512) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    FloatVector512
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2) {
        return (FloatVector512) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector512
    lanewise(Ternary op, Vector<Float> v1, Vector<Float> v2, VectorMask<Float> m) {
        return (FloatVector512) super.lanewiseTemplate(op, FloatMask512.class, v1, v2, (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    FloatVector512 addIndex(int scale) {
        return (FloatVector512) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, FloatMask512.class, (FloatMask512) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, FloatMask512.class, (FloatMask512) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final FloatShuffle512 toShuffle() {
        return (FloatShuffle512) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final FloatMask512 test(Test op) {
        return super.testTemplate(FloatMask512.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask512 test(Test op, VectorMask<Float> m) {
        return super.testTemplate(FloatMask512.class, op, (FloatMask512) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final FloatMask512 compare(Comparison op, Vector<Float> v) {
        return super.compareTemplate(FloatMask512.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask512 compare(Comparison op, float s) {
        return super.compareTemplate(FloatMask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask512 compare(Comparison op, long s) {
        return super.compareTemplate(FloatMask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final FloatMask512 compare(Comparison op, Vector<Float> v, VectorMask<Float> m) {
        return super.compareTemplate(FloatMask512.class, op, v, (FloatMask512) m);
    }


    @Override
    @ForceInline
    public FloatVector512 blend(Vector<Float> v, VectorMask<Float> m) {
        return (FloatVector512)
            super.blendTemplate(FloatMask512.class,
                                (FloatVector512) v,
                                (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 slice(int origin, Vector<Float> v) {
        return (FloatVector512) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 slice(int origin) {
        return (FloatVector512) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 unslice(int origin, Vector<Float> w, int part) {
        return (FloatVector512) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 unslice(int origin, Vector<Float> w, int part, VectorMask<Float> m) {
        return (FloatVector512)
            super.unsliceTemplate(FloatMask512.class,
                                  origin, w, part,
                                  (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 unslice(int origin) {
        return (FloatVector512) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 rearrange(VectorShuffle<Float> s) {
        return (FloatVector512)
            super.rearrangeTemplate(FloatShuffle512.class,
                                    (FloatShuffle512) s);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 rearrange(VectorShuffle<Float> shuffle,
                                  VectorMask<Float> m) {
        return (FloatVector512)
            super.rearrangeTemplate(FloatShuffle512.class,
                                    FloatMask512.class,
                                    (FloatShuffle512) shuffle,
                                    (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 rearrange(VectorShuffle<Float> s,
                                  Vector<Float> v) {
        return (FloatVector512)
            super.rearrangeTemplate(FloatShuffle512.class,
                                    (FloatShuffle512) s,
                                    (FloatVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 compress(VectorMask<Float> m) {
        return (FloatVector512)
            super.compressTemplate(FloatMask512.class,
                                   (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 expand(VectorMask<Float> m) {
        return (FloatVector512)
            super.expandTemplate(FloatMask512.class,
                                   (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 selectFrom(Vector<Float> v) {
        return (FloatVector512)
            super.selectFromTemplate((FloatVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 selectFrom(Vector<Float> v,
                                   VectorMask<Float> m) {
        return (FloatVector512)
            super.selectFromTemplate((FloatVector512) v,
                                     FloatMask512.class, (FloatMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public FloatVector512 selectFrom(Vector<Float> v1,
                                   Vector<Float> v2) {
        return (FloatVector512)
            super.selectFromTemplate((FloatVector512) v1, (FloatVector512) v2);  // specialize
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
            case 4: bits = laneHelper(4); break;
            case 5: bits = laneHelper(5); break;
            case 6: bits = laneHelper(6); break;
            case 7: bits = laneHelper(7); break;
            case 8: bits = laneHelper(8); break;
            case 9: bits = laneHelper(9); break;
            case 10: bits = laneHelper(10); break;
            case 11: bits = laneHelper(11); break;
            case 12: bits = laneHelper(12); break;
            case 13: bits = laneHelper(13); break;
            case 14: bits = laneHelper(14); break;
            case 15: bits = laneHelper(15); break;
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
    public FloatVector512 withLane(int i, float e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            case 4: return withLaneHelper(4, e);
            case 5: return withLaneHelper(5, e);
            case 6: return withLaneHelper(6, e);
            case 7: return withLaneHelper(7, e);
            case 8: return withLaneHelper(8, e);
            case 9: return withLaneHelper(9, e);
            case 10: return withLaneHelper(10, e);
            case 11: return withLaneHelper(11, e);
            case 12: return withLaneHelper(12, e);
            case 13: return withLaneHelper(13, e);
            case 14: return withLaneHelper(14, e);
            case 15: return withLaneHelper(15, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public FloatVector512 withLaneHelper(int i, float e) {
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

    static final class FloatMask512 extends AbstractMask<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Float> ETYPE = float.class; // used by the JVM

        FloatMask512(boolean[] bits) {
            this(bits, 0);
        }

        FloatMask512(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        FloatMask512(boolean val) {
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
        FloatMask512 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new FloatMask512(res);
        }

        @Override
        FloatMask512 bOp(VectorMask<Float> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((FloatMask512)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new FloatMask512(res);
        }

        @ForceInline
        @Override
        public final
        FloatVector512 toVector() {
            return (FloatVector512) super.toVectorTemplate();  // specialize
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
        FloatMask512 indexPartiallyInUpperRange(long offset, long limit) {
            return (FloatMask512) VectorSupport.indexPartiallyInUpperRange(
                FloatMask512.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (FloatMask512) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public FloatMask512 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public FloatMask512 compress() {
            return (FloatMask512)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                FloatVector512.class, FloatMask512.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public FloatMask512 and(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask512 m = (FloatMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, FloatMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public FloatMask512 or(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask512 m = (FloatMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, FloatMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public FloatMask512 xor(VectorMask<Float> mask) {
            Objects.requireNonNull(mask);
            FloatMask512 m = (FloatMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, FloatMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(FloatMask512.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((FloatMask512)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((FloatMask512)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static FloatMask512 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(FloatMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final FloatMask512  TRUE_MASK = new FloatMask512(true);
        private static final FloatMask512 FALSE_MASK = new FloatMask512(false);

    }

    // Shuffle

    static final class FloatShuffle512 extends AbstractShuffle<Float> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Integer> ETYPE = int.class; // used by the JVM

        FloatShuffle512(int[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        FloatShuffle512(int[] indices, int i) {
            this(prepare(indices, i));
        }

        FloatShuffle512(IntUnaryOperator fn) {
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
        static final FloatShuffle512 IOTA = new FloatShuffle512(IDENTITY);

        @Override
        @ForceInline
        public FloatVector512 toVector() {
            return (FloatVector512) toBitsVector().castShape(vspecies(), 0);
        }

        @Override
        @ForceInline
        IntVector512 toBitsVector() {
            return (IntVector512) super.toBitsVectorTemplate();
        }

        @Override
        IntVector512 toBitsVector0() {
            return ((IntVector512) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final FloatMask512 laneIsValid() {
            return (FloatMask512) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final FloatShuffle512 rearrange(VectorShuffle<Float> shuffle) {
            FloatShuffle512 concreteShuffle = (FloatShuffle512) shuffle;
            return (FloatShuffle512) toBitsVector().rearrange(concreteShuffle.cast(IntVector.SPECIES_512))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final FloatShuffle512 wrapIndexes() {
            IntVector512 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (IntVector512) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (IntVector512) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (FloatShuffle512) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(FloatMask512.class, a, offset, (FloatMask512) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    FloatVector fromArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        return super.fromArray0Template(FloatMask512.class, a, offset, indexMap, mapOffset, (FloatMask512) m);
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
        return super.fromMemorySegment0Template(FloatMask512.class, ms, offset, (FloatMask512) m, offsetInRange);  // specialize
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
        super.intoArray0Template(FloatMask512.class, a, offset, (FloatMask512) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(float[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float> m) {
        super.intoArray0Template(FloatMask512.class, a, offset, indexMap, mapOffset, (FloatMask512) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float> m) {
        super.intoMemorySegment0Template(FloatMask512.class, ms, offset, (FloatMask512) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

