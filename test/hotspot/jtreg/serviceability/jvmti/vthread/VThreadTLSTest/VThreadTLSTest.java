/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verifies JVMTI GetLocalStorage/SetLocalStorage
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires test.thread.factory == null
 * @run main/othervm/native -agentlib:VThreadTLSTest VThreadTLSTest
 */

/**
 * @test
 * @bug 8311556
 * @summary Verifies JVMTI GetLocalStorage/SetLocalStorage
 * @requires vm.continuations
 * @requires vm.jvmti
 * @requires test.thread.factory == null
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading VThreadTLSTest attach
 */

import com.sun.tools.attach.VirtualMachine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VThreadTLSTest {
    static final String AGENT_LIB = "VThreadTLSTest";
    static volatile boolean attached;
    static volatile boolean failed;

    static void log(String msg) { System.out.println(msg); }
    static native long getTLS();
    static native void setTLS(long value);

    static void test() {
        try {
            while (!attached) {
                // keep mounted
            }
            long threadId = Thread.currentThread().threadId();
            setTLS(threadId);
            long mountedValue = getTLS();

            if (mountedValue != threadId) {
                log("Error: wrong TLS value while mounted: " + threadId + ", " + mountedValue);
                failed = true;
                return;
            }
            for (int count = 0; count < 10; count++) {
                Thread.sleep(1);
                long tlsValue = getTLS();
                if (tlsValue != threadId) {
                    log("Error: wrong TLS value after yield: expected: " + threadId + " got: " + tlsValue);
                    failed = true;
                    return;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        try (ExecutorService execService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int threadCount = 0; threadCount < 20; threadCount++) {
                execService.execute(() -> test());
            }
            if (args.length == 1 && args[0].equals("attach")) {
                log("loading " + AGENT_LIB + " lib");
                VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
                vm.loadAgentLibrary(AGENT_LIB);
            }
            Thread.sleep(10);
            attached = true;
        }
        if (failed) {
            throw new RuntimeException("Test FAILED: errors encountered");
        } else {
            log("Test passed");
        }
    }
}

