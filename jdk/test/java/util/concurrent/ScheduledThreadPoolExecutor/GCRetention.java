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
 * Written by Doug Lea and Martin Buchholz with assistance from members of
 * JCP JSR-166 Expert Group and released to the public domain, as explained
 * at http://creativecommons.org/publicdomain/zero/1.0/
 */

/*
 * @test
 * @summary Ensure that waiting pool threads don't retain refs to tasks.
 */

import java.lang.ref.*;
import java.util.concurrent.*;

public class GCRetention {
    /**
     * A custom thread pool with a custom RunnableScheduledFuture, for the
     * sole purpose of ensuring that the task retains a strong reference to
     * the Runnable even after cancellation.
     */
    static class CustomPool extends ScheduledThreadPoolExecutor {
        CustomPool(int n) { super(n); }
        protected <V> RunnableScheduledFuture<V> decorateTask(
            final Runnable r,
            final RunnableScheduledFuture<V> task) {
            return new RunnableScheduledFuture<V>() {
                public void run() { System.err.println(r); task.run(); }
                public boolean isPeriodic() { return task.isPeriodic(); }
                public V get()
                    throws InterruptedException,ExecutionException
                    { return task.get(); }
                public V get(long x, TimeUnit y)
                    throws InterruptedException,ExecutionException,TimeoutException
                    { return task.get(x, y); }
                public boolean isDone() { return task.isDone(); }
                public boolean isCancelled() { return task.isCancelled(); }
                public boolean cancel(boolean x) { return task.cancel(x); }
                public long getDelay(TimeUnit x) { return task.getDelay(x); }
                public int compareTo(Delayed x) { return task.compareTo(x); }
            };
        }
    }

    int countRefsCleared(WeakReference<?>[] refs) {
        int count = 0;
        for (WeakReference<?> ref : refs)
            if (ref.get() == null)
                count++;
        return count;
    }

    void test(String[] args) throws Throwable {
        CustomPool pool = new CustomPool(10);
        final int size = 100;
        WeakReference<?>[] refs = new WeakReference<?>[size];
        Future<?>[] futures = new Future<?>[size];
        for (int i = 0; i < size; i++) {
            final Object x = new Object();
            refs[i] = new WeakReference<Object>(x);
            // Create a Runnable with a strong ref to x.
            Runnable r = new Runnable() {
                    public void run() { System.out.println(x); }
                };
            // Schedule a custom task with a strong reference to r.
            // Later tasks have earlier expiration, to ensure multiple
            // residents of queue head.
            futures[i] = pool.schedule(r, size*2-i, TimeUnit.MINUTES);
        }
        Thread.sleep(10);
        for (int i = 0; i < size; i++) {
            if (futures[i] != null) {
                futures[i].cancel(false);
                futures[i] = null;
            }
        }
        pool.purge();
        int cleared = 0;
        for (int i = 0;
             i < 10 && (cleared = countRefsCleared(refs)) < size;
             i++) {
            System.gc();
            System.runFinalization();
            Thread.sleep(10);
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        if (cleared < size)
            throw new Error(String.format
                            ("references to %d/%d tasks retained (\"leaked\")",
                             size - cleared, size));
    }

    //--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new GCRetention().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
