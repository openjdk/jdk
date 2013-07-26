/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Test scenarios for reference streams.
 *
 * Each scenario is provided with a data source, a function that maps a fresh
 * stream (as provided by the data source) to a new stream, and a sink to
 * receive results.  Each scenario describes a different way of computing the
 * stream contents.  The test driver will ensure that all scenarios produce
 * the same output (modulo allowable differences in ordering).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public enum StreamTestScenario implements OpTestCase.BaseStreamTestScenario {

    STREAM_FOR_EACH(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            Stream<U> s = m.apply(data.stream());
            if (s.isParallel()) {
                s = s.sequential();
            }
            s.forEach(b);
        }
    },

    // Collec to list
    STREAM_COLLECT(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (U t : m.apply(data.stream()).collect(Collectors.toList())) {
                b.accept(t);
            }
        }
    },

    // To array
    STREAM_TO_ARRAY(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (Object t : m.apply(data.stream()).toArray()) {
                b.accept((U) t);
            }
        }
    },

    // Wrap as stream, and iterate in pull mode
    STREAM_ITERATOR(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (Iterator<U> seqIter = m.apply(data.stream()).iterator(); seqIter.hasNext(); )
                b.accept(seqIter.next());
        }
    },

    // Wrap as stream, and spliterate then iterate in pull mode
    STREAM_SPLITERATOR(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (Spliterator<U> spl = m.apply(data.stream()).spliterator(); spl.tryAdvance(b); ) { }
        }
    },

    // Wrap as stream, spliterate, then split a few times mixing advances with forEach
    STREAM_SPLITERATOR_WITH_MIXED_TRAVERSE_AND_SPLIT(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            SpliteratorTestHelper.mixedTraverseAndSplit(b, m.apply(data.stream()).spliterator());
        }
    },

    // Wrap as stream, and spliterate then iterate in pull mode
    STREAM_SPLITERATOR_FOREACH(false) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            m.apply(data.stream()).spliterator().forEachRemaining(b);
        }
    },

    // Wrap as parallel stream + sequential
    PAR_STREAM_SEQUENTIAL_FOR_EACH(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            m.apply(data.parallelStream()).sequential().forEach(b);
        }
    },

    // Wrap as parallel stream + forEachOrdered
    PAR_STREAM_FOR_EACH_ORDERED(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            // @@@ Want to explicitly select ordered equalator
            m.apply(data.parallelStream()).forEachOrdered(b);
        }
    },

    // Wrap as stream, and spliterate then iterate sequentially
    PAR_STREAM_SPLITERATOR(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (Spliterator<U> spl = m.apply(data.parallelStream()).spliterator(); spl.tryAdvance(b); ) { }
        }
    },

    // Wrap as stream, and spliterate then iterate sequentially
    PAR_STREAM_SPLITERATOR_FOREACH(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            m.apply(data.parallelStream()).spliterator().forEachRemaining(b);
        }
    },

    // Wrap as parallel stream + toArray
    PAR_STREAM_TO_ARRAY(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (Object t : m.apply(data.parallelStream()).toArray())
                b.accept((U) t);
        }
    },

    // Wrap as parallel stream, get the spliterator, wrap as a stream + toArray
    PAR_STREAM_SPLITERATOR_STREAM_TO_ARRAY(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            Stream<U> s = m.apply(data.parallelStream());
            Spliterator<U> sp = s.spliterator();
            Stream<U> ss = StreamSupport.stream(() -> sp,
                                                StreamOpFlag.toCharacteristics(OpTestCase.getStreamFlags(s))
                                                | (sp.getExactSizeIfKnown() < 0 ? 0 : Spliterator.SIZED), true);
            for (Object t : ss.toArray())
                b.accept((U) t);
        }
    },

    // Wrap as parallel stream + toArray and clear SIZED flag
    PAR_STREAM_TO_ARRAY_CLEAR_SIZED(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            S_IN pipe1 = (S_IN) OpTestCase.chain(data.parallelStream(),
                                                 new FlagDeclaringOp(StreamOpFlag.NOT_SIZED, data.getShape()));
            Stream<U> pipe2 = m.apply(pipe1);

            for (Object t : pipe2.toArray())
                b.accept((U) t);
        }
    },

    // Wrap as parallel + collect
    PAR_STREAM_COLLECT(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (U u : m.apply(data.parallelStream()).collect(Collectors.toList()))
                b.accept(u);
        }
    },

    // Wrap sequential as parallel, + collect
    STREAM_TO_PAR_STREAM_COLLECT(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (U u : m.apply(data.stream().parallel()).collect(Collectors.toList()))
                b.accept(u);
        }
    },

    // Wrap parallel as sequential,, + collect
    PAR_STREAM_TO_STREAM_COLLECT(true) {
        <T, U, S_IN extends BaseStream<T, S_IN>>
        void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m) {
            for (U u : m.apply(data.parallelStream().sequential()).collect(Collectors.toList()))
                b.accept(u);
        }
    },
    ;

    private boolean isParallel;

    StreamTestScenario(boolean isParallel) {
        this.isParallel = isParallel;
    }

    public StreamShape getShape() {
        return StreamShape.REFERENCE;
    }

    public boolean isParallel() {
        return isParallel;
    }

    public <T, U, S_IN extends BaseStream<T, S_IN>, S_OUT extends BaseStream<U, S_OUT>>
    void run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, S_OUT> m) {
        _run(data, b, (Function<S_IN, Stream<U>>) m);
    }

    abstract <T, U, S_IN extends BaseStream<T, S_IN>>
    void _run(TestData<T, S_IN> data, Consumer<U> b, Function<S_IN, Stream<U>> m);

}
