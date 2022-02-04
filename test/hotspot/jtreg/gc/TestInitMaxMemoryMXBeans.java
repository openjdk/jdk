/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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
 * @test id=Serial
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.Serial
 * @modules java.management
 * @run main/othervm -XX:+UseSerialGC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

/**
 * @test id=Parallel
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.Parallel
 * @modules java.management
 * @run main/othervm -XX:+UseParallelGC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

/**
 * @test id=G1
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.G1
 * @modules java.management
 * @run main/othervm -XX:+UseG1GC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

/**
 * @test id=Shenandoah
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.Shenandoah
 * @modules java.management
 * @run main/othervm -XX:+UseShenandoahGC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

/**
 * @test id=Z
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.Z
 * @modules java.management
 * @run main/othervm -XX:+UseZGC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

/**
 * @test id=Epsilon
 * @bug 8281254
 * @summary Test memory beans report Init == Max, when Xmx == Xms
 * @requires vm.gc.Epsilon
 * @modules java.management
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC -Xms76m -Xmx76m TestInitMaxMemoryMXBeans
 */

import java.lang.management.*;

public class TestInitMaxMemoryMXBeans {
    public static void main(String[] args) throws Exception {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long heapInit = memoryMXBean.getHeapMemoryUsage().getInit();
        long heapMax = memoryMXBean.getHeapMemoryUsage().getMax();

        if (heapInit != heapMax) {
            throw new IllegalStateException("Discrepancy: init = " + heapInit + ", max = " + heapMax);
        }
    }
}
