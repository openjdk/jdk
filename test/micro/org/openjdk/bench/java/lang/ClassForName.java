/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

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

import java.util.concurrent.TimeUnit;

/**
 * Tests java.lang.Class.forName() with various inputs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class ClassForName {

    private String aName, bName, cName;

    @Setup
    public void setup() {
        aName = A.class.getName();
        bName = B.class.getName();
        cName = C.class.getName();
    }

    /** Calls Class.forName with the same name over and over again. The class asked for exists. */
    @Benchmark
    public Class<?> forNameSingle() throws ClassNotFoundException {
        return Class.forName(aName);
    }

    @Benchmark
    public void forNameWithModule(Blackhole bh) throws ClassNotFoundException {
        bh.consume(Class.forName(getClass().getModule(), "java.util.Spliterator"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Random"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Arrays"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.String"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringLatin1"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringUTF16"));
    }

    @Benchmark
    public void forNameWithModuleMany(Blackhole bh) throws ClassNotFoundException {
        bh.consume(Class.forName(getClass().getModule(), "java.util.Spliterator"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Random"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Arrays"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.String"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringLatin1"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringUTF16"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.List"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Map"));
        bh.consume(Class.forName(getClass().getModule(), "java.util.Set"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Class"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Float"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Integer"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Double"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Boolean"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.System"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.Long"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringBuilder"));
        bh.consume(Class.forName(getClass().getModule(), "java.lang.StringBuffer"));
    }

    /** Calls Class.forName with the three different names over and over again. All classes asked for exist. */
    @Benchmark
    public void forName(Blackhole bh) throws ClassNotFoundException {
        bh.consume(Class.forName(aName));
        bh.consume(Class.forName(bName));
        bh.consume(Class.forName(cName));
    }

    static class A {}
    static class B {}
    static class C {}
}
