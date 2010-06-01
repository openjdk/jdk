/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
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

package java.math;

/**
 * A class used to represent multiprecision integers that makes efficient
 * use of allocated space by allowing a number to occupy only part of
 * an array so that the arrays do not have to be reallocated as often.
 * When performing an operation with many iterations the array used to
 * hold a number is only reallocated when necessary and does not have to
 * be the same size as the number it represents. A mutable number allows
 * calculations to occur on the same number without having to create
 * a new number for every step of the calculation as occurs with
 * BigIntegers.
 *
 * @see     BigInteger
 * @author  Michael McCloskey
 * @since   1.3
 */

import java.util.Arrays;

import static java.math.BigInteger.LONG_MASK;
import static java.math.BigDecimal.INFLATED;

class MutableBigInteger {
    /**
     * Holds the magnitude of this MutableBigInteger in big endian order.
     * The magnitude may start at an offset into the value array, and it may
     * end before the length of the value array.
     */
    int[] value;

    /**
     * The number of ints of the value array that are currently used
     * to hold the magnitude of this MutableBigInteger. The magnitude starts
     * at an offset and offset + intLen may be less than value.length.
     */
    int intLen;

    /**
     * The offset into the value array where the magnitude of this
     * MutableBigInteger begins.
     */
    int offset = 0;

    // Constants
    /**
     * MutableBigInteger with one element value array with the value 1. Used by
     * BigDecimal divideAndRound to increment the quotient. Use this constant
     * only when the method is not going to modify this object.
     */
    static final MutableBigInteger ONE = new MutableBigInteger(1);

    // Constructors

    /**
     * The default constructor. An empty MutableBigInteger is created with
     * a one word capacity.
     */
    MutableBigInteger() {
        value = new int[1];
        intLen = 0;
    }

    /**
     * Construct a new MutableBigInteger with a magnitude specified by
     * the int val.
     */
    MutableBigInteger(int val) {
        value = new int[1];
        intLen = 1;
        value[0] = val;
    }

    /**
     * Construct a new MutableBigInteger with the specified value array
     * up to the length of the array supplied.
     */
    MutableBigInteger(int[] val) {
        value = val;
        intLen = val.length;
    }

    /**
     * Construct a new MutableBigInteger with a magnitude equal to the
     * specified BigInteger.
     */
    MutableBigInteger(BigInteger b) {
        intLen = b.mag.length;
        value = Arrays.copyOf(b.mag, intLen);
    }

    /**
     * Construct a new MutableBigInteger with a magnitude equal to the
     * specified MutableBigInteger.
     */
    MutableBigInteger(MutableBigInteger val) {
        intLen = val.intLen;
        value = Arrays.copyOfRange(val.value, val.offset, val.offset + intLen);
    }

    /**
     * Internal helper method to return the magnitude array. The caller is not
     * supposed to modify the returned array.
     */
    private int[] getMagnitudeArray() {
        if (offset > 0 || value.length != intLen)
            return Arrays.copyOfRange(value, offset, offset + intLen);
        return value;
    }

    /**
     * Convert this MutableBigInteger to a long value. The caller has to make
     * sure this MutableBigInteger can be fit into long.
     */
    private long toLong() {
        assert (intLen <= 2) : "this MutableBigInteger exceeds the range of long";
        if (intLen == 0)
            return 0;
        long d = value[offset] & LONG_MASK;
        return (intLen == 2) ? d << 32 | (value[offset + 1] & LONG_MASK) : d;
    }

    /**
     * Convert this MutableBigInteger to a BigInteger object.
     */
    BigInteger toBigInteger(int sign) {
        if (intLen == 0 || sign == 0)
            return BigInteger.ZERO;
        return new BigInteger(getMagnitudeArray(), sign);
    }

    /**
     * Convert this MutableBigInteger to BigDecimal object with the specified sign
     * and scale.
     */
    BigDecimal toBigDecimal(int sign, int scale) {
        if (intLen == 0 || sign == 0)
            return BigDecimal.valueOf(0, scale);
        int[] mag = getMagnitudeArray();
        int len = mag.length;
        int d = mag[0];
        // If this MutableBigInteger can't be fit into long, we need to
        // make a BigInteger object for the resultant BigDecimal object.
        if (len > 2 || (d < 0 && len == 2))
            return new BigDecimal(new BigInteger(mag, sign), INFLATED, scale, 0);
        long v = (len == 2) ?
            ((mag[1] & LONG_MASK) | (d & LONG_MASK) << 32) :
            d & LONG_MASK;
        return new BigDecimal(null, sign == -1 ? -v : v, scale, 0);
    }

    /**
     * Clear out a MutableBigInteger for reuse.
     */
    void clear() {
        offset = intLen = 0;
        for (int index=0, n=value.length; index < n; index++)
            value[index] = 0;
    }

    /**
     * Set a MutableBigInteger to zero, removing its offset.
     */
    void reset() {
        offset = intLen = 0;
    }

    /**
     * Compare the magnitude of two MutableBigIntegers. Returns -1, 0 or 1
     * as this MutableBigInteger is numerically less than, equal to, or
     * greater than <tt>b</tt>.
     */
    final int compare(MutableBigInteger b) {
        int blen = b.intLen;
        if (intLen < blen)
            return -1;
        if (intLen > blen)
           return 1;

        // Add Integer.MIN_VALUE to make the comparison act as unsigned integer
        // comparison.
        int[] bval = b.value;
        for (int i = offset, j = b.offset; i < intLen + offset; i++, j++) {
            int b1 = value[i] + 0x80000000;
            int b2 = bval[j]  + 0x80000000;
            if (b1 < b2)
                return -1;
            if (b1 > b2)
                return 1;
        }
        return 0;
    }

