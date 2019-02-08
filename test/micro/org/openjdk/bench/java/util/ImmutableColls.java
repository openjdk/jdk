/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Micros for the various collections implemented in
 * java.util.ImmutableCollections
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ImmutableColls {

    public static String[] STRINGS = {"hi", "all", "of", "you"};

    public static List<String> l0 = List.of();
    public static List<String> l1 = List.of(Arrays.copyOf(STRINGS, 1));
    public static List<String> l2 = List.of(Arrays.copyOf(STRINGS, 2));
    public static List<String> l3 = List.of(Arrays.copyOf(STRINGS, 3));
    public static List<String> l4 = List.of(Arrays.copyOf(STRINGS, 4));

    public static Set<String> s0 = Set.copyOf(l0);
    public static Set<String> s1 = Set.copyOf(l1);
    public static Set<String> s2 = Set.copyOf(l2);
    public static Set<String> s3 = Set.copyOf(l3);
    public static Set<String> s4 = Set.copyOf(l4);

    public static Map<String, String> m0 = Map.of();
    public static Map<String, String> m1 = Map.of(STRINGS[0], STRINGS[0]);
    public static Map<String, String> m2 = Map.of(STRINGS[0], STRINGS[0],
                                                  STRINGS[1], STRINGS[1]);
    public static Map<String, String> m3 = Map.of(STRINGS[0], STRINGS[0],
                                                  STRINGS[1], STRINGS[1],
                                                  STRINGS[2], STRINGS[2]);
    public static Map<String, String> m4 = Map.of(STRINGS[0], STRINGS[0],
                                                  STRINGS[1], STRINGS[1],
                                                  STRINGS[2], STRINGS[2],
                                                  STRINGS[3], STRINGS[3]);

    public static List<String> a0 = new ArrayList<>(l0);
    public static List<String> a1 = new ArrayList<>(l1);
    public static List<String> a2 = new ArrayList<>(l2);
    public static List<String> a3 = new ArrayList<>(l3);
    public static List<String> a4 = new ArrayList<>(l4);

    public static final List<String> fl0 = List.of();
    public static final List<String> fl1 = List.copyOf(l1);
    public static final List<String> fl2 = List.copyOf(l2);
    public static final List<String> fl3 = List.copyOf(l3);
    public static final List<String> fl4 = List.copyOf(l4);

    public static final List<String> fsl0 = fl0.subList(0, 0);
    public static final List<String> fsl1 = fl2.subList(1, 2);
    public static final List<String> fsl2 = fl3.subList(0, 2);
    public static final List<String> fsl3 = fl4.subList(0, 3);

    public static final Set<String> fs0 = Set.copyOf(l0);
    public static final Set<String> fs1 = Set.copyOf(l1);
    public static final Set<String> fs2 = Set.copyOf(l2);
    public static final Set<String> fs3 = Set.copyOf(l3);
    public static final Set<String> fs4 = Set.copyOf(l4);

    public static final Map<String, String> fm0 = Map.copyOf(m0);
    public static final Map<String, String> fm1 = Map.copyOf(m1);
    public static final Map<String, String> fm2 = Map.copyOf(m2);
    public static final Map<String, String> fm3 = Map.copyOf(m3);
    public static final Map<String, String> fm4 = Map.copyOf(m4);

    public static final List<String> fa0 = new ArrayList<>(l0);
    public static final List<String> fa1 = new ArrayList<>(l1);
    public static final List<String> fa2 = new ArrayList<>(l2);
    public static final List<String> fa3 = new ArrayList<>(l3);
    public static final List<String> fa4 = new ArrayList<>(l4);

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sumSizesList() {
        return sizeOf(l0) +
                sizeOf(l1) +
                sizeOf(l2) +
                sizeOf(l3) +
                sizeOf(l4);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int finalSumSizesList() {
        return sizeOf(fl0) +
                sizeOf(fl1) +
                sizeOf(fl2) +
                sizeOf(fl3) +
                sizeOf(fl4);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int sumSizesArrayList() {
        return sizeOf2(a0) +
                sizeOf2(a1) +
                sizeOf2(a2) +
                sizeOf2(a3) +
                sizeOf2(a4);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int finalSumSizesArrayList() {
        return sizeOf2(fa0) +
                sizeOf2(fa1) +
                sizeOf2(fa2) +
                sizeOf2(fa3) +
                sizeOf2(fa4);
    }

    public int sizeOf2(List<String> list) {
        return list.size();
    }

    public int sizeOf(List<String> list) {
        return list.size();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int getFromList() {
        return get(l1, 0).length() +
                get(l2, 1).length() +
                get(l3, 2).length() +
                get(l4, 3).length();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int finalGetFromList() {
        return get(fl1, 0).length() +
                get(fl2, 1).length() +
                get(fl3, 2).length() +
                get(fl4, 3).length();
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toArrayFromSet(Blackhole bh) {
        bh.consume(fs4.toArray());
        bh.consume(s1.toArray());
        bh.consume(s3.toArray());
        bh.consume(fs2.toArray());
        bh.consume(s0.toArray());
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toArrayFromMap(Blackhole bh) {
        bh.consume(fm4.entrySet().toArray());
        bh.consume(m1.entrySet().toArray());
        bh.consume(m3.entrySet().toArray());
        bh.consume(fm2.entrySet().toArray());
        bh.consume(m0.entrySet().toArray());
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toArrayFromList(Blackhole bh) {
        bh.consume(fl4.toArray());
        bh.consume(fl1.toArray());
        bh.consume(l3.toArray());
        bh.consume(l0.toArray());
        bh.consume(fsl3.toArray());
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toTypedArrayFromSet(Blackhole bh) {
        bh.consume(fs4.toArray(new String[0]));
        bh.consume(s1.toArray(new String[0]));
        bh.consume(s3.toArray(new String[0]));
        bh.consume(fs2.toArray(new String[0]));
        bh.consume(s0.toArray(new String[0]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toTypedArrayFromMap(Blackhole bh) {
        bh.consume(fm4.entrySet().toArray(new Map.Entry[0]));
        bh.consume(m1.entrySet().toArray(new Map.Entry[0]));
        bh.consume(m3.entrySet().toArray(new Map.Entry[0]));
        bh.consume(fm2.entrySet().toArray(new Map.Entry[0]));
        bh.consume(m0.entrySet().toArray(new Map.Entry[0]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void toTypedArrayFromList(Blackhole bh) {
        bh.consume(fl4.toArray(new String[0]));
        bh.consume(fl1.toArray(new String[0]));
        bh.consume(l3.toArray(new String[0]));
        bh.consume(l0.toArray(new String[0]));
        bh.consume(fsl3.toArray(new String[0]));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void copyOfLists(Blackhole bh) {
        bh.consume(List.copyOf(fl1));
        bh.consume(List.copyOf(fl4));
        bh.consume(List.copyOf(fsl2));
        bh.consume(List.copyOf(fsl3));
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int finalGetFromArrayList() {
        return get2(fa1, 0).length() +
                get2(fa2, 1).length() +
                get2(fa3, 2).length() +
                get2(fa4, 3).length();
    }

    public String get2(List<String> list, int idx) {
        return list.get(idx);
    }

    public String get(List<String> list, int idx) {
        return list.get(idx);
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Set<String> createSetOf() {
        return Set.of(STRINGS);
    }
}
