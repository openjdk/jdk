/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Test
public class UnorderedTest extends OpTestCase {

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testTerminalOps(String name, TestData<Integer, Stream<Integer>> data) {
        testTerminal(data, s -> { s.forEach(x -> { }); return 0; });

        testTerminal(data, s -> s.findAny(), (a, b) -> assertEquals(a.isPresent(), b.isPresent()));

        testTerminal(data, s -> s.anyMatch(e -> true));
    }


    private <T, R> void testTerminal(TestData<T, Stream<T>> data, Function<Stream<T>, R> terminalF) {
        testTerminal(data, terminalF, LambdaTestHelpers::assertContentsEqual);
    }

    static class WrappingUnaryOperator<S> implements UnaryOperator<S> {

        final boolean isLimit;
        final UnaryOperator<S> uo;

        WrappingUnaryOperator(UnaryOperator<S> uo) {
            this(uo, false);
        }

        WrappingUnaryOperator(UnaryOperator<S> uo, boolean isLimit) {
            this.uo = uo;
            this.isLimit = isLimit;
        }

        @Override
        public S apply(S s) {
            return uo.apply(s);
        }
    }

    static <S> WrappingUnaryOperator<S> wrap(UnaryOperator<S> uo) {
        return new WrappingUnaryOperator<>(uo);
    }

    static <S> WrappingUnaryOperator<S> wrap(UnaryOperator<S> uo, boolean isLimit) {
        return new WrappingUnaryOperator<>(uo, isLimit);
    }

    @SuppressWarnings("rawtypes")
    private List permutationOfFunctions =
            LambdaTestHelpers.perm(Arrays.<WrappingUnaryOperator<Stream<Object>>>asList(
                    wrap(s -> s.sorted()),
                    wrap(s -> s.distinct()),
                    wrap(s -> s.limit(5), true)
            ));

    @SuppressWarnings("unchecked")
    private <T, R> void testTerminal(TestData<T, Stream<T>> data,
                                     Function<Stream<T>, R> terminalF,
                                     BiConsumer<R, R> equalityAsserter) {
        testTerminal(data, terminalF, equalityAsserter, permutationOfFunctions, StreamShape.REFERENCE);
    }

    //

    @Test(dataProvider = "IntStreamTestData", dataProviderClass = IntStreamTestDataProvider.class)
    public void testIntTerminalOps(String name, TestData.OfInt data) {
        testIntTerminal(data, s -> { s.forEach(x -> { }); return 0; });
        testIntTerminal(data, s -> s.findAny(), (a, b) -> assertEquals(a.isPresent(), b.isPresent()));
        testIntTerminal(data, s -> s.anyMatch(e -> true));
    }


    private <T, R> void testIntTerminal(TestData.OfInt data, Function<IntStream, R> terminalF) {
        testIntTerminal(data, terminalF, LambdaTestHelpers::assertContentsEqual);
    }

    private List<List<WrappingUnaryOperator<IntStream>>> intPermutationOfFunctions =
            LambdaTestHelpers.perm(Arrays.asList(
                    wrap(s -> s.sorted()),
                    wrap(s -> s.distinct()),
                    wrap(s -> s.limit(5), true)
            ));

    private <R> void testIntTerminal(TestData.OfInt data,
                                     Function<IntStream, R> terminalF,
                                     BiConsumer<R, R> equalityAsserter) {
        testTerminal(data, terminalF, equalityAsserter, intPermutationOfFunctions, StreamShape.INT_VALUE);
    }

    //

    @Test(dataProvider = "LongStreamTestData", dataProviderClass = LongStreamTestDataProvider.class)
    public void testLongTerminalOps(String name, TestData.OfLong data) {
        testLongTerminal(data, s -> { s.forEach(x -> { }); return 0; });
        testLongTerminal(data, s -> s.findAny(), (a, b) -> assertEquals(a.isPresent(), b.isPresent()));
        testLongTerminal(data, s -> s.anyMatch(e -> true));
    }


    private <T, R> void testLongTerminal(TestData.OfLong data, Function<LongStream, R> terminalF) {
        testLongTerminal(data, terminalF, LambdaTestHelpers::assertContentsEqual);
    }

    private List<List<WrappingUnaryOperator<LongStream>>> longPermutationOfFunctions =
            LambdaTestHelpers.perm(Arrays.asList(
                    wrap(s -> s.sorted()),
                    wrap(s -> s.distinct()),
                    wrap(s -> s.limit(5), true)
            ));

