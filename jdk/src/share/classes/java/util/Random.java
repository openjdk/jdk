/*
 * Copyright 1995-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.util;
import java.io.*;
import java.util.concurrent.atomic.AtomicLong;
import sun.misc.Unsafe;

/**
 * An instance of this class is used to generate a stream of
 * pseudorandom numbers. The class uses a 48-bit seed, which is
 * modified using a linear congruential formula. (See Donald Knuth,
 * <i>The Art of Computer Programming, Volume 3</i>, Section 3.2.1.)
 * <p>
 * If two instances of {@code Random} are created with the same
 * seed, and the same sequence of method calls is made for each, they
 * will generate and return identical sequences of numbers. In order to
 * guarantee this property, particular algorithms are specified for the
 * class {@code Random}. Java implementations must use all the algorithms
 * shown here for the class {@code Random}, for the sake of absolute
 * portability of Java code. However, subclasses of class {@code Random}
 * are permitted to use other algorithms, so long as they adhere to the
 * general contracts for all the methods.
 * <p>
 * The algorithms implemented by class {@code Random} use a
 * {@code protected} utility method that on each invocation can supply
 * up to 32 pseudorandomly generated bits.
 * <p>
 * Many applications will find the method {@link Math#random} simpler to use.
 *
 * @author  Frank Yellin
 * @since   1.0
 */
public
class Random implements java.io.Serializable {
    /** use serialVersionUID from JDK 1.1 for interoperability */
    static final long serialVersionUID = 3905348978240129619L;

    /**
     * The internal state associated with this pseudorandom number generator.
     * (The specs for the methods in this class describe the ongoing
     * computation of this value.)
     */
    private final AtomicLong seed;

    private final static long multiplier = 0x5DEECE66DL;
    private final static long addend = 0xBL;
    private final static long mask = (1L << 48) - 1;

    /**
     * Creates a new random number generator. This constructor sets
     * the seed of the random number generator to a value very likely
     * to be distinct from any other invocation of this constructor.
     */
    public Random() { this(++seedUniquifier + System.nanoTime()); }
    private static volatile long seedUniquifier = 8682522807148012L;

    /**
     * Creates a new random number generator using a single {@code long} seed.
     * The seed is the initial value of the internal state of the pseudorandom
     * number generator which is maintained by method {@link #next}.
     *
     * <p>The invocation {@code new Random(seed)} is equivalent to:
     *  <pre> {@code
     * Random rnd = new Random();
     * rnd.setSeed(seed);}</pre>
     *
     * @param seed the initial seed
     * @see   #setSeed(long)
     */
    public Random(long seed) {
        this.seed = new AtomicLong(0L);
        setSeed(seed);
    }

    /**
     * Sets the seed of this random number generator using a single
     * {@code long} seed. The general contract of {@code setSeed} is
     * that it alters the state of this random number generator object
     * so as to be in exactly the same state as if it had just been
     * created with the argument {@code seed} as a seed. The method
     * {@code setSeed} is implemented by class {@code Random} by
     * atomically updating the seed to
     *  <pre>{@code (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1)}</pre>
     * and clearing the {@code haveNextNextGaussian} flag used by {@link
     * #nextGaussian}.
     *
     * <p>The implementation of {@code setSeed} by class {@code Random}
     * happens to use only 48 bits of the given seed. In general, however,
     * an overriding method may use all 64 bits of the {@code long}
     * argument as a seed value.
     *
     * @param seed the initial seed
     */
    synchronized public void setSeed(long seed) {
        seed = (seed ^ multiplier) & mask;
        this.seed.set(seed);
        haveNextNextGaussian = false;
    }

    /**
     * Generates the next pseudorandom number. Subclasses should
     * override this, as this is used by all other methods.
     *
     * <p>The general contract of {@code next} is that it returns an
     * {@code int} value and if the argument {@code bits} is between
     * {@code 1} and {@code 32} (inclusive), then that many low-order
     * bits of the returned value will be (approximately) independently
     * chosen bit values, each of which is (approximately) equally
     * likely to be {@code 0} or {@code 1}. The method {@code next} is
     * implemented by class {@code Random} by atomically updating the seed to
     *  <pre>{@code (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1)}</pre>
     * and returning
     *  <pre>{@code (int)(seed >>> (48 - bits))}.</pre>
     *
     * This is a linear congruential pseudorandom number generator, as
     * defined by D. H. Lehmer and described by Donald E. Knuth in
     * <i>The Art of Computer Programming,</i> Volume 3:
     * <i>Seminumerical Algorithms</i>, section 3.2.1.
     *
     * @param  bits random bits
     * @return the next pseudorandom value from this random number
     *         generator's sequence
     * @since  1.1
     */
    protected int next(int bits) {
        long oldseed, nextseed;
        AtomicLong seed = this.seed;
        do {
            oldseed = seed.get();
            nextseed = (oldseed * multiplier + addend) & mask;
        } while (!seed.compareAndSet(oldseed, nextseed));
        return (int)(nextseed >>> (48 - bits));
    }

