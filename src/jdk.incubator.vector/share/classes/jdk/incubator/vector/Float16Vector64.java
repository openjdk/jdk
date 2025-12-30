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
final class Float16Vector64 extends Float16Vector {
    static final Float16Species VSPECIES =
        (Float16Species) Float16Vector.SPECIES_64;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Float16Vector64> VCLASS = Float16Vector64.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> CTYPE = short.class; // carrier type used by the JVM

    static final Class<Float16> ETYPE = Float16.class; // vector element type used by the JVM

    Float16Vector64(short[] v) {
        super(v);
    }

    // For compatibility as Float16Vector64::new,
    // stored into species.vectorFactory.
    Float16Vector64(Object v) {
        this((short[]) v);
    }

    static final Float16Vector64 ZERO = new Float16Vector64(new short[VLENGTH]);
    static final Float16Vector64 IOTA = new Float16Vector64(VSPECIES.iotaArray());

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
    public final Float16Vector64 broadcast(short e) {
        return (Float16Vector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Vector64 broadcast(long e) {
        return (Float16Vector64) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    Float16Mask64 maskFromArray(boolean[] bits) {
        return new Float16Mask64(bits);
    }

    @Override
    @ForceInline
    Float16Shuffle64 iotaShuffle() { return Float16Shuffle64.IOTA; }

    @Override
    @ForceInline
    Float16Shuffle64 iotaShuffle(int start, int step, boolean wrap) {
        return (Float16Shuffle64) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    Float16Shuffle64 shuffleFromArray(int[] indices, int i) { return new Float16Shuffle64(indices, i); }

    @Override
    @ForceInline
    Float16Shuffle64 shuffleFromOp(IntUnaryOperator fn) { return new Float16Shuffle64(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Float16Vector64 vectorFactory(short[] vec) {
        return new Float16Vector64(vec);
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
    Float16Vector64 uOp(FUnOp f) {
        return (Float16Vector64) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector64 uOp(VectorMask<Float16> m, FUnOp f) {
        return (Float16Vector64)
            super.uOpTemplate((Float16Mask64)m, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector64 sOp(FSnOp f) {
        return (Float16Vector64) super.sOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector64 sOp(VectorMask<Float16> m, FSnOp f) {
        return (Float16Vector64)
            super.sOpTemplate((Float16Mask64)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Float16Vector64 bOp(Vector<Float16> v, FBinOp f) {
        return (Float16Vector64) super.bOpTemplate((Float16Vector64)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector64 bOp(Vector<Float16> v,
                     VectorMask<Float16> m, FBinOp f) {
        return (Float16Vector64)
            super.bOpTemplate((Float16Vector64)v, (Float16Mask64)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Float16Vector64 tOp(Vector<Float16> v1, Vector<Float16> v2, FTriOp f) {
        return (Float16Vector64)
            super.tOpTemplate((Float16Vector64)v1, (Float16Vector64)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector64 tOp(Vector<Float16> v1, Vector<Float16> v2,
                     VectorMask<Float16> m, FTriOp f) {
        return (Float16Vector64)
            super.tOpTemplate((Float16Vector64)v1, (Float16Vector64)v2,
                              (Float16Mask64)m, f);  // specialize
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
    public Float16Vector64 lanewise(Unary op) {
        return (Float16Vector64) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 lanewise(Unary op, VectorMask<Float16> m) {
        return (Float16Vector64) super.lanewiseTemplate(op, Float16Mask64.class, (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 lanewise(Binary op, Vector<Float16> v) {
        return (Float16Vector64) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 lanewise(Binary op, Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16Vector64) super.lanewiseTemplate(op, Float16Mask64.class, v, (Float16Mask64) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    Float16Vector64
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2) {
        return (Float16Vector64) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16Vector64
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2, VectorMask<Float16> m) {
        return (Float16Vector64) super.lanewiseTemplate(op, Float16Mask64.class, v1, v2, (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16Vector64 addIndex(int scale) {
        return (Float16Vector64) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, Float16Mask64.class, (Float16Mask64) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, Float16Mask64.class, (Float16Mask64) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final Float16Shuffle64 toShuffle() {
        return (Float16Shuffle64) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final Float16Mask64 test(Test op) {
        return super.testTemplate(Float16Mask64.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask64 test(Test op, VectorMask<Float16> m) {
        return super.testTemplate(Float16Mask64.class, op, (Float16Mask64) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final Float16Mask64 compare(Comparison op, Vector<Float16> v) {
        return super.compareTemplate(Float16Mask64.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask64 compare(Comparison op, short s) {
        return super.compareTemplate(Float16Mask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask64 compare(Comparison op, long s) {
        return super.compareTemplate(Float16Mask64.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask64 compare(Comparison op, Vector<Float16> v, VectorMask<Float16> m) {
        return super.compareTemplate(Float16Mask64.class, op, v, (Float16Mask64) m);
    }


    @Override
    @ForceInline
    public Float16Vector64 blend(Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16Vector64)
            super.blendTemplate(Float16Mask64.class,
                                (Float16Vector64) v,
                                (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 slice(int origin, Vector<Float16> v) {
        return (Float16Vector64) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 slice(int origin) {
        return (Float16Vector64) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 unslice(int origin, Vector<Float16> w, int part) {
        return (Float16Vector64) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 unslice(int origin, Vector<Float16> w, int part, VectorMask<Float16> m) {
        return (Float16Vector64)
            super.unsliceTemplate(Float16Mask64.class,
                                  origin, w, part,
                                  (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 unslice(int origin) {
        return (Float16Vector64) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 rearrange(VectorShuffle<Float16> s) {
        return (Float16Vector64)
            super.rearrangeTemplate(Float16Shuffle64.class,
                                    (Float16Shuffle64) s);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 rearrange(VectorShuffle<Float16> shuffle,
                                  VectorMask<Float16> m) {
        return (Float16Vector64)
            super.rearrangeTemplate(Float16Shuffle64.class,
                                    Float16Mask64.class,
                                    (Float16Shuffle64) shuffle,
                                    (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 rearrange(VectorShuffle<Float16> s,
                                  Vector<Float16> v) {
        return (Float16Vector64)
            super.rearrangeTemplate(Float16Shuffle64.class,
                                    (Float16Shuffle64) s,
                                    (Float16Vector64) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 compress(VectorMask<Float16> m) {
        return (Float16Vector64)
            super.compressTemplate(Float16Mask64.class,
                                   (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 expand(VectorMask<Float16> m) {
        return (Float16Vector64)
            super.expandTemplate(Float16Mask64.class,
                                   (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 selectFrom(Vector<Float16> v) {
        return (Float16Vector64)
            super.selectFromTemplate((Float16Vector64) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 selectFrom(Vector<Float16> v,
                                   VectorMask<Float16> m) {
        return (Float16Vector64)
            super.selectFromTemplate((Float16Vector64) v,
                                     Float16Mask64.class, (Float16Mask64) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector64 selectFrom(Vector<Float16> v1,
                                   Vector<Float16> v2) {
        return (Float16Vector64)
            super.selectFromTemplate((Float16Vector64) v1, (Float16Vector64) v2);  // specialize
    }

    @ForceInline
    @Override
    public short lane(int i) {
        short bits;
        switch(i) {
            case 0: bits = laneHelper(0); break;
            case 1: bits = laneHelper(1); break;
            case 2: bits = laneHelper(2); break;
            case 3: bits = laneHelper(3); break;
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
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
    public Float16Vector64 withLane(int i, short e) {
        switch(i) {
            case 0: return withLaneHelper(0, e);
            case 1: return withLaneHelper(1, e);
            case 2: return withLaneHelper(2, e);
            case 3: return withLaneHelper(3, e);
            default: throw new IllegalArgumentException("Index " + i + " must be zero or positive, and less than " + VLENGTH);
        }
    }

    @ForceInline
    public Float16Vector64 withLaneHelper(int i, short e) {
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

    static final class Float16Mask64 extends AbstractMask<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16Mask64(boolean[] bits) {
            this(bits, 0);
        }

        Float16Mask64(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Float16Mask64(boolean val) {
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
        Float16Mask64 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Float16Mask64(res);
        }

        @Override
        Float16Mask64 bOp(VectorMask<Float16> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Float16Mask64)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Float16Mask64(res);
        }

        @ForceInline
        @Override
        public final
        Float16Vector64 toVector() {
            return (Float16Vector64) super.toVectorTemplate();  // specialize
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
        Float16Mask64 indexPartiallyInUpperRange(long offset, long limit) {
            return (Float16Mask64) VectorSupport.indexPartiallyInUpperRange(
                Float16Mask64.class, T_FLOAT16, VLENGTH, offset, limit,
                (o, l) -> (Float16Mask64) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public Float16Mask64 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Float16Mask64 compress() {
            return (Float16Mask64)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Float16Vector64.class, Float16Mask64.class, T_FLOAT16, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT,
                Float16.float16ToShortBits(Float16.valueOf(m1.trueCount()))));
        }


        // Binary operations

        @Override
        @ForceInline
        public Float16Mask64 and(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask64 m = (Float16Mask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Float16Mask64.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Float16Mask64 or(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask64 m = (Float16Mask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Float16Mask64.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Float16Mask64 xor(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask64 m = (Float16Mask64)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Float16Mask64.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Float16Mask64.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Float16Mask64.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Float16Mask64.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Float16Mask64.class, T_SHORT,
                                                      VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Float16Mask64.class, T_FLOAT16, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Float16Mask64.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> anyTrueHelper(((Float16Mask64)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Float16Mask64.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> allTrueHelper(((Float16Mask64)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Float16Mask64 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Float16Mask64.class, T_SHORT, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Float16Mask64  TRUE_MASK = new Float16Mask64(true);
        private static final Float16Mask64 FALSE_MASK = new Float16Mask64(false);

    }

    // Shuffle

    static final class Float16Shuffle64 extends AbstractShuffle<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16Shuffle64(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        Float16Shuffle64(int[] indices, int i) {
            this(prepare(indices, i));
        }

        Float16Shuffle64(IntUnaryOperator fn) {
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
        static final Float16Shuffle64 IOTA = new Float16Shuffle64(IDENTITY);

        @Override
        @ForceInline
        public Float16Vector64 toVector() {
            return (Float16Vector64) toBitsVector().castShape(vspecies(), 0);
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
        public final Float16Mask64 laneIsValid() {
            return (Float16Mask64) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final Float16Shuffle64 rearrange(VectorShuffle<Float16> shuffle) {
            Float16Shuffle64 concreteShuffle = (Float16Shuffle64) shuffle;
            return (Float16Shuffle64) toBitsVector().rearrange(concreteShuffle.cast(ShortVector.SPECIES_64))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final Float16Shuffle64 wrapIndexes() {
            ShortVector64 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVector64) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVector64) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (Float16Shuffle64) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(Float16Mask64.class, a, offset, (Float16Mask64) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float16> m) {
        return super.fromArray0Template(Float16Mask64.class, a, offset, indexMap, mapOffset, (Float16Mask64) m);
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
        return super.fromCharArray0Template(Float16Mask64.class, a, offset, (Float16Mask64) m, offsetInRange);  // specialize
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
        return super.fromMemorySegment0Template(Float16Mask64.class, ms, offset, (Float16Mask64) m, offsetInRange);  // specialize
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
        super.intoArray0Template(Float16Mask64.class, a, offset, (Float16Mask64) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m) {
        super.intoMemorySegment0Template(Float16Mask64.class, ms, offset, (Float16Mask64) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Float16> m) {
        super.intoCharArray0Template(Float16Mask64.class, a, offset, (Float16Mask64) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

