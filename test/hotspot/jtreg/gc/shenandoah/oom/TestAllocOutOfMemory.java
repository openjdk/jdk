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
 *
 */

/**
 * @test id=large
 * @summary Test allocation of large objects results in OOM, but will not crash the JVM
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run driver TestAllocOutOfMemory large
 */

/**
 * @test id=heap
 * @summary Test allocation of a heap-sized object results in OOM, but will not crash the JVM
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run driver TestAllocOutOfMemory heap
 */

/**
 * @test id=small
 * @summary Test allocation of small objects results in OOM, but will not crash the JVM
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @run driver TestAllocOutOfMemory small
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAllocOutOfMemory {

    static volatile Object sink;

    public static void work(int size, int count) throws Exception {
        Object[] root = new Object[count];
        sink = root;
        for (int c = 0; c < count; c++) {
            root[c] = new Object[size];
        }
    }

    private static void allocate(String size, int multiplier) throws Exception {
        switch (size) {
            case "large":
                work(1024 * 1024, 16 * multiplier);
                break;
            case "heap":
                work(16 * 1024 * 1024, multiplier);
                break;
            case "small":
                work(1, 16 * 1024 * 1024 * multiplier);
                break;
            default:
                throw new IllegalArgumentException("Usage: test [large|small|heap]");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) {
            // Called from test, size is second argument, heap requested is third
            String size = args[1];
            long spec_heap = Integer.parseInt(args[2]);

            // The actual heap we get may be larger than the one we asked for
            // (particularly in the generational case)
            final long actual_heap = Runtime.getRuntime().maxMemory();
            int multiplier = 1;
            if (actual_heap > spec_heap) {
                // A suitable multiplier is used, so as to allocate an
                // amount appropriate to the larger actual heap size than what
                // was specified.
                multiplier = (int)((actual_heap + spec_heap - 1)/spec_heap);
            }

            allocate(size, multiplier);
            return;
        }

        // Called from jtreg, size is first argument
        String size = args[0];
        {
            int heap = 16*1024*1024;      // -Xmx16m
            expectFailure("-Xmx16m",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:+UseShenandoahGC",
                          TestAllocOutOfMemory.class.getName(),
                          "test", size, Integer.toString(heap));

            expectFailure("-Xmx16m",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational",
                          TestAllocOutOfMemory.class.getName(),
                          "test", size, Integer.toString(heap));
        }

        {
            int heap = 1*1024*1024*1024;  // -Xmx1g
            expectSuccess("-Xmx1g",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:+UseShenandoahGC",
                          TestAllocOutOfMemory.class.getName(),
                          "test", size, Integer.toString(heap));

            expectSuccess("-Xmx1g",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational",
                          TestAllocOutOfMemory.class.getName(),
                          "test", size, Integer.toString(heap));
        }
    }

    private static void expectSuccess(String... args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldNotContain("java.lang.OutOfMemoryError: Java heap space");
    }

    private static void expectFailure(String... args) throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(1);
        analyzer.shouldContain("java.lang.OutOfMemoryError: Java heap space");
    }
}
