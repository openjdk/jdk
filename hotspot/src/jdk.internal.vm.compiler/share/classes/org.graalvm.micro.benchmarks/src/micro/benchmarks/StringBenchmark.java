/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks cost of {@link String#indexOf(int)} and {@link String#indexOf(String)}.
 */
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class StringBenchmark extends BenchmarkBase {

    @State(Scope.Benchmark)
    public static class BenchState {
        char ch1 = 'Q';
        char ch2 = 'X';
        String s1 = "Qu";
        String s2 = "ne";

        // Checkstyle: stop
        String lorem = "Lorem ipsum dolor sit amet, consectetur adipisici elit, sed eiusmod tempor incidunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquid ex ea commodi consequat. Quis aute iure reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint obcaecat cupiditat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
        // Checkstyle: resume
    }

    @Benchmark
    @Warmup(iterations = 5)
    public int indexOfChar(BenchState state) {
        return state.lorem.indexOf(state.ch1);
    }

    @Benchmark
    @Warmup(iterations = 5)
    public int indexOfCharNotFound(BenchState state) {
        return state.lorem.indexOf(state.ch2);
    }

    @Benchmark
    @Warmup(iterations = 5)
    public int indexOfString(BenchState state) {
        return state.lorem.indexOf(state.s1);
    }

    @Benchmark
    @Warmup(iterations = 5)
    public int indexOfStringNotFound(BenchState state) {
        return state.lorem.indexOf(state.s2);
    }
}
