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
 * @test
 * @summary Call forceEarlyReturn() on threads in various states not covered
 *          well by other tests. Most notably, this test includes a
 *          test case for a suspended but unmounted virtual thread.
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g ForceEarlyReturnTest.java
 * @run driver ForceEarlyReturnTest NATIVE
 * @run driver ForceEarlyReturnTest LOOP
 * @run driver ForceEarlyReturnTest SLEEP
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import java.util.*;

/*
 * There are three test modes covered by this test:
 *   NATIVE: the debuggee sits in a native method.
 *   SLEEP:  the debuggee blocks in Thread.sleep().
 *   LOOP:   the debuggee sits in a tight loop.
 *
 * In all cases the thread is suspended and errors such as IllegalArgumentException
 * and InvalidStackFrameException should not happen. The forceEarlyReturn() calls should
 * either pass, or produce OpaqueFrameException or NativeMethodException.
 *
 * Call stacks for each test mode (and expected result):
 *
 * NATIVE (NativeMethodException):
 *   nativeMethod()  <-- native method, which sleeps
 *   loopOrSleep()
 *   main()
 *
 * LOOP (no exception):
 *   loopOrSleep()  <-- tight loop
 *   main()
 *
 * SLEEP (NativeMethodException for platform thread or OpaqueFrameException
 * for virtual thread. See explanation in runTests().):
 *   Thread.sleep() + methods called by Thread.sleep()
 *   loopOrSleep()
 *   main()
 */

class ForceEarlyReturnTestTarg {
    static TestMode mode;

    static {
        System.loadLibrary("ForceEarlyReturnTestTarg");
    }

    public static void loopOrSleep() {
        switch (mode) {
        case TestMode.LOOP:
            while (true);
        case TestMode.SLEEP:
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            break;
        case TestMode.NATIVE:
            nativeMethod();
            break;
        }
    }

    public static native void nativeMethod(); // native method that does a very long sleep

    public static void main(String[] args) {
        System.out.println("    debuggee: Howdy!");

        // We expect just one argument, which is the test mode, such as SLEEP.
        if (args.length != 1) {
            throw new RuntimeException("Must pass 1 arguments to ForceEarlyReturnTestTarg");
        }
        System.out.println("    debuggee: args[0]: " + args[0]);
        mode = Enum.valueOf(TestMode.class, args[0]); // convert test mode string to an enum
        System.out.println("    debuggee: test mode: " + mode);

        loopOrSleep();

        System.out.println("    debuggee: Goodbye from ForceEarlyReturnTest!");
    }
}

/*
 * The different modes the test can be run in. See test description comment above.
 */
enum TestMode {
    NATIVE,
    SLEEP,
    LOOP;
}

/********** test program **********/

public class ForceEarlyReturnTest extends TestScaffold {
    private static TestMode mode;

    ForceEarlyReturnTest(String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        // We should get one argument that indicates the test mode, such as SLEEP.
        if (args.length != 1) {
            throw new RuntimeException("Must pass one argument to ForceEarlyReturnTestTarg");
        }
        mode = Enum.valueOf(TestMode.class, args[0]); // convert test mode string to an enum

        /*
         * The @run command looks something like:
         *   @run driver ForceEarlyReturnTest SLEEP
         * We need to pass SLEEP to the debuggee. We also need to insert
         * -Djava.library.path so the native method can be accessed if called.
         */
        String nativePath = "-Djava.library.path=" + System.getProperty("java.library.path");
        String[] newArgs = new String[2];
        newArgs[0] = nativePath;
        newArgs[1] = args[0]; // pass test mode, such as SLEEP_NONATIVE

        new ForceEarlyReturnTest(newArgs).startTests();
    }

