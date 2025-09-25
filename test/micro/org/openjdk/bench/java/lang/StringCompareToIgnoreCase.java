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

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::compareToIgnoreCase performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class StringCompareToIgnoreCase {

    public String upper = "\u0100\u0102\u0104\u0106\u0108";
    public String upperLower = "\u0100\u0102\u0104\u0106\u0109";
    public String lower = "\u0101\u0103\u0105\u0107\u0109";
    public String supUpper = "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc04";
    public String supUpperLower = "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc2c";
    public String supLower = "\ud801\udc28\ud801\udc29\ud801\udc2a\ud801\udc2b\ud801\udc2c";

    public String asciiUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public String asciiUpperLower = "ABCDEFGHIJKLMNOpqrstuvwxyz";
    public String asciiLower = "abcdefghijklmnopqrstuvwxyz";

    public String greekUpper = "\u0391\u0392\u0393\u0394\u0395"; // ΑΒΓΔΕ
    public String greekUpperLower = "\u0391\u0392\u0393\u0394\u03B5"; // ΑΒΓΔε
    public String greekLower = "\u03B1\u03B2\u03B3\u03B4\u03B5"; // αβγδε

    public String asciiGreekUpper = "ABC\u0391\u0392\u0393"; // ABCΑΒΓ
    public String asciiGreekUpperLower = "ABC\u0391\u0392\u03B3"; // ABCΑΒγ
    public String asciiGreekLower = "abc\u03B1\u03B2\u03B3"; // abcαβγ

    public String utf16SupUpper = "\uD835\uDC00\uD835\uDC01\uD835\uDC02\uD835\uDC03\uD835\uDC04"; // 1D400..1D404
    public String utf16SupUpperLower = "\uD835\uDC00\uD835\uDC01\uD835\uDC02\uD835\uDC03\uD835\uDC1C"; // 1D400..1D41C
    public String utf16SubLower = "\uD835\uDC1C\uD835\uDC1D\uD835\uDC1E\uD835\uDC1F\uD835\uDC20"; // 1D41C..1D420


    @Benchmark
    public int upperLower() {
        return upper.compareToIgnoreCase(upperLower);
    }

    @Benchmark
    public int lower() {
        return upper.compareToIgnoreCase(lower);
    }

    @Benchmark
    public int supUpperLower() {
        return supUpper.compareToIgnoreCase(supUpperLower);
    }

    @Benchmark
    public int supLower() {
        return supUpper.compareToIgnoreCase(supLower);
    }

    @Benchmark
    public int upperLowerCF() {
        return upper.compareToFoldCase(upperLower);
    }

    @Benchmark
    public int lowerrCF() {
        return upper.compareToFoldCase(lower);
    }

    @Benchmark
    public int supUpperLowerCF() {
        return supUpper.compareToFoldCase(supUpperLower);
    }

    @Benchmark
    public int supLowerCF() {
        return supUpper.compareToFoldCase(supLower);
    }

    @Benchmark
    public int asciiUpperLower() {
        return asciiUpper.compareToIgnoreCase(asciiUpperLower);
    }

    @Benchmark
    public int asciiLower() {
        return asciiUpper.compareToIgnoreCase(asciiLower);
    }

    @Benchmark
    public int asciiGreekUpperLower() {
        return asciiGreekUpper.compareToIgnoreCase(asciiGreekUpperLower);
    }

    @Benchmark
    public int asciiGreekLower() {
        return asciiGreekUpper.compareToIgnoreCase(asciiGreekLower);
    }

    @Benchmark
    public int greekUpperLower() {
        return greekUpper.compareToIgnoreCase(greekUpperLower);
    }

    @Benchmark
    public int greekLower() {
        return greekUpper.compareToIgnoreCase(greekLower);
    }

    @Benchmark
    public int utf16SupUpperLower() {
        return utf16SupUpper.compareToIgnoreCase(utf16SupUpperLower);
    }

    @Benchmark
    public int utf16SubLower() {
        return utf16SupUpper.compareToIgnoreCase(utf16SubLower);
    }

    @Benchmark
    public int asciiUpperLowerCF() {
        return asciiUpper.compareToFoldCase(asciiUpperLower);
    }

    @Benchmark
    public int asciiLowerCF() {
        return asciiUpper.compareToFoldCase(asciiLower);
    }

    @Benchmark
    public int greekUpperLowerCF() {
        return greekUpper.compareToFoldCase(greekUpperLower);
    }

    @Benchmark
    public int greekLowerCF() {
        return greekUpper.compareToFoldCase(greekLower);
    }

    @Benchmark
    public int asciiGreekUpperLowerCF() {
        return asciiGreekUpper.compareToFoldCase(asciiGreekUpperLower);
    }

    @Benchmark
    public int asciiGreekLowerCF() {
        return asciiGreekUpper.compareToFoldCase(asciiGreekLower);
    }


    @Benchmark
    public int utf16SupUpperLowerCF() {
        return utf16SupUpper.compareToFoldCase(utf16SupUpperLower);
    }

    @Benchmark
    public int utf16SubLowerCF() {
        return utf16SupUpper.compareToFoldCase(utf16SubLower);
    }

}
