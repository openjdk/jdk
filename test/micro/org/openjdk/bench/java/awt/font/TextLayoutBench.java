/*
 * Copyright (c) 2026, JetBrains s.r.o.. All rights reserved.
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

package org.openjdk.bench.java.awt.font;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JBR-10592: measures {@code TextLayout} creation for text that HarfBuzz shapes in many runs.
 * <p>
 * The text alternates Latin and Cyrillic words, so every word is a separate shaping run.
 * Kerning makes {@code Graphics2D.drawString} take the same {@code TextLayout} path.
 * Shaping time must grow linearly with the text length.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "-Djava.awt.headless=true")
@State(Scope.Thread)
public class TextLayoutBench {

    @Param({"100", "10000", "100000"})
    private int length;

    private String text;
    private Font font;
    private FontRenderContext frc;

    @Setup
    public void setup() {
        String words = "hello привет ";
        text = words.repeat(length / words.length() + 1).substring(0, length);
        font = new Font(Map.of(
                TextAttribute.FAMILY, Font.DIALOG,
                TextAttribute.SIZE, 13f,
                TextAttribute.KERNING, TextAttribute.KERNING_ON
        ));
        frc = new FontRenderContext(null, true, true);
    }

    @Benchmark
    public void createTextLayout(Blackhole bh) {
        bh.consume(new TextLayout(text, font, frc));
    }
}
