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
final class ByteVector128 extends ByteVector {
    static final ByteSpecies VSPECIES =
        (ByteSpecies) ByteVector.SPECIES_128;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ByteVector128> VCLASS = ByteVector128.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Byte> ETYPE = byte.class; // used by the JVM

    ByteVector128(byte[] v) {
        super(v);
    }

    // For compatibility as ByteVector128::new,
    // stored into species.vectorFactory.
    ByteVector128(Object v) {
        this((byte[]) v);
    }

    static final ByteVector128 ZERO = new ByteVector128(new byte[VLENGTH]);
    static final ByteVector128 IOTA = new ByteVector128(VSPECIES.iotaArray());

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
    public ByteSpecies vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Byte> elementType() { return byte.class; }

    @ForceInline
    @Override
    public final int elementSize() { return Byte.SIZE; }

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
    byte[] vec() {
        return (byte[])getPayload();
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
    public final ByteVector128 broadcast(byte e) {
        return (ByteVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ByteVector128 broadcast(long e) {
        return (ByteVector128) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ByteMask128 maskFromArray(boolean[] bits) {
        return new ByteMask128(bits);
    }

    @Override
    @ForceInline
    ByteShuffle128 iotaShuffle() { return ByteShuffle128.IOTA; }

    @Override
    @ForceInline
    ByteShuffle128 iotaShuffle(int start, int step, boolean wrap) {
        return (ByteShuffle128) iotaShuffleTemplate((byte) start, (byte) step, wrap);
    }

    @Override
    @ForceInline
    ByteShuffle128 shuffleFromArray(int[] indices, int i) { return new ByteShuffle128(indices, i); }

    @Override
    @ForceInline
    ByteShuffle128 shuffleFromOp(IntUnaryOperator fn) { return new ByteShuffle128(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ByteVector128 vectorFactory(byte[] vec) {
        return new ByteVector128(vec);
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
    ByteVector128 uOp(FUnOp f) {
        return (ByteVector128) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector128 uOp(VectorMask<Byte> m, FUnOp f) {
        return (ByteVector128)
            super.uOpTemplate((ByteMask128)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ByteVector128 bOp(Vector<Byte> v, FBinOp f) {
        return (ByteVector128) super.bOpTemplate((ByteVector128)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector128 bOp(Vector<Byte> v,
                     VectorMask<Byte> m, FBinOp f) {
        return (ByteVector128)
            super.bOpTemplate((ByteVector128)v, (ByteMask128)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ByteVector128 tOp(Vector<Byte> v1, Vector<Byte> v2, FTriOp f) {
        return (ByteVector128)
            super.tOpTemplate((ByteVector128)v1, (ByteVector128)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector128 tOp(Vector<Byte> v1, Vector<Byte> v2,
                     VectorMask<Byte> m, FTriOp f) {
        return (ByteVector128)
            super.tOpTemplate((ByteVector128)v1, (ByteVector128)v2,
                              (ByteMask128)m, f);  // specialize
    }

    @ForceInline
    final @Override
    byte rOp(byte v, VectorMask<Byte> m, FBinOp f) {
        return super.rOpTemplate(v, m, f);  // specialize
    }

    @Override
    @ForceInline
    public final <F>
    Vector<F> convertShape(VectorOperators.Conversion<Byte,F> conv,
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
    public ByteVector128 lanewise(Unary op) {
        return (ByteVector128) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 lanewise(Unary op, VectorMask<Byte> m) {
        return (ByteVector128) super.lanewiseTemplate(op, ByteMask128.class, (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 lanewise(Binary op, Vector<Byte> v) {
        return (ByteVector128) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 lanewise(Binary op, Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVector128) super.lanewiseTemplate(op, ByteMask128.class, v, (ByteMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVector128
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ByteVector128) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVector128
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Byte> m) {
        return (ByteVector128) super.lanewiseShiftTemplate(op, ByteMask128.class, e, (ByteMask128) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ByteVector128
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2) {
        return (ByteVector128) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVector128
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2, VectorMask<Byte> m) {
        return (ByteVector128) super.lanewiseTemplate(op, ByteMask128.class, v1, v2, (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVector128 addIndex(int scale) {
        return (ByteVector128) super.addIndexTemplate(scale);  // specialize
    }

    // Type specific horizontal reductions

    @Override
    @ForceInline
    public final byte reduceLanes(VectorOperators.Associative op) {
        return super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final byte reduceLanes(VectorOperators.Associative op,
                                    VectorMask<Byte> m) {
        return super.reduceLanesTemplate(op, ByteMask128.class, (ByteMask128) m);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op) {
        return (long) super.reduceLanesTemplate(op);  // specialized
    }

    @Override
    @ForceInline
    public final long reduceLanesToLong(VectorOperators.Associative op,
                                        VectorMask<Byte> m) {
        return (long) super.reduceLanesTemplate(op, ByteMask128.class, (ByteMask128) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ByteShuffle128 toShuffle() {
        return (ByteShuffle128) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ByteMask128 test(Test op) {
        return super.testTemplate(ByteMask128.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask128 test(Test op, VectorMask<Byte> m) {
        return super.testTemplate(ByteMask128.class, op, (ByteMask128) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ByteMask128 compare(Comparison op, Vector<Byte> v) {
        return super.compareTemplate(ByteMask128.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask128 compare(Comparison op, byte s) {
        return super.compareTemplate(ByteMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask128 compare(Comparison op, long s) {
        return super.compareTemplate(ByteMask128.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask128 compare(Comparison op, Vector<Byte> v, VectorMask<Byte> m) {
        return super.compareTemplate(ByteMask128.class, op, v, (ByteMask128) m);
    }


    @Override
    @ForceInline
    public ByteVector128 blend(Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVector128)
            super.blendTemplate(ByteMask128.class,
                                (ByteVector128) v,
                                (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 slice(int origin, Vector<Byte> v) {
        return (ByteVector128) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 slice(int origin) {
        return (ByteVector128) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 unslice(int origin, Vector<Byte> w, int part) {
        return (ByteVector128) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 unslice(int origin, Vector<Byte> w, int part, VectorMask<Byte> m) {
        return (ByteVector128)
            super.unsliceTemplate(ByteMask128.class,
                                  origin, w, part,
                                  (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 unslice(int origin) {
        return (ByteVector128) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 rearrange(VectorShuffle<Byte> s) {
        return (ByteVector128)
            super.rearrangeTemplate(ByteShuffle128.class,
                                    (ByteShuffle128) s);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 rearrange(VectorShuffle<Byte> shuffle,
                                  VectorMask<Byte> m) {
        return (ByteVector128)
            super.rearrangeTemplate(ByteShuffle128.class,
                                    ByteMask128.class,
                                    (ByteShuffle128) shuffle,
                                    (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 rearrange(VectorShuffle<Byte> s,
                                  Vector<Byte> v) {
        return (ByteVector128)
            super.rearrangeTemplate(ByteShuffle128.class,
                                    (ByteShuffle128) s,
                                    (ByteVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 compress(VectorMask<Byte> m) {
        return (ByteVector128)
            super.compressTemplate(ByteMask128.class,
                                   (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 expand(VectorMask<Byte> m) {
        return (ByteVector128)
            super.expandTemplate(ByteMask128.class,
                                   (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 selectFrom(Vector<Byte> v) {
        return (ByteVector128)
            super.selectFromTemplate((ByteVector128) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 selectFrom(Vector<Byte> v,
                                   VectorMask<Byte> m) {
        return (ByteVector128)
            super.selectFromTemplate((ByteVector128) v,
                                     ByteMask128.class, (ByteMask128) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector128 selectFrom(Vector<Byte> v1,
                                   Vector<Byte> v2) {
        return (ByteVector128)
            super.selectFromTemplate((ByteVector128) v1, (ByteVector128) v2);  // specialize
    }

    @ForceInline
    @Override
    public byte lane(int i) {
        switch(i) {
            case 0: return laneHelper(0);
            case 1: return laneHelper(1);
            case 2: return laneHelper(2);
            case 3: return laneHelper(3);
            case 4: return laneHelper(4);
            case 5: return laneHelper(5);
            case 6: return laneHelper(6);
            case 7: return laneHelper(7);
            case 8: return laneHelper(8);
            case 9: return laneHelper(9);
            case 10: return laneHelper(10);
            case 11: return laneHelper(11);
            case 12: return laneHelper(12);
            case 13: return laneHelper(13);
            case 14: return laneHelper(14);
            case 15: return laneHelper(15);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public byte laneHelper(int i) {
        return (byte) VectorSupport.extract(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i,
                                (vec, ix) -> {
                                    byte[] vecarr = vec.vec();
                                    return (long)vecarr[ix];
                                });
    }

    @ForceInline
    @Override
    public ByteVector128 withLane(int i, byte e) {
        switch (i) {
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
    public ByteVector128 withLaneHelper(int i, byte e) {
        return VectorSupport.insert(
                                VCLASS, LANE_TYPE_ORDINAL, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    byte[] res = v.vec().clone();
                                    res[ix] = (byte)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class ByteMask128 extends AbstractMask<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteMask128(boolean[] bits) {
            this(bits, 0);
        }

        ByteMask128(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ByteMask128(boolean val) {
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
        public ByteSpecies vspecies() {
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
        ByteMask128 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ByteMask128(res);
        }

        @Override
        ByteMask128 bOp(VectorMask<Byte> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ByteMask128)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ByteMask128(res);
        }

        @ForceInline
        @Override
        public final
        ByteVector128 toVector() {
            return (ByteVector128) super.toVectorTemplate();  // specialize
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
        ByteMask128 indexPartiallyInUpperRange(long offset, long limit) {
            return (ByteMask128) VectorSupport.indexPartiallyInUpperRange(
                ByteMask128.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ByteMask128) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ByteMask128 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ByteMask128 compress() {
            return (ByteMask128)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ByteVector128.class, ByteMask128.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ByteMask128 and(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask128 m = (ByteMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ByteMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ByteMask128 or(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask128 m = (ByteMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ByteMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ByteMask128 xor(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask128 m = (ByteMask128)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ByteMask128.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ByteMask128.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ByteMask128)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ByteMask128)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ByteMask128 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ByteMask128.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ByteMask128  TRUE_MASK = new ByteMask128(true);
        private static final ByteMask128 FALSE_MASK = new ByteMask128(false);

    }

    // Shuffle

    static final class ByteShuffle128 extends AbstractShuffle<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteShuffle128(byte[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ByteShuffle128(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ByteShuffle128(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        byte[] indices() {
            return (byte[])getPayload();
        }

        @Override
        @ForceInline
        public ByteSpecies vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Byte.MAX_VALUE);
            assert(Byte.MIN_VALUE <= -VLENGTH);
        }
        static final ByteShuffle128 IOTA = new ByteShuffle128(IDENTITY);

        @Override
        @ForceInline
        public ByteVector128 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ByteVector128 toBitsVector() {
            return (ByteVector128) super.toBitsVectorTemplate();
        }

        @Override
        ByteVector128 toBitsVector0() {
            return ((ByteVector128) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            VectorSpecies<Integer> species = IntVector.SPECIES_128;
            Vector<Byte> v = toBitsVector();
            v.convertShape(VectorOperators.B2I, species, 0)
                    .reinterpretAsInts()
                    .intoArray(a, offset);
            v.convertShape(VectorOperators.B2I, species, 1)
                    .reinterpretAsInts()
                    .intoArray(a, offset + species.length());
            v.convertShape(VectorOperators.B2I, species, 2)
                    .reinterpretAsInts()
                    .intoArray(a, offset + species.length() * 2);
            v.convertShape(VectorOperators.B2I, species, 3)
                    .reinterpretAsInts()
                    .intoArray(a, offset + species.length() * 3);
        }

        @Override
        @ForceInline
        public void intoMemorySegment(MemorySegment ms, long offset, ByteOrder bo) {
            VectorSpecies<Integer> species = IntVector.SPECIES_128;
            Vector<Byte> v = toBitsVector();
            v.convertShape(VectorOperators.B2I, species, 0)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset, bo);
            v.convertShape(VectorOperators.B2I, species, 1)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset + species.vectorByteSize(), bo);
            v.convertShape(VectorOperators.B2I, species, 2)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset + species.vectorByteSize() * 2, bo);
            v.convertShape(VectorOperators.B2I, species, 3)
                    .reinterpretAsInts()
                    .intoMemorySegment(ms, offset + species.vectorByteSize() * 3, bo);
         }

        @Override
        @ForceInline
        public final ByteMask128 laneIsValid() {
            return (ByteMask128) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ByteShuffle128 rearrange(VectorShuffle<Byte> shuffle) {
            ByteShuffle128 concreteShuffle = (ByteShuffle128) shuffle;
            return (ByteShuffle128) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ByteShuffle128 wrapIndexes() {
            ByteVector128 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ByteVector128) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ByteVector128) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ByteShuffle128) v.toShuffle(vspecies(), false);
        }

        private static byte[] prepare(int[] indices, int offset) {
            byte[] a = new byte[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = indices[offset + i];
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (byte)si;
            }
            return a;
        }

        private static byte[] prepare(IntUnaryOperator f) {
            byte[] a = new byte[VLENGTH];
            for (int i = 0; i < VLENGTH; i++) {
                int si = f.applyAsInt(i);
                si = partiallyWrapIndex(si, VLENGTH);
                a[i] = (byte)si;
            }
            return a;
        }

        private static boolean indicesInRange(byte[] indices) {
            int length = indices.length;
            for (byte si : indices) {
                if (si >= (byte)length || si < (byte)(-length)) {
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
    ByteVector fromArray0(byte[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromArray0(byte[] a, int offset, VectorMask<Byte> m, int offsetInRange) {
        return super.fromArray0Template(ByteMask128.class, a, offset, (ByteMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromArray0(byte[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Byte> m) {
        return super.fromArray0Template(ByteMask128.class, a, offset, indexMap, mapOffset, (ByteMask128) m);
    }


    @ForceInline
    @Override
    final
    ByteVector fromBooleanArray0(boolean[] a, int offset) {
        return super.fromBooleanArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromBooleanArray0(boolean[] a, int offset, VectorMask<Byte> m, int offsetInRange) {
        return super.fromBooleanArray0Template(ByteMask128.class, a, offset, (ByteMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Byte> m, int offsetInRange) {
        return super.fromMemorySegment0Template(ByteMask128.class, ms, offset, (ByteMask128) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(byte[] a, int offset) {
        super.intoArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    void intoArray0(byte[] a, int offset, VectorMask<Byte> m) {
        super.intoArray0Template(ByteMask128.class, a, offset, (ByteMask128) m);
    }


    @ForceInline
    @Override
    final
    void intoBooleanArray0(boolean[] a, int offset, VectorMask<Byte> m) {
        super.intoBooleanArray0Template(ByteMask128.class, a, offset, (ByteMask128) m);
    }

    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Byte> m) {
        super.intoMemorySegment0Template(ByteMask128.class, ms, offset, (ByteMask128) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

