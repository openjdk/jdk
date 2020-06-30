/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestUncommit
 * @requires vm.gc.Z & !vm.graal.enabled
 * @summary Test ZGC uncommit unused memory
 * @run main/othervm -XX:+UseZGC -Xlog:gc*,gc+heap=debug,gc+stats=off -Xms128M -Xmx512M -XX:ZUncommitDelay=10 gc.z.TestUncommit true 2
 */

/*
 * @test TestUncommit
 * @requires vm.gc.Z & !vm.graal.enabled
 * @summary Test ZGC uncommit unused memory
 * @run main/othervm -XX:+UseZGC -Xlog:gc*,gc+heap=debug,gc+stats=off -Xms512M -Xmx512M -XX:ZUncommitDelay=10 gc.z.TestUncommit false 1
 */

/*
 * @test TestUncommit
 * @requires vm.gc.Z & !vm.graal.enabled
 * @summary Test ZGC uncommit unused memory
 * @run main/othervm -XX:+UseZGC -Xlog:gc*,gc+heap=debug,gc+stats=off -Xms128M -Xmx512M -XX:ZUncommitDelay=10 -XX:-ZUncommit gc.z.TestUncommit false 1
 */

import java.util.ArrayList;

public class TestUncommit {
    private static final int delay = 10; // seconds
    private static final int allocSize = 200 * 1024 * 1024; // 200M
    private static final int smallObjectSize = 4 * 1024; // 4K
    private static final int mediumObjectSize = 2 * 1024 * 1024; // 2M
    private static final int largeObjectSize = allocSize;

    private static volatile ArrayList<byte[]> keepAlive;

    private static long capacity() {
        return Runtime.getRuntime().totalMemory();
    }

    private static void allocate(int objectSize) {
        keepAlive = new ArrayList<>();
        for (int i = 0; i < allocSize; i+= objectSize) {
            keepAlive.add(new byte[objectSize]);
        }
    }

    private static void reclaim() {
        keepAlive = null;
        System.gc();
    }

    private static void test(boolean enabled, int objectSize) throws Exception {
        final var beforeAlloc = capacity();

        // Allocate memory
        allocate(objectSize);

        final var afterAlloc = capacity();

        // Reclaim memory
        reclaim();

        // Wait shorter than the uncommit delay
        Thread.sleep(delay * 1000 / 2);

        final var beforeUncommit = capacity();

        // Wait longer than the uncommit delay
        Thread.sleep(delay * 1000);

        final var afterUncommit = capacity();

        System.out.println("  Uncommit Enabled: " + enabled);
        System.out.println("    Uncommit Delay: " + delay);
        System.out.println("       Object Size: " + objectSize);
        System.out.println("        Alloc Size: " + allocSize);
        System.out.println("      Before Alloc: " + beforeAlloc);
        System.out.println("       After Alloc: " + afterAlloc);
        System.out.println("   Before Uncommit: " + beforeUncommit);
        System.out.println("    After Uncommit: " + afterUncommit);
        System.out.println();

        // Verify
        if (enabled) {
            if (beforeUncommit == beforeAlloc) {
                throw new Exception("Uncommitted too fast");
            }

            if (afterUncommit >= afterAlloc) {
                throw new Exception("Uncommitted too slow");
            }

            if (afterUncommit < beforeAlloc) {
                throw new Exception("Uncommitted too much");
            }

            if (afterUncommit > beforeAlloc) {
                throw new Exception("Uncommitted too little");
            }
        } else {
            if (afterAlloc > beforeUncommit ||
                afterAlloc > afterUncommit) {
                throw new Exception("Should not uncommit");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        final boolean enabled = Boolean.parseBoolean(args[0]);
        final int iterations = Integer.parseInt(args[1]);

        for (int i = 0; i < iterations; i++) {
            System.out.println("Iteration " + i);
            test(enabled, smallObjectSize);
            test(enabled, mediumObjectSize);
            test(enabled, largeObjectSize);
        }
    }
}
