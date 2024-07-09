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
public class StringConcatStartup {

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
    @Fork(value = 40, warmups = 2)
    public static class StringSingle {

        public String s = "foo";

        @Benchmark
        public String run() {
            return "" + s;
        }
    }


    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 20, warmups = 2)
    public static class MixedSmall {

        public String s = "foo";
        public int i = 17;
        public long l = 21L;
        public char c = 'a';
        public boolean z = true;

        @Benchmark
        public String run() {
            String concat;
            concat = "foo" + s + "bar" + i + "baz" + l + "bur" + c + "dub" + z + "foo";
            concat = "bar" + i + "baz" + l + c + "dub" + z + "foo";
            concat = "bar" + i + "baz" + l + "dub" + z;
            concat = s + "bar" + i + s + "bur" + c + "dub" + s + "foo";
            return concat;
        }
    }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 10, warmups = 2)
    public static class StringLarge {

        public String i = "1";
        public String l = "2";
        public String b = "3";
        public String s = "4";
        public String c = "5";
        public String S = "6";
        public String z = "7";
        public String f = "8";
        public String d = "9";

        @Benchmark
        public void run() {
            String concat;
            concat = "" + "S" + f + l + z + f + "S" + d + S + d + S;
            concat = "" + "S" + S + i + b + b + z + i + s + S + b + "S";
            concat = "" + S + f + f + f + b + f + "S" + S + S + i + b;
            concat = "" + b + l + i + l + b + S + i + i + f + z;
            concat = "" + f + z + d + b + "S" + c + S + f + s + s + d;
            concat = "" + f + b + d + d + l + s + s + b + l + c + z;
            concat = "" + S + z + l + s + s + i + f + c + i + i + d;
            concat = "" + b + "S" + c + d + "S" + d + s + "S" + f + c + l + "S" + i + z + d + "S";
            concat = "" + S + "S" + S + i + c + z + i + i + S + b;
            concat = "" + S + S + d + s + z + f + z + i + b + s + s + "S";
            concat = "" + i + z + f + d + f + S + c + "S" + i;
            concat = "" + c + c + c + "S" + S + l;
            concat = "" + z + d + s + i + l + i + z + c + i + f + l + s + b + S + S + s + z + "S" + c + z;
            concat = "" + d + b + l + S + s + b + "S" + c + d + c + c + l + d + S + b + l + b + S + d + "S";
            concat = "" + c + z + c + d + b + S + c + b + S + "S" + d + s + c + s + b + c + b + z + s + i;
            concat = "" + l + S + "S";
            concat = "" + s + i + f + S + f + i + s + d + S + l + i + "S" + i + S + d + i + l + c + i + d;
            concat = "" + S + l + s + i + b + f + z + c + S + d + s + f + l + i + s + b + f + s + d + l;
            concat = "" + i + d + b + d + S + b + d + "S" + "S" + i + l + i + b + "S" + "S" + s + "S" + i + b + c;
            concat = "" + "S" + l + "S" + s + d + l + i + l + z + s + i + z + b + b + c + S + d + d + s + i;
            concat = "" + b + c + i + b + z + d + z + z + d + z + l + b + z + f + b + c + d + c + z + c;
            concat = "" + b + z + f + b + z + f + s + z + f + "S" + l + f + l + z + b + z + i + l + i + S;
            concat = "" + c + b + "S" + z;
            concat = "" + b + "S" + i + "S" + S + i + l + c + i + c + z + z + d + "S" + z + z + c + z + z + i;
            concat = "" + f + c + c + "S" + c + s + i + z + b + s + f + b + i + i + z + f + d + f + i + i;
            concat = "" + d + s + z + l + s + d + S + i + S + s + i + c + b + c + s + "S" + d + S + f + s;
            concat = "" + S + f + s + z + d + d + S + s + s + z + f + z + "S" + i + d + d + S + c + S + "S";
            concat = "" + c + c + b + S + "S" + "S" + d + S + s + b + c + d + z + c + b + i + S + z + i + s;
            concat = "" + l + l + d + z + s + s + i + i + l + c + f + z + i + f + l + z + s + d + f + l;
            concat = "" + f + d + "S" + s;
            concat = "" + d + S + "S" + S + f + "S" + c + i + s + b + c + b + l + f + S + c + c + i + z + s;
            concat = "" + z + "S" + s + S + s + d + d + s + f + "S" + f + "S" + i + S + "S" + c + l + b + f + f;
            concat = "" + l + f + d + b + s + f + d + "S" + l + s + "S" + b + b + s + S + S + "S" + "S" + d + b;
            concat = "" + b + l + f + b + S + f + z + s + S + f + b + b + s + s + b + s + l + d + l;
            concat = "" + b + b + S + S + S + z + z + d + "S" + l + "S" + s + i + "S" + c + f + S + f + i;
            concat = "" + l + l + f + i + S + s + "S" + "S" + z + d + "S" + l + d + b + f + f + l + b + b;
            concat = "" + l + f + "S" + f + f + i + l + l + i + S + b + f + d + i + c + c + d + d + i;
            concat = "" + l + b + s + d + i + i + d + c + "S" + s + f + d + z + d + S + c;
            concat = "" + f + s + "S" + z + s + "S" + b + b + b + d + d + b + z + l + c + b;
            concat = "" + l + d + "S" + b + z + z + f + c + z + c + c + c + c + d;
            concat = "" + z + d + l + "S" + i + s + b + b + d + s + s;
            concat = "" + f + i + d + S + f + f + i + s + d + S + c + l + d + s + c + i;
            concat = "" + f + c + i + "S" + "S" + c + f + b + l + i + s + c + i + S + S + i;
            concat = "" + z + S + z + d + d + S + "S" + f + d + s + s + "S" + l + z + l + c;
            concat = "" + b + c + s + f + S + l + b + f + "S" + l + "S" + c + c + z + b + b;
            concat = "" + c + b + z + s + d + l + l + S + l + "S" + f + S + c + f + s + f;
            concat = "" + z + z + d + i + z + s + z + S + f + S + "S" + "S" + l + d + c + d;
            concat = "" + c + S + s + f + c + i + b + l + S + c + l + f + f + l + i + l;
            concat = "" + "S" + i + f + d + s + S + S + l + s + S + l + "S" + b + l + s + l + d + d + f + S;
            concat = "" + l + z + c + l + f + f + d + s + l + b + d + f + S + S + "S" + i + i + s + f + i;
            concat = "" + S + S + l + S + z + d + s + c + "S" + d + f + d + f + f + z + i + f + l + S + s;
            concat = "" + z + d + z + l + f + s + d + z + i + S + S + d + i + z + c + i + i + f + b + "S";
            concat = "" + b + d + "S" + f + f + d + s + i + b + l + i + b + f + f + b + f + l + i + z + l;
            concat = "" + c + z + s + "S" + z + f + "S" + i + f + s + l + i + "S" + d + i + b + i + S + b + l;
            concat = "" + d + l + s + c + l + d + "S" + "S" + s + S + f + z + b + s + b + f + z + z + l + l;
            concat = "" + f + b + "S" + s + i + "S" + s + f + c + f + c + f + i + i + b + i + i + b + S + S;
            concat = "" + i + i + s + i + s + S + s + "S" + c + c + f + s + d + l + l + d + f + l + i + S;
            concat = "" + z + d + z + "S" + c + i + f + s + b + S + i + c + s + b + c + f + s + z + f + c;
            concat = "" + f + s + f + b + l + z + f + f + f + c + z + S + b + s + z + i + s + S + i + b;
            concat = "" + d + i + S + b + i + "S" + l + S + S + S + z + i + z + b;
            concat = "" + "S" + S + s + l + f + i + l + b + f + S + d + c + b + d;
            concat = "" + c + i + i + d + S + z + c + i + c + S + f + i + c + c;
            concat = "" + "S" + "S" + c + d + z + l + d + z + f + b + d + z + S + f;
            concat = "" + b + d + z + d + i + z + d + b + d + "S" + c + f + d;
            concat = "" + d + s + f + c + i + "S" + b + b + S + i + s + d + "S" + f;
            concat = "" + l + S + d + b + S + s + "S" + s + s + l + S + "S" + c + d;
            concat = "" + c + s + z + c + S + S + "S" + l + S + f + f + c + S + f;
            concat = "" + d + i + s + c + z + "S" + d + f + "S" + S + c + b + "S" + c;
            concat = "" + i + b + "S" + l + S + d + "S" + c + b + s + f + l + f + "S";
            concat = "" + c + b + f + "S" + S + s + i + l + s + z + z + f + l + b;
            concat = "" + S + s + "S" + d + s + z + "S" + i + i + z + S + b + f + i;
            concat = "" + z + S + S + "S" + S + S + z + b + S + z + b + f + s + l;
            concat = "" + s + z + d + "S" + z + l + f + z + s + z + d + l + s + l;
            concat = "" + l + d + i + s + i + c + i + f + b + f + s + b + s + s;
            concat = "" + z + "S" + S + "S" + "S" + i + "S" + s + d + z + l;
            concat = "" + i + S + S + "S" + f + "S" + "S" + z + S + z + b + z + c + b;
            concat = "" + i + f + f + d + z + f + z + b + "S" + c + l + l + z + s + S + s;
            concat = "" + b + b + z + "S" + f + s + "S" + l +c + S + i + i + b + "S" + S;
            concat = "" + i + "S" + d + d + d + "S" + f + "S" + b + s + S + i + "S" + d + b;
            concat = "" + s + f + b + d + c + d + c + S + S + b + i + b + z + c;
            concat = "" + l + l + S + l + f + s + i + c + z + f + d + l + f + b + l + f + f + i + i + z;
            concat = "" + l + l + l + l + s + s + f + i + i + f + z + c + S + s + f + "S" + "S" + s + z + s;
            concat = "" + S + z + f + b + l + c + i + l;
            concat = "" + c + z + b + f + i + i + f + d + f + f + d + d + l + d + S + "S" + i + c + b + f;
            concat = "" + s + d + S + d + b + l + l + f + b + "S" + i + z + b + S + S + c + S + f + S + z;
            concat = "" + l + S + S + i + l + s + d + f + z + i + "S" + b + f + c + z + c + S + c + i + s;
            concat = "" + l + S + S + s + f + S + s + "S" + c + c + c;
            concat = "" + s + "S" + c + d + z + c + l + c + z + S + i + f + c + c + s + "S" + S + z + s + "S";
            concat = "" + c + i + z + s + b + s + s + b + "S" + d + "S" + z + f + "S" + c + S + s + S + b + i;
            concat = "" + s + c + d + d + "S" + "S" + l + s + i + l + l + f + S + f + f + i + S + d + l + c;
            concat = "" + "S" + S + b + c + i + "S" + c + c + s + i + "S" + b + i + b + b + S + f + l + s + "S";
            concat = "" + l + l + b + f + i + i + f + z + c + S + b + f + z + "S" + s + z + "S" + f + S + s;
            concat = "" + i + c + b + i + b + z + "S" + i + c + i + l + "S" + z + b + b + i + i + c + i + f;
            concat = "" + "S" + c + d + z + d + f + c + c + b + "S" + l + f + d + "S" + s + s + S + i + s + i;
            concat = "" + S + "S" + d + c + "S" + S + "S" + b + f + z + "S" + l + d + f + "S" + S + d + b + c + c;
            concat = "" + f + S + l + s + l + z + S + d + S + b + f + c + s + b + "S" + z + "S" + "S" + b + z;
            concat = "" + f + s + c + i + S + b + s + S + i + S + c + b + s + d + i + "S" + s + l + c + s;
            concat = "" + l + f + s + b + d + b + i + c + c + b + s + f + i + z + s + i + s + "S" + l + z;
            concat = "" + d + z + z + c + b + b + s + b + S + l + d + i + S + d + "S" + i + S + i + b + S;
            concat = "" + c + d + "S" + f + i + b + d + c + z + f + "S" + i + d + b + f + s + "S" + c + S + i;
            concat = "" + i + z + "S" + b + S + s + c + s + f + S + S + f + z + s + b + d + z + i + s + z;
            concat = "" + z + s + z + l + "S" + S + s + "S" + i + b + c + s + l + l + s + i + c + i + i + d;
            concat = "" + "S" + b + l + z + c + f + l + S + "S" + l + i + z + z + l + S + "S" + z + S + z + c + "S";
            concat = "" + "S" + f + S + i + i + i + "S" + i + i + l + c + l + S + S + z + b + i + c + f + S;
            concat = "" + c + z + S + S + b + i + c;
            concat = "" + S + s + S + c;
        }
    }

    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Fork(value = 10, warmups = 2)
    public static class MixedLarge {

        public int i = 17;
        public long l = 21L;
        public byte b = (byte)17;
        public short s = (short)17;
        public char c = 'a';
        public String S = "S";
        public float f = 1.0f;
        public double d = 2.0;
        public boolean z = true;

        @Benchmark
        public void run() {
            String concat;
            concat = "" + "S" + f + l + z + f + "S" + d + S + d + S;
            concat = "" + "S" + S + i + b + b + z + i + s + S + b + "S";
            concat = "" + S + f + f + f + b + f + "S" + S + S + i + b;
            concat = "" + b + l + i + l + b + S + i + i + f + z;
            concat = "" + f + z + d + b + "S" + c + S + f + s + s + d;
            concat = "" + f + b + d + d + l + s + s + b + l + c + z;
            concat = "" + S + z + l + s + s + i + f + c + i + i + d;
            concat = "" + b + "S" + c + d + "S" + d + s + "S" + f + c + l + "S" + i + z + d + "S";
            concat = "" + S + "S" + S + i + c + z + i + i + S + b;
            concat = "" + S + S + d + s + z + f + z + i + b + s + s + "S";
            concat = "" + i + z + f + d + f + S + c + "S" + i;
            concat = "" + c + c + c + "S" + S + l;
            concat = "" + z + d + s + i + l + i + z + c + i + f + l + s + b + S + S + s + z + "S" + c + z;
            concat = "" + d + b + l + S + s + b + "S" + c + d + c + c + l + d + S + b + l + b + S + d + "S";
            concat = "" + c + z + c + d + b + S + c + b + S + "S" + d + s + c + s + b + c + b + z + s + i;
            concat = "" + l + S + "S";
            concat = "" + s + i + f + S + f + i + s + d + S + l + i + "S" + i + S + d + i + l + c + i + d;
            concat = "" + S + l + s + i + b + f + z + c + S + d + s + f + l + i + s + b + f + s + d + l;
            concat = "" + i + d + b + d + S + b + d + "S" + "S" + i + l + i + b + "S" + "S" + s + "S" + i + b + c;
            concat = "" + "S" + l + "S" + s + d + l + i + l + z + s + i + z + b + b + c + S + d + d + s + i;
            concat = "" + b + c + i + b + z + d + z + z + d + z + l + b + z + f + b + c + d + c + z + c;
            concat = "" + b + z + f + b + z + f + s + z + f + "S" + l + f + l + z + b + z + i + l + i + S;
            concat = "" + c + b + "S" + z;
            concat = "" + b + "S" + i + "S" + S + i + l + c + i + c + z + z + d + "S" + z + z + c + z + z + i;
            concat = "" + f + c + c + "S" + c + s + i + z + b + s + f + b + i + i + z + f + d + f + i + i;
            concat = "" + d + s + z + l + s + d + S + i + S + s + i + c + b + c + s + "S" + d + S + f + s;
            concat = "" + S + f + s + z + d + d + S + s + s + z + f + z + "S" + i + d + d + S + c + S + "S";
            concat = "" + c + c + b + S + "S" + "S" + d + S + s + b + c + d + z + c + b + i + S + z + i + s;
            concat = "" + l + l + d + z + s + s + i + i + l + c + f + z + i + f + l + z + s + d + f + l;
            concat = "" + f + d + "S" + s;
            concat = "" + d + S + "S" + S + f + "S" + c + i + s + b + c + b + l + f + S + c + c + i + z + s;
            concat = "" + z + "S" + s + S + s + d + d + s + f + "S" + f + "S" + i + S + "S" + c + l + b + f + f;
            concat = "" + l + f + d + b + s + f + d + "S" + l + s + "S" + b + b + s + S + S + "S" + "S" + d + b;
            concat = "" + b + l + f + b + S + f + z + s + S + f + b + b + s + s + b + s + l + d + l;
            concat = "" + b + b + S + S + S + z + z + d + "S" + l + "S" + s + i + "S" + c + f + S + f + i;
            concat = "" + l + l + f + i + S + s + "S" + "S" + z + d + "S" + l + d + b + f + f + l + b + b;
            concat = "" + l + f + "S" + f + f + i + l + l + i + S + b + f + d + i + c + c + d + d + i;
            concat = "" + l + b + s + d + i + i + d + c + "S" + s + f + d + z + d + S + c;
            concat = "" + f + s + "S" + z + s + "S" + b + b + b + d + d + b + z + l + c + b;
            concat = "" + l + d + "S" + b + z + z + f + c + z + c + c + c + c + d;
            concat = "" + z + d + l + "S" + i + s + b + b + d + s + s;
            concat = "" + f + i + d + S + f + f + i + s + d + S + c + l + d + s + c + i;
            concat = "" + f + c + i + "S" + "S" + c + f + b + l + i + s + c + i + S + S + i;
            concat = "" + z + S + z + d + d + S + "S" + f + d + s + s + "S" + l + z + l + c;
            concat = "" + b + c + s + f + S + l + b + f + "S" + l + "S" + c + c + z + b + b;
            concat = "" + c + b + z + s + d + l + l + S + l + "S" + f + S + c + f + s + f;
            concat = "" + z + z + d + i + z + s + z + S + f + S + "S" + "S" + l + d + c + d;
            concat = "" + c + S + s + f + c + i + b + l + S + c + l + f + f + l + i + l;
            concat = "" + "S" + i + f + d + s + S + S + l + s + S + l + "S" + b + l + s + l + d + d + f + S;
            concat = "" + l + z + c + l + f + f + d + s + l + b + d + f + S + S + "S" + i + i + s + f + i;
            concat = "" + S + S + l + S + z + d + s + c + "S" + d + f + d + f + f + z + i + f + l + S + s;
            concat = "" + z + d + z + l + f + s + d + z + i + S + S + d + i + z + c + i + i + f + b + "S";
            concat = "" + b + d + "S" + f + f + d + s + i + b + l + i + b + f + f + b + f + l + i + z + l;
            concat = "" + c + z + s + "S" + z + f + "S" + i + f + s + l + i + "S" + d + i + b + i + S + b + l;
            concat = "" + d + l + s + c + l + d + "S" + "S" + s + S + f + z + b + s + b + f + z + z + l + l;
            concat = "" + f + b + "S" + s + i + "S" + s + f + c + f + c + f + i + i + b + i + i + b + S + S;
            concat = "" + i + i + s + i + s + S + s + "S" + c + c + f + s + d + l + l + d + f + l + i + S;
            concat = "" + z + d + z + "S" + c + i + f + s + b + S + i + c + s + b + c + f + s + z + f + c;
            concat = "" + f + s + f + b + l + z + f + f + f + c + z + S + b + s + z + i + s + S + i + b;
            concat = "" + d + i + S + b + i + "S" + l + S + S + S + z + i + z + b;
            concat = "" + "S" + S + s + l + f + i + l + b + f + S + d + c + b + d;
            concat = "" + c + i + i + d + S + z + c + i + c + S + f + i + c + c;
            concat = "" + "S" + "S" + c + d + z + l + d + z + f + b + d + z + S + f;
            concat = "" + b + d + z + d + i + z + d + b + d + "S" + c + f + d;
            concat = "" + d + s + f + c + i + "S" + b + b + S + i + s + d + "S" + f;
            concat = "" + l + S + d + b + S + s + "S" + s + s + l + S + "S" + c + d;
            concat = "" + c + s + z + c + S + S + "S" + l + S + f + f + c + S + f;
            concat = "" + d + i + s + c + z + "S" + d + f + "S" + S + c + b + "S" + c;
            concat = "" + i + b + "S" + l + S + d + "S" + c + b + s + f + l + f + "S";
            concat = "" + c + b + f + "S" + S + s + i + l + s + z + z + f + l + b;
            concat = "" + S + s + "S" + d + s + z + "S" + i + i + z + S + b + f + i;
            concat = "" + z + S + S + "S" + S + S + z + b + S + z + b + f + s + l;
            concat = "" + s + z + d + "S" + z + l + f + z + s + z + d + l + s + l;
            concat = "" + l + d + i + s + i + c + i + f + b + f + s + b + s + s;
            concat = "" + z + "S" + S + "S" + "S" + i + "S" + s + d + z + l;
            concat = "" + i + S + S + "S" + f + "S" + "S" + z + S + z + b + z + c + b;
            concat = "" + i + f + f + d + z + f + z + b + "S" + c + l + l + z + s + S + s;
            concat = "" + b + b + z + "S" + f + s + "S" + l +c + S + i + i + b + "S" + S;
            concat = "" + i + "S" + d + d + d + "S" + f + "S" + b + s + S + i + "S" + d + b;
            concat = "" + s + f + b + d + c + d + c + S + S + b + i + b + z + c;
            concat = "" + l + l + S + l + f + s + i + c + z + f + d + l + f + b + l + f + f + i + i + z;
            concat = "" + l + l + l + l + s + s + f + i + i + f + z + c + S + s + f + "S" + "S" + s + z + s;
            concat = "" + S + z + f + b + l + c + i + l;
            concat = "" + c + z + b + f + i + i + f + d + f + f + d + d + l + d + S + "S" + i + c + b + f;
            concat = "" + s + d + S + d + b + l + l + f + b + "S" + i + z + b + S + S + c + S + f + S + z;
            concat = "" + l + S + S + i + l + s + d + f + z + i + "S" + b + f + c + z + c + S + c + i + s;
            concat = "" + l + S + S + s + f + S + s + "S" + c + c + c;
            concat = "" + s + "S" + c + d + z + c + l + c + z + S + i + f + c + c + s + "S" + S + z + s + "S";
            concat = "" + c + i + z + s + b + s + s + b + "S" + d + "S" + z + f + "S" + c + S + s + S + b + i;
            concat = "" + s + c + d + d + "S" + "S" + l + s + i + l + l + f + S + f + f + i + S + d + l + c;
            concat = "" + "S" + S + b + c + i + "S" + c + c + s + i + "S" + b + i + b + b + S + f + l + s + "S";
            concat = "" + l + l + b + f + i + i + f + z + c + S + b + f + z + "S" + s + z + "S" + f + S + s;
            concat = "" + i + c + b + i + b + z + "S" + i + c + i + l + "S" + z + b + b + i + i + c + i + f;
            concat = "" + "S" + c + d + z + d + f + c + c + b + "S" + l + f + d + "S" + s + s + S + i + s + i;
            concat = "" + S + "S" + d + c + "S" + S + "S" + b + f + z + "S" + l + d + f + "S" + S + d + b + c + c;
            concat = "" + f + S + l + s + l + z + S + d + S + b + f + c + s + b + "S" + z + "S" + "S" + b + z;
            concat = "" + f + s + c + i + S + b + s + S + i + S + c + b + s + d + i + "S" + s + l + c + s;
            concat = "" + l + f + s + b + d + b + i + c + c + b + s + f + i + z + s + i + s + "S" + l + z;
            concat = "" + d + z + z + c + b + b + s + b + S + l + d + i + S + d + "S" + i + S + i + b + S;
            concat = "" + c + d + "S" + f + i + b + d + c + z + f + "S" + i + d + b + f + s + "S" + c + S + i;
            concat = "" + i + z + "S" + b + S + s + c + s + f + S + S + f + z + s + b + d + z + i + s + z;
            concat = "" + z + s + z + l + "S" + S + s + "S" + i + b + c + s + l + l + s + i + c + i + i + d;
            concat = "" + "S" + b + l + z + c + f + l + S + "S" + l + i + z + z + l + S + "S" + z + S + z + c + "S";
            concat = "" + "S" + f + S + i + i + i + "S" + i + i + l + c + l + S + S + z + b + i + c + f + S;
            concat = "" + c + z + S + S + b + i + c;
            concat = "" + S + s + S + c;
        }
    }
}
