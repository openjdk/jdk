/*
 * Copyright (c) 1996, 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Portions Copyright (c) 1995  Colin Plumb.  All rights reserved.
 */

package java.math;

import java.util.Random;
import java.io.*;

/**
 * Immutable arbitrary-precision integers.  All operations behave as if
 * BigIntegers were represented in two's-complement notation (like Java's
 * primitive integer types).  BigInteger provides analogues to all of Java's
 * primitive integer operators, and all relevant methods from java.lang.Math.
 * Additionally, BigInteger provides operations for modular arithmetic, GCD
 * calculation, primality testing, prime generation, bit manipulation,
 * and a few other miscellaneous operations.
 *
 * <p>Semantics of arithmetic operations exactly mimic those of Java's integer
 * arithmetic operators, as defined in <i>The Java Language Specification</i>.
 * For example, division by zero throws an {@code ArithmeticException}, and
 * division of a negative by a positive yields a negative (or zero) remainder.
 * All of the details in the Spec concerning overflow are ignored, as
 * BigIntegers are made as large as necessary to accommodate the results of an
 * operation.
 *
 * <p>Semantics of shift operations extend those of Java's shift operators
 * to allow for negative shift distances.  A right-shift with a negative
 * shift distance results in a left shift, and vice-versa.  The unsigned
 * right shift operator ({@code >>>}) is omitted, as this operation makes
 * little sense in combination with the "infinite word size" abstraction
 * provided by this class.
 *
 * <p>Semantics of bitwise logical operations exactly mimic those of Java's
 * bitwise integer operators.  The binary operators ({@code and},
 * {@code or}, {@code xor}) implicitly perform sign extension on the shorter
 * of the two operands prior to performing the operation.
 *
 * <p>Comparison operations perform signed integer comparisons, analogous to
 * those performed by Java's relational and equality operators.
 *
 * <p>Modular arithmetic operations are provided to compute residues, perform
 * exponentiation, and compute multiplicative inverses.  These methods always
 * return a non-negative result, between {@code 0} and {@code (modulus - 1)},
 * inclusive.
 *
 * <p>Bit operations operate on a single bit of the two's-complement
 * representation of their operand.  If necessary, the operand is sign-
 * extended so that it contains the designated bit.  None of the single-bit
 * operations can produce a BigInteger with a different sign from the
 * BigInteger being operated on, as they affect only a single bit, and the
 * "infinite word size" abstraction provided by this class ensures that there
 * are infinitely many "virtual sign bits" preceding each BigInteger.
 *
 * <p>For the sake of brevity and clarity, pseudo-code is used throughout the
 * descriptions of BigInteger methods.  The pseudo-code expression
 * {@code (i + j)} is shorthand for "a BigInteger whose value is
 * that of the BigInteger {@code i} plus that of the BigInteger {@code j}."
 * The pseudo-code expression {@code (i == j)} is shorthand for
 * "{@code true} if and only if the BigInteger {@code i} represents the same
 * value as the BigInteger {@code j}."  Other pseudo-code expressions are
 * interpreted similarly.
 *
 * <p>All methods and constructors in this class throw
 * {@code NullPointerException} when passed
 * a null object reference for any input parameter.
 *
 * @see     BigDecimal
 * @author  Josh Bloch
 * @author  Michael McCloskey
 * @since JDK1.1
 */

public class BigInteger extends Number implements Comparable<BigInteger> {
    /**
     * The signum of this BigInteger: -1 for negative, 0 for zero, or
     * 1 for positive.  Note that the BigInteger zero <i>must</i> have
     * a signum of 0.  This is necessary to ensures that there is exactly one
     * representation for each BigInteger value.
     *
     * @serial
     */
    final int signum;

    /**
     * The magnitude of this BigInteger, in <i>big-endian</i> order: the
     * zeroth element of this array is the most-significant int of the
     * magnitude.  The magnitude must be "minimal" in that the most-significant
     * int ({@code mag[0]}) must be non-zero.  This is necessary to
     * ensure that there is exactly one representation for each BigInteger
     * value.  Note that this implies that the BigInteger zero has a
     * zero-length mag array.
     */
    final int[] mag;

    // These "redundant fields" are initialized with recognizable nonsense
    // values, and cached the first time they are needed (or never, if they
    // aren't needed).

     /**
     * One plus the bitCount of this BigInteger. Zeros means unitialized.
     *
     * @serial
     * @see #bitCount
     * @deprecated Deprecated since logical value is offset from stored
     * value and correction factor is applied in accessor method.
     */
    @Deprecated
    private int bitCount;

    /**
     * One plus the bitLength of this BigInteger. Zeros means unitialized.
     * (either value is acceptable).
     *
     * @serial
     * @see #bitLength()
     * @deprecated Deprecated since logical value is offset from stored
     * value and correction factor is applied in accessor method.
     */
    @Deprecated
    private int bitLength;

    /**
     * Two plus the lowest set bit of this BigInteger, as returned by
     * getLowestSetBit().
     *
     * @serial
     * @see #getLowestSetBit
     * @deprecated Deprecated since logical value is offset from stored
     * value and correction factor is applied in accessor method.
     */
    @Deprecated
    private int lowestSetBit;

    /**
     * Two plus the index of the lowest-order int in the magnitude of this
     * BigInteger that contains a nonzero int, or -2 (either value is acceptable).
     * The least significant int has int-number 0, the next int in order of
     * increasing significance has int-number 1, and so forth.
     * @deprecated Deprecated since logical value is offset from stored
     * value and correction factor is applied in accessor method.
     */
    @Deprecated
    private int firstNonzeroIntNum;

    /**
     * This mask is used to obtain the value of an int as if it were unsigned.
     */
    final static long LONG_MASK = 0xffffffffL;

    //Constructors

    /**
     * Translates a byte array containing the two's-complement binary
     * representation of a BigInteger into a BigInteger.  The input array is
     * assumed to be in <i>big-endian</i> byte-order: the most significant
     * byte is in the zeroth element.
     *
     * @param  val big-endian two's-complement binary representation of
     *         BigInteger.
     * @throws NumberFormatException {@code val} is zero bytes long.
     */
    public BigInteger(byte[] val) {
        if (val.length == 0)
            throw new NumberFormatException("Zero length BigInteger");

        if (val[0] < 0) {
            mag = makePositive(val);
            signum = -1;
        } else {
            mag = stripLeadingZeroBytes(val);
            signum = (mag.length == 0 ? 0 : 1);
        }
    }

    /**
     * This private constructor translates an int array containing the
     * two's-complement binary representation of a BigInteger into a
     * BigInteger. The input array is assumed to be in <i>big-endian</i>
     * int-order: the most significant int is in the zeroth element.
     */
    private BigInteger(int[] val) {
        if (val.length == 0)
            throw new NumberFormatException("Zero length BigInteger");

        if (val[0] < 0) {
            mag = makePositive(val);
            signum = -1;
        } else {
            mag = trustedStripLeadingZeroInts(val);
            signum = (mag.length == 0 ? 0 : 1);
        }
    }

    /**
     * Translates the sign-magnitude representation of a BigInteger into a
     * BigInteger.  The sign is represented as an integer signum value: -1 for
     * negative, 0 for zero, or 1 for positive.  The magnitude is a byte array
     * in <i>big-endian</i> byte-order: the most significant byte is in the
     * zeroth element.  A zero-length magnitude array is permissible, and will
     * result in a BigInteger value of 0, whether signum is -1, 0 or 1.
     *
     * @param  signum signum of the number (-1 for negative, 0 for zero, 1
     *         for positive).
     * @param  magnitude big-endian binary representation of the magnitude of
     *         the number.
     * @throws NumberFormatException {@code signum} is not one of the three
     *         legal values (-1, 0, and 1), or {@code signum} is 0 and
     *         {@code magnitude} contains one or more non-zero bytes.
     */
    public BigInteger(int signum, byte[] magnitude) {
        this.mag = stripLeadingZeroBytes(magnitude);

        if (signum < -1 || signum > 1)
            throw(new NumberFormatException("Invalid signum value"));

        if (this.mag.length==0) {
            this.signum = 0;
        } else {
            if (signum == 0)
                throw(new NumberFormatException("signum-magnitude mismatch"));
            this.signum = signum;
        }
    }

    /**
     * A constructor for internal use that translates the sign-magnitude
     * representation of a BigInteger into a BigInteger. It checks the
     * arguments and copies the magnitude so this constructor would be
     * safe for external use.
     */
    private BigInteger(int signum, int[] magnitude) {
        this.mag = stripLeadingZeroInts(magnitude);

        if (signum < -1 || signum > 1)
            throw(new NumberFormatException("Invalid signum value"));

        if (this.mag.length==0) {
            this.signum = 0;
        } else {
            if (signum == 0)
                throw(new NumberFormatException("signum-magnitude mismatch"));
            this.signum = signum;
        }
    }

    /**
     * Translates the String representation of a BigInteger in the
     * specified radix into a BigInteger.  The String representation
     * consists of an optional minus or plus sign followed by a
     * sequence of one or more digits in the specified radix.  The
     * character-to-digit mapping is provided by {@code
     * Character.digit}.  The String may not contain any extraneous
     * characters (whitespace, for example).
     *
     * @param val String representation of BigInteger.
     * @param radix radix to be used in interpreting {@code val}.
     * @throws NumberFormatException {@code val} is not a valid representation
     *         of a BigInteger in the specified radix, or {@code radix} is
     *         outside the range from {@link Character#MIN_RADIX} to
     *         {@link Character#MAX_RADIX}, inclusive.
     * @see    Character#digit
     */
    public BigInteger(String val, int radix) {
        int cursor = 0, numDigits;
        final int len = val.length();

        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
            throw new NumberFormatException("Radix out of range");
        if (len == 0)
            throw new NumberFormatException("Zero length BigInteger");

        // Check for at most one leading sign
        int sign = 1;
        int index1 = val.lastIndexOf('-');
        int index2 = val.lastIndexOf('+');
        if ((index1 + index2) <= -1) {
            // No leading sign character or at most one leading sign character
            if (index1 == 0 || index2 == 0) {
                cursor = 1;
                if (len == 1)
                    throw new NumberFormatException("Zero length BigInteger");
            }
            if (index1 == 0)
                sign = -1;
        } else
            throw new NumberFormatException("Illegal embedded sign character");

        // Skip leading zeros and compute number of digits in magnitude
        while (cursor < len &&
               Character.digit(val.charAt(cursor), radix) == 0)
            cursor++;
        if (cursor == len) {
            signum = 0;
            mag = ZERO.mag;
            return;
        }

        numDigits = len - cursor;
        signum = sign;

        // Pre-allocate array of expected size. May be too large but can
        // never be too small. Typically exact.
        int numBits = (int)(((numDigits * bitsPerDigit[radix]) >>> 10) + 1);
        int numWords = (numBits + 31) >>> 5;
        int[] magnitude = new int[numWords];

        // Process first (potentially short) digit group
        int firstGroupLen = numDigits % digitsPerInt[radix];
        if (firstGroupLen == 0)
            firstGroupLen = digitsPerInt[radix];
        String group = val.substring(cursor, cursor += firstGroupLen);
        magnitude[numWords - 1] = Integer.parseInt(group, radix);
        if (magnitude[numWords - 1] < 0)
            throw new NumberFormatException("Illegal digit");

        // Process remaining digit groups
        int superRadix = intRadix[radix];
        int groupVal = 0;
        while (cursor < len) {
            group = val.substring(cursor, cursor += digitsPerInt[radix]);
            groupVal = Integer.parseInt(group, radix);
            if (groupVal < 0)
                throw new NumberFormatException("Illegal digit");
            destructiveMulAdd(magnitude, superRadix, groupVal);
        }
        // Required for cases where the array was overallocated.
        mag = trustedStripLeadingZeroInts(magnitude);
    }

    // Constructs a new BigInteger using a char array with radix=10
    BigInteger(char[] val) {
        int cursor = 0, numDigits;
        int len = val.length;

        // Check for leading minus sign
        int sign = 1;
        if (val[0] == '-') {
            if (len == 1)
                throw new NumberFormatException("Zero length BigInteger");
            sign = -1;
            cursor = 1;
        } else if (val[0] == '+') {
            if (len == 1)
                throw new NumberFormatException("Zero length BigInteger");
            cursor = 1;
        }

        // Skip leading zeros and compute number of digits in magnitude
        while (cursor < len && Character.digit(val[cursor], 10) == 0)
            cursor++;
        if (cursor == len) {
            signum = 0;
            mag = ZERO.mag;
            return;
        }

        numDigits = len - cursor;
        signum = sign;

        // Pre-allocate array of expected size
        int numWords;
        if (len < 10) {
            numWords = 1;
        } else {
            int numBits = (int)(((numDigits * bitsPerDigit[10]) >>> 10) + 1);
            numWords = (numBits + 31) >>> 5;
        }
        int[] magnitude = new int[numWords];

        // Process first (potentially short) digit group
        int firstGroupLen = numDigits % digitsPerInt[10];
        if (firstGroupLen == 0)
            firstGroupLen = digitsPerInt[10];
        magnitude[numWords - 1] = parseInt(val, cursor,  cursor += firstGroupLen);

        // Process remaining digit groups
        while (cursor < len) {
            int groupVal = parseInt(val, cursor, cursor += digitsPerInt[10]);
            destructiveMulAdd(magnitude, intRadix[10], groupVal);
        }
        mag = trustedStripLeadingZeroInts(magnitude);
    }

