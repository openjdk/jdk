/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Tests the overheads of I/O API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(time=2, iterations=5)
@Measurement(time=3, iterations=5)
@Fork(value=2, jvmArgs="-Xmx1g")
public class FileOpen {

    public String normalFile = "/test/dir/file/name.txt";
    public String root = "/";
    public String trailingSlash = "/test/dir/file//name.txt";
    public String notNormalizedFile = "/test/dir/file//name.txt";

    @Benchmark
    public void mix(Blackhole bh) {
        bh.consume(new File(normalFile));
        bh.consume(new File(root));
        bh.consume(new File(trailingSlash));
        bh.consume(new File(notNormalizedFile));
    }

    @Benchmark
    public File normalized() {
        return new File(normalFile);
    }

    @Benchmark
    public File trailingSlash() {
        return new File(trailingSlash);
    }

    @Benchmark
    public File notNormalized() {
        return new File(notNormalizedFile);
    }
}
