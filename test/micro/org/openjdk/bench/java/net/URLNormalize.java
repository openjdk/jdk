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
package org.openjdk.bench.java.net;

import java.net.URL;
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
public class URLNormalize {
    @Param({"16", "1024", "8192"})
    public int count;

    private String noDots;                    // foo/foo/foo/foo/foo
    private String leadingDots;               // ././././././foo
    private String nestedDotDot;              // foo/foo/../../foo
    private String relativeLeadingDotDot;     // ../../../../foo
    private String absoluteLeadingDotDot;     // /../../../../foo
    private static final URL base;
    static {
        try {
            base = new URL("http://localhost/x");
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    @Setup
    public void setup() {
        String names = "foo/".repeat(count);
        String dotDots = "../".repeat(count);

        noDots = names;
        leadingDots = "./".repeat(count) + "foo";
        nestedDotDot = names + dotDots + "foo";
        relativeLeadingDotDot = dotDots + "foo";
        absoluteLeadingDotDot = "/" + dotDots + "foo";
    }

    @Benchmark
    public URL noDots() throws Exception {
        return new URL(base, noDots);
    }

    @Benchmark
    public URL leadingDots() throws Exception {
        return new URL(base, leadingDots);
    }

    @Benchmark
    public URL nestedDotDot() throws Exception {
        return new URL(base, nestedDotDot);
    }

    @Benchmark
    public URL relativeLeadingDotDot() throws Exception {
        return new URL(base, relativeLeadingDotDot);
    }

    @Benchmark
    // this method does not normalize
    public URL absoluteLeadingDotDot() throws Exception {
        return new URL(base, absoluteLeadingDotDot);
    }
}
