/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.text;

import java.awt.Color;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class AttributedStringBench {

    private AttributedString string1;
    private AttributedString string2;
    private AttributedString string3;

    @Setup
    public void setup() {

        string1 = new AttributedString("this is an attributed string test");

        string2 = new AttributedString("this is an attributed string test");
        string2.addAttribute(TextAttribute.SIZE, 20);

        string3 = new AttributedString("this is an attributed string test");
        string3.addAttribute(TextAttribute.SIZE, 20);
        string3.addAttribute(TextAttribute.FOREGROUND, Color.BLACK);
        string3.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
        string3.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 11, 21);
    }

    @Benchmark
    public void iterateMissingWithNoAttributes(Blackhole blackhole) {
        iterate(string1, TextAttribute.BIDI_EMBEDDING, blackhole);
    }

    @Benchmark
    public void iterateMissingWithOneAttribute(Blackhole blackhole) {
        iterate(string2, TextAttribute.BIDI_EMBEDDING, blackhole);
    }

    @Benchmark
    public void iterateMissingWithThreeAttributes(Blackhole blackhole) {
        iterate(string3, TextAttribute.BIDI_EMBEDDING, blackhole);
    }

    @Benchmark
    public void iteratePresentWithOneAttribute(Blackhole blackhole) {
        iterate(string2, TextAttribute.SIZE, blackhole);
    }

    @Benchmark
    public void iteratePresentWithThreeAttributes(Blackhole blackhole) {
        iterate(string3, TextAttribute.SIZE, blackhole);
    }

    private static void iterate(AttributedString as, TextAttribute att, Blackhole blackhole) {
        AttributedCharacterIterator it = as.getIterator();
        for (char c = it.first(); c != AttributedCharacterIterator.DONE; c = it.next()) {
            Object val = it.getAttribute(att);
            blackhole.consume(val);
        }
    }

    @Benchmark
    public void createWithNoAttributes(Blackhole blackhole) {
        AttributedString string = new AttributedString("this is an attributed string test");
        blackhole.consume(string);
    }

    @Benchmark
    public void createWithOneAttribute(Blackhole blackhole) {
        AttributedString string = new AttributedString("this is an attributed string test");
        string.addAttribute(TextAttribute.SIZE, 20);
        blackhole.consume(string);
    }

    @Benchmark
    public void createWithThreeAttributes(Blackhole blackhole) {
        AttributedString string = new AttributedString("this is an attributed string test");
        string.addAttribute(TextAttribute.SIZE, 20);
        string.addAttribute(TextAttribute.FOREGROUND, Color.BLACK);
        string.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_REGULAR);
        string.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, 11, 21);
        blackhole.consume(string);
    }
}
