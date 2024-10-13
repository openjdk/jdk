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

/**
 * @test id=default
 * @summary Do not suspend virtual threads in a critical section.
 * @bug 8311218
 * @requires vm.continuations
 * @library /testlibrary
 * @run main/othervm SuspendWithInterruptLock
 */

/**
 * @test id=xint
 * @summary Do not suspend virtual threads in a critical section.
 * @bug 8311218
 * @requires vm.continuations
 * @library /testlibrary
 * @run main/othervm -Xint SuspendWithInterruptLock
 */

import jvmti.JVMTIUtils;

public class SuspendWithInterruptLock {
    static volatile boolean done;

    public static void main(String[] args) throws Exception {
        Thread yielder = Thread.ofVirtual().name("yielder").start(() -> yielder());
        Thread stateReader = Thread.ofVirtual().name("stateReader").start(() -> stateReader(yielder));
        Thread suspender = new Thread(() -> suspender(stateReader));
        suspender.start();

        yielder.join();
        stateReader.join();
        suspender.join();
    }

    static private void yielder() {
        int iterations = 100_000;
        while (iterations-- > 0) {
            Thread.yield();
        }
        done = true;
    }

    static private void stateReader(Thread target) {
        while (!done) {
            target.getState();
        }
    }

    static private void suspender(Thread target) {
        while (!done) {
            suspendThread(target);
            sleep(1);
            resumeThread(target);
            // Allow progress
            sleep(5);
        }
    }

    static void suspendThread(Thread t) {
        try {
            JVMTIUtils.suspendThread(t);
        } catch (JVMTIUtils.JvmtiException e) {
            if (e.getCode() != JVMTIUtils.JVMTI_ERROR_THREAD_NOT_ALIVE) {
                throw e;
            }
        }
    }

    static void resumeThread(Thread t) {
        try {
            JVMTIUtils.resumeThread(t);
        } catch (JVMTIUtils.JvmtiException e) {
            if (e.getCode() != JVMTIUtils.JVMTI_ERROR_THREAD_NOT_ALIVE) {
                throw e;
            }
        }
    }

    static private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {}
    }
}

