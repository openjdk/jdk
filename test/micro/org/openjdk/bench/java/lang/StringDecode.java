/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 2)
@Warmup(iterations = 5, time = 3)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class StringDecode {

    // Reduced by default to only UTF-8, previous coverage:
    // @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6", "ISO-2022-KR"})
    @Param({"UTF-8"})
    private String charsetName;

    private Charset charset;
    private byte[] asciiString;
    private byte[] longAsciiString;
    private byte[] utf16String;
    private byte[] longUtf16EndString;
    private byte[] longUtf16StartString;
    private byte[] longUtf16OnlyString;
    private byte[] latin1String;
    private byte[] longLatin1EndString;
    private byte[] longLatin1StartString;
    private byte[] longLatin1OnlyString;

    private static final String LOREM = """
             Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam ac sem eu
             urna egestas placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
             Nulla nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
             sapien in magna porta ultricies. Sed vel pellentesque nibh. Pellentesque dictum
             dignissim diam eu ultricies. Class aptent taciti sociosqu ad litora torquent
             per conubia nostra, per inceptos himenaeos. Suspendisse erat diam, fringilla
             sed massa sed, posuere viverra orci. Suspendisse tempor libero non gravida
             efficitur. Vivamus lacinia risus non orci viverra, at consectetur odio laoreet.
             Suspendisse potenti.""";
    private static final String UTF16_STRING = "\uFF11".repeat(31);
    private static final String LATIN1_STRING = "\u00B6".repeat(31);

    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = LOREM.substring(0, 32).getBytes(charset);
        longAsciiString = LOREM.repeat(200).getBytes(charset);
        utf16String = "UTF-\uFF11\uFF16 string".getBytes(charset);
        longUtf16EndString = LOREM.repeat(4).concat(UTF16_STRING).getBytes(charset);
        longUtf16StartString = UTF16_STRING.concat(LOREM.repeat(4)).getBytes(charset);
        longUtf16OnlyString = UTF16_STRING.repeat(10).getBytes(charset);
        latin1String = LATIN1_STRING.getBytes(charset);
        longLatin1EndString = LOREM.repeat(4).concat(LATIN1_STRING).getBytes(charset);
        longLatin1StartString = LATIN1_STRING.concat(LOREM.repeat(4)).getBytes(charset);
        longLatin1OnlyString = LATIN1_STRING.repeat(10).getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeAsciiShort(Blackhole bh) throws Exception {
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(longAsciiString, 0, 15, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(longAsciiString, 512, 7, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeAsciiLong(Blackhole bh) throws Exception {
        bh.consume(new String(longAsciiString, charset));
        bh.consume(new String(longAsciiString, 0, 1024 + 31, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeLatin1Short(Blackhole bh) throws Exception {
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(latin1String, 0, 15, charset));
        bh.consume(new String(latin1String, 0, 3, charset));
        bh.consume(new String(longLatin1OnlyString, 512, 7, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1LongStart() throws Exception {
        return new String(longLatin1StartString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1LongEnd() throws Exception {
        return new String(longLatin1EndString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1LongOnly() throws Exception {
        return new String(longLatin1OnlyString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeLatin1Mixed(Blackhole bh) throws Exception {
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(longLatin1OnlyString, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeUTF16Short(Blackhole bh) throws Exception {
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(utf16String, 0, 3, charset));
        bh.consume(new String(utf16String, 0, 7, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeUTF16LongEnd() throws Exception {
        return new String(longUtf16EndString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeUTF16LongStart() throws Exception {
        return new String(longUtf16StartString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeUTF16LongOnly() throws Exception {
        return new String(longUtf16OnlyString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeUTF16Mixed(Blackhole bh) throws Exception {
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(longUtf16OnlyString, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeAllMixed(Blackhole bh) throws Exception {
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(longUtf16OnlyString, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(latin1String, 0, 7, charset));
        bh.consume(new String(longLatin1OnlyString, charset));
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(longAsciiString, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeShortMixed(Blackhole bh) throws Exception {
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(latin1String, 0, 15, charset));
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(latin1String, 0, 3, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(utf16String, 0, 7, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(asciiString, 0, 7, charset));
        bh.consume(new String(utf16String, 0, 3, charset));
        bh.consume(new String(latin1String, 0, 7, charset));
        bh.consume(new String(asciiString, 0, 15, charset));
    }
}