    // Create an integer with the digits between the two indexes
    // Assumes start < end. The result may be negative, but it
    // is to be treated as an unsigned value.
    private int parseInt(char[] source, int start, int end) {
        int result = Character.digit(source[start++], 10);
        if (result == -1)
            throw new NumberFormatException(new String(source));

        for (int index = start; index<end; index++) {
            int nextVal = Character.digit(source[index], 10);
            if (nextVal == -1)
                throw new NumberFormatException(new String(source));
            result = 10*result + nextVal;
        }

        return result;
    }

    // bitsPerDigit in the given radix times 1024
    // Rounded up to avoid underallocation.
    private static long bitsPerDigit[] = { 0, 0,
        1024, 1624, 2048, 2378, 2648, 2875, 3072, 3247, 3402, 3543, 3672,
        3790, 3899, 4001, 4096, 4186, 4271, 4350, 4426, 4498, 4567, 4633,
        4696, 4756, 4814, 4870, 4923, 4975, 5025, 5074, 5120, 5166, 5210,
                                           5253, 5295};

    // Multiply x array times word y in place, and add word z
    private static void destructiveMulAdd(int[] x, int y, int z) {
        // Perform the multiplication word by word
        long ylong = y & LONG_MASK;
        long zlong = z & LONG_MASK;
        int len = x.length;

        long product = 0;
        long carry = 0;
        for (int i = len-1; i >= 0; i--) {
            product = ylong * (x[i] & LONG_MASK) + carry;
            x[i] = (int)product;
            carry = product >>> 32;
        }

        // Perform the addition
        long sum = (x[len-1] & LONG_MASK) + zlong;
        x[len-1] = (int)sum;
        carry = sum >>> 32;
        for (int i = len-2; i >= 0; i--) {
            sum = (x[i] & LONG_MASK) + carry;
            x[i] = (int)sum;
            carry = sum >>> 32;
        }
    }

    /**
     * Translates the decimal String representation of a BigInteger into a
     * BigInteger.  The String representation consists of an optional minus
     * sign followed by a sequence of one or more decimal digits.  The
     * character-to-digit mapping is provided by {@code Character.digit}.
     * The String may not contain any extraneous characters (whitespace, for
     * example).
     *
     * @param val decimal String representation of BigInteger.
     * @throws NumberFormatException {@code val} is not a valid representation
     *         of a BigInteger.
     * @see    Character#digit
     */
    public BigInteger(String val) {
        this(val, 10);
    }

    /**
     * Constructs a randomly generated BigInteger, uniformly distributed over
     * the range 0 to (2<sup>{@code numBits}</sup> - 1), inclusive.
     * The uniformity of the distribution assumes that a fair source of random
     * bits is provided in {@code rnd}.  Note that this constructor always
     * constructs a non-negative BigInteger.
     *
     * @param  numBits maximum bitLength of the new BigInteger.
     * @param  rnd source of randomness to be used in computing the new
     *         BigInteger.
     * @throws IllegalArgumentException {@code numBits} is negative.
     * @see #bitLength()
     */
    public BigInteger(int numBits, Random rnd) {
        this(1, randomBits(numBits, rnd));
    }

    private static byte[] randomBits(int numBits, Random rnd) {
        if (numBits < 0)
            throw new IllegalArgumentException("numBits must be non-negative");
        int numBytes = (int)(((long)numBits+7)/8); // avoid overflow
        byte[] randomBits = new byte[numBytes];

        // Generate random bytes and mask out any excess bits
        if (numBytes > 0) {
            rnd.nextBytes(randomBits);
            int excessBits = 8*numBytes - numBits;
            randomBits[0] &= (1 << (8-excessBits)) - 1;
        }
        return randomBits;
    }

    /**
     * Constructs a randomly generated positive BigInteger that is probably
     * prime, with the specified bitLength.
     *
     * <p>It is recommended that the {@link #probablePrime probablePrime}
     * method be used in preference to this constructor unless there
     * is a compelling need to specify a certainty.
     *
     * @param  bitLength bitLength of the returned BigInteger.
     * @param  certainty a measure of the uncertainty that the caller is
     *         willing to tolerate.  The probability that the new BigInteger
     *         represents a prime number will exceed
     *         (1 - 1/2<sup>{@code certainty}</sup>).  The execution time of
     *         this constructor is proportional to the value of this parameter.
     * @param  rnd source of random bits used to select candidates to be
     *         tested for primality.
     * @throws ArithmeticException {@code bitLength < 2}.
     * @see    #bitLength()
     */
    public BigInteger(int bitLength, int certainty, Random rnd) {
        BigInteger prime;

        if (bitLength < 2)
            throw new ArithmeticException("bitLength < 2");
        // The cutoff of 95 was chosen empirically for best performance
        prime = (bitLength < 95 ? smallPrime(bitLength, certainty, rnd)
                                : largePrime(bitLength, certainty, rnd));
        signum = 1;
        mag = prime.mag;
    }

    // Minimum size in bits that the requested prime number has
    // before we use the large prime number generating algorithms
    private static final int SMALL_PRIME_THRESHOLD = 95;

    // Certainty required to meet the spec of probablePrime
    private static final int DEFAULT_PRIME_CERTAINTY = 100;

    /**
     * Returns a positive BigInteger that is probably prime, with the
     * specified bitLength. The probability that a BigInteger returned
     * by this method is composite does not exceed 2<sup>-100</sup>.
     *
     * @param  bitLength bitLength of the returned BigInteger.
     * @param  rnd source of random bits used to select candidates to be
     *         tested for primality.
     * @return a BigInteger of {@code bitLength} bits that is probably prime
     * @throws ArithmeticException {@code bitLength < 2}.
     * @see    #bitLength()
     * @since 1.4
     */
    public static BigInteger probablePrime(int bitLength, Random rnd) {
        if (bitLength < 2)
            throw new ArithmeticException("bitLength < 2");

        // The cutoff of 95 was chosen empirically for best performance
        return (bitLength < SMALL_PRIME_THRESHOLD ?
                smallPrime(bitLength, DEFAULT_PRIME_CERTAINTY, rnd) :
                largePrime(bitLength, DEFAULT_PRIME_CERTAINTY, rnd));
    }

    /**
     * Find a random number of the specified bitLength that is probably prime.
     * This method is used for smaller primes, its performance degrades on
     * larger bitlengths.
     *
     * This method assumes bitLength > 1.
     */
    private static BigInteger smallPrime(int bitLength, int certainty, Random rnd) {
        int magLen = (bitLength + 31) >>> 5;
        int temp[] = new int[magLen];
        int highBit = 1 << ((bitLength+31) & 0x1f);  // High bit of high int
        int highMask = (highBit << 1) - 1;  // Bits to keep in high int

        while(true) {
            // Construct a candidate
            for (int i=0; i<magLen; i++)
                temp[i] = rnd.nextInt();
            temp[0] = (temp[0] & highMask) | highBit;  // Ensure exact length
            if (bitLength > 2)
                temp[magLen-1] |= 1;  // Make odd if bitlen > 2

            BigInteger p = new BigInteger(temp, 1);

            // Do cheap "pre-test" if applicable
            if (bitLength > 6) {
                long r = p.remainder(SMALL_PRIME_PRODUCT).longValue();
                if ((r%3==0)  || (r%5==0)  || (r%7==0)  || (r%11==0) ||
                    (r%13==0) || (r%17==0) || (r%19==0) || (r%23==0) ||
                    (r%29==0) || (r%31==0) || (r%37==0) || (r%41==0))
                    continue; // Candidate is composite; try another
            }

            // All candidates of bitLength 2 and 3 are prime by this point
            if (bitLength < 4)
                return p;

            // Do expensive test if we survive pre-test (or it's inapplicable)
            if (p.primeToCertainty(certainty, rnd))
                return p;
        }
    }

    private static final BigInteger SMALL_PRIME_PRODUCT
                       = valueOf(3L*5*7*11*13*17*19*23*29*31*37*41);

    /**
     * Find a random number of the specified bitLength that is probably prime.
     * This method is more appropriate for larger bitlengths since it uses
     * a sieve to eliminate most composites before using a more expensive
     * test.
     */
    private static BigInteger largePrime(int bitLength, int certainty, Random rnd) {
        BigInteger p;
        p = new BigInteger(bitLength, rnd).setBit(bitLength-1);
        p.mag[p.mag.length-1] &= 0xfffffffe;

        // Use a sieve length likely to contain the next prime number
        int searchLen = (bitLength / 20) * 64;
        BitSieve searchSieve = new BitSieve(p, searchLen);
        BigInteger candidate = searchSieve.retrieve(p, certainty, rnd);

        while ((candidate == null) || (candidate.bitLength() != bitLength)) {
            p = p.add(BigInteger.valueOf(2*searchLen));
            if (p.bitLength() != bitLength)
                p = new BigInteger(bitLength, rnd).setBit(bitLength-1);
            p.mag[p.mag.length-1] &= 0xfffffffe;
            searchSieve = new BitSieve(p, searchLen);
            candidate = searchSieve.retrieve(p, certainty, rnd);
        }
        return candidate;
    }

   /**
    * Returns the first integer greater than this {@code BigInteger} that
    * is probably prime.  The probability that the number returned by this
    * method is composite does not exceed 2<sup>-100</sup>. This method will
    * never skip over a prime when searching: if it returns {@code p}, there
    * is no prime {@code q} such that {@code this < q < p}.
    *
    * @return the first integer greater than this {@code BigInteger} that
    *         is probably prime.
    * @throws ArithmeticException {@code this < 0}.
    * @since 1.5
    */
    public BigInteger nextProbablePrime() {
        if (this.signum < 0)
            throw new ArithmeticException("start < 0: " + this);

        // Handle trivial cases
        if ((this.signum == 0) || this.equals(ONE))
            return TWO;

        BigInteger result = this.add(ONE);

        // Fastpath for small numbers
        if (result.bitLength() < SMALL_PRIME_THRESHOLD) {

            // Ensure an odd number
            if (!result.testBit(0))
                result = result.add(ONE);

            while(true) {
                // Do cheap "pre-test" if applicable
                if (result.bitLength() > 6) {
                    long r = result.remainder(SMALL_PRIME_PRODUCT).longValue();
                    if ((r%3==0)  || (r%5==0)  || (r%7==0)  || (r%11==0) ||
                        (r%13==0) || (r%17==0) || (r%19==0) || (r%23==0) ||
                        (r%29==0) || (r%31==0) || (r%37==0) || (r%41==0)) {
                        result = result.add(TWO);
                        continue; // Candidate is composite; try another
                    }
                }

                // All candidates of bitLength 2 and 3 are prime by this point
                if (result.bitLength() < 4)
                    return result;

                // The expensive test
                if (result.primeToCertainty(DEFAULT_PRIME_CERTAINTY, null))
                    return result;

                result = result.add(TWO);
            }
        }

        // Start at previous even number
        if (result.testBit(0))
            result = result.subtract(ONE);

        // Looking for the next large prime
        int searchLen = (result.bitLength() / 20) * 64;

        while(true) {
           BitSieve searchSieve = new BitSieve(result, searchLen);
           BigInteger candidate = searchSieve.retrieve(result,
                                                 DEFAULT_PRIME_CERTAINTY, null);
           if (candidate != null)
               return candidate;
           result = result.add(BigInteger.valueOf(2 * searchLen));
        }
    }

    /**
     * Returns {@code true} if this BigInteger is probably prime,
     * {@code false} if it's definitely composite.
     *
     * This method assumes bitLength > 2.
     *
     * @param  certainty a measure of the uncertainty that the caller is
     *         willing to tolerate: if the call returns {@code true}
     *         the probability that this BigInteger is prime exceeds
     *         {@code (1 - 1/2<sup>certainty</sup>)}.  The execution time of
     *         this method is proportional to the value of this parameter.
     * @return {@code true} if this BigInteger is probably prime,
     *         {@code false} if it's definitely composite.
     */
    boolean primeToCertainty(int certainty, Random random) {
        int rounds = 0;
        int n = (Math.min(certainty, Integer.MAX_VALUE-1)+1)/2;

        // The relationship between the certainty and the number of rounds
        // we perform is given in the draft standard ANSI X9.80, "PRIME
        // NUMBER GENERATION, PRIMALITY TESTING, AND PRIMALITY CERTIFICATES".
        int sizeInBits = this.bitLength();
        if (sizeInBits < 100) {
            rounds = 50;
            rounds = n < rounds ? n : rounds;
            return passesMillerRabin(rounds, random);
        }

        if (sizeInBits < 256) {
            rounds = 27;
        } else if (sizeInBits < 512) {
            rounds = 15;
        } else if (sizeInBits < 768) {
            rounds = 8;
        } else if (sizeInBits < 1024) {
            rounds = 4;
        } else {
            rounds = 2;
        }
        rounds = n < rounds ? n : rounds;

        return passesMillerRabin(rounds, random) && passesLucasLehmer();
    }

