/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package testlibrary_tests.generators.tests;

import compiler.lib.generators.RandomnessSource;

import java.util.ArrayDeque;
import java.util.Queue;

class MockRandomness implements RandomnessSource {
    private record Bounded<T>(T lo, T hi, T value) {}

    private final Queue<Long> unboundedLongQueue = new ArrayDeque<>();
    private final Queue<Integer> unboundedIntegerQueue = new ArrayDeque<>();
    private final Queue<Bounded<Long>> boundedLongQueue = new ArrayDeque<>();
    private final Queue<Bounded<Integer>> boundedIntegerQueue = new ArrayDeque<>();
    private final Queue<Bounded<Double>> boundedDoubleQueue = new ArrayDeque<>();
    private final Queue<Bounded<Float>> boundedFloatQueue = new ArrayDeque<>();

    private <T> T dequeueBounded(Queue<Bounded<T>> queue, T lo, T hi) {
        Bounded<T> bounded = queue.remove();
        if (!bounded.lo.equals(lo) || !bounded.hi.equals(hi)) {
            throw new RuntimeException("Expected bounds " + bounded.lo + " and " + bounded.hi + " but found " + lo + " and " + hi);
        }
        return bounded.value;
    }

    private void checkQueueEmpty(Queue<?> queue, String name) {
        if (!queue.isEmpty()) throw new RuntimeException("Expected empty queue for " + name + " but found " + queue);
    }

    public MockRandomness enqueueLong(long value) {
        unboundedLongQueue.add(value);
        return this;
    }

    public MockRandomness enqueueInteger(int value) {
        unboundedIntegerQueue.add(value);
        return this;
    }

    public MockRandomness enqueueLong(long lo, long hi, long value) {
        boundedLongQueue.add(new Bounded<>(lo, hi, value));
        return this;
    }

    public MockRandomness enqueueInteger(int lo, int hi, int value) {
        boundedIntegerQueue.add(new Bounded<>(lo, hi, value));
        return this;
    }

    public MockRandomness enqueueDouble(double lo, double hi, double value) {
        boundedDoubleQueue.add(new Bounded<>(lo, hi, value));
        return this;
    }

    public MockRandomness enqueueFloat(float lo, float hi, float value) {
        boundedFloatQueue.add(new Bounded<>(lo, hi, value));
        return this;
    }

    public MockRandomness checkEmpty() {
        checkQueueEmpty(unboundedLongQueue, "unbounded longs");
        checkQueueEmpty(unboundedIntegerQueue, "unbounded integers");
        checkQueueEmpty(boundedLongQueue, "bounded longs");
        checkQueueEmpty(boundedIntegerQueue, "bounded integers");
        checkQueueEmpty(boundedDoubleQueue, "bounded doubles");
        checkQueueEmpty(boundedFloatQueue, "bounded floats");
        return this;
    }

    @Override
    public long nextLong() {
        return unboundedLongQueue.remove();
    }

    @Override
    public long nextLong(long lo, long hi) {
        return dequeueBounded(boundedLongQueue, lo, hi);
    }

    @Override
    public int nextInt() {
        return unboundedIntegerQueue.remove();
    }

    @Override
    public int nextInt(int lo, int hi) {
        return dequeueBounded(boundedIntegerQueue, lo, hi);
    }

    @Override
    public double nextDouble(double lo, double hi) {
        return dequeueBounded(boundedDoubleQueue, lo, hi);
    }

    @Override
    public float nextFloat(float lo, float hi) {
        return dequeueBounded(boundedFloatQueue, lo, hi);
    }
}
