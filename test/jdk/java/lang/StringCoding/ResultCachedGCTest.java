/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8258956
 * @summary Check if resultCached thread local is properly gc'ed.
 * @run main/othervm -Xmx256m -XX:SoftRefLRUPolicyMSPerMB=0 -verbose:gc ResultCachedGCTest
 */

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class ResultCachedGCTest {
    private static final int NUM_THREADS = 100;
    private static final int BA_LENGTH = 100_000_000;
    private static final byte[] ba = new byte[BA_LENGTH];
    private static final List<Worker> workers = new ArrayList<>((int)(NUM_THREADS / 0.75f) + 1);
    private static final ReentrantLock rl = new ReentrantLock();
    private static final CountDownLatch doneSignal = new CountDownLatch(NUM_THREADS);

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < NUM_THREADS; i++) {
            var w = new Worker();
            workers.add(w);
            var t = new Thread(w);
            t.setDaemon(true);
            t.start();
        }

        doneSignal.await();
    }

    static class Worker implements Runnable {
        @Override
        public void run() {
            rl.lock(); // one 'new String()' at a time.
            try {
                System.out.println(Thread.currentThread());
                new String(ba, 0, BA_LENGTH, StandardCharsets.UTF_8);
            } catch (OutOfMemoryError oome) {
                throw new RuntimeException("StringCoding.resultCached was not properly GC'ed.", oome);
            } finally {
                rl.unlock();
                doneSignal.countDown();
            }
            // keep it alive
            while (true) {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
