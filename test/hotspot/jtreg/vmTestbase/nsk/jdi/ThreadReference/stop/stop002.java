/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ThreadReference.stop;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;
import java.io.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

/**
 * The test checks that the JDI method:<br><code>com.sun.jdi.ThreadReference.stop()</code><br>
 * behaves properly in various situations. It consists of 5 subtests.
 *
 * TEST #1: Tests that stop() properly throws <i>InvalidTypeException</i> if
 * specified throwable is not an instance of java.lang.Throwable in the target VM.<p>
 *
 * TEST #2: Verify that stop() works when suspended at a breakpoint.
 *
 * TEST #3: Verify that stop() works when not suspended in a loop. For virtual threads
 * we expect an IncompatibleThreadStateException.
 *
 * TEST #4: Verify that stop() works when suspended in a loop.
 *
 * TEST #5: Verify that stop() works when suspended in Thread.sleep(). For virtual
 * threads we expect an OpaqueFrameException.
 */
public class stop002 {
    static final String DEBUGGEE_CLASS =
        "nsk.jdi.ThreadReference.stop.stop002t";

    // names of debuggee main thread
    static final String DEBUGGEE_THRNAME = "stop002tThr";

    // debuggee local var used to find needed non-throwable object
    static final String DEBUGGEE_NON_THROWABLE_VAR= "stop002tNonThrowable";
    // debuggee local var used to find needed throwable object
    static final String DEBUGGEE_THROWABLE_VAR = "stop002tThrowable";
    // debuggee fields used to indicate to exit infinite loops
    static final String DEBUGGEE_STOP_LOOP1_FIELD = "stopLooping1";
    static final String DEBUGGEE_STOP_LOOP2_FIELD = "stopLooping2";

    // debuggee source line where it should be stopped
    static final int DEBUGGEE_STOPATLINE = 90;

    static final int DELAY = 500; // in milliseconds

    static final String COMMAND_READY = "ready";
    static final String COMMAND_GO = "go";
    static final String COMMAND_QUIT = "quit";

    static final boolean vthreadMode = "Virtual".equals(System.getProperty("test.thread.factory"));

    private ArgumentHandler argHandler;
    private Log log;
    private IOPipe pipe;
    private Debugee debuggee;
    private VirtualMachine vm;
    private BreakpointRequest BPreq;
    private ReferenceType mainClass;
    private volatile int tot_res = Consts.TEST_PASSED;
    private volatile boolean gotEvent = false;

    public static void main (String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new stop002().runIt(argv, out);
    }