    /**
     * Returns true iff this BigInteger is a Lucas-Lehmer probable prime.
     *
     * The following assumptions are made:
     * This BigInteger is a positive, odd number.
     */
    private boolean passesLucasLehmer() {
        BigInteger thisPlusOne = this.add(ONE);

        // Step 1
        int d = 5;
        while (jacobiSymbol(d, this) != -1) {
            // 5, -7, 9, -11, ...
            d = (d<0) ? Math.abs(d)+2 : -(d+2);
        }

        // Step 2
        BigInteger u = lucasLehmerSequence(d, thisPlusOne, this);

        // Step 3
        return u.mod(this).equals(ZERO);
    }

    /**
     * Computes Jacobi(p,n).
     * Assumes n positive, odd, n>=3.
     */
    private static int jacobiSymbol(int p, BigInteger n) {
        if (p == 0)
            return 0;

        // Algorithm and comments adapted from Colin Plumb's C library.
        int j = 1;
        int u = n.mag[n.mag.length-1];

        // Make p positive
        if (p < 0) {
            p = -p;
            int n8 = u & 7;
            if ((n8 == 3) || (n8 == 7))
                j = -j; // 3 (011) or 7 (111) mod 8
        }

        // Get rid of factors of 2 in p
        while ((p & 3) == 0)
            p >>= 2;
        if ((p & 1) == 0) {
            p >>= 1;
            if (((u ^ (u>>1)) & 2) != 0)
                j = -j; // 3 (011) or 5 (101) mod 8
        }
        if (p == 1)
            return j;
        // Then, apply quadratic reciprocity
        if ((p & u & 2) != 0)   // p = u = 3 (mod 4)?
            j = -j;
        // And reduce u mod p
        u = n.mod(BigInteger.valueOf(p)).intValue();

        // Now compute Jacobi(u,p), u < p
        while (u != 0) {
            while ((u & 3) == 0)
                u >>= 2;
            if ((u & 1) == 0) {
                u >>= 1;
                if (((p ^ (p>>1)) & 2) != 0)
                    j = -j;     // 3 (011) or 5 (101) mod 8
            }
            if (u == 1)
                return j;
            // Now both u and p are odd, so use quadratic reciprocity
            assert (u < p);
            int t = u; u = p; p = t;
            if ((u & p & 2) != 0) // u = p = 3 (mod 4)?
                j = -j;
            // Now u >= p, so it can be reduced
            u %= p;
        }
        return 0;
    }

    private static BigInteger lucasLehmerSequence(int z, BigInteger k, BigInteger n) {
        BigInteger d = BigInteger.valueOf(z);
        BigInteger u = ONE; BigInteger u2;
        BigInteger v = ONE; BigInteger v2;

        for (int i=k.bitLength()-2; i>=0; i--) {
            u2 = u.multiply(v).mod(n);

            v2 = v.square().add(d.multiply(u.square())).mod(n);
            if (v2.testBit(0))
                v2 = v2.subtract(n);

            v2 = v2.shiftRight(1);

            u = u2; v = v2;
            if (k.testBit(i)) {
                u2 = u.add(v).mod(n);
                if (u2.testBit(0))
                    u2 = u2.subtract(n);

                u2 = u2.shiftRight(1);
                v2 = v.add(d.multiply(u)).mod(n);
                if (v2.testBit(0))
                    v2 = v2.subtract(n);
                v2 = v2.shiftRight(1);

                u = u2; v = v2;
            }
        }
        return u;
    }

    private static volatile Random staticRandom;

    private static Random getSecureRandom() {
        if (staticRandom == null) {
            staticRandom = new java.security.SecureRandom();
        }
        return staticRandom;
    }

    /**
     * Returns true iff this BigInteger passes the specified number of
     * Miller-Rabin tests. This test is taken from the DSA spec (NIST FIPS
     * 186-2).
     *
     * The following assumptions are made:
     * This BigInteger is a positive, odd number greater than 2.
     * iterations<=50.
     */
    private boolean passesMillerRabin(int iterations, Random rnd) {
        // Find a and m such that m is odd and this == 1 + 2**a * m
        BigInteger thisMinusOne = this.subtract(ONE);
        BigInteger m = thisMinusOne;
        int a = m.getLowestSetBit();
        m = m.shiftRight(a);

        // Do the tests
        if (rnd == null) {
            rnd = getSecureRandom();
        }
        for (int i=0; i<iterations; i++) {
            // Generate a uniform random on (1, this)
            BigInteger b;
            do {
                b = new BigInteger(this.bitLength(), rnd);
            } while (b.compareTo(ONE) <= 0 || b.compareTo(this) >= 0);

            int j = 0;
            BigInteger z = b.modPow(m, this);
            while(!((j==0 && z.equals(ONE)) || z.equals(thisMinusOne))) {
                if (j>0 && z.equals(ONE) || ++j==a)
                    return false;
                z = z.modPow(TWO, this);
            }
        }
        return true;
    }

    /**
     * This internal constructor differs from its public cousin
     * with the arguments reversed in two ways: it assumes that its
     * arguments are correct, and it doesn't copy the magnitude array.
     */
    BigInteger(int[] magnitude, int signum) {
        this.signum = (magnitude.length==0 ? 0 : signum);
        this.mag = magnitude;
    }

    /**
     * This private constructor is for internal use and assumes that its
     * arguments are correct.
     */
    private BigInteger(byte[] magnitude, int signum) {
        this.signum = (magnitude.length==0 ? 0 : signum);
        this.mag = stripLeadingZeroBytes(magnitude);
    }

    //Static Factory Methods

    /**
     * Returns a BigInteger whose value is equal to that of the
     * specified {@code long}.  This "static factory method" is
     * provided in preference to a ({@code long}) constructor
     * because it allows for reuse of frequently used BigIntegers.
     *
     * @param  val value of the BigInteger to return.
     * @return a BigInteger with the specified value.
     */
    public static BigInteger valueOf(long val) {
        // If -MAX_CONSTANT < val < MAX_CONSTANT, return stashed constant
        if (val == 0)
            return ZERO;
        if (val > 0 && val <= MAX_CONSTANT)
            return posConst[(int) val];
        else if (val < 0 && val >= -MAX_CONSTANT)
            return negConst[(int) -val];

        return new BigInteger(val);
    }

    /**
     * Constructs a BigInteger with the specified value, which may not be zero.
     */
    private BigInteger(long val) {
        if (val < 0) {
            val = -val;
            signum = -1;
        } else {
            signum = 1;
        }

        int highWord = (int)(val >>> 32);
        if (highWord==0) {
            mag = new int[1];
            mag[0] = (int)val;
        } else {
            mag = new int[2];
            mag[0] = highWord;
            mag[1] = (int)val;
        }
    }

    /**
     * Returns a BigInteger with the given two's complement representation.
     * Assumes that the input array will not be modified (the returned
     * BigInteger will reference the input array if feasible).
     */
    private static BigInteger valueOf(int val[]) {
        return (val[0]>0 ? new BigInteger(val, 1) : new BigInteger(val));
    }

    // Constants

    /**
     * Initialize static constant array when class is loaded.
     */
    private final static int MAX_CONSTANT = 16;
    private static BigInteger posConst[] = new BigInteger[MAX_CONSTANT+1];
    private static BigInteger negConst[] = new BigInteger[MAX_CONSTANT+1];
    static {
        for (int i = 1; i <= MAX_CONSTANT; i++) {
            int[] magnitude = new int[1];
            magnitude[0] = i;
            posConst[i] = new BigInteger(magnitude,  1);
            negConst[i] = new BigInteger(magnitude, -1);
        }
    }

    /**
     * The BigInteger constant zero.
     *
     * @since   1.2
     */
    public static final BigInteger ZERO = new BigInteger(new int[0], 0);

    /**
     * The BigInteger constant one.
     *
     * @since   1.2
     */
    public static final BigInteger ONE = valueOf(1);

    /**
     * The BigInteger constant two.  (Not exported.)
     */
    private static final BigInteger TWO = valueOf(2);

    /**
     * The BigInteger constant ten.
     *
     * @since   1.5
     */
    public static final BigInteger TEN = valueOf(10);

    // Arithmetic Operations

    /**
     * Returns a BigInteger whose value is {@code (this + val)}.
     *
     * @param  val value to be added to this BigInteger.
     * @return {@code this + val}
     */
    public BigInteger add(BigInteger val) {
        if (val.signum == 0)
            return this;
        if (signum == 0)
            return val;
        if (val.signum == signum)
            return new BigInteger(add(mag, val.mag), signum);

        int cmp = compareMagnitude(val);
        if (cmp == 0)
            return ZERO;
        int[] resultMag = (cmp > 0 ? subtract(mag, val.mag)
                           : subtract(val.mag, mag));
        resultMag = trustedStripLeadingZeroInts(resultMag);

        return new BigInteger(resultMag, cmp == signum ? 1 : -1);
    }

    /**
     * Adds the contents of the int arrays x and y. This method allocates
     * a new int array to hold the answer and returns a reference to that
     * array.
     */
    private static int[] add(int[] x, int[] y) {
        // If x is shorter, swap the two arrays
        if (x.length < y.length) {
            int[] tmp = x;
            x = y;
            y = tmp;
        }

        int xIndex = x.length;
        int yIndex = y.length;
        int result[] = new int[xIndex];
        long sum = 0;

        // Add common parts of both numbers
        while(yIndex > 0) {
            sum = (x[--xIndex] & LONG_MASK) +
                  (y[--yIndex] & LONG_MASK) + (sum >>> 32);
            result[xIndex] = (int)sum;
        }

        // Copy remainder of longer number while carry propagation is required
        boolean carry = (sum >>> 32 != 0);
        while (xIndex > 0 && carry)
            carry = ((result[--xIndex] = x[xIndex] + 1) == 0);

        // Copy remainder of longer number
        while (xIndex > 0)
            result[--xIndex] = x[xIndex];

        // Grow result if necessary
        if (carry) {
            int bigger[] = new int[result.length + 1];
            System.arraycopy(result, 0, bigger, 1, result.length);
            bigger[0] = 0x01;
            return bigger;
        }
        return result;
    }

    /**
     * Returns a BigInteger whose value is {@code (this - val)}.
     *
     * @param  val value to be subtracted from this BigInteger.
     * @return {@code this - val}
     */
    public BigInteger subtract(BigInteger val) {
        if (val.signum == 0)
            return this;
        if (signum == 0)
            return val.negate();
        if (val.signum != signum)
            return new BigInteger(add(mag, val.mag), signum);

        int cmp = compareMagnitude(val);
        if (cmp == 0)
            return ZERO;
        int[] resultMag = (cmp > 0 ? subtract(mag, val.mag)
                           : subtract(val.mag, mag));
        resultMag = trustedStripLeadingZeroInts(resultMag);
        return new BigInteger(resultMag, cmp == signum ? 1 : -1);
    }

    /**
     * Subtracts the contents of the second int arrays (little) from the
     * first (big).  The first int array (big) must represent a larger number
     * than the second.  This method allocates the space necessary to hold the
     * answer.
     */
    private static int[] subtract(int[] big, int[] little) {
        int bigIndex = big.length;
        int result[] = new int[bigIndex];
        int littleIndex = little.length;
        long difference = 0;

        // Subtract common parts of both numbers
        while(littleIndex > 0) {
            difference = (big[--bigIndex] & LONG_MASK) -
                         (little[--littleIndex] & LONG_MASK) +
                         (difference >> 32);
            result[bigIndex] = (int)difference;
        }

        // Subtract remainder of longer number while borrow propagates
        boolean borrow = (difference >> 32 != 0);
        while (bigIndex > 0 && borrow)
            borrow = ((result[--bigIndex] = big[bigIndex] - 1) == -1);

        // Copy remainder of longer number
        while (bigIndex > 0)
            result[--bigIndex] = big[bigIndex];

        return result;
    }

    /**
     * Returns a BigInteger whose value is {@code (this * val)}.
     *
     * @param  val value to be multiplied by this BigInteger.
     * @return {@code this * val}
     */
    public BigInteger multiply(BigInteger val) {
        if (val.signum == 0 || signum == 0)
            return ZERO;

        int[] result = multiplyToLen(mag, mag.length,
                                     val.mag, val.mag.length, null);
        result = trustedStripLeadingZeroInts(result);
        return new BigInteger(result, signum == val.signum ? 1 : -1);
    }

    /**
     * Package private methods used by BigDecimal code to multiply a BigInteger
     * with a long. Assumes v is not equal to INFLATED.
     */
    BigInteger multiply(long v) {
        if (v == 0 || signum == 0)
          return ZERO;
        if (v == BigDecimal.INFLATED)
            return multiply(BigInteger.valueOf(v));
        int rsign = (v > 0 ? signum : -signum);
        if (v < 0)
            v = -v;
        long dh = v >>> 32;      // higher order bits
        long dl = v & LONG_MASK; // lower order bits

        int xlen = mag.length;
        int[] value = mag;
        int[] rmag = (dh == 0L) ? (new int[xlen + 1]) : (new int[xlen + 2]);
        long carry = 0;
        int rstart = rmag.length - 1;
        for (int i = xlen - 1; i >= 0; i--) {
            long product = (value[i] & LONG_MASK) * dl + carry;
            rmag[rstart--] = (int)product;
            carry = product >>> 32;
        }
        rmag[rstart] = (int)carry;
        if (dh != 0L) {
            carry = 0;
            rstart = rmag.length - 2;
            for (int i = xlen - 1; i >= 0; i--) {
                long product = (value[i] & LONG_MASK) * dh +
                    (rmag[rstart] & LONG_MASK) + carry;
                rmag[rstart--] = (int)product;
                carry = product >>> 32;
            }
            rmag[0] = (int)carry;
        }
        if (carry == 0L)
            rmag = java.util.Arrays.copyOfRange(rmag, 1, rmag.length);
        return new BigInteger(rmag, rsign);
    }

