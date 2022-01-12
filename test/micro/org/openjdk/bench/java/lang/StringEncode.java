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
@Fork(value = 3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class StringEncode {

    @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6"})
    private String charsetName;
    private Charset charset;
    private String asciiString;
    private String utf16String;
    private String longUtf16String;
    private String longUtf16StartString;

    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = "ascii string";
        utf16String = "UTF-\uFF11\uFF16 string";
        longUtf16String = """
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
                 \uFF11
                """;
        longUtf16StartString = """
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
                """;
    }

    @Benchmark
    public byte[] encodeAsciiCharsetName() throws Exception {
        return asciiString.getBytes(charset);
    }

    @Benchmark
    public byte[] encodeAscii() throws Exception {
        return asciiString.getBytes(charset);
    }

    @Benchmark
    public void encodeMix(Blackhole bh) throws Exception {
        bh.consume(asciiString.getBytes(charset));
        bh.consume(utf16String.getBytes(charset));
    }

    @Benchmark
    public byte[] encodeUTF16LongEnd() throws Exception {
        return longUtf16String.getBytes(charset);
    }

    @Benchmark
    public byte[] encodeUTF16LongStart() throws Exception {
        return longUtf16StartString.getBytes(charset);
    }

    @Benchmark
    public byte[] encodeUTF16() throws Exception {
        return utf16String.getBytes(charset);
    }
}
