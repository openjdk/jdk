/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package org.openjdk.bench.java.util.zip;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * A simple benchmark to measure the performance improvements achieved by avoiding
 * unnecessary native calls in InflaterInputStream::read() (see JDK-8281962).
 *
 * before JDK-8281962
 * ------------------
 * Benchmark                                     (size)  Mode  Cnt  Score   Error  Units
 * InflaterInputStreams.inflaterInputStreamRead     256  avgt    5  2.571   0.120  us/op
 * InflaterInputStreams.inflaterInputStreamRead     512  avgt    5  2.861   0.064  us/op
 * InflaterInputStreams.inflaterInputStreamRead    4096  avgt    5  5.110   0.278  us/op
 *
 * after JDK-8281962
 * -----------------
 * Benchmark                                     (size)  Mode  Cnt  Score   Error  Units
 * InflaterInputStreams.inflaterInputStreamRead     256  avgt    5  2.332   0.081  us/op
 * InflaterInputStreams.inflaterInputStreamRead     512  avgt    5  2.691   0.293  us/op
 * InflaterInputStreams.inflaterInputStreamRead    4096  avgt    5  4.812   1.038  us/op
 *
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
public class InflaterInputStreams {

    @Param({"256", "512", "4096"})
    private int size;
    private byte[] chars;
    private byte[] words;
    private static byte[] inflated;
    ByteArrayInputStream deflated;

    @Setup(Level.Trial)
    public void beforeRun() throws IOException {
        final int charCount = 64;
        final int wordLength = 8;
        chars = new byte[charCount];
        Random r = new Random(123456789);
        r.nextBytes(chars);
        words = new byte[1024];
        for (int i = 0; i < words.length / wordLength; i++) {
            System.arraycopy(chars, r.nextInt(charCount - wordLength), words, i * wordLength, wordLength);
        }
        inflated = new byte[2*size];
   }

    @Setup(Level.Iteration)
    public void beforeIteration() throws IOException {
        // Maximum deflated size (see https://stackoverflow.com/a/23578269/4146053)
        int maxDeflated = size + 5*(size/16383 + 1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream(maxDeflated);
        DeflaterOutputStream defout = new DeflaterOutputStream(baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(words, 0, size);
        bais.transferTo(defout);
        // We need to close the DeflaterOutputStream in order to flush
        // all the compressed data in the Deflater.
        defout.close();
        deflated = new ByteArrayInputStream(baos.toByteArray());
    }

    @Benchmark
    public void inflaterInputStreamRead() throws IOException {
        deflated.reset();
        // We close the InflaterInputStream to release underlying native resources of the Inflater.
        // The "deflated" ByteArrayInputStream remains unaffected.
        try (InflaterInputStream iis = new InflaterInputStream(deflated)) {
            while (iis.read(inflated, 0, inflated.length) != -1);
        }
    }
}