    public void printStack(ThreadReference thread, String msg) throws Exception {
        System.out.println(msg);
        List<StackFrame> stack_frames = thread.frames();
        int i = 0;
        String sourceName;
        for (StackFrame f : stack_frames) {
            try {
                sourceName = f.location().sourceName();
            } catch (AbsentInformationException aie) {
                sourceName = "Unknown source";
            }
            System.out.println("frame[" + i++ +"]: " + f.location().method() +
                               " (bci:"+ f.location().codeIndex() + ")" +
                               " (" + sourceName + ":"+ f.location().lineNumber() + ")");
        }
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        BreakpointEvent bpe = startTo("ForceEarlyReturnTestTarg", "loopOrSleep", "()V");
        ThreadReference mainThread = bpe.thread();
        boolean is_vthread_mode = DebuggeeWrapper.isVirtual();

        // Resume main thread until it is in Thread.sleep() or the infinite loop.
        mainThread.resume();
        try {
            Thread.sleep(1000); // give thread chance to get into Thread.sleep() or loop
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        mainThread.suspend(); // Suspend thread while in Thread.sleep() or loop
        printStack(mainThread, "Debuggee stack before forceEarlyReturn():");

        /*
         * Figure out which exception forceEarlyReturn() should throw.
         */
        Class expected_exception;
        switch(mode) {
        case NATIVE:
            /*
             * There is a native frame on the top of the stack, so we expect NativeMethodException.
             */
            expected_exception = NativeMethodException.class;
            break;
        case LOOP:
            /*
             * There is a java frame on the top of the stack, so we expect no exception.
             */
            expected_exception = null;
            break;
        case SLEEP:
            /*
             * For platform threads, Thread.sleep() results in the Thread.sleep0() native
             * frame on the stack, so the end result is NativeMethodException. For virtual
             * threads it is not quite so simple. If the thead is pinned (such as when
             * there is already a native method on the stack), you end up in
             * VirtualThread.parkOnCarrierThread(), which calls Unsafe.park(), which is a
             * native method, so again this results in NativeMethodException. However, for
             * a virtual thread that is not pinned (which is true for this test case), you
             * end up with no native methods on the stack due to how Continuation.yield()
             * works. So you have an unmounted virtual thread with no native frames, which
             * results in OpaqueFrameException being thrown.
             */
            if (is_vthread_mode) {
                expected_exception = OpaqueFrameException.class;
            } else {
                expected_exception = NativeMethodException.class;
            }
            break;
        default:
            throw new RuntimeException("Bad test mode: " + mode);
        }

        /*
         * Call ThreadReference.forceEarlyReturn() and check for errors.
         */
        try {
            if (is_vthread_mode && mode == TestMode.SLEEP) {
                // For this test case with virtual threads, the topmost frame is for
                // Continuation.yield0(), which returns a boolean.
                BooleanValue theValue = vm().mirrorOf(true);
                mainThread.forceEarlyReturn(theValue);
            } else {
                // For all other cases, the topmost frame will be one that returns void.
                VoidValue theValue = vm().mirrorOfVoid();
                mainThread.forceEarlyReturn(theValue);
            }
            if (expected_exception != null) {
                failure("failure: forceEarlyReturn() did not get expected exception: " + expected_exception);
            } else {
                System.out.println("success: no exception for forceEarlyReturn()");
            }
        } catch (Exception ex) {
            if (expected_exception == ex.getClass()) {
                System.out.println("success: forceEarlyReturn() got expected exception: " + ex);
            } else {
                failure("failure: forceEarlyReturn() got unexpected exception: " + ex);
            }
        }

        /*
         * Most tests do a listenUntilVMDisconnect() here, but there is no real need for it
         * with this test, and doing so would require finding a way to get the debuggee
         * to exit the endless loop it might be in. When we return, TestScaffold will
         * call TestScaffold.shutdown(), causing the debuggee process to be terminated quickly.
         */

        if (testFailed) {
            throw new Exception("ForceEarlyReturnTest failed");
        }
        System.out.println("Passed:");
    }
}
