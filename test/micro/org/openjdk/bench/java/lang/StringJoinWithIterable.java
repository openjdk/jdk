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
package org.openjdk.bench.java.lang;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations=20, time=500, timeUnit=TimeUnit.MILLISECONDS)
@Measurement(iterations=10, time=1000, timeUnit=TimeUnit.MILLISECONDS)
@Fork(3)
public class StringJoinWithIterable {
    private ArrayList<String> arrayList;
    private LinkedHashSet<String> linkedHashSet;
    private LinkedBlockingDeque<String> blockingDeque;
    private LinkedTransferQueue<String> transferQueue;
    private Iterable<String> iterable;

    private static final int STRING_COUNT = 5000000;

    @Setup
    public void setup() {
        ArrayList<String> list = arrayList = new ArrayList<>(STRING_COUNT);
        for (int i = 0; i < STRING_COUNT; i++) {
            list.add(Integer.toString(i));
        }
        linkedHashSet = new LinkedHashSet<>(list);
        blockingDeque = new LinkedBlockingDeque<>(list);
        transferQueue = new LinkedTransferQueue<>(list);
        iterable = new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return list.iterator();
            }
        };
    }

    @Benchmark
    public String joinWithArrayList() {
        return String.join(" ", arrayList);
    }

    @Benchmark
    public String joinWithLinkedHashSet() {
        return String.join(" ", linkedHashSet);
    }

    @Benchmark
    public String joinWithLinkedBlockingDeque() {
        return String.join(" ", blockingDeque);
    }

    @Benchmark
    public String joinWithLinkedTransferQueue() {
        return String.join(" ", transferQueue);
    }

    @Benchmark
    public String joinWithIterable() {
        return String.join(" ", iterable);
    }
}
