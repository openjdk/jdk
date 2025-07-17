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
 * @summary Call popFrames() on threads in various states not covered
 *          well by other tests. Most notably, this test includes
 *          test cases for a suspended but unmounted virtual thread.
 *          It is mostly for testing for OpaqueFrameException and
 *          NativeMethodException.
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g PopFramesTest.java
 * @run driver PopFramesTest SLEEP_NATIVE
 * @run driver PopFramesTest LOOP_NATIVE
 * @run driver PopFramesTest SLEEP_PRENATIVE
 * @run driver PopFramesTest LOOP_PRENATIVE
 * @run driver PopFramesTest SLEEP_NONATIVE
 * @run driver PopFramesTest LOOP_NONATIVE
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import java.util.*;

/*
 * There are six test modes covered by this test:
 *   SLEEP_NATIVE
 *   LOOP_NATIVE
 *   SLEEP_PRENATIVE
 *   LOOP_PRENATIVE
 *   SLEEP_NONATIVE
 *   LOOP_NONATIVE
 *
 * SLEEP:     the debuggee blocks in Thread.sleep().
 * LOOP:      the debuggee sits in a tight loop.
 * NATIVE:    there is a native frame within the set of frames to pop.
 * PRENATIVE: there is a native frame before the set of frames to pop.
 * NONATIVE:  there is no native frame (purposefully) present in the stack.
 *
 * In all cases the thread is suspended and errors such as IllegalArgumentException
 * and InvalidStackFrameException should not happen. The popFrames() calls should
 * either pass, or produce OpaqueFrameException or NativeMethodException.
 *
 * Call stacks for each test mode (and expected result):
 *  - Note in all cases the popMethod() frame is the frame passed to popFrames().
 *  - Note that Thread.sleep() usually results in the native Thread.sleep0() frame
 *    being at the top of the stack. However, for a mounted virtual thread that is
 *    not pinned, it does not result in any native frames due to how the VM
 *    parks non-pinned virtual threads.
 *
 * SLEEP_NATIVE (NativeMethodException):
 *   Thread.sleep() + methods called by Thread.sleep()
 *   loopOrSleep()
 *   upcallMethod()
 *   doUpcall()  <-- native method
 *   popMethod()
 *   main()
 *
 * LOOP_NATIVE (NativeMethodException):
 *   loopOrSleep()  <-- tight loop
 *   upcallMethod()
 *   doUpcall()  <-- native method
 *   popMethod()
 *   main()
 *
 * SLEEP_PRENATIVE (NativeMethodException due to Thread.sleep() blocking in a native method):
 *   Thread.sleep() + methods called by Thread.sleep()
 *   loopOrSleep()
 *   popMethod()
 *   upcallMethod()
 *   doUpcall()  <-- native method
 *   main()
 *
 * LOOP_PRENATIVE (no exception):
 *   loopOrSleep()  <-- tight loop
 *   popMethod()
 *   upcallMethod()
 *   doUpcall()  <-- native method
 *   main()
 *
 * SLEEP_NONATIVE (NativeMethodException for platform thread or OpaqueFrameException
 * for virtual thread. See explanation in runTests().):
 *   Thread.sleep() + methods called by Thread.sleep()
 *   loopOrSleep()
 *   popMethod()
 *   main()
 *
 * LOOP_NONATIVE (no exception):
 *   loopOrSleep()  <-- tight loop
 *   popMethod()
 *   main()
 */

class PopFramesTestTarg {
    static TestMode mode;

    static {
        System.loadLibrary("PopFramesTestTarg");
    }

    /*
     * This is the method whose frame (and all those after it) will be popped.
     */
    public static void popMethod() {
        System.out.println("    debuggee: in popMethod");
        if (mode.isCallNative()) {
            doUpcall();
        } else {
            loopOrSleep();
        }
    }

