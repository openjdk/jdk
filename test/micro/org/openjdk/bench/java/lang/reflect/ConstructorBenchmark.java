/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package org.openjdk.bench.java.lang.reflect;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark measuring the speed of Method/Constructor.getExceptionTypes() and
 * getParameterTypes(), in cases where the result array is length zero.
 */
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class ConstructorBenchmark {
    Constructor<?> emptyExceptionsConstructor;
    Constructor<?> emptyParametersConstructor;
    Constructor<?> oneExceptionConstructor;
    Constructor<?> oneParameterConstructor;

    public ConstructorBenchmark() {
        try {
            emptyParametersConstructor = Object.class.getConstructor();
            oneParameterConstructor = String.class.getConstructor(String.class);

            emptyExceptionsConstructor = emptyParametersConstructor;
            oneExceptionConstructor = String.class.getConstructor(byte[].class, String.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public Object[] getExceptionTypes() throws Exception {
        return oneExceptionConstructor.getExceptionTypes();
    }

    @Benchmark
    public Object[] getExceptionTypesEmpty() throws Exception {
        return emptyExceptionsConstructor.getExceptionTypes();
    }

    @Benchmark
    public Object[] getParameterTypes() throws Exception {
        return oneParameterConstructor.getParameterTypes();
    }

    @Benchmark
    public Object[] getParameterTypesEmpty() throws Exception {
        return emptyParametersConstructor.getParameterTypes();
    }
}
