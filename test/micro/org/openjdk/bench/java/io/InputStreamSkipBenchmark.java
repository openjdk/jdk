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
package org.openjdk.bench.java.io;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

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

/**
 * Benchmark for {@link InputStream} skip functions.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
public class InputStreamSkipBenchmark {

    @Benchmark
    public long testSkip(Data data) throws IOException {
        InputStream testBaseInputStream = data.inputStreamProvider.apply(data.inputStreamSize);
        long res;
        do {
            res = testBaseInputStream.skip(data.skipLength);
        } while (res != 0);
        return res;
    }

    @State(Scope.Thread)
    public static class Data {

        @Param({"1000000"})
        private int inputStreamSize;

        @Param({"1", "8", "32", "128", "512", "2048", "8192"})
        private int skipLength;

        @Param({"LOCAL_VARIABLE", "FIELD", "FIELD_ONLY_MIN_MAX", "SOFT_REFERENCE"})
        private String inputStreamType;

        private Function<Integer, ? extends InputStream> inputStreamProvider;

        @Setup
        public void setup() {
            switch (inputStreamType) {
                case "LOCAL_VARIABLE": {
                    this.inputStreamProvider = TestBaseInputStream0::new;
                    break;
                }
                case "FIELD": {
                    this.inputStreamProvider = TestBaseInputStream1::new;
                    break;
                }
                case "FIELD_ONLY_MIN_MAX": {
                    this.inputStreamProvider = TestBaseInputStream2::new;
                    break;
                }
                case "SOFT_REFERENCE": {
                    this.inputStreamProvider = TestBaseInputStream3::new;
                    break;
                }
                default:
                    // never
            }
        }
    }

    static class TestBaseInputStream extends InputStream {

        protected static final int MAX_SKIP_BUFFER_SIZE = 2048;

        private int length;

        public TestBaseInputStream(int length) {
            this.length = length;
        }

        @Override
        public int read() throws IOException {
            if (length > 0) {
                --length;
                return 0;
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (length <= 0) {
                return -1;
            }
            if (length < len) {
                len = length;
            }
            Arrays.fill(b, off, off + len, (byte) ThreadLocalRandom.current().nextInt());
            length -= len;
            return len;
        }

    }

    static class TestBaseInputStream0 extends TestBaseInputStream {

        public TestBaseInputStream0(int length) {
            super(length);
        }

        public long skip(long n) throws IOException {
            long remaining = n;
            int nr;

            if (n <= 0) {
                return 0;
            }

            int size = (int) Math.min(MAX_SKIP_BUFFER_SIZE, remaining);
            byte[] skipBuffer = new byte[size];
            while (remaining > 0) {
                nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
                if (nr < 0) {
                    break;
                }
                remaining -= nr;
            }

            return n - remaining;
        }
    }

    static class TestBaseInputStream1 extends TestBaseInputStream {

        public TestBaseInputStream1(int length) {
            super(length);
        }

        private byte[] skipBuffer;

        @Override
        public long skip(long n) throws IOException {
            long remaining = n;

            if (n <= 0) {
                return 0;
            }

            int size = (int) Math.min(MAX_SKIP_BUFFER_SIZE, remaining);

            byte[] skipBuffer = this.skipBuffer;
            if ((skipBuffer == null) || (skipBuffer.length < size)) {
                this.skipBuffer = skipBuffer = new byte[size];
            }

            while (remaining > 0) {
                int nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
                if (nr < 0) {
                    break;
                }
                remaining -= nr;
            }

            return n - remaining;
        }
    }

    static class TestBaseInputStream2 extends TestBaseInputStream {

        public TestBaseInputStream2(int length) {
            super(length);
        }

        private static final int MIN_SKIP_BUFFER_SIZE = 128;

        private byte[] skipBuffer;

        @Override
        public long skip(long n) throws IOException {
            long remaining = n;

            if (n <= 0) {
                return 0;
            }

            int size = (int) Math.min(MAX_SKIP_BUFFER_SIZE, remaining);

            byte[] skipBuffer = this.skipBuffer;
            if ((skipBuffer == null) || (skipBuffer.length < size)) {
                this.skipBuffer = skipBuffer = new byte[size < MIN_SKIP_BUFFER_SIZE ? MIN_SKIP_BUFFER_SIZE :
                        MAX_SKIP_BUFFER_SIZE];
            }

            while (remaining > 0) {
                int nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
                if (nr < 0) {
                    break;
                }
                remaining -= nr;
            }

            return n - remaining;
        }
    }

    static class TestBaseInputStream3 extends TestBaseInputStream {

        public TestBaseInputStream3(int length) {
            super(length);
        }

        private static final int MIN_SKIP_BUFFER_SIZE = 128;

        private SoftReference<byte[]> skipBufferReference;

        private byte[] skipBufferReference(long remaining) {
            int size = (int) Math.min(MAX_SKIP_BUFFER_SIZE, remaining);
            SoftReference<byte[]> ref = this.skipBufferReference;
            byte[] buffer;
            if (ref == null || (buffer = ref.get()) == null || buffer.length < size) {
                buffer = new byte[size];
                this.skipBufferReference = new SoftReference<>(buffer);
            }
            return buffer;
        }

        @Override
        public long skip(long n) throws IOException {
            long remaining = n;

            if (n <= 0) {
                return 0;
            }

            int size = (int) Math.min(MAX_SKIP_BUFFER_SIZE, remaining);

            byte[] skipBuffer = this.skipBufferReference(size);

            while (remaining > 0) {
                int nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
                if (nr < 0) {
                    break;
                }
                remaining -= nr;
            }

            return n - remaining;
        }
    }

}