    /**
     * Generates random bytes and places them into a user-supplied
     * byte array.  The number of random bytes produced is equal to
     * the length of the byte array.
     *
     * <p>The method {@code nextBytes} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public void nextBytes(byte[] bytes) {
     *   for (int i = 0; i < bytes.length; )
     *     for (int rnd = nextInt(), n = Math.min(bytes.length - i, 4);
     *          n-- > 0; rnd >>= 8)
     *       bytes[i++] = (byte)rnd;
     * }}</pre>
     *
     * @param  bytes the byte array to fill with random bytes
     * @throws NullPointerException if the byte array is null
     * @since  1.1
     */
    public void nextBytes(byte[] bytes) {
        for (int i = 0, len = bytes.length; i < len; )
            for (int rnd = nextInt(),
                     n = Math.min(len - i, Integer.SIZE/Byte.SIZE);
                 n-- > 0; rnd >>= Byte.SIZE)
                bytes[i++] = (byte)rnd;
    }

    /**
     * Returns the next pseudorandom, uniformly distributed {@code int}
     * value from this random number generator's sequence. The general
     * contract of {@code nextInt} is that one {@code int} value is
     * pseudorandomly generated and returned. All 2<font size="-1"><sup>32
     * </sup></font> possible {@code int} values are produced with
     * (approximately) equal probability.
     *
     * <p>The method {@code nextInt} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public int nextInt() {
     *   return next(32);
     * }}</pre>
     *
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value from this random number generator's sequence
     */
    public int nextInt() {
        return next(32);
    }

    /**
     * Returns a pseudorandom, uniformly distributed {@code int} value
     * between 0 (inclusive) and the specified value (exclusive), drawn from
     * this random number generator's sequence.  The general contract of
     * {@code nextInt} is that one {@code int} value in the specified range
     * is pseudorandomly generated and returned.  All {@code n} possible
     * {@code int} values are produced with (approximately) equal
     * probability.  The method {@code nextInt(int n)} is implemented by
     * class {@code Random} as if by:
     *  <pre> {@code
     * public int nextInt(int n) {
     *   if (n <= 0)
     *     throw new IllegalArgumentException("n must be positive");
     *
     *   if ((n & -n) == n)  // i.e., n is a power of 2
     *     return (int)((n * (long)next(31)) >> 31);
     *
     *   int bits, val;
     *   do {
     *       bits = next(31);
     *       val = bits % n;
     *   } while (bits - val + (n-1) < 0);
     *   return val;
     * }}</pre>
     *
     * <p>The hedge "approximately" is used in the foregoing description only
     * because the next method is only approximately an unbiased source of
     * independently chosen bits.  If it were a perfect source of randomly
     * chosen bits, then the algorithm shown would choose {@code int}
     * values from the stated range with perfect uniformity.
     * <p>
     * The algorithm is slightly tricky.  It rejects values that would result
     * in an uneven distribution (due to the fact that 2^31 is not divisible
     * by n). The probability of a value being rejected depends on n.  The
     * worst case is n=2^30+1, for which the probability of a reject is 1/2,
     * and the expected number of iterations before the loop terminates is 2.
     * <p>
     * The algorithm treats the case where n is a power of two specially: it
     * returns the correct number of high-order bits from the underlying
     * pseudo-random number generator.  In the absence of special treatment,
     * the correct number of <i>low-order</i> bits would be returned.  Linear
     * congruential pseudo-random number generators such as the one
     * implemented by this class are known to have short periods in the
     * sequence of values of their low-order bits.  Thus, this special case
     * greatly increases the length of the sequence of values returned by
     * successive calls to this method if n is a small power of two.
     *
     * @param n the bound on the random number to be returned.  Must be
     *        positive.
     * @return the next pseudorandom, uniformly distributed {@code int}
     *         value between {@code 0} (inclusive) and {@code n} (exclusive)
     *         from this random number generator's sequence
     * @exception IllegalArgumentException if n is not positive
     * @since 1.2
     */

    public int nextInt(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be positive");

        if ((n & -n) == n)  // i.e., n is a power of 2
            return (int)((n * (long)next(31)) >> 31);

        int bits, val;
        do {
            bits = next(31);
            val = bits % n;
        } while (bits - val + (n-1) < 0);
        return val;
    }

