/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.math;

import jdk.internal.misc.CDS;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.Stable;

import java.util.Arrays;

/**
 * A simple big integer class specifically for floating point base conversion.
 */
@AOTSafeClassInitializer
final class FDBigInteger {

    @Stable
    static final int[] SMALL_5_POW;

    @Stable
    static final long[] LONG_5_POW;

    // Size of full cache of powers of 5 as FDBigIntegers.
    private static final int MAX_FIVE_POW = 340;

    // Cache of big powers of 5 as FDBigIntegers.
    @Stable
    private static final FDBigInteger[] POW_5_CACHE;

    // Archive proxy
    private static Object[] archivedCaches;

    // Initialize FDBigInteger cache of powers of 5.
    static {
        // Legacy CDS archive support (to be deprecated)
        CDS.initializeFromArchive(FDBigInteger.class);
        Object[] caches = archivedCaches;
        if (caches == null) {
            int[] small5pow = new int[13 + 1];  // 5^13 fits in an int, 5^14 does not
            small5pow[0] = 1;
            for (int i = 1; i < small5pow.length; ++i) {
                small5pow[i] = 5 * small5pow[i - 1];
            }

            long[] long5pow = new long[27 + 1];  // 5^27 fits in a long, 5^28 does not
            long5pow[0] = 1;
            for (int i = 1; i < long5pow.length; ++i) {
                long5pow[i] = 5 * long5pow[i - 1];
            }

            FDBigInteger[] pow5cache = new FDBigInteger[MAX_FIVE_POW];
            int i = 0;
            for (; i < long5pow.length; ++i) {
                pow5cache[i] = new FDBigInteger(long5pow[i]).makeImmutable();
            }
            FDBigInteger prev = pow5cache[i - 1];
            for (; i < MAX_FIVE_POW; ++i) {
                pow5cache[i] = prev = prev.mult(5).makeImmutable();
            }

            /* Here prev is 5^(MAX_FIVE_POW-1). */
            FDBigInteger[] largePow5cache =
                    new FDBigInteger[(2 - DoubleToDecimal.Q_MIN) - MAX_FIVE_POW + 1];
            largePow5cache[2 * (MAX_FIVE_POW - 1) - MAX_FIVE_POW] =
                    prev = prev.mult(prev).makeImmutable();
            largePow5cache[3 * (MAX_FIVE_POW - 1) - MAX_FIVE_POW] =
                    pow5cache[MAX_FIVE_POW - 1].mult(prev).makeImmutable();
            archivedCaches = caches = new Object[] {small5pow, long5pow, pow5cache, largePow5cache};
        }
        SMALL_5_POW = (int[]) caches[0];
        LONG_5_POW = (long[]) caches[1];
        POW_5_CACHE = (FDBigInteger[]) caches[2];
        LARGE_POW_5_CACHE = (FDBigInteger[]) caches[3];
    }

    // Constant for casting an int to a long via bitwise AND.
    private static final long LONG_MASK = 0xffff_ffffL;

    private int[] data;  // value: data[0] is least significant
    private int offset;  // number of least significant zero padding ints
    private int nWords;  // data[nWords-1]!=0, all values above are zero
                 // if nWords==0 -> this FDBigInteger is zero
    private boolean isImmutable = false;

    /**
     * Constructs an {@link FDBigInteger} from data and padding. The
     * {@code data} parameter has the least significant {@code int} at
     * the zeroth index. The {@code offset} parameter gives the number of
     * zero {@code int}s to be inferred below the least significant element
     * of {@code data}.
     *
     * @param data An array containing all non-zero {@code int}s of the value.
     * @param offset An offset indicating the number of zero {@code int}s to pad
     * below the least significant element of {@code data}.
     */
    private FDBigInteger(int[] data, int offset) {
        this.data = data;
        this.offset = offset;
        this.nWords = data.length;
        trimLeadingZeros();
    }

