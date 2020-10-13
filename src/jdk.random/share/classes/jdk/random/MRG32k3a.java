/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.random;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import java.util.random.RandomSupport;
import java.util.random.RandomSupport.AbstractArbitrarilyJumpableGenerator;

/**
 * An "arbitrarily jumpable" pseudorandom number generator (PRNG) whose period
 * is roughly 2<sup>191</sup>.  Class {@link MRG32k3a} implements
 * interface {@link RandomGenerator} and extends abstract class
 * {@link AbstractArbitrarilyJumpableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * values of type {@code int}, {@code long}, {@code float}, {@code double},
 * and {@code boolean} (and for producing streams of pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, and {@code double}),
 * as well as methods for creating new {@link MRG32k3a} objects
 * by moving forward either a large distance (2<sup>76</sup>), a very large
 * distance (2<sup>127</sup>), or an arbitrary user-specified distance
 * around the state cycle.
 *
 * <p>Instances of {@link MRG32k3a} are <em>not</em> thread-safe.
 * They are designed to be used so that each thread has its own instance.
 * The methods {@link #jump} and {@link #leap} and {@link #jumps} and {@link #leaps}
 * can be used to construct new instances of {@link MRG32k3a} that traverse
 * other parts of the state cycle.
 * <p>
 * {@link MRG32k3a} is most appropriate for an application that uses only
 * floating-point values to be chosen from a uniform distribution,
 * where no more than 32 bits of floating-point precision are required
 * and exact equidistribution is not required.  The methods
 * {@link #nextGaussian} and {@link #nextExponential} are not supported
 * (attempting to use either will throw an error).
 * <p>
 * Instances of {@link MRG32k3a} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since   16
 */
public final class MRG32k3a extends AbstractArbitrarilyJumpableGenerator {

    /*
     * Implementation Overview.
     *
     * See http://simul.iro.umontreal.ca/rng/MRG32k3a.c .
     *
     * File organization: First the non-public methods that constitute
     * the main algorithm, then the main public methods, followed by
     * some custom spliterator classes needed for stream methods.
     */

    /**
     * Group name.
     */
    private static final String GROUP = "MRG";

    private final static double NORM1 = 2.328306549295728e-10;
    private final static double NORM2 = 2.328318824698632e-10;
    private final static double M1 =   4294967087.0;
    private final static double M2 =   4294944443.0;
    private final static double A12 =     1403580.0;
    private final static double A13N =     810728.0;
    private final static double A21 =      527612.0;
    private final static double A23N =    1370589.0;
    private final static int M1_DEFICIT = 209;

    /**
     * The per-instance state.
     The seeds for s10, s11, s12 must be integers in [0, m1 - 1] and not all 0.
     The seeds for s20, s21, s22 must be integers in [0, m2 - 1] and not all 0.
     */
    private double s10, s11, s12,
                   s20, s21, s22;

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong DEFAULT_GEN =
        new AtomicLong(RandomSupport.initialSeed());

    /*
      32-bits Random number generator U(0,1): MRG32k3a
      Author: Pierre L'Ecuyer,
      Source: Good Parameter Sets for Combined Multiple Recursive Random
           Number Generators,
           Shorter version in Operations Research,
           47, 1 (1999), 159--164.
           ---------------------------------------------------------
    */

    private void nextState() {
        /* Component 1 */
        double p1 = A12 * s11 - A13N * s10;
        double k1 = p1 / M1;   p1 -= k1 * M1;   if (p1 < 0.0) p1 += M1;
        s10 = s11;   s11 = s12;   s12 = p1;
        /* Component 2 */
        double p2 = A21 * s22 - A23N * s20;
        double k2 = p2 / M2;   p2 -= k2 * M2;   if (p2 < 0.0) p2 += M2;
        s20 = s21;   s21 = s22;   s22 = p2;
    }


    /**
     * The form of nextInt used by IntStream Spliterators.
     * Exactly the same as long version, except for types.
     *
     * @param origin the least value, unless greater than bound
     * @param bound the upper bound (exclusive), must not equal origin
     *
     * @return a pseudorandom value
     */
    private int internalNextInt(int origin, int bound) {
        if (origin < bound) {
            final int n = bound - origin;
            final int m = n - 1;
            if (n > 0) {
                int r;
                for (int u = (int)nextDouble() >>> 1;
                     u + m + ((M1_DEFICIT + 1) >>> 1) - (r = u % n) < 0;
                     u = (int)nextDouble() >>> 1)
                    ;
                return (r + origin);
            } else {
                return RandomSupport.boundedNextInt(this, origin, bound);
            }
        } else {
            return nextInt();
        }
    }

