/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default
 * @bug 8284161 8303242
 * @summary Test com.sun.management.ThreadMXBean with virtual threads
 * @library /test/lib
 * @run junit/othervm VirtualThreads
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @library /test/lib
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreads
 */

import java.lang.management.ManagementFactory;
import java.util.concurrent.locks.LockSupport;
import com.sun.management.ThreadMXBean;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

public class VirtualThreads {

    /**
     * Test that ThreadMXBean::getCurrentThreadAllocatedBytes() returns -1 when
     * invoked from a virtual thread.
     */
    @Test
    void testGetCurrentThreadAllocatedBytes() throws Exception {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "Thread memory allocation measurement not supported");

        VThreadRunner.run(() -> {
            assertEquals(-1L, bean.getCurrentThreadAllocatedBytes());
        });
    }

    /**
     * Test that ThreadMXBean.getThreadAllocatedBytes(long) returns -1 when invoked
     * with the thread ID of a virtual thread.
     */
    @Test
    void testGetThreadAllocatedBytes1() {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "Thread memory allocation measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long allocated = bean.getThreadAllocatedBytes(vthread.threadId());
            assertEquals(-1L, allocated);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadAllocatedBytes(long) returns -1 when invoked
     * by a virtual thread with its own thread id.
     */
    @Test
    void testGetThreadAllocatedBytes2() throws Exception {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "Thread memory allocation measurement not supported");

        VThreadRunner.run(() -> {
            long tid = Thread.currentThread().threadId();
            long allocated = bean.getThreadAllocatedBytes(tid);
            assertEquals(-1L, allocated);
        });
    }

    /**
     * Test that ThreadMXBean.getThreadAllocatedBytes(long[]) returns -1 for
     * elements that correspond to a virtual thread.
     */
    @Test
    void testGetThreadAllocatedBytes3() {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadAllocatedMemorySupported(),
                "Thread memory allocation measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid0 = Thread.currentThread().threadId();
            long tid1 = vthread.threadId();
            long[] tids = new long[] { tid0, tid1 };
            long[] allocated = bean.getThreadAllocatedBytes(tids);
            if (Thread.currentThread().isVirtual()) {
                assertEquals(-1L, allocated[0]);
            } else {
                assertTrue(allocated[0] >= 0L);
            }
            assertEquals(-1L, allocated[1]);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadCpuTime(long[]) returns -1 for
     * elements that correspond to a virtual thread.
     */
    @Test
    void testGetThreadCpuTime() {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid0 = Thread.currentThread().threadId();
            long tid1 = vthread.threadId();
            long[] tids = new long[] { tid0, tid1 };
            long[] cpuTimes = bean.getThreadCpuTime(tids);
            if (Thread.currentThread().isVirtual()) {
                assertEquals(-1L, cpuTimes[0]);
            } else {
                assertTrue(cpuTimes[0] >= 0L);
            }
            assertEquals(-1L, cpuTimes[1]);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadUserTime(long[])returns -1 for
     * elements that correspond to a virtual thread.
     */
    @Test
    void testGetThreadUserTime() {
        ThreadMXBean bean = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid0 = Thread.currentThread().threadId();
            long tid1 = vthread.threadId();
            long[] tids = new long[] { tid0, tid1 };
            long[] userTimes = bean.getThreadUserTime(tids);
            if (Thread.currentThread().isVirtual()) {
                assertEquals(-1L, userTimes[0]);
            } else {
                assertTrue(userTimes[0] >= 0L);
            }
            assertEquals(-1L, userTimes[1]);
        } finally {
            LockSupport.unpark(vthread);
        }
    }
}
