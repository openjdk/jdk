/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284161 8290562 8303242
 * @summary Test java.lang.management.ThreadMXBean with virtual threads
 * @enablePreview
 * @modules java.base/java.lang:+open java.management
 * @library /test/lib
 * @run junit/othervm VirtualThreads
 */

/**
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @enablePreview
 * @modules java.base/java.lang:+open java.management
 * @library /test/lib
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreads
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

public class VirtualThreads {

    /**
     * Test that ThreadMXBean.dumpAllThreads does not include virtual threads.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void testDumpAllThreads(int maxDepth) {
        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] infos = bean.dumpAllThreads(false, false, maxDepth);
            Set<Long> tids = Arrays.stream(infos)
                    .map(ThreadInfo::getThreadId)
                    .collect(Collectors.toSet());

            // current thread should be included
            assertTrue(tids.contains(Thread.currentThread().threadId()));

            // virtual thread should not be included
            assertFalse(tids.contains(vthread.threadId()));
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean::getAllThreadsIds does not include virtual threads.
     */
    @Test
    void testGetAllThreadIds() {
        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long[] tids = ManagementFactory.getThreadMXBean().getAllThreadIds();

            // current thread should be included
            long currentTid = Thread.currentThread().threadId();
            assertTrue(Arrays.stream(tids).anyMatch(tid -> tid == currentTid));

            // virtual thread should not be included
            long vtid = vthread.threadId();
            assertFalse(Arrays.stream(tids).anyMatch(tid -> tid == vtid));
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadInfo(long, maxDepth) returns null for a virtual
     * thread.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void testGetThreadInfo1(int maxDepth) {
        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid = vthread.threadId();
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(tid, maxDepth);
            assertNull(info);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadInfo(long, maxDepth) returns null when invoked
     * by a virtual thread with its own thread id.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void testGetThreadInfo2(int maxDepth) throws Exception {
        VThreadRunner.run(() -> {
            long tid = Thread.currentThread().threadId();
            ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(tid, maxDepth);
            assertNull(info);
        });
    }

    /**
     * Test that ThreadMXBean.getThreadInfo(long[], maxDepth) returns a null ThreadInfo
     * for elements that correspond to a virtual thread.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void testGetThreadInfo3(int maxDepth) {
        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid0 = Thread.currentThread().threadId();
            long tid1 = vthread.threadId();
            long[] tids = new long[] { tid0, tid1 };
            ThreadInfo[] infos = ManagementFactory.getThreadMXBean().getThreadInfo(tids, maxDepth);
            assertEquals(tid0, infos[0].getThreadId());
            assertNull(infos[1]);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadInfo(long[], boolean, boolean, maxDepth) returns
     * a null ThreadInfo for elements that correspond to a virtual thread.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, Integer.MAX_VALUE})
    void testGetThreadInfo4(int maxDepth) {
        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid0 = Thread.currentThread().threadId();
            long tid1 = vthread.threadId();
            long[] tids = new long[] { tid0, tid1 };
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            ThreadInfo[] infos = bean.getThreadInfo(tids, false, false, maxDepth);
            assertEquals(tid0, infos[0].getThreadId());
            assertNull(infos[1]);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test ThreadMXBean.getThreadInfo(long) with the thread id of a carrier thread.
     * The stack frames of the virtual thread should not be returned.
     */
    @Test
    void testGetThreadInfoCarrierThread() throws Exception {
        assumeTrue(supportsCustomScheduler(), "No support for custom schedulers");
        try (ExecutorService pool = Executors.newFixedThreadPool(1)) {
            var carrierRef = new AtomicReference<Thread>();
            Executor scheduler = (task) -> {
                pool.execute(() -> {
                    carrierRef.set(Thread.currentThread());
                    task.run();
                });
            };

            // start virtual thread so carrier Thread can be captured
            virtualThreadBuilder(scheduler).start(() -> { }).join();
            Thread carrier = carrierRef.get();
            assertTrue(carrier != null && !carrier.isVirtual());

            try (Selector sel = Selector.open()) {
                // start virtual thread that blocks in a native method
                virtualThreadBuilder(scheduler).start(() -> {
                    try {
                        sel.select();
                    } catch (Exception e) { }
                });

                // invoke getThreadInfo get and check the stack trace
                long tid = carrier.getId();
                ThreadInfo info = ManagementFactory.getThreadMXBean().getThreadInfo(tid, 100);

                // should only see carrier frames
                StackTraceElement[] stack = info.getStackTrace();
                assertTrue(contains(stack, "java.util.concurrent.ThreadPoolExecutor"));
                assertFalse(contains(stack, "java.nio.channels.Selector"));

                // carrier should not be holding any monitors
                assertEquals(0, info.getLockedMonitors().length);
            }
        }
    }

    /**
     * Test that ThreadMXBean.getThreadCpuTime(long) returns -1 for a virtual thread.
     */
    @Test
    void testGetThreadCpuTime1() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid = vthread.threadId();
            long cpuTime = bean.getThreadCpuTime(tid);
            assertEquals(-1L, cpuTime);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadCpuTime(long) returns -1 when invoked by a
     * virtual thread with its own thread id.
     */
    @Test
    void testGetThreadCpuTime2() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        VThreadRunner.run(() -> {
            long tid = Thread.currentThread().threadId();
            long cpuTime = bean.getThreadCpuTime(tid);
            assertEquals(-1L, cpuTime);
        });
    }

    /**
     * Test that ThreadMXBean.getThreadUserTime(long) returns -1 for a virtual thread.
     */
    @Test
    void testGetThreadUserTime1() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        Thread vthread = Thread.startVirtualThread(LockSupport::park);
        try {
            long tid = vthread.threadId();
            long userTime = ManagementFactory.getThreadMXBean().getThreadUserTime(tid);
            assertEquals(-1L, userTime);
        } finally {
            LockSupport.unpark(vthread);
        }
    }

    /**
     * Test that ThreadMXBean.getThreadUserTime(long) returns -1 when invoked by a
     * virtual thread with its own thread id.
     */
    @Test
    void testGetThreadUserTime2() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isThreadCpuTimeSupported(), "Thread CPU time measurement not supported");

        VThreadRunner.run(() -> {
            long tid = Thread.currentThread().threadId();
            long userTime = ManagementFactory.getThreadMXBean().getThreadUserTime(tid);
            assertEquals(-1L, userTime);
        });
    }

    /**
     * Test that ThreadMXBean::isCurrentThreadCpuTimeSupported returns true when
     * CPU time measurement for the current thread is supported.
     */
    @Test
    void testIsCurrentThreadCpuTimeSupported() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isCurrentThreadCpuTimeSupported(),
                "Thread CPU time measurement for the current thread not supported");

        VThreadRunner.run(() -> {
            assertTrue(bean.isCurrentThreadCpuTimeSupported());
        });
    }

    /**
     * Test that ThreadMXBean::getCurrentThreadCpuTime returns -1 when invoked
     * from a virtual thread.
     */
    @Test
    void testGetCurrentThreadCpuTime() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isCurrentThreadCpuTimeSupported(),
                "Thread CPU time measurement for the current thread not supported");

        VThreadRunner.run(() -> {
            assertEquals(-1L, bean.getCurrentThreadCpuTime());
        });
    }

    /**
     * Test that ThreadMXBean::getCurrentThreadUserTime returns -1 when invoked
     * from a virtual thread.
     */
    @Test
    void testGetCurrentThreadUserTime() throws Exception {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean.isCurrentThreadCpuTimeSupported(),
                "Thread CPU time measurement for the current thread not supported");

        VThreadRunner.run(() -> {
            assertEquals(-1L, bean.getCurrentThreadUserTime());
        });
    }

    private static boolean contains(StackTraceElement[] stack, String className) {
        return Arrays.stream(stack)
                .map(StackTraceElement::getClassName)
                .anyMatch(className::equals);
    }

    /**
     * Returns a builder to create virtual threads that use the given scheduler.
     * @throws UnsupportedOperationException if there is no support for custom schedulers
     */
    private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
            ctor.setAccessible(true);
            return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return true if custom schedulers are supported.
     */
    private static boolean supportsCustomScheduler() {
        try (var pool = Executors.newCachedThreadPool()) {
            try {
                virtualThreadBuilder(pool);
                return true;
            } catch (UnsupportedOperationException e) {
                return false;
            }
        }
    }
}
