/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.util.concurrent.TimeUnit;

/**
 * Check StringConcatFactory bootstrap overheads
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 2000, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class StringConcatFactoryBootstraps {

    public MethodType mt1 =
            MethodType.methodType(String.class, String.class, int.class,
                                  String.class, String.class);
    public String recipe1 = "test\u0001foo\u0001bar\u0001baz\u0001";

    public MethodType mt2 =
            MethodType.methodType(String.class, String.class, String.class);
    public String recipe2 = "test\u0001foo\u0001";

    public MethodType mt3 =
            MethodType.methodType(String.class, String.class, String.class, String.class,
                                  String.class, String.class, String.class);
    public String recipe3 = "\u0001test\u0001foo\u0001bar\u0001baz\u0001\u0001";

    public MethodHandles.Lookup lookup;

    @Setup
    public void setup() {
        try {
            lookup = MethodHandles.privateLookupIn(this.getClass(), MethodHandles.lookup());
        } catch (Exception e) {}
    }

    @Benchmark
    public void makeConcatWithConstants(Blackhole bh) throws Throwable {
        bh.consume(StringConcatFactory.makeConcatWithConstants(lookup, "dummy", mt1, recipe1));
        bh.consume(StringConcatFactory.makeConcatWithConstants(lookup, "dummy", mt2, recipe2));
        bh.consume(StringConcatFactory.makeConcatWithConstants(lookup, "dummy", mt3, recipe3));
    }
}
