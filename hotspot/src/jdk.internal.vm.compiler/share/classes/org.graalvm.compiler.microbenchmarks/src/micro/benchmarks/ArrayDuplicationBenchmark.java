/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.util.Arrays;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark;

@State(Scope.Thread)
public class ArrayDuplicationBenchmark extends GraalBenchmark {

    /** How large should the test-arrays be. */
    private static final int TESTSIZE = 300;

    private Object[][] testObjectArray;

    private Object[] dummy;

    @Setup
    public void setup() {
        testObjectArray = new Object[TESTSIZE][];
        for (int i = 0; i < TESTSIZE; i++) {
            testObjectArray[i] = new Object[20];
        }
    }

    @Setup(Level.Iteration)
    public void iterationSetup() {
        dummy = new Object[TESTSIZE * 3];
    }

    @TearDown(Level.Iteration)
    public void iterationTearDown() {
        dummy = null;
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] normalArraycopy() {
        int j = 0;
        for (int i = 0; i < TESTSIZE; i++) {
            dummy[j++] = normalArraycopy(testObjectArray[i]);
        }
        return dummy;
    }

    public Object[] normalArraycopy(Object[] cache) {
        Object[] result = new Object[cache.length];
        System.arraycopy(cache, 0, result, 0, result.length);
        return result;
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] arraysCopyOf() {
        int j = 0;
        for (int i = 0; i < TESTSIZE; i++) {
            dummy[j++] = arraysCopyOf(testObjectArray[i]);
        }
        return dummy;
    }

    public Object[] arraysCopyOf(Object[] cache) {
        return Arrays.copyOf(cache, cache.length);
    }

    @Benchmark
    @OperationsPerInvocation(TESTSIZE)
    public Object[] cloneObjectArray() {
        int j = 0;
        for (int i = 0; i < TESTSIZE; i++) {
            dummy[j++] = arraysClone(testObjectArray[i]);
        }
        return dummy;
    }

    @SuppressWarnings("cast")
    public Object[] arraysClone(Object[] cache) {
        return (Object[]) cache.clone();
    }

}