    /**
     * Constructs an {@link FDBigInteger} from a starting value and some
     * decimal digits.
     *
     * @param lValue The starting value.
     * @param digits The decimal digits.
     * @param i The initial index into {@code digits}.
     * @param nDigits The final index into {@code digits}.
     */
    public FDBigInteger(long lValue, byte[] digits, int i, int nDigits) {
        int n = (nDigits + 8) / 9;  // estimate size needed: ⌈nDigits / 9⌉
        data = new int[Math.max(n, 2)];
        data[0] = (int) lValue;  // starting value
        data[1] = (int) (lValue >>> 32);
        offset = 0;
        nWords = 2;
        int limit = nDigits - 9;
        while (i < limit) {
            int v = 0;
            int ilim = i + 9;
            while (i < ilim) {
                v = 10 * v + digits[i++] - '0';
            }
            multAdd(1_000_000_000, v);  // 10^9
        }
        if (i < nDigits) {
            int factor = (int) MathUtils.pow10(nDigits - i);
            int v = 0;
            while (i < nDigits) {
                v = 10 * v + digits[i++] - '0';
            }
            multAdd(factor, v);
        }
        trimLeadingZeros();
    }

    public FDBigInteger(long v) {
        this(new int[] {(int) v, (int) (v >>> 32)}, 0);
    }

    /**
     * Returns an {@link FDBigInteger} with the numerical value
     * 5<sup>{@code e5}</sup> * 2<sup>{@code e2}</sup>.
     *
     * @param e5 The exponent of the power-of-five factor.
     * @param e2 The exponent of the power-of-two factor.
     * @return 5<sup>{@code e5}</sup> * 2<sup>{@code e2}</sup>
     */
    public static FDBigInteger valueOfPow52(int e5, int e2) {
        if (e5 == 0) {
            return valueOfPow2(e2);
        }
        if (e2 == 0) {
            return pow5(e5);
        }
        if (e5 >= SMALL_5_POW.length) {
            return pow5(e5).leftShift(e2);
        }
        int pow5 = SMALL_5_POW[e5];
        int offset = e2 >> 5;
        int bitcount = e2 & 0x1f;
        if (bitcount == 0) {
            return new FDBigInteger(new int[] {pow5}, offset);
        }
        return new FDBigInteger(new int[] {
                pow5 << bitcount,
                pow5 >>> -bitcount
            }, offset);
    }

    /**
     * Returns an {@link FDBigInteger} with the numerical value:
     * value * 5<sup>{@code e5}</sup> * 2<sup>{@code e2}</sup>.
     *
     * @param value The constant factor.
     * @param e5 The exponent of the power-of-five factor.
     * @param e2 The exponent of the power-of-two factor.
     * @return value * 5<sup>{@code e5}</sup> * 2<sup>{@code e2}</sup>
     */
    public static FDBigInteger valueOfMulPow52(long value, int e5, int e2) {
        int v0 = (int) value;
        int v1 = (int) (value >>> 32);
        int offset = e2 >> 5;
        int bitcount = e2 & 0x1f;
        if (e5 != 0) {
            if (e5 < SMALL_5_POW.length) {
                long pow5 = SMALL_5_POW[e5] & LONG_MASK;
                long carry = (v0 & LONG_MASK) * pow5;
                v0 = (int) carry;
                carry >>>= 32;
                carry = (v1 & LONG_MASK) * pow5 + carry;
                v1 = (int) carry;
                int v2 = (int) (carry >>> 32);
                return bitcount == 0
                        ? new FDBigInteger(new int[] {v0, v1, v2}, offset)
                        : new FDBigInteger(new int[] {
                                v0 << bitcount,
                                (v1 << bitcount) | (v0 >>> -bitcount),
                                (v2 << bitcount) | (v1 >>> -bitcount),
                                v2 >>> -bitcount
                            }, offset);
            }
            FDBigInteger pow5 = pow5(e5);
            int[] r;
            if (v1 == 0) {
                r = new int[pow5.nWords + 1 + ((e2 != 0) ? 1 : 0)];
                mult(pow5.data, pow5.nWords, v0, r);
            } else {
                r = new int[pow5.nWords + 2 + ((e2 != 0) ? 1 : 0)];
                mult(pow5.data, pow5.nWords, v0, v1, r);
            }
            return (new FDBigInteger(r, 0)).leftShift(e2);
        }
        if (e2 != 0) {
            return bitcount == 0
                    ? new FDBigInteger(new int[] {v0, v1}, offset)
                    : new FDBigInteger(new int[] {
                            v0 << bitcount,
                            (v1 << bitcount) | (v0 >>> -bitcount),
                            v1 >>> -bitcount
                        }, offset);
        }
        return new FDBigInteger(new int[] {v0, v1}, 0);
    }

