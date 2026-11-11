/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs.mapprotos;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@Fork(1)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class MapBase {

    @Param({
            "11",
            "767",
            "1572863",
    })
    public int size;

    @Param({
//            "0",
            "42",
    })
    public int seed;

    @Param(value = {
            "java.util.HashMap",
            "org.openjdk.bench.valhalla.corelibs.mapprotos.HashMap",
//            "org.openjdk.bench.valhalla.corelibs.mapprotos.XHashMap",
//            "java.util.HashMap0",
    })
    public String mapType;

    public Random rnd;
    public Integer[] keys;
    public Integer[] nonKeys;

    public void init(int size) {
        Integer[] all;
        if (seed != 0) {
            rnd = new Random(seed);
            all = rnd.ints().distinct().limit(size * 2).boxed().toArray(Integer[]::new);
            Collections.shuffle(Arrays.asList(all), rnd);
        } else {
            rnd = new Random();
            all = IntStream.range(0, size * 2).boxed().toArray(Integer[]::new);
            Collections.shuffle(Arrays.asList(all));
        }
        keys = Arrays.copyOfRange(all, 0, size);
        nonKeys = Arrays.copyOfRange(all, size, size * 2);
    }

    void TearDown(Map<Integer, Integer> map) {
        try {
            Method m = map.getClass().getMethod("dumpStats", java.io.PrintStream.class);
            m.invoke(map, System.out);
        } catch (Throwable nsme) {
            System.out.println("Stats not available:");
        }
    }
}
