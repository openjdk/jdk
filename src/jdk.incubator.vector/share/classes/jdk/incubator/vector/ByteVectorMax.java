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
final class ByteVectorMax extends ByteVector {
    static final ByteSpecies VSPECIES =
        (ByteSpecies) ByteVector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ByteVectorMax> VCLASS = ByteVectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Byte> ETYPE = byte.class; // used by the JVM

    ByteVectorMax(byte[] v) {
        super(v);
    }

    // For compatibility as ByteVectorMax::new,
    // stored into species.vectorFactory.
    ByteVectorMax(Object v) {
        this((byte[]) v);
    }

    static final ByteVectorMax ZERO = new ByteVectorMax(new byte[VLENGTH]);
    static final ByteVectorMax IOTA = new ByteVectorMax(VSPECIES.iotaArray());

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
    public final ByteVectorMax broadcast(byte e) {
        return (ByteVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ByteVectorMax broadcast(long e) {
        return (ByteVectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ByteMaskMax maskFromArray(boolean[] bits) {
        return new ByteMaskMax(bits);
    }

    @Override
    @ForceInline
    ByteShuffleMax iotaShuffle() { return ByteShuffleMax.IOTA; }

    @Override
    @ForceInline
    ByteShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (ByteShuffleMax) iotaShuffleTemplate((byte) start, (byte) step, wrap);
    }

    @Override
    @ForceInline
    ByteShuffleMax shuffleFromArray(int[] indices, int i) { return new ByteShuffleMax(indices, i); }

    @Override
    @ForceInline
    ByteShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new ByteShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ByteVectorMax vectorFactory(byte[] vec) {
        return new ByteVectorMax(vec);
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
    ByteVectorMax uOp(FUnOp f) {
        return (ByteVectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVectorMax uOp(VectorMask<Byte> m, FUnOp f) {
        return (ByteVectorMax)
            super.uOpTemplate((ByteMaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ByteVectorMax bOp(Vector<Byte> v, FBinOp f) {
        return (ByteVectorMax) super.bOpTemplate((ByteVectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVectorMax bOp(Vector<Byte> v,
                     VectorMask<Byte> m, FBinOp f) {
        return (ByteVectorMax)
            super.bOpTemplate((ByteVectorMax)v, (ByteMaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ByteVectorMax tOp(Vector<Byte> v1, Vector<Byte> v2, FTriOp f) {
        return (ByteVectorMax)
            super.tOpTemplate((ByteVectorMax)v1, (ByteVectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ByteVectorMax tOp(Vector<Byte> v1, Vector<Byte> v2,
                     VectorMask<Byte> m, FTriOp f) {
        return (ByteVectorMax)
            super.tOpTemplate((ByteVectorMax)v1, (ByteVectorMax)v2,
                              (ByteMaskMax)m, f);  // specialize
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
    public ByteVectorMax lanewise(Unary op) {
        return (ByteVectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax lanewise(Unary op, VectorMask<Byte> m) {
        return (ByteVectorMax) super.lanewiseTemplate(op, ByteMaskMax.class, (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax lanewise(Binary op, Vector<Byte> v) {
        return (ByteVectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax lanewise(Binary op, Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVectorMax) super.lanewiseTemplate(op, ByteMaskMax.class, v, (ByteMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVectorMax
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ByteVectorMax) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ByteVectorMax
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Byte> m) {
        return (ByteVectorMax) super.lanewiseShiftTemplate(op, ByteMaskMax.class, e, (ByteMaskMax) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ByteVectorMax
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2) {
        return (ByteVectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVectorMax
    lanewise(Ternary op, Vector<Byte> v1, Vector<Byte> v2, VectorMask<Byte> m) {
        return (ByteVectorMax) super.lanewiseTemplate(op, ByteMaskMax.class, v1, v2, (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ByteVectorMax addIndex(int scale) {
        return (ByteVectorMax) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, ByteMaskMax.class, (ByteMaskMax) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, ByteMaskMax.class, (ByteMaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ByteShuffleMax toShuffle() {
        return (ByteShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ByteMaskMax test(Test op) {
        return super.testTemplate(ByteMaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMaskMax test(Test op, VectorMask<Byte> m) {
        return super.testTemplate(ByteMaskMax.class, op, (ByteMaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ByteMaskMax compare(Comparison op, Vector<Byte> v) {
        return super.compareTemplate(ByteMaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMaskMax compare(Comparison op, byte s) {
        return super.compareTemplate(ByteMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMaskMax compare(Comparison op, long s) {
        return super.compareTemplate(ByteMaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ByteMaskMax compare(Comparison op, Vector<Byte> v, VectorMask<Byte> m) {
        return super.compareTemplate(ByteMaskMax.class, op, v, (ByteMaskMax) m);
    }


    @Override
    @ForceInline
    public ByteVectorMax blend(Vector<Byte> v, VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.blendTemplate(ByteMaskMax.class,
                                (ByteVectorMax) v,
                                (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax slice(int origin, Vector<Byte> v) {
        return (ByteVectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax slice(int origin) {
        return (ByteVectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax unslice(int origin, Vector<Byte> w, int part) {
        return (ByteVectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax unslice(int origin, Vector<Byte> w, int part, VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.unsliceTemplate(ByteMaskMax.class,
                                  origin, w, part,
                                  (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax unslice(int origin) {
        return (ByteVectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax rearrange(VectorShuffle<Byte> s) {
        return (ByteVectorMax)
            super.rearrangeTemplate(ByteShuffleMax.class,
                                    (ByteShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax rearrange(VectorShuffle<Byte> shuffle,
                                  VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.rearrangeTemplate(ByteShuffleMax.class,
                                    ByteMaskMax.class,
                                    (ByteShuffleMax) shuffle,
                                    (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax rearrange(VectorShuffle<Byte> s,
                                  Vector<Byte> v) {
        return (ByteVectorMax)
            super.rearrangeTemplate(ByteShuffleMax.class,
                                    (ByteShuffleMax) s,
                                    (ByteVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax compress(VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.compressTemplate(ByteMaskMax.class,
                                   (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax expand(VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.expandTemplate(ByteMaskMax.class,
                                   (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax selectFrom(Vector<Byte> v) {
        return (ByteVectorMax)
            super.selectFromTemplate((ByteVectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax selectFrom(Vector<Byte> v,
                                   VectorMask<Byte> m) {
        return (ByteVectorMax)
            super.selectFromTemplate((ByteVectorMax) v,
                                     ByteMaskMax.class, (ByteMaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public ByteVectorMax selectFrom(Vector<Byte> v1,
                                   Vector<Byte> v2) {
        return (ByteVectorMax)
            super.selectFromTemplate((ByteVectorMax) v1, (ByteVectorMax) v2);  // specialize
    }

    @ForceInline
    @Override
    public byte lane(int i) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return laneHelper(i);
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
    public ByteVectorMax withLane(int i, byte e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public ByteVectorMax withLaneHelper(int i, byte e) {
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

    static final class ByteMaskMax extends AbstractMask<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteMaskMax(boolean[] bits) {
            this(bits, 0);
        }

        ByteMaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ByteMaskMax(boolean val) {
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
        ByteMaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ByteMaskMax(res);
        }

        @Override
        ByteMaskMax bOp(VectorMask<Byte> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ByteMaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ByteMaskMax(res);
        }

        @ForceInline
        @Override
        public final
        ByteVectorMax toVector() {
            return (ByteVectorMax) super.toVectorTemplate();  // specialize
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
        ByteMaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (ByteMaskMax) VectorSupport.indexPartiallyInUpperRange(
                ByteMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ByteMaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ByteMaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ByteMaskMax compress() {
            return (ByteMaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ByteVectorMax.class, ByteMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ByteMaskMax and(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMaskMax m = (ByteMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ByteMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ByteMaskMax or(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMaskMax m = (ByteMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ByteMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ByteMaskMax xor(VectorMask<Byte> mask) {
            Objects.requireNonNull(mask);
            ByteMaskMax m = (ByteMaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ByteMaskMax.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ByteMaskMax.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ByteMaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ByteMaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ByteMaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ByteMaskMax.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ByteMaskMax  TRUE_MASK = new ByteMaskMax(true);
        private static final ByteMaskMax FALSE_MASK = new ByteMaskMax(false);

    }

    // Shuffle

    static final class ByteShuffleMax extends AbstractShuffle<Byte> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Byte> ETYPE = byte.class; // used by the JVM

        ByteShuffleMax(byte[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ByteShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ByteShuffleMax(IntUnaryOperator fn) {
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
        static final ByteShuffleMax IOTA = new ByteShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public ByteVectorMax toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ByteVectorMax toBitsVector() {
            return (ByteVectorMax) super.toBitsVectorTemplate();
        }

        @Override
        ByteVectorMax toBitsVector0() {
            return ((ByteVectorMax) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
            VectorSpecies<Integer> species = IntVector.SPECIES_MAX;
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
        public final ByteMaskMax laneIsValid() {
            return (ByteMaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ByteShuffleMax rearrange(VectorShuffle<Byte> shuffle) {
            ByteShuffleMax concreteShuffle = (ByteShuffleMax) shuffle;
            return (ByteShuffleMax) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ByteShuffleMax wrapIndexes() {
            ByteVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ByteVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ByteVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ByteShuffleMax) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(ByteMaskMax.class, a, offset, (ByteMaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ByteVector fromArray0(byte[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Byte> m) {
        return super.fromArray0Template(ByteMaskMax.class, a, offset, indexMap, mapOffset, (ByteMaskMax) m);
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
        return super.fromBooleanArray0Template(ByteMaskMax.class, a, offset, (ByteMaskMax) m, offsetInRange);  // specialize
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
        return super.fromMemorySegment0Template(ByteMaskMax.class, ms, offset, (ByteMaskMax) m, offsetInRange);  // specialize
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
        super.intoArray0Template(ByteMaskMax.class, a, offset, (ByteMaskMax) m);
    }


    @ForceInline
    @Override
    final
    void intoBooleanArray0(boolean[] a, int offset, VectorMask<Byte> m) {
        super.intoBooleanArray0Template(ByteMaskMax.class, a, offset, (ByteMaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Byte> m) {
        super.intoMemorySegment0Template(ByteMaskMax.class, ms, offset, (ByteMaskMax) m);
    }


    // End of specialized low-level memory operations.

    // ================================================

}

