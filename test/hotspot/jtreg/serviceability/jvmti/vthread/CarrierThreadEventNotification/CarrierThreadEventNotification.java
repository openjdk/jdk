/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311177
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:CarrierThreadEventNotification CarrierThreadEventNotification
 */

import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;

public class CarrierThreadEventNotification {
    static final int VTHREAD_COUNT = 64;
    static volatile boolean stopRunning = false;

    private static native void setSingleSteppingMode(boolean enable);

    final Runnable FOO = () -> {
        while(!stopRunning) {
            recurse(10);
        }
    };

    private void recurse(int depth) {
        if (depth > 0) {
            recurse(depth -1);
        } else {
            Thread.yield();
        }
    }

    private void runTest() throws Exception {
        List<Thread> virtualThreads = new ArrayList<>();
        for (int i = 0; i < VTHREAD_COUNT; i++) {
            virtualThreads.add(Thread.ofVirtual().start(FOO));
        }
        for (int cnt = 0; cnt < 500; cnt++) {
            setSingleSteppingMode(true);
            Thread.sleep(10);
            setSingleSteppingMode(false);
        }
        stopRunning = true;
        for (Thread t : virtualThreads) {
            t.join();
        }
    }

    public static void main(String[] args) throws Exception {
        CarrierThreadEventNotification obj = new CarrierThreadEventNotification();
        obj.runTest();
    }
}
