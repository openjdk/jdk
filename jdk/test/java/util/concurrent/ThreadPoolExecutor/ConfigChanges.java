/*
 * Copyright (c) 2007, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6450200
 * @summary Test proper handling of pool state changes
 * @run main/othervm ConfigChanges
 * @author Martin Buchholz
 */

import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static java.util.concurrent.TimeUnit.*;

public class ConfigChanges {
    static final ThreadGroup tg = new ThreadGroup("pool");

    static final Random rnd = new Random();

    static void report(ThreadPoolExecutor tpe) {
        try {
            System.out.printf(
                "active=%d submitted=%d completed=%d queued=%d sizes=%d/%d/%d%n",
                tg.activeCount(),
                tpe.getTaskCount(),
                tpe.getCompletedTaskCount(),
                tpe.getQueue().size(),
                tpe.getPoolSize(),
                tpe.getCorePoolSize(),
                tpe.getMaximumPoolSize());
        } catch (Throwable t) { unexpected(t); }
    }

    static void report(String label, ThreadPoolExecutor tpe) {
        System.out.printf("%10s ", label);
        report(tpe);
    }

    static class PermissiveSecurityManger extends SecurityManager {
        public void checkPermission(Permission p) { /* bien sur, Monsieur */ }
    }

    static void checkShutdown(final ExecutorService es) {
        final Runnable nop = new Runnable() {public void run() {}};
        try {
            if (new Random().nextBoolean()) {
                check(es.isShutdown());
                if (es instanceof ThreadPoolExecutor)
                    check(((ThreadPoolExecutor) es).isTerminating()
                          || es.isTerminated());
                THROWS(RejectedExecutionException.class,
                       () -> es.execute(nop));
            }
        } catch (Throwable t) { unexpected(t); }
    }

    static void checkTerminated(final ThreadPoolExecutor tpe) {
        try {
            checkShutdown(tpe);
            check(tpe.getQueue().isEmpty());
            check(tpe.isTerminated());
            check(! tpe.isTerminating());
            equal(tpe.getActiveCount(), 0);
            equal(tpe.getPoolSize(), 0);
            equal(tpe.getTaskCount(), tpe.getCompletedTaskCount());
            check(tpe.awaitTermination(0, SECONDS));
        } catch (Throwable t) { unexpected(t); }
    }

    static Runnable waiter(final CyclicBarrier barrier) {
        return new Runnable() { public void run() {
            try { barrier.await(); barrier.await(); }
            catch (Throwable t) { unexpected(t); }}};
    }

    static volatile Runnable runnableDuJour;

