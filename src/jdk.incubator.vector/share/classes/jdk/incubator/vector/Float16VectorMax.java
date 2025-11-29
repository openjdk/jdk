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
final class Float16VectorMax extends Float16Vector {
    static final Float16Species VSPECIES =
        (Float16Species) Float16Vector.SPECIES_MAX;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Float16VectorMax> VCLASS = Float16VectorMax.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> CTYPE = short.class; // carrier type used by the JVM

    static final Class<Float16> ETYPE = Float16.class; // vector element type used by the JVM

    Float16VectorMax(short[] v) {
        super(v);
    }

    // For compatibility as Float16VectorMax::new,
    // stored into species.vectorFactory.
    Float16VectorMax(Object v) {
        this((short[]) v);
    }

    static final Float16VectorMax ZERO = new Float16VectorMax(new short[VLENGTH]);
    static final Float16VectorMax IOTA = new Float16VectorMax(VSPECIES.iotaArray());

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
    public Float16Species vspecies() {
        // ISSUE:  This should probably be a @Stable
        // field inside AbstractVector, rather than
        // a megamorphic method.
        return VSPECIES;
    }

    @ForceInline
    @Override
    public final Class<Float16> elementType() { return ETYPE; }

    @ForceInline
    final Class<Short> carrierType() { return CTYPE; }

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
    public final Float16VectorMax broadcast(short e) {
        return (Float16VectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final Float16VectorMax broadcast(long e) {
        return (Float16VectorMax) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    Float16MaskMax maskFromArray(boolean[] bits) {
        return new Float16MaskMax(bits);
    }

    @Override
    @ForceInline
    Float16ShuffleMax iotaShuffle() { return Float16ShuffleMax.IOTA; }

    @Override
    @ForceInline
    Float16ShuffleMax iotaShuffle(int start, int step, boolean wrap) {
        return (Float16ShuffleMax) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    Float16ShuffleMax shuffleFromArray(int[] indices, int i) { return new Float16ShuffleMax(indices, i); }

    @Override
    @ForceInline
    Float16ShuffleMax shuffleFromOp(IntUnaryOperator fn) { return new Float16ShuffleMax(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Float16VectorMax vectorFactory(short[] vec) {
        return new Float16VectorMax(vec);
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
    Float16VectorMax uOp(FUnOp f) {
        return (Float16VectorMax) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16VectorMax uOp(VectorMask<Float16> m, FUnOp f) {
        return (Float16VectorMax)
            super.uOpTemplate((Float16MaskMax)m, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16VectorMax sOp(FSnOp f) {
        return (Float16VectorMax) super.sOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16VectorMax sOp(VectorMask<Float16> m, FSnOp f) {
        return (Float16VectorMax)
            super.sOpTemplate((Float16MaskMax)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Float16VectorMax bOp(Vector<Float16> v, FBinOp f) {
        return (Float16VectorMax) super.bOpTemplate((Float16VectorMax)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16VectorMax bOp(Vector<Float16> v,
                     VectorMask<Float16> m, FBinOp f) {
        return (Float16VectorMax)
            super.bOpTemplate((Float16VectorMax)v, (Float16MaskMax)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Float16VectorMax tOp(Vector<Float16> v1, Vector<Float16> v2, FTriOp f) {
        return (Float16VectorMax)
            super.tOpTemplate((Float16VectorMax)v1, (Float16VectorMax)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Float16VectorMax tOp(Vector<Float16> v1, Vector<Float16> v2,
                     VectorMask<Float16> m, FTriOp f) {
        return (Float16VectorMax)
            super.tOpTemplate((Float16VectorMax)v1, (Float16VectorMax)v2,
                              (Float16MaskMax)m, f);  // specialize
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
    public Float16VectorMax lanewise(Unary op) {
        return (Float16VectorMax) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax lanewise(Unary op, VectorMask<Float16> m) {
        return (Float16VectorMax) super.lanewiseTemplate(op, Float16MaskMax.class, (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax lanewise(Binary op, Vector<Float16> v) {
        return (Float16VectorMax) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax lanewise(Binary op, Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16VectorMax) super.lanewiseTemplate(op, Float16MaskMax.class, v, (Float16MaskMax) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    Float16VectorMax
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2) {
        return (Float16VectorMax) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16VectorMax
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2, VectorMask<Float16> m) {
        return (Float16VectorMax) super.lanewiseTemplate(op, Float16MaskMax.class, v1, v2, (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16VectorMax addIndex(int scale) {
        return (Float16VectorMax) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, Float16MaskMax.class, (Float16MaskMax) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, Float16MaskMax.class, (Float16MaskMax) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final Float16ShuffleMax toShuffle() {
        return (Float16ShuffleMax) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final Float16MaskMax test(Test op) {
        return super.testTemplate(Float16MaskMax.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final Float16MaskMax test(Test op, VectorMask<Float16> m) {
        return super.testTemplate(Float16MaskMax.class, op, (Float16MaskMax) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final Float16MaskMax compare(Comparison op, Vector<Float16> v) {
        return super.compareTemplate(Float16MaskMax.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final Float16MaskMax compare(Comparison op, short s) {
        return super.compareTemplate(Float16MaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16MaskMax compare(Comparison op, long s) {
        return super.compareTemplate(Float16MaskMax.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16MaskMax compare(Comparison op, Vector<Float16> v, VectorMask<Float16> m) {
        return super.compareTemplate(Float16MaskMax.class, op, v, (Float16MaskMax) m);
    }


    @Override
    @ForceInline
    public Float16VectorMax blend(Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.blendTemplate(Float16MaskMax.class,
                                (Float16VectorMax) v,
                                (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax slice(int origin, Vector<Float16> v) {
        return (Float16VectorMax) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax slice(int origin) {
        return (Float16VectorMax) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax unslice(int origin, Vector<Float16> w, int part) {
        return (Float16VectorMax) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax unslice(int origin, Vector<Float16> w, int part, VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.unsliceTemplate(Float16MaskMax.class,
                                  origin, w, part,
                                  (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax unslice(int origin) {
        return (Float16VectorMax) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax rearrange(VectorShuffle<Float16> s) {
        return (Float16VectorMax)
            super.rearrangeTemplate(Float16ShuffleMax.class,
                                    (Float16ShuffleMax) s);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax rearrange(VectorShuffle<Float16> shuffle,
                                  VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.rearrangeTemplate(Float16ShuffleMax.class,
                                    Float16MaskMax.class,
                                    (Float16ShuffleMax) shuffle,
                                    (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax rearrange(VectorShuffle<Float16> s,
                                  Vector<Float16> v) {
        return (Float16VectorMax)
            super.rearrangeTemplate(Float16ShuffleMax.class,
                                    (Float16ShuffleMax) s,
                                    (Float16VectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax compress(VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.compressTemplate(Float16MaskMax.class,
                                   (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax expand(VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.expandTemplate(Float16MaskMax.class,
                                   (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax selectFrom(Vector<Float16> v) {
        return (Float16VectorMax)
            super.selectFromTemplate((Float16VectorMax) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax selectFrom(Vector<Float16> v,
                                   VectorMask<Float16> m) {
        return (Float16VectorMax)
            super.selectFromTemplate((Float16VectorMax) v,
                                     Float16MaskMax.class, (Float16MaskMax) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16VectorMax selectFrom(Vector<Float16> v1,
                                   Vector<Float16> v2) {
        return (Float16VectorMax)
            super.selectFromTemplate((Float16VectorMax) v1, (Float16VectorMax) v2);  // specialize
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
                     VCLASS, T_FLOAT16, VLENGTH,
                     this, i,
                     (vec, ix) -> {
                     short[] vecarr = vec.vec();
                     return vecarr[ix];
                     });
    }

    @ForceInline
    @Override
    public Float16VectorMax withLane(int i, short e) {
        if (i < 0 || i >= VLENGTH) {
            throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
        return withLaneHelper(i, e);
    }

    @ForceInline
    public Float16VectorMax withLaneHelper(int i, short e) {
        return VectorSupport.insert(
                                VCLASS, T_FLOAT16, VLENGTH,
                                this, i, (long)e,
                                (v, ix, bits) -> {
                                    short[] res = v.vec().clone();
                                    res[ix] = (short)bits;
                                    return v.vectorFactory(res);
                                });
    }

    // Mask

    static final class Float16MaskMax extends AbstractMask<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16MaskMax(boolean[] bits) {
            this(bits, 0);
        }

        Float16MaskMax(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Float16MaskMax(boolean val) {
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
        public Float16Species vspecies() {
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
        Float16MaskMax uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Float16MaskMax(res);
        }

        @Override
        Float16MaskMax bOp(VectorMask<Float16> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Float16MaskMax)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Float16MaskMax(res);
        }

        @ForceInline
        @Override
        public final
        Float16VectorMax toVector() {
            return (Float16VectorMax) super.toVectorTemplate();  // specialize
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
                this.getClass(), T_FLOAT16, VLENGTH,
                species.maskType(), species.laneBasicType(), VLENGTH,
                this, species,
                (m, s) -> s.maskFactory(m.toArray()).check(s));
        }

        @Override
        @ForceInline
        /*package-private*/
        Float16MaskMax indexPartiallyInUpperRange(long offset, long limit) {
            return (Float16MaskMax) VectorSupport.indexPartiallyInUpperRange(
                Float16MaskMax.class, T_FLOAT16, VLENGTH, offset, limit,
                (o, l) -> (Float16MaskMax) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public Float16MaskMax not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Float16MaskMax compress() {
            return (Float16MaskMax)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Float16VectorMax.class, Float16MaskMax.class, T_FLOAT16, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT,
                Float16.float16ToShortBits(Float16.valueOf(m1.trueCount()))));
        }


        // Binary operations

        @Override
        @ForceInline
        public Float16MaskMax and(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16MaskMax m = (Float16MaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Float16MaskMax.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Float16MaskMax or(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16MaskMax m = (Float16MaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Float16MaskMax.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Float16MaskMax xor(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16MaskMax m = (Float16MaskMax)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Float16MaskMax.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Float16MaskMax.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Float16MaskMax.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Float16MaskMax.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Float16MaskMax.class, T_SHORT,
                                                      VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Float16MaskMax.class, T_FLOAT16, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Float16MaskMax.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> anyTrueHelper(((Float16MaskMax)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Float16MaskMax.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> allTrueHelper(((Float16MaskMax)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Float16MaskMax maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Float16MaskMax.class, T_SHORT, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Float16MaskMax  TRUE_MASK = new Float16MaskMax(true);
        private static final Float16MaskMax FALSE_MASK = new Float16MaskMax(false);

    }

    // Shuffle

    static final class Float16ShuffleMax extends AbstractShuffle<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16ShuffleMax(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        Float16ShuffleMax(int[] indices, int i) {
            this(prepare(indices, i));
        }

        Float16ShuffleMax(IntUnaryOperator fn) {
            this(prepare(fn));
        }

        short[] indices() {
            return (short[])getPayload();
        }

        @Override
        @ForceInline
        public Float16Species vspecies() {
            return VSPECIES;
        }

        static {
            // There must be enough bits in the shuffle lanes to encode
            // VLENGTH valid indexes and VLENGTH exceptional ones.
            assert(VLENGTH < Short.MAX_VALUE);
            assert(Short.MIN_VALUE <= -VLENGTH);
        }
        static final Float16ShuffleMax IOTA = new Float16ShuffleMax(IDENTITY);

        @Override
        @ForceInline
        public Float16VectorMax toVector() {
            return (Float16VectorMax) toBitsVector().castShape(vspecies(), 0);
        }

        @Override
        @ForceInline
        ShortVectorMax toBitsVector() {
            return (ShortVectorMax) super.toBitsVectorTemplate();
        }

        @Override
        ShortVectorMax toBitsVector0() {
            return ((ShortVectorMax) vspecies().asIntegral().dummyVector()).vectorFactory(indices());
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
        public final Float16MaskMax laneIsValid() {
            return (Float16MaskMax) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final Float16ShuffleMax rearrange(VectorShuffle<Float16> shuffle) {
            Float16ShuffleMax concreteShuffle = (Float16ShuffleMax) shuffle;
            return (Float16ShuffleMax) toBitsVector().rearrange(concreteShuffle.cast(ShortVector.SPECIES_MAX))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final Float16ShuffleMax wrapIndexes() {
            ShortVectorMax v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVectorMax) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVectorMax) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (Float16ShuffleMax) v.toShuffle(vspecies(), false);
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
    Float16Vector fromArray0(short[] a, int offset) {
        return super.fromArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromArray0(short[] a, int offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromArray0Template(Float16MaskMax.class, a, offset, (Float16MaskMax) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float16> m) {
        return super.fromArray0Template(Float16MaskMax.class, a, offset, indexMap, mapOffset, (Float16MaskMax) m);
    }

    @ForceInline
    @Override
    final
    Float16Vector fromCharArray0(char[] a, int offset) {
        return super.fromCharArray0Template(a, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromCharArray0(char[] a, int offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromCharArray0Template(Float16MaskMax.class, a, offset, (Float16MaskMax) m, offsetInRange);  // specialize
    }


    @ForceInline
    @Override
    final
    Float16Vector fromMemorySegment0(MemorySegment ms, long offset) {
        return super.fromMemorySegment0Template(ms, offset);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m, int offsetInRange) {
        return super.fromMemorySegment0Template(Float16MaskMax.class, ms, offset, (Float16MaskMax) m, offsetInRange);  // specialize
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
        super.intoArray0Template(Float16MaskMax.class, a, offset, (Float16MaskMax) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m) {
        super.intoMemorySegment0Template(Float16MaskMax.class, ms, offset, (Float16MaskMax) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Float16> m) {
        super.intoCharArray0Template(Float16MaskMax.class, a, offset, (Float16MaskMax) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

