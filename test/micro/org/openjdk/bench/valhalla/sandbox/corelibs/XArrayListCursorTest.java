/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 */
package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs;

import java.util.List;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 3)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class XArrayListCursorTest {
    @Param({"100000"})
    public static int size;

    private static final String constantString = "abc";

    private static XArrayList<String> list;

    @Setup
    public void setup() {
        list = new XArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(constantString);
        }
    }

    @Benchmark
    public void getViaCursorWhileLoop(Blackhole blackhole) {
        InlineCursor<String> cur = list.cursor();
        while (cur.exists()) {
            blackhole.consume(cur.get());
            cur = cur.advance();
        }
    }

    @Benchmark
    public void getViaCursorForLoop(Blackhole blackhole) {
        for (InlineCursor<String> cur = list.cursor();
             cur.exists();
             cur = cur.advance()) {
            blackhole.consume(cur.get());
        }
    }

    @Benchmark
    public void getViaIterator(Blackhole blackhole) {
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            blackhole.consume(it.next());
        }
    }

    @Benchmark
    public void getViaIteratorCurs(Blackhole blackhole) {
        Iterator<String> it = list.iteratorCurs();
        while (it.hasNext()) {
            blackhole.consume(it.next());
        }
    }

    @Benchmark
    public void getViaArray(Blackhole blackhole) {
        for (int i = 0; i < list.size(); i++) {
            blackhole.consume(list.get(i));
        }
    }

}