    /**
     * Compare this against half of a MutableBigInteger object (Needed for
     * remainder tests).
     * Assumes no leading unnecessary zeros, which holds for results
     * from divide().
     */
    final int compareHalf(MutableBigInteger b) {
        int blen = b.intLen;
        int len = intLen;
        if (len <= 0)
            return blen <=0 ? 0 : -1;
        if (len > blen)
            return 1;
        if (len < blen - 1)
            return -1;
        int[] bval = b.value;
        int bstart = 0;
        int carry = 0;
        // Only 2 cases left:len == blen or len == blen - 1
        if (len != blen) { // len == blen - 1
            if (bval[bstart] == 1) {
                ++bstart;
                carry = 0x80000000;
            } else
                return -1;
        }
        // compare values with right-shifted values of b,
        // carrying shifted-out bits across words
        int[] val = value;
        for (int i = offset, j = bstart; i < len + offset;) {
            int bv = bval[j++];
            long hb = ((bv >>> 1) + carry) & LONG_MASK;
            long v = val[i++] & LONG_MASK;
            if (v != hb)
                return v < hb ? -1 : 1;
            carry = (bv & 1) << 31; // carray will be either 0x80000000 or 0
        }
        return carry == 0? 0 : -1;
    }

    /**
     * Return the index of the lowest set bit in this MutableBigInteger. If the
     * magnitude of this MutableBigInteger is zero, -1 is returned.
     */
    private final int getLowestSetBit() {
        if (intLen == 0)
            return -1;
        int j, b;
        for (j=intLen-1; (j>0) && (value[j+offset]==0); j--)
            ;
        b = value[j+offset];
        if (b==0)
            return -1;
        return ((intLen-1-j)<<5) + Integer.numberOfTrailingZeros(b);
    }

    /**
     * Return the int in use in this MutableBigInteger at the specified
     * index. This method is not used because it is not inlined on all
     * platforms.
     */
    private final int getInt(int index) {
        return value[offset+index];
    }

    /**
     * Return a long which is equal to the unsigned value of the int in
     * use in this MutableBigInteger at the specified index. This method is
     * not used because it is not inlined on all platforms.
     */
    private final long getLong(int index) {
        return value[offset+index] & LONG_MASK;
    }

    /**
     * Ensure that the MutableBigInteger is in normal form, specifically
     * making sure that there are no leading zeros, and that if the
     * magnitude is zero, then intLen is zero.
     */
    final void normalize() {
        if (intLen == 0) {
            offset = 0;
            return;
        }

        int index = offset;
        if (value[index] != 0)
            return;

        int indexBound = index+intLen;
        do {
            index++;
        } while(index < indexBound && value[index]==0);

        int numZeros = index - offset;
        intLen -= numZeros;
        offset = (intLen==0 ?  0 : offset+numZeros);
    }

    /**
     * If this MutableBigInteger cannot hold len words, increase the size
     * of the value array to len words.
     */
    private final void ensureCapacity(int len) {
        if (value.length < len) {
            value = new int[len];
            offset = 0;
            intLen = len;
        }
    }

    /**
     * Convert this MutableBigInteger into an int array with no leading
     * zeros, of a length that is equal to this MutableBigInteger's intLen.
     */
    int[] toIntArray() {
        int[] result = new int[intLen];
        for(int i=0; i<intLen; i++)
            result[i] = value[offset+i];
        return result;
    }

    /**
     * Sets the int at index+offset in this MutableBigInteger to val.
     * This does not get inlined on all platforms so it is not used
     * as often as originally intended.
     */
    void setInt(int index, int val) {
        value[offset + index] = val;
    }

    /**
     * Sets this MutableBigInteger's value array to the specified array.
     * The intLen is set to the specified length.
     */
    void setValue(int[] val, int length) {
        value = val;
        intLen = length;
        offset = 0;
    }

    /**
     * Sets this MutableBigInteger's value array to a copy of the specified
     * array. The intLen is set to the length of the new array.
     */
    void copyValue(MutableBigInteger src) {
        int len = src.intLen;
        if (value.length < len)
            value = new int[len];
        System.arraycopy(src.value, src.offset, value, 0, len);
        intLen = len;
        offset = 0;
    }

    /**
     * Sets this MutableBigInteger's value array to a copy of the specified
     * array. The intLen is set to the length of the specified array.
     */
    void copyValue(int[] val) {
        int len = val.length;
        if (value.length < len)
            value = new int[len];
        System.arraycopy(val, 0, value, 0, len);
        intLen = len;
        offset = 0;
    }

    /**
     * Returns true iff this MutableBigInteger has a value of one.
     */
    boolean isOne() {
        return (intLen == 1) && (value[offset] == 1);
    }

    /**
     * Returns true iff this MutableBigInteger has a value of zero.
     */
    boolean isZero() {
        return (intLen == 0);
    }

    /**
     * Returns true iff this MutableBigInteger is even.
     */
    boolean isEven() {
        return (intLen == 0) || ((value[offset + intLen - 1] & 1) == 0);
    }