    /**
     * Multiplies int arrays x and y to the specified lengths and places
     * the result into z. There will be no leading zeros in the resultant array.
     */
    private int[] multiplyToLen(int[] x, int xlen, int[] y, int ylen, int[] z) {
        int xstart = xlen - 1;
        int ystart = ylen - 1;

        if (z == null || z.length < (xlen+ ylen))
            z = new int[xlen+ylen];

        long carry = 0;
        for (int j=ystart, k=ystart+1+xstart; j>=0; j--, k--) {
            long product = (y[j] & LONG_MASK) *
                           (x[xstart] & LONG_MASK) + carry;
            z[k] = (int)product;
            carry = product >>> 32;
        }
        z[xstart] = (int)carry;

        for (int i = xstart-1; i >= 0; i--) {
            carry = 0;
            for (int j=ystart, k=ystart+1+i; j>=0; j--, k--) {
                long product = (y[j] & LONG_MASK) *
                               (x[i] & LONG_MASK) +
                               (z[k] & LONG_MASK) + carry;
                z[k] = (int)product;
                carry = product >>> 32;
            }
            z[i] = (int)carry;
        }
        return z;
    }

    /**
     * Returns a BigInteger whose value is {@code (this<sup>2</sup>)}.
     *
     * @return {@code this<sup>2</sup>}
     */
    private BigInteger square() {
        if (signum == 0)
            return ZERO;
        int[] z = squareToLen(mag, mag.length, null);
        return new BigInteger(trustedStripLeadingZeroInts(z), 1);
    }

    /**
     * Squares the contents of the int array x. The result is placed into the
     * int array z.  The contents of x are not changed.
     */
    private static final int[] squareToLen(int[] x, int len, int[] z) {
        /*
         * The algorithm used here is adapted from Colin Plumb's C library.
         * Technique: Consider the partial products in the multiplication
         * of "abcde" by itself:
         *
         *               a  b  c  d  e
         *            *  a  b  c  d  e
         *          ==================
         *              ae be ce de ee
         *           ad bd cd dd de
         *        ac bc cc cd ce
         *     ab bb bc bd be
         *  aa ab ac ad ae
         *
         * Note that everything above the main diagonal:
         *              ae be ce de = (abcd) * e
         *           ad bd cd       = (abc) * d
         *        ac bc             = (ab) * c
         *     ab                   = (a) * b
         *
         * is a copy of everything below the main diagonal:
         *                       de
         *                 cd ce
         *           bc bd be
         *     ab ac ad ae
         *
         * Thus, the sum is 2 * (off the diagonal) + diagonal.
         *
         * This is accumulated beginning with the diagonal (which
         * consist of the squares of the digits of the input), which is then
         * divided by two, the off-diagonal added, and multiplied by two
         * again.  The low bit is simply a copy of the low bit of the
         * input, so it doesn't need special care.
         */
        int zlen = len << 1;
        if (z == null || z.length < zlen)
            z = new int[zlen];

        // Store the squares, right shifted one bit (i.e., divided by 2)
        int lastProductLowWord = 0;
        for (int j=0, i=0; j<len; j++) {
            long piece = (x[j] & LONG_MASK);
            long product = piece * piece;
            z[i++] = (lastProductLowWord << 31) | (int)(product >>> 33);
            z[i++] = (int)(product >>> 1);
            lastProductLowWord = (int)product;
        }

        // Add in off-diagonal sums
        for (int i=len, offset=1; i>0; i--, offset+=2) {
            int t = x[i-1];
            t = mulAdd(z, x, offset, i-1, t);
            addOne(z, offset-1, i, t);
        }

        // Shift back up and set low bit
        primitiveLeftShift(z, zlen, 1);
        z[zlen-1] |= x[len-1] & 1;

        return z;
    }

    /**
     * Returns a BigInteger whose value is {@code (this / val)}.
     *
     * @param  val value by which this BigInteger is to be divided.
     * @return {@code this / val}
     * @throws ArithmeticException if {@code val} is zero.
     */
    public BigInteger divide(BigInteger val) {
        MutableBigInteger q = new MutableBigInteger(),
                          a = new MutableBigInteger(this.mag),
                          b = new MutableBigInteger(val.mag);

        a.divide(b, q);
        return q.toBigInteger(this.signum == val.signum ? 1 : -1);
    }

    /**
     * Returns an array of two BigIntegers containing {@code (this / val)}
     * followed by {@code (this % val)}.
     *
     * @param  val value by which this BigInteger is to be divided, and the
     *         remainder computed.
     * @return an array of two BigIntegers: the quotient {@code (this / val)}
     *         is the initial element, and the remainder {@code (this % val)}
     *         is the final element.
     * @throws ArithmeticException if {@code val} is zero.
     */
    public BigInteger[] divideAndRemainder(BigInteger val) {
        BigInteger[] result = new BigInteger[2];
        MutableBigInteger q = new MutableBigInteger(),
                          a = new MutableBigInteger(this.mag),
                          b = new MutableBigInteger(val.mag);
        MutableBigInteger r = a.divide(b, q);
        result[0] = q.toBigInteger(this.signum == val.signum ? 1 : -1);
        result[1] = r.toBigInteger(this.signum);
        return result;
    }

    /**
     * Returns a BigInteger whose value is {@code (this % val)}.
     *
     * @param  val value by which this BigInteger is to be divided, and the
     *         remainder computed.
     * @return {@code this % val}
     * @throws ArithmeticException if {@code val} is zero.
     */
    public BigInteger remainder(BigInteger val) {
        MutableBigInteger q = new MutableBigInteger(),
                          a = new MutableBigInteger(this.mag),
                          b = new MutableBigInteger(val.mag);

        return a.divide(b, q).toBigInteger(this.signum);
    }

    /**
     * Returns a BigInteger whose value is <tt>(this<sup>exponent</sup>)</tt>.
     * Note that {@code exponent} is an integer rather than a BigInteger.
     *
     * @param  exponent exponent to which this BigInteger is to be raised.
     * @return <tt>this<sup>exponent</sup></tt>
     * @throws ArithmeticException {@code exponent} is negative.  (This would
     *         cause the operation to yield a non-integer value.)
     */
    public BigInteger pow(int exponent) {
        if (exponent < 0)
            throw new ArithmeticException("Negative exponent");
        if (signum==0)
            return (exponent==0 ? ONE : this);

        // Perform exponentiation using repeated squaring trick
        int newSign = (signum<0 && (exponent&1)==1 ? -1 : 1);
        int[] baseToPow2 = this.mag;
        int[] result = {1};

        while (exponent != 0) {
            if ((exponent & 1)==1) {
                result = multiplyToLen(result, result.length,
                                       baseToPow2, baseToPow2.length, null);
                result = trustedStripLeadingZeroInts(result);
            }
            if ((exponent >>>= 1) != 0) {
                baseToPow2 = squareToLen(baseToPow2, baseToPow2.length, null);
                baseToPow2 = trustedStripLeadingZeroInts(baseToPow2);
            }
        }
        return new BigInteger(result, newSign);
    }

    /**
     * Returns a BigInteger whose value is the greatest common divisor of
     * {@code abs(this)} and {@code abs(val)}.  Returns 0 if
     * {@code this==0 && val==0}.
     *
     * @param  val value with which the GCD is to be computed.
     * @return {@code GCD(abs(this), abs(val))}
     */
    public BigInteger gcd(BigInteger val) {
        if (val.signum == 0)
            return this.abs();
        else if (this.signum == 0)
            return val.abs();

        MutableBigInteger a = new MutableBigInteger(this);
        MutableBigInteger b = new MutableBigInteger(val);

        MutableBigInteger result = a.hybridGCD(b);

        return result.toBigInteger(1);
    }

    /**
     * Package private method to return bit length for an integer.
     */
    static int bitLengthForInt(int n) {
        return 32 - Integer.numberOfLeadingZeros(n);
    }

    /**
     * Left shift int array a up to len by n bits. Returns the array that
     * results from the shift since space may have to be reallocated.
     */
    private static int[] leftShift(int[] a, int len, int n) {
        int nInts = n >>> 5;
        int nBits = n&0x1F;
        int bitsInHighWord = bitLengthForInt(a[0]);

        // If shift can be done without recopy, do so
        if (n <= (32-bitsInHighWord)) {
            primitiveLeftShift(a, len, nBits);
            return a;
        } else { // Array must be resized
            if (nBits <= (32-bitsInHighWord)) {
                int result[] = new int[nInts+len];
                for (int i=0; i<len; i++)
                    result[i] = a[i];
                primitiveLeftShift(result, result.length, nBits);
                return result;
            } else {
                int result[] = new int[nInts+len+1];
                for (int i=0; i<len; i++)
                    result[i] = a[i];
                primitiveRightShift(result, result.length, 32 - nBits);
                return result;
            }
        }
    }

    // shifts a up to len right n bits assumes no leading zeros, 0<n<32
    static void primitiveRightShift(int[] a, int len, int n) {
        int n2 = 32 - n;
        for (int i=len-1, c=a[i]; i>0; i--) {
            int b = c;
            c = a[i-1];
            a[i] = (c << n2) | (b >>> n);
        }
        a[0] >>>= n;
    }

    // shifts a up to len left n bits assumes no leading zeros, 0<=n<32
    static void primitiveLeftShift(int[] a, int len, int n) {
        if (len == 0 || n == 0)
            return;

        int n2 = 32 - n;
        for (int i=0, c=a[i], m=i+len-1; i<m; i++) {
            int b = c;
            c = a[i+1];
            a[i] = (b << n) | (c >>> n2);
        }
        a[len-1] <<= n;
    }

    /**
     * Calculate bitlength of contents of the first len elements an int array,
     * assuming there are no leading zero ints.
     */
    private static int bitLength(int[] val, int len) {
        if (len == 0)
            return 0;
        return ((len - 1) << 5) + bitLengthForInt(val[0]);
    }

    /**
     * Returns a BigInteger whose value is the absolute value of this
     * BigInteger.
     *
     * @return {@code abs(this)}
     */
    public BigInteger abs() {
        return (signum >= 0 ? this : this.negate());
    }

    /**
     * Returns a BigInteger whose value is {@code (-this)}.
     *
     * @return {@code -this}
     */
    public BigInteger negate() {
        return new BigInteger(this.mag, -this.signum);
    }

    /**
     * Returns the signum function of this BigInteger.
     *
     * @return -1, 0 or 1 as the value of this BigInteger is negative, zero or
     *         positive.
     */
    public int signum() {
        return this.signum;
    }

    // Modular Arithmetic Operations

    /**
     * Returns a BigInteger whose value is {@code (this mod m}).  This method
     * differs from {@code remainder} in that it always returns a
     * <i>non-negative</i> BigInteger.
     *
     * @param  m the modulus.
     * @return {@code this mod m}
     * @throws ArithmeticException {@code m} &le; 0
     * @see    #remainder
     */
    public BigInteger mod(BigInteger m) {
        if (m.signum <= 0)
            throw new ArithmeticException("BigInteger: modulus not positive");

        BigInteger result = this.remainder(m);
        return (result.signum >= 0 ? result : result.add(m));
    }

    /**
     * Returns a BigInteger whose value is
     * <tt>(this<sup>exponent</sup> mod m)</tt>.  (Unlike {@code pow}, this
     * method permits negative exponents.)
     *
     * @param  exponent the exponent.
     * @param  m the modulus.
     * @return <tt>this<sup>exponent</sup> mod m</tt>
     * @throws ArithmeticException {@code m} &le; 0 or the exponent is
     *         negative and this BigInteger is not <i>relatively
     *         prime</i> to {@code m}.
     * @see    #modInverse
     */
    public BigInteger modPow(BigInteger exponent, BigInteger m) {
        if (m.signum <= 0)
            throw new ArithmeticException("BigInteger: modulus not positive");

        // Trivial cases
        if (exponent.signum == 0)
            return (m.equals(ONE) ? ZERO : ONE);

        if (this.equals(ONE))
            return (m.equals(ONE) ? ZERO : ONE);

        if (this.equals(ZERO) && exponent.signum >= 0)
            return ZERO;

        if (this.equals(negConst[1]) && (!exponent.testBit(0)))
            return (m.equals(ONE) ? ZERO : ONE);

        boolean invertResult;
        if ((invertResult = (exponent.signum < 0)))
            exponent = exponent.negate();

        BigInteger base = (this.signum < 0 || this.compareTo(m) >= 0
                           ? this.mod(m) : this);
        BigInteger result;
        if (m.testBit(0)) { // odd modulus
            result = base.oddModPow(exponent, m);
        } else {
            /*
             * Even modulus.  Tear it into an "odd part" (m1) and power of two
             * (m2), exponentiate mod m1, manually exponentiate mod m2, and
             * use Chinese Remainder Theorem to combine results.
             */

            // Tear m apart into odd part (m1) and power of 2 (m2)
            int p = m.getLowestSetBit();   // Max pow of 2 that divides m

            BigInteger m1 = m.shiftRight(p);  // m/2**p
            BigInteger m2 = ONE.shiftLeft(p); // 2**p

            // Calculate new base from m1
            BigInteger base2 = (this.signum < 0 || this.compareTo(m1) >= 0
                                ? this.mod(m1) : this);

            // Caculate (base ** exponent) mod m1.
            BigInteger a1 = (m1.equals(ONE) ? ZERO :
                             base2.oddModPow(exponent, m1));

            // Calculate (this ** exponent) mod m2
            BigInteger a2 = base.modPow2(exponent, p);

            // Combine results using Chinese Remainder Theorem
            BigInteger y1 = m2.modInverse(m1);
            BigInteger y2 = m1.modInverse(m2);

            result = a1.multiply(m2).multiply(y1).add
                     (a2.multiply(m1).multiply(y2)).mod(m);
        }

        return (invertResult ? result.modInverse(m) : result);
    }

