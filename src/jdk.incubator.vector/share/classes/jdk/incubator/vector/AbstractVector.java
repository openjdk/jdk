/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.Utils;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.VectorSupport;

import java.lang.foreign.ValueLayout;
import java.lang.reflect.Array;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

import static jdk.incubator.vector.VectorOperators.*;

@SuppressWarnings("cast")
abstract class AbstractVector<E> extends Vector<E> {
    /**
     * The order of vector bytes when stored in natural,
     * array elements of the same lane type.
     * This is the also the behavior of the
     * VectorSupport load/store instructions.
     * If these instructions gain the capability to do
     * byte swapping on the fly, add a bit to those
     * instructions, but let this polarity be the
     * "neutral" or "default" setting of the bit.
     */
    /*package-private*/
    static final ByteOrder NATIVE_ENDIAN = ByteOrder.nativeOrder();

    /**
     * The order of vector bytes as stored in the register
     * file.  This becomes visible with the asRaw[Type]Vector
     * operations, which convert between the internal byte-wise
     * representation and the typed lane-wise representation.
     * It is very possible for a platform to have big-endian
     * memory layout and little-endian register layout,
     * so this is a different setting from NATIVE_ENDIAN.
     * In fact, both Intel and ARM use LE conventions here.
     * Future work may be needed for resolutely BE platforms.
     */
    /*package-private*/
    static final ByteOrder REGISTER_ENDIAN = ByteOrder.LITTLE_ENDIAN;

    /*package-private*/
    static final int OFFSET_IN_RANGE = 1;

    /*package-private*/
    static final int OFFSET_OUT_OF_RANGE = 0;

    /*package-private*/
    AbstractVector(Object bits) {
        super(bits);
    }

    // Extractors

    /*package-private*/
    abstract AbstractSpecies<E> vspecies();

    @Override
    @ForceInline
    public final VectorSpecies<E> species() {
        return vspecies();
    }

    // Something to make types match up better:

    @Override
    @ForceInline
    public final
    <F> Vector<F> check(VectorSpecies<F> species) {
        return check0(species);
    }

