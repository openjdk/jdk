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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

/**
 * Benchmarks stressing String concat startup. Provides a main method that takes names of the sub-benchmarks
 * of choice as arguments to work well as a standalone startup test/diagnostic
 *
 *   StringSingle
 *   MixedSmall - small number of mixed expressions
 *   StringLarge - large number of expressions with a mix of String arguments and constants
 *   MixedLarge - large number of expressions with a mix of constants, Strings and primivitive arguments
 */
public class StringConcatStartupGenerate {

    public static void main(String... args) {
        String[] selection = new String[] { "StringLarge", "MixedSmall", "StringSingle", "MixedLarge" };
        if (args.length > 0) {
            selection = args;
        }
        for (String select : selection) {
            switch (select) {
                case "StringSingle" -> new StringSingle().run();
                case "MixedSmall" -> new MixedSmall().run();
                case "StringLarge" -> new StringLarge().run();
                case "MixedLarge" -> new MixedLarge().run();
            }
        }
    }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 40, warmups = 2, jvmArgsAppend = "-Djava.lang.invoke.StringConcat.highArityThreshold=0")
    public static class StringSingle extends StringConcatStartup.StringSingle { }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 20, warmups = 2, jvmArgsAppend = "-Djava.lang.invoke.StringConcat.highArityThreshold=0")
    public static class MixedSmall extends StringConcatStartup.MixedSmall { }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 10, warmups = 2, jvmArgsAppend = "-Djava.lang.invoke.StringConcat.highArityThreshold=0")
    public static class StringLarge extends StringConcatStartup.StringLarge { }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 10, warmups = 2, jvmArgsAppend = "-Djava.lang.invoke.StringConcat.highArityThreshold=0")
    public static class MixedLarge extends StringConcatStartup.MixedLarge { }
}