    /**
     * Returns an {@link FDBigInteger} with the numerical value
     * 2<sup>{@code e}</sup>.
     *
     * @param e The exponent of 2.
     * @return 2<sup>{@code e}</sup>
     */
    private static FDBigInteger valueOfPow2(int e) {
        return new FDBigInteger(new int[] {1 << (e & 0x1f)}, e >> 5);
    }

    /**
     * Removes all leading zeros from this {@link FDBigInteger} adjusting
     * the offset and number of non-zero leading words accordingly.
     */
    private void trimLeadingZeros() {
        int i = nWords - 1;
        for (; i >= 0 && data[i] == 0; --i);  // empty body
        nWords = i + 1;
        if (i < 0) {
            offset = 0;
        }
    }

    /**
     * Retrieves the normalization bias of the {@link FDBigInteger}. The
     * normalization bias is a left shift such that after it the highest word
     * of the value will have the 4 highest bits equal to zero:
     * {@code (highestWord & 0xf0000000) == 0}, but the next bit should be 1
     * {@code (highestWord & 0x08000000) != 0}.
     *
     * @return The normalization bias.
     */
    public int getNormalizationBias() {
        if (nWords == 0) {
            throw new IllegalArgumentException("Zero value cannot be normalized");
        }
        int zeros = Integer.numberOfLeadingZeros(data[nWords - 1]);
        return (zeros < 4) ? 28 + zeros : zeros - 4;
    }

    /**
     * Left shifts the contents of one int array into another.
     *
     * @param src The source array.
     * @param idx The initial index of the source array.
     * @param result The destination array.
     * @param bitcount The left shift.
     * @param prev The prior source value.
     */
    private static void leftShift(int[] src, int idx, int[] result, int bitcount, int prev) {
        for (; idx > 0; idx--) {
            int v = prev << bitcount;
            prev = src[idx - 1];
            v |= prev >>> -bitcount;
            result[idx] = v;
        }
        int v = prev << bitcount;
        result[0] = v;
    }

    /**
     * Shifts this {@link FDBigInteger} to the left. The shift is performed
     * in-place unless the {@link FDBigInteger} is immutable in which case
     * a new instance of {@link FDBigInteger} is returned.
     *
     * @param shift The number of bits to shift left.
     * @return The shifted {@link FDBigInteger}.
     */
    public FDBigInteger leftShift(int shift) {
        if (shift == 0 || nWords == 0) {
            return this;
        }
        int wordcount = shift >> 5;
        int bitcount = shift & 0x1f;
        if (isImmutable) {
            if (bitcount == 0) {
                return new FDBigInteger(Arrays.copyOf(data, nWords), offset + wordcount);
            }
            int idx = nWords - 1;
            int prev = data[idx];
            int hi = prev >>> -bitcount;
            int[] result;
            if (hi != 0) {
                result = new int[nWords + 1];
                result[nWords] = hi;
            } else {
                result = new int[nWords];
            }
            leftShift(data, idx, result, bitcount, prev);
            return new FDBigInteger(result, offset + wordcount);
        }
        if (bitcount != 0) {
            if (data[0] << bitcount == 0) {
                int idx = 0;
                int prev = data[idx];
                for (; idx < nWords - 1; idx++) {
                    int v = prev >>> -bitcount;
                    prev = data[idx + 1];
                    v |= prev << bitcount;
                    data[idx] = v;
                }
                int v = prev >>> -bitcount;
                data[idx] = v;
                if (v == 0) {
                    nWords--;
                }
                offset++;
            } else {
                int idx = nWords - 1;
                int prev = data[idx];
                int hi = prev >>> -bitcount;
                int[] result = data;
                int[] src = data;
                if (hi != 0) {
                    if (nWords == data.length) {
                        data = result = new int[nWords + 1];
                    }
                    result[nWords++] = hi;
                }
                leftShift(src, idx, result, bitcount, prev);
            }
        }
        offset += wordcount;
        return this;
    }

