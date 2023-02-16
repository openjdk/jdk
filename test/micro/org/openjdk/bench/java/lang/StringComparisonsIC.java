/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::regionMatches, ignoring case
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringComparisonsIC {

    public static final String LAT_LETTER_MATCH = "lat1-letter-match";
    public static final String LAT_LETTER_MISMATCH = "lat1-letter-mismatch";
    public static final String LAT_NUMBER_MATCH = "lat1-number-match";
    public static final String LAT_NUMBER_MISMATCH = "lat1-number-mismatch";
    public static final String MIXED_LETTER_MATCH = "mixed-letter-match";
    public static final String MIXED_LETTER_MISMATCH = "mixed-letter-mismatch";
    public static final String MIXED_NUMBER_MATCH = "mixed-number-match";
    public static final String MIXED_NUMBER_MISMATCH = "mixed-number-mismatch";
    public static final String MIXED_UTF_MATCH = "mixed-unicode-match";
    public static final String MIXED_UTF_MISMATCH = "mixed-unicode-mismatch";
    public static final String UTF_LETTER_MATCH = "utf16-letter-match";
    public static final String UTF_LETTER_MISMATCH = "utf16-letter-mismatch";
    public static final String UTF_NUMBER_MATCH = "utf16-number-match";
    public static final String UTF_NUMBER_MISMATCH = "utf16-number-minmatch";
    public static final String UTF_UTF_MATCH = "utf16-unicode-match";
    public static final String UTF_UTF_MISMATCH = "utf16-unicode-mismatch";

    @Param({"1024"})
    public int size;

    @Param({
            LAT_LETTER_MATCH,
            LAT_LETTER_MISMATCH,
            LAT_NUMBER_MATCH,
            LAT_NUMBER_MISMATCH,
            MIXED_LETTER_MATCH,
            MIXED_LETTER_MISMATCH,
            MIXED_NUMBER_MATCH,
            MIXED_NUMBER_MISMATCH,
            MIXED_UTF_MATCH,
            MIXED_UTF_MISMATCH,
            UTF_LETTER_MATCH,
            UTF_LETTER_MISMATCH,
            UTF_NUMBER_MATCH,
            UTF_NUMBER_MISMATCH,
            UTF_UTF_MATCH,
            UTF_UTF_MISMATCH
    })

    public String code_content_match;

    private String leftString;
    private String rightString;

    @Setup
    public void setup() {



        String let = "e";
        String otherLet = "f";
        String ue = "\u025b";
        String num =  "1";
        String otherNum =  "2";


        switch (code_content_match) {
            case LAT_LETTER_MATCH -> {
                leftString = let  + let.repeat(size);
                rightString = leftString;
            }

            case LAT_LETTER_MISMATCH -> {
                leftString = let  + let.repeat(size);
                rightString = let + otherLet.repeat(size);
            }
            case LAT_NUMBER_MATCH -> {
                leftString = let  + num.repeat(size);
                rightString = leftString;
            }
            case LAT_NUMBER_MISMATCH -> {
                leftString = let  + num.repeat(size);
                rightString = let + otherNum.repeat(size);
            }
            case MIXED_LETTER_MATCH -> {
                leftString = let  + let.repeat(size);
                rightString = ue + let.repeat(size);
            }
            case MIXED_LETTER_MISMATCH -> {
                leftString = let  + let.repeat(size);
                rightString = ue + otherLet.repeat(size);
            }
            case MIXED_NUMBER_MATCH -> {
                leftString = let  + num.repeat(size);
                rightString = ue + num.repeat(size);
            }
            case MIXED_NUMBER_MISMATCH -> {
                leftString = let  + num.repeat(size);
                rightString = ue + otherNum.repeat(size);
            }
            case MIXED_UTF_MATCH -> {
                leftString = let  + "i".repeat(size);
                rightString = ue + "\u0130".repeat(size);
            }
            case MIXED_UTF_MISMATCH -> {
                leftString = let  + let.repeat(size);
                rightString = ue + "\u0130".repeat(size);
            }
            case UTF_LETTER_MATCH -> {
                leftString = ue  + let.repeat(size);
                rightString = ue + let.repeat(size);
            }
            case UTF_LETTER_MISMATCH -> {
                leftString = ue  + let.repeat(size);
                rightString = ue + otherLet.repeat(size);
            }
            case UTF_NUMBER_MATCH -> {
                leftString = ue  + num.repeat(size);
                rightString = ue + num.repeat(size);
            }
            case UTF_NUMBER_MISMATCH -> {
                leftString = ue  + num.repeat(size);
                rightString = ue + otherNum.repeat(size);
            }
            case UTF_UTF_MATCH -> {
                leftString = ue  + ue.repeat(size);
                rightString = ue + ue.repeat(size);
            }
            case UTF_UTF_MISMATCH -> {
                leftString = ue  + ue.repeat(size);
                rightString = ue + "\u0130".repeat(size);
            }
            default -> throw new IllegalArgumentException("Unconfigured coder: " + code_content_match);
        }
        rightString = rightString.toUpperCase(Locale.ENGLISH);
    }


    @Benchmark
    public boolean regionMatchesIC() {
        return leftString.regionMatches(true, 1, rightString, 1, size);
    }



}
