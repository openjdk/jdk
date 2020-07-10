/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test TestMemoryManagerMXBean
 * @requires vm.gc.Z
 * @summary Test ZGC memory manager MXBean
 * @modules java.management
 * @run main/othervm -XX:+UseZGC -Xmx128M TestMemoryManagerMXBean
 */

import java.lang.management.ManagementFactory;

public class TestMemoryManagerMXBean {
    private static void checkName(String name) throws Exception {
        if (name == null || name.length() == 0) {
            throw new Exception("Invalid name");
        }
    }

    public static void main(String[] args) throws Exception {
        int zgcMemoryManagers = 0;
        int zgcMemoryPools = 0;

        for (final var memoryManager : ManagementFactory.getMemoryManagerMXBeans()) {
            final var memoryManagerName = memoryManager.getName();
            checkName(memoryManagerName);

            System.out.println("MemoryManager: " + memoryManagerName);

            if (memoryManagerName.equals("ZGC")) {
                zgcMemoryManagers++;
            }

            for (final var memoryPoolName : memoryManager.getMemoryPoolNames()) {
                checkName(memoryPoolName);

                System.out.println("   MemoryPool:   " + memoryPoolName);

                if (memoryPoolName.equals("ZHeap")) {
                    zgcMemoryPools++;
                }
            }

            if (zgcMemoryManagers != zgcMemoryPools) {
                throw new Exception("MemoryManagers/MemoryPools mismatch");
            }
        }

        if (zgcMemoryManagers != 1) {
            throw new Exception("All MemoryManagers not found");
        }

        if (zgcMemoryPools != 1) {
            throw new Exception("All MemoryPools not found");
        }
    }
}