    /**
     * Returns the number of {@code int}s this {@link FDBigInteger} represents.
     *
     * @return Number of {@code int}s required to represent this {@link FDBigInteger}.
     */
    private int size() {
        return nWords + offset;
    }

    /**
     * Returns whether this {@link FDBigInteger} is zero.
     *
     * @return {@code this} is zero.
     */
    public boolean isZero() {
        return nWords == 0;
    }

    /**
     * Computes
     * <pre>
     * q = (int)( this / S )
     * this = 10 * ( this mod S )
     * Return q.
     * </pre>
     * This is the iteration step of digit development for output.
     * We assume that S has been normalized, as above, and that
     * "this" has been left-shifted accordingly.
     * Also assumed, of course, is that the result, q, can be expressed
     * as an integer, {@code 0 <= q < 10}.
     *
     * @param S The divisor of this {@link FDBigInteger}.
     * @return {@code q = (int)(this / S)}.
     */
    public int quoRemIteration(FDBigInteger S) throws IllegalArgumentException {
        assert !this.isImmutable : "cannot modify immutable value";
        // ensure that this and S have the same number of
        // digits. If S is properly normalized and q < 10 then
        // this must be so.
        int thSize = this.size();
        int sSize = S.size();
        if (thSize < sSize) {
            // this value is significantly less than S, result of division is zero.
            // just mult this by 10.
            int p = multAndCarryBy10(this.data, this.nWords, this.data);
            if(p!=0) {
                this.data[nWords++] = p;
            } else {
                trimLeadingZeros();
            }
            return 0;
        } else if (thSize > sSize) {
            throw new IllegalArgumentException("disparate values");
        }
        // estimate q the obvious way. We will usually be
        // right. If not, then we're only off by a little and
        // will re-add.
        long q = (this.data[this.nWords - 1] & LONG_MASK) / (S.data[S.nWords - 1] & LONG_MASK);
        long diff = multSub(q, S);
        if (diff != 0L) {
            //@ assert q != 0;
            //@ assert this.offset == \old(Math.min(this.offset, S.offset));
            //@ assert this.offset <= S.offset;

            // q is too big.
            // add S back in until this turns +. This should
            // not be very many times!
            long sum = 0L;
            int tStart = S.offset - this.offset;
            //@ assert tStart >= 0;
            int[] sd = S.data;
            int[] td = this.data;
            while (sum == 0L) {
                for (int sIndex = 0, tIndex = tStart; tIndex < this.nWords; sIndex++, tIndex++) {
                    sum += (td[tIndex] & LONG_MASK) + (sd[sIndex] & LONG_MASK);
                    td[tIndex] = (int) sum;
                    sum >>>= 32; // Signed or unsigned, answer is 0 or 1
                }
                //
                // Originally the following line read
                // "if ( sum !=0 && sum != -1 )"
                // but that would be wrong, because of the
                // treatment of the two values as entirely unsigned,
                // it would be impossible for a carry-out to be interpreted
                // as -1 -- it would have to be a single-bit carry-out, or +1.
                //
                assert sum == 0 || sum == 1 : sum; // carry out of division correction
                q -= 1;
            }
        }
        // finally, we can multiply this by 10.
        // it cannot overflow, right, as the high-order word has
        // at least 4 high-order zeros!
        int p = multAndCarryBy10(this.data, this.nWords, this.data);
        assert p == 0 : p; // Carry out of *10
        trimLeadingZeros();
        return (int) q;
    }

    /**
     * Multiplies this {@link FDBigInteger} by 10. The operation will be
     * performed in place unless the {@link FDBigInteger} is immutable in
     * which case a new {@link FDBigInteger} will be returned.
     *
     * @return The {@link FDBigInteger} multiplied by 10.
     */
    public FDBigInteger multBy10() {
        if (nWords == 0) {
            return this;
        }
        if (isImmutable) {
            int[] res = new int[nWords + 1];
            res[nWords] = multAndCarryBy10(data, nWords, res);
            return new FDBigInteger(res, offset);
        } else {
            int p = multAndCarryBy10(this.data, this.nWords, this.data);
            if (p != 0) {
                if (nWords == data.length) {
                    if (data[0] == 0) {
                        System.arraycopy(data, 1, data, 0, --nWords);
                        offset++;
                    } else {
                        data = Arrays.copyOf(data, data.length + 1);
                    }
                }
                data[nWords++] = p;
            } else {
                trimLeadingZeros();
            }
            return this;
        }
    }

