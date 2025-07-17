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
 * @test
 * @summary Unit tests for java.lang.ThreadGroup
 * @run junit BasicTests
 */

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BasicTests {

    @Test
    void testGetName1() {
        ThreadGroup group = new ThreadGroup(null);
        assertTrue(group.getName() == null);
    }

    @Test
    void testGetName2() {
        ThreadGroup group = new ThreadGroup("fred");
        assertEquals("fred", group.getName());
    }

    @Test
    void testGetParent() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        ThreadGroup group3 = new ThreadGroup(group2, "group3");

        assertTrue(group1.getParent() == Thread.currentThread().getThreadGroup());
        assertTrue(group2.getParent() == group1);
        assertTrue(group3.getParent() == group2);
    }

    @Test
    void testParentOf() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        ThreadGroup group3 = new ThreadGroup(group2, "group3");

        assertFalse(group1.parentOf(null));
        assertTrue(group1.parentOf(group1));
        assertTrue(group1.parentOf(group2));
        assertTrue(group1.parentOf(group3));

        assertFalse(group2.parentOf(null));
        assertFalse(group2.parentOf(group1));
        assertTrue(group2.parentOf(group2));
        assertTrue(group2.parentOf(group3));

        assertFalse(group3.parentOf(null));
        assertFalse(group3.parentOf(group1));
        assertFalse(group3.parentOf(group2));
        assertTrue(group3.parentOf(group3));
    }

    @Test
    void testActiveCount1() {
        ThreadGroup group = new ThreadGroup("group");
        assertTrue(group.activeCount() == 0);
        TestThread thread = TestThread.start(group, "foo");
        try {
            assertTrue(group.activeCount() == 1);
        } finally {
            thread.terminate();
        }
        assertTrue(group.activeCount() == 0);
    }

    @Test
    void testActiveCount2() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        assertTrue(group1.activeCount() == 0);
        assertTrue(group2.activeCount() == 0);
        TestThread thread1 = TestThread.start(group1, "foo");
        try {
            assertTrue(group1.activeCount() == 1);
            assertTrue(group2.activeCount() == 0);
            TestThread thread2 = TestThread.start(group2, "bar");
            try {
                assertTrue(group1.activeCount() == 2);
                assertTrue(group2.activeCount() == 1);
            } finally {
                thread2.terminate();
            }
            assertTrue(group1.activeCount() == 1);
            assertTrue(group2.activeCount() == 0);
        } finally {
            thread1.terminate();
        }
        assertTrue(group1.activeCount() == 0);
        assertTrue(group2.activeCount() == 0);
    }

    @Test
    void enumerateThreads1() {
        ThreadGroup group = new ThreadGroup("group");
        Thread[] threads = new Thread[100];
        assertTrue(group.enumerate(threads) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group.enumerate(threads, true) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group.enumerate(threads, false) == 0);
        assertTrue(threads[0] == null);
        TestThread thread = TestThread.start(group, "foo");
        try {
            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads) == 1);
            assertTrue(threads[0] == thread);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread);
            assertTrue(threads[1] == null);
        } finally {
            thread.terminate();
        }
        assertTrue(group.activeCount() == 0);
    }

    @Test
    void enumerateThreads2() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");

        Thread[] threads = new Thread[100];
        assertTrue(group1.enumerate(threads) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group1.enumerate(threads, true) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group1.enumerate(threads, false) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group2.enumerate(threads) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group2.enumerate(threads, true) == 0);
        assertTrue(threads[0] == null);
        assertTrue(group2.enumerate(threads, false) == 0);
        assertTrue(threads[0] == null);

        TestThread thread1 = TestThread.start(group1, "foo");
        try {
            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads) == 1);
            assertTrue(threads[0] == thread1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group2.enumerate(threads, true) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group2.enumerate(threads, false) == 0);
            assertTrue(threads[0] == null);

            TestThread thread2 = TestThread.start(group2, "bar");
            try {
                Arrays.setAll(threads, i -> null);
                assertTrue(group1.enumerate(threads) == 2);
                assertEquals(Set.of(thread1, thread2), toSet(threads, 2));
                assertTrue(threads[2] == null);

                Arrays.setAll(threads, i -> null);
                assertTrue(group1.enumerate(threads, true) == 2);
                assertEquals(Set.of(thread1, thread2), toSet(threads, 2));
                assertTrue(threads[2] == null);

                Arrays.setAll(threads, i -> null);
                assertTrue(group1.enumerate(threads, false) == 1);
                assertTrue(threads[0] == thread1);
                assertTrue(threads[1] == null);

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads) == 1);
                assertTrue(threads[0] == thread2);
                assertTrue(threads[1] == null);

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads, true) == 1);
                assertTrue(threads[0] == thread2);
                assertTrue(threads[1] == null);

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads, false) == 1);
                assertTrue(threads[0] == thread2);
                assertTrue(threads[1] == null);
            } finally {
                thread2.terminate();
            }

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads) == 1);
            assertTrue(threads[0] == thread1);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread1);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread1);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group2.enumerate(threads, true) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group2.enumerate(threads, false) == 0);
            assertTrue(threads[0] == null);
        } finally {
            thread1.terminate();
        }
        assertTrue(group1.activeCount() == 0);
        assertTrue(group2.activeCount() == 0);
    }

    @Test
    void enumerateThreads3() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        ThreadGroup group3 = new ThreadGroup(group2, "group3");

        Thread[] threads = new Thread[100];
        assertTrue(group1.enumerate(threads) == 0);
        assertTrue(group1.enumerate(threads, true) == 0);
        assertTrue(group1.enumerate(threads, false) == 0);
        assertTrue(group2.enumerate(threads) == 0);
        assertTrue(group2.enumerate(threads, true) == 0);
        assertTrue(group2.enumerate(threads, false) == 0);
        assertTrue(group3.enumerate(threads) == 0);
        assertTrue(group3.enumerate(threads, true) == 0);
        assertTrue(group3.enumerate(threads, false) == 0);

        TestThread thread2 = TestThread.start(group2, "foo");
        try {
            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads) == 1);
            assertTrue(group1.enumerate(threads, true) == 1);
            assertTrue(group1.enumerate(threads, false) == 0);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads) == 1);
            assertTrue(threads[0] == thread2);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread2);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread2);

            assertTrue(group3.enumerate(threads) == 0);
            assertTrue(group3.enumerate(threads, true) == 0);
            assertTrue(group3.enumerate(threads, false) == 0);

            TestThread thread3 = TestThread.start(group3, "bar");
            try {
                Arrays.setAll(threads, i -> null);
                assertTrue(group1.enumerate(threads) == 2);
                assertEquals(Set.of(thread2, thread3), toSet(threads, 2));

                Arrays.setAll(threads, i -> null);
                assertTrue(group1.enumerate(threads, true) == 2);
                assertEquals(Set.of(thread2, thread3), toSet(threads, 2));
                assertTrue(group1.enumerate(threads, false) == 0);

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads) == 2);
                assertEquals(Set.of(thread2, thread3), toSet(threads, 2));

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads, true) == 2);
                assertEquals(Set.of(thread2, thread3), toSet(threads, 2));

                Arrays.setAll(threads, i -> null);
                assertTrue(group2.enumerate(threads, false) == 1);
                assertTrue(threads[0] == thread2);

                Arrays.setAll(threads, i -> null);
                assertTrue(group3.enumerate(threads) == 1);
                assertTrue(threads[0] == thread3);

                Arrays.setAll(threads, i -> null);
                assertTrue(group3.enumerate(threads, true) == 1);
                assertTrue(threads[0] == thread3);

                Arrays.setAll(threads, i -> null);
                assertTrue(group3.enumerate(threads, false) == 1);
                assertTrue(threads[0] == thread3);
            } finally {
                thread3.terminate();
            }

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads) == 1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, true) == 1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group1.enumerate(threads, false) == 0);
            assertTrue(threads[0] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads) == 1);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread2);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group2.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread2);
            assertTrue(threads[1] == null);

            Arrays.setAll(threads, i -> null);
            assertTrue(group3.enumerate(threads) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group3.enumerate(threads, true) == 0);
            assertTrue(threads[0] == null);
            assertTrue(group3.enumerate(threads, false) == 0);
            assertTrue(threads[0] == null);

        } finally {
            thread2.terminate();
        }

        assertTrue(group1.enumerate(threads) == 0);
        assertTrue(group1.enumerate(threads, true) == 0);
        assertTrue(group1.enumerate(threads, false) == 0);
        assertTrue(group2.enumerate(threads) == 0);
        assertTrue(group2.enumerate(threads, true) == 0);
        assertTrue(group2.enumerate(threads, false) == 0);
        assertTrue(group3.enumerate(threads) == 0);
        assertTrue(group3.enumerate(threads, true) == 0);
        assertTrue(group3.enumerate(threads, false) == 0);
    }

    /**
     * Test enumerate(Thread[]) with an array of insufficient size
     */
    @Test
    void enumerateThreads4() {
        ThreadGroup group = new ThreadGroup("group");

        // array too small
        Thread[] threads = new Thread[1];

        TestThread thread1 = TestThread.start(group, "thread1");
        TestThread thread2 = TestThread.start(group, "thread2");
        try {
            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads) == 1);
            assertTrue(threads[0] == thread1 || threads[1] == thread2);

            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads, true) == 1);
            assertTrue(threads[0] == thread1 || threads[1] == thread2);

            Arrays.setAll(threads, i -> null);
            assertTrue(group.enumerate(threads, false) == 1);
            assertTrue(threads[0] == thread1 || threads[1] == thread2);
        } finally {
            thread1.terminate();
            thread2.terminate();
        }
    }

    @Test
    void testActiveGroupCount() throws Exception {
        ThreadGroup group1 = new ThreadGroup("group1");
        assertTrue(group1.activeGroupCount() == 0);

        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        assertTrue(group1.activeGroupCount() == 1);
        assertTrue(group2.activeGroupCount() == 0);

        ThreadGroup group3 = new ThreadGroup(group2, "group3");
        assertTrue(group1.activeGroupCount() == 2);
        assertTrue(group2.activeGroupCount() == 1);
        assertTrue(group3.activeGroupCount() == 0);

        // unreference group3 and wait for it to be GC'ed
        var ref = new WeakReference<>(group3);
        group3 = null;
        System.gc();
        while (ref.get() != null) {
            Thread.sleep(50);
        }

        assertTrue(group1.activeGroupCount() == 1);
        assertTrue(group2.activeGroupCount() == 0);
    }

    @Test
    void testEnumerateGroups1() throws Exception {
        ThreadGroup[] groups = new ThreadGroup[100];

        ThreadGroup group1 = new ThreadGroup("group1");

        assertTrue(group1.enumerate(groups) == 0);
        assertTrue(groups[0] == null);
        assertTrue(group1.enumerate(groups, true) == 0);
        assertTrue(groups[0] == null);
        assertTrue(group1.enumerate(groups, false) == 0);
        assertTrue(groups[0] == null);

        ThreadGroup group2 = new ThreadGroup(group1, "group2");

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, true) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, false) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        ThreadGroup group3 = new ThreadGroup(group2, "group3");

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups) == 2);
        assertEquals(Set.of(group2, group3), toSet(groups, 2));
        assertTrue(groups[2] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, true) == 2);
        assertEquals(Set.of(group2, group3), toSet(groups, 2));
        assertTrue(groups[2] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, false) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group2.enumerate(groups) == 1);
        assertTrue(groups[0] == group3);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group2.enumerate(groups, true) == 1);
        assertTrue(groups[0] == group3);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group2.enumerate(groups, false) == 1);
        assertTrue(groups[0] == group3);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group3.enumerate(groups) == 0);
        assertTrue(groups[0] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group3.enumerate(groups, true) == 0);
        assertTrue(groups[0] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group3.enumerate(groups, false) == 0);
        assertTrue(groups[0] == null);

        // unreference group3 and wait for it to be GC'ed
        var ref = new WeakReference<>(group3);
        Arrays.setAll(groups, i -> null);
        group3 = null;
        System.gc();
        while (ref.get() != null) {
            Thread.sleep(50);
        }

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, true) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);

        Arrays.setAll(groups, i -> null);
        assertTrue(group1.enumerate(groups, false) == 1);
        assertTrue(groups[0] == group2);
        assertTrue(groups[1] == null);
    }

    /**
     * Test enumerate(ThreadGroup[]) with an array of insufficient size
     */
    @Test
    void testEnumerateGroups2() throws Exception {
        ThreadGroup group = new ThreadGroup("group");
        ThreadGroup child1 = new ThreadGroup(group, "child1");
        ThreadGroup child2 = new ThreadGroup(group,"child2");

        // array too small
        ThreadGroup[] groups = new ThreadGroup[1];

        Arrays.setAll(groups, i -> null);
        assertTrue(group.enumerate(groups) == 1);
        assertTrue(groups[0] == child1 || groups[1] == child2);

        Arrays.setAll(groups, i -> null);
        assertTrue(group.enumerate(groups, true) == 1);
        assertTrue(groups[0] == child1 || groups[1] == child2);

        Arrays.setAll(groups, i -> null);
        assertTrue(group.enumerate(groups, false) == 1);
        assertTrue(groups[0] == child1 || groups[1] == child2);
    }

    @Test
    void testMaxPriority1() {
        ThreadGroup group = new ThreadGroup("group");
        final int maxPriority = group.getMaxPriority();
        assertTrue(maxPriority == Thread.currentThread().getThreadGroup().getMaxPriority());

        group.setMaxPriority(Thread.MIN_PRIORITY-1);
        assertTrue(group.getMaxPriority() == maxPriority);

        group.setMaxPriority(Thread.MAX_PRIORITY+1);
        assertTrue(group.getMaxPriority() == maxPriority);

        group.setMaxPriority(maxPriority+1);
        assertTrue(group.getMaxPriority() == maxPriority);

        if (maxPriority > Thread.MIN_PRIORITY) {
            group.setMaxPriority(maxPriority-1);
            assertTrue(group.getMaxPriority() == (maxPriority-1));
        }

        group.setMaxPriority(maxPriority);
        assertTrue(group.getMaxPriority() == maxPriority);
    }

    @Test
    void testMaxPriority2() {
        ThreadGroup group1 = new ThreadGroup("group1");
        int maxPriority = group1.getMaxPriority();
        if (maxPriority > Thread.MIN_PRIORITY) {
            ThreadGroup group2 = new ThreadGroup(group1, "group2");
            assertTrue(group2.getMaxPriority() == maxPriority);

            ThreadGroup group3 = new ThreadGroup(group2, "group3");
            assertTrue(group3.getMaxPriority() == maxPriority);

            group1.setMaxPriority(Thread.MIN_PRIORITY);
            assertTrue(group1.getMaxPriority() == Thread.MIN_PRIORITY);
            assertTrue(group2.getMaxPriority() == Thread.MIN_PRIORITY);
            assertTrue(group3.getMaxPriority() == Thread.MIN_PRIORITY);
            group1.setMaxPriority(maxPriority);

            assertTrue(group1.getMaxPriority() == maxPriority);
            assertTrue(group2.getMaxPriority() == maxPriority);
            assertTrue(group3.getMaxPriority() == maxPriority);

            group2.setMaxPriority(Thread.MIN_PRIORITY);
            assertTrue(group1.getMaxPriority() == maxPriority);
            assertTrue(group2.getMaxPriority() == Thread.MIN_PRIORITY);
            assertTrue(group3.getMaxPriority() == Thread.MIN_PRIORITY);
            group2.setMaxPriority(maxPriority);

            assertTrue(group1.getMaxPriority() == maxPriority);
            assertTrue(group2.getMaxPriority() == maxPriority);
            assertTrue(group3.getMaxPriority() == maxPriority);

            group3.setMaxPriority(Thread.MIN_PRIORITY);
            assertTrue(group1.getMaxPriority() == maxPriority);
            assertTrue(group2.getMaxPriority() == maxPriority);
            assertTrue(group3.getMaxPriority() == Thread.MIN_PRIORITY);
        }
    }

    @Test
    void testMaxPriority3() {
        ThreadGroup group = new ThreadGroup("group");
        if (group.getMaxPriority() > Thread.MIN_PRIORITY) {
            int maxPriority = Thread.MIN_PRIORITY + 1;
            group.setMaxPriority(maxPriority);
            assertTrue(group.getMaxPriority() == maxPriority);

            Thread thread = new Thread(group, () -> { });
            int priority = thread.getPriority();

            int expectedPriority = Math.min(Thread.currentThread().getPriority(), maxPriority);
            assertTrue(priority == expectedPriority);

            thread.setPriority(Thread.MAX_PRIORITY);
            assertTrue(thread.getPriority() == maxPriority);

            thread.setPriority(maxPriority);
            assertTrue(thread.getPriority() == maxPriority);

            thread.setPriority(Thread.MIN_PRIORITY);
            assertTrue(thread.getPriority() == Thread.MIN_PRIORITY);

        }
    }

    @Test
    void testInterrupt1() {
        ThreadGroup group = new ThreadGroup("group");
        assertTrue(group.activeCount() == 0);
        TestThread thread = TestThread.start(group, "foo");
        try {
            group.interrupt();
        } finally {
            thread.terminate();
        }
        assertTrue(thread.wasInterrupted());
    }

    @Test
    void testInterrupt2() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        TestThread thread1 = TestThread.start(group1, "foo");
        TestThread thread2 = TestThread.start(group2, "bar");
        try {
            group1.interrupt();
        } finally {
            thread1.terminate();
            thread2.terminate();
        }
        assertTrue(thread2.wasInterrupted());
        assertTrue(thread1.wasInterrupted());
    }

    @Test
    void testInterrupt3() {
        ThreadGroup group1 = new ThreadGroup("group1");
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        TestThread thread1 = TestThread.start(group1, "foo");
        TestThread thread2 = TestThread.start(group2, "bar");
        try {
            group2.interrupt();
        } finally {
            thread1.terminate();
            thread2.terminate();
        }
        assertTrue(thread2.wasInterrupted());
        assertFalse(thread1.wasInterrupted());
    }

    @Test
    void testDestroy() {
        ThreadGroup group = new ThreadGroup("group");
        assertFalse(group.isDestroyed());
        group.destroy();  // does nothing
        assertFalse(group.isDestroyed());
    }

    @Test
    void testDaemon() {
        boolean d = Thread.currentThread().getThreadGroup().isDaemon();

        ThreadGroup group1 = new ThreadGroup("group1");
        assertTrue(group1.isDaemon() == d);  // inherit

        group1.setDaemon(true);
        assertTrue(group1.isDaemon());
        ThreadGroup group2 = new ThreadGroup(group1, "group2");
        assertTrue(group2.isDaemon());   // inherit

        group1.setDaemon(false);
        assertFalse(group1.isDaemon());
        ThreadGroup group3 = new ThreadGroup(group1, "group2");
        assertFalse(group3.isDaemon());  // inherit
    }

    @Test
    void testList() {
        ThreadGroup group = new ThreadGroup("foo");
        group.list();
    }

    @Test
    void testNull1() {
        assertThrows(NullPointerException.class,
                     () -> new ThreadGroup(null, "group"));
    }

    @Test
    void testNull2() {
        ThreadGroup group = new ThreadGroup("group");
        assertThrows(NullPointerException.class,
                     () -> group.enumerate((Thread[]) null));
    }

    @Test
    void testNull3() {
        ThreadGroup group = new ThreadGroup("group");
        assertThrows(NullPointerException.class,
                     () -> group.enumerate((Thread[]) null, false));
    }

    @Test
    void testNull4() {
        ThreadGroup group = new ThreadGroup("group");
        assertThrows(NullPointerException.class,
                     () -> group.enumerate((ThreadGroup[]) null));
    }

    @Test
    void testNull5() {
        ThreadGroup group = new ThreadGroup("group");
        assertThrows(NullPointerException.class,
                     () -> group.enumerate((ThreadGroup[]) null, false));
    }

    private static <T> Set<T> toSet(T[] array, int len) {
        return Arrays.stream(array, 0, len).collect(Collectors.toSet());
    }

    private static class TestThread extends Thread {
        TestThread(ThreadGroup group, String name) {
            super(group, name);
        }

        static TestThread start(ThreadGroup group, String name) {
            TestThread thread = new TestThread(group, name);
            thread.start();
            return thread;
        }

        private volatile boolean done;
        private volatile boolean interrupted;

        @Override
        public void run() {
            if (Thread.currentThread() != this)
                throw new IllegalCallerException();
            while (!done) {
                LockSupport.park();
                if (Thread.interrupted()) {
                    interrupted = true;
                }
            }
            if (Thread.interrupted()) {
                interrupted = true;
            }
        }

        boolean wasInterrupted() {
            return interrupted;
        }

        void terminate() {
            done = true;
            LockSupport.unpark(this);
            boolean interrupted = false;
            while (isAlive()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
