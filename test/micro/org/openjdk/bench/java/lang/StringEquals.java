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
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=3, time=1)
@Measurement(iterations=5, time=1)
@State(Scope.Benchmark)
@Fork(value=1)
public class StringEquals {
    @Param({"8", "11", "16", "22", "32", "45", "64", "91", "121", "181", "256", "512", "1024"})
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
    public String strDup;

    @Setup()
    public void init() {
        str = newString(size, 'c', -1, 'a');
        strh = newString(size, 'c', size / 3, 'a');
        strt = newString(size, 'c', size - 1 - size / 3, 'a');
        strDup = new String (str.toCharArray());
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

    public boolean different() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= test.equals(test2);
        }
        return result;
    }

    public boolean almostEqual() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= test.equals(test6);
        }
        return result;
    }

    public boolean almostEqualUTF16() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= test4.equals(test7);
        }
        return result;
    }

    public boolean differentCoders() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= test.equals(test4);
        }
        return result;
    }

    public boolean equalUTF16() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= test5.equals(test4);
        }
        return result;
    }

    public boolean equalDiffAtHead() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= str.equals(strh);
        }
        return result;
    }

    public boolean equalDiffAtTail() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= str.equals(strt);
        }
        return result;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean equal() {
        boolean result = false;
        for (int i = 0; i < 1000; i++) {
            result ^= str.equals(strDup);
        }
        return result;
    }
}

