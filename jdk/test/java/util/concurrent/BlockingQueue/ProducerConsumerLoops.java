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
 * @compile -source 1.5 ProducerConsumerLoops.java
 * @run main/timeout=3600 ProducerConsumerLoops
 * @summary  multiple producers and consumers using blocking queues
 */

import java.util.concurrent.*;

public class ProducerConsumerLoops {
    static final int CAPACITY =      100;

    static final ExecutorService pool = Executors.newCachedThreadPool();
    static boolean print = false;
    static int producerSum;
    static int consumerSum;
    static synchronized void addProducerSum(int x) {
        producerSum += x;
    }

    static synchronized void addConsumerSum(int x) {
        consumerSum += x;
    }

    static synchronized void checkSum() {
        if (producerSum != consumerSum)
            throw new Error("CheckSum mismatch");
    }

    public static void main(String[] args) throws Exception {
        int maxPairs = 8;
        int iters = 10000;

        if (args.length > 0)
            maxPairs = Integer.parseInt(args[0]);

        print = false;
        System.out.println("Warmup...");
        oneTest(1, 10000);
        Thread.sleep(100);
        oneTest(2, 10000);
        Thread.sleep(100);
        print = true;

        for (int i = 1; i <= maxPairs; i += (i+1) >>> 1) {
            System.out.println("----------------------------------------");
            System.out.println("Pairs: " + i);
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

    static void oneTest(int pairs, int iters) throws Exception {
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY), pairs, iters);
        oneRun(new LinkedBlockingQueue<Integer>(CAPACITY), pairs, iters);
        oneRun(new LinkedBlockingDeque<Integer>(CAPACITY), pairs, iters);
        oneRun(new LinkedTransferQueue<Integer>(), pairs, iters);
        oneRun(new LTQasSQ<Integer>(), pairs, iters);
        oneRun(new HalfSyncLTQ<Integer>(), pairs, iters);
        oneRun(new PriorityBlockingQueue<Integer>(), pairs, iters);
        oneRun(new SynchronousQueue<Integer>(), pairs, iters);

        if (print)
            System.out.println("fair implementations:");

        oneRun(new SynchronousQueue<Integer>(true), pairs, iters);
        oneRun(new ArrayBlockingQueue<Integer>(CAPACITY, true), pairs, iters);
    }

    static abstract class Stage implements Runnable {
        final int iters;
        final BlockingQueue<Integer> queue;
        final CyclicBarrier barrier;
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
                int s = 0;
                int l = hashCode();
                for (int i = 0; i < iters; ++i) {
                    l = LoopHelpers.compute2(l);
                    queue.put(new Integer(l));
                    s += LoopHelpers.compute1(l);
                }
                addProducerSum(s);
                barrier.await();
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
                for (int i = 0; i < iters; ++i) {
                    l = LoopHelpers.compute1(queue.take().intValue());
                    s += l;
                }
                addConsumerSum(s);
                barrier.await();
            }
            catch (Exception ie) {
                ie.printStackTrace();
                return;
            }
        }

    }

    static void oneRun(BlockingQueue<Integer> q, int npairs, int iters) throws Exception {
        if (print)
            System.out.printf("%-18s", q.getClass().getSimpleName());
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(npairs * 2 + 1, timer);
        for (int i = 0; i < npairs; ++i) {
            pool.execute(new Producer(q, barrier, iters));
            pool.execute(new Consumer(q, barrier, iters));
        }
        barrier.await();
        barrier.await();
        long time = timer.getTime();
        checkSum();
        if (print)
            System.out.println("\t: " + LoopHelpers.rightJustify(time / (iters * npairs)) + " ns per transfer");
    }

}
