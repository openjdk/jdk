/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark measuring cold-start of reflective method invocation.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 30, warmups = 10)
public class ReflectionColdstartBenchmark {

    static class Nested {
        static Object m00(Object p) {return p;}

        static Object m01(Object p) {return p;}

        static Object m02(Object p) {return p;}

        static Object m03(Object p) {return p;}

        static Object m04(Object p) {return p;}

        static Object m05(Object p) {return p;}

        static Object m06(Object p) {return p;}

        static Object m07(Object p) {return p;}

        static Object m08(Object p) {return p;}

        static Object m09(Object p) {return p;}

        static Object m0A(Object p) {return p;}

        static Object m0B(Object p) {return p;}

        static Object m0C(Object p) {return p;}

        static Object m0D(Object p) {return p;}

        static Object m0E(Object p) {return p;}

        static Object m0F(Object p) {return p;}

        static Object m10(Object p) {return p;}

        static Object m11(Object p) {return p;}

        static Object m12(Object p) {return p;}

        static Object m13(Object p) {return p;}

        static Object m14(Object p) {return p;}

        static Object m15(Object p) {return p;}

        static Object m16(Object p) {return p;}

        static Object m17(Object p) {return p;}

        static Object m18(Object p) {return p;}

        static Object m19(Object p) {return p;}

        static Object m1A(Object p) {return p;}

        static Object m1B(Object p) {return p;}

        static Object m1C(Object p) {return p;}

        static Object m1D(Object p) {return p;}

        static Object m1E(Object p) {return p;}

        static Object m1F(Object p) {return p;}
    }

    private Method[] methods;
    private Object arg;

    @Setup(Level.Trial)
    public void setup() {
        methods = Nested.class.getDeclaredMethods();
        arg = new Object();
    }

    @Benchmark
    public void invokeMethods(Blackhole bh) throws ReflectiveOperationException {
        for (Method m : methods) {
            bh.consume(m.invoke(null, arg));
        }
    }
}