    /**
     * Returns true iff this MutableBigInteger is odd.
     */
    boolean isOdd() {
        return isZero() ? false : ((value[offset + intLen - 1] & 1) == 1);
    }

    /**
     * Returns true iff this MutableBigInteger is in normal form. A
     * MutableBigInteger is in normal form if it has no leading zeros
     * after the offset, and intLen + offset <= value.length.
     */
    boolean isNormal() {
        if (intLen + offset > value.length)
            return false;
        if (intLen ==0)
            return true;
        return (value[offset] != 0);
    }

    /**
     * Returns a String representation of this MutableBigInteger in radix 10.
     */
    public String toString() {
        BigInteger b = toBigInteger(1);
        return b.toString();
    }

    /**
     * Right shift this MutableBigInteger n bits. The MutableBigInteger is left
     * in normal form.
     */
    void rightShift(int n) {
        if (intLen == 0)
            return;
        int nInts = n >>> 5;
        int nBits = n & 0x1F;
        this.intLen -= nInts;
        if (nBits == 0)
            return;
        int bitsInHighWord = BigInteger.bitLengthForInt(value[offset]);
        if (nBits >= bitsInHighWord) {
            this.primitiveLeftShift(32 - nBits);
            this.intLen--;
        } else {
            primitiveRightShift(nBits);
        }
    }

    /**
     * Left shift this MutableBigInteger n bits.
     */
    void leftShift(int n) {
        /*
         * If there is enough storage space in this MutableBigInteger already
         * the available space will be used. Space to the right of the used
         * ints in the value array is faster to utilize, so the extra space
         * will be taken from the right if possible.
         */
        if (intLen == 0)
           return;
        int nInts = n >>> 5;
        int nBits = n&0x1F;
        int bitsInHighWord = BigInteger.bitLengthForInt(value[offset]);

        // If shift can be done without moving words, do so
        if (n <= (32-bitsInHighWord)) {
            primitiveLeftShift(nBits);
            return;
        }

        int newLen = intLen + nInts +1;
        if (nBits <= (32-bitsInHighWord))
            newLen--;
        if (value.length < newLen) {
            // The array must grow
            int[] result = new int[newLen];
            for (int i=0; i<intLen; i++)
                result[i] = value[offset+i];
            setValue(result, newLen);
        } else if (value.length - offset >= newLen) {
            // Use space on right
            for(int i=0; i<newLen - intLen; i++)
                value[offset+intLen+i] = 0;
        } else {
            // Must use space on left
            for (int i=0; i<intLen; i++)
                value[i] = value[offset+i];
            for (int i=intLen; i<newLen; i++)
                value[i] = 0;
            offset = 0;
        }
        intLen = newLen;
        if (nBits == 0)
            return;
        if (nBits <= (32-bitsInHighWord))
            primitiveLeftShift(nBits);
        else
            primitiveRightShift(32 -nBits);
    }

    /**
     * A primitive used for division. This method adds in one multiple of the
     * divisor a back to the dividend result at a specified offset. It is used
     * when qhat was estimated too large, and must be adjusted.
     */
    private int divadd(int[] a, int[] result, int offset) {
        long carry = 0;

        for (int j=a.length-1; j >= 0; j--) {
            long sum = (a[j] & LONG_MASK) +
                       (result[j+offset] & LONG_MASK) + carry;
            result[j+offset] = (int)sum;
            carry = sum >>> 32;
        }
        return (int)carry;
    }

    /**
     * This method is used for division. It multiplies an n word input a by one
     * word input x, and subtracts the n word product from q. This is needed
     * when subtracting qhat*divisor from dividend.
     */
    private int mulsub(int[] q, int[] a, int x, int len, int offset) {
        long xLong = x & LONG_MASK;
        long carry = 0;
        offset += len;

        for (int j=len-1; j >= 0; j--) {
            long product = (a[j] & LONG_MASK) * xLong + carry;
            long difference = q[offset] - product;
            q[offset--] = (int)difference;
            carry = (product >>> 32)
                     + (((difference & LONG_MASK) >
                         (((~(int)product) & LONG_MASK))) ? 1:0);
        }
        return (int)carry;
    }

    /**
     * Right shift this MutableBigInteger n bits, where n is
     * less than 32.
     * Assumes that intLen > 0, n > 0 for speed
     */
    private final void primitiveRightShift(int n) {
        int[] val = value;
        int n2 = 32 - n;
        for (int i=offset+intLen-1, c=val[i]; i>offset; i--) {
            int b = c;
            c = val[i-1];
            val[i] = (c << n2) | (b >>> n);
        }
        val[offset] >>>= n;
    }

    /**
     * Left shift this MutableBigInteger n bits, where n is
     * less than 32.
     * Assumes that intLen > 0, n > 0 for speed
     */
    private final void primitiveLeftShift(int n) {
        int[] val = value;
        int n2 = 32 - n;
        for (int i=offset, c=val[i], m=i+intLen-1; i<m; i++) {
            int b = c;
            c = val[i+1];
            val[i] = (b << n) | (c >>> n2);
        }
        val[offset+intLen-1] <<= n;
    }

