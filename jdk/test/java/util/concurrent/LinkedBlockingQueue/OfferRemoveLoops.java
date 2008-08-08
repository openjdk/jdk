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

import java.util.concurrent.*;

public class OfferRemoveLoops {
    private static void realMain(String[] args) throws Throwable {
        testQueue(new LinkedBlockingQueue<String>(10));
        testQueue(new LinkedBlockingQueue<String>());
        testQueue(new LinkedBlockingDeque<String>(10));
        testQueue(new LinkedBlockingDeque<String>());
        testQueue(new ArrayBlockingQueue<String>(10));
        testQueue(new PriorityBlockingQueue<String>(10));
    }

    private abstract static class ControlledThread extends Thread {
        abstract protected void realRun();
        public void run() {
            try { realRun(); } catch (Throwable t) { unexpected(t); }
        }
    }

    private static void testQueue(final BlockingQueue<String> q) throws Throwable {
        System.out.println(q.getClass());
        final int count = 10000;
        final long quittingTime = System.nanoTime() + 1L * 1000L * 1000L * 1000L;
        Thread t1 = new ControlledThread() {
                protected void realRun() {
                    for (int i = 0, j = 0; i < count; i++)
                        while (! q.remove(String.valueOf(i))
                               && System.nanoTime() - quittingTime < 0)
                            Thread.yield();}};
        Thread t2 = new ControlledThread() {
                protected void realRun() {
                    for (int i = 0, j = 0; i < count; i++)
                        while (! q.offer(String.valueOf(i))
                               && System.nanoTime() - quittingTime < 0)
                            Thread.yield();}};
        t1.setDaemon(true); t2.setDaemon(true);
        t1.start(); t2.start();
        t1.join(10000); t2.join(10000);
        check(! t1.isAlive());
        check(! t2.isAlive());
    }

    //--------------------- Infrastructure ---------------------------
    static volatile int passed = 0, failed = 0;
    static void pass() { passed++; }
    static void fail() { failed++; Thread.dumpStack(); }
    static void unexpected(Throwable t) { failed++; t.printStackTrace(); }
    static void check(boolean cond) { if (cond) pass(); else fail(); }
    static void equal(Object x, Object y) {
        if (x == null ? y == null : x.equals(y)) pass();
        else {System.out.println(x + " not equal to " + y); fail(); }}

    public static void main(String[] args) throws Throwable {
        try { realMain(args); } catch (Throwable t) { unexpected(t); }

        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new Exception("Some tests failed");
    }
}
