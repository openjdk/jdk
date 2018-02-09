/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8187143
 * @summary JDI crash in ~BufferBlob::MethodHandles adapters
 *
 * @run build TestScaffold VMConnection TargetListener TargetAdapter
 * @run compile -g NashornPopFrameTest.java
 * @run driver NashornPopFrameTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import javax.script.*;

import java.io.PrintStream;


// The debuggee, creates and uses a Nashorn engine to evaluate a simple script.

// The debugger  tries to set a breakpoint in Nashorn internal DEBUGGER method.
// When the breakpoint is reached, it looks for stack frame whose method's
// declaring type name starts with jdk.nashorn.internal.scripts.Script$.
// (nashorn dynamically generated classes)
// It then pops stack frames using the ThreadReference.popFrames() call, up to
// and including the above stackframe.
// The execution of the debuggee application is resumed after the needed
// frames have been popped.

class ScriptDebuggee {
    public final static int BKPT_LINE = 74;
    static ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
    static public String failReason = null;

    static void doit() throws Exception {
        System.out.println("Debugee: started!");
        String script =
                "function f() {\r\n" +
                        " debugger;\r\n" +
                        " debugger;\r\n" +
                        "}\r\n" +
                        "f();";
        try {
            engine.eval(script);
        } catch (Exception ex) {
            failReason = "ScriptDebuggee failed: Exception in engine.eval(): "
                    + ex.toString();
            ex.printStackTrace();
        }
        System.out.println("Debugee: finished!"); // BKPT_LINE
    }

    public static void main(String[] args) throws Exception {
        doit();
    }
}

/********** test program **********/

public class NashornPopFrameTest extends TestScaffold {
    static PrintStream out = System.out;
    static boolean breakpointReached = false;
    String debuggeeFailReason = null;
    ClassType targetClass;
    ThreadReference mainThread;
    BreakpointRequest bkptRequest;

    NashornPopFrameTest(String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        NashornPopFrameTest nashornPopFrameTest = new NashornPopFrameTest(args);
        nashornPopFrameTest.startTests();
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main() to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain("ScriptDebuggee");
        targetClass = (ClassType)bpe.location().declaringType();
        out.println("Agent: runTests: after startToMain()");

        mainThread = bpe.thread();
        EventRequestManager erm = vm().eventRequestManager();

        Location loc = findLocation(targetClass, ScriptDebuggee.BKPT_LINE);

        try {
            addListener(this);
        } catch (Exception ex){
            ex.printStackTrace();
            failure("Failed: Could not add listener");
            throw new Exception("NashornPopFrameTest: failed with Exception in AddListener");
        }

        pauseAtDebugger(vm());
        bkptRequest = erm.createBreakpointRequest(loc);
        bkptRequest.enable();

        vm().resume();

        try {
            listen(vm());
        } catch (Exception exp) {
            exp.printStackTrace();
            failure("Failed: Caught Exception while Listening");
            throw new Exception("NashornPopFrameTest: failed with Exception in listen()");
        }

        // Debugger continues to run until it receives a VMdisconnect event either because
        // the Debuggee crashed / got exception / finished successfully.
        while (!vmDisconnected) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ee) {
            }
        }

        removeListener(this);

        if (breakpointReached) {
            if (debuggeeFailReason != null) {
                failure(debuggeeFailReason);
            }
        } else {
            failure("Expected breakpoint in ScriptDebuggee:" +
                    ScriptDebuggee.BKPT_LINE + " was not reached");
        }
        if (testFailed) {
            throw new Exception("NashornPopFrameTest: failed");
        }
        out.println("NashornPopFrameTest: passed");
    }

    private static void pauseAtDebugger(VirtualMachine vm) throws AbsentInformationException {
        for (ReferenceType t : vm.allClasses()) pauseAtDebugger(t);
    }

    // Set a breakpoint in Nashorn internal DEBUGGER method.
    private static void pauseAtDebugger(ReferenceType t) throws AbsentInformationException {
        if (!t.name().endsWith(".ScriptRuntime")) {
            return;
        }
        for (Location l : t.allLineLocations()) {
            if (!l.method().name().equals("DEBUGGER")) continue;
            BreakpointRequest bkptReq = t.virtualMachine().eventRequestManager().createBreakpointRequest(l);
            out.println("Setting breakpoint for " + l);
            bkptReq.enable();
            break;
        }
    }

    private static void listen(VirtualMachine vm) throws Exception {
        EventQueue eventQueue = vm.eventQueue();
        EventSet es = eventQueue.remove();
        if (es != null) {
            handle(es);
        }
    }

    // Handle event when breakpoint is reached
    private static void handle(EventSet eventSet) throws Exception {
        out.println("Agent handle(): started");
        for (Event event : eventSet) {
            if (event instanceof BreakpointEvent) {
                findFrameAndPop(event);
            }
        }
        eventSet.resume();
        out.println("Agent handle(): finished");
    }

    private static void findFrameAndPop(Event event) throws Exception {
        ThreadReference thread = ((BreakpointEvent) event).thread();
        out.println("Agent: handling Breakpoint " + " at " +
                ((BreakpointEvent) event).location() +
                " in thread: " + thread);
        StackFrame sf = findScriptFrame(thread);
        if (sf != null) {
            out.println("Thread Pop Frame on StackFrame = " + sf);
            thread.popFrames(sf);
        }
    }

    // Find stack frame whose method's declaring type name starts with
    // jdk.nashorn.internal.scripts.Script$ and return that frame
    private static StackFrame findScriptFrame(ThreadReference t) throws IncompatibleThreadStateException {
        for (int i = 0; i < t.frameCount(); i++) {
            StackFrame sf = t.frame(i);
            String typeName = sf.location().method().declaringType().name();
            if (typeName.startsWith("jdk.nashorn.internal.scripts.Script$")) {
                out.println("Agent: in findScriptFrame: TypeName = " + typeName);
                return sf;
            }
        }
        throw new RuntimeException("no script frame");
    }

    static int bkptCount = 0;

    /********** event handlers **********/

    public void breakpointReached(BreakpointEvent event) {
        ThreadReference thread = ((BreakpointEvent) event).thread();
        String locStr = "" + ((BreakpointEvent) event).location();
        out.println("Agent: BreakpointEvent #" + (bkptCount++) +
                " at " + locStr + " in thread: " + thread);
        if (locStr.equals("ScriptDebuggee:" + ScriptDebuggee.BKPT_LINE)) {
            breakpointReached = true;
            Field failReasonField = targetClass.fieldByName("failReason");
            Value failReasonVal = targetClass.getValue(failReasonField);
            if (failReasonVal != null) {
                debuggeeFailReason = ((StringReference)failReasonVal).value();
            }
            bkptRequest.disable();
        }
    }

    public void eventSetComplete(EventSet set) {
        set.resume();
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        println("Agent: Got VMDisconnectEvent");
    }

}
