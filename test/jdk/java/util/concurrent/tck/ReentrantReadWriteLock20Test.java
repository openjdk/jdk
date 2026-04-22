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

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReentrantReadWriteLock20Test extends JSR166TestCase {
    public static void main(String[] args) {
        main(suite(), args);
    }
    public static Test suite() {
        return new TestSuite(ReentrantReadWriteLock20Test.class);
    }
    public void test66kReadersFair() throws InterruptedException   { test66kReaders(true); }
    public void test66kReadersUnfair() throws InterruptedException { test66kReaders(false); }

    private void test66kReaders(boolean fairness) throws InterruptedException {
        final var failure = new AtomicReference<Throwable>();
        final var lock = new ReentrantReadWriteLock(fairness);
        final var numThreads = 0x10000;
        final var threads = new ArrayDeque<Thread>(numThreads);
        final var latch = new CountDownLatch(1);
        try {
            for(int i = 0; i < numThreads && failure.get() == null;++i) {
                var t = Thread.ofVirtual().unstarted(() -> {

                    try {
                        lock.readLock().lock();
                    } catch (Throwable ex) {
                        failure.compareAndSet(null, ex);
                        return;
                    }

                    try {
                        while (latch.getCount() > 0) {
                            try {
                                latch.await();
                            } catch (InterruptedException ie) {
                                failure.compareAndSet(null, ie);
                            }
                        }
                    }
                    finally {
                        lock.readLock().unlock();
                    }
                });

                threads.addLast(t);
                t.start();
            }
        } finally {
            latch.countDown(); // Make sure waiters are signalled
            Thread next;
            while ((next = threads.pollFirst()) != null) {
                while (next.isAlive()) {
                    next.join();
                }
            }
        }

        assertEquals(null, failure.get());
    }
}
