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

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 3)
@State(Scope.Thread)
public class StringDecode {

    @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6", "ISO-2022-KR"})
    private String charsetName;

    private Charset charset;
    private byte[] asciiString;
    private byte[] utf16String;
    private byte[] longUtf16String;
    private byte[] longUtf16StartString;
    private byte[] longLatin1String;

    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = "ascii string".getBytes(charset);
        utf16String = "UTF-\uFF11\uFF16 string".getBytes(charset);
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
             hendrerit sapien. \uFF11Donec aliquam mattis lectus eu ultrices. Duis eu nisl\uFF11
             euismod, blandit mauris vel, \uFF11placerat urna. Etiam malesuada enim purus,
             tristique mollis odio blandit quis.\uFF11 Vivamus posuere. \uFF11
             \uFF11
            """.getBytes(charset);
        longUtf16StartString = """
             \uFF11
             Lorem ipsum dolor sit amet, \uFF11consectetur adipiscing elit. Aliquam ac sem eu
             urna egestas \uFF11placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
             Nulla \uFF11nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
             sapien in \uFF11magna porta ultricies. \uFF11Sed vel pellentesque nibh. Pellentesque dictum
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
            """.getBytes(charset);

        longLatin1String = """
             a\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             b\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             c\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             d\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             e\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             f\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             g\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             h\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             i\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
             j\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6
             k\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6\u00F6
             l\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6\u00F6
             m\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00B6\u00F6\u00F6
            """.getBytes(charset);
    }

    @Benchmark
    public String decodeAsciiCharsetName() throws Exception {
        return new String(asciiString, charsetName);
    }

    @Benchmark
    public String decodeAscii() throws Exception {
        return new String(asciiString, charset);
    }

    @Benchmark
    public String decodeLatin1Long() throws Exception {
        return new String(longLatin1String, charset);
    }

    @Benchmark
    public String decodeUTF16Short() throws Exception {
        return new String(utf16String, charset);
    }

    @Benchmark
    public String decodeUTF16LongEnd() throws Exception {
        return new String(longUtf16String, charset);
    }

    @Benchmark
    public String decodeUTF16LongStart() throws Exception {
        return new String(longUtf16StartString, charset);
    }

    @Benchmark
    public void decodeUTF16LongMixed(Blackhole bh) throws Exception {
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(longUtf16String, charset));
    }
}
