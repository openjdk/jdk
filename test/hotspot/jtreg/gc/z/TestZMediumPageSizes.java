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

package gc.z;

/**
 * @test TestZMediumPageSizes
 * @requires vm.gc.Z
 * @summary Test TestZMediumPageSizes heap reservation / commits interactions.
 * @library / /test/lib
 * @run driver gc.z.TestZMediumPageSizes
 */

import static gc.testlibrary.Allocation.blackHole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

public class TestZMediumPageSizes {
    private static final int XmxInM = 1024;
    private static final int XmsInM = 512;
    static class Test {
        private static final int K = 1024;
        private static final int M = 1024 * K;
        private static final int SMALL_PAGE_SIZE = 2 * M;
        private static final int SMALL_OBJECTS_PER_PAGE = 20;
        private static final int SMALL_OBJECT_SIZE = SMALL_PAGE_SIZE / SMALL_OBJECTS_PER_PAGE;
        private static final int MEDIUM_OBJECT_SIZE = 3 * M;
        private static final int CHUNK_COUNT = 32;
        private static final int CHUNK_LENGTH = SMALL_OBJECTS_PER_PAGE * 3;
        private static final int ITERATIONS = 10;

        public static void main(String[] args) throws Exception {
            for (int page = 0; page < ITERATIONS; ++page) {
                var keep = new ArrayList<byte[]>();
                try {
                    System.gc();
                    for (;;) {
                        // Allocate objects on small pages until we're unable to
                        keep.add(new byte[SMALL_OBJECT_SIZE]);
                    }
                } catch (OutOfMemoryError oome) {
                    // Assume that most small allocations were allocated in
                    // consecutive addresses. By making CHUNK_LENGTH consecutive
                    // number of small objects eligible for collection, at least
                    // CHUNK_LENGTH - 1 pages should get eligible for relocation
                    // and be returned to the mapped cache. We do this for
                    // CHUNK_COUNT number of chunks which should ensure that
                    // there is enough free memory to allocate a full sized
                    // medium page. These chunks are spread out, so as not to be
                    // consecutive, so that a full sized medium page can only be
                    // created harvesting some number of smaller memory chunks.
                    for (int i = 0; i < CHUNK_COUNT; i++) {
                        for (int j = 0; j < CHUNK_LENGTH; j++) {
                            keep.set(2 * i * CHUNK_LENGTH + j, null);
                        }
                    }

                    // Reclaim memory from pages
                    System.gc();

                    // Allocate a new medium page (may happen through flush/harvest)
                    blackHole(new byte[MEDIUM_OBJECT_SIZE]);
                }

                blackHole(keep);
            }

        }
    }

    private static OutputAnalyzer createAndExecuteJava(String... extraOptions) throws Exception {
        final var options = new ArrayList<String>(List.of(
            "-XX:+UseZGC",
            "-Xms" + XmsInM + "M",
            "-Xmx" + XmxInM + "M",
            "-Xlog:gc,gc+init,gc+heap=debug"));
        Collections.addAll(options, extraOptions);
        options.add(Test.class.getName());
        OutputAnalyzer oa = ProcessTools
                .executeTestJava(options)
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldHaveExitValue(0);
        return oa;
    }

    private static void runTestDefault() throws Exception {
        var oa = createAndExecuteJava(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+ZUseMediumPageSizeRange");
        oa.shouldContain("Page Size Medium: Range");
        oa.shouldNotContain("Mapped Cache Harvested");
    }

    private static void runTestFixedPageSize() throws Exception {
        var oa = createAndExecuteJava(
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-ZUseMediumPageSizeRange");
        oa.shouldNotContain("Page Size Medium: Range");
        oa.shouldContain("Mapped Cache Harvested");
    }

    public static void main(String[] args) throws Exception {
        runTestDefault();
        runTestFixedPageSize();
    }
}