    static int[] bnExpModThreshTable = {7, 25, 81, 241, 673, 1793,
                                                Integer.MAX_VALUE}; // Sentinel

    /**
     * Returns a BigInteger whose value is x to the power of y mod z.
     * Assumes: z is odd && x < z.
     */
    private BigInteger oddModPow(BigInteger y, BigInteger z) {
    /*
     * The algorithm is adapted from Colin Plumb's C library.
     *
     * The window algorithm:
     * The idea is to keep a running product of b1 = n^(high-order bits of exp)
     * and then keep appending exponent bits to it.  The following patterns
     * apply to a 3-bit window (k = 3):
     * To append   0: square
     * To append   1: square, multiply by n^1
     * To append  10: square, multiply by n^1, square
     * To append  11: square, square, multiply by n^3
     * To append 100: square, multiply by n^1, square, square
     * To append 101: square, square, square, multiply by n^5
     * To append 110: square, square, multiply by n^3, square
     * To append 111: square, square, square, multiply by n^7
     *
     * Since each pattern involves only one multiply, the longer the pattern
     * the better, except that a 0 (no multiplies) can be appended directly.
     * We precompute a table of odd powers of n, up to 2^k, and can then
     * multiply k bits of exponent at a time.  Actually, assuming random
     * exponents, there is on average one zero bit between needs to
     * multiply (1/2 of the time there's none, 1/4 of the time there's 1,
     * 1/8 of the time, there's 2, 1/32 of the time, there's 3, etc.), so
     * you have to do one multiply per k+1 bits of exponent.
     *
     * The loop walks down the exponent, squaring the result buffer as
     * it goes.  There is a wbits+1 bit lookahead buffer, buf, that is
     * filled with the upcoming exponent bits.  (What is read after the
     * end of the exponent is unimportant, but it is filled with zero here.)
     * When the most-significant bit of this buffer becomes set, i.e.
     * (buf & tblmask) != 0, we have to decide what pattern to multiply
     * by, and when to do it.  We decide, remember to do it in future
     * after a suitable number of squarings have passed (e.g. a pattern
     * of "100" in the buffer requires that we multiply by n^1 immediately;
     * a pattern of "110" calls for multiplying by n^3 after one more
     * squaring), clear the buffer, and continue.
     *
     * When we start, there is one more optimization: the result buffer
     * is implcitly one, so squaring it or multiplying by it can be
     * optimized away.  Further, if we start with a pattern like "100"
     * in the lookahead window, rather than placing n into the buffer
     * and then starting to square it, we have already computed n^2
     * to compute the odd-powers table, so we can place that into
     * the buffer and save a squaring.
     *
     * This means that if you have a k-bit window, to compute n^z,
     * where z is the high k bits of the exponent, 1/2 of the time
     * it requires no squarings.  1/4 of the time, it requires 1
     * squaring, ... 1/2^(k-1) of the time, it reqires k-2 squarings.
     * And the remaining 1/2^(k-1) of the time, the top k bits are a
     * 1 followed by k-1 0 bits, so it again only requires k-2
     * squarings, not k-1.  The average of these is 1.  Add that
     * to the one squaring we have to do to compute the table,
     * and you'll see that a k-bit window saves k-2 squarings
     * as well as reducing the multiplies.  (It actually doesn't
     * hurt in the case k = 1, either.)
     */
        // Special case for exponent of one
        if (y.equals(ONE))
            return this;

        // Special case for base of zero
        if (signum==0)
            return ZERO;

        int[] base = mag.clone();
        int[] exp = y.mag;
        int[] mod = z.mag;
        int modLen = mod.length;

        // Select an appropriate window size
        int wbits = 0;
        int ebits = bitLength(exp, exp.length);
        // if exponent is 65537 (0x10001), use minimum window size
        if ((ebits != 17) || (exp[0] != 65537)) {
            while (ebits > bnExpModThreshTable[wbits]) {
                wbits++;
            }
        }

        // Calculate appropriate table size
        int tblmask = 1 << wbits;

        // Allocate table for precomputed odd powers of base in Montgomery form
        int[][] table = new int[tblmask][];
        for (int i=0; i<tblmask; i++)
            table[i] = new int[modLen];

        // Compute the modular inverse
        int inv = -MutableBigInteger.inverseMod32(mod[modLen-1]);

        // Convert base to Montgomery form
        int[] a = leftShift(base, base.length, modLen << 5);

        MutableBigInteger q = new MutableBigInteger(),
                          a2 = new MutableBigInteger(a),
                          b2 = new MutableBigInteger(mod);

        MutableBigInteger r= a2.divide(b2, q);
        table[0] = r.toIntArray();

        // Pad table[0] with leading zeros so its length is at least modLen
        if (table[0].length < modLen) {
           int offset = modLen - table[0].length;
           int[] t2 = new int[modLen];
           for (int i=0; i<table[0].length; i++)
               t2[i+offset] = table[0][i];
           table[0] = t2;
        }

        // Set b to the square of the base
        int[] b = squareToLen(table[0], modLen, null);
        b = montReduce(b, mod, modLen, inv);

        // Set t to high half of b
        int[] t = new int[modLen];
        for(int i=0; i<modLen; i++)
            t[i] = b[i];

        // Fill in the table with odd powers of the base
        for (int i=1; i<tblmask; i++) {
            int[] prod = multiplyToLen(t, modLen, table[i-1], modLen, null);
            table[i] = montReduce(prod, mod, modLen, inv);
        }

        // Pre load the window that slides over the exponent
        int bitpos = 1 << ((ebits-1) & (32-1));

        int buf = 0;
        int elen = exp.length;
        int eIndex = 0;
        for (int i = 0; i <= wbits; i++) {
            buf = (buf << 1) | (((exp[eIndex] & bitpos) != 0)?1:0);
            bitpos >>>= 1;
            if (bitpos == 0) {
                eIndex++;
                bitpos = 1 << (32-1);
                elen--;
            }
        }

        int multpos = ebits;

        // The first iteration, which is hoisted out of the main loop
        ebits--;
        boolean isone = true;

        multpos = ebits - wbits;
        while ((buf & 1) == 0) {
            buf >>>= 1;
            multpos++;
        }

        int[] mult = table[buf >>> 1];

        buf = 0;
        if (multpos == ebits)
            isone = false;

        // The main loop
        while(true) {
            ebits--;
            // Advance the window
            buf <<= 1;

            if (elen != 0) {
                buf |= ((exp[eIndex] & bitpos) != 0) ? 1 : 0;
                bitpos >>>= 1;
                if (bitpos == 0) {
                    eIndex++;
                    bitpos = 1 << (32-1);
                    elen--;
                }
            }

            // Examine the window for pending multiplies
            if ((buf & tblmask) != 0) {
                multpos = ebits - wbits;
                while ((buf & 1) == 0) {
                    buf >>>= 1;
                    multpos++;
                }
                mult = table[buf >>> 1];
                buf = 0;
            }

            // Perform multiply
            if (ebits == multpos) {
                if (isone) {
                    b = mult.clone();
                    isone = false;
                } else {
                    t = b;
                    a = multiplyToLen(t, modLen, mult, modLen, a);
                    a = montReduce(a, mod, modLen, inv);
                    t = a; a = b; b = t;
                }
            }

            // Check if done
            if (ebits == 0)
                break;

            // Square the input
            if (!isone) {
                t = b;
                a = squareToLen(t, modLen, a);
                a = montReduce(a, mod, modLen, inv);
                t = a; a = b; b = t;
            }
        }

        // Convert result out of Montgomery form and return
        int[] t2 = new int[2*modLen];
        for(int i=0; i<modLen; i++)
            t2[i+modLen] = b[i];

        b = montReduce(t2, mod, modLen, inv);

        t2 = new int[modLen];
        for(int i=0; i<modLen; i++)
            t2[i] = b[i];

        return new BigInteger(1, t2);
    }

    /**
     * Montgomery reduce n, modulo mod.  This reduces modulo mod and divides
     * by 2^(32*mlen). Adapted from Colin Plumb's C library.
     */
    private static int[] montReduce(int[] n, int[] mod, int mlen, int inv) {
        int c=0;
        int len = mlen;
        int offset=0;

        do {
            int nEnd = n[n.length-1-offset];
            int carry = mulAdd(n, mod, offset, mlen, inv * nEnd);
            c += addOne(n, offset, mlen, carry);
            offset++;
        } while(--len > 0);

        while(c>0)
            c += subN(n, mod, mlen);

        while (intArrayCmpToLen(n, mod, mlen) >= 0)
            subN(n, mod, mlen);

        return n;
    }


    /*
     * Returns -1, 0 or +1 as big-endian unsigned int array arg1 is less than,
     * equal to, or greater than arg2 up to length len.
     */
    private static int intArrayCmpToLen(int[] arg1, int[] arg2, int len) {
        for (int i=0; i<len; i++) {
            long b1 = arg1[i] & LONG_MASK;
            long b2 = arg2[i] & LONG_MASK;
            if (b1 < b2)
                return -1;
            if (b1 > b2)
                return 1;
        }
        return 0;
    }

    /**
     * Subtracts two numbers of same length, returning borrow.
     */
    private static int subN(int[] a, int[] b, int len) {
        long sum = 0;

        while(--len >= 0) {
            sum = (a[len] & LONG_MASK) -
                 (b[len] & LONG_MASK) + (sum >> 32);
            a[len] = (int)sum;
        }

        return (int)(sum >> 32);
    }

    /**
     * Multiply an array by one word k and add to result, return the carry
     */
    static int mulAdd(int[] out, int[] in, int offset, int len, int k) {
        long kLong = k & LONG_MASK;
        long carry = 0;

        offset = out.length-offset - 1;
        for (int j=len-1; j >= 0; j--) {
            long product = (in[j] & LONG_MASK) * kLong +
                           (out[offset] & LONG_MASK) + carry;
            out[offset--] = (int)product;
            carry = product >>> 32;
        }
        return (int)carry;
    }

    /**
     * Add one word to the number a mlen words into a. Return the resulting
     * carry.
     */
    static int addOne(int[] a, int offset, int mlen, int carry) {
        offset = a.length-1-mlen-offset;
        long t = (a[offset] & LONG_MASK) + (carry & LONG_MASK);

        a[offset] = (int)t;
        if ((t >>> 32) == 0)
            return 0;
        while (--mlen >= 0) {
            if (--offset < 0) { // Carry out of number
                return 1;
            } else {
                a[offset]++;
                if (a[offset] != 0)
                    return 0;
            }
        }
        return 1;
    }

    /**
     * Returns a BigInteger whose value is (this ** exponent) mod (2**p)
     */
    private BigInteger modPow2(BigInteger exponent, int p) {
        /*
         * Perform exponentiation using repeated squaring trick, chopping off
         * high order bits as indicated by modulus.
         */
        BigInteger result = valueOf(1);
        BigInteger baseToPow2 = this.mod2(p);
        int expOffset = 0;

        int limit = exponent.bitLength();

        if (this.testBit(0))
           limit = (p-1) < limit ? (p-1) : limit;

        while (expOffset < limit) {
            if (exponent.testBit(expOffset))
                result = result.multiply(baseToPow2).mod2(p);
            expOffset++;
            if (expOffset < limit)
                baseToPow2 = baseToPow2.square().mod2(p);
        }

        return result;
    }

    /**
     * Returns a BigInteger whose value is this mod(2**p).
     * Assumes that this {@code BigInteger >= 0} and {@code p > 0}.
     */
    private BigInteger mod2(int p) {
        if (bitLength() <= p)
            return this;

        // Copy remaining ints of mag
        int numInts = (p + 31) >>> 5;
        int[] mag = new int[numInts];
        for (int i=0; i<numInts; i++)
            mag[i] = this.mag[i + (this.mag.length - numInts)];

        // Mask out any excess bits
        int excessBits = (numInts << 5) - p;
        mag[0] &= (1L << (32-excessBits)) - 1;

        return (mag[0]==0 ? new BigInteger(1, mag) : new BigInteger(mag, 1));
    }

