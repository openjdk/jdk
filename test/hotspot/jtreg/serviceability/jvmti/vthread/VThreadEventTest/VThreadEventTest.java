/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8312174
 * @summary missing JVMTI events from vthreads parked during JVMTI attach
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires vm.compMode != "Xcomp"
 * @modules java.base/java.lang:+open
 * @library /test/lib
 * @run main/othervm/native
 *     -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading VThreadEventTest attach
 */

import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import java.util.ArrayList;
import jdk.test.lib.thread.VThreadRunner;

public class VThreadEventTest {
    static final int PARKED_THREAD_COUNT = 4;
    static final int SPINNING_THREAD_COUNT = 4;

    private static void log(String msg) { System.out.println(msg); }

    private static native int threadEndCount();
    private static native int threadMountCount();
    private static native int threadUnmountCount();

    private static volatile boolean attached;

    // called by agent when it is initialized and has enabled events
    static void agentStarted() {
        attached = true;
    }

    public static void main(String[] args) throws Exception {
        if (Thread.currentThread().isVirtual()) {
            System.out.println("Skipping test as current thread is a virtual thread");
            return;
        }
        VThreadRunner.ensureParallelism(SPINNING_THREAD_COUNT+1);

        // start threads that park (unmount)
        var threads1 = new ArrayList<Thread>();
        for (int i = 0; i < PARKED_THREAD_COUNT; i++) {
            var started = new AtomicBoolean();
            var thread = Thread.startVirtualThread(() -> {
                started.set(true);
                LockSupport.park();
            });

            // wait for thread to start execution + park
            while (!started.get()) {
                Thread.sleep(10);
            }
            await(thread, Thread.State.WAITING);
            threads1.add(thread);
        }

        // start threads that spin (stay mounted)
        var threads2 = new ArrayList<Thread>();
        for (int i = 0; i < SPINNING_THREAD_COUNT; i++) {
            var started = new AtomicBoolean();
            var thread = Thread.startVirtualThread(() -> {
                started.set(true);
                while (!attached) {
                    Thread.onSpinWait();
                }
            });

            // wait for thread to start execution
            while (!started.get()) {
                Thread.sleep(10);
            }
            threads2.add(thread);
        }

        // attach to the current VM
        VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
        vm.loadAgentLibrary("VThreadEventTest");

        // wait for agent to start
        while (!attached) {
            Thread.sleep(10);
        }

        // unpark the threads that were parked
        for (Thread thread : threads1) {
            LockSupport.unpark(thread);
        }

        // wait for all threads to terminate
        for (Thread thread : threads1) {
            thread.join();
        }
        for (Thread thread : threads2) {
            thread.join();
        }

        int threadEndCnt = threadEndCount();
        int threadMountCnt = threadMountCount();
        int threadUnmountCnt = threadUnmountCount();

        int threadCount = PARKED_THREAD_COUNT + SPINNING_THREAD_COUNT;
        log("VirtualThreadEnd events: " + threadEndCnt + ", expected: " + threadCount);
        log("VirtualThreadMount events: " + threadMountCnt + ", expected: " + PARKED_THREAD_COUNT);
        log("VirtualThreadUnmount events: " + threadUnmountCnt + ", expected: " + threadCount);

        boolean failed = false;
        if (threadEndCnt != threadCount) {
            log("FAILED: unexpected count of VirtualThreadEnd events");
            failed = true;
        }
        if (threadMountCnt != PARKED_THREAD_COUNT) {
            log("FAILED: unexpected count of VirtualThreadMount events");
            failed = true;
        }
        if (threadUnmountCnt != threadCount) {
            log("FAILED: unexpected count of VirtualThreadUnmount events");
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("FAILED: event count is wrong");
        }
    }

    private static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            assert state != Thread.State.TERMINATED : "Thread has terminated";
            Thread.sleep(10);
            state = thread.getState();
        }
    }

}

