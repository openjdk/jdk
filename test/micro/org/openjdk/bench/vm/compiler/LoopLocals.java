/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Examine issues with (potentially) uninitialized locals interfering with
 * loop optimizations
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class LoopLocals {

    public char[] bytesStartingWithNegative = """
                 \uFF11
                 Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam ac sem eu
                 urna egestas placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
                 Nulla nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
                 sapien in magna porta ultricies. Sed vel pellentesque nibh. Pellentesque dictum
                 dignissim diam eu ultricies. Class aptent taciti sociosqu ad litora torquent
                 per conubia nostra, per inceptos himenaeos. Suspendisse erat diam, fringilla
                 sed massa sed, posuere viverra orci. Suspendisse tempor libero non gravida
                 efficitur. Vivamus lacinia risus non orci viverra, at consectetur odio laoreet.
                 Suspendisse potenti.

                 Phasellus vel nisi iaculis, accumsan quam sed, bibendum eros. Sed venenatis
                 nulla tortor, et eleifend urna sodales id. Nullam tempus ac metus sit amet
                 sollicitudin. Nam sed ex diam. Praesent vitae eros et neque condimentum
                 consectetur eget non tortor. Praesent bibendum vel felis nec dignissim.
                 Maecenas a enim diam. Suspendisse quis ligula at nisi accumsan lacinia id
                 hendrerit sapien. Donec aliquam mattis lectus eu ultrices. Duis eu nisl
                 euismod, blandit mauris vel, placerat urna. Etiam malesuada enim purus,
                 tristique mollis odio blandit quis. Vivamus posuere.
                """.toCharArray();

    public char[] bytesEndingWithNegative = """
                 Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam ac sem eu
                 urna egestas placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
                 Nulla nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
                 sapien in magna porta ultricies. Sed vel pellentesque nibh. Pellentesque dictum
                 dignissim diam eu ultricies. Class aptent taciti sociosqu ad litora torquent
                 per conubia nostra, per inceptos himenaeos. Suspendisse erat diam, fringilla
                 sed massa sed, posuere viverra orci. Suspendisse tempor libero non gravida
                 efficitur. Vivamus lacinia risus non orci viverra, at consectetur odio laoreet.
                 Suspendisse potenti.

                 Phasellus vel nisi iaculis, accumsan quam sed, bibendum eros. Sed venenatis
                 nulla tortor, et eleifend urna sodales id. Nullam tempus ac metus sit amet
                 sollicitudin. Nam sed ex diam. Praesent vitae eros et neque condimentum
                 consectetur eget non tortor. Praesent bibendum vel felis nec dignissim.
                 Maecenas a enim diam. Suspendisse quis ligula at nisi accumsan lacinia id
                 hendrerit sapien. Donec aliquam mattis lectus eu ultrices. Duis eu nisl
                 euismod, blandit mauris vel, placerat urna. Etiam malesuada enim purus,
                 tristique mollis odio blandit quis. Vivamus posuere. \uFF11
                """.toCharArray();

    @Param({"startNonASCII", "endNonASCII", "mixed"})
    private String variant;
    private char[] val;
    @Setup
    public void setup() {
        val = switch (variant) {
            case "startNonASCII" -> bytesStartingWithNegative;
            case "endNonASCII" -> bytesEndingWithNegative;
            case "mixed" -> {
                char[] chars = bytesEndingWithNegative.clone();
                var random = new Random(0L);
                for (int i = 0; i < chars.length; i++) {
                    if (random.nextInt(100) < 30) {
                        chars[i] = (char)(chars[i] + random.nextInt(0x2F00));
                    }
                }
                yield chars;
            }
            default -> throw new RuntimeException("Unknown variant: " + variant);
        };
    }

    @Benchmark
    public byte[] loopsWithSharedLocal() {
        int dp = 0;
        int sp = 0;
        int sl = val.length >> 1;
        byte[] dst = new byte[sl * 3];
        char c;
        while (sp < sl && (c = getChar(val, sp)) < '\u0080') {
            dst[dp++] = (byte)c;
            sp++;
        }
        while (sp < sl) {
            c = getChar(val, sp++);
            if (c < 0x80) {
                dst[dp++] = (byte)c;
            } else if (c < 0x800) {
                dst[dp++] = (byte)(0xc0 | (c >> 6));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {
                int uc = -1;
                char c2;
                if (Character.isHighSurrogate(c) && sp < sl &&
                        Character.isLowSurrogate(c2 = getChar(val, sp))) {
                    uc = Character.toCodePoint(c, c2);
                }
                if (uc < 0) {
                    dst[dp++] = '?';
                } else {
                    dst[dp++] = (byte)(0xf0 | ((uc >> 18)));
                    dst[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                    dst[dp++] = (byte)(0x80 | ((uc >>  6) & 0x3f));
                    dst[dp++] = (byte)(0x80 | (uc & 0x3f));
                    sp++;  // 2 chars
                }
            } else {
                // 3 bytes, 16 bits
                dst[dp++] = (byte)(0xe0 | ((c >> 12)));
                dst[dp++] = (byte)(0x80 | ((c >>  6) & 0x3f));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            }
        }
        if (dp == dst.length) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
    }

    @Benchmark
    public byte[] loopsWithScopedLocal() {
        int dp = 0;
        int sp = 0;
        int sl = val.length >> 1;
        byte[] dst = new byte[sl * 3];
        while (sp < sl) {
            // ascii fast loop;
            char c = getChar(val, sp);
            if (c >= '\u0080') {
                break;
            }
            dst[dp++] = (byte)c;
            sp++;
        }
        while (sp < sl) {
            char c = getChar(val, sp++);
            if (c < 0x80) {
                dst[dp++] = (byte)c;
            } else if (c < 0x800) {
                dst[dp++] = (byte)(0xc0 | (c >> 6));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) {
                int uc = -1;
                char c2;
                if (Character.isHighSurrogate(c) && sp < sl &&
                        Character.isLowSurrogate(c2 = getChar(val, sp))) {
                    uc = Character.toCodePoint(c, c2);
                }
                if (uc < 0) {
                    dst[dp++] = '?';
                } else {
                    dst[dp++] = (byte)(0xf0 | ((uc >> 18)));
                    dst[dp++] = (byte)(0x80 | ((uc >> 12) & 0x3f));
                    dst[dp++] = (byte)(0x80 | ((uc >>  6) & 0x3f));
                    dst[dp++] = (byte)(0x80 | (uc & 0x3f));
                    sp++;  // 2 chars
                }
            } else {
                // 3 bytes, 16 bits
                dst[dp++] = (byte)(0xe0 | ((c >> 12)));
                dst[dp++] = (byte)(0x80 | ((c >>  6) & 0x3f));
                dst[dp++] = (byte)(0x80 | (c & 0x3f));
            }
        }
        if (dp == dst.length) {
            return dst;
        }
        return Arrays.copyOf(dst, dp);
    }

    static char getChar(char[] val, int index) {
        return val[index];
    }
}
