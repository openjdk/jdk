/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies JVMTI RawMoitor functions works correctly on virtual threads
 * @requires vm.continuations
 * @compile --enable-preview -source ${jdk.version} RawMonitorTest.java
 * @run main/othervm/native --enable-preview -agentlib:RawMonitorTest RawMonitorTest
 */

import java.util.List;
import java.util.ArrayList;

public class RawMonitorTest {
    private static final String AGENT_LIB = "RawMonitorTest";

    native void rawMonitorEnter();
    native void rawMonitorExit();
    native void rawMonitorWait();
    native void rawMonitorNotifyAll();

    final Runnable parkingTask = () -> {
       for (int i = 0; i < 100; i++) {
            rawMonitorEnter();
            rawMonitorNotifyAll();
            // uncomment lines below to get failures with NOT_MONITOR_OWNER
            Thread.yield();
            rawMonitorWait();
            rawMonitorExit();
        }
    };

    void runTest() throws Exception {
        List<Thread> vthreads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            vthreads.add(Thread.ofVirtual().name("VT" + i).start(parkingTask));
        }
        for (Thread vthread: vthreads) {
            vthread.join();
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            System.loadLibrary(AGENT_LIB);
        } catch (UnsatisfiedLinkError ex) {
            System.err.println("Failed to load " + AGENT_LIB + " lib");
            System.err.println("java.library.path: " + System.getProperty("java.library.path"));
            throw ex;
        }
        RawMonitorTest t = new RawMonitorTest();
        t.runTest();
    }
}
