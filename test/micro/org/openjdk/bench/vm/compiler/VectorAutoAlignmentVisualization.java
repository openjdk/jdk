/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.*;

import java.lang.invoke.*;
import java.lang.foreign.*;

import java.util.concurrent.TimeUnit;

/*

  The purpose of this benchmark is to see the effect of automatic alignment in auto vectorization.
  It is recommended to view the differing results when using SuperWordAutomaticAlignment.

  Without automatic alignment, i.e. SuperWordAutomaticAlignment=0, we may get a plot like below, for bench1L1S:

  OFFSET_STORE
  ^
  | ###############|X
  | ---------------0-  <--- store aligned
  | ##############X|#
  | #############X#|#
  | ############X##|#
  | ###########X###|#
  | ##########X####|#
  | #########X#####|#
  | ########X######|#
  | #######X#######|#
  | ######X########|#
  | #####X#########|#
  | ####X##########|#
  | ###X###########|#
  | ##X############|#
  | #X#############|#
  | X##############|#
    ---OFFSET_LOAD ---->

                   ^
     loads aligned |

  #: lowest performance, both misaligned and also relatively misaligned.
  X: low performance, both misaligned but relatively aligned.
  |: medium performance, load aligned, store misaligned.
  -: good performance, load misaligned, store aligned.
  0: extreme performance, load and store aligned.

  Why is case "-" better than "|"? I.e. why are misaligned stores worse than misaligned loads?
  Misalignment means that a load or store goes over a cache line, and is split into two loads
  or stores. Most CPU's can execute 2 loads and 1 store per cycle, that is at least a partial
  explanation why we are more limited on stores than loads.
  No splitting, full alignment -> 1 load  and 1 store
  Split load, store aligned    -> 2 loads and 1 store
  Split store, load aligned    -> 1 load  and 2 stores

  The warmup and measurement time is relatively short, but the benchmark already takes 25 min
  to go over the whole grid. This leads to some noise, but the pattern is very visible visually.
  Hence: this benchmark is more for visualization than for regression testing.
  For regression testing, please look at the related VectorAutoAlignment benchmark.

  If you want to turn the JMH results into a table, then you may use this Java code.

    import java.io.*;
    import java.util.ArrayList;

    public class Extract {
        record Cell(int x, int y, float t) {}

        public static void main(String[] args) throws Exception {
            String fileName = args[0];
            System.out.println("Loading from file: " + fileName);

            ArrayList<Cell> cells = new ArrayList<>();

            try(BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                for(String line; (line = br.readLine()) != null; ) {
                    System.out.println(line);
                    String[] parts = line.split("[ ]+");
                    if (parts.length != 11) { continue; }
                    System.out.println(String.join(" ", parts));
                    int x = Integer.parseInt(parts[2]);
                    int y = Integer.parseInt(parts[3]);
                    float t = Float.parseFloat(parts[7]);
                    System.out.println("x=" + x + ", y=" + y + ", t=" + t);
                    cells.add(new Cell(x, y, t));
                }
            }

            int maxX = cells.stream().mapToInt(c -> c.x).max().getAsInt();
            int maxY = cells.stream().mapToInt(c -> c.y).max().getAsInt();
            float[][] grid = new float[maxX + 1][maxY + 1];

            for (Cell c : cells) {
                grid[c.x][c.y] = c.t;
            }

            for (int x = maxY; x >= 0; x--) {
                for (int y = 0; y <= maxY; y++) {
                    System.out.print(String.format("%.5f ", grid[x][y]));
                }
                System.out.println();
            }
            System.out.println("x-axis  (->)  LOAD_OFFSET");
            System.out.println("y-axis  (up)  STORE_OFFSET");
        }
    }

 */

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1)
public class VectorAutoAlignmentVisualization {
    @Param({"2560"})
    public int SIZE;

    @Param({  "0",   "1",   "2",   "3",   "4",   "5",   "6",   "7",   "8",   "9",
             "10",  "11",  "12",  "13",  "14",  "15",  "16",  "17",  "18",  "19",
             "20",  "21",  "22",  "23",  "24",  "25",  "26",  "27",  "28",  "29",
             "30",  "31"})
    public int OFFSET_LOAD;

    @Param({  "0",   "1",   "2",   "3",   "4",   "5",   "6",   "7",   "8",   "9",
             "10",  "11",  "12",  "13",  "14",  "15",  "16",  "17",  "18",  "19",
             "20",  "21",  "22",  "23",  "24",  "25",  "26",  "27",  "28",  "29",
             "30",  "31"})
    public int OFFSET_STORE;

    @Param({"2000"})
    public int DISTANCE;

    // To get compile-time constants for OFFSET_LOAD, OFFSET_STORE, and DISTANCE
    static final MutableCallSite MUTABLE_CONSTANT_OFFSET_LOAD = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_OFFSET_LOAD_HANDLE = MUTABLE_CONSTANT_OFFSET_LOAD.dynamicInvoker();
    static final MutableCallSite MUTABLE_CONSTANT_OFFSET_STORE = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_OFFSET_STORE_HANDLE = MUTABLE_CONSTANT_OFFSET_STORE.dynamicInvoker();
    static final MutableCallSite MUTABLE_CONSTANT_DISTANCE = new MutableCallSite(MethodType.methodType(int.class));
    static final MethodHandle MUTABLE_CONSTANT_DISTANCE_HANDLE = MUTABLE_CONSTANT_DISTANCE.dynamicInvoker();

    private MemorySegment ms;

    @Setup
    public void init() throws Throwable {
        long totalSize = 4L * SIZE + 4L * DISTANCE;
        long alignment = 4 * 1024; // 4k = page size
        ms = Arena.ofAuto().allocate(totalSize, alignment);

        MethodHandle offset_load_con = MethodHandles.constant(int.class, OFFSET_LOAD);
        MUTABLE_CONSTANT_OFFSET_LOAD.setTarget(offset_load_con);
        MethodHandle offset_store_con = MethodHandles.constant(int.class, OFFSET_STORE);
        MUTABLE_CONSTANT_OFFSET_STORE.setTarget(offset_store_con);
        MethodHandle distance_con = MethodHandles.constant(int.class, DISTANCE);
        MUTABLE_CONSTANT_DISTANCE.setTarget(distance_con);
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int offset_load_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_OFFSET_LOAD_HANDLE.invokeExact();
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int offset_store_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_OFFSET_STORE_HANDLE.invokeExact();
    }

    @CompilerControl(CompilerControl.Mode.INLINE)
    private int distance_con() throws Throwable {
        return (int) MUTABLE_CONSTANT_DISTANCE_HANDLE.invokeExact();
    }

    @Benchmark
    public void bench1L1S() throws Throwable {
        int offset_load = offset_load_con();
        int offset_store = offset_store_con();
        int distance = distance_con();
        // Note: the offsets and distance are compile-time constants, which means
        //       we can already prove non-aliasing of loads and stores at compile
        //       time, which allows vectorization even without any aliasing runtime
        //       checks.
        for (int i = 0; i < SIZE - /* slack for offset */ 32; i++) {
            int v = ms.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_load + 4L * distance);
            ms.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * i + 4L * offset_store, v);
        }
    }
}