    /**
     * Adds the contents of two MutableBigInteger objects.The result
     * is placed within this MutableBigInteger.
     * The contents of the addend are not changed.
     */
    void add(MutableBigInteger addend) {
        int x = intLen;
        int y = addend.intLen;
        int resultLen = (intLen > addend.intLen ? intLen : addend.intLen);
        int[] result = (value.length < resultLen ? new int[resultLen] : value);

        int rstart = result.length-1;
        long sum;
        long carry = 0;

        // Add common parts of both numbers
        while(x>0 && y>0) {
            x--; y--;
            sum = (value[x+offset] & LONG_MASK) +
                (addend.value[y+addend.offset] & LONG_MASK) + carry;
            result[rstart--] = (int)sum;
            carry = sum >>> 32;
        }

        // Add remainder of the longer number
        while(x>0) {
            x--;
            if (carry == 0 && result == value && rstart == (x + offset))
                return;
            sum = (value[x+offset] & LONG_MASK) + carry;
            result[rstart--] = (int)sum;
            carry = sum >>> 32;
        }
        while(y>0) {
            y--;
            sum = (addend.value[y+addend.offset] & LONG_MASK) + carry;
            result[rstart--] = (int)sum;
            carry = sum >>> 32;
        }

        if (carry > 0) { // Result must grow in length
            resultLen++;
            if (result.length < resultLen) {
                int temp[] = new int[resultLen];
                // Result one word longer from carry-out; copy low-order
                // bits into new result.
                System.arraycopy(result, 0, temp, 1, result.length);
                temp[0] = 1;
                result = temp;
            } else {
                result[rstart--] = 1;
            }
        }

        value = result;
        intLen = resultLen;
        offset = result.length - resultLen;
    }


    /**
     * Subtracts the smaller of this and b from the larger and places the
     * result into this MutableBigInteger.
     */
    int subtract(MutableBigInteger b) {
        MutableBigInteger a = this;

        int[] result = value;
        int sign = a.compare(b);

        if (sign == 0) {
            reset();
            return 0;
        }
        if (sign < 0) {
            MutableBigInteger tmp = a;
            a = b;
            b = tmp;
        }

        int resultLen = a.intLen;
        if (result.length < resultLen)
            result = new int[resultLen];

        long diff = 0;
        int x = a.intLen;
        int y = b.intLen;
        int rstart = result.length - 1;

        // Subtract common parts of both numbers
        while (y>0) {
            x--; y--;

            diff = (a.value[x+a.offset] & LONG_MASK) -
                   (b.value[y+b.offset] & LONG_MASK) - ((int)-(diff>>32));
            result[rstart--] = (int)diff;
        }
        // Subtract remainder of longer number
        while (x>0) {
            x--;
            diff = (a.value[x+a.offset] & LONG_MASK) - ((int)-(diff>>32));
            result[rstart--] = (int)diff;
        }

        value = result;
        intLen = resultLen;
        offset = value.length - resultLen;
        normalize();
        return sign;
    }

    /**
     * Subtracts the smaller of a and b from the larger and places the result
     * into the larger. Returns 1 if the answer is in a, -1 if in b, 0 if no
     * operation was performed.
     */
    private int difference(MutableBigInteger b) {
        MutableBigInteger a = this;
        int sign = a.compare(b);
        if (sign ==0)
            return 0;
        if (sign < 0) {
            MutableBigInteger tmp = a;
            a = b;
            b = tmp;
        }

        long diff = 0;
        int x = a.intLen;
        int y = b.intLen;

        // Subtract common parts of both numbers
        while (y>0) {
            x--; y--;
            diff = (a.value[a.offset+ x] & LONG_MASK) -
                (b.value[b.offset+ y] & LONG_MASK) - ((int)-(diff>>32));
            a.value[a.offset+x] = (int)diff;
        }
        // Subtract remainder of longer number
        while (x>0) {
            x--;
            diff = (a.value[a.offset+ x] & LONG_MASK) - ((int)-(diff>>32));
            a.value[a.offset+x] = (int)diff;
        }

        a.normalize();
        return sign;
    }

    /**
     * Multiply the contents of two MutableBigInteger objects. The result is
     * placed into MutableBigInteger z. The contents of y are not changed.
     */
    void multiply(MutableBigInteger y, MutableBigInteger z) {
        int xLen = intLen;
        int yLen = y.intLen;
        int newLen = xLen + yLen;

        // Put z into an appropriate state to receive product
        if (z.value.length < newLen)
            z.value = new int[newLen];
        z.offset = 0;
        z.intLen = newLen;

        // The first iteration is hoisted out of the loop to avoid extra add
        long carry = 0;
        for (int j=yLen-1, k=yLen+xLen-1; j >= 0; j--, k--) {
                long product = (y.value[j+y.offset] & LONG_MASK) *
                               (value[xLen-1+offset] & LONG_MASK) + carry;
                z.value[k] = (int)product;
                carry = product >>> 32;
        }
        z.value[xLen-1] = (int)carry;

        // Perform the multiplication word by word
        for (int i = xLen-2; i >= 0; i--) {
            carry = 0;
            for (int j=yLen-1, k=yLen+i; j >= 0; j--, k--) {
                long product = (y.value[j+y.offset] & LONG_MASK) *
                               (value[i+offset] & LONG_MASK) +
                               (z.value[k] & LONG_MASK) + carry;
                z.value[k] = (int)product;
                carry = product >>> 32;
            }
            z.value[i] = (int)carry;
        }

        // Remove leading zeros from product
        z.normalize();
    }

