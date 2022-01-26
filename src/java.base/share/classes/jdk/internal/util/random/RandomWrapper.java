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
    private final RandomGenerator generator;

    private RandomWrapper(RandomGenerator randomToWrap) {
	this.generator = randomToWrap;
    }

    public static Random wrap(RandomGenerator random) {
	// Check to see if its not wrapping another Random instance
	if (random instanceof Random rand)
	    return rand;

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
	this.generator.nextBytes(bytes);
    }

    @Override
    public int nextInt() {
	return this.generator.nextInt();
    }

    @Override
    public int nextInt(int bound) {
	return this.generator.nextInt(bound);
    }

    @Override
    public long nextLong() {
	return this.generator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
	return this.generator.nextBoolean();
    }

    @Override
    public float nextFloat() {
	return this.generator.nextFloat();
    }

    @Override
    public double nextDouble() {
	return this.generator.nextDouble();
    }

    @Override
    public double nextGaussian() {
	return this.generator.nextGaussian();
    }

    @Override
    public IntStream ints(long streamSize) {
	return this.generator.ints(streamSize);
    }

    @Override
    public IntStream ints() {
	return this.generator.ints();
    }

    @Override
    public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
	return this.generator.ints(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
	return this.generator.ints(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long streamSize) {
	return this.generator.longs(streamSize);
    }

    @Override
    public LongStream longs() {
	return this.generator.longs();
    }

    @Override
    public LongStream longs(long streamSize, long randomNumberOrigin, long randomNumberBound) {
	return this.generator.longs(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
	return this.generator.longs(randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(long streamSize) {
	return this.generator.doubles(streamSize);
    }

    @Override
    public DoubleStream doubles() {
	return this.generator.doubles();
    }

    @Override
    public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
	return this.generator.doubles(streamSize, randomNumberOrigin, randomNumberBound);
    }

    @Override
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
	return this.generator.doubles(randomNumberOrigin, randomNumberBound);
    }
}
