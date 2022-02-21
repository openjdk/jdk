/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

package org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class IdentityHashMapBench {
    private Supplier<Map<Object, Object>> mapSupplier;
    private Object[] objects;
    // orders, 2 * i th is key, 2* i+1 th is value
    private Object[] orders;

    @Param("1000000")
    private int orderSize;

    @Param("100000")
    private int size;

    @Setup @TearDown
    public void setup() {
        mapSupplier = IdentityHashMap::new;

        {
            final int size = this.size;
            final Object[] objects = new Object[size];
            for (int i = 0; i < size; i++) {
                objects[i] = new Object();
            }
            this.objects = objects;
        }

        {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            final int poolSize = this.size;
            final Object[] objects = this.objects;
            final int size = this.orderSize;
            final Object[] orders = new Object[size];
            for (int i = 0; i < size; i++) {
                orders[i] = objects[rnd.nextInt(poolSize)];
            }
            this.orders = orders;
        }
    }

    @Benchmark
    public int putBench(Blackhole blackhole) {
        var map = mapSupplier.get();
        final Object[] data = this.objects;
        final int len = data.length;
        for (int i = 0; i < len; i += 2) {
            blackhole.consume(map.put(data[i], data[i + 1]));
        }
        return map.size();
    }

    @Benchmark
    public int putIfAbsentBench(Blackhole blackhole) {
        var map = mapSupplier.get();
        final Object[] data = this.objects;
        final int len = data.length;
        for (int i = 0; i < len; i += 2) {
            blackhole.consume(map.putIfAbsent(data[i], data[i + 1]));
        }
        return map.size();
    }
}
