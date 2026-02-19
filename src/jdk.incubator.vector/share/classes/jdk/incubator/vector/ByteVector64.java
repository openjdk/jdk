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
final class ByteVector64 extends ByteVector {
    static final ByteSpecies VSPECIES =
        (ByteSpecies) ByteVector.SPECIES_64;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ByteVector64> VCLASS = ByteVector64.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Byte> ETYPE = byte.class; // used by the JVM

    ByteVector64(byte[] v) {
        super(v);
    }

    // For compatibility as ByteVector64::new,
    // stored into species.vectorFactory.
    ByteVector64(Object v) {
        this((byte[]) v);
    }

    static final ByteVector64 ZERO = new ByteVector64(new byte[VLENGTH]);
    static final ByteVector64 IOTA = new ByteVector64(VSPECIES.iotaArray());

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
    public final ByteVector64 broadcast(byte e) {
        return (ByteVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ByteVector64 broadcast(long e) {
        return (ByteVector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ByteMask64 maskFromArray(boolean[] bits) {
        return new ByteMask64(bits);
    }

    @Override
    @ForceInline
    ByteShuffle64 iotaShuffle() { return ByteShuffle64.IOTA; }

    @Override
    @ForceInline
    ByteShuffle64 iotaShuffle(int start, int step, boolean wrap) {
        return (ByteShuffle64) iotaShuffleTemplate((byte) start, (byte) step, wrap);
    }

    @Override
    @ForceInline
    ByteShuffle64 shuffleFromArray(int[] indices, int i) { return new ByteShuffle64(indices, i); }

    @Override
    @ForceInline
    ByteShuffle64 shuffleFromOp(IntUnaryOperator fn) { return new ByteShuffle64(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ByteVector64 vectorFactory(byte[] vec) {
        return new ByteVector64(vec);
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
    ByteVector64 uOp(FUnOp f) {
        return (ByteVector64) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector64 uOp(VectorMask<Byte> m, FUnOp f) {
        return (ByteVector64)
            super.uOpTemplate((ByteMask64)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ByteVector64 bOp(Vector<Byte> v, FBinOp f) {
        return (ByteVector64) super.bOpTemplate((ByteVector64)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector64 bOp(Vector<Byte> v,
                     VectorMask<Byte> m, FBinOp f) {
        return (ByteVector64)
            super.bOpTemplate((ByteVector64)v, (ByteMask64)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ByteVector64 tOp(Vector<Byte> v1, Vector<Byte> v2, FTriOp f) {
        return (ByteVector64)
            super.tOpTemplate((ByteVector64)v1, (ByteVector64)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVector64 tOp(Vector<Byte> v1, Vector<Byte> v2,
                     VectorMask<Byte> m, FTriOp f) {
        return (ByteVector64)
            super.tOpTemplate((ByteVector64)v1, (ByteVector64)v2,
                              (ByteMask64)m, f);  // specialize
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
    public ByteVector64 lanewise(Unary op) {
        return (ByteVector64) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 lanewise(Unary op, VectorMask<Byte> m) {
        return (ByteVector64) super.lanewiseTemplate(op, ByteMask64.class, (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 lanewise(Binary op, Vector<Byte> v) {
        return (ByteVector64) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 lanewise(Binary op, Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVector64) super.lanewiseTemplate(op, ByteMask64.class, v, (ByteMask64) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVector64
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ByteVector64) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVector64
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Byte> m) {
        return (ByteVector64) super.lanewiseShiftTemplate(op, ByteMask64.class, e, (ByteMask64) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ByteVector64
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2) {
        return (ByteVector64) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVector64
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2, VectorMask<Byte> m) {
        return (ByteVector64) super.lanewiseTemplate(op, ByteMask64.class, v1, v2, (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVector64 addIndex(int scale) {
        return (ByteVector64) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, ByteMask64.class, (ByteMask64) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, ByteMask64.class, (ByteMask64) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ByteShuffle64 toShuffle() {
        return (ByteShuffle64) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ByteMask64 test(Test op) {
        return super.testTemplate(ByteMask64.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask64 test(Test op, VectorMask<Byte> m) {
        return super.testTemplate(ByteMask64.class, op, (ByteMask64) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ByteMask64 compare(Comparison op, Vector<Byte> v) {
        return super.compareTemplate(ByteMask64.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask64 compare(Comparison op, byte s) {
        return super.compareTemplate(ByteMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask64 compare(Comparison op, long s) {
        return super.compareTemplate(ByteMask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMask64 compare(Comparison op, Vector<Byte> v, VectorMask<Byte> m) {
        return super.compareTemplate(ByteMask64.class, op, v, (ByteMask64) m);
    }


    @Override
    @ForceInline
    public ByteVector64 blend(Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVector64)
            super.blendTemplate(ByteMask64.class,
                                (ByteVector64) v,
                                (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 slice(int origin, Vector<Byte> v) {
        return (ByteVector64) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 slice(int origin) {
        return (ByteVector64) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 unslice(int origin, Vector<Byte> w, int part) {
        return (ByteVector64) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 unslice(int origin, Vector<Byte> w, int part, VectorMask<Byte> m) {
        return (ByteVector64)
            super.unsliceTemplate(ByteMask64.class,
                                  origin, w, part,
                                  (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 unslice(int origin) {
        return (ByteVector64) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 rearrange(VectorShuffle<Byte> s) {
        return (ByteVector64)
            super.rearrangeTemplate(ByteShuffle64.class,
                                    (ByteShuffle64) s);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 rearrange(VectorShuffle<Byte> shuffle,
                                  VectorMask<Byte> m) {
        return (ByteVector64)
            super.rearrangeTemplate(ByteShuffle64.class,
                                    ByteMask64.class,
                                    (ByteShuffle64) shuffle,
                                    (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 rearrange(VectorShuffle<Byte> s,
                                  Vector<Byte> v) {
        return (ByteVector64)
            super.rearrangeTemplate(ByteShuffle64.class,
                                    (ByteShuffle64) s,
                                    (ByteVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 compress(VectorMask<Byte> m) {
        return (ByteVector64)
            super.compressTemplate(ByteMask64.class,
                                   (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 expand(VectorMask<Byte> m) {
        return (ByteVector64)
            super.expandTemplate(ByteMask64.class,
                                   (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 selectFrom(Vector<Byte> v) {
        return (ByteVector64)
            super.selectFromTemplate((ByteVector64) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 selectFrom(Vector<Byte> v,
                                   VectorMask<Byte> m) {
        return (ByteVector64)
            super.selectFromTemplate((ByteVector64) v,
                                     ByteMask64.class, (ByteMask64) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVector64 selectFrom(Vector<Byte> v1,
                                   Vector<Byte> v2) {
        return (ByteVector64)
            super.selectFromTemplate((ByteVector64) v1, (ByteVector64) v2);  // specialize
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
    public ByteVector64 withLane(int i, byte e) {
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

    @ForceInline
    public ByteVector64 withLaneHelper(int i, byte e) {
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

    static final class ByteMask64 extends AbstractMask<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteMask64(boolean[] bits) {
            this(bits, 0);
        }

        ByteMask64(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ByteMask64(boolean val) {
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
        ByteMask64 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ByteMask64(res);
        }

        @Override
        ByteMask64 bOp(VectorMask<Byte> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ByteMask64)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ByteMask64(res);
        }

        @ForceInline
        @Override
        public final
        ByteVector64 toVector() {
            return (ByteVector64) super.toVectorTemplate();  // specialize
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
        ByteMask64 indexPartiallyInUpperRange(long offset, long limit) {
            return (ByteMask64) VectorSupport.indexPartiallyInUpperRange(
                ByteMask64.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ByteMask64) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ByteMask64 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ByteMask64 compress() {
            return (ByteMask64)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ByteVector64.class, ByteMask64.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ByteMask64 and(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask64 m = (ByteMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ByteMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ByteMask64 or(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask64 m = (ByteMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ByteMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ByteMask64 xor(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMask64 m = (ByteMask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ByteMask64.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ByteMask64.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ByteMask64)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ByteMask64)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ByteMask64 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ByteMask64.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ByteMask64  TRUE_MASK = new ByteMask64(true);
        private static final ByteMask64 FALSE_MASK = new ByteMask64(false);

    }

    // Shuffle

    static final class ByteShuffle64 extends AbstractShuffle<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteShuffle64(byte[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ByteShuffle64(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ByteShuffle64(IntUnaryOperator fn) {
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
        static final ByteShuffle64 IOTA = new ByteShuffle64(IDENTITY);

        @Override
        @ForceInline
        public ByteVector64 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ByteVector64 toBitsVector() {
            return (ByteVector64) super.toBitsVectorTemplate();
        }

        @Override
        ByteVector64 toBitsVector0() {
            return ((ByteVector64) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
            VectorSpecies<Integer> species = IntVector.SPECIES_64;
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
        public final ByteMask64 laneIsValid() {
            return (ByteMask64) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ByteShuffle64 rearrange(VectorShuffle<Byte> shuffle) {
            ByteShuffle64 concreteShuffle = (ByteShuffle64) shuffle;
            return (ByteShuffle64) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ByteShuffle64 wrapIndexes() {
            ByteVector64 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ByteVector64) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ByteVector64) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ByteShuffle64) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(ByteMask64.class, a, offset, (ByteMask64) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromArray0(byte[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Byte> m) {
        return super.fromArray0Template(ByteMask64.class, a, offset, indexMap, mapOffset, (ByteMask64) m);
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
        return super.fromBooleanArray0Template(ByteMask64.class, a, offset, (ByteMask64) m, offsetInRange);  // specialize
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
        return super.fromMemorySegment0Template(ByteMask64.class, ms, offset, (ByteMask64) m, offsetInRange);  // specialize
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
        super.intoArray0Template(ByteMask64.class, a, offset, (ByteMask64) m);
    }


    @ForceInline
    @Override
    final
    void intoBooleanArray0(boolean[] a, int offset, VectorMask<Byte> m) {
        super.intoBooleanArray0Template(ByteMask64.class, a, offset, (ByteMask64) m);
    }

    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Byte> m) {
        super.intoMemorySegment0Template(ByteMask64.class, ms, offset, (ByteMask64) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