    /**
     * Returns a BigInteger whose value is {@code (this}<sup>-1</sup> {@code mod m)}.
     *
     * @param  m the modulus.
     * @return {@code this}<sup>-1</sup> {@code mod m}.
     * @throws ArithmeticException {@code  m} &le; 0, or this BigInteger
     *         has no multiplicative inverse mod m (that is, this BigInteger
     *         is not <i>relatively prime</i> to m).
     */
    public BigInteger modInverse(BigInteger m) {
        if (m.signum != 1)
            throw new ArithmeticException("BigInteger: modulus not positive");

        if (m.equals(ONE))
            return ZERO;

        // Calculate (this mod m)
        BigInteger modVal = this;
        if (signum < 0 || (this.compareMagnitude(m) >= 0))
            modVal = this.mod(m);

        if (modVal.equals(ONE))
            return ONE;

        MutableBigInteger a = new MutableBigInteger(modVal);
        MutableBigInteger b = new MutableBigInteger(m);

        MutableBigInteger result = a.mutableModInverse(b);
        return result.toBigInteger(1);
    }

    // Shift Operations

    /**
     * Returns a BigInteger whose value is {@code (this << n)}.
     * The shift distance, {@code n}, may be negative, in which case
     * this method performs a right shift.
     * (Computes <tt>floor(this * 2<sup>n</sup>)</tt>.)
     *
     * @param  n shift distance, in bits.
     * @return {@code this << n}
     * @throws ArithmeticException if the shift distance is {@code
     *         Integer.MIN_VALUE}.
     * @see #shiftRight
     */
    public BigInteger shiftLeft(int n) {
        if (signum == 0)
            return ZERO;
        if (n==0)
            return this;
        if (n<0) {
            if (n == Integer.MIN_VALUE) {
                throw new ArithmeticException("Shift distance of Integer.MIN_VALUE not supported.");
            } else {
                return shiftRight(-n);
            }
        }

        int nInts = n >>> 5;
        int nBits = n & 0x1f;
        int magLen = mag.length;
        int newMag[] = null;

        if (nBits == 0) {
            newMag = new int[magLen + nInts];
            for (int i=0; i<magLen; i++)
                newMag[i] = mag[i];
        } else {
            int i = 0;
            int nBits2 = 32 - nBits;
            int highBits = mag[0] >>> nBits2;
            if (highBits != 0) {
                newMag = new int[magLen + nInts + 1];
                newMag[i++] = highBits;
            } else {
                newMag = new int[magLen + nInts];
            }
            int j=0;
            while (j < magLen-1)
                newMag[i++] = mag[j++] << nBits | mag[j] >>> nBits2;
            newMag[i] = mag[j] << nBits;
        }

        return new BigInteger(newMag, signum);
    }

    /**
     * Returns a BigInteger whose value is {@code (this >> n)}.  Sign
     * extension is performed.  The shift distance, {@code n}, may be
     * negative, in which case this method performs a left shift.
     * (Computes <tt>floor(this / 2<sup>n</sup>)</tt>.)
     *
     * @param  n shift distance, in bits.
     * @return {@code this >> n}
     * @throws ArithmeticException if the shift distance is {@code
     *         Integer.MIN_VALUE}.
     * @see #shiftLeft
     */
    public BigInteger shiftRight(int n) {
        if (n==0)
            return this;
        if (n<0) {
            if (n == Integer.MIN_VALUE) {
                throw new ArithmeticException("Shift distance of Integer.MIN_VALUE not supported.");
            } else {
                return shiftLeft(-n);
            }
        }

        int nInts = n >>> 5;
        int nBits = n & 0x1f;
        int magLen = mag.length;
        int newMag[] = null;

        // Special case: entire contents shifted off the end
        if (nInts >= magLen)
            return (signum >= 0 ? ZERO : negConst[1]);

        if (nBits == 0) {
            int newMagLen = magLen - nInts;
            newMag = new int[newMagLen];
            for (int i=0; i<newMagLen; i++)
                newMag[i] = mag[i];
        } else {
            int i = 0;
            int highBits = mag[0] >>> nBits;
            if (highBits != 0) {
                newMag = new int[magLen - nInts];
                newMag[i++] = highBits;
            } else {
                newMag = new int[magLen - nInts -1];
            }

            int nBits2 = 32 - nBits;
            int j=0;
            while (j < magLen - nInts - 1)
                newMag[i++] = (mag[j++] << nBits2) | (mag[j] >>> nBits);
        }

        if (signum < 0) {
            // Find out whether any one-bits were shifted off the end.
            boolean onesLost = false;
            for (int i=magLen-1, j=magLen-nInts; i>=j && !onesLost; i--)
                onesLost = (mag[i] != 0);
            if (!onesLost && nBits != 0)
                onesLost = (mag[magLen - nInts - 1] << (32 - nBits) != 0);

            if (onesLost)
                newMag = javaIncrement(newMag);
        }

        return new BigInteger(newMag, signum);
    }

    int[] javaIncrement(int[] val) {
        int lastSum = 0;
        for (int i=val.length-1;  i >= 0 && lastSum == 0; i--)
            lastSum = (val[i] += 1);
        if (lastSum == 0) {
            val = new int[val.length+1];
            val[0] = 1;
        }
        return val;
    }

    // Bitwise Operations