    private int internalNextInt(int bound) {
        // Specialize internalNextInt for origin == 0, bound > 0
        final int n = bound;
        final int m = n - 1;
        int r;
        for (int u = (int)nextDouble() >>> 1;
             u + m + ((M1_DEFICIT + 1) >>> 1) - (r = u % n) < 0;
             u = (int)nextDouble() >>> 1)
            ;
        return r;
    }

    /**
     * All arguments must be known to be nonnegative integral values
     * less than the appropriate modulus.
     */
    private MRG32k3a(double s10, double s11, double s12,
                     double s20, double s21, double s22) {
        this.s10 = s10; this.s11 = s11; this.s12 = s12;
        this.s20 = s20; this.s21 = s21; this.s22 = s22;
        if ((s10 == 0.0) && (s11 == 0.0) && (s12 == 0.0)) {
            this.s10 = this.s11 = this.s12 = 12345.0;
        }
        if ((s20 == 0.0) && (s21 == 0.0) && (s22 == 0.0)) {
            this.s20 = this.s21 = this.s21 = 12345.0;
        }
    }

    /* ---------------- public methods ---------------- */

    /**
     * Creates a new MRG32k3a instance using six specified {@code int}
     * initial seed values. MRG32k3a instances created with the same
     * seeds in the same program generate identical sequences of values.
     * If all six seed values are zero, the generator is seeded to a
     * widely used initialization of MRG32k3a: all six state variables
     * are set to 12345.
     *
     * @param s10 the first seed value for the first subgenerator
     * @param s11 the second seed value for the first subgenerator
     * @param s12 the third seed value for the first subgenerator
     * @param s20 the first seed value for the second subgenerator
     * @param s21 the second seed value for the second subgenerator
     * @param s22 the third seed value for the second subgenerator
     */
    public MRG32k3a(int s10, int s11, int s12,
                    int s20, int s21, int s22) {
        this(((double)(((long)s10) & 0x00000000ffffffffL)) % M1,
             ((double)(((long)s11) & 0x00000000ffffffffL)) % M1,
             ((double)(((long)s12) & 0x00000000ffffffffL)) % M1,
             ((double)(((long)s20) & 0x00000000ffffffffL)) % M2,
             ((double)(((long)s21) & 0x00000000ffffffffL)) % M2,
             ((double)(((long)s22) & 0x00000000ffffffffL)) % M2);
    }

    /**
     * Creates a new MRG32k3a instance using the specified
     * initial seed. MRG32k3a instances created with the same
     * seed in the same program generate identical sequences of values.
     * An argument of 0 seeds the generator to a widely used initialization
     * of MRG32k3a: all six state variables are set to 12345.
     *
     * @param seed the initial seed
     */
    public MRG32k3a(long seed) {
        this((double)((seed & 0x7FF) + 12345),
             (double)(((seed >>> 11) & 0x7FF) + 12345),
             (double)(((seed >>> 22) & 0x7FF) + 12345),
             (double)(((seed >>> 33) & 0x7FF) + 12345),
             (double)(((seed >>> 44) & 0x7FF) + 12345),
             (double)((seed >>> 55) + 12345));
    }

