/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6450200 6450205 6450207 6450211
 * @summary Test proper handling of tasks that terminate abruptly
 * @run main/othervm -XX:-UseVMInterruptibleIO ThrowingTasks
 * @author Martin Buchholz
 */

import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ThrowingTasks {
    static final Random rnd = new Random();

    @SuppressWarnings("serial")
    static class UncaughtExceptions
        extends ConcurrentHashMap<Class<?>, Integer> {

        void inc(Class<?> key) {
            for (;;) {
                Integer i = get(key);
                if (i == null) {
                    if (putIfAbsent(key, 1) == null)
                        return;
                } else {
                    if (replace(key, i, i + 1))
                        return;
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static class UncaughtExceptionsTable
        extends Hashtable<Class<?>, Integer> {

        synchronized void inc(Class<?> key) {
            Integer i = get(key);
            put(key, (i == null) ? 1 : i + 1);
        }
    }

    static final UncaughtExceptions uncaughtExceptions
        = new UncaughtExceptions();
    static final UncaughtExceptionsTable uncaughtExceptionsTable
        = new UncaughtExceptionsTable();
    static final AtomicLong totalUncaughtExceptions
        = new AtomicLong(0);
    static final CountDownLatch uncaughtExceptionsLatch
        = new CountDownLatch(24);

    static final Thread.UncaughtExceptionHandler handler
        = new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    check(! Thread.currentThread().isInterrupted());
                    totalUncaughtExceptions.getAndIncrement();
                    uncaughtExceptions.inc(e.getClass());
                    uncaughtExceptionsTable.inc(e.getClass());
                    uncaughtExceptionsLatch.countDown();
                }};

    static final ThreadGroup tg = new ThreadGroup("Flaky");

    static final ThreadFactory tf = new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(tg, r);
                t.setUncaughtExceptionHandler(handler);
                return t;
            }};

    static final RuntimeException rte = new RuntimeException();
    static final Error error = new Error();
    static final Throwable weird = new Throwable();
    static final Exception checkedException = new Exception();

    static class Thrower implements Runnable {
        Throwable t;
        Thrower(Throwable t) { this.t = t; }
        @SuppressWarnings("deprecation")
        public void run() { if (t != null) Thread.currentThread().stop(t); }
    }

    static final Thrower noThrower      = new Thrower(null);
    static final Thrower rteThrower     = new Thrower(rte);
    static final Thrower errorThrower   = new Thrower(error);
    static final Thrower weirdThrower   = new Thrower(weird);
    static final Thrower checkedThrower = new Thrower(checkedException);

    static final List<Thrower> throwers = Arrays.asList(
        noThrower, rteThrower, errorThrower, weirdThrower, checkedThrower);

    static class Flaky implements Runnable {
        final Runnable beforeExecute;
        final Runnable execute;
        Flaky(Runnable beforeExecute,
              Runnable execute) {
            this.beforeExecute = beforeExecute;
            this.execute = execute;
        }
        public void run() { execute.run(); }
    }

    static final List<Flaky> flakes = new ArrayList<Flaky>();
    static {
        for (Thrower x : throwers)
            for (Thrower y : throwers)
                flakes.add(new Flaky(x, y));
        Collections.shuffle(flakes);
    }

    static final CountDownLatch allStarted = new CountDownLatch(flakes.size());
    static final CountDownLatch allContinue = new CountDownLatch(1);

    static class PermissiveSecurityManger extends SecurityManager {
        public void checkPermission(Permission p) { /* bien sur, Monsieur */ }
    }

    static void checkTerminated(ThreadPoolExecutor tpe) {
        try {
            check(tpe.getQueue().isEmpty());
            check(tpe.isShutdown());
            check(tpe.isTerminated());
            check(! tpe.isTerminating());
            equal(tpe.getActiveCount(), 0);
            equal(tpe.getPoolSize(), 0);
            equal(tpe.getTaskCount(), tpe.getCompletedTaskCount());
            check(tpe.awaitTermination(0, TimeUnit.SECONDS));
        } catch (Throwable t) { unexpected(t); }
    }

    static class CheckingExecutor extends ThreadPoolExecutor {
        CheckingExecutor() {
            super(10, 10,
                  1L, TimeUnit.HOURS,
                  new LinkedBlockingQueue<Runnable>(),
                  tf);
        }
        @Override protected void beforeExecute(Thread t, Runnable r) {
            allStarted.countDown();
            if (allStarted.getCount() < getCorePoolSize())
                try { allContinue.await(); }
                catch (InterruptedException x) { unexpected(x); }
            beforeExecuteCount.getAndIncrement();
            check(! isTerminated());
            ((Flaky)r).beforeExecute.run();
        }
        @Override protected void afterExecute(Runnable r, Throwable t) {
            //System.out.println(tg.activeCount());
            afterExecuteCount.getAndIncrement();
            check(((Thrower)((Flaky)r).execute).t == t);
            check(! isTerminated());
        }
        @Override protected void terminated() {
            try {
                terminatedCount.getAndIncrement();
                if (rnd.nextBoolean()) {
                    check(isShutdown());
                    check(isTerminating());
                    check(! isTerminated());
                    check(! awaitTermination(0L, TimeUnit.MINUTES));
                }
            } catch (Throwable t) { unexpected(t); }
        }
    }

    static final AtomicInteger beforeExecuteCount = new AtomicInteger(0);
    static final AtomicInteger afterExecuteCount  = new AtomicInteger(0);
    static final AtomicInteger terminatedCount    = new AtomicInteger(0);

    private static void realMain(String[] args) throws Throwable {
        if (rnd.nextBoolean())
            System.setSecurityManager(new PermissiveSecurityManger());

        CheckingExecutor tpe = new CheckingExecutor();

        for (Runnable task : flakes)
            tpe.execute(task);

        if (rnd.nextBoolean()) {
            allStarted.await();
            equal(tpe.getTaskCount(),
                  (long) flakes.size());
            equal(tpe.getCompletedTaskCount(),
                  (long) flakes.size() - tpe.getCorePoolSize());
        }
        allContinue.countDown();

        //System.out.printf("thread count = %d%n", tg.activeCount());
        uncaughtExceptionsLatch.await();

        while (tg.activeCount() != tpe.getCorePoolSize() ||
               tg.activeCount() != tpe.getCorePoolSize())
            Thread.sleep(10);
        equal(tg.activeCount(), tpe.getCorePoolSize());

        tpe.shutdown();

        check(tpe.awaitTermination(10L, TimeUnit.MINUTES));
        checkTerminated(tpe);

        //while (tg.activeCount() > 0) Thread.sleep(10);
        //System.out.println(uncaughtExceptions);
        List<Map<Class<?>, Integer>> maps
            = new ArrayList<Map<Class<?>, Integer>>();
        maps.add(uncaughtExceptions);
        maps.add(uncaughtExceptionsTable);
        for (Map<Class<?>, Integer> map : maps) {
            equal(map.get(Exception.class), throwers.size());
            equal(map.get(weird.getClass()), throwers.size());
            equal(map.get(Error.class), throwers.size() + 1 + 2);
            equal(map.get(RuntimeException.class), throwers.size() + 1);
            equal(map.size(), 4);
        }
        equal(totalUncaughtExceptions.get(), 4L*throwers.size() + 4L);

        equal(beforeExecuteCount.get(), flakes.size());
        equal(afterExecuteCount.get(), throwers.size());
        equal(tpe.getCompletedTaskCount(), (long) flakes.size());
        equal(terminatedCount.get(), 1);

        // check for termination operation idempotence
        tpe.shutdown();
        tpe.shutdownNow();
        check(tpe.awaitTermination(10L, TimeUnit.MINUTES));
        checkTerminated(tpe);
        equal(terminatedCount.get(), 1);
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() {passed++;}
    static void fail() {failed++; Thread.dumpStack();}
    static void fail(String msg) {System.out.println(msg); fail();}
    static void unexpected(Throwable t) {failed++; t.printStackTrace();}
    static void check(boolean cond) {if (cond) pass(); else fail();}
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        try {realMain(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
