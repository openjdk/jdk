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

/**
 * @test
 * @summary Verifies JVMTI GetStackTrace does not truncate virtual thread stack trace with agent attach
 * @requires vm.jvmti
 * @requires vm.continuations
 * @enablePreview
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true VirtualStackTraceTest
 */

import com.sun.tools.attach.VirtualMachine;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VirtualStackTraceTest {
    private static final String AGENT_LIB = "VirtualStackTraceTest";

    public static native String[] getStackTrace();

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(String.valueOf(ProcessHandle.current().pid()));
        vm.loadAgentLibrary(AGENT_LIB);
        VirtualStackTraceTest t = new VirtualStackTraceTest();
        t.runTest();
    }

    void runTest() throws Exception {
        Thread thr = Thread.ofVirtual().name("VT").start(VirtualStackTraceTest::test);
        thr.join();
    }

    private static void test() {
        work();
    }

    private static void work() {
        inner();
    }

    private static void inner() {
        checkCurrentThread();
    }

    private static void checkCurrentThread() {
        System.out.println("Stack trace for " + Thread.currentThread() + ": ");
        var javaStackTrace = Arrays.stream(Thread.currentThread().getStackTrace()).map(StackTraceElement::getMethodName).toList();
        var jvmtiStackTrace = List.of(getStackTrace());

        System.out.println("JVMTI: " + jvmtiStackTrace);
        System.out.println("Java : " + javaStackTrace);

        if (!Objects.equals(jvmtiStackTrace, javaStackTrace)) {
            throw new RuntimeException("VirtualStackTraceTest failed: stack traces do not match");
        }
    }
}
