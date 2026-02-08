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
final class ShortVector512 extends ShortVector {
    static final ShortSpecies VSPECIES =
        (ShortSpecies) ShortVector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<ShortVector512> VCLASS = ShortVector512.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> ETYPE = short.class; // used by the JVM

    ShortVector512(short[] v) {
        super(v);
    }

    // For compatibility as ShortVector512::new,
    // stored into species.vectorFactory.
    ShortVector512(Object v) {
        this((short[]) v);
    }

    static final ShortVector512 ZERO = new ShortVector512(new short[VLENGTH]);
    static final ShortVector512 IOTA = new ShortVector512(VSPECIES.iotaArray());

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
    public final ShortVector512 broadcast(short e) {
        return (ShortVector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final ShortVector512 broadcast(long e) {
        return (ShortVector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    ShortMask512 maskFromArray(boolean[] bits) {
        return new ShortMask512(bits);
    }

    @Override
    @ForceInline
    ShortShuffle512 iotaShuffle() { return ShortShuffle512.IOTA; }

    @Override
    @ForceInline
    ShortShuffle512 iotaShuffle(int start, int step, boolean wrap) {
        return (ShortShuffle512) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    ShortShuffle512 shuffleFromArray(int[] indices, int i) { return new ShortShuffle512(indices, i); }

    @Override
    @ForceInline
    ShortShuffle512 shuffleFromOp(IntUnaryOperator fn) { return new ShortShuffle512(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    ShortVector512 vectorFactory(short[] vec) {
        return new ShortVector512(vec);
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
    ShortVector512 uOp(FUnOp f) {
        return (ShortVector512) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector512 uOp(VectorMask<Short> m, FUnOp f) {
        return (ShortVector512)
            super.uOpTemplate((ShortMask512)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    ShortVector512 bOp(Vector<Short> v, FBinOp f) {
        return (ShortVector512) super.bOpTemplate((ShortVector512)v, f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector512 bOp(Vector<Short> v,
                     VectorMask<Short> m, FBinOp f) {
        return (ShortVector512)
            super.bOpTemplate((ShortVector512)v, (ShortMask512)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    ShortVector512 tOp(Vector<Short> v1, Vector<Short> v2, FTriOp f) {
        return (ShortVector512)
            super.tOpTemplate((ShortVector512)v1, (ShortVector512)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    ShortVector512 tOp(Vector<Short> v1, Vector<Short> v2,
                     VectorMask<Short> m, FTriOp f) {
        return (ShortVector512)
            super.tOpTemplate((ShortVector512)v1, (ShortVector512)v2,
                              (ShortMask512)m, f);  // specialize
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
    public ShortVector512 lanewise(Unary op) {
        return (ShortVector512) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 lanewise(Unary op, VectorMask<Short> m) {
        return (ShortVector512) super.lanewiseTemplate(op, ShortMask512.class, (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 lanewise(Binary op, Vector<Short> v) {
        return (ShortVector512) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 lanewise(Binary op, Vector<Short> v, VectorMask<Short> m) {
        return (ShortVector512) super.lanewiseTemplate(op, ShortMask512.class, v, (ShortMask512) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVector512
    lanewiseShift(VectorOperators.Binary op, int e) {
        return (ShortVector512) super.lanewiseShiftTemplate(op, e);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline ShortVector512
    lanewiseShift(VectorOperators.Binary op, int e, VectorMask<Short> m) {
        return (ShortVector512) super.lanewiseShiftTemplate(op, ShortMask512.class, e, (ShortMask512) m);  // specialize
    }

    /*package-private*/
    @Override
    @ForceInline
    public final
    ShortVector512
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2) {
        return (ShortVector512) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVector512
    lanewise(Ternary op, Vector<Short> v1, Vector<Short> v2, VectorMask<Short> m) {
        return (ShortVector512) super.lanewiseTemplate(op, ShortMask512.class, v1, v2, (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    ShortVector512 addIndex(int scale) {
        return (ShortVector512) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, ShortMask512.class, (ShortMask512) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, ShortMask512.class, (ShortMask512) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        return bitsToShuffleTemplate(dsp);
    }

    @Override
    @ForceInline
    public final ShortShuffle512 toShuffle() {
        return (ShortShuffle512) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final ShortMask512 test(Test op) {
        return super.testTemplate(ShortMask512.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask512 test(Test op, VectorMask<Short> m) {
        return super.testTemplate(ShortMask512.class, op, (ShortMask512) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final ShortMask512 compare(Comparison op, Vector<Short> v) {
        return super.compareTemplate(ShortMask512.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask512 compare(Comparison op, short s) {
        return super.compareTemplate(ShortMask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask512 compare(Comparison op, long s) {
        return super.compareTemplate(ShortMask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final ShortMask512 compare(Comparison op, Vector<Short> v, VectorMask<Short> m) {
        return super.compareTemplate(ShortMask512.class, op, v, (ShortMask512) m);
    }


    @Override
    @ForceInline
    public ShortVector512 blend(Vector<Short> v, VectorMask<Short> m) {
        return (ShortVector512)
            super.blendTemplate(ShortMask512.class,
                                (ShortVector512) v,
                                (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 slice(int origin, Vector<Short> v) {
        return (ShortVector512) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 slice(int origin) {
        return (ShortVector512) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 unslice(int origin, Vector<Short> w, int part) {
        return (ShortVector512) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 unslice(int origin, Vector<Short> w, int part, VectorMask<Short> m) {
        return (ShortVector512)
            super.unsliceTemplate(ShortMask512.class,
                                  origin, w, part,
                                  (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 unslice(int origin) {
        return (ShortVector512) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 rearrange(VectorShuffle<Short> s) {
        return (ShortVector512)
            super.rearrangeTemplate(ShortShuffle512.class,
                                    (ShortShuffle512) s);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 rearrange(VectorShuffle<Short> shuffle,
                                  VectorMask<Short> m) {
        return (ShortVector512)
            super.rearrangeTemplate(ShortShuffle512.class,
                                    ShortMask512.class,
                                    (ShortShuffle512) shuffle,
                                    (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 rearrange(VectorShuffle<Short> s,
                                  Vector<Short> v) {
        return (ShortVector512)
            super.rearrangeTemplate(ShortShuffle512.class,
                                    (ShortShuffle512) s,
                                    (ShortVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 compress(VectorMask<Short> m) {
        return (ShortVector512)
            super.compressTemplate(ShortMask512.class,
                                   (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 expand(VectorMask<Short> m) {
        return (ShortVector512)
            super.expandTemplate(ShortMask512.class,
                                   (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 selectFrom(Vector<Short> v) {
        return (ShortVector512)
            super.selectFromTemplate((ShortVector512) v);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 selectFrom(Vector<Short> v,
                                   VectorMask<Short> m) {
        return (ShortVector512)
            super.selectFromTemplate((ShortVector512) v,
                                     ShortMask512.class, (ShortMask512) m);  // specialize
    }

    @Override
    @ForceInline
    public ShortVector512 selectFrom(Vector<Short> v1,
                                   Vector<Short> v2) {
        return (ShortVector512)
            super.selectFromTemplate((ShortVector512) v1, (ShortVector512) v2);  // specialize
    }

    @ForceInline
    @Override
    public short lane(int i) {
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
            case 16: return laneHelper(16);
            case 17: return laneHelper(17);
            case 18: return laneHelper(18);
            case 19: return laneHelper(19);
            case 20: return laneHelper(20);
            case 21: return laneHelper(21);
            case 22: return laneHelper(22);
            case 23: return laneHelper(23);
            case 24: return laneHelper(24);
            case 25: return laneHelper(25);
            case 26: return laneHelper(26);
            case 27: return laneHelper(27);
            case 28: return laneHelper(28);
            case 29: return laneHelper(29);
            case 30: return laneHelper(30);
            case 31: return laneHelper(31);
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
    public ShortVector512 withLane(int i, short e) {
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
            case 16: return withLaneHelper(16, e);
            case 17: return withLaneHelper(17, e);
            case 18: return withLaneHelper(18, e);
            case 19: return withLaneHelper(19, e);
            case 20: return withLaneHelper(20, e);
            case 21: return withLaneHelper(21, e);
            case 22: return withLaneHelper(22, e);
            case 23: return withLaneHelper(23, e);
            case 24: return withLaneHelper(24, e);
            case 25: return withLaneHelper(25, e);
            case 26: return withLaneHelper(26, e);
            case 27: return withLaneHelper(27, e);
            case 28: return withLaneHelper(28, e);
            case 29: return withLaneHelper(29, e);
            case 30: return withLaneHelper(30, e);
            case 31: return withLaneHelper(31, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public ShortVector512 withLaneHelper(int i, short e) {
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

    static final class ShortMask512 extends AbstractMask<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortMask512(boolean[] bits) {
            this(bits, 0);
        }

        ShortMask512(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        ShortMask512(boolean val) {
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
        ShortMask512 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new ShortMask512(res);
        }

        @Override
        ShortMask512 bOp(VectorMask<Short> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((ShortMask512)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new ShortMask512(res);
        }

        @ForceInline
        @Override
        public final
        ShortVector512 toVector() {
            return (ShortVector512) super.toVectorTemplate();  // specialize
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
        ShortMask512 indexPartiallyInUpperRange(long offset, long limit) {
            return (ShortMask512) VectorSupport.indexPartiallyInUpperRange(
                ShortMask512.class, LANE_TYPE_ORDINAL, VLENGTH, offset, limit,
                (o, l) -> (ShortMask512) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public ShortMask512 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public ShortMask512 compress() {
            return (ShortMask512)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                ShortVector512.class, ShortMask512.class, LANE_TYPE_ORDINAL, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT, m1.trueCount()));
        }


        // Binary operations

        @Override
        @ForceInline
        public ShortMask512 and(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask512 m = (ShortMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, ShortMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public ShortMask512 or(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask512 m = (ShortMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, ShortMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public ShortMask512 xor(VectorMask<Short> mask) {
            Objects.requireNonNull(mask);
            ShortMask512 m = (ShortMask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, ShortMask512.class, null, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(ShortMask512.class, LANE_TYPE_ORDINAL, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> anyTrueHelper(((ShortMask512)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                         this, vspecies().maskAll(true),
                                         (m, __) -> allTrueHelper(((ShortMask512)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static ShortMask512 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(ShortMask512.class, LANEBITS_TYPE_ORDINAL, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final ShortMask512  TRUE_MASK = new ShortMask512(true);
        private static final ShortMask512 FALSE_MASK = new ShortMask512(false);

    }

    // Shuffle

    static final class ShortShuffle512 extends AbstractShuffle<Short> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM
        static final Class<Short> ETYPE = short.class; // used by the JVM

        ShortShuffle512(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        ShortShuffle512(int[] indices, int i) {
            this(prepare(indices, i));
        }

        ShortShuffle512(IntUnaryOperator fn) {
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
        static final ShortShuffle512 IOTA = new ShortShuffle512(IDENTITY);

        @Override
        @ForceInline
        public ShortVector512 toVector() {
            return toBitsVector();
        }

        @Override
        @ForceInline
        ShortVector512 toBitsVector() {
            return (ShortVector512) super.toBitsVectorTemplate();
        }

        @Override
        ShortVector512 toBitsVector0() {
            return ((ShortVector512) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
        }

        @Override
        @ForceInline
        public int laneSource(int i) {
            return (int)toBitsVector().lane(i);
        }

        @Override
        @ForceInline
        public void intoArray(int[] a, int offset) {
            VectorSpecies<Integer> species = IntVector.SPECIES_512;
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
            VectorSpecies<Integer> species = IntVector.SPECIES_512;
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
        public final ShortMask512 laneIsValid() {
            return (ShortMask512) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final ShortShuffle512 rearrange(VectorShuffle<Short> shuffle) {
            ShortShuffle512 concreteShuffle = (ShortShuffle512) shuffle;
            return (ShortShuffle512) toBitsVector().rearrange(concreteShuffle)
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final ShortShuffle512 wrapIndexes() {
            ShortVector512 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVector512) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVector512) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (ShortShuffle512) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(ShortMask512.class, a, offset, (ShortMask512) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    ShortVector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Short> m) {
        return super.fromArray0Template(ShortMask512.class, a, offset, indexMap, mapOffset, (ShortMask512) m);
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
        return super.fromCharArray0Template(ShortMask512.class, a, offset, (ShortMask512) m, offsetInRange);  // specialize
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
        return super.fromMemorySegment0Template(ShortMask512.class, ms, offset, (ShortMask512) m, offsetInRange);  // specialize
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
        super.intoArray0Template(ShortMask512.class, a, offset, (ShortMask512) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Short> m) {
        super.intoMemorySegment0Template(ShortMask512.class, ms, offset, (ShortMask512) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Short> m) {
        super.intoCharArray0Template(ShortMask512.class, a, offset, (ShortMask512) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

