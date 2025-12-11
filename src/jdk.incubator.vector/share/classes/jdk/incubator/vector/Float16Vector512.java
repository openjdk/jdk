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
final class Float16Vector512 extends Float16Vector {
    static final Float16Species VSPECIES =
        (Float16Species) Float16Vector.SPECIES_512;

    static final VectorShape VSHAPE =
        VSPECIES.vectorShape();

    static final Class<Float16Vector512> VCLASS = Float16Vector512.class;

    static final int VSIZE = VSPECIES.vectorBitSize();

    static final int VLENGTH = VSPECIES.laneCount(); // used by the JVM

    static final Class<Short> CTYPE = short.class; // carrier type used by the JVM

    static final Class<Float16> ETYPE = Float16.class; // vector element type used by the JVM

    Float16Vector512(short[] v) {
        super(v);
    }

    // For compatibility as Float16Vector512::new,
    // stored into species.vectorFactory.
    Float16Vector512(Object v) {
        this((short[]) v);
    }

    static final Float16Vector512 ZERO = new Float16Vector512(new short[VLENGTH]);
    static final Float16Vector512 IOTA = new Float16Vector512(VSPECIES.iotaArray());

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
    public final Float16Vector512 broadcast(short e) {
        return (Float16Vector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Vector512 broadcast(long e) {
        return (Float16Vector512) super.broadcastTemplate(e);  // specialize
    }

    @Override
    @ForceInline
    Float16Mask512 maskFromArray(boolean[] bits) {
        return new Float16Mask512(bits);
    }

    @Override
    @ForceInline
    Float16Shuffle512 iotaShuffle() { return Float16Shuffle512.IOTA; }

    @Override
    @ForceInline
    Float16Shuffle512 iotaShuffle(int start, int step, boolean wrap) {
        return (Float16Shuffle512) iotaShuffleTemplate((short) start, (short) step, wrap);
    }

    @Override
    @ForceInline
    Float16Shuffle512 shuffleFromArray(int[] indices, int i) { return new Float16Shuffle512(indices, i); }

    @Override
    @ForceInline
    Float16Shuffle512 shuffleFromOp(IntUnaryOperator fn) { return new Float16Shuffle512(fn); }

    // Make a vector of the same species but the given elements:
    @ForceInline
    final @Override
    Float16Vector512 vectorFactory(short[] vec) {
        return new Float16Vector512(vec);
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
    Float16Vector512 uOp(FUnOp f) {
        return (Float16Vector512) super.uOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector512 uOp(VectorMask<Float16> m, FUnOp f) {
        return (Float16Vector512)
            super.uOpTemplate((Float16Mask512)m, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector512 sOp(FSnOp f) {
        return (Float16Vector512) super.sOpTemplate(f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector512 sOp(VectorMask<Float16> m, FSnOp f) {
        return (Float16Vector512)
            super.sOpTemplate((Float16Mask512)m, f);  // specialize
    }

    // Binary operator

    @ForceInline
    final @Override
    Float16Vector512 bOp(Vector<Float16> v, FBinOp f) {
        return (Float16Vector512) super.bOpTemplate((Float16Vector512)v, f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector512 bOp(Vector<Float16> v,
                     VectorMask<Float16> m, FBinOp f) {
        return (Float16Vector512)
            super.bOpTemplate((Float16Vector512)v, (Float16Mask512)m,
                              f);  // specialize
    }

    // Ternary operator

    @ForceInline
    final @Override
    Float16Vector512 tOp(Vector<Float16> v1, Vector<Float16> v2, FTriOp f) {
        return (Float16Vector512)
            super.tOpTemplate((Float16Vector512)v1, (Float16Vector512)v2,
                              f);  // specialize
    }

    @ForceInline
    final @Override
    Float16Vector512 tOp(Vector<Float16> v1, Vector<Float16> v2,
                     VectorMask<Float16> m, FTriOp f) {
        return (Float16Vector512)
            super.tOpTemplate((Float16Vector512)v1, (Float16Vector512)v2,
                              (Float16Mask512)m, f);  // specialize
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
    public Float16Vector512 lanewise(Unary op) {
        return (Float16Vector512) super.lanewiseTemplate(op);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 lanewise(Unary op, VectorMask<Float16> m) {
        return (Float16Vector512) super.lanewiseTemplate(op, Float16Mask512.class, (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 lanewise(Binary op, Vector<Float16> v) {
        return (Float16Vector512) super.lanewiseTemplate(op, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 lanewise(Binary op, Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16Vector512) super.lanewiseTemplate(op, Float16Mask512.class, v, (Float16Mask512) m);  // specialize
    }


    /*package-private*/
    @Override
    @ForceInline
    public final
    Float16Vector512
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2) {
        return (Float16Vector512) super.lanewiseTemplate(op, v1, v2);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16Vector512
    lanewise(Ternary op, Vector<Float16> v1, Vector<Float16> v2, VectorMask<Float16> m) {
        return (Float16Vector512) super.lanewiseTemplate(op, Float16Mask512.class, v1, v2, (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public final
    Float16Vector512 addIndex(int scale) {
        return (Float16Vector512) super.addIndexTemplate(scale);  // specialize
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
        return super.reduceLanesTemplate(op, Float16Mask512.class, (Float16Mask512) m);  // specialized
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
        return (long) super.reduceLanesTemplate(op, Float16Mask512.class, (Float16Mask512) m);  // specialized
    }

    @Override
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp) {
        throw new AssertionError();
    }

    @Override
    @ForceInline
    public final Float16Shuffle512 toShuffle() {
        return (Float16Shuffle512) toShuffle(vspecies(), false);
    }

    // Specialized unary testing

    @Override
    @ForceInline
    public final Float16Mask512 test(Test op) {
        return super.testTemplate(Float16Mask512.class, op);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask512 test(Test op, VectorMask<Float16> m) {
        return super.testTemplate(Float16Mask512.class, op, (Float16Mask512) m);  // specialize
    }

    // Specialized comparisons

    @Override
    @ForceInline
    public final Float16Mask512 compare(Comparison op, Vector<Float16> v) {
        return super.compareTemplate(Float16Mask512.class, op, v);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask512 compare(Comparison op, short s) {
        return super.compareTemplate(Float16Mask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask512 compare(Comparison op, long s) {
        return super.compareTemplate(Float16Mask512.class, op, s);  // specialize
    }

    @Override
    @ForceInline
    public final Float16Mask512 compare(Comparison op, Vector<Float16> v, VectorMask<Float16> m) {
        return super.compareTemplate(Float16Mask512.class, op, v, (Float16Mask512) m);
    }


    @Override
    @ForceInline
    public Float16Vector512 blend(Vector<Float16> v, VectorMask<Float16> m) {
        return (Float16Vector512)
            super.blendTemplate(Float16Mask512.class,
                                (Float16Vector512) v,
                                (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 slice(int origin, Vector<Float16> v) {
        return (Float16Vector512) super.sliceTemplate(origin, v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 slice(int origin) {
        return (Float16Vector512) super.sliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 unslice(int origin, Vector<Float16> w, int part) {
        return (Float16Vector512) super.unsliceTemplate(origin, w, part);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 unslice(int origin, Vector<Float16> w, int part, VectorMask<Float16> m) {
        return (Float16Vector512)
            super.unsliceTemplate(Float16Mask512.class,
                                  origin, w, part,
                                  (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 unslice(int origin) {
        return (Float16Vector512) super.unsliceTemplate(origin);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 rearrange(VectorShuffle<Float16> s) {
        return (Float16Vector512)
            super.rearrangeTemplate(Float16Shuffle512.class,
                                    (Float16Shuffle512) s);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 rearrange(VectorShuffle<Float16> shuffle,
                                  VectorMask<Float16> m) {
        return (Float16Vector512)
            super.rearrangeTemplate(Float16Shuffle512.class,
                                    Float16Mask512.class,
                                    (Float16Shuffle512) shuffle,
                                    (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 rearrange(VectorShuffle<Float16> s,
                                  Vector<Float16> v) {
        return (Float16Vector512)
            super.rearrangeTemplate(Float16Shuffle512.class,
                                    (Float16Shuffle512) s,
                                    (Float16Vector512) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 compress(VectorMask<Float16> m) {
        return (Float16Vector512)
            super.compressTemplate(Float16Mask512.class,
                                   (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 expand(VectorMask<Float16> m) {
        return (Float16Vector512)
            super.expandTemplate(Float16Mask512.class,
                                   (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 selectFrom(Vector<Float16> v) {
        return (Float16Vector512)
            super.selectFromTemplate((Float16Vector512) v);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 selectFrom(Vector<Float16> v,
                                   VectorMask<Float16> m) {
        return (Float16Vector512)
            super.selectFromTemplate((Float16Vector512) v,
                                     Float16Mask512.class, (Float16Mask512) m);  // specialize
    }

    @Override
    @ForceInline
    public Float16Vector512 selectFrom(Vector<Float16> v1,
                                   Vector<Float16> v2) {
        return (Float16Vector512)
            super.selectFromTemplate((Float16Vector512) v1, (Float16Vector512) v2);  // specialize
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
            case 16: bits = laneHelper(16); break;
            case 17: bits = laneHelper(17); break;
            case 18: bits = laneHelper(18); break;
            case 19: bits = laneHelper(19); break;
            case 20: bits = laneHelper(20); break;
            case 21: bits = laneHelper(21); break;
            case 22: bits = laneHelper(22); break;
            case 23: bits = laneHelper(23); break;
            case 24: bits = laneHelper(24); break;
            case 25: bits = laneHelper(25); break;
            case 26: bits = laneHelper(26); break;
            case 27: bits = laneHelper(27); break;
            case 28: bits = laneHelper(28); break;
            case 29: bits = laneHelper(29); break;
            case 30: bits = laneHelper(30); break;
            case 31: bits = laneHelper(31); break;
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
    public Float16Vector512 withLane(int i, short e) {
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
    public Float16Vector512 withLaneHelper(int i, short e) {
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

    static final class Float16Mask512 extends AbstractMask<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16Mask512(boolean[] bits) {
            this(bits, 0);
        }

        Float16Mask512(boolean[] bits, int offset) {
            super(prepare(bits, offset));
        }

        Float16Mask512(boolean val) {
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
        Float16Mask512 uOp(MUnOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i]);
            }
            return new Float16Mask512(res);
        }

        @Override
        Float16Mask512 bOp(VectorMask<Float16> m, MBinOp f) {
            boolean[] res = new boolean[vspecies().laneCount()];
            boolean[] bits = getBits();
            boolean[] mbits = ((Float16Mask512)m).getBits();
            for (int i = 0; i < res.length; i++) {
                res[i] = f.apply(i, bits[i], mbits[i]);
            }
            return new Float16Mask512(res);
        }

        @ForceInline
        @Override
        public final
        Float16Vector512 toVector() {
            return (Float16Vector512) super.toVectorTemplate();  // specialize
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
        Float16Mask512 indexPartiallyInUpperRange(long offset, long limit) {
            return (Float16Mask512) VectorSupport.indexPartiallyInUpperRange(
                Float16Mask512.class, T_FLOAT16, VLENGTH, offset, limit,
                (o, l) -> (Float16Mask512) TRUE_MASK.indexPartiallyInRange(o, l));
        }

        // Unary operations

        @Override
        @ForceInline
        public Float16Mask512 not() {
            return xor(maskAll(true));
        }

        @Override
        @ForceInline
        public Float16Mask512 compress() {
            return (Float16Mask512)VectorSupport.compressExpandOp(VectorSupport.VECTOR_OP_MASK_COMPRESS,
                Float16Vector512.class, Float16Mask512.class, T_FLOAT16, VLENGTH, null, this,
                (v1, m1) -> VSPECIES.iota().compare(VectorOperators.LT,
                Float16.float16ToShortBits(Float16.valueOf(m1.trueCount()))));
        }


        // Binary operations

        @Override
        @ForceInline
        public Float16Mask512 and(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask512 m = (Float16Mask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_AND, Float16Mask512.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a & b));
        }

        @Override
        @ForceInline
        public Float16Mask512 or(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask512 m = (Float16Mask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_OR, Float16Mask512.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a | b));
        }

        @Override
        @ForceInline
        public Float16Mask512 xor(VectorMask<Float16> mask) {
            Objects.requireNonNull(mask);
            Float16Mask512 m = (Float16Mask512)mask;
            return VectorSupport.binaryOp(VECTOR_OP_XOR, Float16Mask512.class, null, T_SHORT, VLENGTH,
                                          this, m, null,
                                          (m1, m2, vm) -> m1.bOp(m2, (i, a, b) -> a ^ b));
        }

        // Mask Query operations

        @Override
        @ForceInline
        public int trueCount() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TRUECOUNT, Float16Mask512.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> trueCountHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int firstTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_FIRSTTRUE, Float16Mask512.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> firstTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public int lastTrue() {
            return (int) VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_LASTTRUE, Float16Mask512.class, T_SHORT,
                                                            VLENGTH, this,
                                                            (m) -> lastTrueHelper(m.getBits()));
        }

        @Override
        @ForceInline
        public long toLong() {
            if (length() > Long.SIZE) {
                throw new UnsupportedOperationException("too many lanes for one long");
            }
            return VectorSupport.maskReductionCoerced(VECTOR_OP_MASK_TOLONG, Float16Mask512.class, T_SHORT,
                                                      VLENGTH, this,
                                                      (m) -> toLongHelper(m.getBits()));
        }

        // laneIsSet

        @Override
        @ForceInline
        public boolean laneIsSet(int i) {
            Objects.checkIndex(i, length());
            return VectorSupport.extract(Float16Mask512.class, T_FLOAT16, VLENGTH,
                                         this, i, (m, idx) -> (m.getBits()[idx] ? 1L : 0L)) == 1L;
        }

        // Reductions

        @Override
        @ForceInline
        public boolean anyTrue() {
            return VectorSupport.test(BT_ne, Float16Mask512.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> anyTrueHelper(((Float16Mask512)m).getBits()));
        }

        @Override
        @ForceInline
        public boolean allTrue() {
            return VectorSupport.test(BT_overflow, Float16Mask512.class, T_SHORT, VLENGTH,
                                      this, vspecies().maskAll(true),
                                      (m, __) -> allTrueHelper(((Float16Mask512)m).getBits()));
        }

        @ForceInline
        /*package-private*/
        static Float16Mask512 maskAll(boolean bit) {
            return VectorSupport.fromBitsCoerced(Float16Mask512.class, T_SHORT, VLENGTH,
                                                 (bit ? -1 : 0), MODE_BROADCAST, null,
                                                 (v, __) -> (v != 0 ? TRUE_MASK : FALSE_MASK));
        }
        private static final Float16Mask512  TRUE_MASK = new Float16Mask512(true);
        private static final Float16Mask512 FALSE_MASK = new Float16Mask512(false);

    }

    // Shuffle

    static final class Float16Shuffle512 extends AbstractShuffle<Float16> {
        static final int VLENGTH = VSPECIES.laneCount();    // used by the JVM

        static final Class<Short> CTYPE = short.class; // used by the JVM

        Float16Shuffle512(short[] indices) {
            super(indices);
            assert(VLENGTH == indices.length);
            assert(indicesInRange(indices));
        }

        Float16Shuffle512(int[] indices, int i) {
            this(prepare(indices, i));
        }

        Float16Shuffle512(IntUnaryOperator fn) {
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
        static final Float16Shuffle512 IOTA = new Float16Shuffle512(IDENTITY);

        @Override
        @ForceInline
        public Float16Vector512 toVector() {
            return (Float16Vector512) toBitsVector().castShape(vspecies(), 0);
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
        public final Float16Mask512 laneIsValid() {
            return (Float16Mask512) toBitsVector().compare(VectorOperators.GE, 0)
                    .cast(vspecies());
        }

        @ForceInline
        @Override
        public final Float16Shuffle512 rearrange(VectorShuffle<Float16> shuffle) {
            Float16Shuffle512 concreteShuffle = (Float16Shuffle512) shuffle;
            return (Float16Shuffle512) toBitsVector().rearrange(concreteShuffle.cast(ShortVector.SPECIES_512))
                    .toShuffle(vspecies(), false);
        }

        @ForceInline
        @Override
        public final Float16Shuffle512 wrapIndexes() {
            ShortVector512 v = toBitsVector();
            if ((length() & (length() - 1)) == 0) {
                v = (ShortVector512) v.lanewise(VectorOperators.AND, length() - 1);
            } else {
                v = (ShortVector512) v.blend(v.lanewise(VectorOperators.ADD, length()),
                            v.compare(VectorOperators.LT, 0));
            }
            return (Float16Shuffle512) v.toShuffle(vspecies(), false);
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
        return super.fromArray0Template(Float16Mask512.class, a, offset, (Float16Mask512) m, offsetInRange);  // specialize
    }

    @ForceInline
    @Override
    final
    Float16Vector fromArray0(short[] a, int offset, int[] indexMap, int mapOffset, VectorMask<Float16> m) {
        return super.fromArray0Template(Float16Mask512.class, a, offset, indexMap, mapOffset, (Float16Mask512) m);
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
        return super.fromCharArray0Template(Float16Mask512.class, a, offset, (Float16Mask512) m, offsetInRange);  // specialize
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
        return super.fromMemorySegment0Template(Float16Mask512.class, ms, offset, (Float16Mask512) m, offsetInRange);  // specialize
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
        super.intoArray0Template(Float16Mask512.class, a, offset, (Float16Mask512) m);
    }



    @ForceInline
    @Override
    final
    void intoMemorySegment0(MemorySegment ms, long offset, VectorMask<Float16> m) {
        super.intoMemorySegment0Template(Float16Mask512.class, ms, offset, (Float16Mask512) m);
    }

    @ForceInline
    @Override
    final
    void intoCharArray0(char[] a, int offset, VectorMask<Float16> m) {
        super.intoCharArray0Template(Float16Mask512.class, a, offset, (Float16Mask512) m);
    }

    // End of specialized low-level memory operations.

    // ================================================

}