    /**
     * Returns the next pseudorandom, uniformly distributed {@code long}
     * value from this random number generator's sequence. The general
     * contract of {@code nextLong} is that one {@code long} value is
     * pseudorandomly generated and returned.
     *
     * <p>The method {@code nextLong} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public long nextLong() {
     *   return ((long)next(32) << 32) + next(32);
     * }}</pre>
     *
     * Because class {@code Random} uses a seed with only 48 bits,
     * this algorithm will not return all possible {@code long} values.
     *
     * @return the next pseudorandom, uniformly distributed {@code long}
     *         value from this random number generator's sequence
     */
    public long nextLong() {
        // it's okay that the bottom word remains signed.
        return ((long)(next(32)) << 32) + next(32);
    }

    /**
     * Returns the next pseudorandom, uniformly distributed
     * {@code boolean} value from this random number generator's
     * sequence. The general contract of {@code nextBoolean} is that one
     * {@code boolean} value is pseudorandomly generated and returned.  The
     * values {@code true} and {@code false} are produced with
     * (approximately) equal probability.
     *
     * <p>The method {@code nextBoolean} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public boolean nextBoolean() {
     *   return next(1) != 0;
     * }}</pre>
     *
     * @return the next pseudorandom, uniformly distributed
     *         {@code boolean} value from this random number generator's
     *         sequence
     * @since 1.2
     */
    public boolean nextBoolean() {
        return next(1) != 0;
    }

    /**
     * Returns the next pseudorandom, uniformly distributed {@code float}
     * value between {@code 0.0} and {@code 1.0} from this random
     * number generator's sequence.
     *
     * <p>The general contract of {@code nextFloat} is that one
     * {@code float} value, chosen (approximately) uniformly from the
     * range {@code 0.0f} (inclusive) to {@code 1.0f} (exclusive), is
     * pseudorandomly generated and returned. All 2<font
     * size="-1"><sup>24</sup></font> possible {@code float} values
     * of the form <i>m&nbsp;x&nbsp</i>2<font
     * size="-1"><sup>-24</sup></font>, where <i>m</i> is a positive
     * integer less than 2<font size="-1"><sup>24</sup> </font>, are
     * produced with (approximately) equal probability.
     *
     * <p>The method {@code nextFloat} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public float nextFloat() {
     *   return next(24) / ((float)(1 << 24));
     * }}</pre>
     *
     * <p>The hedge "approximately" is used in the foregoing description only
     * because the next method is only approximately an unbiased source of
     * independently chosen bits. If it were a perfect source of randomly
     * chosen bits, then the algorithm shown would choose {@code float}
     * values from the stated range with perfect uniformity.<p>
     * [In early versions of Java, the result was incorrectly calculated as:
     *  <pre> {@code
     *   return next(30) / ((float)(1 << 30));}</pre>
     * This might seem to be equivalent, if not better, but in fact it
     * introduced a slight nonuniformity because of the bias in the rounding
     * of floating-point numbers: it was slightly more likely that the
     * low-order bit of the significand would be 0 than that it would be 1.]
     *
     * @return the next pseudorandom, uniformly distributed {@code float}
     *         value between {@code 0.0} and {@code 1.0} from this
     *         random number generator's sequence
     */
    public float nextFloat() {
        return next(24) / ((float)(1 << 24));
    }

    /**
     * Returns the next pseudorandom, uniformly distributed
     * {@code double} value between {@code 0.0} and
     * {@code 1.0} from this random number generator's sequence.
     *
     * <p>The general contract of {@code nextDouble} is that one
     * {@code double} value, chosen (approximately) uniformly from the
     * range {@code 0.0d} (inclusive) to {@code 1.0d} (exclusive), is
     * pseudorandomly generated and returned.
     *
     * <p>The method {@code nextDouble} is implemented by class {@code Random}
     * as if by:
     *  <pre> {@code
     * public double nextDouble() {
     *   return (((long)next(26) << 27) + next(27))
     *     / (double)(1L << 53);
     * }}</pre>
     *
     * <p>The hedge "approximately" is used in the foregoing description only
     * because the {@code next} method is only approximately an unbiased
     * source of independently chosen bits. If it were a perfect source of
     * randomly chosen bits, then the algorithm shown would choose
     * {@code double} values from the stated range with perfect uniformity.
     * <p>[In early versions of Java, the result was incorrectly calculated as:
     *  <pre> {@code
     *   return (((long)next(27) << 27) + next(27))
     *     / (double)(1L << 54);}</pre>
     * This might seem to be equivalent, if not better, but in fact it
     * introduced a large nonuniformity because of the bias in the rounding
     * of floating-point numbers: it was three times as likely that the
     * low-order bit of the significand would be 0 than that it would be 1!
     * This nonuniformity probably doesn't matter much in practice, but we
     * strive for perfection.]
     *
     * @return the next pseudorandom, uniformly distributed {@code double}
     *         value between {@code 0.0} and {@code 1.0} from this
     *         random number generator's sequence
     * @see Math#random
     */
    public double nextDouble() {
        return (((long)(next(26)) << 27) + next(27))
            / (double)(1L << 53);
    }

