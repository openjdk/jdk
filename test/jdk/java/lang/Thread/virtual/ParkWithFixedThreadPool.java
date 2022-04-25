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
 * @modules java.base/java.lang:+open
 * @compile --enable-preview -source ${jdk.version} ParkWithFixedThreadPool.java
 * @run testng/othervm --enable-preview ParkWithFixedThreadPool
 */
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class ParkWithFixedThreadPool {
    @Test
    public static void multipleThreadPoolParkTest() throws Exception {
        try (ExecutorService scheduler = Executors.newFixedThreadPool(8)) {
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

            ThreadFactory factory = ThreadBuilders.virtualThreadBuilder(scheduler)
                    .name("vthread-", 0)
                    .factory();

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
}