    /**
     * Multiplies this {@link FDBigInteger} by
     * 5<sup>{@code e5}</sup> * 2<sup>{@code e2}</sup>. The operation will be
     * performed in place if possible, otherwise a new {@link FDBigInteger}
     * will be returned.
     *
     * @param e5 The exponent of the power-of-five factor.
     * @param e2 The exponent of the power-of-two factor.
     * @return The multiplication result.
     */
    public FDBigInteger multByPow52(int e5, int e2) {
        if (nWords == 0) {
            return this;
        }
        FDBigInteger res = this;
        if (e5 != 0) {
            int[] r;
            int extraSize = e2 != 0 ? 1 : 0;  // accounts for e2 % 32 shift bits
            if (e5 < SMALL_5_POW.length) {
                r = new int[nWords + 1 + extraSize];
                mult(data, nWords, SMALL_5_POW[e5], r);
            } else if (e5 < LONG_5_POW.length) {
                long pow5 = LONG_5_POW[e5];
                r = new int[nWords + 2 + extraSize];
                mult(data, nWords, (int) pow5, (int) (pow5 >>> 32), r);
            } else {
                FDBigInteger pow5 = pow5(e5);
                r = new int[nWords + pow5.nWords + extraSize];
                mult(data, nWords, pow5.data, pow5.nWords, r);
            }
            res = new FDBigInteger(r, offset);
        }
        return res.leftShift(e2);
    }

    /**
     * Multiplies two big integers represented as int arrays.
     *
     * @param s1 The first array factor.
     * @param s1Len The number of elements of {@code s1} to use.
     * @param s2 The second array factor.
     * @param s2Len The number of elements of {@code s2} to use.
     * @param dst The product array.
     */
    private static void mult(int[] s1, int s1Len, int[] s2, int s2Len, int[] dst) {
        if (s1Len > s2Len) {
            /* Swap ensures that inner loop is longest. */
            int l = s1Len; s1Len = s2Len; s2Len = l;
            int[] s = s1; s1 = s2; s2 = s;
        }
        for (int i = 0; i < s1Len; i++) {
            long v = s1[i] & LONG_MASK;
            long p = 0L;
            for (int j = 0; j < s2Len; j++) {
                p += (dst[i + j] & LONG_MASK) + v * (s2[j] & LONG_MASK);
                dst[i + j] = (int) p;
                p >>>= 32;
            }
            dst[i + s2Len] = (int) p;
        }
    }

    /**
     * Determines whether all elements of an array are zero for all indices less
     * than a given index.
     *
     * @param a The array to be examined.
     * @param from The index strictly below which elements are to be examined.
     * @return Zero if all elements in range are zero, 1 otherwise.
     */
    private static int checkZeroTail(int[] a, int from) {
        while (from > 0) {
            if (a[--from] != 0) {
                return 1;
            }
        }
        return 0;
    }

    /**
     * Compares the parameter with this {@link FDBigInteger}. Returns an
     * integer accordingly as:
     * <pre>{@code
     * > 0: this > other
     *   0: this == other
     * < 0: this < other
     * }</pre>
     *
     * @param other The {@link FDBigInteger} to compare.
     * @return A negative value, zero, or a positive value according to the
     * result of the comparison.
     */
    public int cmp(FDBigInteger other) {
        int aSize = nWords + offset;
        int bSize = other.nWords + other.offset;
        if (aSize > bSize) {
            return 1;
        } else if (aSize < bSize) {
            return -1;
        }
        int aLen = nWords;
        int bLen = other.nWords;
        while (aLen > 0 && bLen > 0) {
            int cmp = Integer.compareUnsigned(data[--aLen], other.data[--bLen]);
            if (cmp != 0) {
                return cmp;
            }
        }
        if (aLen > 0) {
            return checkZeroTail(data, aLen);
        }
        if (bLen > 0) {
            return -checkZeroTail(other.data, bLen);
        }
        return 0;
    }