    private <R> void testLongTerminal(TestData.OfLong data,
                                      Function<LongStream, R> terminalF,
                                      BiConsumer<R, R> equalityAsserter) {
        testTerminal(data, terminalF, equalityAsserter, longPermutationOfFunctions, StreamShape.LONG_VALUE);
    }

    //

    @Test(dataProvider = "DoubleStreamTestData", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDoubleTerminalOps(String name, TestData.OfDouble data) {
        testDoubleTerminal(data, s -> { s.forEach(x -> { }); return 0; });
        testDoubleTerminal(data, s -> s.findAny(), (a, b) -> assertEquals(a.isPresent(), b.isPresent()));
        testDoubleTerminal(data, s -> s.anyMatch(e -> true));
    }


    private <T, R> void testDoubleTerminal(TestData.OfDouble data, Function<DoubleStream, R> terminalF) {
        testDoubleTerminal(data, terminalF, LambdaTestHelpers::assertContentsEqual);
    }

    private List<List<WrappingUnaryOperator<DoubleStream>>> doublePermutationOfFunctions =
            LambdaTestHelpers.perm(Arrays.asList(
                    wrap(s -> s.sorted()),
                    wrap(s -> s.distinct()),
                    wrap(s -> s.limit(5), true)
            ));

    private <R> void testDoubleTerminal(TestData.OfDouble data,
                                        Function<DoubleStream, R> terminalF,
                                        BiConsumer<R, R> equalityAsserter) {
        testTerminal(data, terminalF, equalityAsserter, doublePermutationOfFunctions, StreamShape.DOUBLE_VALUE);
    }

    //

    private <T, S extends BaseStream<T, S>, R> void testTerminal(TestData<T, S> data,
                                                                 Function<S, R> terminalF,
                                                                 BiConsumer<R, R> equalityAsserter,
                                                                 List<List<WrappingUnaryOperator<S>>> pFunctions,
                                                                 StreamShape shape) {
        CheckClearOrderedOp<T> checkClearOrderedOp = new CheckClearOrderedOp<>(shape);
        for (List<WrappingUnaryOperator<S>> f : pFunctions) {
            @SuppressWarnings("unchecked")
            UnaryOperator<S> fi = interpose(f, (S s) -> (S) chain(s, checkClearOrderedOp));
            withData(data).
                    terminal(fi, terminalF).
                    equalator(equalityAsserter).
                    exercise();
        }

        CheckSetOrderedOp<T> checkSetOrderedOp = new CheckSetOrderedOp<>(shape);
        for (List<WrappingUnaryOperator<S>> f : pFunctions) {
            @SuppressWarnings("unchecked")
            UnaryOperator<S> fi = interpose(f, (S s) -> (S) chain(s, checkSetOrderedOp));
            withData(data).
                    terminal(fi, s -> terminalF.apply(s.sequential())).
                    equalator(equalityAsserter).
                    exercise();
        }
    }

    static class CheckClearOrderedOp<T> implements StatelessTestOp<T, T> {
        private final StreamShape shape;

        CheckClearOrderedOp(StreamShape shape) {
            this.shape = shape;
        }

        @Override
        public StreamShape outputShape() {
            return shape;
        }

        @Override
        public StreamShape inputShape() {
            return shape;
        }

        @Override
        public Sink<T> opWrapSink(int flags, boolean parallel, Sink<T> sink) {
            if (parallel) {
                assertTrue(StreamOpFlag.ORDERED.isCleared(flags));
            }

            return sink;
        }
    }

    static class CheckSetOrderedOp<T> extends CheckClearOrderedOp<T> {

        CheckSetOrderedOp(StreamShape shape) {
            super(shape);
        }

        @Override
        public Sink<T> opWrapSink(int flags, boolean parallel, Sink<T> sink) {
            assertTrue(StreamOpFlag.ORDERED.isKnown(flags) || StreamOpFlag.ORDERED.isPreserved(flags));

            return sink;
        }
    }

    private <T, S extends BaseStream<T, S>>
    UnaryOperator<S> interpose(List<WrappingUnaryOperator<S>> fs, UnaryOperator<S> fi) {
        int l = -1;
        for (int i = 0; i < fs.size(); i++) {
            if (fs.get(i).isLimit) {
                l = i;
            }
        }

        final int lastLimitIndex = l;
        return s -> {
            if (lastLimitIndex == -1)
                s = fi.apply(s);
            for (int i = 0; i < fs.size(); i++) {
                s = fs.get(i).apply(s);
                if (i >= lastLimitIndex) {
                    s = fi.apply(s);
                }
            }
            return s;
        };
    }
}