    /**
     * Multiply the contents of this MutableBigInteger by the word y. The
     * result is placed into z.
     */
    void mul(int y, MutableBigInteger z) {
        if (y == 1) {
            z.copyValue(this);
            return;
        }

        if (y == 0) {
            z.clear();
            return;
        }

        // Perform the multiplication word by word
        long ylong = y & LONG_MASK;
        int[] zval = (z.value.length<intLen+1 ? new int[intLen + 1]
                                              : z.value);
        long carry = 0;
        for (int i = intLen-1; i >= 0; i--) {
            long product = ylong * (value[i+offset] & LONG_MASK) + carry;
            zval[i+1] = (int)product;
            carry = product >>> 32;
        }

        if (carry == 0) {
            z.offset = 1;
            z.intLen = intLen;
        } else {
            z.offset = 0;
            z.intLen = intLen + 1;
            zval[0] = (int)carry;
        }
        z.value = zval;
    }

     /**
     * This method is used for division of an n word dividend by a one word
     * divisor. The quotient is placed into quotient. The one word divisor is
     * specified by divisor.
     *
     * @return the remainder of the division is returned.
     *
     */
    int divideOneWord(int divisor, MutableBigInteger quotient) {
        long divisorLong = divisor & LONG_MASK;

        // Special case of one word dividend
        if (intLen == 1) {
            long dividendValue = value[offset] & LONG_MASK;
            int q = (int) (dividendValue / divisorLong);
            int r = (int) (dividendValue - q * divisorLong);
            quotient.value[0] = q;
            quotient.intLen = (q == 0) ? 0 : 1;
            quotient.offset = 0;
            return r;
        }

        if (quotient.value.length < intLen)
            quotient.value = new int[intLen];
        quotient.offset = 0;
        quotient.intLen = intLen;

        // Normalize the divisor
        int shift = Integer.numberOfLeadingZeros(divisor);

        int rem = value[offset];
        long remLong = rem & LONG_MASK;
        if (remLong < divisorLong) {
            quotient.value[0] = 0;
        } else {
            quotient.value[0] = (int)(remLong / divisorLong);
            rem = (int) (remLong - (quotient.value[0] * divisorLong));
            remLong = rem & LONG_MASK;
        }

        int xlen = intLen;
        int[] qWord = new int[2];
        while (--xlen > 0) {
            long dividendEstimate = (remLong<<32) |
                (value[offset + intLen - xlen] & LONG_MASK);
            if (dividendEstimate >= 0) {
                qWord[0] = (int) (dividendEstimate / divisorLong);
                qWord[1] = (int) (dividendEstimate - qWord[0] * divisorLong);
            } else {
                divWord(qWord, dividendEstimate, divisor);
            }
            quotient.value[intLen - xlen] = qWord[0];
            rem = qWord[1];
            remLong = rem & LONG_MASK;
        }

        quotient.normalize();
        // Unnormalize
        if (shift > 0)
            return rem % divisor;
        else
            return rem;
    }

    /**
     * Calculates the quotient of this div b and places the quotient in the
     * provided MutableBigInteger objects and the remainder object is returned.
     *
     * Uses Algorithm D in Knuth section 4.3.1.
     * Many optimizations to that algorithm have been adapted from the Colin
     * Plumb C library.
     * It special cases one word divisors for speed. The content of b is not
     * changed.
     *
     */
    MutableBigInteger divide(MutableBigInteger b, MutableBigInteger quotient) {
        if (b.intLen == 0)
            throw new ArithmeticException("BigInteger divide by zero");

        // Dividend is zero
        if (intLen == 0) {
            quotient.intLen = quotient.offset;
            return new MutableBigInteger();
        }

        int cmp = compare(b);
        // Dividend less than divisor
        if (cmp < 0) {
            quotient.intLen = quotient.offset = 0;
            return new MutableBigInteger(this);
        }
        // Dividend equal to divisor
        if (cmp == 0) {
            quotient.value[0] = quotient.intLen = 1;
            quotient.offset = 0;
            return new MutableBigInteger();
        }

        quotient.clear();
        // Special case one word divisor
        if (b.intLen == 1) {
            int r = divideOneWord(b.value[b.offset], quotient);
            if (r == 0)
                return new MutableBigInteger();
            return new MutableBigInteger(r);
        }

        // Copy divisor value to protect divisor
        int[] div = Arrays.copyOfRange(b.value, b.offset, b.offset + b.intLen);
        return divideMagnitude(div, quotient);
    }

    /**
     * Internally used  to calculate the quotient of this div v and places the
     * quotient in the provided MutableBigInteger object and the remainder is
     * returned.
     *
     * @return the remainder of the division will be returned.
     */
    long divide(long v, MutableBigInteger quotient) {
        if (v == 0)
            throw new ArithmeticException("BigInteger divide by zero");

        // Dividend is zero
        if (intLen == 0) {
            quotient.intLen = quotient.offset = 0;
            return 0;
        }
        if (v < 0)
            v = -v;

        int d = (int)(v >>> 32);
        quotient.clear();
        // Special case on word divisor
        if (d == 0)
            return divideOneWord((int)v, quotient) & LONG_MASK;
        else {
            int[] div = new int[]{ d, (int)(v & LONG_MASK) };
            return divideMagnitude(div, quotient).toLong();
        }
    }

