/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2026 Arm Limited and/or its affiliates.
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

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::equals performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringEquals {

    public String test = new String("0123456789");
    public String test2 = new String("tgntogjnrognagronagroangroarngorngaorng");
    public String test3 = new String(test); // equal to test, but not same
    public String test4 = new String("0123\u01FF");
    public String test5 = new String(test4); // equal to test4, but not same
    public String test6 = new String("0123456780");
    public String test7 = new String("0123\u01FE");
    // string with parameterizable size
    public String test8;
    // same chars as test8, but different object; forces the intrinsic to read
    // the entire string to check equality
    public String test9;
    // same chars as test8, except at length + diff_pos; set diff_pos to the
    // worst case for the intrinsic being tested (usually -1, but could be -9
    // if the intrinsic reads the last 8B first, or -length if the intrinsic
    // reads the string backwards
    public String test10;

    @Param({"30"})  // can be used at runtime to define a length sweep
    public int size;

    @Param({"-1"})  // set to the worst location for the intrinsic under test
    public int diff_pos;

    @Setup
    public void setup() {
        if (size > 0) {
            test8 = "a".repeat(size);
            // NOTE 1: can't do test9 = new String(test8) or they'll share byte
            // arrays, which improves cache hit rate of the equal-string case
            test9 = "a".repeat(size);
            StringBuilder sb = new StringBuilder("a".repeat(size));
            sb.setCharAt(Math.max(test8.length() + diff_pos, 0), 'b');
            test10 = sb.toString();
        }
        else {
            // NOTE 2: can't use "a".repeat(0) or it returns the "" literal,
            // which will early-exit from String.equals()
            // NOTE 3: can't use no-arg String ctor or they'll share the byte
            // array of the "" literal, which improves cache hit rate for
            // intrinsics that read backwards into the object header
            test8 = new String(new char [] {});
            test9 = new String(new char [] {});
            test10 = new String(new char [] {});
        }
    }

    @Benchmark
    public boolean different() {
        return test.equals(test2);
    }

    @Benchmark
    public boolean equal() {
        return test.equals(test3);
    }

    @Benchmark
    public boolean differentParam() {
        return test8.equals(test10);
    }

    @Benchmark
    public boolean equalParam() {
        return test8.equals(test9);
    }

    @Benchmark
    public boolean almostEqual() {
        return test.equals(test6);
    }

    @Benchmark
    public boolean almostEqualUTF16() {
        return test4.equals(test7);
    }

    @Benchmark
    public boolean differentCoders() {
        return test.equals(test4);
    }

    @Benchmark
    public boolean equalsUTF16() {
        return test5.equals(test4);
    }
}

