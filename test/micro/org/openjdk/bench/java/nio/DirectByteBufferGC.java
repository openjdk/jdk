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
package org.openjdk.bench.java.nio;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {"-Xmx1g", "-Xms1g", "-XX:+AlwaysPreTouch"})
public class DirectByteBufferGC {

    @Param({"16384", "65536", "262144", "1048576", "4194304"})
    int count;

    // Make sure all buffers are reachable and available for GC. Buffers
    // directly reference their Cleanables, so we do not want to provide
    // excess GC parallelism opportunities here, this is why reference
    // buffers from a linked list.
    //
    // This exposes the potential GC parallelism problem in Cleaner lists.
    LinkedList<ByteBuffer> buffers;

    @Setup
    public void setup() {
        buffers = new LinkedList<>();
        for (int c = 0; c < count; c++) {
            buffers.add(ByteBuffer.allocateDirect(1));
        }
    }

    @Benchmark
    public void test() {
        System.gc();
    }

}
