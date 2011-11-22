/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4176355
 * @summary Stopping a ThreadGroup that contains the current thread has
 *          unpredictable results.
 */

public class Stop implements Runnable {
    private static boolean groupStopped = false ;
    private static final Object lock = new Object();

    private static final ThreadGroup group = new ThreadGroup("");
    private static final Thread first = new Thread(group, new Stop());
    private static final Thread second = new Thread(group, new Stop());

    public void run() {
        while (true) {
            // Give the other thread a chance to start
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }

            // When the first thread runs, it will stop the group.
            if (Thread.currentThread() == first) {
                synchronized (lock) {
                    try {
                        group.stop();
                    } finally {
                        // Signal the main thread it is time to check
                        // that the stopped thread group was successful
                        groupStopped = true;
                        lock.notifyAll();
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Launch two threads as part of the same thread group
        first.start();
        second.start();

        // Wait for the thread group stop to be issued
        synchronized(lock){
            while (!groupStopped) {
                lock.wait();
                // Give the other thread a chance to stop
                Thread.sleep(1000);
            }
        }

        // Check that the second thread is terminated when the
        // first thread terminates the thread group.
        boolean failed = second.isAlive();

        // Clean up any threads that may have not been terminated
        first.stop();
        second.stop();
        if (failed)
            throw new RuntimeException("Failure.");
    }
}
