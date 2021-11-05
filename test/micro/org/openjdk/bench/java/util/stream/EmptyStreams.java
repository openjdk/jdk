/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.stream;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * Benchmark for checking that the new empty stream
 * implementations are faster than the old way of creating
 * empty streams from empty spliterators.
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class EmptyStreams {
    private static OptionalInt addStreamFilters(Stream<String> stream) {
        return stream
            .filter(Objects::nonNull)
            .filter(s -> s.length() > 0)
            .mapToInt(Integer::parseInt)
            .map(i -> i * 2)
            .mapToLong(i -> i + 1000)
            .mapToDouble(i -> i * 3.5)
            .boxed()
            .mapToLong(Double::intValue)
            .mapToInt(d -> (int)d)
            .max();
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamSupport() {
        return addStreamFilters(
            StreamSupport.stream(
                Spliterators.emptySpliterator(), false));
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamEmpty() {
        return addStreamFilters(Stream.empty());
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamOf() {
        return addStreamFilters(Stream.of());
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithArrayListStream() {
        return addStreamFilters(new ArrayList<String>().stream());
    }
}