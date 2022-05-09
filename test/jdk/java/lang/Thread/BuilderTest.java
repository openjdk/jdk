/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit test for Thread.Builder
 * @compile --enable-preview -source ${jdk.version} BuilderTest.java
 * @run testng/othervm --enable-preview BuilderTest
 */

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.testng.SkipException;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class BuilderTest {

    /**
     * Test Thread.ofPlatform to create platform threads.
     */
    @Test
    public void testPlatformThread() throws Exception {
        Thread parent = Thread.currentThread();
        Thread.Builder.OfPlatform builder = Thread.ofPlatform();

        // unstarted
        AtomicBoolean done1 = new AtomicBoolean();
        Thread thread1 = builder.unstarted(() -> done1.set(true));
        assertFalse(thread1.isVirtual());
        assertTrue(thread1.getState() == Thread.State.NEW);
        assertFalse(thread1.getName().isEmpty());
        assertTrue(thread1.getThreadGroup() == parent.getThreadGroup());
        assertTrue(thread1.isDaemon() == parent.isDaemon());
        assertTrue(thread1.getPriority() == parent.getPriority());
        assertTrue(thread1.getContextClassLoader() == parent.getContextClassLoader());
        thread1.start();
        thread1.join();
        assertTrue(done1.get());

        // start
        AtomicBoolean done2 = new AtomicBoolean();
        Thread thread2 = builder.start(() -> done2.set(true));
        assertFalse(thread2.isVirtual());
        assertTrue(thread2.getState() != Thread.State.NEW);
        assertFalse(thread2.getName().isEmpty());
        ThreadGroup group2 = thread2.getThreadGroup();
        assertTrue(group2 == parent.getThreadGroup() || group2 == null);
        assertTrue(thread2.isDaemon() == parent.isDaemon());
        assertTrue(thread2.getPriority() == parent.getPriority());
        assertTrue(thread2.getContextClassLoader() == parent.getContextClassLoader());
        thread2.join();
        assertTrue(done2.get());

        // factory
        AtomicBoolean done3 = new AtomicBoolean();
        Thread thread3 = builder.factory().newThread(() -> done3.set(true));
        assertFalse(thread3.isVirtual());
        assertTrue(thread3.getState() == Thread.State.NEW);
        assertFalse(thread3.getName().isEmpty());
        assertTrue(thread3.getThreadGroup() == parent.getThreadGroup());
        assertTrue(thread3.isDaemon() == parent.isDaemon());
        assertTrue(thread3.getPriority() == parent.getPriority());
        assertTrue(thread3.getContextClassLoader() == parent.getContextClassLoader());
        thread3.start();
        thread3.join();
        assertTrue(done3.get());
    }

    /**
     * Test Thread.ofVirtual to create virtual threads.
     */
    @Test
    public void testVirtualThread() throws Exception {
        Thread parent = Thread.currentThread();
        Thread.Builder.OfVirtual builder = Thread.ofVirtual();

        // unstarted
        AtomicBoolean done1 = new AtomicBoolean();
        Thread thread1 = builder.unstarted(() -> done1.set(true));
        assertTrue(thread1.isVirtual());
        assertTrue(thread1.getState() == Thread.State.NEW);
        assertTrue(thread1.getName().isEmpty());
        assertTrue(thread1.getContextClassLoader() == parent.getContextClassLoader());
        assertTrue(thread1.isDaemon());
        assertTrue(thread1.getPriority() == Thread.NORM_PRIORITY);
        thread1.start();
        thread1.join();
        assertTrue(done1.get());

        // start
        AtomicBoolean done2 = new AtomicBoolean();
        Thread thread2 = builder.start(() -> done2.set(true));
        assertTrue(thread2.isVirtual());
        assertTrue(thread2.getState() != Thread.State.NEW);
        assertTrue(thread2.getName().isEmpty());
        assertTrue(thread2.getContextClassLoader() == parent.getContextClassLoader());
        assertTrue(thread2.isDaemon());
        assertTrue(thread2.getPriority() == Thread.NORM_PRIORITY);
        thread2.join();
        assertTrue(done2.get());

        // factory
        AtomicBoolean done3 = new AtomicBoolean();
        Thread thread3 = builder.factory().newThread(() -> done3.set(true));
        assertTrue(thread3.isVirtual());
        assertTrue(thread3.getState() == Thread.State.NEW);
        assertTrue(thread3.getName().isEmpty());
        assertTrue(thread3.getContextClassLoader() == parent.getContextClassLoader());
        assertTrue(thread3.isDaemon());
        assertTrue(thread3.getPriority() == Thread.NORM_PRIORITY);
        thread3.start();
        thread3.join();
        assertTrue(done3.get());
    }

    /**
     * Test Thread.Builder.name.
     */
    @Test
    public void testName1() {
        Thread.Builder builder = Thread.ofPlatform().name("duke");

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.getName().equals("duke"));
        assertTrue(thread2.getName().equals("duke"));
        assertTrue(thread3.getName().equals("duke"));
    }

    @Test
    public void testName2() {
        Thread.Builder builder = Thread.ofVirtual().name("duke");

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.getName().equals("duke"));
        assertTrue(thread2.getName().equals("duke"));
        assertTrue(thread3.getName().equals("duke"));
    }

    @Test
    public void testName3() {
        Thread.Builder builder = Thread.ofPlatform().name("duke-", 100);

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.unstarted(() -> { });
        Thread thread3 = builder.unstarted(() -> { });

        assertTrue(thread1.getName().equals("duke-100"));
        assertTrue(thread2.getName().equals("duke-101"));
        assertTrue(thread3.getName().equals("duke-102"));

        ThreadFactory factory = builder.factory();
        Thread thread4 = factory.newThread(() -> { });
        Thread thread5 = factory.newThread(() -> { });
        Thread thread6 = factory.newThread(() -> { });

        assertTrue(thread4.getName().equals("duke-103"));
        assertTrue(thread5.getName().equals("duke-104"));
        assertTrue(thread6.getName().equals("duke-105"));
    }

    @Test
    public void testName4() {
        Thread.Builder builder = Thread.ofVirtual().name("duke-", 100);

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.unstarted(() -> { });
        Thread thread3 = builder.unstarted(() -> { });

        assertTrue(thread1.getName().equals("duke-100"));
        assertTrue(thread2.getName().equals("duke-101"));
        assertTrue(thread3.getName().equals("duke-102"));

        ThreadFactory factory = builder.factory();
        Thread thread4 = factory.newThread(() -> { });
        Thread thread5 = factory.newThread(() -> { });
        Thread thread6 = factory.newThread(() -> { });

        assertTrue(thread4.getName().equals("duke-103"));
        assertTrue(thread5.getName().equals("duke-104"));
        assertTrue(thread6.getName().equals("duke-105"));
    }

    /**
     * Test Thread.Builder.OfPlatform.group.
     */
    @Test
    public void testThreadGroup1() {
        ThreadGroup group = new ThreadGroup("groupies");
        Thread.Builder builder = Thread.ofPlatform().group(group);

        Thread thread1 = builder.unstarted(() -> { });

        AtomicBoolean done = new AtomicBoolean();
        Thread thread2 = builder.start(() -> {
            while (!done.get()) {
                LockSupport.park();
            }
        });

        Thread thread3 = builder.factory().newThread(() -> { });

        try {
            assertTrue(thread1.getThreadGroup() == group);
            assertTrue(thread2.getThreadGroup() == group);
            assertTrue(thread3.getThreadGroup() == group);
        } finally {
            done.set(true);
            LockSupport.unpark(thread2);
        }
    }

    @Test
    public void testThreadGroup2() {
        ThreadGroup vgroup = Thread.ofVirtual().unstarted(() -> { }).getThreadGroup();
        assertEquals(vgroup.getName(), "VirtualThreads");

        Thread thread1 = Thread.ofVirtual().unstarted(() -> { });
        Thread thread2 = Thread.ofVirtual().start(LockSupport::park);
        Thread thread3 = Thread.ofVirtual().factory().newThread(() -> { });

        try {
            assertTrue(thread1.getThreadGroup() == vgroup);
            assertTrue(thread2.getThreadGroup() == vgroup);
            assertTrue(thread3.getThreadGroup() == vgroup);
        } finally {
            LockSupport.unpark(thread2);
        }
    }

    /**
     * Test Thread.Builder.OfPlatform.priority.
     */
    @Test
    public void testPriority1() {
        int priority = Thread.currentThread().getPriority();

        Thread.Builder builder = Thread.ofPlatform();
        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.getPriority() == priority);
        assertTrue(thread2.getPriority() == priority);
        assertTrue(thread3.getPriority() == priority);
    }

    @Test
    public void testPriority2() {
        int priority = Thread.MIN_PRIORITY;

        Thread.Builder builder = Thread.ofPlatform().priority(priority);
        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.getPriority() == priority);
        assertTrue(thread2.getPriority() == priority);
        assertTrue(thread3.getPriority() == priority);
    }

    @Test
    public void testPriority3() {
        Thread currentThread = Thread.currentThread();
        if (currentThread.isVirtual())
            throw new SkipException("Main test is a virtual thread");

        int maxPriority = currentThread.getThreadGroup().getMaxPriority();
        int priority = Math.min(maxPriority + 1, Thread.MAX_PRIORITY);

        Thread.Builder builder = Thread.ofPlatform().priority(priority);
        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.getPriority() == priority);
        assertTrue(thread2.getPriority() == priority);
        assertTrue(thread3.getPriority() == priority);
    }

    @Test
    public void testPriority4() {
        var builder = Thread.ofPlatform();
        assertThrows(IllegalArgumentException.class,
                     () -> builder.priority(Thread.MIN_PRIORITY - 1));
    }

    @Test
    public void testPriority5() {
        var builder = Thread.ofPlatform();
        assertThrows(IllegalArgumentException.class,
                     () -> builder.priority(Thread.MAX_PRIORITY + 1));
    }

    /**
     * Test Thread.Builder.OfPlatform.daemon.
     */
    @Test
    public void testDaemon1() {
        Thread.Builder builder = Thread.ofPlatform().daemon(false);

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertFalse(thread1.isDaemon());
        assertFalse(thread2.isDaemon());
        assertFalse(thread3.isDaemon());
    }

    @Test
    public void testDaemon2() {
        Thread.Builder builder = Thread.ofPlatform().daemon(true);

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.isDaemon());
        assertTrue(thread2.isDaemon());
        assertTrue(thread3.isDaemon());
    }

    @Test
    public void testDaemon3() {
        Thread.Builder builder = Thread.ofPlatform().daemon();

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        assertTrue(thread1.isDaemon());
        assertTrue(thread2.isDaemon());
        assertTrue(thread3.isDaemon());
    }

    @Test
    public void testDaemon4() {
        Thread.Builder builder = Thread.ofPlatform();

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        // daemon status should be inherited
        boolean d = Thread.currentThread().isDaemon();
        assertTrue(thread1.isDaemon() == d);
        assertTrue(thread2.isDaemon() == d);
        assertTrue(thread3.isDaemon() == d);
    }

    /**
     * Test Thread.ofVirtual creates daemon threads.
     */
    @Test
    public void testDaemon5() {
        Thread.Builder builder = Thread.ofVirtual();

        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });

        // daemon status should always be true
        assertTrue(thread1.isDaemon());
        assertTrue(thread2.isDaemon());
        assertTrue(thread3.isDaemon());
    }

    /**
     * Test Thread.Builder.OfPlatform.stackSize.
     */
    @Test
    public void testStackSize1() {
        Thread.Builder builder = Thread.ofPlatform().stackSize(1024*1024);
        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });
    }

    @Test
    public void testStackSize2() {
        Thread.Builder builder = Thread.ofPlatform().stackSize(0);
        Thread thread1 = builder.unstarted(() -> { });
        Thread thread2 = builder.start(() -> { });
        Thread thread3 = builder.factory().newThread(() -> { });
    }

    @Test
    public void testStackSize3() {
        var builder = Thread.ofPlatform();
        assertThrows(IllegalArgumentException.class, () -> builder.stackSize(-1));
    }

    /**
     * Test Thread.Builder.uncaughtExceptionHandler.
     */
    @Test
    public void testUncaughtExceptionHandler1() throws Exception {
        class FooException extends RuntimeException { }
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        Thread thread = Thread.ofPlatform()
                .uncaughtExceptionHandler((t, e) -> {
                    assertTrue(t == Thread.currentThread());
                    threadRef.set(t);
                    exceptionRef.set(e);
                })
                .start(() -> { throw new FooException(); });
        thread.join();
        assertTrue(threadRef.get() == thread);
        assertTrue(exceptionRef.get() instanceof FooException);
    }

    @Test
    public void testUncaughtExceptionHandler2() throws Exception {
        class FooException extends RuntimeException { }
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        Thread thread = Thread.ofVirtual()
                .uncaughtExceptionHandler((t, e) -> {
                    assertTrue(t == Thread.currentThread());
                    threadRef.set(t);
                    exceptionRef.set(e);
                })
                .start(() -> { throw new FooException(); });
        thread.join();
        assertTrue(threadRef.get() == thread);
        assertTrue(exceptionRef.get() instanceof FooException);
    }

    @Test
    public void testUncaughtExceptionHandler3() throws Exception {
        class FooException extends RuntimeException { }
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        Thread thread = Thread.ofPlatform()
                .uncaughtExceptionHandler((t, e) -> {
                    assertTrue(t == Thread.currentThread());
                    threadRef.set(t);
                    exceptionRef.set(e);
                })
                .factory()
                .newThread(() -> { throw new FooException(); });
        thread.start();
        thread.join();
        assertTrue(threadRef.get() == thread);
        assertTrue(exceptionRef.get() instanceof FooException);
    }

    @Test
    public void testUncaughtExceptionHandler4() throws Exception {
        class FooException extends RuntimeException { }
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        AtomicReference<Throwable> exceptionRef = new AtomicReference<>();
        Thread thread = Thread.ofPlatform()
                .uncaughtExceptionHandler((t, e) -> {
                    assertTrue(t == Thread.currentThread());
                    threadRef.set(t);
                    exceptionRef.set(e);
                })
                .factory()
                .newThread(() -> { throw new FooException(); });
        thread.start();
        thread.join();
        assertTrue(threadRef.get() == thread);
        assertTrue(exceptionRef.get() instanceof FooException);
    }

    static final ThreadLocal<Object> LOCAL = new ThreadLocal<>();
    static final ThreadLocal<Object> INHERITED_LOCAL = new InheritableThreadLocal<>();

    /**
     * Tests that a builder creates threads that support thread locals
     */
    private void testThreadLocals(Thread.Builder builder) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Runnable task = () -> {
            Object value = new Object();
            LOCAL.set(value);
            assertTrue(LOCAL.get() == value);
            done.set(true);
        };

        done.set(false);
        Thread thread1 = builder.unstarted(task);
        thread1.start();
        thread1.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread2 = builder.start(task);
        thread2.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread3 = builder.factory().newThread(task);
        thread3.start();
        thread3.join();
        assertTrue(done.get());
    }

    /**
     * Tests that a builder creates threads that do not support thread locals
     */
    private void testNoThreadLocals(Thread.Builder builder) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Runnable task = () -> {
            try {
                LOCAL.set(new Object());
            } catch (UnsupportedOperationException expected) {
                done.set(true);
            }
        };

        done.set(false);
        Thread thread1 = builder.unstarted(task);
        thread1.start();
        thread1.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread2 = builder.start(task);
        thread2.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread3 = builder.factory().newThread(task);
        thread3.start();
        thread3.join();
        assertTrue(done.get());
    }

    /**
     * Test Thread.Builder creates threads that allow thread locals.
     */
    @Test
    public void testThreadLocals1() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();
        testThreadLocals(builder);
    }

    @Test
    public void testThreadLocals2() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();
        testThreadLocals(builder);
    }

    /**
     * Test Thread.Builder creating threads that disallow or allow
     * thread locals.
     */
    @Test
    public void testThreadLocals3() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();

        // disallow
        builder.allowSetThreadLocals(false);
        testNoThreadLocals(builder);

        // allow
        builder.allowSetThreadLocals(true);
        testThreadLocals(builder);
    }

    @Test
    public void testThreadLocals4() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();

        // disallow
        builder.allowSetThreadLocals(false);
        testNoThreadLocals(builder);

        // allow
        builder.allowSetThreadLocals(true);
        testThreadLocals(builder);
    }

    /**
     * Tests that a builder creates threads that inherits the initial values of
     * inheritable thread locals.
     */
    private void testInheritedThreadLocals(Thread.Builder builder) throws Exception {
        Object value = new Object();
        INHERITED_LOCAL.set(value);

        AtomicBoolean done = new AtomicBoolean();
        Runnable task = () -> {
            assertTrue(INHERITED_LOCAL.get() == value);
            done.set(true);
        };

        done.set(false);
        Thread thread1 = builder.unstarted(task);
        thread1.start();
        thread1.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread2 = builder.start(task);
        thread2.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread3 = builder.factory().newThread(task);
        thread3.start();
        thread3.join();
        assertTrue(done.get());
    }

    /**
     * Tests that a builder creates threads that do not inherit the initial values
     * of inheritable thread locals.
     */
    private void testNoInheritedThreadLocals(Thread.Builder builder) throws Exception {
        Object value = new Object();
        INHERITED_LOCAL.set(value);

        AtomicBoolean done = new AtomicBoolean();
        Runnable task = () -> {
            assertTrue(INHERITED_LOCAL.get() == null);
            done.set(true);
        };

        done.set(false);
        Thread thread1 = builder.unstarted(task);
        thread1.start();
        thread1.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread2 = builder.start(task);
        thread2.join();
        assertTrue(done.get());

        done.set(false);
        Thread thread3 = builder.factory().newThread(task);
        thread3.start();
        thread3.join();
        assertTrue(done.get());
    }

    /**
     * Test Thread.Builder creating threads that inherit or do not inherit
     * the initial values of inheritable thread locals.
     */
    @Test
    public void testInheritedThreadLocals1() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();
        testInheritedThreadLocals(builder); // default

        // do no inherit
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);

        // inherit
        builder.inheritInheritableThreadLocals(true);
        testInheritedThreadLocals(builder);
    }

    @Test
    public void testInheritedThreadLocals2() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();
        testInheritedThreadLocals(builder); // default

        // do no inherit
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);

        // inherit
        builder.inheritInheritableThreadLocals(true);
        testInheritedThreadLocals(builder);
    }

    @Test
    public void testInheritedThreadLocals3() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();

        // thread locals not allowed
        builder.allowSetThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(true);
        testNoInheritedThreadLocals(builder);

        // thread locals allowed
        builder.allowSetThreadLocals(true);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(true);
        testInheritedThreadLocals(builder);
    }

    @Test
    public void testInheritedThreadLocals4() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();

        // thread locals not allowed
        builder.allowSetThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(true);
        testNoInheritedThreadLocals(builder);

        // thread locals allowed
        builder.allowSetThreadLocals(true);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritedThreadLocals(builder);
        builder.inheritInheritableThreadLocals(true);
        testInheritedThreadLocals(builder);
    }

    /**
     * Tests a builder creates threads that inherit the context class loader.
     */
    private void testInheritContextClassLoader(Thread.Builder builder) throws Exception {
        ClassLoader savedCCL = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader loader = new ClassLoader() { };
            Thread.currentThread().setContextClassLoader(loader);

            var ref = new AtomicReference<ClassLoader>();
            Runnable task = () -> {
                ref.set(Thread.currentThread().getContextClassLoader());
            };

            // unstarted
            Thread thread1 = builder.unstarted(task);
            assertTrue(thread1.getContextClassLoader() == loader);

            // started
            ref.set(null);
            thread1.start();
            thread1.join();
            assertTrue(ref.get() == loader);

            // factory
            Thread thread2 = builder.factory().newThread(task);
            assertTrue(thread2.getContextClassLoader() == loader);

            // started
            ref.set(null);
            thread2.start();
            thread2.join();
            assertTrue(ref.get() == loader);

        } finally {
            Thread.currentThread().setContextClassLoader(savedCCL);
        }
    }

    /**
     * Tests a builder creates threads does not inherit the context class loader.
     */
    private void testNoInheritContextClassLoader(Thread.Builder builder) throws Exception {
        ClassLoader savedCCL = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader( new ClassLoader() { });

            ClassLoader scl = ClassLoader.getSystemClassLoader();

            var ref = new AtomicReference<ClassLoader>();
            Runnable task = () -> {
                ref.set(Thread.currentThread().getContextClassLoader());
            };

            // unstarted
            Thread thread1 = builder.unstarted(task);
            assertTrue(thread1.getContextClassLoader() == scl);

            // started
            ref.set(null);
            thread1.start();
            thread1.join();
            assertTrue(ref.get() == scl);

            // factory
            Thread thread2 = builder.factory().newThread(task);
            assertTrue(thread2.getContextClassLoader() == scl);

            // started
            ref.set(null);
            thread2.start();
            thread2.join();
            assertTrue(ref.get() == scl);

        } finally {
            Thread.currentThread().setContextClassLoader(savedCCL);
        }
    }

    /**
     * Test Thread.Builder creating threads that inherit or do not inherit
     * the thread context class loader.
     */
    @Test
    public void testContextClassLoader1() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();
        testInheritContextClassLoader(builder); // default

        // do no inherit
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);

        // inherit
        builder.inheritInheritableThreadLocals(true);
        testInheritContextClassLoader(builder);
    }

    @Test
    public void testContextClassLoader2() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();
        testInheritContextClassLoader(builder); // default

        // do no inherit
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);

        // inherit
        builder.inheritInheritableThreadLocals(true);
        testInheritContextClassLoader(builder);
    }

    @Test
    public void testContextClassLoader3() throws Exception {
        Thread.Builder builder = Thread.ofPlatform();

        // thread locals not allowed
        builder.allowSetThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(true);
        testNoInheritContextClassLoader(builder);

        // thread locals allowed
        builder.allowSetThreadLocals(true);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(true);
        testInheritContextClassLoader(builder);
    }

    @Test
    public void testContextClassLoader4() throws Exception {
        Thread.Builder builder = Thread.ofVirtual();

        // thread locals not allowed
        builder.allowSetThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(true);
        testNoInheritContextClassLoader(builder);

        // thread locals allowed
        builder.allowSetThreadLocals(true);
        builder.inheritInheritableThreadLocals(false);
        testNoInheritContextClassLoader(builder);
        builder.inheritInheritableThreadLocals(true);
        testInheritContextClassLoader(builder);
    }

    /**
     * Test NullPointerException.
     */
    @Test
    public void testNulls1() {
        Thread.Builder.OfPlatform builder = Thread.ofPlatform();
        assertThrows(NullPointerException.class, () -> builder.group(null));
        assertThrows(NullPointerException.class, () -> builder.name(null));
        assertThrows(NullPointerException.class, () -> builder.name(null, 0));
        assertThrows(NullPointerException.class, () -> builder.uncaughtExceptionHandler(null));
        assertThrows(NullPointerException.class, () -> builder.unstarted(null));
        assertThrows(NullPointerException.class, () -> builder.start(null));
    }

    @Test
    public void testNulls2() {
        Thread.Builder builder = Thread.ofVirtual();
        assertThrows(NullPointerException.class, () -> builder.name(null));
        assertThrows(NullPointerException.class, () -> builder.name(null, 0));
        assertThrows(NullPointerException.class, () -> builder.uncaughtExceptionHandler(null));
        assertThrows(NullPointerException.class, () -> builder.unstarted(null));
        assertThrows(NullPointerException.class, () -> builder.start(null));
    }
}
