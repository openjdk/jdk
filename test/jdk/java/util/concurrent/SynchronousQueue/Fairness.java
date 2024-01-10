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
 * @modules java.base/java.util.concurrent:open
 * @bug 4992438 6633113
 * @summary Checks that fairness setting is respected.
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Fairness {
    private final static VarHandle underlyingTransferQueueAccess;

    static {
        try {
            underlyingTransferQueueAccess =
                MethodHandles.privateLookupIn(
                    SynchronousQueue.class,
                    MethodHandles.lookup()
                ).findVarHandle(
                    SynchronousQueue.class,
                    "transferer",
                    Class.forName(SynchronousQueue.class.getName() + "$Transferer")
            );
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }


    private static void testFairness(boolean fair, final SynchronousQueue<Integer> q)
        throws Throwable
    {
        final LinkedTransferQueue<Integer> underlying =
            (LinkedTransferQueue<Integer>)underlyingTransferQueueAccess.get(q);

        final ReentrantLock lock = new ReentrantLock();
        final Condition ready = lock.newCondition();
        final int threadCount = 10;
        final Throwable[] badness = new Throwable[1];
        lock.lock();
        for (int i = 0; i < threadCount; i++) {
            final Integer I = i;
            Thread t = new Thread() { public void run() {
                try {
                    lock.lock();
                    ready.signal();
                    lock.unlock();
                    q.put(I);
                } catch (Throwable t) { badness[0] = t; }}};
            t.start();
            ready.await();
            // Wait until previous put:ing thread is provably parked
            while (underlying.size() < (i + 1))
                Thread.yield();

            if (underlying.size() > (i + 1))
                throw new Error("Unexpected number of waiting producers: " + i);
        }
        for (int i = 0; i < threadCount; i++) {
            int j = q.take();
            // Non-fair queues are lifo in our implementation
            if (fair ? j != i : j != threadCount - 1 - i)
                throw new Error(String.format("fair=%b i=%d j=%d%n",
                                              fair, i, j));
        }
        if (badness[0] != null) throw new Error(badness[0]);
    }

    public static void main(String[] args) throws Throwable {
        testFairness(false, new SynchronousQueue<>());
        testFairness(false, new SynchronousQueue<>(false));
        testFairness(true,  new SynchronousQueue<>(true));
    }
}