    /**
     * Compares this {@link FDBigInteger} with {@code x + y}. Returns a
     * value according to the comparison as:
     * <pre>{@code
     * -1: this <  x + y
     *  0: this == x + y
     *  1: this >  x + y
     * }</pre>
     * @param x The first addend of the sum to compare.
     * @param y The second addend of the sum to compare.
     * @return -1, 0, or 1 according to the result of the comparison.
     */
    public int addAndCmp(FDBigInteger x, FDBigInteger y) {
        FDBigInteger big;
        FDBigInteger small;
        int xSize = x.size();
        int ySize = y.size();
        int bSize;
        int sSize;
        if (xSize >= ySize) {
            big = x;
            small = y;
            bSize = xSize;
            sSize = ySize;
        } else {
            big = y;
            small = x;
            bSize = ySize;
            sSize = xSize;
        }
        int thSize = this.size();
        if (bSize == 0) {
            return thSize == 0 ? 0 : 1;
        }
        if (sSize == 0) {
            return this.cmp(big);
        }
        if (bSize > thSize) {
            return -1;
        }
        if (bSize + 1 < thSize) {
            return 1;
        }
        long top = (big.data[big.nWords - 1] & LONG_MASK);
        if (sSize == bSize) {
            top += (small.data[small.nWords - 1] & LONG_MASK);
        }
        if ((top >>> 32) == 0) {
            if (((top + 1) >>> 32) == 0) {
                // good case - no carry extension
                if (bSize < thSize) {
                    return 1;
                }
                // here sum.nWords == this.nWords
                long v = (this.data[this.nWords - 1] & LONG_MASK);
                if (v < top) {
                    return -1;
                }
                if (v > top + 1) {
                    return 1;
                }
            }
        } else { // (top>>>32)!=0 guaranteed carry extension
            if (bSize + 1 > thSize) {
                return -1;
            }
            // here sum.nWords == this.nWords
            top >>>= 32;
            long v = (this.data[this.nWords - 1] & LONG_MASK);
            if (v < top) {
                return -1;
            }
            if (v > top + 1) {
                return 1;
            }
        }
        return this.cmp(big.add(small));
    }

    /**
     * Makes this {@link FDBigInteger} immutable.
     *
     * @return {@code this}
     */
    public FDBigInteger makeImmutable() {
        isImmutable = true;
        return this;
    }

    /**
     * Multiplies this {@link FDBigInteger} by an integer.
     *
     * @param v The factor by which to multiply this {@link FDBigInteger}.
     * @return This {@link FDBigInteger} multiplied by an integer.
     */
    public FDBigInteger mult(int v) {
        if (nWords == 0 || v == 0) {
            return this;
        }
        int[] r = new int[nWords + 1];
        mult(data, nWords, v, r);
        return new FDBigInteger(r, offset);
    }

    /**
     * Multiplies this {@link FDBigInteger} by another {@link FDBigInteger}.
     *
     * @param other The {@link FDBigInteger} factor by which to multiply.
     * @return The product of this and the parameter {@link FDBigInteger}s.
     */
    private FDBigInteger mult(FDBigInteger other) {
        int[] r = new int[nWords + other.nWords];
        mult(data, nWords, other.data, other.nWords, r);
        return new FDBigInteger(r, offset + other.offset);
    }

    /**
     * Adds another {@link FDBigInteger} to this {@link FDBigInteger}.
     *
     * @param other The {@link FDBigInteger} to add.
     * @return The sum of the {@link FDBigInteger}s.
     */
    private FDBigInteger add(FDBigInteger other) {
        FDBigInteger big, small;
        int bigLen, smallLen;
        int tSize = this.size();
        int oSize = other.size();
        if (tSize >= oSize) {
            big = this;
            bigLen = tSize;
            small = other;
            smallLen = oSize;
        } else {
            big = other;
            bigLen = oSize;
            small = this;
            smallLen = tSize;
        }
        int[] r = new int[bigLen + 1];
        int i = 0;
        long carry = 0L;
        for (; i < smallLen; i++) {
            carry += (i < big.offset   ? 0L : (big.data[i - big.offset] & LONG_MASK) )
                   + ((i < small.offset ? 0L : (small.data[i - small.offset] & LONG_MASK)));
            r[i] = (int) carry;
            carry >>= 32; // signed shift.
        }
        for (; i < bigLen; i++) {
            carry += (i < big.offset ? 0L : (big.data[i - big.offset] & LONG_MASK) );
            r[i] = (int) carry;
            carry >>= 32; // signed shift.
        }
        r[bigLen] = (int) carry;
        return new FDBigInteger(r, 0);
    }


