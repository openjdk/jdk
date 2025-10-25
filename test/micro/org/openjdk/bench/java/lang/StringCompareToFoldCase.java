/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This benchmark naively explores String::compareToFoldCase performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class StringCompareToFoldCase {

    private String asciiUpper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private String asciiUpperLower = "ABCDEFGHIJKLMNOpqrstuvwxyz";
    private String asciiLower = "abcdefghijklmnopqrstuvwxyz";

    private String asciiWithDF = "abcdßßßßßßßßßßßßßßßßWXYZ";
    private String asciiWithDFSS = "abcdssssssssssssssssßßßßßßßßWXYZ";

    private String asciiLatine1 = "ABCDEFGHIJKLMNOpqrstuvwxyz0";
    private String asciiLatin1UTF16 = "abcdefghijklmnopqrstuvwxyz\u0391";

    private String greekUpper = "\u0391\u0392\u0393\u0394\u0395\u0391\u0392\u0393\u0394\u0395"; // ΑΒΓΔΕ
    private String greekUpperLower = "\u0391\u0392\u0393\u0394\u0395\u0391\u0392\u0393\u0394\u03B5"; // ΑΒΓΔε
    private String greekLower = "\u03B1\u03B2\u03B3\u03B4\u03B5\u03B1\u03B2\u03B3\u03B4\u03B5"; // αβγδε

    public String supUpper = "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc04";
    public String supUpperLower = "\ud801\udc00\ud801\udc01\ud801\udc02\ud801\udc03\ud801\udc2c";
    public String supLower = "\ud801\udc28\ud801\udc29\ud801\udc2a\ud801\udc2b\ud801\udc2c";

    @Benchmark
    public int asciiUpperLower() {
        return asciiUpper.compareToIgnoreCase(asciiUpperLower);
    }

    @Benchmark
    public int asciiLower() {
        return asciiUpper.compareToIgnoreCase(asciiLower);
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
    public int latin1UTF16() {
        return asciiLatine1.compareToIgnoreCase(asciiLatin1UTF16);
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
    public int asciiUpperLowerFC() {
        return asciiUpper.compareToFoldCase(asciiUpperLower);
    }

    @Benchmark
    public int asciiLowerFC() {
        return asciiUpper.compareToFoldCase(asciiLower);
    }

    @Benchmark
    public int asciiWithDFFC() {
        return asciiWithDF.compareToFoldCase(asciiWithDFSS);
    }

    @Benchmark
    public int greekUpperLowerFC() {
        return greekUpper.compareToFoldCase(greekUpperLower);
    }

    @Benchmark
    public int greekLowerFC() {
        return greekUpper.compareToFoldCase(greekLower);
    }

    @Benchmark
    public int latin1UTF16FC() {
        return asciiLatine1.compareToFoldCase(asciiLatin1UTF16); }

    @Benchmark
    public int supUpperLowerFC() {
        return supUpper.compareToFoldCase(supUpperLower);
    }

    @Benchmark
    public int supLowerFC() {
        return supUpper.compareToFoldCase(supLower);
    }

    @Benchmark
    public boolean asciiUpperLowerEQ() {
        return asciiUpper.equalsIgnoreCase(asciiUpperLower);
    }

    @Benchmark
    public boolean asciiLowerEQ() {
        return asciiUpper.equalsIgnoreCase(asciiLower);
    }

    @Benchmark
    public boolean greekUpperLowerEQ() {
        return greekUpper.equalsIgnoreCase(greekUpperLower);
    }

    @Benchmark
    public boolean greekLowerEQ() {
        return greekUpper.equalsIgnoreCase(greekLower);
    }

    @Benchmark
    public boolean latin1UTF16EQ() {
        return asciiLatine1.equalsIgnoreCase(asciiLatin1UTF16);
    }

    @Benchmark
    public boolean supUpperLowerEQ() {
        return supUpper.equalsIgnoreCase(supUpperLower);
    }

    @Benchmark
    public boolean supLowerEQ() {
        return supUpper.equalsIgnoreCase(supLower);
    }

    @Benchmark
    public boolean asciiUpperLowerEQFC() {
        return asciiUpper.equalsFoldCase(asciiUpperLower);
    }

    @Benchmark
    public boolean asciiLowerEQFC() {
        return asciiUpper.equalsFoldCase(asciiLower);
    }

    @Benchmark
    public boolean greekUpperLowerEQFC() {
        return greekUpper.equalsFoldCase(greekUpperLower);
    }

    @Benchmark
    public boolean greekLowerEQFC() {
        return greekUpper.equalsFoldCase(greekLower);
    }

    @Benchmark
    public boolean latin1UTF16EQFC() {
        return asciiLatine1.equalsFoldCase(asciiLatin1UTF16);
    }

    @Benchmark
    public boolean supUpperLowerEQFC() {
        return supUpper.equalsFoldCase(supUpperLower);
    }

    @Benchmark
    public boolean supLowerEQFC() {
        return supUpper.equalsFoldCase(supLower);
    }
 }
