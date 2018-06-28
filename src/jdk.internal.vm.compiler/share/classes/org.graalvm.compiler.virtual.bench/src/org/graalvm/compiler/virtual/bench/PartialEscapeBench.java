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


package org.graalvm.compiler.virtual.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import org.graalvm.compiler.microbenchmarks.graal.GraalBenchmark;

public class PartialEscapeBench extends GraalBenchmark {

    private static class Thing {
        final int id;
        final String name;

        Thing(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @State(Scope.Thread)
    public static class ThingsCache {

        private Thing[] cache = new Thing[100];

        public Thing getOrAdd(Thing input) {
            if (cache[input.id] == null) {
                cache[input.id] = input;
            }
            return cache[input.id];
        }
    }

    @Benchmark
    @Warmup(iterations = 30)
    public String benchPartialEscape(ThingsCache cache) {
        Thing thing = cache.getOrAdd(new Thing(42, "the answer!"));
        return thing.name;
    }
}