    /**
     * Divide this MutableBigInteger by the divisor represented by its magnitude
     * array. The quotient will be placed into the provided quotient object &
     * the remainder object is returned.
     */
    private MutableBigInteger divideMagnitude(int[] divisor,
                                              MutableBigInteger quotient) {

        // Remainder starts as dividend with space for a leading zero
        MutableBigInteger rem = new MutableBigInteger(new int[intLen + 1]);
        System.arraycopy(value, offset, rem.value, 1, intLen);
        rem.intLen = intLen;
        rem.offset = 1;

        int nlen = rem.intLen;

        // Set the quotient size
        int dlen = divisor.length;
        int limit = nlen - dlen + 1;
        if (quotient.value.length < limit) {
            quotient.value = new int[limit];
            quotient.offset = 0;
        }
        quotient.intLen = limit;
        int[] q = quotient.value;

        // D1 normalize the divisor
        int shift = Integer.numberOfLeadingZeros(divisor[0]);
        if (shift > 0) {
            // First shift will not grow array
            BigInteger.primitiveLeftShift(divisor, dlen, shift);
            // But this one might
            rem.leftShift(shift);
        }

        // Must insert leading 0 in rem if its length did not change
        if (rem.intLen == nlen) {
            rem.offset = 0;
            rem.value[0] = 0;
            rem.intLen++;
        }

        int dh = divisor[0];
        long dhLong = dh & LONG_MASK;
        int dl = divisor[1];
        int[] qWord = new int[2];

        // D2 Initialize j
        for(int j=0; j<limit; j++) {
            // D3 Calculate qhat
            // estimate qhat
            int qhat = 0;
            int qrem = 0;
            boolean skipCorrection = false;
            int nh = rem.value[j+rem.offset];
            int nh2 = nh + 0x80000000;
            int nm = rem.value[j+1+rem.offset];

            if (nh == dh) {
                qhat = ~0;
                qrem = nh + nm;
                skipCorrection = qrem + 0x80000000 < nh2;
            } else {
                long nChunk = (((long)nh) << 32) | (nm & LONG_MASK);
                if (nChunk >= 0) {
                    qhat = (int) (nChunk / dhLong);
                    qrem = (int) (nChunk - (qhat * dhLong));
                } else {
                    divWord(qWord, nChunk, dh);
                    qhat = qWord[0];
                    qrem = qWord[1];
                }
            }

            if (qhat == 0)
                continue;

            if (!skipCorrection) { // Correct qhat
                long nl = rem.value[j+2+rem.offset] & LONG_MASK;
                long rs = ((qrem & LONG_MASK) << 32) | nl;
                long estProduct = (dl & LONG_MASK) * (qhat & LONG_MASK);

                if (unsignedLongCompare(estProduct, rs)) {
                    qhat--;
                    qrem = (int)((qrem & LONG_MASK) + dhLong);
                    if ((qrem & LONG_MASK) >=  dhLong) {
                        estProduct -= (dl & LONG_MASK);
                        rs = ((qrem & LONG_MASK) << 32) | nl;
                        if (unsignedLongCompare(estProduct, rs))
                            qhat--;
                    }
                }
            }

            // D4 Multiply and subtract
            rem.value[j+rem.offset] = 0;
            int borrow = mulsub(rem.value, divisor, qhat, dlen, j+rem.offset);

            // D5 Test remainder
            if (borrow + 0x80000000 > nh2) {
                // D6 Add back
                divadd(divisor, rem.value, j+1+rem.offset);
                qhat--;
            }

            // Store the quotient digit
            q[j] = qhat;
        } // D7 loop on j

        // D8 Unnormalize
        if (shift > 0)
            rem.rightShift(shift);

        quotient.normalize();
        rem.normalize();
        return rem;
    }

    /**
     * Compare two longs as if they were unsigned.
     * Returns true iff one is bigger than two.
     */
    private boolean unsignedLongCompare(long one, long two) {
        return (one+Long.MIN_VALUE) > (two+Long.MIN_VALUE);
    }

    /**
     * This method divides a long quantity by an int to estimate
     * qhat for two multi precision numbers. It is used when
     * the signed value of n is less than zero.
     */
    private void divWord(int[] result, long n, int d) {
        long dLong = d & LONG_MASK;

        if (dLong == 1) {
            result[0] = (int)n;
            result[1] = 0;
            return;
        }

        // Approximate the quotient and remainder
        long q = (n >>> 1) / (dLong >>> 1);
        long r = n - q*dLong;

        // Correct the approximation
        while (r < 0) {
            r += dLong;
            q--;
        }
        while (r >= dLong) {
            r -= dLong;
            q++;
        }

        // n - q*dlong == r && 0 <= r <dLong, hence we're done.
        result[0] = (int)q;
        result[1] = (int)r;
    }

    /**
     * Calculate GCD of this and b. This and b are changed by the computation.
     */
    MutableBigInteger hybridGCD(MutableBigInteger b) {
        // Use Euclid's algorithm until the numbers are approximately the
        // same length, then use the binary GCD algorithm to find the GCD.
        MutableBigInteger a = this;
        MutableBigInteger q = new MutableBigInteger();

        while (b.intLen != 0) {
            if (Math.abs(a.intLen - b.intLen) < 2)
                return a.binaryGCD(b);

            MutableBigInteger r = a.divide(b, q);
            a = b;
            b = r;
        }
        return a;
    }

