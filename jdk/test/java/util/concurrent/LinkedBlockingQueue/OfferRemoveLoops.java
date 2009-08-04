/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @test
 * @bug 6316155 6595669
 * @summary Test concurrent offer vs. remove
 * @author Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;

public class OfferRemoveLoops {
    void test(String[] args) throws Throwable {
        testQueue(new LinkedBlockingQueue<String>(10));
        testQueue(new LinkedBlockingQueue<String>());
        testQueue(new LinkedBlockingDeque<String>(10));
        testQueue(new LinkedBlockingDeque<String>());
        testQueue(new ArrayBlockingQueue<String>(10));
        testQueue(new PriorityBlockingQueue<String>(10));
        testQueue(new ConcurrentLinkedQueue<String>());
    }

    abstract class CheckedThread extends Thread {
        abstract protected void realRun();
        public void run() {
            try { realRun(); } catch (Throwable t) { unexpected(t); }
        }
    }

    void testQueue(final Queue<String> q) throws Throwable {
        System.out.println(q.getClass().getSimpleName());
        final int count = 1000 * 1000;
        final long testDurationSeconds = 1L;
        final long testDurationMillis = testDurationSeconds * 1000L;
        final long quittingTimeNanos
            = System.nanoTime() + testDurationSeconds * 1000L * 1000L * 1000L;
        Thread t1 = new CheckedThread() {
            protected void realRun() {
                for (int i = 0; i < count; i++) {
                    if ((i % 1024) == 0 &&
                        System.nanoTime() - quittingTimeNanos > 0)
                        return;
                    while (! q.remove(String.valueOf(i)))
                        Thread.yield();
                }}};
        Thread t2 = new CheckedThread() {
            protected void realRun() {
                for (int i = 0; i < count; i++) {
                    if ((i % 1024) == 0 &&
                        System.nanoTime() - quittingTimeNanos > 0)
                        return;
                    while (! q.offer(String.valueOf(i)))
                        Thread.yield();
                    }}};
        t1.setDaemon(true); t2.setDaemon(true);
        t1.start(); t2.start();
        t1.join(10 * testDurationMillis);
        t2.join(10 * testDurationMillis);
        check(! t1.isAlive());
        check(! t2.isAlive());
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
        new OfferRemoveLoops().instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
