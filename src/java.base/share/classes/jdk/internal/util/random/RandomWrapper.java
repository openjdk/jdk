/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util.random;

import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Class used to wrap a {@link java.util.random.RandomGenerator} to
 * {@link java.util.Random}
 */

@SuppressWarnings("serial")
public class RandomWrapper extends Random implements RandomGenerator {
    private final RandomGenerator randomToWrap;

    private RandomWrapper(RandomGenerator randomToWrap) {
        this.randomToWrap = randomToWrap;
    }

    public static Random wrapRandom(RandomGenerator random) {
        // Check to see if its not wrapping another Random instance
        if (random instanceof Random)
            return (Random) random;

        return new RandomWrapper(random);
    }

    /**
     * setSeed does not exist in {@link java.util.random.RandomGenerator} so can't
     * use it
     */
    @Override
    public void setSeed(long seed) {
    }

    @Override
    public void nextBytes(byte[] bytes) {
        this.randomToWrap.nextBytes(bytes);
    }

    @Override
    public int nextInt() {
        return this.randomToWrap.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return this.randomToWrap.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return this.randomToWrap.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return this.randomToWrap.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return this.randomToWrap.nextFloat();
    }

    @Override
    public double nextDouble() {
        return this.randomToWrap.nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        return this.randomToWrap.nextGaussian();
    }

    @Override
    public IntStream ints(long streamSize) {
        return this.randomToWrap.ints(streamSize);
    }

    @Override
    public IntStream ints() {
        return this.randomToWrap.ints();
    }

    @Override
    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
        return this.randomToWrap.ints(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
        return this.randomToWrap.ints(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long streamSize) {
        return this.randomToWrap.longs(streamSize);
    }

    @Override
    public LongStream longs() {
        return this.randomToWrap.longs();
    }

    @Override
    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
        return this.randomToWrap.longs(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
        return this.randomToWrap.longs(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(long streamSize) {
        return this.randomToWrap.doubles(streamSize);
    }

    @Override
    public DoubleStream doubles() {
        return this.randomToWrap.doubles();
    }

    @Override
    public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
        return this.randomToWrap.doubles(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
        return this.randomToWrap.doubles(randomNumberOrigin, randomNumberBound);
    }
}
