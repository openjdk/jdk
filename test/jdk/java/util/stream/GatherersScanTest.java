/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Tests the API and contract of Gatherers.scan
 * @enablePreview
 * @run junit GatherersScanTest
 */

public class GatherersScanTest {

    record Config(int streamSize, boolean parallel) {
        Stream<Integer> stream() {
            var stream = Stream.iterate(1, i -> i + 1).limit(streamSize);
            stream = parallel ? stream.parallel() : stream.sequential();
            return stream;
        }
    }

    static final Stream<Integer> sizes(){
        return Stream.of(0,1,10,33,99,9999);
    }

    static final Stream<Config> sequentialAndParallel(int size) {
        return Stream.of(false, true)
                .map(parallel ->
                        new Config(size, parallel));
    }

    static final Stream<Config> configurations() {
        return sizes().flatMap(i -> sequentialAndParallel(i));
    }

    @Test
    public void throwsNPEWhenStateSupplierIsNull() {
        assertThrows(NullPointerException.class,
                () -> Gatherers.<String, String>scan(null, (state, next) -> state));
    }

    @Test
    public void throwsNPEWhenScannerFunctionIsNull() {
        assertThrows(NullPointerException.class,
                () -> Gatherers.<String, String>scan(() -> "", null));
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void behavesAsExpected(Config config) {
        final List<Long> expectedResult;
        if (config.streamSize() == 0) {
            expectedResult = List.of();
        } else {
            expectedResult = config.stream()
                .sequential()
                .reduce(
                        new LinkedList<Long>(),
                        (acc, next) -> {
                            acc.addLast((acc.isEmpty() ? 0L : acc.getLast()) + next);
                            return acc;
                        },
                        (l, r) -> { throw new IllegalStateException(); }
                );
        }

        final var result = config.stream()
                .gather(Gatherers.scan(() -> 0L, (acc, next) -> acc + next))
                .toList();

        assertEquals(expectedResult, result);
    }
}
