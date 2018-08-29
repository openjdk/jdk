/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.util.math.intpoly;

import sun.security.util.math.*;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * A large number polynomial representation using sparse limbs of signed
 * long (64-bit) values. Limb values will always fit within a long, so inputs
 * to multiplication must be less than 32 bits. All IntegerPolynomial
 * implementations allow at most one addition before multiplication. Additions
 * after that will result in an ArithmeticException.
 *
 * The following element operations are branch-free for all subclasses:
 *
 * fixed
 * mutable
 * add
 * additiveInverse
 * multiply
 * square
 * subtract
 * conditionalSwapWith
 * setValue (may branch on high-order byte parameter only)
 * setSum
 * setDifference
 * setProduct
 * setSquare
 * addModPowerTwo
 * asByteArray
 *
 * All other operations may branch in some subclasses.
 *
 */

public abstract class IntegerPolynomial implements IntegerFieldModuloP {

    protected static final BigInteger TWO = BigInteger.valueOf(2);

    protected final int numLimbs;
    private final BigInteger modulus;
    protected final int bitsPerLimb;
    private final long[] posModLimbs;

    /**
     * Multiply an IntegerPolynomial representation (a) with a long (b) and
     * store the result in an IntegerPolynomial representation (r). Requires
     * that a.length == r.length == numLimbs. It is allowed for a and r to be
     * the same array.
     */
    protected abstract void multByInt(long[] a, long b, long[] r);

    /**
     * Multiply two IntegerPolynomial representations (a and b) and store the
     * result in an IntegerPolynomial representation (r). Requires that
     * a.length == b.length == r.length == numLimbs. It is allowed for a and r
     * to be the same array.
     */
    protected abstract void mult(long[] a, long[] b, long[] r);

    /**
     * Multiply an IntegerPolynomial representation (a) with itself and store
     * the result in an IntegerPolynomialRepresentation (r). Requires that
     * a.length == r.length == numLimbs. It is allowed for a and r
     * to be the same array.
     */
    protected abstract void square(long[] a, long[] r);

    IntegerPolynomial(int bitsPerLimb,
                      int numLimbs,
                      BigInteger modulus) {


        this.numLimbs = numLimbs;
        this.modulus = modulus;
        this.bitsPerLimb = bitsPerLimb;

        posModLimbs = setPosModLimbs();
    }

    private long[] setPosModLimbs() {
        long[] result = new long[numLimbs];
        setLimbsValuePositive(modulus, result);
        return result;
    }

    protected int getNumLimbs() {
        return numLimbs;
    }

    @Override
    public BigInteger getSize() {
        return modulus;
    }

    @Override
    public ImmutableElement get0() {
        return new ImmutableElement(false);
    }

    @Override
    public ImmutableElement get1() {
        return new ImmutableElement(true);
    }

    @Override
    public ImmutableElement getElement(BigInteger v) {
        return new ImmutableElement(v);
    }

    @Override
    public SmallValue getSmallValue(int value) {
        int maxMag = 1 << (bitsPerLimb - 1);
        if (Math.abs(value) >= maxMag) {
            throw new IllegalArgumentException(
                "max magnitude is " + maxMag);
        }
        return new Limb(value);
    }

    /**
     * This version of encode takes a ByteBuffer that is properly ordered, and
     * may extract larger values (e.g. long) from the ByteBuffer for better
     * performance. The implementation below only extracts bytes from the
     * buffer, but this method may be overridden in field-specific
     * implementations.
     */
    protected void encode(ByteBuffer buf, int length, byte highByte,
                          long[] result) {
        int numHighBits = 32 - Integer.numberOfLeadingZeros(highByte);
        int numBits = 8 * length + numHighBits;
        int maxBits = bitsPerLimb * result.length;
        if (numBits > maxBits) {
            throw new ArithmeticException("Value is too large.");
        }

        int limbIndex = 0;
        long curLimbValue = 0;
        int bitPos = 0;
        for (int i = 0; i < length; i++) {
            long curV = buf.get() & 0xFF;

            if (bitPos + 8 >= bitsPerLimb) {
                int bitsThisLimb = bitsPerLimb - bitPos;
                curLimbValue += (curV & (0xFF >> (8 - bitsThisLimb))) << bitPos;
                result[limbIndex++] = curLimbValue;
                curLimbValue = curV >> bitsThisLimb;
                bitPos = 8 - bitsThisLimb;
            }
            else {
                curLimbValue += curV << bitPos;
                bitPos += 8;
            }
        }

        // one more for the high byte
        if (highByte != 0) {
            long curV = highByte & 0xFF;
            if (bitPos + 8 >= bitsPerLimb) {
                int bitsThisLimb = bitsPerLimb - bitPos;
                curLimbValue += (curV & (0xFF >> (8 - bitsThisLimb))) << bitPos;
                result[limbIndex++] = curLimbValue;
                curLimbValue = curV >> bitsThisLimb;
            }
            else {
                curLimbValue += curV << bitPos;
            }
        }

        if (limbIndex < numLimbs) {
            result[limbIndex++] = curLimbValue;
        }
        Arrays.fill(result, limbIndex, numLimbs, 0);

        postEncodeCarry(result);
    }

