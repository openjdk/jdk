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
 * @compile -source 1.5 ConcurrentQueueLoops.java
 * @run main/timeout=230 ConcurrentQueueLoops
 * @summary Checks that a set of threads can repeatedly get and modify items
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class ConcurrentQueueLoops {
    static final ExecutorService pool = Executors.newCachedThreadPool();
    static AtomicInteger totalItems;
    static boolean print = false;

    public static void main(String[] args) throws Exception {
        int maxStages = 8;
        int items = 100000;

        if (args.length > 0)
            maxStages = Integer.parseInt(args[0]);

        print = false;
        System.out.println("Warmup...");
        oneRun(1, items);
        Thread.sleep(100);
        oneRun(1, items);
        Thread.sleep(100);
        print = true;

        for (int i = 1; i <= maxStages; i += (i+1) >>> 1) {
            oneRun(i, items);
        }
        pool.shutdown();
        if (! pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS))
            throw new Error();
   }

    static class Stage implements Callable<Integer> {
        final Queue<Integer> queue;
        final CyclicBarrier barrier;
        int items;
        Stage (Queue<Integer> q, CyclicBarrier b, int items) {
            queue = q;
            barrier = b;
            this.items = items;
        }

        public Integer call() {
            // Repeatedly take something from queue if possible,
            // transform it, and put back in.
            try {
                barrier.await();
                int l = 4321;
                int takes = 0;
                for (;;) {
                    Integer item = queue.poll();
                    if (item != null) {
                        ++takes;
                        l = LoopHelpers.compute2(item.intValue());
                    }
                    else if (takes != 0) {
                        totalItems.getAndAdd(-takes);
                        takes = 0;
                    }
                    else if (totalItems.get() <= 0)
                        break;
                    l = LoopHelpers.compute1(l);
                    if (items > 0) {
                        --items;
                        queue.offer(new Integer(l));
                    }
                    else if ( (l & (3 << 5)) == 0) // spinwait
                        Thread.sleep(1);
                }
                return new Integer(l);
            }
            catch (Exception ie) {
                ie.printStackTrace();
                throw new Error("Call loop failed");
            }
        }
    }

    static void oneRun(int n, int items) throws Exception {
        Queue<Integer> q = new ConcurrentLinkedQueue<Integer>();
        LoopHelpers.BarrierTimer timer = new LoopHelpers.BarrierTimer();
        CyclicBarrier barrier = new CyclicBarrier(n + 1, timer);
        totalItems = new AtomicInteger(n * items);
        ArrayList<Future<Integer>> results = new ArrayList<Future<Integer>>(n);
        for (int i = 0; i < n; ++i)
            results.add(pool.submit(new Stage(q, barrier, items)));

        if (print)
            System.out.print("Threads: " + n + "\t:");
        barrier.await();
        int total = 0;
        for (int i = 0; i < n; ++i) {
            Future<Integer> f = results.get(i);
            Integer r = f.get();
            total += r.intValue();
        }
        long endTime = System.nanoTime();
        long time = endTime - timer.startTime;
        if (print)
            System.out.println(LoopHelpers.rightJustify(time / (items * n)) + " ns per item");
        if (total == 0) // avoid overoptimization
            System.out.println("useless result: " + total);

    }
}