    private int runIt(String args[], PrintStream out) {
        argHandler = new ArgumentHandler(args);
        log = new Log(out, argHandler);
        Binder binder = new Binder(argHandler, log);

        debuggee = binder.bindToDebugee(DEBUGGEE_CLASS);
        pipe = debuggee.createIOPipe();
        vm = debuggee.VM();
        debuggee.redirectStderr(log, "stop002t.err> ");
        debuggee.resume();
        String cmd = pipe.readln();
        if (!cmd.equals(COMMAND_READY)) {
            log.complain("TEST BUG: unknown debuggee command: " + cmd);
            tot_res = Consts.TEST_FAILED;
            return quitDebuggee();
        }

        Field stopLoop1 = null;
        Field stopLoop2 = null;
        ObjectReference objRef = null;
        ObjectReference throwableRef = null;

        try {
            // debuggee main class
            mainClass = debuggee.classByName(DEBUGGEE_CLASS);

            ThreadReference thrRef = debuggee.threadByFieldName(mainClass, "testThread", DEBUGGEE_THRNAME);
            if (thrRef == null) {
                log.complain("TEST FAILURE: method Debugee.threadByFieldName() returned null for debuggee thread "
                             + DEBUGGEE_THRNAME);
                tot_res = Consts.TEST_FAILED;
                return quitDebuggee();
            }

            suspendAtBP(mainClass, DEBUGGEE_STOPATLINE);
            objRef = findObjRef(thrRef, DEBUGGEE_NON_THROWABLE_VAR);
            throwableRef = findObjRef(thrRef, DEBUGGEE_THROWABLE_VAR);

            // These fields are used to indicate that debuggee has to stop looping
            stopLoop1 = mainClass.fieldByName(DEBUGGEE_STOP_LOOP1_FIELD);
            stopLoop2 = mainClass.fieldByName(DEBUGGEE_STOP_LOOP2_FIELD);
            if (stopLoop1 == null || stopLoop2 == null) {
                throw new RuntimeException("Failed to find a \"stop loop\" field");
            }

            log.display("non-throwable object: \"" + objRef + "\"");
            log.display("throwable object:     \"" + throwableRef + "\"");
            log.display("debuggee thread:      \"" + thrRef + "\"");

            /*
             * Test #1: verify using a non-throwable object with stop() fails appropriately.
             */
            log.display("\nTEST #1: Trying to stop debuggee thread using non-throwable object.");
            try {
                thrRef.stop(objRef); // objRef is an instance of the debuggee class, not a Throwable
                log.complain("TEST #1 FAILED: expected InvalidTypeException was not thrown");
                tot_res = Consts.TEST_FAILED;
            } catch (InvalidTypeException ee) {
                log.display("TEST #1 PASSED: caught expected " + ee);
            } catch (Exception ue) {
                ue.printStackTrace();
                log.complain("TEST #1 FAILED: caught unexpected " + ue + "instead of InvalidTypeException");
                tot_res = Consts.TEST_FAILED;
            }
            log.display("TEST #1: all done.");

            /*
             * Test #2: verify that stop() works when suspended at a breakpoint.
             */
            log.display("\nTEST #2: Trying to stop debuggee thread while suspended at a breakpoint.");
            try {
                thrRef.stop(throwableRef);
                log.display("TEST #2 PASSED: stop() call succeeded.");
            } catch (Exception ue) {
                ue.printStackTrace();
                log.complain("TEST #2 FAILED: caught unexpected " + ue);
                tot_res = Consts.TEST_FAILED;
            }
            log.display("TEST #2: Resuming debuggee VM to allow async exception to be handled");
            vm.resume();
            log.display("TEST #2: all done.");

            /*
             * Test #3: verify that stop() works when not suspended in a loop. Expect
             * IllegalThreadStateException for virtual threads.
             */
            log.display("\nTEST #3: Trying to stop debuggee thread while not suspended in a loop.");
            waitForTestReady(3);
            try {
                thrRef.stop(throwableRef);
                if (vthreadMode) {
                    log.complain("TEST #3 FAILED: expected IllegalThreadStateException"
                                 + " was not thrown for virtual thread");
                    tot_res = Consts.TEST_FAILED;
                } else {
                    log.display("TEST #3 PASSED: stop() call succeeded.");
                }
            } catch (Exception ue) {
                if (vthreadMode && ue instanceof IllegalThreadStateException) {
                    log.display("TEST #3 PASSED: stop() call threw IllegalThreadStateException"
                                + " for virtual thread");
                } else {
                    ue.printStackTrace();
                    log.complain("TEST #3 FAILED: caught unexpected " + ue);
                    tot_res = Consts.TEST_FAILED;
                }
            } finally {
                // Force the debuggee out of the loop. Not really needed if the stop() call
                // successfully threw the async exception, but it's easier to just always do this.
                log.display("TEST #3: clearing loop flag.");
                objRef.setValue(stopLoop1, vm.mirrorOf(true));
            }
            log.display("TEST #3: all done.");

            /*
             * Test #4: verify that stop() works when suspended in a loop
             */
            log.display("\nTEST #4: Trying to stop debuggee thread while suspended in a loop.");
            waitForTestReady(4);
            try {
                thrRef.suspend();
                log.display("TEST #4: thread is suspended.");
                thrRef.stop(throwableRef);
                log.display("TEST #4 PASSED: stop() call succeeded.");
            } catch (Throwable ue) {
                ue.printStackTrace();
                log.complain("TEST #4 FAILED: caught unexpected " + ue);
                tot_res = Consts.TEST_FAILED;
            } finally {
                log.display("TEST #4: resuming thread.");
                thrRef.resume();
                // Force the debuggee out of the loop. Not really needed if the stop() call
                // successfully threw the async exception, but it's easier to just always do this.
                log.display("TEST #4: clearing loop flag.");
                objRef.setValue(stopLoop2, vm.mirrorOf(true));
            }
            log.display("TEST #4: all done.");

            /*
             * Test #5: verify that stop() works when suspended in Thread.sleep(). Expect
             * OpaqueFrameException for virtual threads.
             */
            log.display("\nTEST #5: Trying to stop debuggee thread while suspended in Thread.sleep().");
            waitForTestReady(5);
            // Allow debuggee to reach Thread.sleep() first.
            log.display("TEST #5: waiting for debuggee to sleep...");
            while (true) {
                int status = thrRef.status();
                if (status == ThreadReference.THREAD_STATUS_SLEEPING ||
                    status == ThreadReference.THREAD_STATUS_WAIT)
                {
                    break;
                }
                Thread.sleep(50);
            }
            log.display("TEST #5: debuggee is sleeping.");
            try {
                thrRef.suspend();
                log.display("TEST #5: thread is suspended.");
                thrRef.stop(throwableRef);
                if (vthreadMode) {
                    log.complain("TEST #5 FAILED: expected OpaqueFrameException was not thrown");
                    tot_res = Consts.TEST_FAILED;
                } else {
                    log.display("TEST #5 PASSED: stop() call for suspended thread succeeded");
                }
            } catch (Throwable ue) {
                if (vthreadMode && ue instanceof OpaqueFrameException) {
                    log.display("TEST #5 PASSED: stop() call threw OpaqueFrameException for virtual thread");
                } else {
                    ue.printStackTrace();
                    log.complain("TEST #5 FAILED: caught unexpected " + ue);
                    tot_res = Consts.TEST_FAILED;
                }
            } finally {
                log.display("TEST #5: resuming thread.");
                thrRef.resume();
            }
            log.display("TEST #5: all done.");
        } catch (Exception e) {
            e.printStackTrace();
            log.complain("TEST FAILURE: caught unexpected exception: " + e);
            tot_res = Consts.TEST_FAILED;
        } finally {
            // Force the debuggee out of both loops
            if (objRef != null && stopLoop1 != null && stopLoop2 != null) {
                try {
                    objRef.setValue(stopLoop1, vm.mirrorOf(true));
                    objRef.setValue(stopLoop2, vm.mirrorOf(true));
                } catch (Exception sve) {
                    sve.printStackTrace();
                    tot_res = Consts.TEST_FAILED;
                }
            }
        }

        return quitDebuggee();
    }

