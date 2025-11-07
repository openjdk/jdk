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
final class HalffloatMaxVector extends HalffloatVector {
    static final HalffloatSpecies VSPECIES =
        (HalffloatSpecies) HalffloatVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<HalffloatMaxVector> VCLASS = HalffloatMaxVector.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> CTYPE = short.class; // carrier type used by the JVM

    static final Class<Float16> ETYPE = Float16.class; // vector element type used by the JVM

    static final int VECTOR_OPER_TYPE = VECTOR_TYPE_FP16;

    HalffloatMaxVector(short[] v) {
        super(v);
    }

    // For compatibility as HalffloatMaxVector::new,
    // stored into species.vectorFactory.
    HalffloatMaxVector(Object v) {
        this((short[]) v);
    }

    static final HalffloatMaxVector ZERO = new HalffloatMaxVector(new short[VLENGTH]);
    static final HalffloatMaxVector IOTA = new HalffloatMaxVector(VSPECIES.iotaArray());

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
    public HalffloatSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    final Class<Short> carrierType() { return CTYPE; }

    @ForceInline
    @Override
    public final Class<Float16> elementType() { return ETYPE; }

    @ForceInline
    @Override
    public final int elementSize() { return Float16.SIZE; }

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
    short[] vec() {
        return (short[])getPayload();
    }

    // Virtualized constructors

