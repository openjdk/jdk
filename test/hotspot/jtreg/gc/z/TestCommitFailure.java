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

/*
 * @test id=Normal
 * @requires vm.gc.Z & vm.debug
 * @summary Test ZGC graceful failure when a commit fails
 * @library / /test/lib
 * @run driver gc.z.TestCommitFailure
 */

/*
 * @test id=ZFakeNUMA
 * @requires vm.gc.Z & vm.debug
 * @library / /test/lib
 * @summary Test ZGC graceful failure when a commit fails (with ZFakeNUMA)
 * @run driver gc.z.TestCommitFailure -XX:ZFakeNUMA=16
 */

import jdk.test.lib.process.ProcessTools;

import static gc.testlibrary.Allocation.blackHole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestCommitFailure {
    static final int K = 1024;
    static final int M = 1024 * K;

    static final int XMS = 128 * M;
    static final int XMX = 512 * M;

    static class Test {
        static final int LARGE_ALLOC = 256 * M;
        static final int SMALL_GARBAGE = 256 * M;
        static final int SMALL_LIVE = 128 * M;

        // Allocates at least totalLive bytes of objects and add them to list.
        static void allocLive(List<Object> list, int totalLive) {
            final int largePageAllocationSize = 6 * M;
            for (int live = 0; live < totalLive; live += largePageAllocationSize) {
                list.add(new byte[largePageAllocationSize - K]);
            }
        }

        // Allocates at least totalGarbage bytes of garbage large pages.
        static void allocGarbage(int totalGarbage) {
            final int largePageAllocationSize = 6 * M;
            for (int garbage = 0; garbage < totalGarbage; garbage += largePageAllocationSize) {
                blackHole(new byte[largePageAllocationSize - K]);
            }
        }

        public static void main(String[] args) {
            final var list = new ArrayList<Object>();
            try {
                // Fill heap with small live objects
                allocLive(list, SMALL_LIVE);
                // Fill with small garbage objects
                allocGarbage(SMALL_GARBAGE);
                // Allocate large objects where commit fails until an OOME is thrown
                while (true) {
                    list.add(new byte[LARGE_ALLOC - K]);
                }
            } catch (OutOfMemoryError oome) {}
            blackHole(list);
        }
    }

    public static void main(String[] args) throws Exception {
        final int xmxInM = XMX / M;
        final int xmsInM = XMS / M;
        final var arguments = new ArrayList(Arrays.asList(args));
        arguments.addAll(List.of(
            "-XX:+UseZGC",
            "-Xlog:gc+init",
            "-XX:ZFailLargerCommits=" + XMS,
            "-Xms" + xmsInM + "M",
            "-Xmx" + xmxInM + "M",
            Test.class.getName()));

        ProcessTools.executeTestJava(arguments)
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldHaveExitValue(0);
    }
}
