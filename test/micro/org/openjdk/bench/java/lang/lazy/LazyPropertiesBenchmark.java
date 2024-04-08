/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.lazy;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Benchmark measuring Lazy performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--enable-preview")
public class LazyPropertiesBenchmark {

    private static final Function<String, String> FUNCTION = s -> switch (s) {
        case "int" -> "1";
        case "long" -> "2";
        case "String" -> "Hello";
        default -> throw new IllegalArgumentException();
    };

    private static final String KEY = "int";

    private static final Set<String> PROPERTY_KEYS = Set.of("int", "long", "String");

    private static final Map<String, Lazy<String>> LAZY_MAP = Lazy.ofMap(PROPERTY_KEYS);
    private static final Properties PROPERTIES;
    private static final Map<String, String> CHM;

    static {
        PROPERTIES = new Properties();
        PROPERTY_KEYS.forEach(k -> PROPERTIES.setProperty(k, FUNCTION.apply(k)));
        CHM = new ConcurrentHashMap<>();
        PROPERTY_KEYS.forEach(k -> CHM.put(k, FUNCTION.apply(k)));
    }

    @Benchmark
    public int chm() {
        return Integer.valueOf(CHM.get(KEY));
    }

    @Benchmark
    public int prop() {
        return Integer.valueOf(PROPERTIES.getProperty(KEY));
    }

    @Benchmark
    public int lazy() {
        return Integer.valueOf(Lazy.computeIfUnset(LAZY_MAP, KEY, FUNCTION));
    }

}
