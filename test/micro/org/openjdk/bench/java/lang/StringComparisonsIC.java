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

    @Param({"6", "15", "1024"})
    public int size;

    @Param({"mixed", "lat", "utf16"})
    public String coders;
    @Param({"az","lat", "utf"})
    public String letter;

    private String leftString;
    private String rightString;

    @Setup
    public void setup() {

        String l;
        switch (letter) {
            case "az" -> letter = "e";
            case "lat" -> letter = "1";
            case "utf" -> letter = "\u025b";
        }

        String e = "e";
        String ue = "\u025b";


        switch (coders) {
            case "utf16" -> {
                leftString = ue + letter.repeat(size);
                rightString = leftString;
            }
            case "mixed" -> {
                leftString = ue + letter.repeat(size);
                rightString = e + letter.repeat(size);
            }
            case "lat" -> {
                leftString = e + letter.repeat(size);
                rightString = leftString;
            }
        }
        rightString = rightString.toUpperCase(Locale.ENGLISH);
    }


    @Benchmark
    public boolean regionMatchesIC() {
        return leftString.regionMatches(true, 1, rightString, 1, size);
    }



}
