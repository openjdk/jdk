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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

/*
 * @test
 * @bug 6805775 6815766
 * @summary Test concurrent offer vs. drainTo
 */

import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings({"unchecked", "rawtypes"})
public class OfferDrainToLoops {
    void checkNotContainsNull(Iterable it) {
        for (Object x : it)
            check(x != null);
    }

    abstract class CheckedThread extends Thread {
        abstract protected void realRun();
        public void run() {
            try { realRun(); } catch (Throwable t) { unexpected(t); }
        }
        {
            setDaemon(true);
            start();
        }
    }

    void test(String[] args) throws Throwable {
        test(new LinkedBlockingQueue());
        test(new LinkedBlockingQueue(2000));
        test(new LinkedBlockingDeque());
        test(new LinkedBlockingDeque(2000));
        test(new ArrayBlockingQueue(2000));
    }

    void test(final BlockingQueue q) throws Throwable {
        System.out.println(q.getClass().getSimpleName());
        final long testDurationSeconds = 1L;
        final long testDurationMillis = testDurationSeconds * 1000L;
        final long quittingTimeNanos
            = System.nanoTime() + testDurationSeconds * 1000L * 1000L * 1000L;

        Thread offerer = new CheckedThread() {
            protected void realRun() {
                for (long i = 0; ; i++) {
                    if ((i % 1024) == 0 &&
                        System.nanoTime() - quittingTimeNanos > 0)
                        break;
                    while (! q.offer(i))
                        Thread.yield();
                }}};

        Thread drainer = new CheckedThread() {
            protected void realRun() {
                for (long i = 0; ; i++) {
                    if (System.nanoTime() - quittingTimeNanos > 0)
                        break;
                    List list = new ArrayList();
                    int n = q.drainTo(list);
                    equal(list.size(), n);
                    for (int j = 0; j < n - 1; j++)
                        equal((Long) list.get(j) + 1L, list.get(j + 1));
                    Thread.yield();
                }}};

        Thread scanner = new CheckedThread() {
            protected void realRun() {
                for (long i = 0; ; i++) {
                    if (System.nanoTime() - quittingTimeNanos > 0)
                        break;
                    checkNotContainsNull(q);
                    Thread.yield();
                }}};

        offerer.join(10 * testDurationMillis);
        drainer.join(10 * testDurationMillis);
        check(! offerer.isAlive());
        check(! drainer.isAlive());
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
        new OfferDrainToLoops().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
