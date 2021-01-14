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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@State(Scope.Thread)
public class StringDecode {

    @Param({"US-ASCII", "ISO_8859_1", "UTF-8", "UTF_16"})
    private String charsetName;

    private Charset charset;

    private byte[] asciiString;
    private byte[] utf16String;

    private byte[] asciiDefaultString;
    private byte[] utf16DefaultString;
    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = "ascii string".getBytes(charset);
        utf16String = "UTF-\uFF11\uFF16 string".getBytes(charset);

        asciiDefaultString = "ascii string".getBytes();
        utf16DefaultString = "UTF-\uFF11\uFF16 string".getBytes();
    }

    @Benchmark
    public String decodeCharsetName(Blackhole bh) throws Exception {
        bh.consume(new String(asciiString, charsetName));
        bh.consume(new String(utf16String, charsetName));
    }

    @Benchmark
    public String decodeCharset(Blackhole bh) throws Exception {
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(utf16String, charset));
    }

    @Benchmark
    public String decodeDefault(Blackhole bh) throws Exception {
        bh.consume(new String(asciiDefaultString, charset));
        bh.consume(new String(utf16DefaultString, charset));
    }
}
