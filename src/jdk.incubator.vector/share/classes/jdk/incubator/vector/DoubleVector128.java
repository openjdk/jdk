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
final class DoubleVector128 extends DoubleVector {
    static final DoubleSpecies VSPECIES =
        (DoubleSpecies) DoubleVector.SPECIES_128;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<DoubleVector128> VCLASS = DoubleVector128.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Double> ETYPE = double.class; // used by the JVM

    DoubleVector128(double[] v) {
        super(v);
    }

    // For compatibility as DoubleVector128::new,
    // stored into species.vectorFactory.
    DoubleVector128(Object v) {
        this((double[]) v);
    }

    static final DoubleVector128 ZERO = new DoubleVector128(new double[VLENGTH]);
    static final DoubleVector128 IOTA = new DoubleVector128(VSPECIES.iotaArray());

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
    public DoubleSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Double> elementType() { return double.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Double.SIZE; }

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
    double[] vec() {
        return (double[])getPayload();
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
    public final DoubleVector128 broadcast(double e) {
        return (DoubleVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final DoubleVector128 broadcast(long e) {
        return (DoubleVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    DoubleMask128 maskFromArray(boolean[] bits) {
        return new DoubleMask128(bits);
    }

    @Override
    @ForceInline
    DoubleShuffle128 iotaShuffle() { return DoubleShuffle128.IOTA; }

    @Override
    @ForceInline
    DoubleShuffle128 iotaShuffle(int start, int step, boolean wrap) {
        return (DoubleShuffle128) iotaShuffleTemplate(start, step, wrap);
    }

    @Override
    @ForceInline
    DoubleShuffle128 shuffleFromArray(int[] indices, int i) { return new DoubleShuffle128(indices, i); }

    @Override
    @ForceInline
    DoubleShuffle128 shuffleFromOp(IntUnaryOperator fn) { return new DoubleShuffle128(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    DoubleVector128 vectorFactory(double[] vec) {
        return new DoubleVector128(vec);
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
    DoubleVector128 uOp(FUnOp f) {
        return (DoubleVector128) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    DoubleVector128 uOp(VectorMask<Double> m, FUnOp f) {
        return (DoubleVector128)
            super.uOpTemplate((DoubleMask128)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    DoubleVector128 bOp(Vector<Double> v, FBinOp f) {
        return (DoubleVector128) super.bOpTemplate((DoubleVector128)v, f);  // specialize
    }

    @ForceInline
    final @Override
    DoubleVector128 bOp(Vector<Double> v,
                     VectorMask<Double> m, FBinOp f) {
        return (DoubleVector128)
            super.bOpTemplate((DoubleVector128)v, (DoubleMask128)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    DoubleVector128 tOp(Vector<Double> v1, Vector<Double> v2, FTriOp f) {
        return (DoubleVector128)
            super.tOpTemplate((DoubleVector128)v1, (DoubleVector128)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    DoubleVector128 tOp(Vector<Double> v1, Vector<Double> v2,
                     VectorMask<Double> m, FTriOp f) {
        return (DoubleVector128)
            super.tOpTemplate((DoubleVector128)v1, (DoubleVector128)v2,
                              (DoubleMask128)m, f);  // specialize
    }

    @ForceInline
    final @Override
    double rOp(double v, VectorMask<Double> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Double,F> conv,
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
    public DoubleVector128 lanewise(Unary op) {
        return (DoubleVector128) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 lanewise(Unary op, VectorMask<Double> m) {
        return (DoubleVector128) super.lanewiseTemplate(op, DoubleMask128.class, (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 lanewise(Binary op, Vector<Double> v) {
        return (DoubleVector128) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 lanewise(Binary op, Vector<Double> v, VectorMask<Double> m) {
        return (DoubleVector128) super.lanewiseTemplate(op, DoubleMask128.class, v, (DoubleMask128) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    DoubleVector128
    lanewise(Ternary op, Vector<Double> v1, Vector<Double> v2) {
        return (DoubleVector128) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    DoubleVector128
    lanewise(Ternary op, Vector<Double> v1, Vector<Double> v2, VectorMask<Double> m) {
        return (DoubleVector128) super.lanewiseTemplate(op, DoubleMask128.class, v1, v2, (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    DoubleVector128 addIndex(int scale) {
        return (DoubleVector128) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final double reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final double reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Double> m) {
        return super.reduceLanesTemplate(op, DoubleMask128.class, (DoubleMask128) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Double> m) {
        return (long) super.reduceLanesTemplate(op, DoubleMask128.class, (DoubleMask128) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final DoubleShuffle128 toShuffle() {
        return (DoubleShuffle128) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final DoubleMask128 test(Test op) {
        return super.testTemplate(DoubleMask128.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final DoubleMask128 test(Test op, VectorMask<Double> m) {
        return super.testTemplate(DoubleMask128.class, op, (DoubleMask128) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final DoubleMask128 compare(Comparison op, Vector<Double> v) {
        return super.compareTemplate(DoubleMask128.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final DoubleMask128 compare(Comparison op, double s) {
        return super.compareTemplate(DoubleMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final DoubleMask128 compare(Comparison op, long s) {
        return super.compareTemplate(DoubleMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final DoubleMask128 compare(Comparison op, Vector<Double> v, VectorMask<Double> m) {
        return super.compareTemplate(DoubleMask128.class, op, v, (DoubleMask128) m);
    }


    @Override
    @ForceInline
    public DoubleVector128 blend(Vector<Double> v, VectorMask<Double> m) {
        return (DoubleVector128)
            super.blendTemplate(DoubleMask128.class,
                                (DoubleVector128) v,
                                (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 slice(int origin, Vector<Double> v) {
        return (DoubleVector128) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 slice(int origin) {
        return (DoubleVector128) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 unslice(int origin, Vector<Double> w, int part) {
        return (DoubleVector128) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 unslice(int origin, Vector<Double> w, int part, VectorMask<Double> m) {
        return (DoubleVector128)
            super.unsliceTemplate(DoubleMask128.class,
                                  origin, w, part,
                                  (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 unslice(int origin) {
        return (DoubleVector128) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 rearrange(VectorShuffle<Double> s) {
        return (DoubleVector128)
            super.rearrangeTemplate(DoubleShuffle128.class,
                                    (DoubleShuffle128) s);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 rearrange(VectorShuffle<Double> shuffle,
                                  VectorMask<Double> m) {
        return (DoubleVector128)
            super.rearrangeTemplate(DoubleShuffle128.class,
                                    DoubleMask128.class,
                                    (DoubleShuffle128) shuffle,
                                    (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 rearrange(VectorShuffle<Double> s,
                                  Vector<Double> v) {
        return (DoubleVector128)
            super.rearrangeTemplate(DoubleShuffle128.class,
                                    (DoubleShuffle128) s,
                                    (DoubleVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 compress(VectorMask<Double> m) {
        return (DoubleVector128)
            super.compressTemplate(DoubleMask128.class,
                                   (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 expand(VectorMask<Double> m) {
        return (DoubleVector128)
            super.expandTemplate(DoubleMask128.class,
                                   (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 selectFrom(Vector<Double> v) {
        return (DoubleVector128)
            super.selectFromTemplate((DoubleVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 selectFrom(Vector<Double> v,
                                   VectorMask<Double> m) {
        return (DoubleVector128)
            super.selectFromTemplate((DoubleVector128) v,
                                     DoubleMask128.class, (DoubleMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public DoubleVector128 selectFrom(Vector<Double> v1,
                                   Vector<Double> v2) {
        return (DoubleVector128)
            super.selectFromTemplate((DoubleVector128) v1, (DoubleVector128) v2);  // specialize
    }

    @ForceInline
    @Override
    public double lane(int i) {
        long bits;
        switch(i) {
            case 0: bits = laneHelper(0); break;
            case 1: bits = laneHelper(1); break;
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return Double.longBitsToDouble(bits);
    }

    @ForceInline
    public long laneHelper(int i) {
        return (long) VectorSupport.extract(
                     VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                     this, i,
                     (vec, ix) -> {
                     double[] vecarr = vec.vec();
                     return (long)Double.doubleToRawLongBits(vecarr[ix]);
                     });
    }

    @ForceInline
    @Override
    public DoubleVector128 withLane(int i, double e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public DoubleVector128 withLaneHelper(int i, double e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)Double.doubleToRawLongBits(e),
                                (v, ix, bits) -> {
                                    double[] res = v.vec().clone();
                                    res[ix] = Double.longBitsToDouble((long)bits);
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class DoubleMask128 extends AbstractMask<Double> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Double> ETYPE = double.class; // used by the JVM

        DoubleMask128(boolean[] bits) {
            this(bits, 0);
        }

        DoubleMask128(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        DoubleMask128(boolean val) {
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
        public DoubleSpecies vspecies() {
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
        DoubleMask128 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new DoubleMask128(res);
        }

        @Override
        DoubleMask128 bOp(VectorMask<Double> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((DoubleMask128)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new DoubleMask128(res);
        }

        @ForceInline
        @Override
        public final
        DoubleVector128 toVector() {
            return (DoubleVector128) super.toVectorTemplate();  // specialize
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
        DoubleMask128 indexPartiallyInUpperRange(long offset, long limit) {
            return (DoubleMask128) VectorSupport.indexPartiallyInUpperRange(
                DoubleMask128.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (DoubleMask128) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public DoubleMask128 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public DoubleMask128 compress() {
            return (DoubleMask128)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                DoubleVector128.class, DoubleMask128.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public DoubleMask128 and(VectorMask<Double> mask) {
            Objects.requireNonNull(mask);
            DoubleMask128 m = (DoubleMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, DoubleMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public DoubleMask128 or(VectorMask<Double> mask) {
            Objects.requireNonNull(mask);
            DoubleMask128 m = (DoubleMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, DoubleMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public DoubleMask128 xor(VectorMask<Double> mask) {
            Objects.requireNonNull(mask);
            DoubleMask128 m = (DoubleMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, DoubleMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(DoubleMask128.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((DoubleMask128)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((DoubleMask128)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static DoubleMask128 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(DoubleMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final DoubleMask128  TRUE_MASK = new DoubleMask128(true);
        private static final DoubleMask128 FALSE_MASK = new DoubleMask128(false);

    }

    // Shuffle

    static final class DoubleShuffle128 extends AbstractShuffle<Double> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Long> ETYPE = long.class; // used by the JVM

        DoubleShuffle128(long[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        DoubleShuffle128(int[] indices, int i) {
            this(prepare(indices, i));
        }

        DoubleShuffle128(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        long[] indices() {
            return (long[])getPayload();
        }

        @Override
        @ForceInline
        public DoubleSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Long.MAX_VALUE);
            assert(Long.MIN_VALUE <= -VLENGTH);
        }
        static final DoubleShuffle128 IOTA = new DoubleShuffle128(IDENTITY);

        @Override
        @ForceInline
        public DoubleVector128 toVector() {
            return (DoubleVector128) toBitsVector().castShape(vspecies(), 0);
        }

        @Override
        @ForceInline
        LongVector128 toBitsVector() {
            return (LongVector128) super.toBitsVectorTemplate();
        }

        @Override
        LongVector128 toBitsVector0() {
            return ((LongVector128) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final DoubleMask128 laneIsValid() {
            return (DoubleMask128) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final DoubleShuffle128 rearrange(VectorShuffle<Double> shuffle) {
            DoubleShuffle128 concreteShuffle = (DoubleShuffle128) shuffle;
            return (DoubleShuffle128) toBitsVector().rearrange(concreteShuffle.cast(LongVector.SPECIES_128))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final DoubleShuffle128 wrapIndexes() {
            LongVector128 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (LongVector128) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (LongVector128) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (DoubleShuffle128) v.toShuffle(vspecies(), false);
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
    DoubleVector fromArray0(double[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    DoubleVector fromArray0(double[] a, int offset, VectorMask<Double> m, int offsetInRange) {
        return super.fromArray0Template(DoubleMask128.class, a, offset, (DoubleMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    DoubleVector fromArray0(double[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Double> m) {
        return super.fromArray0Template(DoubleMask128.class, a, offset, indexMap, mapOffset, (DoubleMask128) m);
    }



    @ForceInline
    @Override
    final
    DoubleVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    DoubleVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Double> m, int offsetInRange) {
        return super.fromMemorySegment0Template(DoubleMask128.class, ms, offset, (DoubleMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(double[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(double[] a, int offset, VectorMask<Double> m) {
        super.intoArray0Template(DoubleMask128.class, a, offset, (DoubleMask128) m);
    }

    @ForceInline
    @Override
    final
    void intoArray0(double[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Double> m) {
        super.intoArray0Template(DoubleMask128.class, a, offset, indexMap, mapOffset, (DoubleMask128) m);
    }


    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Double> m) {
        super.intoMemorySegment0Template(DoubleMask128.class, ms, offset, (DoubleMask128) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

