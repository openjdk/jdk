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
public class StringDecode {

    @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6", "ISO-2022-KR"})
    private String charsetName;

    private Charset charset;
    private byte[] asciiString;
    private byte[] longAsciiString;
    private byte[] utf16String;
    private byte[] longUtf16EndString;
    private byte[] longUtf16StartString;
    private byte[] latin1String;
    private byte[] longLatin1EndString;
    private byte[] longLatin1StartString;

    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = "ascii string".getBytes(charset);
        longAsciiString = """
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
            """.getBytes(charset);
        utf16String = "UTF-\uFF11\uFF16 string".getBytes(charset);
        longUtf16EndString = """
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

        latin1String = """
             a\u00B6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6\u00F6
            """.getBytes(charset);

        longLatin1EndString = """
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
             hendrerit sapien. \u00F6Donec aliquam mattis lectus eu ultrices. Duis eu nisl\u00F6
             euismod, blandit mauris vel, \u00F6placerat urna. Etiam malesuada enim purus,
             tristique mollis odio blandit quis.\u00B6 Vivamus posuere. \u00F6
             \u00F6
            """.getBytes(charset);
        longLatin1StartString = """
             \u00F6
             Lorem ipsum dolor sit amet, \u00B6consectetur adipiscing elit. Aliquam ac sem eu
             urna egestas \u00F6placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
             Nulla \u00F6nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
             sapien in \u00F6magna porta ultricies. \u00F6Sed vel pellentesque nibh. Pellentesque dictum
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

    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeAsciiCharsetName() throws Exception {
        return new String(asciiString, charsetName);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeAscii() throws Exception {
        return new String(asciiString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeAsciiLong() throws Exception {
        return new String(longAsciiString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1Short() throws Exception {
        return new String(latin1String, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1StartLong() throws Exception {
        return new String(longLatin1StartString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeLatin1EndLong() throws Exception {
        return new String(longLatin1EndString, charset);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeLatin1Mixed(Blackhole bh) throws Exception {
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(latin1String, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public String decodeUTF16Short() throws Exception {
        return new String(utf16String, charset);
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
    public void decodeUTF16Mixed(Blackhole bh) throws Exception {
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(utf16String, charset));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void decodeAllMixed(Blackhole bh) throws Exception {
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(longAsciiString, charset));
    }
}
