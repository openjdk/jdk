/*
 * Copyright (c) 2020, 2022, Red Hat Inc. All rights reserved.
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

package micro.org.openjdk.bench.java.io;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 4, warmups = 0)
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 2, time = 2)
@State(Scope.Thread)
public class DataInputStreamTest {
    private final int size = 1024;

    private ByteArrayInputStream bais;

    @Setup(Level.Iteration)
    public void setup() {
        byte[] bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        bais = new ByteArrayInputStream(bytes);
    }

    @Benchmark
    public void readChar(Blackhole bh) throws Exception {
        bais.reset();
        DataInputStream dis = new DataInputStream(bais);
        for (int i = 0; i < size / 2; i++) {
            bh.consume(dis.readChar());
        }
    }

    @Benchmark
    public void readInt(Blackhole bh) throws Exception {
        bais.reset();
        DataInputStream dis = new DataInputStream(bais);
        for (int i = 0; i < size / 4; i++) {
            bh.consume(dis.readInt());
        }
    }
}
