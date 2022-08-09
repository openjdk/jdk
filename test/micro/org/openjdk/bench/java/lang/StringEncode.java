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
public class StringEncode {

    // Reduced by default to only UTF-8, previous coverage:
    // @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6", "ISO-2022-KR"})
    @Param({"UTF-8"})
    private String charsetName;

    private Charset charset;
    private String asciiString;
    private String asciiString3;
    private String asciiString7;
    private String asciiString15;
    private String longAsciiString;
    private String longAsciiString1055;
    private String utf16String;
    private String utf16String3;
    private String utf16String7;
    private String utf16String15;
    private String longUtf16EndString;
    private String longUtf16StartString;
    private String longUtf16OnlyString;
    private String latin1String;
    private String latin1String3;
    private String latin1String7;
    private String latin1String15;
    private String longLatin1EndString;
    private String longLatin1StartString;
    private String longLatin1OnlyString;

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
        asciiString = LOREM.substring(0, 32);
        asciiString3 = LOREM.substring(0, 3);
        asciiString7 = LOREM.substring(0, 7);
        asciiString15 = LOREM.substring(0, 15);
        longAsciiString = LOREM.repeat(200);
        longAsciiString1055 = longAsciiString.substring(0, 1055);
        utf16String = UTF16_STRING;
        utf16String3 = UTF16_STRING.substring(0, 3);
        utf16String7 = UTF16_STRING.substring(0, 7);
        utf16String15 = UTF16_STRING.substring(0, 15);
        longUtf16EndString = LOREM.repeat(4).concat(UTF16_STRING);
        longUtf16StartString = UTF16_STRING.concat(LOREM.repeat(4));
        longUtf16OnlyString = UTF16_STRING.repeat(10);
        latin1String = LATIN1_STRING;
        latin1String3 = LATIN1_STRING.substring(0, 3);
        latin1String7 = LATIN1_STRING.substring(0, 7);
        latin1String15 = LATIN1_STRING.substring(0, 15);
        longLatin1EndString = LOREM.repeat(4).concat(LATIN1_STRING);
        longLatin1StartString = LATIN1_STRING.concat(LOREM.repeat(4));
        longLatin1OnlyString = LATIN1_STRING.repeat(10);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeAsciiShort(Blackhole bh) throws Exception {
        bh.consume(asciiString.getBytes(charset));
        bh.consume(asciiString3.getBytes(charset));
        bh.consume(asciiString15.getBytes(charset));
        bh.consume(asciiString7.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeAsciiLong(Blackhole bh) throws Exception {
        bh.consume(longAsciiString.getBytes(charset));
        bh.consume(longAsciiString1055.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeUTF16Short(Blackhole bh) throws Exception {
        bh.consume(utf16String.getBytes(charset));
        bh.consume(utf16String3.getBytes(charset));
        bh.consume(utf16String15.getBytes(charset));
        bh.consume(utf16String7.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeUTF16LongEnd() throws Exception {
        return longUtf16EndString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeUTF16LongStart() throws Exception {
        return longUtf16StartString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeUTF16LongOnly() throws Exception {
        return longUtf16OnlyString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeUTF16Mixed(Blackhole bh) throws Exception {
        bh.consume(utf16String.getBytes(charset));
        bh.consume(longUtf16StartString.getBytes(charset));
        bh.consume(longUtf16EndString.getBytes(charset));
        bh.consume(longUtf16OnlyString.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeLatin1Short(Blackhole bh) throws Exception {
        bh.consume(latin1String.getBytes(charset));
        bh.consume(latin1String3.getBytes(charset));
        bh.consume(latin1String15.getBytes(charset));
        bh.consume(latin1String7.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeLatin1LongOnly() throws Exception {
        return longLatin1OnlyString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeLatin1LongStart() throws Exception {
        return longLatin1StartString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public byte[] encodeLatin1LongEnd() throws Exception {
        return longLatin1EndString.getBytes(charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeLatin1Mixed(Blackhole bh) throws Exception {
        bh.consume(longLatin1EndString.getBytes(charset));
        bh.consume(longLatin1StartString.getBytes(charset));
        bh.consume(longLatin1OnlyString.getBytes(charset));
        bh.consume(latin1String.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeAllMixed(Blackhole bh) throws Exception {
        bh.consume(utf16String.getBytes(charset));
        bh.consume(longUtf16StartString.getBytes(charset));
        bh.consume(asciiString7.getBytes(charset));
        bh.consume(longUtf16EndString.getBytes(charset));
        bh.consume(latin1String3.getBytes(charset));
        bh.consume(longUtf16OnlyString.getBytes(charset));
        bh.consume(longLatin1EndString.getBytes(charset));
        bh.consume(longLatin1StartString.getBytes(charset));
        bh.consume(utf16String15.getBytes(charset));
        bh.consume(longLatin1OnlyString.getBytes(charset));
        bh.consume(latin1String.getBytes(charset));
        bh.consume(asciiString.getBytes(charset));
        bh.consume(longAsciiString.getBytes(charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void encodeShortMixed(Blackhole bh) throws Exception {
        bh.consume(utf16String3.getBytes(charset));
        bh.consume(latin1String7.getBytes(charset));
        bh.consume(asciiString15.getBytes(charset));
        bh.consume(utf16String.getBytes(charset));
        bh.consume(latin1String3.getBytes(charset));
        bh.consume(asciiString7.getBytes(charset));
        bh.consume(utf16String15.getBytes(charset));
        bh.consume(latin1String.getBytes(charset));
        bh.consume(asciiString3.getBytes(charset));
        bh.consume(utf16String7.getBytes(charset));
        bh.consume(latin1String15.getBytes(charset));
        bh.consume(asciiString.getBytes(charset));
    }
}