    /**
     * Returns a BigInteger whose value is {@code (this & val)}.  (This
     * method returns a negative BigInteger if and only if this and val are
     * both negative.)
     *
     * @param val value to be AND'ed with this BigInteger.
     * @return {@code this & val}
     */
    public BigInteger and(BigInteger val) {
        int[] result = new int[Math.max(intLength(), val.intLength())];
        for (int i=0; i<result.length; i++)
            result[i] = (getInt(result.length-i-1)
                         & val.getInt(result.length-i-1));

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is {@code (this | val)}.  (This method
     * returns a negative BigInteger if and only if either this or val is
     * negative.)
     *
     * @param val value to be OR'ed with this BigInteger.
     * @return {@code this | val}
     */
    public BigInteger or(BigInteger val) {
        int[] result = new int[Math.max(intLength(), val.intLength())];
        for (int i=0; i<result.length; i++)
            result[i] = (getInt(result.length-i-1)
                         | val.getInt(result.length-i-1));

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is {@code (this ^ val)}.  (This method
     * returns a negative BigInteger if and only if exactly one of this and
     * val are negative.)
     *
     * @param val value to be XOR'ed with this BigInteger.
     * @return {@code this ^ val}
     */
    public BigInteger xor(BigInteger val) {
        int[] result = new int[Math.max(intLength(), val.intLength())];
        for (int i=0; i<result.length; i++)
            result[i] = (getInt(result.length-i-1)
                         ^ val.getInt(result.length-i-1));

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is {@code (~this)}.  (This method
     * returns a negative value if and only if this BigInteger is
     * non-negative.)
     *
     * @return {@code ~this}
     */
    public BigInteger not() {
        int[] result = new int[intLength()];
        for (int i=0; i<result.length; i++)
            result[i] = ~getInt(result.length-i-1);

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is {@code (this & ~val)}.  This
     * method, which is equivalent to {@code and(val.not())}, is provided as
     * a convenience for masking operations.  (This method returns a negative
     * BigInteger if and only if {@code this} is negative and {@code val} is
     * positive.)
     *
     * @param val value to be complemented and AND'ed with this BigInteger.
     * @return {@code this & ~val}
     */
    public BigInteger andNot(BigInteger val) {
        int[] result = new int[Math.max(intLength(), val.intLength())];
        for (int i=0; i<result.length; i++)
            result[i] = (getInt(result.length-i-1)
                         & ~val.getInt(result.length-i-1));

        return valueOf(result);
    }


    // Single Bit Operations

    /**
     * Returns {@code true} if and only if the designated bit is set.
     * (Computes {@code ((this & (1<<n)) != 0)}.)
     *
     * @param  n index of bit to test.
     * @return {@code true} if and only if the designated bit is set.
     * @throws ArithmeticException {@code n} is negative.
     */
    public boolean testBit(int n) {
        if (n<0)
            throw new ArithmeticException("Negative bit address");

        return (getInt(n >>> 5) & (1 << (n & 31))) != 0;
    }

    /**
     * Returns a BigInteger whose value is equivalent to this BigInteger
     * with the designated bit set.  (Computes {@code (this | (1<<n))}.)
     *
     * @param  n index of bit to set.
     * @return {@code this | (1<<n)}
     * @throws ArithmeticException {@code n} is negative.
     */
    public BigInteger setBit(int n) {
        if (n<0)
            throw new ArithmeticException("Negative bit address");

        int intNum = n >>> 5;
        int[] result = new int[Math.max(intLength(), intNum+2)];

        for (int i=0; i<result.length; i++)
            result[result.length-i-1] = getInt(i);

        result[result.length-intNum-1] |= (1 << (n & 31));

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is equivalent to this BigInteger
     * with the designated bit cleared.
     * (Computes {@code (this & ~(1<<n))}.)
     *
     * @param  n index of bit to clear.
     * @return {@code this & ~(1<<n)}
     * @throws ArithmeticException {@code n} is negative.
     */
    public BigInteger clearBit(int n) {
        if (n<0)
            throw new ArithmeticException("Negative bit address");

        int intNum = n >>> 5;
        int[] result = new int[Math.max(intLength(), ((n + 1) >>> 5) + 1)];

        for (int i=0; i<result.length; i++)
            result[result.length-i-1] = getInt(i);

        result[result.length-intNum-1] &= ~(1 << (n & 31));

        return valueOf(result);
    }

    /**
     * Returns a BigInteger whose value is equivalent to this BigInteger
     * with the designated bit flipped.
     * (Computes {@code (this ^ (1<<n))}.)
     *
     * @param  n index of bit to flip.
     * @return {@code this ^ (1<<n)}
     * @throws ArithmeticException {@code n} is negative.
     */
    public BigInteger flipBit(int n) {
        if (n<0)
            throw new ArithmeticException("Negative bit address");

        int intNum = n >>> 5;
        int[] result = new int[Math.max(intLength(), intNum+2)];

        for (int i=0; i<result.length; i++)
            result[result.length-i-1] = getInt(i);

        result[result.length-intNum-1] ^= (1 << (n & 31));

        return valueOf(result);
    }

    /**
     * Returns the index of the rightmost (lowest-order) one bit in this
     * BigInteger (the number of zero bits to the right of the rightmost
     * one bit).  Returns -1 if this BigInteger contains no one bits.
     * (Computes {@code (this==0? -1 : log2(this & -this))}.)
     *
     * @return index of the rightmost one bit in this BigInteger.
     */
    public int getLowestSetBit() {
        @SuppressWarnings("deprecation") int lsb = lowestSetBit - 2;
        if (lsb == -2) {  // lowestSetBit not initialized yet
            lsb = 0;
            if (signum == 0) {
                lsb -= 1;
            } else {
                // Search for lowest order nonzero int
                int i,b;
                for (i=0; (b = getInt(i))==0; i++)
                    ;
                lsb += (i << 5) + Integer.numberOfTrailingZeros(b);
            }
            lowestSetBit = lsb + 2;
        }
        return lsb;
    }


    // Miscellaneous Bit Operations

    /**
     * Returns the number of bits in the minimal two's-complement
     * representation of this BigInteger, <i>excluding</i> a sign bit.
     * For positive BigIntegers, this is equivalent to the number of bits in
     * the ordinary binary representation.  (Computes
     * {@code (ceil(log2(this < 0 ? -this : this+1)))}.)
     *
     * @return number of bits in the minimal two's-complement
     *         representation of this BigInteger, <i>excluding</i> a sign bit.
     */
    public int bitLength() {
        @SuppressWarnings("deprecation") int n = bitLength - 1;
        if (n == -1) { // bitLength not initialized yet
            int[] m = mag;
            int len = m.length;
            if (len == 0) {
                n = 0; // offset by one to initialize
            }  else {
                // Calculate the bit length of the magnitude
                int magBitLength = ((len - 1) << 5) + bitLengthForInt(mag[0]);
                 if (signum < 0) {
                     // Check if magnitude is a power of two
                     boolean pow2 = (Integer.bitCount(mag[0]) == 1);
                     for(int i=1; i< len && pow2; i++)
                         pow2 = (mag[i] == 0);

                     n = (pow2 ? magBitLength -1 : magBitLength);
                 } else {
                     n = magBitLength;
                 }
            }
            bitLength = n + 1;
        }
        return n;
    }

    /**
     * Returns the number of bits in the two's complement representation
     * of this BigInteger that differ from its sign bit.  This method is
     * useful when implementing bit-vector style sets atop BigIntegers.
     *
     * @return number of bits in the two's complement representation
     *         of this BigInteger that differ from its sign bit.
     */
    public int bitCount() {
        @SuppressWarnings("deprecation") int bc = bitCount - 1;
        if (bc == -1) {  // bitCount not initialized yet
            bc = 0;      // offset by one to initialize
            // Count the bits in the magnitude
            for (int i=0; i<mag.length; i++)
                bc += Integer.bitCount(mag[i]);
            if (signum < 0) {
                // Count the trailing zeros in the magnitude
                int magTrailingZeroCount = 0, j;
                for (j=mag.length-1; mag[j]==0; j--)
                    magTrailingZeroCount += 32;
                magTrailingZeroCount += Integer.numberOfTrailingZeros(mag[j]);
                bc += magTrailingZeroCount - 1;
            }
            bitCount = bc + 1;
        }
        return bc;
    }

    // Primality Testing

    /**
     * Returns {@code true} if this BigInteger is probably prime,
     * {@code false} if it's definitely composite.  If
     * {@code certainty} is &le; 0, {@code true} is
     * returned.
     *
     * @param  certainty a measure of the uncertainty that the caller is
     *         willing to tolerate: if the call returns {@code true}
     *         the probability that this BigInteger is prime exceeds
     *         (1 - 1/2<sup>{@code certainty}</sup>).  The execution time of
     *         this method is proportional to the value of this parameter.
     * @return {@code true} if this BigInteger is probably prime,
     *         {@code false} if it's definitely composite.
     */
    public boolean isProbablePrime(int certainty) {
        if (certainty <= 0)
            return true;
        BigInteger w = this.abs();
        if (w.equals(TWO))
            return true;
        if (!w.testBit(0) || w.equals(ONE))
            return false;

        return w.primeToCertainty(certainty, null);
    }

    // Comparison Operations

    /**
     * Compares this BigInteger with the specified BigInteger.  This
     * method is provided in preference to individual methods for each
     * of the six boolean comparison operators ({@literal <}, ==,
     * {@literal >}, {@literal >=}, !=, {@literal <=}).  The suggested
     * idiom for performing these comparisons is: {@code
     * (x.compareTo(y)} &lt;<i>op</i>&gt; {@code 0)}, where
     * &lt;<i>op</i>&gt; is one of the six comparison operators.
     *
     * @param  val BigInteger to which this BigInteger is to be compared.
     * @return -1, 0 or 1 as this BigInteger is numerically less than, equal
     *         to, or greater than {@code val}.
     */
    public int compareTo(BigInteger val) {
        if (signum == val.signum) {
            switch (signum) {
            case 1:
                return compareMagnitude(val);
            case -1:
                return val.compareMagnitude(this);
            default:
                return 0;
            }
        }
        return signum > val.signum ? 1 : -1;
    }

    /**
     * Compares the magnitude array of this BigInteger with the specified
     * BigInteger's. This is the version of compareTo ignoring sign.
     *
     * @param val BigInteger whose magnitude array to be compared.
     * @return -1, 0 or 1 as this magnitude array is less than, equal to or
     *         greater than the magnitude aray for the specified BigInteger's.
     */
    final int compareMagnitude(BigInteger val) {
        int[] m1 = mag;
        int len1 = m1.length;
        int[] m2 = val.mag;
        int len2 = m2.length;
        if (len1 < len2)
            return -1;
        if (len1 > len2)
            return 1;
        for (int i = 0; i < len1; i++) {
            int a = m1[i];
            int b = m2[i];
            if (a != b)
                return ((a & LONG_MASK) < (b & LONG_MASK)) ? -1 : 1;
        }
        return 0;
    }

    /**
     * Compares this BigInteger with the specified Object for equality.
     *
     * @param  x Object to which this BigInteger is to be compared.
     * @return {@code true} if and only if the specified Object is a
     *         BigInteger whose value is numerically equal to this BigInteger.
     */
    public boolean equals(Object x) {
        // This test is just an optimization, which may or may not help
        if (x == this)
            return true;

        if (!(x instanceof BigInteger))
            return false;

        BigInteger xInt = (BigInteger) x;
        if (xInt.signum != signum)
            return false;

        int[] m = mag;
        int len = m.length;
        int[] xm = xInt.mag;
        if (len != xm.length)
            return false;

        for (int i = 0; i < len; i++)
            if (xm[i] != m[i])
                return false;

        return true;
    }

    /**
     * Returns the minimum of this BigInteger and {@code val}.
     *
     * @param  val value with which the minimum is to be computed.
     * @return the BigInteger whose value is the lesser of this BigInteger and
     *         {@code val}.  If they are equal, either may be returned.
     */
    public BigInteger min(BigInteger val) {
        return (compareTo(val)<0 ? this : val);
    }

    /**
     * Returns the maximum of this BigInteger and {@code val}.
     *
     * @param  val value with which the maximum is to be computed.
     * @return the BigInteger whose value is the greater of this and
     *         {@code val}.  If they are equal, either may be returned.
     */
    public BigInteger max(BigInteger val) {
        return (compareTo(val)>0 ? this : val);
    }


    // Hash Function

    /**
     * Returns the hash code for this BigInteger.
     *
     * @return hash code for this BigInteger.
     */
    public int hashCode() {
        int hashCode = 0;

        for (int i=0; i<mag.length; i++)
            hashCode = (int)(31*hashCode + (mag[i] & LONG_MASK));

        return hashCode * signum;
    }

    /**
     * Returns the String representation of this BigInteger in the
     * given radix.  If the radix is outside the range from {@link
     * Character#MIN_RADIX} to {@link Character#MAX_RADIX} inclusive,
     * it will default to 10 (as is the case for
     * {@code Integer.toString}).  The digit-to-character mapping
     * provided by {@code Character.forDigit} is used, and a minus
     * sign is prepended if appropriate.  (This representation is
     * compatible with the {@link #BigInteger(String, int) (String,
     * int)} constructor.)
     *
     * @param  radix  radix of the String representation.
     * @return String representation of this BigInteger in the given radix.
     * @see    Integer#toString
     * @see    Character#forDigit
     * @see    #BigInteger(java.lang.String, int)
     */
    public String toString(int radix) {
        if (signum == 0)
            return "0";
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
            radix = 10;

        // Compute upper bound on number of digit groups and allocate space
        int maxNumDigitGroups = (4*mag.length + 6)/7;
        String digitGroup[] = new String[maxNumDigitGroups];

        // Translate number to string, a digit group at a time
        BigInteger tmp = this.abs();
        int numGroups = 0;
        while (tmp.signum != 0) {
            BigInteger d = longRadix[radix];

            MutableBigInteger q = new MutableBigInteger(),
                              a = new MutableBigInteger(tmp.mag),
                              b = new MutableBigInteger(d.mag);
            MutableBigInteger r = a.divide(b, q);
            BigInteger q2 = q.toBigInteger(tmp.signum * d.signum);
            BigInteger r2 = r.toBigInteger(tmp.signum * d.signum);

            digitGroup[numGroups++] = Long.toString(r2.longValue(), radix);
            tmp = q2;
        }

        // Put sign (if any) and first digit group into result buffer
        StringBuilder buf = new StringBuilder(numGroups*digitsPerLong[radix]+1);
        if (signum<0)
            buf.append('-');
        buf.append(digitGroup[numGroups-1]);

        // Append remaining digit groups padded with leading zeros
        for (int i=numGroups-2; i>=0; i--) {
            // Prepend (any) leading zeros for this digit group
            int numLeadingZeros = digitsPerLong[radix]-digitGroup[i].length();
            if (numLeadingZeros != 0)
                buf.append(zeros[numLeadingZeros]);
            buf.append(digitGroup[i]);
        }
        return buf.toString();
    }

    /* zero[i] is a string of i consecutive zeros. */
    private static String zeros[] = new String[64];
    static {
        zeros[63] =
            "000000000000000000000000000000000000000000000000000000000000000";
        for (int i=0; i<63; i++)
            zeros[i] = zeros[63].substring(0, i);
    }

    /**
     * Returns the decimal String representation of this BigInteger.
     * The digit-to-character mapping provided by
     * {@code Character.forDigit} is used, and a minus sign is
     * prepended if appropriate.  (This representation is compatible
     * with the {@link #BigInteger(String) (String)} constructor, and
     * allows for String concatenation with Java's + operator.)
     *
     * @return decimal String representation of this BigInteger.
     * @see    Character#forDigit
     * @see    #BigInteger(java.lang.String)
     */
    public String toString() {
        return toString(10);
    }

    /**
     * Returns a byte array containing the two's-complement
     * representation of this BigInteger.  The byte array will be in
     * <i>big-endian</i> byte-order: the most significant byte is in
     * the zeroth element.  The array will contain the minimum number
     * of bytes required to represent this BigInteger, including at
     * least one sign bit, which is {@code (ceil((this.bitLength() +
     * 1)/8))}.  (This representation is compatible with the
     * {@link #BigInteger(byte[]) (byte[])} constructor.)
     *
     * @return a byte array containing the two's-complement representation of
     *         this BigInteger.
     * @see    #BigInteger(byte[])
     */
    public byte[] toByteArray() {
        int byteLen = bitLength()/8 + 1;
        byte[] byteArray = new byte[byteLen];

        for (int i=byteLen-1, bytesCopied=4, nextInt=0, intIndex=0; i>=0; i--) {
            if (bytesCopied == 4) {
                nextInt = getInt(intIndex++);
                bytesCopied = 1;
            } else {
                nextInt >>>= 8;
                bytesCopied++;
            }
            byteArray[i] = (byte)nextInt;
        }
        return byteArray;
    }

    /**
     * Converts this BigInteger to an {@code int}.  This
     * conversion is analogous to a <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/conversions.doc.html#25363"><i>narrowing
     * primitive conversion</i></a> from {@code long} to
     * {@code int} as defined in the <a
     * href="http://java.sun.com/docs/books/jls/html/">Java Language
     * Specification</a>: if this BigInteger is too big to fit in an
     * {@code int}, only the low-order 32 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude of the BigInteger value as well as return a
     * result with the opposite sign.
     *
     * @return this BigInteger converted to an {@code int}.
     */
    public int intValue() {
        int result = 0;
        result = getInt(0);
        return result;
    }

    /**
     * Converts this BigInteger to a {@code long}.  This
     * conversion is analogous to a <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/conversions.doc.html#25363"><i>narrowing
     * primitive conversion</i></a> from {@code long} to
     * {@code int} as defined in the <a
     * href="http://java.sun.com/docs/books/jls/html/">Java Language
     * Specification</a>: if this BigInteger is too big to fit in a
     * {@code long}, only the low-order 64 bits are returned.
     * Note that this conversion can lose information about the
     * overall magnitude of the BigInteger value as well as return a
     * result with the opposite sign.
     *
     * @return this BigInteger converted to a {@code long}.
     */
    public long longValue() {
        long result = 0;

        for (int i=1; i>=0; i--)
            result = (result << 32) + (getInt(i) & LONG_MASK);
        return result;
    }

    /**
     * Converts this BigInteger to a {@code float}.  This
     * conversion is similar to the <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/conversions.doc.html#25363"><i>narrowing
     * primitive conversion</i></a> from {@code double} to
     * {@code float} defined in the <a
     * href="http://java.sun.com/docs/books/jls/html/">Java Language
     * Specification</a>: if this BigInteger has too great a magnitude
     * to represent as a {@code float}, it will be converted to
     * {@link Float#NEGATIVE_INFINITY} or {@link
     * Float#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the precision of the BigInteger value.
     *
     * @return this BigInteger converted to a {@code float}.
     */
    public float floatValue() {
        // Somewhat inefficient, but guaranteed to work.
        return Float.parseFloat(this.toString());
    }

    /**
     * Converts this BigInteger to a {@code double}.  This
     * conversion is similar to the <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/conversions.doc.html#25363"><i>narrowing
     * primitive conversion</i></a> from {@code double} to
     * {@code float} defined in the <a
     * href="http://java.sun.com/docs/books/jls/html/">Java Language
     * Specification</a>: if this BigInteger has too great a magnitude
     * to represent as a {@code double}, it will be converted to
     * {@link Double#NEGATIVE_INFINITY} or {@link
     * Double#POSITIVE_INFINITY} as appropriate.  Note that even when
     * the return value is finite, this conversion can lose
     * information about the precision of the BigInteger value.
     *
     * @return this BigInteger converted to a {@code double}.
     */
    public double doubleValue() {
        // Somewhat inefficient, but guaranteed to work.
        return Double.parseDouble(this.toString());
    }

    /**
     * Returns a copy of the input array stripped of any leading zero bytes.
     */
    private static int[] stripLeadingZeroInts(int val[]) {
        int vlen = val.length;
        int keep;

        // Find first nonzero byte
        for (keep = 0; keep < vlen && val[keep] == 0; keep++)
            ;
        return java.util.Arrays.copyOfRange(val, keep, vlen);
    }

    /**
     * Returns the input array stripped of any leading zero bytes.
     * Since the source is trusted the copying may be skipped.
     */
    private static int[] trustedStripLeadingZeroInts(int val[]) {
        int vlen = val.length;
        int keep;

        // Find first nonzero byte
        for (keep = 0; keep < vlen && val[keep] == 0; keep++)
            ;
        return keep == 0 ? val : java.util.Arrays.copyOfRange(val, keep, vlen);
    }

    /**
     * Returns a copy of the input array stripped of any leading zero bytes.
     */
    private static int[] stripLeadingZeroBytes(byte a[]) {
        int byteLength = a.length;
        int keep;

        // Find first nonzero byte
        for (keep = 0; keep < byteLength && a[keep]==0; keep++)
            ;

        // Allocate new array and copy relevant part of input array
        int intLength = ((byteLength - keep) + 3) >>> 2;
        int[] result = new int[intLength];
        int b = byteLength - 1;
        for (int i = intLength-1; i >= 0; i--) {
            result[i] = a[b--] & 0xff;
            int bytesRemaining = b - keep + 1;
            int bytesToTransfer = Math.min(3, bytesRemaining);
            for (int j=8; j <= (bytesToTransfer << 3); j += 8)
                result[i] |= ((a[b--] & 0xff) << j);
        }
        return result;
    }

    /**
     * Takes an array a representing a negative 2's-complement number and
     * returns the minimal (no leading zero bytes) unsigned whose value is -a.
     */
    private static int[] makePositive(byte a[]) {
        int keep, k;
        int byteLength = a.length;

        // Find first non-sign (0xff) byte of input
        for (keep=0; keep<byteLength && a[keep]==-1; keep++)
            ;


        /* Allocate output array.  If all non-sign bytes are 0x00, we must
         * allocate space for one extra output byte. */
        for (k=keep; k<byteLength && a[k]==0; k++)
            ;

        int extraByte = (k==byteLength) ? 1 : 0;
        int intLength = ((byteLength - keep + extraByte) + 3)/4;
        int result[] = new int[intLength];

        /* Copy one's complement of input into output, leaving extra
         * byte (if it exists) == 0x00 */
        int b = byteLength - 1;
        for (int i = intLength-1; i >= 0; i--) {
            result[i] = a[b--] & 0xff;
            int numBytesToTransfer = Math.min(3, b-keep+1);
            if (numBytesToTransfer < 0)
                numBytesToTransfer = 0;
            for (int j=8; j <= 8*numBytesToTransfer; j += 8)
                result[i] |= ((a[b--] & 0xff) << j);

            // Mask indicates which bits must be complemented
            int mask = -1 >>> (8*(3-numBytesToTransfer));
            result[i] = ~result[i] & mask;
        }

        // Add one to one's complement to generate two's complement
        for (int i=result.length-1; i>=0; i--) {
            result[i] = (int)((result[i] & LONG_MASK) + 1);
            if (result[i] != 0)
                break;
        }

        return result;
    }

    /**
     * Takes an array a representing a negative 2's-complement number and
     * returns the minimal (no leading zero ints) unsigned whose value is -a.
     */
    private static int[] makePositive(int a[]) {
        int keep, j;

        // Find first non-sign (0xffffffff) int of input
        for (keep=0; keep<a.length && a[keep]==-1; keep++)
            ;

        /* Allocate output array.  If all non-sign ints are 0x00, we must
         * allocate space for one extra output int. */
        for (j=keep; j<a.length && a[j]==0; j++)
            ;
        int extraInt = (j==a.length ? 1 : 0);
        int result[] = new int[a.length - keep + extraInt];

        /* Copy one's complement of input into output, leaving extra
         * int (if it exists) == 0x00 */
        for (int i = keep; i<a.length; i++)
            result[i - keep + extraInt] = ~a[i];

        // Add one to one's complement to generate two's complement
        for (int i=result.length-1; ++result[i]==0; i--)
            ;

        return result;
    }

    /*
     * The following two arrays are used for fast String conversions.  Both
     * are indexed by radix.  The first is the number of digits of the given
     * radix that can fit in a Java long without "going negative", i.e., the
     * highest integer n such that radix**n < 2**63.  The second is the
     * "long radix" that tears each number into "long digits", each of which
     * consists of the number of digits in the corresponding element in
     * digitsPerLong (longRadix[i] = i**digitPerLong[i]).  Both arrays have
     * nonsense values in their 0 and 1 elements, as radixes 0 and 1 are not
     * used.
     */
    private static int digitsPerLong[] = {0, 0,
        62, 39, 31, 27, 24, 22, 20, 19, 18, 18, 17, 17, 16, 16, 15, 15, 15, 14,
        14, 14, 14, 13, 13, 13, 13, 13, 13, 12, 12, 12, 12, 12, 12, 12, 12};

    private static BigInteger longRadix[] = {null, null,
        valueOf(0x4000000000000000L), valueOf(0x383d9170b85ff80bL),
        valueOf(0x4000000000000000L), valueOf(0x6765c793fa10079dL),
        valueOf(0x41c21cb8e1000000L), valueOf(0x3642798750226111L),
        valueOf(0x1000000000000000L), valueOf(0x12bf307ae81ffd59L),
        valueOf( 0xde0b6b3a7640000L), valueOf(0x4d28cb56c33fa539L),
        valueOf(0x1eca170c00000000L), valueOf(0x780c7372621bd74dL),
        valueOf(0x1e39a5057d810000L), valueOf(0x5b27ac993df97701L),
        valueOf(0x1000000000000000L), valueOf(0x27b95e997e21d9f1L),
        valueOf(0x5da0e1e53c5c8000L), valueOf( 0xb16a458ef403f19L),
        valueOf(0x16bcc41e90000000L), valueOf(0x2d04b7fdd9c0ef49L),
        valueOf(0x5658597bcaa24000L), valueOf( 0x6feb266931a75b7L),
        valueOf( 0xc29e98000000000L), valueOf(0x14adf4b7320334b9L),
        valueOf(0x226ed36478bfa000L), valueOf(0x383d9170b85ff80bL),
        valueOf(0x5a3c23e39c000000L), valueOf( 0x4e900abb53e6b71L),
        valueOf( 0x7600ec618141000L), valueOf( 0xaee5720ee830681L),
        valueOf(0x1000000000000000L), valueOf(0x172588ad4f5f0981L),
        valueOf(0x211e44f7d02c1000L), valueOf(0x2ee56725f06e5c71L),
        valueOf(0x41c21cb8e1000000L)};

    /*
     * These two arrays are the integer analogue of above.
     */
    private static int digitsPerInt[] = {0, 0, 30, 19, 15, 13, 11,
        11, 10, 9, 9, 8, 8, 8, 8, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 5};

    private static int intRadix[] = {0, 0,
        0x40000000, 0x4546b3db, 0x40000000, 0x48c27395, 0x159fd800,
        0x75db9c97, 0x40000000, 0x17179149, 0x3b9aca00, 0xcc6db61,
        0x19a10000, 0x309f1021, 0x57f6c100, 0xa2f1b6f,  0x10000000,
        0x18754571, 0x247dbc80, 0x3547667b, 0x4c4b4000, 0x6b5a6e1d,
        0x6c20a40,  0x8d2d931,  0xb640000,  0xe8d4a51,  0x1269ae40,
        0x17179149, 0x1cb91000, 0x23744899, 0x2b73a840, 0x34e63b41,
        0x40000000, 0x4cfa3cc1, 0x5c13d840, 0x6d91b519, 0x39aa400
    };

    /**
     * These routines provide access to the two's complement representation
     * of BigIntegers.
     */

    /**
     * Returns the length of the two's complement representation in ints,
     * including space for at least one sign bit.
     */
    private int intLength() {
        return (bitLength() >>> 5) + 1;
    }

    /* Returns sign bit */
    private int signBit() {
        return signum < 0 ? 1 : 0;
    }

    /* Returns an int of sign bits */
    private int signInt() {
        return signum < 0 ? -1 : 0;
    }

    /**
     * Returns the specified int of the little-endian two's complement
     * representation (int 0 is the least significant).  The int number can
     * be arbitrarily high (values are logically preceded by infinitely many
     * sign ints).
     */
    private int getInt(int n) {
        if (n < 0)
            return 0;
        if (n >= mag.length)
            return signInt();

        int magInt = mag[mag.length-n-1];

        return (signum >= 0 ? magInt :
                (n <= firstNonzeroIntNum() ? -magInt : ~magInt));
    }

    /**
     * Returns the index of the int that contains the first nonzero int in the
     * little-endian binary representation of the magnitude (int 0 is the
     * least significant). If the magnitude is zero, return value is undefined.
     */
     private int firstNonzeroIntNum() {
         int fn = firstNonzeroIntNum - 2;
         if (fn == -2) { // firstNonzeroIntNum not initialized yet
             fn = 0;

             // Search for the first nonzero int
             int i;
             int mlen = mag.length;
             for (i = mlen - 1; i >= 0 && mag[i] == 0; i--)
                 ;
             fn = mlen - i - 1;
             firstNonzeroIntNum = fn + 2; // offset by two to initialize
         }
         return fn;
     }

    /** use serialVersionUID from JDK 1.1. for interoperability */
    private static final long serialVersionUID = -8287574255936472291L;

    /**
     * Serializable fields for BigInteger.
     *
     * @serialField signum  int
     *              signum of this BigInteger.
     * @serialField magnitude int[]
     *              magnitude array of this BigInteger.
     * @serialField bitCount  int
     *              number of bits in this BigInteger
     * @serialField bitLength int
     *              the number of bits in the minimal two's-complement
     *              representation of this BigInteger
     * @serialField lowestSetBit int
     *              lowest set bit in the twos complement representation
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("signum", Integer.TYPE),
        new ObjectStreamField("magnitude", byte[].class),
        new ObjectStreamField("bitCount", Integer.TYPE),
        new ObjectStreamField("bitLength", Integer.TYPE),
        new ObjectStreamField("firstNonzeroByteNum", Integer.TYPE),
        new ObjectStreamField("lowestSetBit", Integer.TYPE)
        };

    /**
     * Reconstitute the {@code BigInteger} instance from a stream (that is,
     * deserialize it). The magnitude is read in as an array of bytes
     * for historical reasons, but it is converted to an array of ints
     * and the byte array is discarded.
     * Note:
     * The current convention is to initialize the cache fields, bitCount,
     * bitLength and lowestSetBit, to 0 rather than some other marker value.
     * Therefore, no explicit action to set these fields needs to be taken in
     * readObject because those fields already have a 0 value be default since
     * defaultReadObject is not being used.
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        /*
         * In order to maintain compatibility with previous serialized forms,
         * the magnitude of a BigInteger is serialized as an array of bytes.
         * The magnitude field is used as a temporary store for the byte array
         * that is deserialized. The cached computation fields should be
         * transient but are serialized for compatibility reasons.
         */

        // prepare to read the alternate persistent fields
        ObjectInputStream.GetField fields = s.readFields();

        // Read the alternate persistent fields that we care about
        int sign = fields.get("signum", -2);
        byte[] magnitude = (byte[])fields.get("magnitude", null);

        // Validate signum
        if (sign < -1 || sign > 1) {
            String message = "BigInteger: Invalid signum value";
            if (fields.defaulted("signum"))
                message = "BigInteger: Signum not present in stream";
            throw new java.io.StreamCorruptedException(message);
        }
        if ((magnitude.length == 0) != (sign == 0)) {
            String message = "BigInteger: signum-magnitude mismatch";
            if (fields.defaulted("magnitude"))
                message = "BigInteger: Magnitude not present in stream";
            throw new java.io.StreamCorruptedException(message);
        }

        // Commit final fields via Unsafe
        unsafe.putIntVolatile(this, signumOffset, sign);

        // Calculate mag field from magnitude and discard magnitude
        unsafe.putObjectVolatile(this, magOffset,
                                 stripLeadingZeroBytes(magnitude));
    }

    // Support for resetting final fields while deserializing
    private static final sun.misc.Unsafe unsafe = sun.misc.Unsafe.getUnsafe();
    private static final long signumOffset;
    private static final long magOffset;
    static {
        try {
            signumOffset = unsafe.objectFieldOffset
                (BigInteger.class.getDeclaredField("signum"));
            magOffset = unsafe.objectFieldOffset
                (BigInteger.class.getDeclaredField("mag"));
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * Save the {@code BigInteger} instance to a stream.
     * The magnitude of a BigInteger is serialized as a byte array for
     * historical reasons.
     *
     * @serialData two necessary fields are written as well as obsolete
     *             fields for compatibility with older versions.
     */
    private void writeObject(ObjectOutputStream s) throws IOException {
        // set the values of the Serializable fields
        ObjectOutputStream.PutField fields = s.putFields();
        fields.put("signum", signum);
        fields.put("magnitude", magSerializedForm());
        // The values written for cached fields are compatible with older
        // versions, but are ignored in readObject so don't otherwise matter.
        fields.put("bitCount", -1);
        fields.put("bitLength", -1);
        fields.put("lowestSetBit", -2);
        fields.put("firstNonzeroByteNum", -2);

        // save them
        s.writeFields();
}

    /**
     * Returns the mag array as an array of bytes.
     */
    private byte[] magSerializedForm() {
        int len = mag.length;

        int bitLen = (len == 0 ? 0 : ((len - 1) << 5) + bitLengthForInt(mag[0]));
        int byteLen = (bitLen + 7) >>> 3;
        byte[] result = new byte[byteLen];

        for (int i = byteLen - 1, bytesCopied = 4, intIndex = len - 1, nextInt = 0;
             i>=0; i--) {
            if (bytesCopied == 4) {
                nextInt = mag[intIndex--];
                bytesCopied = 1;
            } else {
                nextInt >>>= 8;
                bytesCopied++;
            }
            result[i] = (byte)nextInt;
        }
        return result;
    }
}
