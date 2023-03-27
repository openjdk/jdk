/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package io;

import org.openjdk.jmh.annotations.*;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 4, warmups = 0)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 5, time = 2)
@State(Scope.Benchmark)
public class BufferedInputStreamBenchmark {

    //read less or more than internal buffer size of 8192 bytes
    @Param({"1024", "16384"})
    private int size;

    private ByteArrayInputStream bais;

    @Setup(Level.Iteration)
    public void setup() {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        bais = new ByteArrayInputStream(bytes);
    }

    /**
     * Read all bytes from an instance of {@link BufferedInputStream}
     * <p>
     * This benchmark demonstrates lazy instantiation of inner {@code byte[]},
     * which is not created when we call {@code InputStream.readAllBytes()}
     * <p>
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8304745">JDK-8304745</a>
     * @see <a href="https://github.com/openjdk/jdk/pull/13150">Pull request for JDK-8304745</a>
     */
    @Benchmark
    public byte[] readAllBytes() throws Exception {
        bais.reset();
        try (var bufferedInputStream = new BufferedInputStream(bais)) {
            return bufferedInputStream.readAllBytes();
        }
    }

    /**
     * Read all bytes from an instance of {@link BufferedInputStream}
     * wrapping another instance of {@link BufferedInputStream}.
     * <p>
     * This benchmark demonstrates lazy instantiation of inner {@code byte[]},
     * which is not created when we call {@code InputStream.readAllBytes()}
     * <p>
     * @see <a href="https://bugs.openjdk.org/browse/JDK-8304745">JDK-8304745</a>
     * @see <a href="https://github.com/openjdk/jdk/pull/13150">Pull request for JDK-8304745</a>
     */
    @Benchmark
    public byte[] readAllBytesCascade() throws Exception {
        bais.reset();
        try (var bufferedInputStream = new BufferedInputStream(new BufferedInputStream(bais))) {
            return bufferedInputStream.readAllBytes();
        }
    }
}
