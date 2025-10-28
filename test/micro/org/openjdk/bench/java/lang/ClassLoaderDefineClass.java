/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;

/**
 * Tests java.lang.ClassLoader.defineClass(ByteBuffer)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
public class ClassLoaderDefineClass {

    private byte[] classBytes;
    private ByteBuffer directBuffer;
    private ByteBuffer heapBuffer;

    @Setup(Level.Iteration)
    public void setup() throws Exception {
        classBytes = getTestClassBytes();
        directBuffer = Arena.ofConfined()
            .allocate(classBytes.length)
            .asByteBuffer()
            .put(classBytes)
            .flip();
        heapBuffer = ByteBuffer.wrap(classBytes);
    }

    @Benchmark
    public void testDefineClassByteBufferHeap(Blackhole bh) throws Exception {
        bh.consume(new DummyClassLoader().defineClassFromHeapBuffer(heapBuffer));
    }

    @Benchmark
    public void testDefineClassByteBufferDirect(Blackhole bh) throws Exception {
        bh.consume(new DummyClassLoader().defineClassFromDirectBuffer(directBuffer));
    }

    private static final class DummyClassLoader extends ClassLoader {

        Class<?> defineClassFromHeapBuffer(ByteBuffer bb) throws Exception {
            bb.rewind();
            return defineClass(null, bb, null);
        }

        Class<?> defineClassFromDirectBuffer(ByteBuffer bb) throws Exception {
            bb.rewind();
            return defineClass(null, bb, null);
        }
    }

    private static byte[] getTestClassBytes() throws Exception {
        final String source = """
        public class Greeting {
            public String hello() {
                return "Hello";
            }
        }
        """;
        // (externally) compiled content of the above source, represented as hex
        final String classBytesHex = """
        cafebabe0000004600110a000200030700040c000500060100106a617661
        2f6c616e672f4f626a6563740100063c696e69743e010003282956080008
        01000548656c6c6f07000a0100084772656574696e67010004436f646501
        000f4c696e654e756d6265725461626c6501000568656c6c6f0100142829
        4c6a6176612f6c616e672f537472696e673b01000a536f7572636546696c
        6501000d4772656574696e672e6a61766100210009000200000000000200
        01000500060001000b0000001d00010001000000052ab70001b100000001
        000c000000060001000000010001000d000e0001000b0000001b00010001
        000000031207b000000001000c000000060001000000030001000f000000
        020010
        """;
        return HexFormat.of().parseHex(classBytesHex.replaceAll("\n", ""));
    }
}
