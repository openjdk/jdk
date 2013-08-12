/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

import java.lang.management.RuntimeMXBean;
import java.lang.management.ManagementFactory;

/* @test TestMetaspaceMemoryPool
 * @bug 8000754
 * @summary Tests that a MemoryPoolMXBeans is created for metaspace and that a
 *          MemoryManagerMXBean is created.
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-UseCompressedOops TestMetaspaceMemoryPool
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-UseCompressedOops -XX:MaxMetaspaceSize=60m TestMetaspaceMemoryPool
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UseCompressedOops -XX:+UseCompressedClassPointers TestMetaspaceMemoryPool
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UseCompressedOops -XX:+UseCompressedClassPointers -XX:CompressedClassSpaceSize=60m TestMetaspaceMemoryPool
 */
public class TestMetaspaceMemoryPool {
    public static void main(String[] args) {
        verifyThatMetaspaceMemoryManagerExists();
        verifyMemoryPool(getMemoryPool("Metaspace"), isFlagDefined("MaxMetaspaceSize"));

        if (runsOn64bit()) {
            if (usesCompressedOops()) {
                MemoryPoolMXBean cksPool = getMemoryPool("Compressed Class Space");
                verifyMemoryPool(cksPool, true);
            }
        }
    }

    private static boolean runsOn64bit() {
        return !System.getProperty("sun.arch.data.model").equals("32");
    }

    private static boolean usesCompressedOops() {
        return isFlagDefined("+UseCompressedOops");
    }

    private static boolean isFlagDefined(String name) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> args = runtimeMxBean.getInputArguments();
        for (String arg : args) {
            if (arg.startsWith("-XX:" + name)) {
                return true;
            }
        }
        return false;
    }

    private static void verifyThatMetaspaceMemoryManagerExists() {
        List<MemoryManagerMXBean> managers = ManagementFactory.getMemoryManagerMXBeans();
        for (MemoryManagerMXBean manager : managers) {
            if (manager.getName().equals("Metaspace Manager")) {
                return;
            }
        }

        throw new RuntimeException("Expected to find a metaspace memory manager");
    }

    private static MemoryPoolMXBean getMemoryPool(String name) {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getName().equals(name)) {
                return pool;
            }
        }

        throw new RuntimeException("Expected to find a memory pool with name " + name);
    }

    private static void verifyMemoryPool(MemoryPoolMXBean pool, boolean isMaxDefined) {
        MemoryUsage mu = pool.getUsage();
        assertDefined(mu.getInit(), "init");
        assertDefined(mu.getUsed(), "used");
        assertDefined(mu.getCommitted(), "committed");

        if (isMaxDefined) {
            assertDefined(mu.getMax(), "max");
        } else {
            assertUndefined(mu.getMax(), "max");
        }
    }

    private static void assertDefined(long value, String name) {
        assertTrue(value != -1, "Expected " + name + " to be defined");
    }

    private static void assertUndefined(long value, String name) {
        assertEquals(value, -1, "Expected " + name + " to be undefined");
    }

    private static void assertEquals(long actual, long expected, String msg) {
        assertTrue(actual == expected, msg);
    }

    private static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }
}
