/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4992438 6633113 8314515
 * @summary Checks that fairness setting is respected.
 */

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

public class Fairness {
    private static void testFairness(boolean fair, SynchronousQueue<Integer> q)
        throws Throwable
    {
        int threadCount = ThreadLocalRandom.current().nextInt(2, 8);
        var badness = new AtomicReference<Throwable>();
        var ts = new ArrayList<Thread>();
        for (int i = 0; i < threadCount; i++) {
            final Integer finali = i;
            CountDownLatch ready = new CountDownLatch(1);
            Runnable put = () -> {
                try {
                    ready.countDown();
                    q.put(finali);
                } catch (Throwable fail) { badness.set(fail); }
            };
            Thread t = new Thread(put);
            t.start();
            ts.add(t);
            ready.await();
            // Force queueing order by waiting for each thread to block in q.put
            // before starting the next
            while (t.getState() == Thread.State.RUNNABLE)
                Thread.yield();
        }
        for (int i = 0; i < threadCount; i++) {
            int j = q.take();
            // Fair queues are specified to be FIFO.
            // Non-fair queues are LIFO in our implementation.
            if (fair ? j != i : j != threadCount - 1 - i)
                throw new Error(String.format("fair=%b i=%d/%d j=%d%n",
                                              fair, i, threadCount, j));
        }
        for (Thread t : ts) t.join();
        if (badness.get() != null) throw new Error(badness.get());
    }

    public static void main(String[] args) throws Throwable {
        testFairness(false, new SynchronousQueue<Integer>());
        testFairness(false, new SynchronousQueue<Integer>(false));
        testFairness(true,  new SynchronousQueue<Integer>(true));
    }
}
