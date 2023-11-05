/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CountDownLatch;

/**
 * Test native threads attaching to the VM with JNI AttachCurrentThread.
 */
public class ExplicitAttach {
    private static volatile CountDownLatch latch;

    public static void main(String[] args) throws Exception {
        int threadCount;
        if (args.length > 0) {
            threadCount = Integer.parseInt(args[0]);
        } else {
            threadCount = 2;
        }
        latch = new CountDownLatch(threadCount);

        // start the threads and wait for the threads to call home
        startThreads(threadCount);
        latch.await();
    }

    /**
     * Invoked by attached threads.
     */
    private static void callback() {
        System.out.println(Thread.currentThread());
        latch.countDown();
    }

    /**
     * Start n native threads that attach to the VM and invoke callback.
     */
    private static native void startThreads(int n);

    static {
        System.loadLibrary("ExplicitAttach");
    }
}