    /**
     * Multiplies a {@link FDBigInteger} by an int and adds another int. The
     * result is computed in place. This method is intended only to be invoked
     * from {@link FDBigInteger(long,char[],int,int)}.
     *
     * @param iv The factor by which to multiply this {@link FDBigInteger}.
     * @param addend The value to add to the product of this
     * {@link FDBigInteger} and {@code iv}.
     */
    private void multAdd(int iv, int addend) {
        long v = iv & LONG_MASK;
        // unroll 0th iteration, doing addition.
        long p = v * (data[0] & LONG_MASK) + (addend & LONG_MASK);
        data[0] = (int) p;
        p >>>= 32;
        for (int i = 1; i < nWords; i++) {
            p += v * (data[i] & LONG_MASK);
            data[i] = (int) p;
            p >>>= 32;
        }
        if (p != 0L) {
            data[nWords++] = (int) p;
        }
    }

    /**
     * Multiplies the parameters and subtracts them from this {@link FDBigInteger}.
     *
     * @param q The integer parameter.
     * @param S The {@link FDBigInteger} parameter.
     * @return {@code this - q*S}.
     */
    private long multSub(long q, FDBigInteger S) {
        long diff = 0L;
        if (q != 0) {
            int deltaSize = S.offset - this.offset;
            if (deltaSize >= 0) {
                int[] sd = S.data;
                int[] td = this.data;
                for (int sIndex = 0, tIndex = deltaSize; sIndex < S.nWords; sIndex++, tIndex++) {
                    diff += (td[tIndex] & LONG_MASK) - q * (sd[sIndex] & LONG_MASK);
                    td[tIndex] = (int) diff;
                    diff >>= 32; // N.B. SIGNED shift.
                }
            } else {
                deltaSize = -deltaSize;
                int[] rd = new int[nWords + deltaSize];
                int sIndex = 0;
                int rIndex = 0;
                int[] sd = S.data;
                for (; rIndex < deltaSize && sIndex < S.nWords; sIndex++, rIndex++) {
                    diff -= q * (sd[sIndex] & LONG_MASK);
                    rd[rIndex] = (int) diff;
                    diff >>= 32; // N.B. SIGNED shift.
                }
                int tIndex = 0;
                int[] td = this.data;
                for (; sIndex < S.nWords; sIndex++, tIndex++, rIndex++) {
                    diff += (td[tIndex] & LONG_MASK) - q * (sd[sIndex] & LONG_MASK);
                    rd[rIndex] = (int) diff;
                    diff >>= 32; // N.B. SIGNED shift.
                }
                this.nWords += deltaSize;
                this.offset -= deltaSize;
                this.data = rd;
            }
        }
        return diff;
    }

    /**
     * Multiplies by 10 a big integer represented as an array. The final carry
     * is returned.
     *
     * @param src The array representation of the big integer.
     * @param srcLen The number of elements of {@code src} to use.
     * @param dst The product array.
     * @return The final carry of the multiplication.
     */
    private static int multAndCarryBy10(int[] src, int srcLen, int[] dst) {
        long carry = 0;
        for (int i = 0; i < srcLen; i++) {
            long product = (src[i] & LONG_MASK) * 10L + carry;
            dst[i] = (int) product;
            carry = product >>> 32;
        }
        return (int) carry;
    }

    /**
     * Multiplies by a constant value a big integer represented as an array.
     * The constant factor is an {@code int}.
     *
     * @param src The array representation of the big integer.
     * @param srcLen The number of elements of {@code src} to use.
     * @param value The constant factor by which to multiply.
     * @param dst The product array.
     */
    private static void mult(int[] src, int srcLen, int value, int[] dst) {
        long v = value & LONG_MASK;
        long carry = 0;
        int i = 0;
        for (; i < srcLen; i++) {
            long product = v * (src[i] & LONG_MASK) + carry;
            dst[i] = (int) product;
            carry = product >>> 32;
        }
        dst[i] = (int) carry;
    }

