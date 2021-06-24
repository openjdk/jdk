/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::equals performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class StringEquals {
    @Param({"8", "16", "32", "64", "128"})
    int size;

    public String test = new String("0123456789");
    public String test2 = new String("tgntogjnrognagronagroangroarngorngaorng");
    public String test3 = new String(test); // equal to test, but not same
    public String test4 = new String("0123\u01FF");
    public String test5 = new String(test4); // equal to test4, but not same
    public String test6 = new String("0123456780");
    public String test7 = new String("0123\u01FE");

    public String str;
    public String strh;
    public String strt;

    @Setup()
    public void init() {
        str = newString(size, 'c', -1, 'a');
        strh = newString(size, 'c', size / 3, 'a');
        strt = newString(size, 'c', size - 1 - size / 3, 'a');
    }

    public String newString(int size, char charToFill, int pos, char charDiff) {
        if (size > 0) {
            char[] array = new char[size];
            Arrays.fill(array, charToFill);
            if (pos >= 0) {
                array[pos] = charDiff;
            }
            return new String(array);
        }
        return "";
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean different_simple() {
        return test.equals(test2);
    }

    public boolean different() {
        return test.equals(test2);
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean equal_simple() {
        return test.equals(test3);
    }

    public boolean equal() {
        return test.equals(test3);
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean almostEqual_simple() {
        return test.equals(test6);
    }

    public boolean almostEqual() {
        return test.equals(test6);
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean almostEqualUTF16_simple() {
        return test4.equals(test7);
    }

    public boolean almostEqualUTF16() {
        return test4.equals(test7);
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean differentCoders_simple() {
        return test.equals(test4);
    }

    public boolean differentCoders() {
        return test.equals(test4);
    }

    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean equalsUTF16_simple() {
        return test5.equals(test4);
    }

    public boolean equalsUTF16() {
        return test5.equals(test4);
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean equalsLenH_simple() {
        return str.equals(strh);
    }

    @Benchmark
    public boolean equalsLenH() {
        return str.equals(strh);
    }

    @Benchmark
    @Fork(jvmArgsAppend = {"-XX:+UseSimpleStringEquals"})
    public boolean equalsLenT_simple() {
        return str.equals(strt);
    }

    @Benchmark
    public boolean equalsLenT() {
        return str.equals(strt);
    }
}