/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
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
 * @summary Test virtual thread park when scheduler is a fixed thread pool
 * @requires vm.continuations
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main ParkWithFixedThreadPool
 */

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.LockSupport;
import jdk.test.lib.thread.VThreadScheduler;

public class ParkWithFixedThreadPool {
    public static void main(String[] args) throws Exception {
        try (var scheduler = new Scheduler(8)) {
            int vthreadCount = 300;
            Thread[] vthreads = new Thread[vthreadCount];
            Runnable target = new Runnable() {
                public void run() {
                    int myIndex = -1;
                    for (int i = 0; i < vthreadCount; i++) {
                        if (vthreads[i] == Thread.currentThread()) {
                            myIndex = i;
                            break;
                        }
                    }

                    if (myIndex > 0) {
                        LockSupport.unpark(vthreads[myIndex - 1]);
                    }

                    if (myIndex != (vthreadCount - 1)) {
                        LockSupport.park();
                    }
                }
            };

            ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

            for (int i = 0; i < vthreadCount; i++) {
                vthreads[i] = factory.newThread(target);
            }
            for (int i = 0; i < vthreadCount; i++) {
                vthreads[i].start();
            }

            for (int i = 0; i < vthreadCount; i++) {
                vthreads[i].join();
            }
        }
    }

    static class Scheduler implements Executor, AutoCloseable {
        private final ExecutorService pool;

        Scheduler(int poolSize) {
            pool = Executors.newFixedThreadPool(poolSize);
        }

        @Override
        public void execute(Runnable task) {
            try {
                pool.execute(task);
            } finally {
                // ExecutorService::execute may consume parking permit
                LockSupport.unpark(Thread.currentThread());
            }
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
