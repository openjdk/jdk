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
 *
 * All other operations may branch in some subclasses.
 *
 */

public abstract class IntegerPolynomial implements IntegerFieldModuloP {

    protected static final BigInteger TWO = BigInteger.valueOf(2);

    protected final int numLimbs;
    private final BigInteger modulus;
    protected final int bitsPerLimb;

    // must work when a==r
    protected abstract void multByInt(long[] a, long b, long[] r);

    // must work when a==r
    protected abstract void mult(long[] a, long[] b, long[] r);

    // must work when a==r
    protected abstract void square(long[] a, long[] r);

    IntegerPolynomial(int bitsPerLimb,
                      int numLimbs,
                      BigInteger modulus) {


        this.numLimbs = numLimbs;
        this.modulus = modulus;
        this.bitsPerLimb = bitsPerLimb;
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

    // carry out of the specified position and return the carry value
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

    // v must be final reduced. I.e. all limbs in [0, bitsPerLimb)
    // and value in [0, modulus)
    protected void decode(long[] v, byte[] dst, int offset, int length) {

        int nextLimbIndex = 0;
        long curLimbValue = v[nextLimbIndex++];
        int bitPos = 0;
        for (int i = 0; i < length; i++) {

            int dstIndex = i + offset;
            if (bitPos + 8 >= bitsPerLimb) {
                dst[dstIndex] = (byte) curLimbValue;
                curLimbValue = v[nextLimbIndex++];
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

    protected void addLimbs(long[] a, long[] b, long[] dst) {
        for (int i = 0; i < dst.length; i++) {
            dst[i] = a[i] + b[i];
        }
    }

    protected static void conditionalSwap(int swap, long[] a, long[] b) {
        int maskValue = 0 - swap;
        for (int i = 0; i < a.length; i++) {
            long dummyLimbs = maskValue & (a[i] ^ b[i]);
            a[i] = dummyLimbs ^ a[i];
            b[i] = dummyLimbs ^ b[i];
        }
    }

    private void bigIntToByteArray(BigInteger bi, byte[] result) {
        byte[] biBytes = bi.toByteArray();
        // biBytes is backwards and possibly too big
        // Copy the low-order bytes into result in reverse
        int sourceIndex = biBytes.length - 1;
        for (int i = 0; i < result.length; i++) {
            if (sourceIndex >= 0) {
                result[i] = biBytes[sourceIndex--];
            }
            else {
                result[i] = 0;
            }
        }
    }

    protected void limbsToByteArray(long[] limbs, byte[] result) {

        bigIntToByteArray(evaluate(limbs), result);
    }

    protected void addLimbsModPowerTwo(long[] limbs, long[] other,
                                       byte[] result) {

        BigInteger bi1 = evaluate(limbs);
        BigInteger bi2 = evaluate(other);
        BigInteger biResult = bi1.add(bi2);
        bigIntToByteArray(biResult, result);
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
            if (!summand) {
                throw new ArithmeticException("Not a valid summand");
            }

            Element other = (Element) arg;
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