    /**
     * Multiplies by a constant value a big integer represented as an array.
     * The constant factor is a long represent as two {@code int}s.
     *
     * @param src The array representation of the big integer.
     * @param srcLen The number of elements of {@code src} to use.
     * @param v0 The lower 32 bits of the long factor.
     * @param v1 The upper 32 bits of the long factor.
     * @param dst The product array.
     */
    private static void mult(int[] src, int srcLen, int v0, int v1, int[] dst) {
        long v = v0 & LONG_MASK;
        long carry = 0;
        int j = 0;
        for (; j < srcLen; j++) {
            long product = v * (src[j] & LONG_MASK) + carry;
            dst[j] = (int) product;
            carry = product >>> 32;
        }
        dst[j] = (int) carry;

        v = v1 & LONG_MASK;
        carry = 0;
        j = 1;
        for (; j <= srcLen; j++) {
            long product = (dst[j] & LONG_MASK) + v * (src[j - 1] & LONG_MASK) + carry;
            dst[j] = (int) product;
            carry = product >>> 32;
        }
        dst[j] = (int) carry;
    }

    /*
     * Lookup table of powers of 5 starting with 5^MAX_FIVE_POW.
     * The size just serves for the conversions.
     * It is filled lazily, except for the entries with exponent
     * 2 (MAX_FIVE_POW - 1) and 3 (MAX_FIVE_POW - 1).
     *
     * Access needs not be synchronized for thread-safety, since races would
     * produce the same non-null value (although not the same instance).
     */
    @Stable
    private static final FDBigInteger[] LARGE_POW_5_CACHE;

    /**
     * Computes 5<sup>{@code e}</sup>.
     *
     * @param e The exponent of 5.
     * @return 5<sup>{@code e}</sup>.
     * @throws IllegalArgumentException if e > 2 - DoubleToDecimal.Q_MIN = 1076
     */
    private static FDBigInteger pow5(int e) {
        if (e < MAX_FIVE_POW) {
            return POW_5_CACHE[e];
        }
        if (e > 2 - DoubleToDecimal.Q_MIN) {
            throw new IllegalArgumentException("exponent too large: " + e);
        }
        FDBigInteger p5 = LARGE_POW_5_CACHE[e - MAX_FIVE_POW];
        if (p5 == null) {
            int ep = (e - 1) - (e - 1) % (MAX_FIVE_POW - 1);
            p5 = (ep < MAX_FIVE_POW
                    ? POW_5_CACHE[ep]
                    : LARGE_POW_5_CACHE[ep - MAX_FIVE_POW])
                    .mult(POW_5_CACHE[e - ep]);
            LARGE_POW_5_CACHE[e - MAX_FIVE_POW] = p5.makeImmutable();
        }
        return p5;
    }

    // for debugging ...
    /**
     * Converts this {@link FDBigInteger} to a hexadecimal string.
     *
     * @return The hexadecimal string representation.
     */
    @Override
    public String toString() {
        if (nWords == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder(8 * (size()));
        int i = nWords - 1;
        sb.append(Integer.toHexString(data[i--]));
        while (i >= 0) {
            String subStr = Integer.toHexString(data[i--]);
            sb.repeat('0', 8 - subStr.length()).append(subStr);
        }
        return sb.repeat('0', 8 * offset).toString();
    }

    // for debugging ...
    /**
     * Converts this {@link FDBigInteger} to a {@code byte[]} suitable
     * for use with {@link java.math.BigInteger#BigInteger(byte[])}.
     *
     * @return The {@code byte[]} representation.
     */
    /*
     * A toBigInteger() method would be more convenient, but it would introduce
     * a dependency on java.math, which is not desirable in such a basic layer
     * like this one used in components as java.lang, javac, and others.
     */
    public byte[] toByteArray() {
        byte[] magnitude = new byte[4 * size() + 1];  // +1 for the "sign" byte
        for (int i = 0, j = magnitude.length - 4 * offset; i < nWords; i += 1, j -= 4) {
            int w = data[i];
            magnitude[j - 1] = (byte) w;
            magnitude[j - 2] = (byte) (w >> 8);
            magnitude[j - 3] = (byte) (w >> 16);
            magnitude[j - 4] = (byte) (w >> 24);
        }
        return magnitude;
    }

}