    public static void loopOrSleep() {
        if (mode.isDoLoop()) {
            while (true);
        } else {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static native void doUpcall(); // native method that will call upcallMethod()

    public static void upcallMethod() {
        if (mode.isCallPrenative()) {
            popMethod();
        } else {
            loopOrSleep();
        }
    }

    public static void main(String[] args) {
        System.out.println("    debuggee: Howdy!");

        // We expect just one argument, which is the test mode, such as SLEEP_NONATIVE.
        if (args.length != 1) {
            throw new RuntimeException("Must pass 1 arguments to PopFramesTestTarg");
        }
        System.out.println("    debuggee: args[0]: " + args[0]);
        mode = Enum.valueOf(TestMode.class, args[0]); // convert test mode string to an enum
        System.out.println("    debuggee: test mode: " + mode);

        if (mode.isCallNative()) {
            popMethod(); // call popMethod() directly, and it will call out to native
        } else if (mode.isCallPrenative()) {
            doUpcall();  // call native method that will call back into java to call popMethod()
        } else {
            popMethod(); // call popMethod() directly
        }

        System.out.println("    debuggee: Goodbye from PopFramesTest!");
    }
}

/*
 * The different modes the test can be run in. See test description comment above.
 */
enum TestMode {
    SLEEP_NATIVE,
    LOOP_NATIVE,
    SLEEP_PRENATIVE,
    LOOP_PRENATIVE,
    SLEEP_NONATIVE,
    LOOP_NONATIVE;

    // Returns true if debuggee should block in an infinite loop. Otherwise it calls Thread.sleep().
    boolean isDoLoop() {
        return this == LOOP_NATIVE || this == LOOP_PRENATIVE || this == LOOP_NONATIVE;
    }

    // Returns true if debuggee should introduce a native frame within the set of frames to pop.
    boolean isCallNative() {
        return this == LOOP_NATIVE || this == SLEEP_NATIVE;
    }

    // Returns true if debuggee should introduce a native frame before the set of frames to pop.
    // The purpose is to cause the virtual thread to be pinned.
    boolean isCallPrenative() {
        return this == LOOP_PRENATIVE || this == SLEEP_PRENATIVE;
    }
}

/********** test program **********/

public class PopFramesTest extends TestScaffold {
    private static TestMode mode;

    PopFramesTest(String args[]) {
        super(args);
    }

    public static void main(String[] args) throws Exception {
        // We should get one argument that indicates the test mode, such as SLEEP_NONATIVE.
        if (args.length != 1) {
            throw new RuntimeException("Must pass one argument to PopFramesTestTarg");
        }
        mode = Enum.valueOf(TestMode.class, args[0]); // convert test mode string to an enum

        /*
         * The @run command looks something like:
         *   @run driver PopFramesTest SLEEP_NONATIVE
         * We need to pass SLEEP_NONATIVE to the debuggee. We also need to insert
         * -Djava.library.path so the native method can be accessed if called.
         */
        String nativePath = "-Djava.library.path=" + System.getProperty("java.library.path");
        String[] newArgs = new String[2];
        newArgs[0] = nativePath;
        newArgs[1] = args[0]; // pass test mode, such as SLEEP_NONATIVE

        new PopFramesTest(newArgs).startTests();
    }

    StackFrame frameFor(ThreadReference thread, String methodName) throws Exception {
        Iterator it = thread.frames().iterator();

        while (it.hasNext()) {
            StackFrame frame = (StackFrame)it.next();
            if (frame.location().method().name().equals(methodName)) {
                return frame;
            }
        }
        failure("FAIL: " + methodName + " not on stack");
        return null;
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
        BreakpointEvent bpe = startTo("PopFramesTestTarg", "loopOrSleep", "()V");
        ThreadReference mainThread = bpe.thread();

        // Resume main thread until it is in Thread.sleep() or the infinite loop.
        mainThread.resume();
        try {
            Thread.sleep(1000); // give thread chance to get into Thread.sleep() or loop
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        mainThread.suspend(); // Suspend thread while in Thread.sleep() or loop
        printStack(mainThread, "Debuggee stack before popFrames():");

        /*
         * Figure out which exception popFrames() should throw.
         */
        Class expected_exception;
        switch(mode) {
        case SLEEP_NATIVE:
        case LOOP_NATIVE:
        case SLEEP_PRENATIVE:
            /*
             * For the two NATIVE cases, there is a native frame within the set of frames
             * to pop. For the SLEEP_PRENATIVE case, there also ends up being a native
             * frame. It will either be Thread.sleep0() for platform threads or
             * Unsafe.park() for virtual threads. See the SLEEP_NATIVE comment below
             * for more details.
             */
            expected_exception = NativeMethodException.class;
            break;
        case LOOP_PRENATIVE:
        case LOOP_NONATIVE:
            /*
             * For these two test cases, there are no native frames within the set of
             * frames to pop, nor in the frame previous to the frame to pop, so no
             * exception is expected.
             */
            expected_exception = null;
            break;
        case SLEEP_NONATIVE:
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
            if (DebuggeeWrapper.isVirtual()) {
                expected_exception = OpaqueFrameException.class;
            } else {
                expected_exception = NativeMethodException.class;
            }
            break;
        default:
            throw new RuntimeException("Bad test mode: " + mode);
        }

        /*
         * Pop all the frames up to and including the popMethod() frame.
         */
        try {
            mainThread.popFrames(frameFor(mainThread, "popMethod"));
            if (expected_exception != null) {
                failure("failure: popFrames() did not get expected exception: " + expected_exception);
            }
        } catch (Exception ex) {
            if (expected_exception == ex.getClass()) {
                System.out.println("success: popFrames() got expected exception: " + ex);
            } else {
                failure("failure: popFrames() got unexpected exception: " + ex);
            }
        }

        printStack(mainThread, "Debuggee stack after popFrames():");

        /*
         * Most tests do a listenUntilVMDisconnect() here, but there is no real need for it
         * with this test, and doing so would require finding a way to get the debuggee
         * to exit the endless loop it might be in. When we return, TestScaffold will
         * call TestScaffold.shutdown(), causing the debuggee process to be terminated quickly.
         */

        if (testFailed) {
            throw new Exception("PopFramesTest failed");
        }
        System.out.println("Passed:");
    }
}
