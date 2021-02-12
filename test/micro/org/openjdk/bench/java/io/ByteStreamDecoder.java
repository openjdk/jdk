/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.io;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * Tests the overheads of reading encoded byte arrays via StreamDecoder
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(time=2, iterations=5)
@Measurement(time=3, iterations=5)
@Fork(value=2, jvmArgs="-Xmx1g")
public class ByteStreamDecoder {

    @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6"})
    private String charsetName;

    private byte[] bytes;

    private char[] chars;

    private Charset cs;

    @Setup
    public void setup() throws IOException {
        bytes = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed non
            magna augue. Sed tristique ante id maximus interdum. Suspendisse
            potenti. Aliquam molestie metus vitae magna gravida egestas.
            Phasellus eleifend tortor sit amet neque euismod, vitae luctus
            ante viverra. Sed quis justo ultrices, eleifend dui sed, egestas
            lorem. Mauris ipsum ex, interdum eu turpis sed, fermentum efficitur
            lorem. Sed vel imperdiet libero, eget ullamcorper sem. Praesent
            gravida arcu quis ipsum viverra tristique. Quisque maximus
            elit nec nisi vulputate tempor. Integer aliquet tortor vel
            vehicula efficitur. Sed neque felis, ultricies eu leo ultricies,
            egestas placerat dolor. Etiam iaculis magna quis lacinia
            tincidunt. Donec in tellus volutpat, semper nunc ornare,
            tempus erat. Donec volutpat mauris in arcu mattis sollicitudin.
            Morbi vestibulum ipsum sed erat porta, mollis commodo nisi
            gravida.
            """.getBytes(charsetName);

        chars = new char[bytes.length * 2];
        cs = Charset.forName(charsetName);
    }

    @Benchmark
    public int readBytesCharsetName() throws Exception {
        return new InputStreamReader(new ByteArrayInputStream(bytes), charsetName).read(chars);
    }

    @Benchmark
    public int readBytesCharset() throws Exception {
        return new InputStreamReader(new ByteArrayInputStream(bytes), cs).read(chars);
    }
}
