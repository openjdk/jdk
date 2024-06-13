/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * A large number polynomial representation using sparse limbs of signed
 * long (64-bit) values. Limb values will always fit within a long, so inputs
 * to multiplication must be less than 32 bits.
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

public abstract sealed class IntegerPolynomial implements IntegerFieldModuloP
    permits IntegerPolynomial1305, IntegerPolynomial25519,
            IntegerPolynomial448, IntegerPolynomialP256,
            MontgomeryIntegerPolynomialP256, IntegerPolynomialP384,
            IntegerPolynomialP521, IntegerPolynomialModBinP, P256OrderField,
            P384OrderField, P521OrderField, Curve25519OrderField,
            Curve448OrderField {

    protected static final BigInteger TWO = BigInteger.valueOf(2);

    protected final int numLimbs;
    private final BigInteger modulus;
    protected final int bitsPerLimb;
    private final long[] posModLimbs;
    private final int maxAddsMul; // max additions before a multiplication
    private final int maxAddsAdd; // max additions before an addition

    /**
     * Reduce an IntegerPolynomial representation (a) and store the result
     * in a. Requires that a.length == numLimbs.
     */
    protected abstract void reduce(long[] a);

    /**
     * Multiply an IntegerPolynomial representation (a) with a long (b) and
     * store the result in an IntegerPolynomial representation in a. Requires
     * that a.length == numLimbs.
     */
    protected int multByInt(long[] a, long b) {
        for (int i = 0; i < a.length; i++) {
            a[i] *= b;
        }
        reduce(a);
        return 0;
    }

    /**
     * Multiply two IntegerPolynomial representations (a and b) and store the
     * result in an IntegerPolynomial representation (r). Requires that
     * a.length == b.length == r.length == numLimbs. It is allowed for a and r
     * to be the same array.
     */
    protected abstract int mult(long[] a, long[] b, long[] r);

    /**
     * Multiply an IntegerPolynomial representation (a) with itself and store
     * the result in an IntegerPolynomialRepresentation (r). Requires that
     * a.length == r.length == numLimbs. It is allowed for a and r
     * to be the same array.
     */
    protected abstract int square(long[] a, long[] r);

    IntegerPolynomial(int bitsPerLimb,
                      int numLimbs,
                      int maxAddsMul,
                      BigInteger modulus) {


        this.numLimbs = numLimbs;
        this.modulus = modulus;
        this.bitsPerLimb = bitsPerLimb;
        this.maxAddsMul = maxAddsMul;
        if (bitsPerLimb>32) {
            this.maxAddsAdd = 64 - bitsPerLimb;
        } else {
            this.maxAddsAdd = 32 - bitsPerLimb;
        }
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

    public int getMaxAdds() {
        return maxAddsMul;
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
            throw new IllegalArgumentException("max magnitude is " + maxMag);
        }
        return new Limb(value);
    }

    protected abstract void reduceIn(long[] c, long v, int i);

    private void reduceHigh(long[] limbs) {

        // conservatively calculate how many reduce operations can be done
        // before a carry is needed
        int extraBits = 63 - 2 * bitsPerLimb;
        int allowedAdds = 1 << extraBits;
        int carryPeriod = allowedAdds / numLimbs;
        int reduceCount = 0;
        for (int i = limbs.length - 1; i >= numLimbs; i--) {
            reduceIn(limbs, limbs[i], i);
            limbs[i] = 0;

            reduceCount++;
            if (reduceCount % carryPeriod == 0) {
                carry(limbs, 0, i);
                reduceIn(limbs, limbs[i], i);
                limbs[i] = 0;
            }
        }
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
        int requiredLimbs = (numBits + bitsPerLimb - 1) / bitsPerLimb;
        if (requiredLimbs > numLimbs) {
            long[] temp = new long[requiredLimbs];
            encodeSmall(buf, length, highByte, temp);
            reduceHigh(temp);
            System.arraycopy(temp, 0, result, 0, result.length);
            reduce(result);
        } else {
            encodeSmall(buf, length, highByte, result);
            postEncodeCarry(result);
        }
    }

    protected void encodeSmall(ByteBuffer buf, int length, byte highByte,
                               long[] result) {

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

        if (limbIndex < result.length) {
            result[limbIndex++] = curLimbValue;
        }
        Arrays.fill(result, limbIndex, result.length, 0);
    }

    protected void encode(byte[] v, int offset, int length, byte highByte,
                          long[] result) {

        ByteBuffer buf = ByteBuffer.wrap(v, offset, length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        encode(buf, length, highByte, result);
    }

    // Encode does not produce compressed limbs. A simplified carry/reduce
    // operation can be used to compress the limbs.
    protected void postEncodeCarry(long[] v) {
        reduce(v);
    }

    public ImmutableElement getElement(byte[] v, int offset, int length,
                                       byte highByte) {

        long[] result = new long[numLimbs];

        encode(v, offset, length, highByte, result);

        return new ImmutableElement(result, 0);
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
        long limbMask = (1L << bitsPerLimb) - 1;
        for (int i = 0; i < limbs.length; i++) {
            limbs[i] = v.longValue() & limbMask;
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

                dst[dstIndex] += (byte) ((curLimbValue & (0xFF >> bitsAdded))
                    << bitsAdded);
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
     * Branch-free conditional assignment of b to a. Requires that set is 0 or
     * 1, and that a.length == b.length. If set==0, then the values of a and b
     * will be unchanged. If set==1, then the values of b will be assigned to a.
     * The behavior is undefined if swap has any value other than 0 or 1.
     */
    @ForceInline
    @IntrinsicCandidate
    protected static void conditionalAssign(int set, long[] a, long[] b) {
        int maskValue = -set;
        for (int i = 0; i < a.length; i++) {
            long dummyLimbs = maskValue & (a[i] ^ b[i]);
            a[i] = dummyLimbs ^ a[i];
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
        int maskValue = -swap;
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
        protected int numAdds;

        public Element(BigInteger v) {
            limbs = new long[numLimbs];
            setValue(v);
        }

        public Element(boolean v) {
            this.limbs = new long[numLimbs];
            this.limbs[0] = v ? 1L : 0L;
            this.numAdds = 0;
        }

        private Element(long[] limbs, int numAdds) {
            this.limbs = limbs;
            this.numAdds = numAdds;
        }

        private void setValue(BigInteger v) {
            setLimbsValue(v, limbs);
            this.numAdds = 0;
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
            return new MutableElement(limbs.clone(), numAdds);
        }

        @Override
        public ImmutableElement add(IntegerModuloP genB) {
            assert IntegerPolynomial.this == genB.getField();
            Element b = (Element)genB;

            // Reduce if required.
            if (numAdds > maxAddsAdd) {
               reduce(limbs);
               numAdds = 0;
            }

            if (b.numAdds > maxAddsAdd) {
                reduce(b.limbs);
                b.numAdds = 0;
            }

            long[] newLimbs = new long[limbs.length];
            for (int i = 0; i < limbs.length; i++) {
                newLimbs[i] = limbs[i] + b.limbs[i];
            }

            int newNumAdds = Math.max(numAdds, b.numAdds) + 1;
            return new ImmutableElement(newLimbs, newNumAdds);
        }

        @Override
        public ImmutableElement additiveInverse() {

            long[] newLimbs = new long[limbs.length];
            for (int i = 0; i < limbs.length; i++) {
                newLimbs[i] = -limbs[i];
            }

            return new ImmutableElement(newLimbs, numAdds+1);
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
            assert IntegerPolynomial.this == genB.getField();
            Element b = (Element)genB;

            // Reduce if required.
            if (numAdds > maxAddsMul) {
                reduce(limbs);
                numAdds = 0;
            }

            if (b.numAdds > maxAddsMul) {
                reduce(b.limbs);
                b.numAdds = 0;
            }

            long[] newLimbs = new long[limbs.length];
            int numAdds = mult(limbs, b.limbs, newLimbs);
            return new ImmutableElement(newLimbs, numAdds);
        }

        @Override
        public ImmutableElement square() {
            // Reduce if required.
            if (numAdds > maxAddsMul) {
                reduce(limbs);
                numAdds = 0;
            }

            long[] newLimbs = new long[limbs.length];
            int numAdds = IntegerPolynomial.this.square(limbs, newLimbs);
            return new ImmutableElement(newLimbs, numAdds);
        }

        public void addModPowerTwo(IntegerModuloP arg, byte[] result) {
            assert IntegerPolynomial.this == arg.getField();
            Element other = (Element)arg;

            // Reduce if required.
            if (numAdds > maxAddsAdd) {
                reduce(limbs);
                numAdds = 0;
            }

            if (other.numAdds > maxAddsAdd) {
                reduce(other.limbs);
                other.numAdds = 0;
            }

            addLimbsModPowerTwo(limbs, other.limbs, result);
        }

        public void asByteArray(byte[] result) {
            // Reduce if required.
            if (numAdds != 0) {
                reduce(limbs);
                numAdds = 0;
            }

            limbsToByteArray(limbs, result);
        }

        public long[] getLimbs() {
            return limbs;
        }
    }

    protected class MutableElement extends Element
        implements MutableIntegerModuloP {

        protected MutableElement(long[] limbs, int numAdds) {
            super(limbs, numAdds);
        }

        @Override
        public ImmutableElement fixed() {
            return new ImmutableElement(limbs.clone(), numAdds);
        }

        @Override
        public void conditionalSet(IntegerModuloP b, int set) {
            assert IntegerPolynomial.this == b.getField();
            Element other = (Element) b;

            conditionalAssign(set, limbs, other.limbs);
            numAdds = other.numAdds;
        }

        @Override
        public void conditionalSwapWith(MutableIntegerModuloP b, int swap) {
            assert IntegerPolynomial.this == b.getField();
            MutableElement other = (MutableElement) b;

            conditionalSwap(swap, limbs, other.limbs);
            int numAddsTemp = numAdds;
            numAdds = other.numAdds;
            other.numAdds = numAddsTemp;
        }


        @Override
        public MutableElement setValue(IntegerModuloP v) {
            assert IntegerPolynomial.this == v.getField();
            Element other = (Element) v;

            System.arraycopy(other.limbs, 0, limbs, 0, other.limbs.length);
            numAdds = other.numAdds;
            return this;
        }

        @Override
        public MutableElement setValue(byte[] arr, int offset,
                                       int length, byte highByte) {

            encode(arr, offset, length, highByte, limbs);
            this.numAdds = 0;

            return this;
        }

        @Override
        public MutableElement setValue(ByteBuffer buf, int length,
                                       byte highByte) {

            encode(buf, length, highByte, limbs);
            numAdds = 0;

            return this;
        }

        @Override
        public MutableElement setProduct(IntegerModuloP genB) {
            assert IntegerPolynomial.this == genB.getField();
            Element b = (Element)genB;

            // Reduce if required.
            if (numAdds > maxAddsMul) {
                reduce(limbs);
                numAdds = 0;
            }

            if (b.numAdds > maxAddsMul) {
                reduce(b.limbs);
                b.numAdds = 0;
            }

            numAdds = mult(limbs, b.limbs, limbs);
            return this;
        }

        @Override
        public MutableElement setProduct(SmallValue v) {
            // Reduce if required.
            if (numAdds > maxAddsMul) {
                reduce(limbs);
                numAdds = 0;
            }

            int value = ((Limb)v).value;
            numAdds += multByInt(limbs, value);
            return this;
        }

        @Override
        public MutableElement setSum(IntegerModuloP genB) {
            assert IntegerPolynomial.this == genB.getField();
            Element b = (Element)genB;

            // Reduce if required.
            if (numAdds > maxAddsAdd) {
               reduce(limbs);
               numAdds = 0;
            }

            if (b.numAdds > maxAddsAdd) {
                reduce(b.limbs);
                b.numAdds = 0;
            }

            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = limbs[i] + b.limbs[i];
            }

            numAdds = Math.max(numAdds, b.numAdds) + 1;
            return this;
        }

        @Override
        public MutableElement setDifference(IntegerModuloP genB) {
            assert IntegerPolynomial.this == genB.getField();
            Element b = (Element)genB;

            // Reduce if required.
            if (numAdds > maxAddsAdd) {
               reduce(limbs);
               numAdds = 0;
            }

            if (b.numAdds > maxAddsAdd) {
                reduce(b.limbs);
                b.numAdds = 0;
            }

            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = limbs[i] - b.limbs[i];
            }

            numAdds = Math.max(numAdds, b.numAdds) + 1;
            return this;
        }

        @Override
        public MutableElement setSquare() {
            // Reduce if required.
            if (numAdds > maxAddsMul) {
                reduce(limbs);
                numAdds = 0;
            }

            numAdds = IntegerPolynomial.this.square(limbs, limbs);
            return this;
        }

        @Override
        public MutableElement setAdditiveInverse() {
            for (int i = 0; i < limbs.length; i++) {
                limbs[i] = -limbs[i];
            }
            numAdds++;
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

        protected ImmutableElement(long[] limbs, int numAdds) {
            super(limbs, numAdds);
        }

        @Override
        public ImmutableElement fixed() {
            return this;
        }

    }

    static class Limb implements SmallValue {
        int value;

        Limb(int value) {
            this.value = value;
        }
    }
}

