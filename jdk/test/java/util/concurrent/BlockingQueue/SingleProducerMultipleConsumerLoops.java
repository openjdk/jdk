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
 * @bug 4486658
 * @compile -source 1.5 SingleProducerMultipleConsumerLoops.java
 * @run main/timeout=600 SingleProducerMultipleConsumerLoops
 * @summary  check ordering for blocking queues with 1 producer and multiple consumers
 */

import java.util.concurrent.*;

public class SingleProducerMultipleConsumerLoops {
    static final int CAPACITY =      100;

    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;

    public static void main(String[] args) throws Exception {
        int maxConsumers = 5;
        int iters = 10000;

        if (args.length > 0)
            maxConsumers = Integer.parseInt(args[0]);

        print = false;
        System.out.println("Warmup...");
        oneTest(1, 10000);
        Thread.sleep(100);
        oneTest(2, 10000);
        Thread.sleep(100);
        print = true;

        for (int i = 1; i <= maxConsumers; i += (i+1) >>> 1) {
            System.out.println("----------------------------------------");
            System.out.println("Consumers: " + i);
            oneTest(i, iters);
            Thread.sleep(100);
        }
        pool.shutdown();
        if (! pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            throw new Error();
   }

    static final class LTQasSQ<T> extends LinkedTransferQueue<T> {
        LTQasSQ() { super(); }
        public void put(T x) {
            try { super.transfer(x); }
            catch (InterruptedException ex) { throw new Error(); }
        }
        private final static long serialVersionUID = 42;
    }

    static final class HalfSyncLTQ<T> extends LinkedTransferQueue<T> {
        HalfSyncLTQ() { super(); }
        public void put(T x) {
            if (ThreadLocalRandom.current().nextBoolean())
                super.put(x);
            else {
                try { super.transfer(x); }
                catch (InterruptedException ex) { throw new Error(); }
            }
        }
        private final static long serialVersionUID = 42;
    }

    static void oneTest(int consumers, int iters) throws Exception {
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY), consumers, iters);
        oneRun(new LinkedBlockingQueue<Integer>(CAPACITY), consumers, iters);
        oneRun(new LinkedBlockingDeque<Integer>(CAPACITY), consumers, iters);
        oneRun(new LinkedTransferQueue<Integer>(), consumers, iters);
        oneRun(new LTQasSQ<Integer>(), consumers, iters);
        oneRun(new HalfSyncLTQ<Integer>(), consumers, iters);
        oneRun(new PriorityBlockingQueue<Integer>(), consumers, iters);
        oneRun(new SynchronousQueue<Integer>(), consumers, iters);
        if (print)
            System.out.println("fair implementations:");
        oneRun(new SynchronousQueue<Integer>(true), consumers, iters);
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY, true), consumers, iters);
    }

    static abstract class Stage implements Runnable {
        final int iters;
        final BlockingQueue<Integer> queue;
        final CyclicBarrier barrier;
        volatile int result;
        Stage (BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            queue = q;
            barrier = b;
            this.iters = iters;
        }
    }

    static class Producer extends Stage {
        Producer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);
        }

        public void run() {
            try {
                barrier.await();
                for (int i = 0; i < iters; ++i) {
                    queue.put(new Integer(i));
                }
                barrier.await();
                result = 432;
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }
    }

    static class Consumer extends Stage {
        Consumer(BlockingQueue<Integer> q, CyclicBarrier b, int iters) {
            super(q, b, iters);
        }

        public void run() {
            try {
                barrier.await();
                int l = 0;
                int s = 0;
                int last = -1;
                for (int i = 0; i < iters; ++i) {
                    Integer item = queue.take();
                    int v = item.intValue();
                    if (v < last)
                        throw new Error("Out-of-Order transfer");
                    last = v;
                    l = LoopHelpers.compute1(v);
                    s += l;
                }
                barrier.await();
                result = s;
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int nconsumers, int iters) throws Exception {
        if (print)
            System.out.printf("%-18s", q.getClass().getSimpleName());
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(nconsumers + 2, timer);
        pool.execute(new Producer(q, barrier, iters * nconsumers));
        for (int i = 0; i < nconsumers; ++i) {
            pool.execute(new Consumer(q, barrier, iters));
        }
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * nconsumers)) + " ns per transfer");
    }

}
