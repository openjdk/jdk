/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 * Other contributors include Andrew Wright, Jeffrey Hayes,
 * Pat Fisher, Mike Judd.
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ExecutorCompletionServiceTest extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ExecutorCompletionServiceTest.class);
    }

    /**
     * Creating a new ECS with null Executor throw NPE
     */
    public void testConstructorNPE() {
        try {
            new ExecutorCompletionService(null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Creating a new ECS with null queue throw NPE
     */
    public void testConstructorNPE2() {
        try {
            ExecutorService e = Executors.newCachedThreadPool();
            new ExecutorCompletionService(e, null);
            shouldThrow();
        } catch (NullPointerException success) {}
    }

    /**
     * Submitting a null callable throws NPE
     */
    public void testSubmitNPE() {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            Callable c = null;
            try {
                ecs.submit(c);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * Submitting a null runnable throws NPE
     */
    public void testSubmitNPE2() {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            Runnable r = null;
            try {
                ecs.submit(r, Boolean.TRUE);
                shouldThrow();
            } catch (NullPointerException success) {}
        }
    }

    /**
     * A taken submitted task is completed
     */
    public void testTake() throws InterruptedException {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            Callable c = new StringTask();
            ecs.submit(c);
            Future f = ecs.take();
            assertTrue(f.isDone());
        }
    }

    /**
     * Take returns the same future object returned by submit
     */
    public void testTake2() throws InterruptedException {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            Callable c = new StringTask();
            Future f1 = ecs.submit(c);
            Future f2 = ecs.take();
            assertSame(f1, f2);
        }
    }

    /**
     * If poll returns non-null, the returned task is completed
     */
    public void testPoll1() throws Exception {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            assertNull(ecs.poll());
            Callable c = new StringTask();
            ecs.submit(c);

            long startTime = System.nanoTime();
            Future f;
            while ((f = ecs.poll()) == null) {
                if (millisElapsedSince(startTime) > LONG_DELAY_MS)
                    fail("timed out");
                Thread.yield();
            }
            assertTrue(f.isDone());
            assertSame(TEST_STRING, f.get());
        }
    }

    /**
     * If timed poll returns non-null, the returned task is completed
     */
    public void testPoll2() throws InterruptedException {
        final ExecutorService e = Executors.newCachedThreadPool();
        final ExecutorCompletionService ecs = new ExecutorCompletionService(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            assertNull(ecs.poll());
            Callable c = new StringTask();
            ecs.submit(c);
            Future f = ecs.poll(SHORT_DELAY_MS, MILLISECONDS);
            if (f != null)
                assertTrue(f.isDone());
        }
    }

    /**
     * Submitting to underlying AES that overrides newTaskFor(Callable)
     * returns and eventually runs Future returned by newTaskFor.
     */
    public void testNewTaskForCallable() throws InterruptedException {
        final AtomicBoolean done = new AtomicBoolean(false);
        class MyCallableFuture<V> extends FutureTask<V> {
            MyCallableFuture(Callable<V> c) { super(c); }
            protected void done() { done.set(true); }
        }
        final ExecutorService e =
            new ThreadPoolExecutor(1, 1,
                                   30L, TimeUnit.SECONDS,
                                   new ArrayBlockingQueue<Runnable>(1)) {
                protected <T> RunnableFuture<T> newTaskFor(Callable<T> c) {
                    return new MyCallableFuture<T>(c);
                }};
        ExecutorCompletionService<String> ecs =
            new ExecutorCompletionService<String>(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            assertNull(ecs.poll());
            Callable<String> c = new StringTask();
            Future f1 = ecs.submit(c);
            assertTrue("submit must return MyCallableFuture",
                       f1 instanceof MyCallableFuture);
            Future f2 = ecs.take();
            assertSame("submit and take must return same objects", f1, f2);
            assertTrue("completed task must have set done", done.get());
        }
    }

    /**
     * Submitting to underlying AES that overrides newTaskFor(Runnable,T)
     * returns and eventually runs Future returned by newTaskFor.
     */
    public void testNewTaskForRunnable() throws InterruptedException {
        final AtomicBoolean done = new AtomicBoolean(false);
        class MyRunnableFuture<V> extends FutureTask<V> {
            MyRunnableFuture(Runnable t, V r) { super(t, r); }
            protected void done() { done.set(true); }
        }
        final ExecutorService e =
            new ThreadPoolExecutor(1, 1,
                                   30L, TimeUnit.SECONDS,
                                   new ArrayBlockingQueue<Runnable>(1)) {
                protected <T> RunnableFuture<T> newTaskFor(Runnable t, T r) {
                    return new MyRunnableFuture<T>(t, r);
                }};
        final ExecutorCompletionService<String> ecs =
            new ExecutorCompletionService<String>(e);
        try (PoolCleaner cleaner = cleaner(e)) {
            assertNull(ecs.poll());
            Runnable r = new NoOpRunnable();
            Future f1 = ecs.submit(r, null);
            assertTrue("submit must return MyRunnableFuture",
                       f1 instanceof MyRunnableFuture);
            Future f2 = ecs.take();
            assertSame("submit and take must return same objects", f1, f2);
            assertTrue("completed task must have set done", done.get());
        }
    }

}