    /**
     * Calculate GCD of this and v.
     * Assumes that this and v are not zero.
     */
    private MutableBigInteger binaryGCD(MutableBigInteger v) {
        // Algorithm B from Knuth section 4.5.2
        MutableBigInteger u = this;
        MutableBigInteger r = new MutableBigInteger();

        // step B1
        int s1 = u.getLowestSetBit();
        int s2 = v.getLowestSetBit();
        int k = (s1 < s2) ? s1 : s2;
        if (k != 0) {
            u.rightShift(k);
            v.rightShift(k);
        }

        // step B2
        boolean uOdd = (k==s1);
        MutableBigInteger t = uOdd ? v: u;
        int tsign = uOdd ? -1 : 1;

        int lb;
        while ((lb = t.getLowestSetBit()) >= 0) {
            // steps B3 and B4
            t.rightShift(lb);
            // step B5
            if (tsign > 0)
                u = t;
            else
                v = t;

            // Special case one word numbers
            if (u.intLen < 2 && v.intLen < 2) {
                int x = u.value[u.offset];
                int y = v.value[v.offset];
                x  = binaryGcd(x, y);
                r.value[0] = x;
                r.intLen = 1;
                r.offset = 0;
                if (k > 0)
                    r.leftShift(k);
                return r;
            }

            // step B6
            if ((tsign = u.difference(v)) == 0)
                break;
            t = (tsign >= 0) ? u : v;
        }

        if (k > 0)
            u.leftShift(k);
        return u;
    }

    /**
     * Calculate GCD of a and b interpreted as unsigned integers.
     */
    static int binaryGcd(int a, int b) {
        if (b==0)
            return a;
        if (a==0)
            return b;

        // Right shift a & b till their last bits equal to 1.
        int aZeros = Integer.numberOfTrailingZeros(a);
        int bZeros = Integer.numberOfTrailingZeros(b);
        a >>>= aZeros;
        b >>>= bZeros;

        int t = (aZeros < bZeros ? aZeros : bZeros);

        while (a != b) {
            if ((a+0x80000000) > (b+0x80000000)) {  // a > b as unsigned
                a -= b;
                a >>>= Integer.numberOfTrailingZeros(a);
            } else {
                b -= a;
                b >>>= Integer.numberOfTrailingZeros(b);
            }
        }
        return a<<t;
    }

    /**
     * Returns the modInverse of this mod p. This and p are not affected by
     * the operation.
     */
    MutableBigInteger mutableModInverse(MutableBigInteger p) {
        // Modulus is odd, use Schroeppel's algorithm
        if (p.isOdd())
            return modInverse(p);

        // Base and modulus are even, throw exception
        if (isEven())
            throw new ArithmeticException("BigInteger not invertible.");

        // Get even part of modulus expressed as a power of 2
        int powersOf2 = p.getLowestSetBit();

        // Construct odd part of modulus
        MutableBigInteger oddMod = new MutableBigInteger(p);
        oddMod.rightShift(powersOf2);

        if (oddMod.isOne())
            return modInverseMP2(powersOf2);

        // Calculate 1/a mod oddMod
        MutableBigInteger oddPart = modInverse(oddMod);

        // Calculate 1/a mod evenMod
        MutableBigInteger evenPart = modInverseMP2(powersOf2);

        // Combine the results using Chinese Remainder Theorem
        MutableBigInteger y1 = modInverseBP2(oddMod, powersOf2);
        MutableBigInteger y2 = oddMod.modInverseMP2(powersOf2);

        MutableBigInteger temp1 = new MutableBigInteger();
        MutableBigInteger temp2 = new MutableBigInteger();
        MutableBigInteger result = new MutableBigInteger();

        oddPart.leftShift(powersOf2);
        oddPart.multiply(y1, result);

        evenPart.multiply(oddMod, temp1);
        temp1.multiply(y2, temp2);

        result.add(temp2);
        return result.divide(p, temp1);
    }

    /*
     * Calculate the multiplicative inverse of this mod 2^k.
     */
    MutableBigInteger modInverseMP2(int k) {
        if (isEven())
            throw new ArithmeticException("Non-invertible. (GCD != 1)");

        if (k > 64)
            return euclidModInverse(k);

        int t = inverseMod32(value[offset+intLen-1]);

        if (k < 33) {
            t = (k == 32 ? t : t & ((1 << k) - 1));
            return new MutableBigInteger(t);
        }

        long pLong = (value[offset+intLen-1] & LONG_MASK);
        if (intLen > 1)
            pLong |=  ((long)value[offset+intLen-2] << 32);
        long tLong = t & LONG_MASK;
        tLong = tLong * (2 - pLong * tLong);  // 1 more Newton iter step
        tLong = (k == 64 ? tLong : tLong & ((1L << k) - 1));

        MutableBigInteger result = new MutableBigInteger(new int[2]);
        result.value[0] = (int)(tLong >>> 32);
        result.value[1] = (int)tLong;
        result.intLen = 2;
        result.normalize();
        return result;
    }

    /*
     * Returns the multiplicative inverse of val mod 2^32.  Assumes val is odd.
     */
    static int inverseMod32(int val) {
        // Newton's iteration!
        int t = val;
        t *= 2 - val*t;
        t *= 2 - val*t;
        t *= 2 - val*t;
        t *= 2 - val*t;
        return t;
    }

