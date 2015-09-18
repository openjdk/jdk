/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6316155 6595669 6871697 6868712
 * @summary Test concurrent offer vs. remove
 * @run main OfferRemoveLoops 300
 * @author Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class OfferRemoveLoops {
    final long testDurationMillisDefault = 10L * 1000L;
    final long testDurationMillis;

    OfferRemoveLoops(String[] args) {
        testDurationMillis = (args.length > 0) ?
            Long.valueOf(args[0]) : testDurationMillisDefault;
    }

    void checkNotContainsNull(Iterable it) {
        for (Object x : it)
            check(x != null);
    }

    void test(String[] args) throws Throwable {
        testQueue(new LinkedBlockingQueue(10));
        testQueue(new LinkedBlockingQueue());
        testQueue(new LinkedBlockingDeque(10));
        testQueue(new LinkedBlockingDeque());
        testQueue(new ArrayBlockingQueue(10));
        testQueue(new PriorityBlockingQueue(10));
        testQueue(new ConcurrentLinkedDeque());
        testQueue(new ConcurrentLinkedQueue());
        testQueue(new LinkedTransferQueue());
    }

    Random getRandom() {
        return ThreadLocalRandom.current();
    }

    void testQueue(final Queue q) throws Throwable {
        System.out.println(q.getClass().getSimpleName());
        final long testDurationNanos = testDurationMillis * 1000L * 1000L;
        final long quittingTimeNanos = System.nanoTime() + testDurationNanos;
        final long timeoutMillis = 10L * 1000L;
        final int maxChunkSize = 1042;
        final int maxQueueSize = 10 * maxChunkSize;

        /** Poor man's bounded buffer. */
        final AtomicLong approximateCount = new AtomicLong(0L);

        abstract class CheckedThread extends Thread {
            CheckedThread(String name) {
                super(name);
                setDaemon(true);
                start();
            }
            /** Polls for quitting time. */
            protected boolean quittingTime() {
                return System.nanoTime() - quittingTimeNanos > 0;
            }
            /** Polls occasionally for quitting time. */
            protected boolean quittingTime(long i) {
                return (i % 1024) == 0 && quittingTime();
            }
            protected abstract void realRun();
            public void run() {
                try { realRun(); } catch (Throwable t) { unexpected(t); }
            }
        }

        Thread offerer = new CheckedThread("offerer") {
            protected void realRun() {
                final long chunkSize = getRandom().nextInt(maxChunkSize) + 2;
                long c = 0;
                for (long i = 0; ! quittingTime(i); i++) {
                    if (q.offer(Long.valueOf(c))) {
                        if ((++c % chunkSize) == 0) {
                            approximateCount.getAndAdd(chunkSize);
                            while (approximateCount.get() > maxQueueSize)
                                Thread.yield();
                        }
                    } else {
                        Thread.yield();
                    }}}};

        Thread remover = new CheckedThread("remover") {
            protected void realRun() {
                final long chunkSize = getRandom().nextInt(maxChunkSize) + 2;
                long c = 0;
                for (long i = 0; ! quittingTime(i); i++) {
                    if (q.remove(Long.valueOf(c))) {
                        if ((++c % chunkSize) == 0) {
                            approximateCount.getAndAdd(-chunkSize);
                        }
                    } else {
                        Thread.yield();
                    }
                }
                q.clear();
                approximateCount.set(0); // Releases waiting offerer thread
            }};

        Thread scanner = new CheckedThread("scanner") {
            protected void realRun() {
                final Random rnd = getRandom();
                while (! quittingTime()) {
                    switch (rnd.nextInt(3)) {
                    case 0: checkNotContainsNull(q); break;
                    case 1: q.size(); break;
                    case 2: checkNotContainsNull
                            (Arrays.asList(q.toArray(new Long[0])));
                        break;
                    }
                    Thread.yield();
                }}};

        for (Thread thread : new Thread[] { offerer, remover, scanner }) {
            thread.join(timeoutMillis + testDurationMillis);
            if (thread.isAlive()) {
                System.err.printf("Hung thread: %s%n", thread.getName());
                failed++;
                for (StackTraceElement e : thread.getStackTrace())
                    System.err.println(e);
                // Kludge alert
                thread.stop();
                thread.join(timeoutMillis);
            }
        }
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
        new OfferRemoveLoops(args).instanceMain(args);}
    public void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}
