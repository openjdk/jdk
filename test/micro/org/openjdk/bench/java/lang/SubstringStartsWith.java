/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 25, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class SubstringStartsWith {
    // model substrings in 3 representative lengths.
    // 1. small size = 4 for a variable name
    // 2. medium size = 24 or url or a filepath
    // 3. long string = 256 for a human-readable message
    @Param({"4", "24", "256"})
    private int substrLength;
    private String sample_ascii;
    private String sample_utf16;
    private String prefix_ascii;
    private String prefix_utf16;

    @Setup(Level.Trial)
    public void doSetup() {
       StringBuilder sb = new StringBuilder();
       String tile = "abcdef";
       for (int i=0; i<512 * 2; i = i + tile.length()) {
           sb.append(tile);
       }
       sample_ascii = sb.toString();
       prefix_ascii = sample_ascii.substring(0, 2);

       sb = new StringBuilder();
       tile = "\u4F60\u597D\u3088\u3046\u3053\u305DJava";
       for (int i=0; i<512 * 2; i = i + tile.length()) {
           sb.append(tile);
       }
       sample_utf16 = sb.toString();
       prefix_utf16 = sample_utf16.substring(0, 2);
    }

    boolean substr2StartsWith(String base, String prefix) {
        return base.substring(1, 1 + substrLength).startsWith(prefix);
    }

    boolean substr2StartsWith_noalloc(String base, String prefix) {
        //boundary check as same as java.lang.String::checkBoundsBeginEnd
        int begin = 1;
        int end = begin + substrLength;
        if (begin < 0 || begin > end || end > base.length()) {
            throw new StringIndexOutOfBoundsException(
                "begin " + begin + ", end " + end + ", length " + base.length());
        }

        return base.startsWith(prefix, begin);
    }

    @Benchmark
    public boolean substr2StartsWith_singleByte() {
        return substr2StartsWith(sample_ascii, prefix_ascii);
    }

    @Benchmark
    public boolean substr2StartsWith_noalloc_singleByte() {
        return substr2StartsWith_noalloc(sample_ascii, prefix_ascii);
    }

    @Benchmark
    public boolean substr2StartsWith_doubleBytes() {
        return substr2StartsWith(sample_utf16, prefix_utf16);
    }

    @Benchmark
    public boolean substr2StartsWith_noalloc_doubleBytes() {
        return substr2StartsWith_noalloc(sample_utf16, prefix_utf16);
    }
}
