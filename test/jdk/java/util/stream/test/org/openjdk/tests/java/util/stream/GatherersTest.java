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
package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import static java.util.stream.LambdaTestHelpers.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.fail;

/**
 * GatherersTest
 *
 * @author Viktor Klang
 */
@Test
public class GatherersTest extends OpTestCase {
    final static List<Integer> TEST_STREAM_SIZES = List.of(0, 1, 10, 33, 99, 9999);

    public void testGroupedAPIandContract() {
        // Groups must be greater than 0
        assertThrows(IllegalArgumentException.class, () -> Gatherers.grouped(0));

        for (var parallel : List.of(false, true)) {
            for (var i : TEST_STREAM_SIZES) {
                // We're already covering less-than-one scenarios above
                if (i > 0) {
                    //Test greating a group of the same size as the stream
                    {
                        final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                        final var result = stream.gather(Gatherers.grouped(i)).toList();
                        assertEquals(result.size(), 1);
                        assertEquals(countTo(i), result.get(0));
                    }

                    // Tests that the layout of the returned data is as expected
                    for (var groupSize : List.of(1, 2, 3, 10)) {
                        final var expectLastGroupSize = i % groupSize == 0 ? groupSize : i % groupSize;
                        final var expectedSize = (i / groupSize) + ((i % groupSize == 0) ? 0 : 1);

                        int expectedElement = 0;
                        int currentGroup = 0;

                        final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();

                        for (var group : stream.gather(Gatherers.grouped(groupSize)).toList()) {
                            ++currentGroup;
                            assertEquals(group.size(), currentGroup < expectedSize ? groupSize : expectLastGroupSize);
                            for (var element : group)
                                assertEquals(element.intValue(), ++expectedElement);
                        }

                        assertEquals(currentGroup, expectedSize);
                    }
                }
            }
        }
    }

    public void testSlidingAPIandContract() {
        // Groups must be greater than 0
        assertThrows(IllegalArgumentException.class, () -> Gatherers.sliding(0));

        for (var parallel : List.of(false, true)) {
            for (var i : TEST_STREAM_SIZES) {
                // We're already covering less-than-one scenarios above
                if (i > 0) {
                    //Test greating a group larger than the size of the stream
                    {
                        final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                        final var result = stream.gather(Gatherers.sliding(i + 1)).toList();
                        assertEquals(result.size(), 1);
                        assertEquals(countTo(i), result.get(0));
                    }

                    // Tests that the layout of the returned data is as expected
                    for (var groupSize : List.of(1, 2, 3, 10)) {
                        final var expectLastGroupSize = Math.max(1, Math.min(i, groupSize - 1));
                        final var expectedNumberOfGroups = Math.max(1, (i + 2 - Math.max(2, groupSize)));

                        int expectedElement = 0;
                        int currentGroup = 0;

                        final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();

                        for (var group : stream.gather(Gatherers.sliding(groupSize)).toList()) {
                            ++currentGroup;
                            assertEquals(group.size(), currentGroup < expectedNumberOfGroups ? groupSize : expectLastGroupSize);
                            for (var element : group) {
                                //assertEquals(element.intValue(), ++expectedElement);

                                // rewind for the sliding motion
                                expectedElement -= (group.size() - 1);
                            }
                        }

                        assertEquals(currentGroup, expectedNumberOfGroups);
                    }
                }
            }
        }
    }

    public void testFoldAPIandContract() {
        // Verify prereqs
        assertThrows(NullPointerException.class, () -> Gatherers.<String,String>fold(null, (state, next) -> state));
        assertThrows(NullPointerException.class, () -> Gatherers.<String,String>fold(() -> "", null));

        for (var parallel : List.of(false, true)) {
            for (var i : TEST_STREAM_SIZES) {
                final var expectedResult = List.of(countTo(i).stream().reduce("", (acc, next) -> acc + next, (l,r) -> { throw new IllegalStateException(); }));

                final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                var result = stream.gather(Gatherers.fold(() -> "", (acc, next) -> acc + next)).toList();

                assertEquals(result, expectedResult);
            }
        }
    }

    public void testPeekOrderedAPIandContract() {
        // Verify prereqs
        assertThrows(NullPointerException.class, () -> Gatherers.<String>peekOrdered(null));

        for (var parallel : List.of(false, true)) {
            for (var i : TEST_STREAM_SIZES) {
                final var expectedResult = countTo(i);

                final var peeked = new ConcurrentLinkedQueue();
                final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                var result = stream.gather(Gatherers.peekOrdered(peeked::add)).toList();

                assertEquals(result, expectedResult);
                assertEquals(result, List.of(peeked.toArray()));
            }
        }
    }

    public void testPeekAPIandContract() {
        // Verify prereqs
        assertThrows(NullPointerException.class, () -> Gatherers.<String>peek(null));

        for (var parallel : List.of(false, true)) {
            for (var i : TEST_STREAM_SIZES) {
                final var expectedResult = Set.of(countTo(i).toArray());

                final var peeked = new ConcurrentLinkedQueue();
                final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                var result = stream.gather(Gatherers.peek(peeked::add)).collect(Collectors.toSet());

                assertEquals(result, expectedResult);
                assertEquals(result, Set.of(peeked.toArray()));
            }
        }
    }

    public void testMapConcurrentAPIandContract() {
        // Verify prereqs
        assertThrows(IllegalArgumentException.class, () -> Gatherers.<String, String>mapConcurrent(0, s -> s));
        assertThrows(NullPointerException.class, () -> Gatherers.<String, String>mapConcurrent(2, null));

        for (var parallel : List.of(false, true)) {

            {
                final var stream = parallel ? Stream.of(1).parallel() : Stream.of(1);

                assertThrows(RuntimeException.class,
                        () -> stream.gather(Gatherers.<Integer, Integer>mapConcurrent(2, x -> {
                            throw new RuntimeException();
                        })).toList());
            }

            for (var i : TEST_STREAM_SIZES) {
                final var expectedResult = countTo(i).stream().map(x -> x*x).toList();
                final var stream = parallel ? countTo(i).stream().parallel() : countTo(i).stream();
                var result = stream.gather(Gatherers.mapConcurrent(2, x -> x*x)).toList();

                assertEquals(result, expectedResult);
            }
        }
    }
}
