/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.apple.concurrent;

import java.util.List;
import java.util.concurrent.*;

class LibDispatchSerialQueue extends AbstractExecutorService {
        static final int RUNNING    = 0;
    static final int SHUTDOWN   = 1;
//  static final int STOP       = 2; // not supported by GCD
    static final int TERMINATED = 3;

    final Object lock = new Object();
        LibDispatchQueue nativeQueueWrapper;
    volatile int runState;

        LibDispatchSerialQueue(final long queuePtr) {
                nativeQueueWrapper = new LibDispatchQueue(queuePtr);
        }

        @Override
        public void execute(final Runnable task) {
                if (nativeQueueWrapper == null) return;
                LibDispatchNative.nativeExecuteAsync(nativeQueueWrapper.ptr, task);
        }

        @Override
        public boolean isShutdown() {
                return runState != RUNNING;
        }

        @Override
        public boolean isTerminated() {
                return runState == TERMINATED;
        }

        @Override
        public void shutdown() {
                synchronized (lock) {
                        if (runState != RUNNING) return;

                        runState = SHUTDOWN;
                        execute(new Runnable() {
                                public void run() {
                                        synchronized (lock) {
                                                runState = TERMINATED;
                                                lock.notifyAll(); // for the benefit of awaitTermination()
                                        }
                                }
                        });
                        nativeQueueWrapper = null;
                }
        }

        @Override
        public List<Runnable> shutdownNow() {
                shutdown();
                return null;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
                if (runState == TERMINATED) return true;

                final long millis = unit.toMillis(timeout);
                if (millis <= 0) return false;

                synchronized (lock) {
                        if (runState == TERMINATED) return true;
                        lock.wait(timeout);
                        if (runState == TERMINATED) return true;
                }

                return false;
        }
}
