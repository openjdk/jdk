/*
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This is a stress test for allocating from a single MetaspaceArena from
 *  multiple threads, optionally with reserve limit (mimicking the non-expandable CompressedClassSpace)
 * or commit limit (mimicking MaxMetaspaceSize).
 *
 * The test threads will start to allocate from the Arena, and occasionally deallocate.
 * The threads run with a safety allocation max; if reached (or, if the underlying arena
 * hits either commit or reserve limit, if given) they will switch to deallocation and then
 * kind of float at the allocation ceiling, alternating between allocation and deallocation.
 *
 * We test with various flags, to exercise all 3 reclaim policies (none, balanced (default)
 * and aggressive) as well as one run with allocation guards enabled.
 *
 * We also set MetaspaceVerifyInterval very low to trigger many verifications in debug vm.
 *
 */

/*
 * @test id=debug-default
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @key randomness
 * @requires (vm.debug == true)
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=400
 *      -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:VerifyMetaspaceInterval=10
 *      TestMetaspaceAllocationMT2 3
 */

/*
 * @test id=debug-default-long-manual
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @key randomness
 * @requires (vm.debug == true)
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/manual
 *      -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      -XX:VerifyMetaspaceInterval=10
 *      TestMetaspaceAllocationMT2 10
 */

/*
 * @test id=ndebug-default
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @key randomness
 * @requires (vm.debug == false)
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm/timeout=400
 *      -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      TestMetaspaceAllocationMT2 3
 */

import jdk.test.lib.Unit;

public class TestMetaspaceAllocationMT2 {

    public static void main(String[] args) throws Exception {

        final long testAllocationCeiling = 48L * Unit.M.size();
        final int numThreads = 4;
        final int seconds = Integer.parseInt(args[0]);

        for (int i = 0; i < 3; i ++) {

            long commitLimit = (i == 1) ? 2L * Unit.M.size() : 0;

            // Note: reserve limit must be a multiple of Metaspace::reserve_alignment_words()
            long reserveLimit = (i == 2) ? Settings.ROOT_CHUNK_WORD_SIZE * 16: 0;

            System.out.println("#### Test: ");
            System.out.println("#### testAllocationCeiling: " + testAllocationCeiling);
            System.out.println("#### numThreads: " + numThreads);
            System.out.println("#### seconds: " + seconds);
            System.out.println("#### commitLimit: " + commitLimit);
            System.out.println("#### reserveLimit: " + reserveLimit);

            MetaspaceTestContext context = new MetaspaceTestContext(commitLimit, reserveLimit);
            MetaspaceTestManyArenasManyThreads test = new MetaspaceTestManyArenasManyThreads(context, testAllocationCeiling, numThreads, seconds);

            try {
                test.runTest();
            } catch (RuntimeException e) {
                System.out.println(e);
                context.printToTTY();
                throw e;
            }

            context.destroy();

            System.out.println("#### Done. ####");
            System.out.println("###############");

        }

    }

}