    @Override
    @ForceInline
    public final HalffloatMaxVector broadcast(short e) {
        return (HalffloatMaxVector) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final HalffloatMaxVector broadcast(long e) {
        return (HalffloatMaxVector) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    HalffloatMaxMask maskFromArray(boolean[] bits) {
        return new HalffloatMaxMask(bits);
    }

    @Override
    @ForceInline
    HalffloatMaxShuffle iotaShuffle() { return HalffloatMaxShuffle.IOTA; }

    @Override
    @ForceInline
    HalffloatMaxShuffle iotaShuffle(int start, int step, boolean wrap) {
        return (HalffloatMaxShuffle) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    HalffloatMaxShuffle shuffleFromArray(int[] indices, int i) { return new HalffloatMaxShuffle(indices, i); }

    @Override
    @ForceInline
    HalffloatMaxShuffle shuffleFromOp(IntUnaryOperator fn) { return new HalffloatMaxShuffle(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    HalffloatMaxVector vectorFactory(short[] vec) {
        return new HalffloatMaxVector(vec);
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
    HalffloatMaxVector uOp(FUnOp f) {
        return (HalffloatMaxVector) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    HalffloatMaxVector uOp(VectorMask<Float16> m, FUnOp f) {
        return (HalffloatMaxVector)
            super.uOpTemplate((HalffloatMaxMask)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    HalffloatMaxVector bOp(Vector<Float16> v, FBinOp f) {
        return (HalffloatMaxVector) super.bOpTemplate((HalffloatMaxVector)v, f);  // specialize
    }

    @ForceInline
    final @Override
    HalffloatMaxVector bOp(Vector<Float16> v,
                     VectorMask<Float16> m, FBinOp f) {
        return (HalffloatMaxVector)
            super.bOpTemplate((HalffloatMaxVector)v, (HalffloatMaxMask)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    HalffloatMaxVector tOp(Vector<Float16> v1, Vector<Float16> v2, FTriOp f) {
        return (HalffloatMaxVector)
            super.tOpTemplate((HalffloatMaxVector)v1, (HalffloatMaxVector)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    HalffloatMaxVector tOp(Vector<Float16> v1, Vector<Float16> v2,
                     VectorMask<Float16> m, FTriOp f) {
        return (HalffloatMaxVector)
            super.tOpTemplate((HalffloatMaxVector)v1, (HalffloatMaxVector)v2,
                              (HalffloatMaxMask)m, f);  // specialize
    }

    @ForceInline
    final @Override
    short rOp(short v, VectorMask<Float16> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Float16,F> conv,
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
    public HalffloatMaxVector lanewise(Unary op) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector lanewise(Unary op, VectorMask<Float16> m) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op, HalffloatMaxMask.class, (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector lanewise(Binary op, Vector<Float16> v) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector lanewise(Binary op, Vector<Float16> v, VectorMask<Float16> m) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op, HalffloatMaxMask.class, v, (HalffloatMaxMask) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    HalffloatMaxVector
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    HalffloatMaxVector
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2, VectorMask<Float16> m) {
        return (HalffloatMaxVector) super.lanewiseTemplate(op, HalffloatMaxMask.class, v1, v2, (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    HalffloatMaxVector addIndex(int scale) {
        return (HalffloatMaxVector) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final short reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final short reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Float16> m) {
        return super.reduceLanesTemplate(op, HalffloatMaxMask.class, (HalffloatMaxMask) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Float16> m) {
        return (long) super.reduceLanesTemplate(op, HalffloatMaxMask.class, (HalffloatMaxMask) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final HalffloatMaxShuffle toShuffle() {
        return (HalffloatMaxShuffle) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final HalffloatMaxMask test(Test op) {
        return super.testTemplate(HalffloatMaxMask.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final HalffloatMaxMask test(Test op, VectorMask<Float16> m) {
        return super.testTemplate(HalffloatMaxMask.class, op, (HalffloatMaxMask) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final HalffloatMaxMask compare(Comparison op, Vector<Float16> v) {
        return super.compareTemplate(HalffloatMaxMask.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final HalffloatMaxMask compare(Comparison op, short s) {
        return super.compareTemplate(HalffloatMaxMask.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final HalffloatMaxMask compare(Comparison op, long s) {
        return super.compareTemplate(HalffloatMaxMask.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final HalffloatMaxMask compare(Comparison op, Vector<Float16> v, VectorMask<Float16> m) {
        return super.compareTemplate(HalffloatMaxMask.class, op, v, (HalffloatMaxMask) m);
    }


    @Override
    @ForceInline
    public HalffloatMaxVector blend(Vector<Float16> v, VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.blendTemplate(HalffloatMaxMask.class,
                                (HalffloatMaxVector) v,
                                (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector slice(int origin, Vector<Float16> v) {
        return (HalffloatMaxVector) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector slice(int origin) {
        return (HalffloatMaxVector) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector unslice(int origin, Vector<Float16> w, int part) {
        return (HalffloatMaxVector) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector unslice(int origin, Vector<Float16> w, int part, VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.unsliceTemplate(HalffloatMaxMask.class,
                                  origin, w, part,
                                  (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector unslice(int origin) {
        return (HalffloatMaxVector) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector rearrange(VectorShuffle<Float16> s) {
        return (HalffloatMaxVector)
            super.rearrangeTemplate(HalffloatMaxShuffle.class,
                                    (HalffloatMaxShuffle) s);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector rearrange(VectorShuffle<Float16> shuffle,
                                  VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.rearrangeTemplate(HalffloatMaxShuffle.class,
                                    HalffloatMaxMask.class,
                                    (HalffloatMaxShuffle) shuffle,
                                    (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector rearrange(VectorShuffle<Float16> s,
                                  Vector<Float16> v) {
        return (HalffloatMaxVector)
            super.rearrangeTemplate(HalffloatMaxShuffle.class,
                                    (HalffloatMaxShuffle) s,
                                    (HalffloatMaxVector) v);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector compress(VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.compressTemplate(HalffloatMaxMask.class,
                                   (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector expand(VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.expandTemplate(HalffloatMaxMask.class,
                                   (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector selectFrom(Vector<Float16> v) {
        return (HalffloatMaxVector)
            super.selectFromTemplate((HalffloatMaxVector) v);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector selectFrom(Vector<Float16> v,
                                   VectorMask<Float16> m) {
        return (HalffloatMaxVector)
            super.selectFromTemplate((HalffloatMaxVector) v,
                                     HalffloatMaxMask.class, (HalffloatMaxMask) m);  // specialize
    }

    @Override
    @ForceInline
    public HalffloatMaxVector selectFrom(Vector<Float16> v1,
                                   Vector<Float16> v2) {
        return (HalffloatMaxVector)
            super.selectFromTemplate((HalffloatMaxVector) v1, (HalffloatMaxVector) v2);  // specialize
    }

    @ForceInline
    @Override
    public short lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        short bits = laneHelper(i);
        return bits;
    }

    @ForceInline
    public short laneHelper(int i) {
        return (short) VectorSupport.extract(
                     VCLASS, CTYPE, VECTOR_OPER_TYPE, VLENGTH,
                     this, i,
                     (vec, ix) -> {
                     short[] vecarr = vec.vec();
                     return vecarr[ix];
                     });
    }

    @ForceInline
    @Override
    public HalffloatMaxVector withLane(int i, short e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public HalffloatMaxVector withLaneHelper(int i, short e) {
        return VectorSupport.insert(
                                VCLASS, CTYPE, VECTOR_OPER_TYPE, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    short[] res = v.vec().clone();
                                    res[ix] = (short)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class HalffloatMaxMask extends AbstractMask<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> CTYPE = short.class; // used by the JVM

        HalffloatMaxMask(boolean[] bits) {
            this(bits, 0);
        }

        HalffloatMaxMask(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        HalffloatMaxMask(boolean val) {
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
        public HalffloatSpecies vspecies() {
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
        HalffloatMaxMask uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new HalffloatMaxMask(res);
        }

        @Override
        HalffloatMaxMask bOp(VectorMask<Float16> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((HalffloatMaxMask)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new HalffloatMaxMask(res);
        }

        @ForceInline
        @Override
        public final
        HalffloatMaxVector toVector() {
            return (HalffloatMaxVector) super.toVectorTemplate();  // specialize
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
        HalffloatMaxMask indexPartiallyInUpperRange(long offset, long limit) {
            return (HalffloatMaxMask) VectorSupport.indexPartiallyInUpperRange(
                HalffloatMaxMask.class, CTYPE, VECTOR_OPER_TYPE, VLENGTH, offset, limit,
                (o, l) -> (HalffloatMaxMask) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public HalffloatMaxMask not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public HalffloatMaxMask compress() {
            return (HalffloatMaxMask)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                HalffloatMaxVector.class, HalffloatMaxMask.class, CTYPE, VECTOR_OPER_TYPE, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT,
                Float16.float16ToShortBits(Float16.valueOf(m1.trueCount()))));
        }


        // Binary operations

        @Override
        @ForceInline
        public HalffloatMaxMask and(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            HalffloatMaxMask m = (HalffloatMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, HalffloatMaxMask.class, null, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public HalffloatMaxMask or(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            HalffloatMaxMask m = (HalffloatMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, HalffloatMaxMask.class, null, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public HalffloatMaxMask xor(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            HalffloatMaxMask m = (HalffloatMaxMask)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, HalffloatMaxMask.class, null, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, HalffloatMaxMask.class, short.class,
                                                            VECTOR_OPER_TYPE, VLENGTH, this,
                                                            (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, HalffloatMaxMask.class, short.class,
                                                            VECTOR_OPER_TYPE, VLENGTH, this,
                                                            (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, HalffloatMaxMask.class, short.class,
                                                            VECTOR_OPER_TYPE, VLENGTH, this,
                                                            (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, HalffloatMaxMask.class, short.class,
                                                      VECTOR_OPER_TYPE, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(HalffloatMaxMask.class, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, HalffloatMaxMask.class, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> anyTrueHelper(((HalffloatMaxMask)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, HalffloatMaxMask.class, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> allTrueHelper(((HalffloatMaxMask)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static HalffloatMaxMask maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(HalffloatMaxMask.class, short.class, VECTOR_OPER_TYPE, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final HalffloatMaxMask  TRUE_MASK = new HalffloatMaxMask(true);
        private static final HalffloatMaxMask FALSE_MASK = new HalffloatMaxMask(false);

    }

    // Shuffle

    static final class HalffloatMaxShuffle extends AbstractShuffle<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> CTYPE = short.class; // used by the JVM

        HalffloatMaxShuffle(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        HalffloatMaxShuffle(int[] indices, int i) {
            this(prepare(indices, i));
        }

        HalffloatMaxShuffle(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        short[] indices() {
            return (short[])getPayload();
        }

        @Override
        @ForceInline
        public HalffloatSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Short.MAX_VALUE);
            assert(Short.MIN_VALUE <= -VLENGTH);
        }
        static final HalffloatMaxShuffle IOTA = new HalffloatMaxShuffle(IDENTITY);

        @Override
        @ForceInline
        public HalffloatMaxVector toVector() {
            return (HalffloatMaxVector) toBitsVector().castShape(vspecies(), 0);
        }

        @Override
        @ForceInline
        ShortMaxVector toBitsVector() {
            return (ShortMaxVector) super.toBitsVectorTemplate();
        }

        @Override
        ShortMaxVector toBitsVector0() {
            return ((ShortMaxVector) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            VectorSpecies<Integer> species = IntVector.SPECIES_MAX;
            Vector<Short> v = toBitsVector();
            v.convertShape(VectorOperators.S2I, species, 0)
                    .reinterpretAsInts()
                    .intoArray(a, offset);
            v.convertShape(VectorOperators.S2I, species, 1)
                    .reinterpretAsInts()
                    .intoArray(a, offset + species.length());
        }

        @Override
        @ForceInline
        public void intoMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            VectorSpecies<Integer> species = IntVector.SPECIES_MAX;
            Vector<Short> v = toBitsVector();
            v.convertShape(VectorOperators.S2I, species, 0)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset, bo);
            v.convertShape(VectorOperators.S2I, species, 1)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset + species.vectorByteSize(), bo);
         }

        @Override
        @ForceInline
        public final HalffloatMaxMask laneIsValid() {
            return (HalffloatMaxMask) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final HalffloatMaxShuffle rearrange(VectorShuffle<Float16> shuffle) {
            HalffloatMaxShuffle concreteShuffle = (HalffloatMaxShuffle) shuffle;
            return (HalffloatMaxShuffle) toBitsVector().rearrange(concreteShuffle.cast(ShortVector.SPECIES_MAX))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final HalffloatMaxShuffle wrapIndexes() {
            ShortMaxVector v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortMaxVector) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortMaxVector) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (HalffloatMaxShuffle) v.toShuffle(vspecies(), false);
        }

        private static short[] prepare(int[] indices, int offset) {
            short[] a = new short[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = indices[offset + i];
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (short)si;
            }
            return a;
        }

        private static short[] prepare(IntUnaryOperator f) {
            short[] a = new short[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = f.applyAsInt(i);
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (short)si;
            }
            return a;
        }

        private static boolean indicesInRange(short[] indices) {
            int length = indices.length;
            for (short si : indices) {
                if (si >= (short)length || si < (short)(-length)) {
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
    HalffloatVector fromArray0(short[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    HalffloatVector fromArray0(short[] a, int offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromArray0Template(HalffloatMaxMask.class, a, offset, (HalffloatMaxMask) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    HalffloatVector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float16> m) {
        return super.fromArray0Template(HalffloatMaxMask.class, a, offset, indexMap, mapOffset, (HalffloatMaxMask) m);
    }

    @ForceInline
    @Override
    final
    HalffloatVector fromCharArray0(char[] a, int offset) {
        return super.fromCharArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    HalffloatVector fromCharArray0(char[] a, int offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromCharArray0Template(HalffloatMaxMask.class, a, offset, (HalffloatMaxMask) m, offsetInRange);  // specialize
    }


    @ForceInline
    @Override
    final
    HalffloatVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    HalffloatVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromMemorySegment0Template(HalffloatMaxMask.class, ms, offset, (HalffloatMaxMask) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(short[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(short[] a, int offset, VectorMask<Float16> m) {
        super.intoArray0Template(HalffloatMaxMask.class, a, offset, (HalffloatMaxMask) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m) {
        super.intoMemorySegment0Template(HalffloatMaxMask.class, ms, offset, (HalffloatMaxMask) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Float16> m) {
        super.intoCharArray0Template(HalffloatMaxMask.class, a, offset, (HalffloatMaxMask) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

