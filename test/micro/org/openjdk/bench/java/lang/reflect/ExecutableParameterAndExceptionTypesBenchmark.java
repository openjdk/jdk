/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
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
import org.openjdk.jmh.infra.Blackhole;

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
public class ExecutableParameterAndExceptionTypesBenchmark {
    Executable objectConstructor;
    Executable hashCodeMethod;

    public ExecutableParameterAndExceptionTypesBenchmark() {
        try {
            hashCodeMethod = Object.class.getDeclaredMethod("hashCode");
            objectConstructor = Object.class.getConstructor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Benchmark
    public void constructorExceptionsWithNoExceptions(Blackhole bh) throws Exception {
        bh.consume(objectConstructor.getExceptionTypes());
    }

    @Benchmark
    public void constructorParametersWithNoExceptions(Blackhole bh) throws Exception {
        bh.consume(objectConstructor.getParameterTypes());
    }

    @Benchmark
    public void methodExceptionsWithNoExceptions(Blackhole bh) throws Exception {
        bh.consume(hashCodeMethod.getExceptionTypes());
    }

    @Benchmark
    public void methodParametersWithNoExceptions(Blackhole bh) throws Exception {
        bh.consume(hashCodeMethod.getParameterTypes());
    }
}
