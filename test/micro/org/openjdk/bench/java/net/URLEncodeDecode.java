/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Tests java.net.URLEncoder.encode and Decoder.decode.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class URLEncodeDecode {

    private static final int COUNT = 1024;

    @Param("1024")
    public int maxLength;

    /**
     * Percentage of strings that will remain unchanged by an encoding/decoding (0-100)
     */
    @Param({"0", "75", "100"})
    public int unchanged;

    /**
     * Percentage of chars in changed strings that cause encoding/decoding to happen (0-100)
     */
    @Param({"6"})
    public int encodeChars;

    public String[] testStringsEncode;
    public String[] testStringsDecode;
    public String[] toStrings;

    @Setup
    public void setupStrings() {
        char[] encodeTokens = new char[] { '[', '(', ' ', '\u00E4', '\u00E5', '\u00F6', ')', '='};
        char[] tokens = new char[((int) 'Z' - (int) 'A' + 1) + ((int) 'z' - (int) 'a' + 1) + ((int) '9' - (int) '0' + 1) + 4];
        int n = 0;
        for (char c = '0'; c <= '9'; c++) {
            tokens[n++] = c;
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            tokens[n++] = c;
        }
        for (char c = 'a'; c <= 'z'; c++) {
            tokens[n++] = c;
        }
        tokens[n++] = '-';
        tokens[n++] = '_';
        tokens[n++] = '.';
        tokens[n++] = '*';

        Random r = new Random(3);
        testStringsEncode = new String[COUNT];
        testStringsDecode = new String[COUNT];
        toStrings = new String[COUNT];
        for (int i = 0; i < COUNT; i++) {
            int l = r.nextInt(maxLength);
            boolean needEncoding = r.nextInt(100) >= unchanged;
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < l; j++) {
                if (needEncoding && r.nextInt(100) < encodeChars) {
                    int c = r.nextInt(encodeTokens.length);
                    sb.append(encodeTokens[c]);
                } else {
                    int c = r.nextInt(tokens.length);
                    sb.append(tokens[c]);
                }
            }
            testStringsEncode[i] = sb.toString();
        }

        for (int i = 0; i < COUNT; i++) {
            int l = r.nextInt(maxLength);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < l; j++) {
                boolean needEncoding = r.nextInt(100) >= unchanged;
                int c = r.nextInt(tokens.length);
                if (needEncoding && r.nextInt(100) < encodeChars) {
                    if (r.nextInt(100) < 15) {
                        sb.append('+'); // exercise '+' -> ' ' decoding paths.
                    } else {
                        sb.append("%").append(tokens[r.nextInt(16)]).append(tokens[r.nextInt(16)]);
                    }
                } else {
                    sb.append(tokens[c]);
                }
            }
            testStringsDecode[i] = sb.toString();
        }
    }

    @Benchmark
    public void testEncodeUTF8(Blackhole bh) throws UnsupportedEncodingException {
        for (String s : testStringsEncode) {
            bh.consume(java.net.URLEncoder.encode(s, StandardCharsets.UTF_8));
        }
    }

    @Benchmark
    public void testDecodeUTF8(Blackhole bh) throws UnsupportedEncodingException {
        for (String s : testStringsDecode) {
            bh.consume(URLDecoder.decode(s, StandardCharsets.UTF_8));
        }
    }


}