    /*
     * Calculate the multiplicative inverse of 2^k mod mod, where mod is odd.
     */
    static MutableBigInteger modInverseBP2(MutableBigInteger mod, int k) {
        // Copy the mod to protect original
        return fixup(new MutableBigInteger(1), new MutableBigInteger(mod), k);
    }

    /**
     * Calculate the multiplicative inverse of this mod mod, where mod is odd.
     * This and mod are not changed by the calculation.
     *
     * This method implements an algorithm due to Richard Schroeppel, that uses
     * the same intermediate representation as Montgomery Reduction
     * ("Montgomery Form").  The algorithm is described in an unpublished
     * manuscript entitled "Fast Modular Reciprocals."
     */
    private MutableBigInteger modInverse(MutableBigInteger mod) {
        MutableBigInteger p = new MutableBigInteger(mod);
        MutableBigInteger f = new MutableBigInteger(this);
        MutableBigInteger g = new MutableBigInteger(p);
        SignedMutableBigInteger c = new SignedMutableBigInteger(1);
        SignedMutableBigInteger d = new SignedMutableBigInteger();
        MutableBigInteger temp = null;
        SignedMutableBigInteger sTemp = null;

        int k = 0;
        // Right shift f k times until odd, left shift d k times
        if (f.isEven()) {
            int trailingZeros = f.getLowestSetBit();
            f.rightShift(trailingZeros);
            d.leftShift(trailingZeros);
            k = trailingZeros;
        }

        // The Almost Inverse Algorithm
        while(!f.isOne()) {
            // If gcd(f, g) != 1, number is not invertible modulo mod
            if (f.isZero())
                throw new ArithmeticException("BigInteger not invertible.");

            // If f < g exchange f, g and c, d
            if (f.compare(g) < 0) {
                temp = f; f = g; g = temp;
                sTemp = d; d = c; c = sTemp;
            }

            // If f == g (mod 4)
            if (((f.value[f.offset + f.intLen - 1] ^
                 g.value[g.offset + g.intLen - 1]) & 3) == 0) {
                f.subtract(g);
                c.signedSubtract(d);
            } else { // If f != g (mod 4)
                f.add(g);
                c.signedAdd(d);
            }

            // Right shift f k times until odd, left shift d k times
            int trailingZeros = f.getLowestSetBit();
            f.rightShift(trailingZeros);
            d.leftShift(trailingZeros);
            k += trailingZeros;
        }

        while (c.sign < 0)
           c.signedAdd(p);

        return fixup(c, p, k);
    }

    /*
     * The Fixup Algorithm
     * Calculates X such that X = C * 2^(-k) (mod P)
     * Assumes C<P and P is odd.
     */
    static MutableBigInteger fixup(MutableBigInteger c, MutableBigInteger p,
                                                                      int k) {
        MutableBigInteger temp = new MutableBigInteger();
        // Set r to the multiplicative inverse of p mod 2^32
        int r = -inverseMod32(p.value[p.offset+p.intLen-1]);

        for(int i=0, numWords = k >> 5; i<numWords; i++) {
            // V = R * c (mod 2^j)
            int  v = r * c.value[c.offset + c.intLen-1];
            // c = c + (v * p)
            p.mul(v, temp);
            c.add(temp);
            // c = c / 2^j
            c.intLen--;
        }
        int numBits = k & 0x1f;
        if (numBits != 0) {
            // V = R * c (mod 2^j)
            int v = r * c.value[c.offset + c.intLen-1];
            v &= ((1<<numBits) - 1);
            // c = c + (v * p)
            p.mul(v, temp);
            c.add(temp);
            // c = c / 2^j
            c.rightShift(numBits);
        }

        // In theory, c may be greater than p at this point (Very rare!)
        while (c.compare(p) >= 0)
            c.subtract(p);

        return c;
    }

    /**
     * Uses the extended Euclidean algorithm to compute the modInverse of base
     * mod a modulus that is a power of 2. The modulus is 2^k.
     */
    MutableBigInteger euclidModInverse(int k) {
        MutableBigInteger b = new MutableBigInteger(1);
        b.leftShift(k);
        MutableBigInteger mod = new MutableBigInteger(b);

        MutableBigInteger a = new MutableBigInteger(this);
        MutableBigInteger q = new MutableBigInteger();
        MutableBigInteger r = b.divide(a, q);

        MutableBigInteger swapper = b;
        // swap b & r
        b = r;
        r = swapper;

        MutableBigInteger t1 = new MutableBigInteger(q);
        MutableBigInteger t0 = new MutableBigInteger(1);
        MutableBigInteger temp = new MutableBigInteger();

        while (!b.isOne()) {
            r = a.divide(b, q);

            if (r.intLen == 0)
                throw new ArithmeticException("BigInteger not invertible.");

            swapper = r;
            a = swapper;

            if (q.intLen == 1)
                t1.mul(q.value[q.offset], temp);
            else
                q.multiply(t1, temp);
            swapper = q;
            q = temp;
            temp = swapper;
            t0.add(q);

            if (a.isOne())
                return t0;

            r = b.divide(a, q);

            if (r.intLen == 0)
                throw new ArithmeticException("BigInteger not invertible.");

            swapper = b;
            b =  r;

            if (q.intLen == 1)
                t0.mul(q.value[q.offset], temp);
            else
                q.multiply(t0, temp);
            swapper = q; q = temp; temp = swapper;

            t1.add(q);
        }
        mod.subtract(t1);
        return mod;
    }

}
