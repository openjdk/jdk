/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=default
 * @summary Verifies JVMTI can_support_virtual_threads works for agents loaded at startup and into running VM
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:VirtualThreadStartTest VirtualThreadStartTest
 * @run main/othervm/native -agentlib:VirtualThreadStartTest=can_support_virtual_threads VirtualThreadStartTest
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true VirtualThreadStartTest attach
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true VirtualThreadStartTest attach can_support_virtual_threads

/*
 * @test id=no-vmcontinuations
 * @requires vm.continuations
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:VirtualThreadStartTest -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadStartTest
 * @run main/othervm/native -agentlib:VirtualThreadStartTest=can_support_virtual_threads -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadStartTest
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadStartTest attach
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+UnlockExperimentalVMOptions -XX:-VMContinuations VirtualThreadStartTest attach can_support_virtual_threads
 */

import com.sun.tools.attach.VirtualMachine;

public class VirtualThreadStartTest {
    private static final String AGENT_LIB = "VirtualThreadStartTest";
    private static final int THREAD_CNT = 10;

    private static native int getAndResetStartedThreads();

    public static void main(String[] args) throws Exception {
        System.out.println("loading " + AGENT_LIB + " lib");

        if (args.length > 0 && args[0].equals("attach")) { // agent loaded into running VM case
            String arg = args.length == 2 ? args[1] : "";
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
            vm.loadAgentLibrary(AGENT_LIB, arg);
        } else {
            System.loadLibrary(AGENT_LIB);
        }
        getAndResetStartedThreads();

        for (int i = 0; i < THREAD_CNT; i++) {
            Thread.ofVirtual().name("Tested-VT-" + i).start(() -> {}).join();
        }

        int startedThreads = getAndResetStartedThreads();
        System.out.println("ThreadStart event count: " + startedThreads + ", expected: " + THREAD_CNT);
        if (startedThreads != THREAD_CNT) {
            throw new RuntimeException("Failed: wrong ThreadStart count: " +
                                       startedThreads + " expected: " + THREAD_CNT);
        }
    }
}