    private void waitForTestReady(int testNum) {
        log.display("TEST #" + testNum + ": waiting for test ready...");
        IntegerValue ival;
        do {
            ival = (IntegerValue)mainClass.getValue(mainClass.fieldByName("testNumReady"));
        } while (ival.value() != testNum);
        log.display("TEST #" + testNum + ": test ready.");
    }

    private ObjectReference findObjRef(ThreadReference thrRef, String varName) {
        try {
            List frames = thrRef.frames();
            Iterator iter = frames.iterator();
            while (iter.hasNext()) {
                StackFrame stackFr = (StackFrame) iter.next();
                try {
                    LocalVariable locVar =
                        stackFr.visibleVariableByName(varName);
                    // visible variable with the given name is found
                    if (locVar != null) {
                        return (ObjectReference)
                            stackFr.getValue(locVar);
                    }
                } catch (AbsentInformationException e) {
                    // this is not needed stack frame, ignoring
                } catch (NativeMethodException ne) {
                    // current method is native, also ignoring
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            tot_res = Consts.TEST_FAILED;
            throw new Failure("findObjRef: caught unexpected exception: " + e);
        }
        throw new Failure("findObjRef: needed debuggee stack frame not found");
    }

    private BreakpointRequest setBP(ReferenceType refType, int bpLine) {
        EventRequestManager evReqMan =
            debuggee.getEventRequestManager();
        Location loc;

        try {
            List locations = refType.allLineLocations();
            Iterator iter = locations.iterator();
            while (iter.hasNext()) {
                loc = (Location) iter.next();
                if (loc.lineNumber() == bpLine) {
                    BreakpointRequest BPreq =
                        evReqMan.createBreakpointRequest(loc);
                    log.display("created " + BPreq + "\n\tfor " + refType
                        + " ; line=" + bpLine);
                    return BPreq;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("setBP: caught unexpected exception: " + e);
        }
        throw new Failure("setBP: location corresponding debuggee source line "
            + bpLine + " not found");
    }

    private void suspendAtBP(ReferenceType rType, int bpLine) {

        /**
         * This is a class containing a critical section which may lead to time
         * out of the test.
         */
        class CriticalSection extends Thread {
            public volatile boolean waitFor = true;

            public void run() {
                try {
                    do {
                        EventSet eventSet = vm.eventQueue().remove(DELAY);
                        if (eventSet != null) { // it is not a timeout
                            EventIterator it = eventSet.eventIterator();
                            while (it.hasNext()) {
                                Event event = it.nextEvent();
                                if (event instanceof VMDisconnectEvent) {
                                    log.complain("TEST FAILED: unexpected VMDisconnectEvent");
                                    break;
                                } else if (event instanceof VMDeathEvent) {
                                    log.complain("TEST FAILED: unexpected VMDeathEvent");
                                    break;
                                } else if (event instanceof BreakpointEvent) {
                                    if (event.request().equals(BPreq)) {
                                        log.display("expected Breakpoint event occured: "
                                            + event.toString());
                                        gotEvent = true;
                                        return;
                                    }
                                } else
                                    log.display("following JDI event occured: "
                                        + event.toString());
                            }
                        }
                    } while(waitFor);
                    log.complain("TEST FAILED: no expected Breakpoint event");
                    tot_res = Consts.TEST_FAILED;
                } catch (Exception e) {
                    e.printStackTrace();
                    tot_res = Consts.TEST_FAILED;
                    log.complain("TEST FAILED: caught unexpected exception: " + e);
                }
            }
        }
/////////////////////////////////////////////////////////////////////////////

        BPreq = setBP(rType, bpLine);
        BPreq.enable();
        CriticalSection critSect = new CriticalSection();
        log.display("\nStarting potential timed out section:\n\twaiting "
            + (argHandler.getWaitTime())
            + " minute(s) for JDI Breakpoint event ...\n");
        critSect.start();
        pipe.println(COMMAND_GO);
        try {
            critSect.join((argHandler.getWaitTime())*60000);
            if (critSect.isAlive()) {
                critSect.waitFor = false;
                throw new Failure("timeout occured while waiting for Breakpoint event");
            }
        } catch (InterruptedException e) {
            critSect.waitFor = false;
            throw new Failure("TEST INCOMPLETE: InterruptedException occured while waiting for Breakpoint event");
        } finally {
            BPreq.disable();
        }
        log.display("\nPotential timed out section successfully passed\n");
        if (gotEvent == false)
            throw new Failure("unable to suspend debuggee thread at breakpoint");
    }

    private int quitDebuggee() {
        log.display("Final resumption of debuggee VM");
        vm.resume();
        pipe.println(COMMAND_QUIT);
        debuggee.waitFor();
        int debStat = debuggee.getStatus();
        if (debStat != (Consts.JCK_STATUS_BASE + Consts.TEST_PASSED)) {
            log.complain("TEST FAILED: debuggee process finished with status: "
                + debStat);
            tot_res = Consts.TEST_FAILED;
        } else
            log.display("\nDebuggee process finished with the status: "
                + debStat);

        return tot_res;
    }
}
