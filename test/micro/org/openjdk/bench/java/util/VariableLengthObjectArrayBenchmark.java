/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.VariableLengthObjectArray;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class VariableLengthObjectArrayBenchmark {

    @Param(value = { "4", "" + (16 * 1024), "" + (16 * 1024 * 1024) })
    public int finalListSize;

    public String input = new String();

    public List<String> list;

    @Param(value = { "array", "linked", "jdk_internal" })
    public String listType;

    /**
     * This test is prone to the loop being optimized; calculating each byte avoids
     * that.
     *
     */
    @Measurement(iterations = 4, time = 1, timeUnit = TimeUnit.SECONDS)
    @Benchmark
    public void add() {
        list = getNewList();
        for (int i = 0; i < finalListSize; i++) {
            list.add(input);
        }
    }

    public List<String> populatedList;

    @Setup
    public void prepareTestObjects() {
        populatedList = getNewList();
        for (int i = 0; i < finalListSize; i++) {
            populatedList.add(input);
        }
    }

    private List<String> getNewList() {
        switch (listType) {
        case "array":
            return new ArrayList<>();
        case "linked":
            return new LinkedList<>();
        case "jdk_internal":
            return VariableLengthObjectArray.asList(String.class);
        default:
            throw new RuntimeException("Unrecognized type parameter: " + listType);
        }

    }

    @Benchmark
    public void iterate() {
        Iterator<String> iterator = populatedList.iterator();
        while (iterator.hasNext()) {
            String entry = iterator.next();
            if (entry == null) {
                throw new RuntimeException("Benchmark encountered invalid result");
            }
        }
    }

    @Benchmark
    public Object toArray() {
        return populatedList.toArray();
    }
}
