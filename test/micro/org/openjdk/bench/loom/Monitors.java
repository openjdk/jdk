/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.loom;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

//@Fork(2)
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 3, time = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@SuppressWarnings("preview")
public class Monitors {
    static final ContinuationScope SCOPE = new ContinuationScope() { };

    static class RunSynchronized implements Runnable {
        private final int stackDepth;
        private final Object[] syncLockArray;

        private RunSynchronized(int stackDepth, ReentrantLock[] lockArray) {
            this.stackDepth = stackDepth;
            this.syncLockArray = lockArray;
        }

        public void recursive(int depth) {
            if (syncLockArray[depth] != null) {
                synchronized (syncLockArray[depth]) {
                    if (depth > 0) {
                        recursive(depth - 1);
                    } else {
                        Continuation.yield(SCOPE);
                    }
                }
            } else {
                if (depth > 0) {
                    recursive(depth - 1);
                } else {
                    Continuation.yield(SCOPE);
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                recursive(stackDepth);
            }
        }
    }

    static class RunReentrantLock implements Runnable {
        private final int stackDepth;
        private final ReentrantLock[] lockArray;

        private RunReentrantLock(int stackDepth, ReentrantLock[] lockArray) {
            this.stackDepth = stackDepth;
            this.lockArray = lockArray;
        }

        public void recursive(int depth) {
            if (lockArray[depth] != null) {
                lockArray[depth].lock();
                if (depth > 0) {
                    recursive(depth - 1);
                } else {
                    Continuation.yield(SCOPE);
                }
                lockArray[depth].unlock();
            } else {
                if (depth > 0) {
                    recursive(depth - 1);
                } else {
                    Continuation.yield(SCOPE);
                }
            }
        }

        @Override
        public void run() {
            while (true) {
                recursive(stackDepth);
            }
        }
    }

    static void inflateAllLocks(ReentrantLock[] lockArray) {
        for (ReentrantLock lock : lockArray) {
            if (lock != null) {
                synchronized (lock) {
                    try {
                        lock.wait(1);
                    } catch (Exception e) {}
                }
            }
        }
    }

    static ReentrantLock[] createLockArray(int stackDepth, int monitorCount) {
        ReentrantLock[] lockArray = new ReentrantLock[stackDepth + 1];

        // Always a lock on the bottom most frame
        lockArray[stackDepth] = new ReentrantLock();
        // Limit extra monitors to no more than one per recursive frame.
        int remainingMonitors = Math.min(Math.max(0, monitorCount - 1), stackDepth);

        if (remainingMonitors > 0) {
            // Calculate which other frames will use a lock.
            int stride = Math.max(2, (stackDepth / monitorCount));
            int frameIndex = (stackDepth - stride) % (stackDepth + 1);
            while (remainingMonitors > 0) {
                if (lockArray[frameIndex] == null) {
                    lockArray[frameIndex] = new ReentrantLock();
                    frameIndex = (frameIndex - stride) % (stackDepth + 1);
                    remainingMonitors--;
                } else {
                    frameIndex = (frameIndex - 1) % (stackDepth + 1);
                }
            }
        }
        System.out.println("Created lock array. synchronized/ReentrantLock will be used at following indexes:");
        for (int i = stackDepth; i >= 0; i--) {
            System.out.println("[" + i + "] : " + (lockArray[i] != null ? "synchronized/ReentrantLock" : "-"));
        }
        return lockArray;
    }

    static Continuation createContinuation(int stackDepth, ReentrantLock[] lockArray, boolean useSyncronized) {
        Runnable task = useSyncronized ? new RunSynchronized(stackDepth, lockArray)
                                       : new RunReentrantLock(stackDepth, lockArray);
        //inflateAllLocks(lockArray);
        return new Continuation(SCOPE, task);
    }

    @State(Scope.Thread)
    public static class MyState {
        @Param({"0", "5", "10", "20"})
        public int stackDepth;

        @Param({"1", "2", "3"})
        public int monitorCount;

        public Continuation syncCont;
        public Continuation reentCont;

        @Setup(Level.Trial)
        public void doSetup() {
            ReentrantLock[] lockArray = createLockArray(stackDepth, monitorCount);
            // We don't care which object we synchronize on so just pass
            // the same ReentrantLock array to the synchronized task.
            syncCont = createContinuation(stackDepth, lockArray, true /* useSyncronized */);
            reentCont = createContinuation(stackDepth, lockArray, false /* useSyncronized */);
            System.out.println("Created continuation used for testing with stackDepth: " + stackDepth + " and monitorCount: " + monitorCount);
        }
    }

    @Benchmark
    public void syncLock(MyState state) {
        state.syncCont.run();
    }

    @Benchmark
    public void reentrantLock(MyState state) {
        state.reentCont.run();
    }
}
