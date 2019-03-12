/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.test;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;

import org.graalvm.compiler.test.SubprocessUtil;
import org.graalvm.compiler.test.SubprocessUtil.Subprocess;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class ReservedStackAccessTest extends HotSpotGraalCompilerTest {
    @Before
    public void check() {
        Assume.assumeTrue(runtime().getVMConfig().enableStackReservedZoneAddress != 0);
    }

    public void stackAccessTest() {
        Assume.assumeTrue(runtime().getVMConfig().enableStackReservedZoneAddress != 0);

        int passed = 0;
        for (int i = 0; i < 1000; i++) {
            // Each iteration has to be executed by a new thread. The test
            // relies on the random size area pushed by the VM at the beginning
            // of the stack of each Java thread it creates.
            RunWithSOEContext r = new RunWithSOEContext(new ReentrantLockTest(), 256);
            Thread thread = new Thread(r);
            thread.start();
            try {
                thread.join();
                assertTrue(r.result.equals("PASSED"), r.result);
                ++passed;
            } catch (InterruptedException ex) {
            }
        }
        System.out.println("RESULT: " + (passed == 1000 ? "PASSED" : "FAILED"));
    }

    public static void main(String[] args) {
        new ReservedStackAccessTest().stackAccessTest();
    }

    @Test
    public void run() throws IOException, InterruptedException {
        Assume.assumeTrue(runtime().getVMConfig().enableStackReservedZoneAddress != 0);
        List<String> vmArgs = SubprocessUtil.withoutDebuggerArguments(SubprocessUtil.getVMCommandLine());
        vmArgs.add("-XX:+UseJVMCICompiler");
        vmArgs.add("-Dgraal.Inline=false");
        vmArgs.add("-XX:CompileCommand=exclude,java/util/concurrent/locks/AbstractOwnableSynchronizer.setExclusiveOwnerThread");

        // Avoid SOE in HotSpotJVMCIRuntime.adjustCompilationLevel
        vmArgs.add("-Dgraal.CompileGraalWithC1Only=false");

        Subprocess proc = SubprocessUtil.java(vmArgs, ReservedStackAccessTest.class.getName());
        boolean passed = false;
        for (String line : proc.output) {
            if (line.equals("RESULT: PASSED")) {
                passed = true;
            }
        }
        if (!passed) {
            System.err.println(proc);
        }
        assertTrue(passed);
    }

    static class ReentrantLockTest {

        private ReentrantLock[] lockArray;
        // Frame sizes vary a lot between interpreted code and compiled code
        // so the lock array has to be big enough to cover all cases.
        // If test fails with message "Not conclusive test", try to increase
        // LOCK_ARRAY_SIZE value
        private static final int LOCK_ARRAY_SIZE = 8192;
        private boolean stackOverflowErrorReceived;
        StackOverflowError soe = null;
        int index = -1;

        public void initialize() {
            lockArray = new ReentrantLock[LOCK_ARRAY_SIZE];
            for (int i = 0; i < LOCK_ARRAY_SIZE; i++) {
                lockArray[i] = new ReentrantLock();
            }
            stackOverflowErrorReceived = false;
        }

        public String getResult() {
            if (!stackOverflowErrorReceived) {
                return "ERROR: Not conclusive test: no StackOverflowError received";
            }
            for (int i = 0; i < LOCK_ARRAY_SIZE; i++) {
                if (lockArray[i].isLocked()) {
                    if (!lockArray[i].isHeldByCurrentThread()) {
                        StringBuilder s = new StringBuilder();
                        s.append("FAILED: ReentrantLock ");
                        s.append(i);
                        s.append(" looks corrupted");
                        return s.toString();
                    }
                }
            }
            return "PASSED";
        }

        public void run() {
            try {
                lockAndCall(0);
            } catch (StackOverflowError e) {
                soe = e;
                stackOverflowErrorReceived = true;
            }
        }

        private void lockAndCall(int i) {
            index = i;
            if (i < LOCK_ARRAY_SIZE) {
                lockArray[i].lock();
                lockAndCall(i + 1);
            }
        }
    }

    static class RunWithSOEContext implements Runnable {

        int counter;
        int deframe;
        int decounter;
        int setupSOEFrame;
        int testStartFrame;
        ReentrantLockTest test;
        String result = "FAILED: no result";

        RunWithSOEContext(ReentrantLockTest test, int deframe) {
            this.test = test;
            this.deframe = deframe;
        }

        @Override
        public void run() {
            counter = 0;
            decounter = deframe;
            test.initialize();
            recursiveCall();
            System.out.println("Framework got StackOverflowError at frame = " + counter);
            System.out.println("Test started execution at frame = " + (counter - deframe));
            result = test.getResult();
        }

        @SuppressWarnings("unused")
        void recursiveCall() {
            // Unused local variables to increase the frame size
            long l1;
            long l2;
            long l3;
            long l4;
            long l5;
            long l6;
            long l7;
            long l8;
            long l9;
            long l10;
            long l11;
            long l12;
            long l13;
            long l14;
            long l15;
            long l16;
            long l17;
            long l18;
            long l19;
            long l20;
            long l21;
            long l22;
            long l23;
            long l24;
            long l25;
            long l26;
            long l27;
            long l28;
            long l30;
            long l31;
            long l32;
            long l33;
            long l34;
            long l35;
            long l36;
            long l37;
            counter++;
            try {
                recursiveCall();
            } catch (StackOverflowError e) {
            }
            decounter--;
            if (decounter == 0) {
                setupSOEFrame = counter;
                testStartFrame = counter - deframe;
                test.run();
            }
        }
    }

}
