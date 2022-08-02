/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.jvm;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class LatchedThread implements Runnable {
    public final static ThreadGroup THREAD_GROUP = new ThreadGroup("Latched Threads");
    public final Thread thread;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicBoolean alive = new AtomicBoolean(true);

    public LatchedThread(String name, boolean isVirtual) {
        if (isVirtual) {
            thread = Thread.ofVirtual().name(name).unstarted(this);
        } else {
            thread = Thread.ofPlatform().name(name).group(THREAD_GROUP).unstarted(this);
        }
    }

    public LatchedThread(String name) {
        this(name, false);
    }

    public void start() {
        thread.start();
    }

    public void join() throws InterruptedException {
        thread.join();
    }

    public void awaitStarted() throws InterruptedException {
        latch.await();
    }

    public long getId() {
        return thread.getId();
    }

    public String getName() {
        return thread.getName();
    }

    public Thread getThread() {
        return thread;
    }

    public void stopAndJoin() throws InterruptedException {
        alive.set(false);
        synchronized (alive) {
            alive.notify();
        }
        join();
    }

    public void run() {
        latch.countDown();
        while (alive.get()) {
            try {
                synchronized (alive) {
                    alive.wait(10);
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

}
