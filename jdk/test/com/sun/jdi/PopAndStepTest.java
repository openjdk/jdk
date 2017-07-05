/* /nodynamiccopyright/ */  // DO NOT DELETE ANY LINES!!!!
//    THIS TEST IS LINE NUMBER SENSITIVE
/**
 *  @test
 *  @bug 4530424
 *  @summary Hin says that doing a step over after a popframe acts like a resume.
 *
 *  @author jjh
 *
 *  @library ..
 *  @modules jdk.jdi
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run compile -g PopAndStepTest.java
 *  @run driver PopAndStepTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

    /********** LINE NUMBER SENSITIVE! *****************************************************************/

class PopAndStepTarg {
    public void B() {
        System.out.println("debuggee: in B");
        System.out.println("debuggee: in B, back to A");   // add line breakpoint here line 27 !!!
    }

    public void A() {
        System.out.println("debuggee: in A, about to call B");  // line 31
        B();
        System.out.println("debuggee: in A, back from B");      // line 33
        throw new RuntimeException("debuggee: Got to line 34");
    }

    public static void main(String[] args) {
        System.out.println("debuggee: Howdy!");      // line 38
        PopAndStepTarg xxx = new PopAndStepTarg();   // line 40
        xxx.A();                                     // line 41
        System.out.println("debugee: Goodbye from PopAndStepTarg!");
    }
}


    /********** test program **********/

public class PopAndStepTest extends TestScaffold {
    ReferenceType targetClass;
    ThreadReference mainThread;

    PopAndStepTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new PopAndStepTest(args).startTests();
    }


    StackFrame frameFor(String methodName) throws Exception {
        Iterator it = mainThread.frames().iterator();

        while (it.hasNext()) {
            StackFrame frame = (StackFrame)it.next();
            if (frame.location().method().name().equals(methodName)) {
                return frame;
            }
        }
        failure("FAIL: " + methodName + " not on stack");
        return null;
    }

    int getDebuggeeLineNum(int expectedLine) throws Exception {
        List allFrames = mainThread.frames();
        if ( allFrames == null) {
            return -1;
        }
        Iterator it = allFrames.iterator();
        StackFrame frame = (StackFrame)it.next();
        Location loc = frame.location();
        int theLine = loc.lineNumber();
        if (expectedLine != theLine) {
            failure("FAIL: Should be at " + expectedLine + ", are at " +
                    theLine + ", method = " + loc.method().name());
        } else {
            println("Should be at, and am at: " + expectedLine);
        }
        return theLine;
    }


    public void vmDied(VMDeathEvent event) {
        println("Got VMDeathEvent");
    }

    public void vmDisconnected(VMDisconnectEvent event) {
        println("Got VMDisconnectEvent");
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        runOnce();
    }

    void runOnce() throws Exception{
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain("PopAndStepTarg");
        targetClass = bpe.location().declaringType();
        mainThread = bpe.thread();
        getDebuggeeLineNum(38);

        println("Resuming to line 27");
        bpe = resumeTo("PopAndStepTarg", 27); getDebuggeeLineNum(27);

        // The failure is this:
        //   create step request
        //   enable step request
        //   pop frame
        //   do the step
        //   do another step - This step runs to completion
        EventRequestManager erm = eventRequestManager();
        StepRequest srInto = erm.createStepRequest(mainThread, StepRequest.STEP_LINE,
                                                   StepRequest.STEP_INTO);
        srInto.addClassExclusionFilter("java.*");
        srInto.addClassExclusionFilter("javax.*");
        srInto.addClassExclusionFilter("sun.*");
        srInto.addClassExclusionFilter("com.sun.*");
        srInto.addClassExclusionFilter("com.oracle.*");
        srInto.addClassExclusionFilter("oracle.*");
        srInto.addClassExclusionFilter("jdk.internal.*");
        srInto.addCountFilter(1);
        srInto.enable(); // This fails
        mainThread.popFrames(frameFor("A"));
        //srInto.enable();   // if the enable is moved here, it passes
        println("Popped back to line 41 in main, the call to A()");
        println("Stepping into line 31");
        waitForRequestedEvent(srInto);   // println
        srInto.disable();

        getDebuggeeLineNum(31);

        // The failure occurs here.
        println("Stepping over to line 32");
        stepOverLine(mainThread);   // println
        getDebuggeeLineNum(32);

        println("Stepping over to line 33");
        stepOverLine(mainThread);        // call to B()
        getDebuggeeLineNum(33);

        vm().exit(0);

        if (testFailed) {
            throw new Exception("PopAndStepTest failed");
        }
        println("Passed:");
    }
}