    protected void encode(byte[] v, int offset, int length, byte highByte,
                          long[] result) {

        ByteBuffer buf = ByteBuffer.wrap(v, offset, length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        encode(buf, length, highByte, result);
    }

    protected void postEncodeCarry(long[] v) {
        carry(v);
    }

    public ImmutableElement getElement(byte[] v, int offset, int length,
                                       byte highByte) {

        long[] result = new long[numLimbs];

        encode(v, offset, length, highByte, result);

        return new ImmutableElement(result, true);
    }

    protected BigInteger evaluate(long[] limbs) {
        BigInteger result = BigInteger.ZERO;
        for (int i = limbs.length - 1; i >= 0; i--) {
            result = result.shiftLeft(bitsPerLimb)
                .add(BigInteger.valueOf(limbs[i]));
        }
        return result.mod(modulus);
    }

    protected long carryValue(long x) {
        // compressing carry operation
        // if large positive number, carry one more to make it negative
        // if large negative number (closer to zero), carry one fewer
        return (x + (1 << (bitsPerLimb - 1))) >> bitsPerLimb;
    }

    protected void carry(long[] limbs, int start, int end) {

        for (int i = start; i < end; i++) {

            long carry = carryOut(limbs, i);
            limbs[i + 1] += carry;
        }
    }

    protected void carry(long[] limbs) {

        carry(limbs, 0, limbs.length - 1);
    }

    /**
     * Carry out of the specified position and return the carry value.
     */
    protected long carryOut(long[] limbs, int index) {
        long carry = carryValue(limbs[index]);
        limbs[index] -= (carry << bitsPerLimb);
        return carry;
    }

    private void setLimbsValue(BigInteger v, long[] limbs) {
        // set all limbs positive, and then carry
        setLimbsValuePositive(v, limbs);
        carry(limbs);
    }

    protected void setLimbsValuePositive(BigInteger v, long[] limbs) {
        BigInteger mod = BigInteger.valueOf(1 << bitsPerLimb);
        for (int i = 0; i < limbs.length; i++) {
            limbs[i] = v.mod(mod).longValue();
            v = v.shiftRight(bitsPerLimb);
        }
    }

    /**
     * Carry out of the last limb and reduce back in. This method will be
     * called as part of the "finalReduce" operation that puts the
     * representation into a fully-reduced form. It is representation-
     * specific, because representations have different amounts of empty
     * space in the high-order limb. Requires that limbs.length=numLimbs.
     */
    protected abstract void finalCarryReduceLast(long[] limbs);

    /**
     * Convert reduced limbs into a number between 0 and MODULUS-1.
     * Requires that limbs.length == numLimbs. This method only works if the
     * modulus has at most three terms.
     */
    protected void finalReduce(long[] limbs) {

        // This method works by doing several full carry/reduce operations.
        // Some representations have extra high bits, so the carry/reduce out
        // of the high position is implementation-specific. The "unsigned"
        // carry operation always carries some (negative) value out of a
        // position occupied by a negative value. So after a number of
        // passes, all negative values are removed.

        // The first pass may leave a negative value in the high position, but
        // this only happens if something was carried out of the previous
        // position. So the previous position must have a "small" value. The
        // next full carry is guaranteed not to carry out of that position.

        for (int pass = 0; pass < 2; pass++) {
            // unsigned carry out of last position and reduce in to
            // first position
            finalCarryReduceLast(limbs);

            // unsigned carry on all positions
            long carry = 0;
            for (int i = 0; i < numLimbs - 1; i++) {
                limbs[i] += carry;
                carry = limbs[i] >> bitsPerLimb;
                limbs[i] -= carry << bitsPerLimb;
            }
            limbs[numLimbs - 1] += carry;
        }

        // Limbs are positive and all less than 2^bitsPerLimb, and the
        // high-order limb may be even smaller due to the representation-
        // specific carry/reduce out of the high position.
        // The value may still be greater than the modulus.
        // Subtract the max limb values only if all limbs end up non-negative
        // This only works if there is at most one position where posModLimbs
        // is less than 2^bitsPerLimb - 1 (not counting the high-order limb,
        // if it has extra bits that are cleared by finalCarryReduceLast).
        int smallerNonNegative = 1;
        long[] smaller = new long[numLimbs];
        for (int i = numLimbs - 1; i >= 0; i--) {
            smaller[i] = limbs[i] - posModLimbs[i];
            // expression on right is 1 if smaller[i] is nonnegative,
            // 0 otherwise
            smallerNonNegative *= (int) (smaller[i] >> 63) + 1;
        }
        conditionalSwap(smallerNonNegative, limbs, smaller);

    }

    /**
     * Decode the value in v and store it in dst. Requires that v is final
     * reduced. I.e. all limbs in [0, 2^bitsPerLimb) and value in [0, modulus).
     */
    protected void decode(long[] v, byte[] dst, int offset, int length) {

        int nextLimbIndex = 0;
        long curLimbValue = v[nextLimbIndex++];
        int bitPos = 0;
        for (int i = 0; i < length; i++) {

            int dstIndex = i + offset;
            if (bitPos + 8 >= bitsPerLimb) {
                dst[dstIndex] = (byte) curLimbValue;
                curLimbValue = 0;
                if (nextLimbIndex < v.length) {
                    curLimbValue = v[nextLimbIndex++];
                }
                int bitsAdded = bitsPerLimb - bitPos;
                int bitsLeft = 8 - bitsAdded;

                dst[dstIndex] += (curLimbValue & (0xFF >> bitsAdded))
                    << bitsAdded;
                curLimbValue >>= bitsLeft;
                bitPos = bitsLeft;
            } else {
                dst[dstIndex] = (byte) curLimbValue;
                curLimbValue >>= 8;
                bitPos += 8;
            }
        }
    }

    /**
     * Add two IntegerPolynomial representations (a and b) and store the result
     * in an IntegerPolynomialRepresentation (dst). Requires that
     * a.length == b.length == dst.length. It is allowed for a and
     * dst to be the same array.
     */
    protected void addLimbs(long[] a, long[] b, long[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = a[i] + b[i];
        }
    }

    /**
     * Branch-free conditional swap of a and b. Requires that swap is 0 or 1,
     * and that a.length == b.length. If swap==0, then the values of a and b
     * will be unchanged. If swap==1, then the values of a and b will be
     * swapped. The behavior is undefined if swap has any value other than
     * 0 or 1.
     */
    protected static void conditionalSwap(int swap, long[] a, long[] b) {
        int maskValue = 0 - swap;
        for (int i = 0; i < a.length; i++) {
            long dummyLimbs = maskValue & (a[i] ^ b[i]);
            a[i] = dummyLimbs ^ a[i];
            b[i] = dummyLimbs ^ b[i];
        }
    }

    /**
     * Stores the reduced, little-endian value of limbs in result.
     */
    protected void limbsToByteArray(long[] limbs, byte[] result) {

        long[] reducedLimbs = limbs.clone();
        finalReduce(reducedLimbs);

        decode(reducedLimbs, result, 0, result.length);
    }

    /**
     * Add the reduced number corresponding to limbs and other, and store
     * the low-order bytes of the sum in result. Requires that
     * limbs.length==other.length. The result array may have any length.
     */
    protected void addLimbsModPowerTwo(long[] limbs, long[] other,
                                       byte[] result) {

        long[] reducedOther = other.clone();
        long[] reducedLimbs = limbs.clone();
        finalReduce(reducedOther);
        finalReduce(reducedLimbs);

        addLimbs(reducedLimbs, reducedOther, reducedLimbs);

        // may carry out a value which can be ignored
        long carry = 0;
        for (int i = 0; i < numLimbs; i++) {
            reducedLimbs[i] += carry;
            carry  = reducedLimbs[i] >> bitsPerLimb;
            reducedLimbs[i] -= carry << bitsPerLimb;
        }

        decode(reducedLimbs, result, 0, result.length);
    }

    private abstract class Element implements IntegerModuloP {

        protected long[] limbs;
        protected boolean summand = false;

        public Element(BigInteger v) {
            limbs = new long[numLimbs];
            setValue(v);
        }

        public Element(boolean v) {
            limbs = new long[numLimbs];
            limbs[0] = v ? 1l : 0l;
            summand = true;
        }

        private Element(long[] limbs, boolean summand) {
            this.limbs = limbs;
            this.summand = summand;
        }

        private void setValue(BigInteger v) {
            setLimbsValue(v, limbs);
            summand = true;
        }

        @Override
        public IntegerFieldModuloP getField() {
            return IntegerPolynomial.this;
        }

        @Override
        public BigInteger asBigInteger() {
            return evaluate(limbs);
        }

        @Override
        public MutableElement mutable() {
            return new MutableElement(limbs.clone(), summand);
        }

        @Override
        public ImmutableElement add(IntegerModuloP genB) {

            Element b = (Element) genB;
            if (!(summand && b.summand)) {
                throw new ArithmeticException("Not a valid summand");
            }

            long[] newLimbs = new long[limbs.length];
            for (int i = 0; i < limbs.length; i++) {
                newLimbs[i] = limbs[i] + b.limbs[i];
            }

            return new ImmutableElement(newLimbs, false);
        }

        @Override
        public ImmutableElement additiveInverse() {

            long[] newLimbs = new long[limbs.length];
            for (int i = 0; i < limbs.length; i++) {
                newLimbs[i] = -limbs[i];
            }

            ImmutableElement result = new ImmutableElement(newLimbs, summand);
            return result;
        }

        protected long[] cloneLow(long[] limbs) {
            long[] newLimbs = new long[numLimbs];
            copyLow(limbs, newLimbs);
            return newLimbs;
        }
        protected void copyLow(long[] limbs, long[] out) {
            System.arraycopy(limbs, 0, out, 0, out.length);
        }

        @Override
        public ImmutableElement multiply(IntegerModuloP genB) {

            Element b = (Element) genB;

            long[] newLimbs = new long[limbs.length];
            mult(limbs, b.limbs, newLimbs);
            return new ImmutableElement(newLimbs, true);
        }

        @Override
        public ImmutableElement square() {
            long[] newLimbs = new long[limbs.length];
            IntegerPolynomial.this.square(limbs, newLimbs);
            return new ImmutableElement(newLimbs, true);
        }

        public void addModPowerTwo(IntegerModuloP arg, byte[] result) {

            Element other = (Element) arg;
            if (!(summand && other.summand)) {
                throw new ArithmeticException("Not a valid summand");
            }
            addLimbsModPowerTwo(limbs, other.limbs, result);
        }

        public void asByteArray(byte[] result) {
            if (!summand) {
                throw new ArithmeticException("Not a valid summand");
            }
            limbsToByteArray(limbs, result);
        }
    }

    private class MutableElement extends Element
        implements MutableIntegerModuloP {

        protected MutableElement(long[] limbs, boolean summand) {
            super(limbs, summand);
        }

        @Override
        public ImmutableElement fixed() {
            return new ImmutableElement(limbs.clone(), summand);
        }

        @Override
        public void conditionalSwapWith(MutableIntegerModuloP b, int swap) {

            MutableElement other = (MutableElement) b;

            conditionalSwap(swap, limbs, other.limbs);
            boolean summandTemp = summand;
            summand = other.summand;
            other.summand = summandTemp;
        }


        @Override
        public MutableElement setValue(IntegerModuloP v) {
            Element other = (Element) v;

            System.arraycopy(other.limbs, 0, limbs, 0, other.limbs.length);
            summand = other.summand;
            return this;
        }

        @Override
        public MutableElement setValue(byte[] arr, int offset,
                                       int length, byte highByte) {

            encode(arr, offset, length, highByte, limbs);
            summand = true;

            return this;
        }

        @Override
        public MutableElement setValue(ByteBuffer buf, int length,
                                       byte highByte) {

            encode(buf, length, highByte, limbs);
            summand = true;

            return this;
        }

        @Override
        public MutableElement setProduct(IntegerModuloP genB) {
            Element b = (Element) genB;
            mult(limbs, b.limbs, limbs);
            summand = true;
            return this;
        }

        @Override
        public MutableElement setProduct(SmallValue v) {
            int value = ((Limb) v).value;
            multByInt(limbs, value, limbs);
            summand = true;
            return this;
        }

        @Override
        public MutableElement setSum(IntegerModuloP genB) {

            Element b = (Element) genB;
            if (!(summand && b.summand)) {
                throw new ArithmeticException("Not a valid summand");
            }

            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = limbs[i] + b.limbs[i];
            }

            summand = false;
            return this;
        }

        @Override
        public MutableElement setDifference(IntegerModuloP genB) {

            Element b = (Element) genB;
            if (!(summand && b.summand)) {
                throw new ArithmeticException("Not a valid summand");
            }

            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = limbs[i] - b.limbs[i];
            }

            return this;
        }

        @Override
        public MutableElement setSquare() {
            IntegerPolynomial.this.square(limbs, limbs);
            summand = true;
            return this;
        }

    }

    class ImmutableElement extends Element implements ImmutableIntegerModuloP {

        protected ImmutableElement(BigInteger v) {
            super(v);
        }

        protected ImmutableElement(boolean v) {
            super(v);
        }

        protected ImmutableElement(long[] limbs, boolean summand) {
            super(limbs, summand);
        }

        @Override
        public ImmutableElement fixed() {
            return this;
        }

    }

    class Limb implements SmallValue {
        int value;

        Limb(int value) {
            this.value = value;
        }
    }


}