    private double nextNextGaussian;
    private boolean haveNextNextGaussian = false;

    /**
     * Returns the next pseudorandom, Gaussian ("normally") distributed
     * {@code double} value with mean {@code 0.0} and standard
     * deviation {@code 1.0} from this random number generator's sequence.
     * <p>
     * The general contract of {@code nextGaussian} is that one
     * {@code double} value, chosen from (approximately) the usual
     * normal distribution with mean {@code 0.0} and standard deviation
     * {@code 1.0}, is pseudorandomly generated and returned.
     *
     * <p>The method {@code nextGaussian} is implemented by class
     * {@code Random} as if by a threadsafe version of the following:
     *  <pre> {@code
     * private double nextNextGaussian;
     * private boolean haveNextNextGaussian = false;
     *
     * public double nextGaussian() {
     *   if (haveNextNextGaussian) {
     *     haveNextNextGaussian = false;
     *     return nextNextGaussian;
     *   } else {
     *     double v1, v2, s;
     *     do {
     *       v1 = 2 * nextDouble() - 1;   // between -1.0 and 1.0
     *       v2 = 2 * nextDouble() - 1;   // between -1.0 and 1.0
     *       s = v1 * v1 + v2 * v2;
     *     } while (s >= 1 || s == 0);
     *     double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
     *     nextNextGaussian = v2 * multiplier;
     *     haveNextNextGaussian = true;
     *     return v1 * multiplier;
     *   }
     * }}</pre>
     * This uses the <i>polar method</i> of G. E. P. Box, M. E. Muller, and
     * G. Marsaglia, as described by Donald E. Knuth in <i>The Art of
     * Computer Programming</i>, Volume 3: <i>Seminumerical Algorithms</i>,
     * section 3.4.1, subsection C, algorithm P. Note that it generates two
     * independent values at the cost of only one call to {@code StrictMath.log}
     * and one call to {@code StrictMath.sqrt}.
     *
     * @return the next pseudorandom, Gaussian ("normally") distributed
     *         {@code double} value with mean {@code 0.0} and
     *         standard deviation {@code 1.0} from this random number
     *         generator's sequence
     */
    synchronized public double nextGaussian() {
        // See Knuth, ACP, Section 3.4.1 Algorithm C.
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        } else {
            double v1, v2, s;
            do {
                v1 = 2 * nextDouble() - 1; // between -1 and 1
                v2 = 2 * nextDouble() - 1; // between -1 and 1
                s = v1 * v1 + v2 * v2;
            } while (s >= 1 || s == 0);
            double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
            nextNextGaussian = v2 * multiplier;
            haveNextNextGaussian = true;
            return v1 * multiplier;
        }
    }

    /**
     * Serializable fields for Random.
     *
     * @serialField    seed long
     *              seed for random computations
     * @serialField    nextNextGaussian double
     *              next Gaussian to be returned
     * @serialField      haveNextNextGaussian boolean
     *              nextNextGaussian is valid
     */
    private static final ObjectStreamField[] serialPersistentFields = {
        new ObjectStreamField("seed", Long.TYPE),
        new ObjectStreamField("nextNextGaussian", Double.TYPE),
        new ObjectStreamField("haveNextNextGaussian", Boolean.TYPE)
    };

    /**
     * Reconstitute the {@code Random} instance from a stream (that is,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {

        ObjectInputStream.GetField fields = s.readFields();

        // The seed is read in as {@code long} for
        // historical reasons, but it is converted to an AtomicLong.
        long seedVal = (long) fields.get("seed", -1L);
        if (seedVal < 0)
          throw new java.io.StreamCorruptedException(
                              "Random: invalid seed");
        resetSeed(seedVal);
        nextNextGaussian = fields.get("nextNextGaussian", 0.0);
        haveNextNextGaussian = fields.get("haveNextNextGaussian", false);
    }

    /**
     * Save the {@code Random} instance to a stream.
     */
    synchronized private void writeObject(ObjectOutputStream s)
        throws IOException {

        // set the values of the Serializable fields
        ObjectOutputStream.PutField fields = s.putFields();

        // The seed is serialized as a long for historical reasons.
        fields.put("seed", seed.get());
        fields.put("nextNextGaussian", nextNextGaussian);
        fields.put("haveNextNextGaussian", haveNextNextGaussian);

        // save them
        s.writeFields();
    }

    // Support for resetting seed while deserializing
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long seedOffset;
    static {
        try {
            seedOffset = unsafe.objectFieldOffset
                (Random.class.getDeclaredField("seed"));
        } catch (Exception ex) { throw new Error(ex); }
    }
    private void resetSeed(long seedVal) {
        unsafe.putObjectVolatile(this, seedOffset, new AtomicLong(seedVal));
    }
}
