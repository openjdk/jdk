/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.microbenchmarks.graal;

import static org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark.Defaults.FORKS;
import static org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark.Defaults.MEASUREMENT_ITERATIONS;
import static org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark.Defaults.WARMUP_ITERATIONS;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;

/**
 * All classes defining Graal benchmarks must subclass this class as it defines the default value
 * for each benchmark option. Individual options can be overridden in the subclasses or by an
 * individual benchmark.
 */
@Warmup(iterations = WARMUP_ITERATIONS)
@Measurement(iterations = MEASUREMENT_ITERATIONS)
@Fork(FORKS)
public class GraalBenchmark {

    public static class Defaults {
        public static final int MEASUREMENT_ITERATIONS = 10;
        public static final int WARMUP_ITERATIONS = 10;
        public static final int FORKS = 1;
    }
}
