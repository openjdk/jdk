/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.channels.AsynchronousChannelGroup;
import java.util.concurrent.*;

/**
 * Test that arbitrary tasks can be submitted to a channel group's thread pool.
 */

public class AsExecutor {

    public static void main(String[] args) throws Exception {
        // create channel groups
        ThreadFactory factory = new PrivilegedThreadFactory();
        AsynchronousChannelGroup group1 = AsynchronousChannelGroup
            .withFixedThreadPool(5, factory);
        AsynchronousChannelGroup group2 = AsynchronousChannelGroup
            .withCachedThreadPool(Executors.newCachedThreadPool(factory), 0);

        try {
            // execute simple tasks
            testSimpleTask(group1);
            testSimpleTask(group2);

            // install security manager and test again
            System.setSecurityManager( new SecurityManager() );
            testSimpleTask(group1);
            testSimpleTask(group2);

            // attempt to execute tasks that run with only frames from boot
            // class loader on the stack.
            testAttackingTask(group1);
            testAttackingTask(group2);
        } finally {
            group1.shutdown();
            group2.shutdown();
        }
    }

    static void testSimpleTask(AsynchronousChannelGroup group) throws Exception {
        Executor executor = (Executor)group;
        final CountDownLatch latch = new CountDownLatch(1);
        executor.execute(new Runnable() {
            public void run() {
                latch.countDown();
            }
        });
        latch.await();
    }

    static void testAttackingTask(AsynchronousChannelGroup group) throws Exception {
        Executor executor = (Executor)group;
        Attack task = new Attack();
        executor.execute(task);
        task.waitUntilDone();
        if (!task.failedDueToSecurityException())
            throw new RuntimeException("SecurityException expected");
    }

}