    @ForceInline
    @SuppressWarnings("unchecked")
    /*package-private*/ final
    <F> AbstractVector<F> check0(VectorSpecies<F> species) {
        if (!sameSpecies(species)) {
            throw AbstractSpecies.checkFailed(this, species);
        }
        return (AbstractVector<F>) this;
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final
    <F> Vector<F> check(Class<F> elementType) {
        return check0(elementType);
    }

    @ForceInline
    @SuppressWarnings("unchecked")
    /*package-private*/ final
    <F> AbstractVector<F> check0(Class<F> elementType) {
        if (this.elementType() != elementType) {
            throw AbstractSpecies.checkFailed(this, elementType);
        }
        return (AbstractVector<F>) this;
    }

    @ForceInline
    @SuppressWarnings("unchecked")
    /*package-private*/ final
    <F> AbstractVector<F> check(Vector<F> other) {
        if (!sameSpecies(other)) {
            throw AbstractSpecies.checkFailed(this, other);
        }
        return (AbstractVector<F>) this;
    }

    @ForceInline
    private boolean sameSpecies(Vector<?> other) {
        // It's simpler and faster to do a class check.
        boolean same = (this.getClass() == other.getClass());
        // Make sure it works, too!
        assert(same == (this.species() == other.species())) : same;
        return same;
    }

    @ForceInline
    private boolean sameSpecies(VectorSpecies<?> species) {
        // It's simpler and faster to do a class check,
        // even if you have to load a dummy vector.
        AbstractVector<?> other = ((AbstractSpecies<?>)species).dummyVector();
        boolean same = (this.getClass() == other.getClass());
        // Make sure it works, too!
        assert(same == (this.species() == species)) : same;
        return same;
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final VectorMask<E> maskAll(boolean bit) {
        return species().maskAll(bit);
    }

    // Make myself into a vector of the same shape
    // and same information content but different lane type
    /*package-private*/
    abstract AbstractVector<?> asVectorRaw(LaneType laneType);

    // Make myself into a byte vector of the same shape
    /*package-private*/
    abstract ByteVector asByteVectorRaw();

    /*package-private*/
    @ForceInline
    final AbstractVector<?> asVectorRawTemplate(LaneType laneType) {
        // NOTE:  This assumes that convert0('X')
        // respects REGISTER_ENDIAN order.
        return convert0('X', vspecies().withLanes(laneType));
    }

    /*package-private*/
    @ForceInline
    ByteVector asByteVectorRawTemplate() {
        return (ByteVector) asVectorRawTemplate(LaneType.BYTE);
    }


    abstract AbstractMask<E> maskFromArray(boolean[] bits);

    abstract <F> VectorShuffle<F> bitsToShuffle(AbstractSpecies<F> dsp);

    /*package-private*/
    @ForceInline
    final <F> VectorShuffle<F> bitsToShuffleTemplate(AbstractSpecies<F> dsp) {
        Class<?> etype = vspecies().elementType();
        Class<?> dvtype = dsp.shuffleType();
        Class<?> dtype = dsp.asIntegral().elementType();
        int dlength = dsp.dummyVector().length();
        return VectorSupport.convert(VectorSupport.VECTOR_OP_CAST,
                                     getClass(), etype, length(),
                                     dvtype, dtype, dlength,
                                     this, dsp,
                                     AbstractVector::bitsToShuffle0);
    }

    abstract <F> VectorShuffle<F> bitsToShuffle0(AbstractSpecies<F> dsp);

    abstract <F> VectorShuffle<F> toShuffle(AbstractSpecies<F> dsp, boolean wrap);

    abstract AbstractShuffle<E> iotaShuffle();

    abstract AbstractShuffle<E> iotaShuffle(int start, int step, boolean wrap);

    @ForceInline
    final VectorShuffle<E> iotaShuffleTemplate(int start, int step, boolean wrap) {
        if ((length() & (length() - 1)) != 0) {
            // Uncommon path, the length is not a power of 2
            return wrap ? shuffleFromOp(i -> (VectorIntrinsics.wrapToRange(i * step + start, length())))
                        : shuffleFromOp(i -> i * step + start);
        }

        AbstractVector<?> iota = vspecies().asIntegral().iota();
        iota = (AbstractVector<?>) iota.lanewise(VectorOperators.MUL, step)
                .lanewise(VectorOperators.ADD, start);
        return iota.toShuffle(vspecies(), wrap);
    }

    abstract AbstractShuffle<E> shuffleFromArray(int[] indexes, int i);

    abstract AbstractShuffle<E> shuffleFromOp(IntUnaryOperator fn);

    /*package-private*/
    abstract AbstractVector<E> fromMemorySegment0(MemorySegment ms, long offset);

    /*package-private*/
    abstract AbstractVector<E> maybeSwap(ByteOrder bo);

    /*package-private*/
    @ForceInline
    VectorShuffle<Byte> swapBytesShuffle() {
        return vspecies().swapBytesShuffle();
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public ShortVector reinterpretAsShorts() {
        return (ShortVector) asVectorRaw(LaneType.SHORT);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public IntVector reinterpretAsInts() {
        return (IntVector) asVectorRaw(LaneType.INT);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public LongVector reinterpretAsLongs() {
        return (LongVector) asVectorRaw(LaneType.LONG);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public FloatVector reinterpretAsFloats() {
        return (FloatVector) asVectorRaw(LaneType.FLOAT);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public DoubleVector reinterpretAsDoubles() {
        return (DoubleVector) asVectorRaw(LaneType.DOUBLE);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final <F>
    Vector<F> convert(Conversion<E,F> conv, int part) {
        // Shape invariance is simple to implement.
        // It's part of the API because shape invariance
        // is the default mode of operation, and shape
        // shifting operations must advertise themselves.
        ConversionImpl<E,F> c = (ConversionImpl<E,F>) conv;
        @SuppressWarnings("unchecked")
        VectorSpecies<F> rsp = (VectorSpecies<F>)
            vspecies().withLanes(c.range());
        return convertShape(conv, rsp, part);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public final <F>
    Vector<F> castShape(VectorSpecies<F> toSpecies, int part) {
        // This is an odd mix of shape conversion plus
        // lanewise conversions.  It seems to be useful
        // sometimes as a shorthand, though maybe we
        // can drop it.
        AbstractSpecies<E> vsp = vspecies();
        AbstractSpecies<F> rsp = (AbstractSpecies<F>) toSpecies;
        @SuppressWarnings("unchecked")
        ConversionImpl<E,F> c = (ConversionImpl<E,F>)
            ConversionImpl.ofCast(vsp.laneType, rsp.laneType);
        return convertShape(c, rsp, part);
    }

    /**
     * {@inheritDoc} <!--workaround-->
     */
    @Override
    @ForceInline
    public abstract <F>
    Vector<F> convertShape(Conversion<E,F> conv, VectorSpecies<F> rsp, int part);

    /**
     * This is the template for Vector::reinterpretShape, to be
     * specialized by each distinct vector class.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> reinterpretShapeTemplate(VectorSpecies<F> toSpecies, int part) {
        AbstractSpecies<F> rsp = (AbstractSpecies<F>) toSpecies;
        AbstractSpecies<E> vsp = vspecies();
        if (part == 0) {
            // Works the same for in-place, expand, or contract.
            return convert0('X', rsp);
        } else {
            int origin = shapeChangeOrigin(vsp, rsp, false, part);
            //System.out.println("*** origin = "+origin+", part = "+part+", reinterpret");
            if (part > 0) {  // Expansion: slice first then cast.
                return slice(origin).convert0('X', rsp);
            } else {  // Contraction: cast first then unslice.
                return rsp.zero().slice(rsp.laneCount() - origin,
                                        convert0('X', rsp));
            }
        }
    }

    @Override
    public abstract AbstractVector<E> slice(int origin, Vector<E> v1);

    @Override
    public abstract AbstractVector<E> slice(int origin);

    /**
     * This is the template for Vector::convertShape, to be
     * specialized by each distinct vector class.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> convertShapeTemplate(Conversion<E,F> conv, VectorSpecies<F> toSpecies, int part) {
        ConversionImpl<E,F> c = (ConversionImpl<E,F>) conv;
        AbstractSpecies<F> rsp = (AbstractSpecies<F>) toSpecies;
        AbstractSpecies<E> vsp = vspecies();
        char kind = c.kind();
        switch (kind) {
        case 'C': // Regular cast conversion, known to the JIT.
            break;
        case 'I':  // Identity conversion => reinterpret.
            assert(c.sizeChangeLog2() == 0);
            kind = 'X';
            break;
        case 'Z':  // Lane-wise expansion with zero padding.
            assert(c.sizeChangeLog2() > 0);
            assert(c.range().elementKind == 'I');
            break;
        case 'R':  // Lane-wise reinterpret conversion.
            if (c.sizeChangeLog2() != 0) {
                kind = 'Z';  // some goofy stuff here
                break;
            }
            kind = 'X';  // No size change => reinterpret whole vector
            break;
        default:
            throw new AssertionError(c);
        }
        vsp.check(c.domain());  // apply dynamic check to conv
        rsp.check(c.range());   // apply dynamic check to conv
        if (part == 0) {
            // Works the same for in-place, expand, or contract.
            return convert0(kind, rsp);
        } else {
            int origin = shapeChangeOrigin(vsp, rsp, true, part);
            //System.out.println("*** origin = "+origin+", part = "+part+", lanewise");
            if (part > 0) {  // Expansion: slice first then cast.
                return slice(origin).convert0(kind, rsp);
            } else {  // Contraction: cast first then unslice.
                return rsp.zero().slice(rsp.laneCount() - origin,
                                        convert0(kind, rsp));
            }
        }
    }

    /**
     * Check a part number and return it multiplied by the appropriate
     * block factor to yield the origin of the operand block, as a
     * lane number.  For expansions the origin is reckoned in the
     * domain vector, since the domain vector has too much information
     * and must be sliced.  For contractions the origin is reckoned in
     * the range vector, since the range vector has too many lanes and
     * the result must be unsliced at the same position as the inverse
     * expansion.  If the conversion is lanewise, then lane sizes may
     * be changing as well.  This affects the logical size of the
     * result, and so the domain size is multiplied or divided by the
     * lane size change.
     */
    /*package-private*/
    @ForceInline
    static
    int shapeChangeOrigin(AbstractSpecies<?> dsp,
                          AbstractSpecies<?> rsp,
                          boolean lanewise,
                          int part) {
        int domSizeLog2 = dsp.vectorShape.vectorBitSizeLog2;
        int phySizeLog2 = rsp.vectorShape.vectorBitSizeLog2;
        int laneChangeLog2 = 0;
        if (lanewise) {
            laneChangeLog2 = (rsp.laneType.elementSizeLog2 -
                              dsp.laneType.elementSizeLog2);
        }
        int resSizeLog2 = domSizeLog2 + laneChangeLog2;
        // resSizeLog2 = 0 => 1-lane vector shrinking to 1-byte lane-size
        // resSizeLog2 < 0 => small vector shrinking by more than a lane-size
        assert(resSizeLog2 >= 0);
        // Expansion ratio: expansionLog2 = resSizeLog2 - phySizeLog2;
        if (!partInRange(resSizeLog2, phySizeLog2, part)) {
            // fall through...
        } else if (resSizeLog2 > phySizeLog2) {
            // Expansion by M means we must slice a block from the domain.
            // What is that block size?  It is 1/M of the domain.
            // Let's compute the log2 of that block size, as 's'.
            //s = (dsp.laneCountLog2() - expansionLog2);
            //s = ((domSizeLog2 - dsp.laneType.elementSizeLog2) - expansionLog2);
            //s = (domSizeLog2 - expansionLog2 - dsp.laneType.elementSizeLog2);
            int s = phySizeLog2 - laneChangeLog2 - dsp.laneType.elementSizeLog2;
            // Scale the part number by the input block size, in input lanes.
            if ((s & 31) == s)  // sanity check
                return part << s;
        } else {
            // Contraction by M means we must drop a block into the range.
            // What is that block size?  It is 1/M of the range.
            // Let's compute the log2 of that block size, as 's'.
            //s = (rsp.laneCountLog2() + expansionLog2);
            //s = ((phySizeLog2 - rsp.laneType.elementSizeLog2) + expansionLog2);
            //s = (phySizeLog2 + expansionLog2 - rsp.laneType.elementSizeLog2);
            int s = resSizeLog2 - rsp.laneType.elementSizeLog2;
            // Scale the part number by the output block size, in output lanes.
            if ((s & 31) == s)  // sanity check
                return -part << s;
        }
        throw wrongPart(dsp, rsp, lanewise, part);
    }

    @ForceInline
    private static boolean partInRange(int resSizeLog2, int phySizeLog2, int part) {
        // Let's try a branch-free version of this.
        int diff = (resSizeLog2 - phySizeLog2);
        int sign = (diff >> -1);
        //d = Math.abs(diff);
        //d = (sign == 0 ? diff : sign == -1 ? 1 + ~diff);
        int d = (diff ^ sign) - sign;
        assert(d == Math.abs(diff) && d <= 16);  // let's not go crazy here
        //p = part * sign;
        int p = (part ^ sign) - sign;
        // z = sign == 0  ? 0<=part<(1<<d),  == (part & (-1 << d)) == 0
        // z = sign == -1 ? 0<=-part<(1<<d), == (-part & (-1 << d)) == 0
        boolean z = (p & (-1 << d)) == 0;
        assert(z == partInRangeSlow(resSizeLog2, phySizeLog2, part)) : z;
        return z;
    }

    private static boolean partInRangeSlow(int resSizeLog2, int phySizeLog2, int part) {
        if (resSizeLog2 > phySizeLog2) {  // expansion
            int limit = 1 << (resSizeLog2 - phySizeLog2);
            return part >= 0 && part < limit;
        } else if (resSizeLog2 < phySizeLog2) {  // contraction
            int limit = 1 << (phySizeLog2 - resSizeLog2);
            return part > -limit && part <= 0;
        } else {
            return (part == 0);
        }
    }

    private static
    ArrayIndexOutOfBoundsException
    wrongPart(AbstractSpecies<?> dsp,
              AbstractSpecies<?> rsp,
              boolean lanewise,
              int part) {
        String laneChange = "";
        String converting = "converting";
        int dsize = dsp.elementSize(), rsize = rsp.elementSize();
        if (!lanewise) {
            converting = "reinterpreting";
        } else if (dsize < rsize) {
            laneChange = String.format(" (lanes are expanding by %d)",
                                       rsize / dsize);
        } else if (dsize > rsize) {
            laneChange = String.format(" (lanes are contracting by %d)",
                                       dsize / rsize);
        }
        String msg = String.format("bad part number %d %s %s -> %s%s",
                                   part, converting, dsp, rsp, laneChange);
        return new ArrayIndexOutOfBoundsException(msg);
    }

    /*package-private*/
    ArithmeticException divZeroException() {
        throw new ArithmeticException("zero vector lane in dividend "+this);
    }

    /**
     * Helper function for all sorts of byte-wise reinterpretation casts.
     * This function kicks in after intrinsic failure.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> defaultReinterpret(AbstractSpecies<F> rsp) {
        int blen = Math.max(this.bitSize(), rsp.vectorBitSize()) / Byte.SIZE;
        ByteOrder bo = ByteOrder.nativeOrder();
        MemorySegment ms = MemorySegment.ofArray(new byte[blen]);
        this.intoMemorySegment(ms, 0, bo);
        VectorMask<F> m = rsp.maskAll(true);
        // enum-switches don't optimize properly JDK-8161245
        switch (rsp.laneType.switchKey) {
        case LaneType.SK_BYTE:
            return ByteVector.fromMemorySegment(rsp.check(byte.class), ms, 0, bo, m.check(byte.class)).check0(rsp);
        case LaneType.SK_SHORT:
            return ShortVector.fromMemorySegment(rsp.check(short.class), ms, 0, bo, m.check(short.class)).check0(rsp);
        case LaneType.SK_INT:
            return IntVector.fromMemorySegment(rsp.check(int.class), ms, 0, bo, m.check(int.class)).check0(rsp);
        case LaneType.SK_LONG:
            return LongVector.fromMemorySegment(rsp.check(long.class), ms, 0, bo, m.check(long.class)).check0(rsp);
        case LaneType.SK_FLOAT:
            return FloatVector.fromMemorySegment(rsp.check(float.class), ms, 0, bo, m.check(float.class)).check0(rsp);
        case LaneType.SK_DOUBLE:
            return DoubleVector.fromMemorySegment(rsp.check(double.class), ms, 0, bo, m.check(double.class)).check0(rsp);
        default:
            throw new AssertionError(rsp.toString());
        }
    }

    /**
     * Helper function for all sorts of lane-wise conversions.
     * This function kicks in after intrinsic failure.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> defaultCast(AbstractSpecies<F> dsp) {
        int rlength = dsp.laneCount;
        if (vspecies().laneType.elementKind == 'F') {
            // Buffer input values in a double array.
            double[] lanes = toDoubleArray();
            int limit = Math.min(lanes.length, rlength);
            // enum-switches don't optimize properly JDK-8161245
            switch (dsp.laneType.switchKey) {
            case LaneType.SK_BYTE: {
                byte[] a = new byte[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (byte) lanes[i];
                }
                return ByteVector.fromArray(dsp.check(byte.class), a, 0).check0(dsp);
            }
            case LaneType.SK_SHORT: {
                short[] a = new short[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (short) lanes[i];
                }
                return ShortVector.fromArray(dsp.check(short.class), a, 0).check0(dsp);
            }
            case LaneType.SK_INT: {
                int[] a = new int[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) lanes[i];
                }
                return IntVector.fromArray(dsp.check(int.class), a, 0).check0(dsp);
            }
            case LaneType.SK_LONG: {
                long[] a = new long[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) lanes[i];
                }
                return LongVector.fromArray(dsp.check(long.class), a, 0).check0(dsp);
            }
            case LaneType.SK_FLOAT: {
                float[] a = new float[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (float) lanes[i];
                }
                return FloatVector.fromArray(dsp.check(float.class), a, 0).check0(dsp);
            }
            case LaneType.SK_DOUBLE: {
                double[] a = new double[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (double) lanes[i];
                }
                return DoubleVector.fromArray(dsp.check(double.class), a, 0).check0(dsp);
            }
            default: break;
            }
        } else {
            // Buffer input values in a long array.
            long[] lanes = toLongArray();
            int limit = Math.min(lanes.length, rlength);
            // enum-switches don't optimize properly JDK-8161245
            switch (dsp.laneType.switchKey) {
            case LaneType.SK_BYTE: {
                byte[] a = new byte[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (byte) lanes[i];
                }
                return ByteVector.fromArray(dsp.check(byte.class), a, 0).check0(dsp);
            }
            case LaneType.SK_SHORT: {
                short[] a = new short[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (short) lanes[i];
                }
                return ShortVector.fromArray(dsp.check(short.class), a, 0).check0(dsp);
            }
            case LaneType.SK_INT: {
                int[] a = new int[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (int) lanes[i];
                }
                return IntVector.fromArray(dsp.check(int.class), a, 0).check0(dsp);
            }
            case LaneType.SK_LONG: {
                long[] a = new long[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (long) lanes[i];
                }
                return LongVector.fromArray(dsp.check(long.class), a, 0).check0(dsp);
            }
            case LaneType.SK_FLOAT: {
                float[] a = new float[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (float) lanes[i];
                }
                return FloatVector.fromArray(dsp.check(float.class), a, 0).check0(dsp);
            }
            case LaneType.SK_DOUBLE: {
                double[] a = new double[rlength];
                for (int i = 0; i < limit; i++) {
                    a[i] = (double) lanes[i];
                }
                return DoubleVector.fromArray(dsp.check(double.class), a, 0).check0(dsp);
            }
            default: break;
            }
        }
        throw new AssertionError();
    }

    /**
     * Helper function for all sorts of lane-wise unsigned conversions.
     * This function kicks in after intrinsic failure.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> defaultUCast(AbstractSpecies<F> dsp) {
        AbstractSpecies<?> vsp = this.vspecies();
        if (vsp.elementSize() >= dsp.elementSize()) {
            // clip in place
            return this.convert0('C', dsp);
        } else {
            // extend in place, but remove unwanted sign extension
            long mask = -1L >>> -vsp.elementSize();
            return (AbstractVector<F>) this.convert0('C', dsp).lanewise(AND, dsp.broadcast(mask));
        }
    }

    // Constant-folded access to conversion intrinsics:

    /**
     * Dispatch on conversion kind and target species.
     * The code of this is arranged to fold up if the
     * vector class is constant and the target species
     * is also constant.  This is often the case.
     * Residual non-folded code may also perform acceptably
     * in some cases due to type profiling, especially
     * of rvtype.  If only one shape is being used,
     * the profiling of rvtype should help speculatively
     * fold the code even when the target species is
     * not a constant.
     */
    /*package-private*/
    @ForceInline
    final <F>
    AbstractVector<F> convert0(char kind, AbstractSpecies<F> rsp) {
        // Derive some JIT-time constants:
        Class<?> vtype;
        Class<?> etype;   // fill in after switch (constant)
        int vlength;      // fill in after switch (mark type profile?)
        Class<?> rvtype;  // fill in after switch (mark type profile)
        Class<?> rtype;
        int rlength;
        switch (kind) {
        case 'Z':  // lane-wise size change, maybe with sign clip
            AbstractSpecies<?> rspi = rsp.asIntegral();
            AbstractSpecies<?> vsp = this.vspecies();
            AbstractSpecies<?> vspi = vsp.asIntegral();
            AbstractVector<?> biti = vspi == vsp ? this : this.convert0('X', vspi);
            rtype = rspi.elementType();
            rlength = rspi.laneCount();
            etype = vspi.elementType();
            vlength = vspi.laneCount();
            rvtype = rspi.dummyVector().getClass();
            vtype = vspi.dummyVector().getClass();
            int opc = vspi.elementSize() < rspi.elementSize() ? VectorSupport.VECTOR_OP_UCAST : VectorSupport.VECTOR_OP_CAST;
            AbstractVector<?> bitv = VectorSupport.convert(opc,
                    vtype, etype, vlength,
                    rvtype, rtype, rlength,
                    biti, rspi,
                    AbstractVector::defaultUCast);
            return (rspi == rsp ? bitv.check0(rsp) : bitv.convert0('X', rsp));
        case 'C':  // lane-wise cast (but not identity)
            rtype = rsp.elementType();
            rlength = rsp.laneCount();
            etype = this.elementType(); // (profile)
            vlength = this.length();  // (profile)
            rvtype = rsp.dummyVector().getClass();  // (profile)
            vtype = this.getClass();
            return VectorSupport.convert(VectorSupport.VECTOR_OP_CAST,
                    vtype, etype, vlength,
                    rvtype, rtype, rlength,
                    this, rsp,
                    AbstractVector::defaultCast);
        case 'X':  // reinterpret cast, not lane-wise if lane sizes differ
            rtype = rsp.elementType();
            rlength = rsp.laneCount();
            etype = this.elementType(); // (profile)
            vlength = this.length();  // (profile)
            rvtype = rsp.dummyVector().getClass();  // (profile)
            vtype = this.getClass();
            return VectorSupport.convert(VectorSupport.VECTOR_OP_REINTERPRET,
                    vtype, etype, vlength,
                    rvtype, rtype, rlength,
                    this, rsp,
                    AbstractVector::defaultReinterpret);
        }
        throw new AssertionError();
    }

    static {
        // Recode uses of VectorSupport.reinterpret if this assertion fails:
        assert(REGISTER_ENDIAN == ByteOrder.LITTLE_ENDIAN);
    }
}
