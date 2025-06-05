/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 4, time = 2)
@Fork(value = 3)
public class TestCRC32 {

    private CRC32 crc32;
    private Random random;
    private byte[] bytes;

    @Param({"64", "128", "256", "512", /* "1024", */ "2048", /* "4096", "8192", */ "16384", /* "32768", */ "65536"})
    private int count;

    public TestCRC32() {
        crc32 = new CRC32();
        random = new Random(2147483648L);
        bytes = new byte[1000000];
        random.nextBytes(bytes);
    }

    @Setup(Level.Iteration)
    public void setupBytes() {
        crc32.reset();
    }

    @Benchmark
    public void testCRC32Update() {
        crc32.update(bytes, 0, count);
    }
}
