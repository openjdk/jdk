/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.invoke;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

/**
 * Assesses general MethodType performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class MethodTypeAcquire {

    private MethodType pTypes;

    private Class<?> objectType = Object.class;

    private Class<?> otherType = A.class;
    @Setup
    public void setup() {
        pTypes = MethodType.methodType(A.class, B.class);
    }

    @Benchmark
    public MethodType testReturnVoid() {
        return MethodType.methodType(void.class);
    }

    @Benchmark
    public MethodType testReturnInt() {
        return MethodType.methodType(int.class);
    }

    @Benchmark
    public MethodType testReturnObject() {
        return MethodType.methodType(Object.class);
    }

    @Benchmark
    public MethodType testGenericObject() {
        return MethodType.genericMethodType(1);
    }

    @Benchmark
    public MethodType testObjectObject() {
        return MethodType.methodType(Object.class, Object.class);
    }

    @Benchmark
    public MethodType testObjectObject_NonConst() {
        return MethodType.methodType(objectType, objectType);
    }

    @Benchmark
    public MethodType testSinglePType() {
        return MethodType.methodType(void.class, int.class);
    }

    @Benchmark
    public MethodType testMultiPType() {
        return MethodType.methodType(void.class, A.class, B.class);
    }

    @Benchmark
    public MethodType testMultiPType_ObjectAndA() {
        return MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class, A.class, B.class);
    }

    @Benchmark
    public MethodType testMultiPType_ObjectAndA_NonConst() {
        return MethodType.methodType(objectType, objectType, objectType, objectType, objectType, otherType, otherType);
    }

    @Benchmark
    public MethodType testMultiPType_ObjectOnly() {
        return MethodType.methodType(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class, Object.class);
    }

    @Benchmark
    public MethodType testMultiPType_ObjectOnly_NonConst() {
        return MethodType.methodType(objectType, objectType, objectType, objectType, objectType, objectType, objectType);
    }

    @Benchmark
    public MethodType testMultiPType_Arg() {
        return MethodType.methodType(void.class, pTypes);
    }

    public static class A {}
    public static class B {}

}
