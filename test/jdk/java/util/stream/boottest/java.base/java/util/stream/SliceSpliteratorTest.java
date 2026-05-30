/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
package java.util.stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Spliterator;
import java.util.SpliteratorTestHelper;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @bug 8012987
 */
public class SliceSpliteratorTest extends LoggingTestCase {

    static class UnorderedContentAsserter<T> implements SpliteratorTestHelper.ContentAsserter<T> {
        Collection<T> source;

        UnorderedContentAsserter(Collection<T> source) {
            this.source = source;
        }

        @Override
        public void assertContents(Collection<T> actual, Collection<T> expected, boolean isOrdered) {
            if (isOrdered) {
                assertEquals(expected, actual);
            }
            else {
                assertEquals(expected.size(), actual.size());
                assertTrue(source.containsAll(actual));
            }
        }
    }

    interface SliceTester {
        void test(int size, int skip, int limit);
    }

    public static Stream<Arguments> sliceSpliteratorDataProvider() {
        List<Arguments> data = new ArrayList<>();

        // SIZED/SUBSIZED slice spliterator

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Integer> source =  IntStream.range(0, size).boxed().collect(toList());

                SpliteratorTestHelper.testSpliterator(() -> {
                    Spliterator<Integer> s = Arrays.spliterator(source.stream().toArray(Integer[]::new));

                    return new StreamSpliterators.SliceSpliterator.OfRef<>(s, skip, limit);
                });
            };
            data.add(Arguments.of("StreamSpliterators.SliceSpliterator.OfRef", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Integer> source =  IntStream.range(0, size).boxed().collect(toList());

                SpliteratorTestHelper.testIntSpliterator(() -> {
                    Spliterator.OfInt s = Arrays.spliterator(source.stream().mapToInt(i->i).toArray());

                    return new StreamSpliterators.SliceSpliterator.OfInt(s, skip, limit);
                });
            };
            data.add(Arguments.of("StreamSpliterators.SliceSpliterator.OfInt", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Long> source =  LongStream.range(0, size).boxed().collect(toList());

                SpliteratorTestHelper.testLongSpliterator(() -> {
                    Spliterator.OfLong s = Arrays.spliterator(source.stream().mapToLong(i->i).toArray());

                    return new StreamSpliterators.SliceSpliterator.OfLong(s, skip, limit);
                });
            };
            data.add(Arguments.of("StreamSpliterators.SliceSpliterator.OfLong", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Double> source =  LongStream.range(0, size).asDoubleStream().boxed().collect(toList());

                SpliteratorTestHelper.testDoubleSpliterator(() -> {
                    Spliterator.OfDouble s = Arrays.spliterator(source.stream().mapToDouble(i->i).toArray());

                    return new StreamSpliterators.SliceSpliterator.OfDouble(s, skip, limit);
                });
            };
            data.add(Arguments.of("StreamSpliterators.SliceSpliterator.OfLong", r));
        }


        // Unordered slice spliterator

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Integer> source =  IntStream.range(0, size).boxed().collect(toList());
                final UnorderedContentAsserter<Integer> uca = new UnorderedContentAsserter<>(source);

                SpliteratorTestHelper.testSpliterator(() -> {
                    Spliterator<Integer> s = Arrays.spliterator(source.stream().toArray(Integer[]::new));

                    return new StreamSpliterators.UnorderedSliceSpliterator.OfRef<>(s, skip, limit);
                }, uca);
            };
            data.add(Arguments.of("StreamSpliterators.UnorderedSliceSpliterator.OfRef", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Integer> source =  IntStream.range(0, size).boxed().collect(toList());
                final UnorderedContentAsserter<Integer> uca = new UnorderedContentAsserter<>(source);

                SpliteratorTestHelper.testIntSpliterator(() -> {
                    Spliterator.OfInt s = Arrays.spliterator(source.stream().mapToInt(i->i).toArray());

                    return new StreamSpliterators.UnorderedSliceSpliterator.OfInt(s, skip, limit);
                }, uca);
            };
            data.add(Arguments.of("StreamSpliterators.UnorderedSliceSpliterator.OfInt", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Long> source =  LongStream.range(0, size).boxed().collect(toList());
                final UnorderedContentAsserter<Long> uca = new UnorderedContentAsserter<>(source);

                SpliteratorTestHelper.testLongSpliterator(() -> {
                    Spliterator.OfLong s = Arrays.spliterator(source.stream().mapToLong(i->i).toArray());

                    return new StreamSpliterators.UnorderedSliceSpliterator.OfLong(s, skip, limit);
                }, uca);
            };
            data.add(Arguments.of("StreamSpliterators.UnorderedSliceSpliterator.OfLong", r));
        }

        {
            SliceTester r = (size, skip, limit) -> {
                final Collection<Double> source =  LongStream.range(0, size).asDoubleStream().boxed().collect(toList());
                final UnorderedContentAsserter<Double> uca = new UnorderedContentAsserter<>(source);

                SpliteratorTestHelper.testDoubleSpliterator(() -> {
                    Spliterator.OfDouble s = Arrays.spliterator(LongStream.range(0, SIZE).asDoubleStream().toArray());

                    return new StreamSpliterators.UnorderedSliceSpliterator.OfDouble(s, skip, limit);
                }, uca);
            };
            data.add(Arguments.of("StreamSpliterators.UnorderedSliceSpliterator.OfLong", r));
        }

        return data.stream();
    }

    static final int SIZE = 256;

    static final int STEP = 32;

    @ParameterizedTest
    @MethodSource("sliceSpliteratorDataProvider")
    public void testSliceSpliterator(String description, SliceTester r) {
        setContext("size", SIZE);
        for (int skip = 0; skip < SIZE; skip += STEP) {
            setContext("skip", skip);
            for (int limit = 0; limit < SIZE; limit += STEP) {
                setContext("limit", skip);
                r.test(SIZE, skip, limit);
            }
        }
    }
}