    private static void realMain(String[] args) throws Throwable {
        if (rnd.nextBoolean())
            System.setSecurityManager(new PermissiveSecurityManger());

        final boolean prestart = rnd.nextBoolean();

        final Thread.UncaughtExceptionHandler handler
            = new Thread.UncaughtExceptionHandler() {
                    public void uncaughtException(Thread t, Throwable e) {
                        check(! Thread.currentThread().isInterrupted());
                        unexpected(e);
                    }};

        final int n = 3;
        final ThreadPoolExecutor tpe
            = new ThreadPoolExecutor(n, 3*n,
                                     3L, MINUTES,
                                     new ArrayBlockingQueue<Runnable>(3*n));
        tpe.setThreadFactory(new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(tg, r);
                    t.setUncaughtExceptionHandler(handler);
                    return t;
                }});

        if (prestart) {
            tpe.prestartAllCoreThreads();
            equal(tg.activeCount(), n);
            equal(tg.activeCount(), tpe.getCorePoolSize());
        }

        final Runnable runRunnableDuJour =
            new Runnable() { public void run() {
                // Delay choice of action till last possible moment.
                runnableDuJour.run(); }};
        final CyclicBarrier pumpedUp = new CyclicBarrier(3*n + 1);
        runnableDuJour = waiter(pumpedUp);

        if (prestart) {
            for (int i = 0; i < 1*n; i++)
                tpe.execute(runRunnableDuJour);
            // Wait for prestarted threads to dequeue their initial tasks.
            while (! tpe.getQueue().isEmpty())
                Thread.sleep(10);
            for (int i = 0; i < 5*n; i++)
                tpe.execute(runRunnableDuJour);
        } else {
            for (int i = 0; i < 6*n; i++)
                tpe.execute(runRunnableDuJour);
        }

        //report("submitted", tpe);
        pumpedUp.await();
        equal(tg.activeCount(), 3*n);
        equal(tg.activeCount(), tpe.getMaximumPoolSize());
        equal(tpe.getCorePoolSize(), n);
        //report("pumped up", tpe);
        equal(tpe.getMaximumPoolSize(), 3*n);
        tpe.setMaximumPoolSize(4*n);
        equal(tpe.getMaximumPoolSize(), 4*n);
        //report("pumped up2", tpe);
        final CyclicBarrier pumpedUp2 = new CyclicBarrier(n + 1);
        runnableDuJour = waiter(pumpedUp2);
        for (int i = 0; i < 1*n; i++)
            tpe.execute(runRunnableDuJour);
        pumpedUp2.await();
        equal(tg.activeCount(), 4*n);
        equal(tg.activeCount(), tpe.getMaximumPoolSize());
        equal(tpe.getCompletedTaskCount(), 0L);
        //report("pumped up2", tpe);
        runnableDuJour = new Runnable() { public void run() {}};

        tpe.setMaximumPoolSize(2*n);
        //report("after set", tpe);

        pumpedUp2.await();
        pumpedUp.await();

//      while (tg.activeCount() != n &&
//             tg.activeCount() != n)
//          Thread.sleep(10);
//      equal(tg.activeCount(), n);
//      equal(tg.activeCount(), tpe.getCorePoolSize());

        while (tg.activeCount() != 2*n &&
               tg.activeCount() != 2*n)
            Thread.sleep(10);
        equal(tg.activeCount(), 2*n);
        equal(tg.activeCount(), tpe.getMaximumPoolSize());


//report("draining", tpe);
        while (tpe.getCompletedTaskCount() < 7*n &&
               tpe.getCompletedTaskCount() < 7*n)
            Thread.sleep(10);

        //equal(tg.activeCount(), n);
        //equal(tg.activeCount(), tpe.getCorePoolSize());
        equal(tg.activeCount(), 2*n);
        equal(tg.activeCount(), tpe.getMaximumPoolSize());

        equal(tpe.getTaskCount(), 7L*n);
        equal(tpe.getCompletedTaskCount(), 7L*n);

        equal(tpe.getKeepAliveTime(MINUTES), 3L);
        tpe.setKeepAliveTime(7L, MILLISECONDS);
        equal(tpe.getKeepAliveTime(MILLISECONDS), 7L);
        while (tg.activeCount() > n &&
               tg.activeCount() > n)
            Thread.sleep(10);
        equal(tg.activeCount(), n);

        //report("idle", tpe);
        check(! tpe.allowsCoreThreadTimeOut());
        tpe.allowCoreThreadTimeOut(true);
        check(tpe.allowsCoreThreadTimeOut());
        while (tg.activeCount() > 0 &&
               tg.activeCount() > 0)
            Thread.sleep(10);
        equal(tg.activeCount(), 0);

        //report("idle", tpe);

        tpe.shutdown();
        checkShutdown(tpe);
        check(tpe.awaitTermination(3L, MINUTES));
        checkTerminated(tpe);
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
    interface Fun {void f() throws Throwable;}
    static void THROWS(Class<? extends Throwable> k, Fun... fs) {
        for (Fun f : fs)
            try { f.f(); fail("Expected " + k.getName() + " not thrown"); }
            catch (Throwable t) {
                if (k.isAssignableFrom(t.getClass())) pass();
                else unexpected(t);}}
}
