/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio.file;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(3)
@State(Scope.Thread)
public class PathNormalize {
    @Param({"16", "1024", "8192"})
    public int count;

    private Path noDots;                    // foo/foo/foo/foo/foo
    private Path leadingDots;               // ././././././foo
    private Path nestedDotDot;              // foo/foo/../../foo
    private Path relativeLeadingDotDot;     // ../../../../foo
    private Path absoluteLeadingDotDot;     // /../../../../foo

    @Setup
    public void setup() {
        Path root = Path.of(System.getProperty("user.dir")).getRoot();
        String names = ("foo" + File.separator).repeat(count);
        String dotDots = (".." + File.separator).repeat(count);

        noDots = Path.of(names);
        leadingDots = Path.of(("." + File.separator).repeat(count) + "foo");
        nestedDotDot = Path.of(names + dotDots + "foo");
        relativeLeadingDotDot = Path.of(dotDots + "foo");
        absoluteLeadingDotDot = root.resolve(dotDots + "foo");
    }

    @Benchmark
    public Path noDots() {
        return noDots.normalize();
    }

    @Benchmark
    public Path leadingDots() {
        return leadingDots.normalize();
    }

    @Benchmark
    public Path nestedDotDot() {
        return nestedDotDot.normalize();
    }

    @Benchmark
    public Path relativeLeadingDotDot() {
        return relativeLeadingDotDot.normalize();
    }

    @Benchmark
    public Path absoluteLeadingDotDot() {
        return absoluteLeadingDotDot.normalize();
    }
}
