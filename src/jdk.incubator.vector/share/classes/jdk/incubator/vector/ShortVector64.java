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
final class ShortVector64 extends ShortVector {
    static final ShortSpecies VSPECIES =
        (ShortSpecies) ShortVector.SPECIES_64;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ShortVector64> VCLASS = ShortVector64.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> ETYPE = short.class; // used by the JVM

    ShortVector64(short[] v) {
        super(v);
    }

    // For compatibility as ShortVector64::new,
    // stored into species.vectorFactory.
    ShortVector64(Object v) {
        this((short[]) v);
    }

    static final ShortVector64 ZERO = new ShortVector64(new short[VLENGTH]);
    static final ShortVector64 IOTA = new ShortVector64(VSPECIES.iotaArray());

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
    public ShortSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Short> elementType() { return short.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Short.SIZE; }

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

    /*package-private*/
    @ForceInline
    final @Override
    int laneTypeOrdinal() {
        return LANE_TYPE_ORDINAL;
    }

    // Virtualized constructors

    @Override
    @ForceInline
    public final ShortVector64 broadcast(short e) {
        return (ShortVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ShortVector64 broadcast(long e) {
        return (ShortVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ShortMask64 maskFromArray(boolean[] bits) {
        return new ShortMask64(bits);
    }

    @Override
    @ForceInline
    ShortShuffle64 iotaShuffle() { return ShortShuffle64.IOTA; }

    @Override
    @ForceInline
    ShortShuffle64 iotaShuffle(int start, int step, boolean wrap) {
        return (ShortShuffle64) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    ShortShuffle64 shuffleFromArray(int[] indices, int i) { return new ShortShuffle64(indices, i); }

    @Override
    @ForceInline
    ShortShuffle64 shuffleFromOp(IntUnaryOperator fn) { return new ShortShuffle64(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ShortVector64 vectorFactory(short[] vec) {
        return new ShortVector64(vec);
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
    ShortVector64 uOp(FUnOp f) {
        return (ShortVector64) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector64 uOp(VectorMask<Short> m, FUnOp f) {
        return (ShortVector64)
            super.uOpTemplate((ShortMask64)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ShortVector64 bOp(Vector<Short> v, FBinOp f) {
        return (ShortVector64) super.bOpTemplate((ShortVector64)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector64 bOp(Vector<Short> v,
                     VectorMask<Short> m, FBinOp f) {
        return (ShortVector64)
            super.bOpTemplate((ShortVector64)v, (ShortMask64)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ShortVector64 tOp(Vector<Short> v1, Vector<Short> v2, FTriOp f) {
        return (ShortVector64)
            super.tOpTemplate((ShortVector64)v1, (ShortVector64)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector64 tOp(Vector<Short> v1, Vector<Short> v2,
                     VectorMask<Short> m, FTriOp f) {
        return (ShortVector64)
            super.tOpTemplate((ShortVector64)v1, (ShortVector64)v2,
                              (ShortMask64)m, f);  // specialize
    }

    @ForceInline
    final @Override
    short rOp(short v, VectorMask<Short> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Short,F> conv,
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
    public ShortVector64 lanewise(Unary op) {
        return (ShortVector64) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 lanewise(Unary op, VectorMask<Short> m) {
        return (ShortVector64) super.lanewiseTemplate(op, ShortMask64.class, (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 lanewise(Binary op, Vector<Short> v) {
        return (ShortVector64) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 lanewise(Binary op, Vector<Short> v, VectorMask<Short> m) {
        return (ShortVector64) super.lanewiseTemplate(op, ShortMask64.class, v, (ShortMask64) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVector64
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ShortVector64) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVector64
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Short> m) {
        return (ShortVector64) super.lanewiseShiftTemplate(op, ShortMask64.class, e, (ShortMask64) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ShortVector64
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2) {
        return (ShortVector64) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVector64
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2, VectorMask<Short> m) {
        return (ShortVector64) super.lanewiseTemplate(op, ShortMask64.class, v1, v2, (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVector64 addIndex(int scale) {
        return (ShortVector64) super.addIndexTemplate(scale);  // specialize
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
                                    VectorMask<Short> m) {
        return super.reduceLanesTemplate(op, ShortMask64.class, (ShortMask64) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Short> m) {
        return (long) super.reduceLanesTemplate(op, ShortMask64.class, (ShortMask64) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ShortShuffle64 toShuffle() {
        return (ShortShuffle64) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ShortMask64 test(Test op) {
        return super.testTemplate(ShortMask64.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask64 test(Test op, VectorMask<Short> m) {
        return super.testTemplate(ShortMask64.class, op, (ShortMask64) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ShortMask64 compare(Comparison op, Vector<Short> v) {
        return super.compareTemplate(ShortMask64.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask64 compare(Comparison op, short s) {
        return super.compareTemplate(ShortMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask64 compare(Comparison op, long s) {
        return super.compareTemplate(ShortMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask64 compare(Comparison op, Vector<Short> v, VectorMask<Short> m) {
        return super.compareTemplate(ShortMask64.class, op, v, (ShortMask64) m);
    }


    @Override
    @ForceInline
    public ShortVector64 blend(Vector<Short> v, VectorMask<Short> m) {
        return (ShortVector64)
            super.blendTemplate(ShortMask64.class,
                                (ShortVector64) v,
                                (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 slice(int origin, Vector<Short> v) {
        return (ShortVector64) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 slice(int origin) {
        return (ShortVector64) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 unslice(int origin, Vector<Short> w, int part) {
        return (ShortVector64) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 unslice(int origin, Vector<Short> w, int part, VectorMask<Short> m) {
        return (ShortVector64)
            super.unsliceTemplate(ShortMask64.class,
                                  origin, w, part,
                                  (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 unslice(int origin) {
        return (ShortVector64) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 rearrange(VectorShuffle<Short> s) {
        return (ShortVector64)
            super.rearrangeTemplate(ShortShuffle64.class,
                                    (ShortShuffle64) s);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 rearrange(VectorShuffle<Short> shuffle,
                                  VectorMask<Short> m) {
        return (ShortVector64)
            super.rearrangeTemplate(ShortShuffle64.class,
                                    ShortMask64.class,
                                    (ShortShuffle64) shuffle,
                                    (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 rearrange(VectorShuffle<Short> s,
                                  Vector<Short> v) {
        return (ShortVector64)
            super.rearrangeTemplate(ShortShuffle64.class,
                                    (ShortShuffle64) s,
                                    (ShortVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 compress(VectorMask<Short> m) {
        return (ShortVector64)
            super.compressTemplate(ShortMask64.class,
                                   (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 expand(VectorMask<Short> m) {
        return (ShortVector64)
            super.expandTemplate(ShortMask64.class,
                                   (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 selectFrom(Vector<Short> v) {
        return (ShortVector64)
            super.selectFromTemplate((ShortVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 selectFrom(Vector<Short> v,
                                   VectorMask<Short> m) {
        return (ShortVector64)
            super.selectFromTemplate((ShortVector64) v,
                                     ShortMask64.class, (ShortMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector64 selectFrom(Vector<Short> v1,
                                   Vector<Short> v2) {
        return (ShortVector64)
            super.selectFromTemplate((ShortVector64) v1, (ShortVector64) v2);  // specialize
    }

    @ForceInline
    @Override
    public short lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            case 2: return laneHelper(2);
            case 3: return laneHelper(3);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public short laneHelper(int i) {
        return (short) VectorSupport.extract(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    short[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public ShortVector64 withLane(int i, short e) {
        switch (i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public ShortVector64 withLaneHelper(int i, short e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    short[] res = v.vec().clone();
                                    res[ix] = (short)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class ShortMask64 extends AbstractMask<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortMask64(boolean[] bits) {
            this(bits, 0);
        }

        ShortMask64(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ShortMask64(boolean val) {
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
        public ShortSpecies vspecies() {
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
        ShortMask64 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ShortMask64(res);
        }

        @Override
        ShortMask64 bOp(VectorMask<Short> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ShortMask64)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ShortMask64(res);
        }

        @ForceInline
        @Override
        public final
        ShortVector64 toVector() {
            return (ShortVector64) super.toVectorTemplate();  // specialize
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
        ShortMask64 indexPartiallyInUpperRange(long offset, long limit) {
            return (ShortMask64) VectorSupport.indexPartiallyInUpperRange(
                ShortMask64.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ShortMask64) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ShortMask64 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ShortMask64 compress() {
            return (ShortMask64)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ShortVector64.class, ShortMask64.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ShortMask64 and(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask64 m = (ShortMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ShortMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ShortMask64 or(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask64 m = (ShortMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ShortMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ShortMask64 xor(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask64 m = (ShortMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ShortMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ShortMask64.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ShortMask64)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ShortMask64)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ShortMask64 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ShortMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ShortMask64  TRUE_MASK = new ShortMask64(true);
        private static final ShortMask64 FALSE_MASK = new ShortMask64(false);

    }

    // Shuffle

    static final class ShortShuffle64 extends AbstractShuffle<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortShuffle64(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ShortShuffle64(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ShortShuffle64(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        short[] indices() {
            return (short[])getPayload();
        }

        @Override
        @ForceInline
        public ShortSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Short.MAX_VALUE);
            assert(Short.MIN_VALUE <= -VLENGTH);
        }
        static final ShortShuffle64 IOTA = new ShortShuffle64(IDENTITY);

        @Override
        @ForceInline
        public ShortVector64 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ShortVector64 toBitsVector() {
            return (ShortVector64) super.toBitsVectorTemplate();
        }

        @Override
        ShortVector64 toBitsVector0() {
            return ((ShortVector64) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            VectorSpecies<Integer> species = IntVector.SPECIES_64;
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
            VectorSpecies<Integer> species = IntVector.SPECIES_64;
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
        public final ShortMask64 laneIsValid() {
            return (ShortMask64) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ShortShuffle64 rearrange(VectorShuffle<Short> shuffle) {
            ShortShuffle64 concreteShuffle = (ShortShuffle64) shuffle;
            return (ShortShuffle64) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ShortShuffle64 wrapIndexes() {
            ShortVector64 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVector64) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVector64) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ShortShuffle64) v.toShuffle(vspecies(), false);
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
    ShortVector fromArray0(short[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromArray0Template(ShortMask64.class, a, offset, (ShortMask64) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Short> m) {
        return super.fromArray0Template(ShortMask64.class, a, offset, indexMap, mapOffset, (ShortMask64) m);
    }

    @ForceInline
    @Override
    final
    ShortVector fromCharArray0(char[] a, int offset) {
        return super.fromCharArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromCharArray0(char[] a, int offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromCharArray0Template(ShortMask64.class, a, offset, (ShortMask64) m, offsetInRange);  // specialize
    }


    @ForceInline
    @Override
    final
    ShortVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Short> m, int offsetInRange) {
        return super.fromMemorySegment0Template(ShortMask64.class, ms, offset, (ShortMask64) m, offsetInRange);  // specialize
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
    void intoArray0(short[] a, int offset, VectorMask<Short> m) {
        super.intoArray0Template(ShortMask64.class, a, offset, (ShortMask64) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Short> m) {
        super.intoMemorySegment0Template(ShortMask64.class, ms, offset, (ShortMask64) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Short> m) {
        super.intoCharArray0Template(ShortMask64.class, a, offset, (ShortMask64) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