    /**
     * Creates a new MRG32k3a instance that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program; and
     * may, and typically does, vary across program invocations.
     */
    public MRG32k3a() {
        this(DEFAULT_GEN.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link MRG32k3a} using the specified array of
     * initial seed bytes. Instances of {@link MRG32k3a} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public MRG32k3a(byte[] seed) {
        // Convert the seed to 6 int values.
        int[] data = RandomSupport.convertSeedBytesToInts(seed, 6, 0);
        int s10 = data[0], s11 = data[1], s12 = data[2];
        int s20 = data[3], s21 = data[4], s22 = data[5];
        this.s10 = ((double)(((long)s10) & 0x00000000ffffffffL)) % M1;
        this.s11 = ((double)(((long)s11) & 0x00000000ffffffffL)) % M1;
        this.s12 = ((double)(((long)s12) & 0x00000000ffffffffL)) % M1;
        this.s20 = ((double)(((long)s20) & 0x00000000ffffffffL)) % M2;
        this.s21 = ((double)(((long)s21) & 0x00000000ffffffffL)) % M2;
        this.s22 = ((double)(((long)s22) & 0x00000000ffffffffL)) % M2;
        if ((s10 == 0.0) && (s11 == 0.0) && (s12 == 0.0)) {
            this.s10 = this.s11 = this.s12 = 12345.0;
        }
        if ((s20 == 0.0) && (s21 == 0.0) && (s22 == 0.0)) {
            this.s20 = this.s21 = this.s21 = 12345.0;
        }
    }

    @Override
    public AbstractArbitrarilyJumpableGenerator copy() {
        return new MRG32k3a(s10, s11, s12, s20, s21, s22);
    }

    /**
     * Returns a pseudorandom {@code double} value between zero
     * (exclusive) and one (exclusive).
     *
     * @return a pseudorandom {@code double} value between zero
     *         (exclusive) and one (exclusive)
     */
    public double nextOpenDouble() {
        nextState();
        double p1 = s12, p2 = s22;
        if (p1 <= p2)
            return ((p1 - p2 + M1) * NORM1);
        else
            return ((p1 - p2) * NORM1);
    }

    @Override
    public double nextDouble() {
        nextState();
        double p1 = s12, p2 = s22;
        final double p = p1 * NORM1 - p2 * NORM2;
        if (p < 0.0) return (p + 1.0);
        else return p;
    }

    @Override
    public float nextFloat() {
        return (float)nextDouble();
    }

    @Override
    public int nextInt() {
        return (internalNextInt(0x10000) << 16) | internalNextInt(0x10000);
    }

    @Override
    public long nextLong() {
         return (((long)internalNextInt(0x200000) << 43) |
                ((long)internalNextInt(0x200000) << 22) |
                ((long)internalNextInt(0x400000)));
    }

    /**
     * This method is not supported by class {@code MRG32k3a}.
     *
     * @throws UnsupportedOperationException MRG32k3a does not support method nextGaussian
     */
    @Override
    public double nextGaussian() {
        throw new UnsupportedOperationException("Class MRG32k3a does not support method nextGaussian");
    }

    /**
     * This method is not supported by class {@code MRG32k3a}.
     *
     * @throws UnsupportedOperationException MRG32k3a does not support method nextExponential
     */
    @Override
    public double nextExponential() {
        throw new UnsupportedOperationException("Class MRG32k3a does not support method nextExponential");
    }

    // Period is (m1**3 - 1)(m2**3 - 1)/2, or approximately 2**191.
    static BigInteger calculateThePeriod() {
        BigInteger bigm1 = BigInteger.valueOf((long)M1);
        BigInteger bigm2 = BigInteger.valueOf((long)M2);
        BigInteger t1 = bigm1.multiply(bigm1).multiply(bigm1).subtract(BigInteger.ONE);
        BigInteger t2 = bigm2.multiply(bigm2).multiply(bigm2).subtract(BigInteger.ONE);
        return t1.shiftRight(1).multiply(t2);
    }

    static final BigInteger PERIOD = calculateThePeriod();

    @Override
    public BigInteger period() {
        return PERIOD;
    }

    // Jump and leap distances recommended in Section 1.3 of this paper:
    // Pierre L'Ecuyer, Richard Simard, E. Jack Chen, and W. David Kelton.
    // An Object-Oriented Random-Number Package with Many Long Streams and Substreams.
    // Operations Research 50, 6 (Nov--Dec 2002), 1073--1075.

    @Override
    public double defaultJumpDistance() {
        return 0x1.0p76;   // 2**76
    }

    @Override
    public double defaultLeapDistance() {
        return 0x1.0p127;   // 2**127
    }

    @Override
    public void jump(double distance) {
        if (distance < 0.0 || Double.isInfinite(distance) || distance != Math.floor(distance))
            throw new IllegalArgumentException("jump distance must be a nonnegative finite integer");
            // We will compute a jump transformation (s => M s) for each LCG.
            // We initialize each transformation to the identity transformation.
            // Each will be turned into the d'th power of the corresponding base transformation.
        long m1_00 = 1, m1_01 = 0, m1_02 = 0,
             m1_10 = 0, m1_11 = 1, m1_12 = 0,
             m1_20 = 0, m1_21 = 0, m1_22 = 1;
        long m2_00 = 1, m2_01 = 0, m2_02 = 0,
             m2_10 = 0, m2_11 = 1, m2_12 = 0,
             m2_20 = 0, m2_21 = 0, m2_22 = 1;
        // These are the base transformations, which will be repeatedly squared,
        // and composed with the computed transformations for each 1-bit in distance.
        long t1_00 = 0,           t1_01 = 1,         t1_02 = 0,
             t1_10 = 0,           t1_11 = 0,         t1_12 = 1,
             t1_20 = -(long)A13N, t1_21 = (long)A12, t1_22 = 0;
        long t2_00 = 0,           t2_01 = 1,         t2_02 = 0,
             t2_10 = 0,           t2_11 = 0,         t2_12 = 1,
             t2_20 = -(long)A23N, t2_21 = (long)A21, t2_22 = 0;
        while (distance > 0.0) {
            final double dhalf = 0.5 * distance;
            if (Math.floor(dhalf) != dhalf) {
                // distance is odd: accumulate current squaring
                final long n1_00 = m1_00 * t1_00 + m1_01 * t1_10 + m1_02 * t1_20;
                final long n1_01 = m1_00 * t1_01 + m1_01 * t1_11 + m1_02 * t1_21;
                final long n1_02 = m1_00 * t1_02 + m1_01 * t1_12 + m1_02 * t1_22;
                final long n1_10 = m1_10 * t1_00 + m1_11 * t1_10 + m1_12 * t1_20;
                final long n1_11 = m1_10 * t1_01 + m1_11 * t1_11 + m1_12 * t1_21;
                final long n1_12 = m1_10 * t1_02 + m1_11 * t1_12 + m1_12 * t1_22;
                final long n1_20 = m1_20 * t1_00 + m1_21 * t1_10 + m1_22 * t1_20;
                final long n1_21 = m1_20 * t1_01 + m1_21 * t1_11 + m1_22 * t1_21;
                final long n1_22 = m1_20 * t1_02 + m1_21 * t1_12 + m1_22 * t1_22;
                m1_00 = Math.floorMod(n1_00, (long)M1);
                m1_01 = Math.floorMod(n1_01, (long)M1);
                m1_02 = Math.floorMod(n1_02, (long)M1);
                m1_10 = Math.floorMod(n1_10, (long)M1);
                m1_11 = Math.floorMod(n1_11, (long)M1);
                m1_12 = Math.floorMod(n1_12, (long)M1);
                m1_20 = Math.floorMod(n1_20, (long)M1);
                m1_21 = Math.floorMod(n1_21, (long)M1);
                m1_22 = Math.floorMod(n1_22, (long)M1);
                final long n2_00 = m2_00 * t2_00 + m2_01 * t2_10 + m2_02 * t2_20;
                final long n2_01 = m2_00 * t2_01 + m2_01 * t2_11 + m2_02 * t2_21;
                final long n2_02 = m2_00 * t2_02 + m2_01 * t2_12 + m2_02 * t2_22;
                final long n2_10 = m2_10 * t2_00 + m2_11 * t2_10 + m2_12 * t2_20;
                final long n2_11 = m2_10 * t2_01 + m2_11 * t2_11 + m2_12 * t2_21;
                final long n2_12 = m2_10 * t2_02 + m2_11 * t2_12 + m2_12 * t2_22;
                final long n2_20 = m2_20 * t2_00 + m2_21 * t2_10 + m2_22 * t2_20;
                final long n2_21 = m2_20 * t2_01 + m2_21 * t2_11 + m2_22 * t2_21;
                final long n2_22 = m2_20 * t2_02 + m2_21 * t2_12 + m2_22 * t2_22;
                m2_00 = Math.floorMod(n2_00, (long)M2);
                m2_01 = Math.floorMod(n2_01, (long)M2);
                m2_02 = Math.floorMod(n2_02, (long)M2);
                m2_10 = Math.floorMod(n2_10, (long)M2);
                m2_11 = Math.floorMod(n2_11, (long)M2);
                m2_12 = Math.floorMod(n2_12, (long)M2);
                m2_20 = Math.floorMod(n2_20, (long)M2);
                m2_21 = Math.floorMod(n2_21, (long)M2);
                m2_22 = Math.floorMod(n2_22, (long)M2);
            }
            // Square the base transformations.
            {
                final long z1_00 = m1_00 * m1_00 + m1_01 * m1_10 + m1_02 * m1_20;
                final long z1_01 = m1_00 * m1_01 + m1_01 * m1_11 + m1_02 * m1_21;
                final long z1_02 = m1_00 * m1_02 + m1_01 * m1_12 + m1_02 * m1_22;
                final long z1_10 = m1_10 * m1_00 + m1_11 * m1_10 + m1_12 * m1_20;
                final long z1_11 = m1_10 * m1_01 + m1_11 * m1_11 + m1_12 * m1_21;
                final long z1_12 = m1_10 * m1_02 + m1_11 * m1_12 + m1_12 * m1_22;
                final long z1_20 = m1_20 * m1_00 + m1_21 * m1_10 + m1_22 * m1_20;
                final long z1_21 = m1_20 * m1_01 + m1_21 * m1_11 + m1_22 * m1_21;
                final long z1_22 = m1_20 * m1_02 + m1_21 * m1_12 + m1_22 * m1_22;
                m1_00 = Math.floorMod(z1_00, (long)M1);
                m1_01 = Math.floorMod(z1_01, (long)M1);
                m1_02 = Math.floorMod(z1_02, (long)M1);
                m1_10 = Math.floorMod(z1_10, (long)M1);
                m1_11 = Math.floorMod(z1_11, (long)M1);
                m1_12 = Math.floorMod(z1_12, (long)M1);
                m1_20 = Math.floorMod(z1_20, (long)M1);
                m1_21 = Math.floorMod(z1_21, (long)M1);
                m1_22 = Math.floorMod(z1_22, (long)M1);
                final long z2_00 = m2_00 * m2_00 + m2_01 * m2_10 + m2_02 * m2_20;
                final long z2_01 = m2_00 * m2_01 + m2_01 * m2_11 + m2_02 * m2_21;
                final long z2_02 = m2_00 * m2_02 + m2_01 * m2_12 + m2_02 * m2_22;
                final long z2_10 = m2_10 * m2_00 + m2_11 * m2_10 + m2_12 * m2_20;
                final long z2_11 = m2_10 * m2_01 + m2_11 * m2_11 + m2_12 * m2_21;
                final long z2_12 = m2_10 * m2_02 + m2_11 * m2_12 + m2_12 * m2_22;
                final long z2_20 = m2_20 * m2_00 + m2_21 * m2_10 + m2_22 * m2_20;
                final long z2_21 = m2_20 * m2_01 + m2_21 * m2_11 + m2_22 * m2_21;
                final long z2_22 = m2_20 * m2_02 + m2_21 * m2_12 + m2_22 * m2_22;
                m2_00 = Math.floorMod(z2_00, (long)M2);
                m2_01 = Math.floorMod(z2_01, (long)M2);
                m2_02 = Math.floorMod(z2_02, (long)M2);
                m2_10 = Math.floorMod(z2_10, (long)M2);
                m2_11 = Math.floorMod(z2_11, (long)M2);
                m2_12 = Math.floorMod(z2_12, (long)M2);
                m2_20 = Math.floorMod(z2_20, (long)M2);
                m2_21 = Math.floorMod(z2_21, (long)M2);
                m2_22 = Math.floorMod(z2_22, (long)M2);
            }
            // Divide distance by 2.
            distance = dhalf;
        }
        final long w10 = m1_00 * (long)s10 + m1_01 * (long)s11 + m1_02 * (long)s12;
        final long w11 = m1_10 * (long)s10 + m1_11 * (long)s11 + m1_12 * (long)s12;
        final long w12 = m1_20 * (long)s10 + m1_21 * (long)s11 + m1_22 * (long)s12;
        s10 = Math.floorMod(w10, (long)M1);
        s11 = Math.floorMod(w11, (long)M1);
        s12 = Math.floorMod(w12, (long)M1);
        final long w20 = m2_00 * (long)s20 + m2_01 * (long)s21 + m2_02 * (long)s22;
        final long w21 = m2_10 * (long)s20 + m2_11 * (long)s21 + m2_12 * (long)s22;
        final long w22 = m2_20 * (long)s20 + m2_21 * (long)s21 + m2_22 * (long)s22;
        s20 = Math.floorMod(w20, (long)M2);
        s21 = Math.floorMod(w21, (long)M2);
        s22 = Math.floorMod(w22, (long)M2);
    }

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a distance equal to 2<sup>{@code logDistance}</sup>
     * within its state cycle.
     *
     * @param logDistance the base-2 logarithm of the distance to jump
     *        forward within the state cycle.  Must be non-negative and
     *        not greater than 192.
     *
     * @throws IllegalArgumentException if {@code logDistance} is
     *         less than zero or 2<sup>{@code logDistance}</sup> is
     *         greater than the period of this generator
     */
    public void jumpPowerOfTwo(int logDistance) {
        if (logDistance < 0 || logDistance > 192)
            throw new IllegalArgumentException("logDistance must be non-negative and not greater than 192");
        jump(Math.scalb(1.0, logDistance));
    }

}
