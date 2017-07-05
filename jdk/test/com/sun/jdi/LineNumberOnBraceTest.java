/**
 *  @test/nodynamiccopyright/
 *  @bug 4952629 4870514
 *  @summary REGRESSION: javac generates a spurious line number entry on } else {
 *
 *  @author jjh
 *
 *  @run build VMConnection TargetListener TargetAdapter
 *  @run compile -g LineNumberOnBraceTest.java
 *  @run driver LineNumberOnBraceTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

    /********** LINE NUMBER SENSITIVE! *****************************************************************/
class LineNumberOnBraceTarg {

    public final static int stopLine = 28;   // THIS MUST BE THE LINE NUMBER OF THE // stopline LINE
    public final static int stopLine2 = 34;  // THIS MUST BE THE LINE NUMBER OF THE // stopline2 LINE


    public static void main(String[] args){
        System.out.println("Howdy!");
        if (args.length == 0) {
            System.out.println("No args to debuggee");             // stopLine
        } else {
            System.out.println("Some args to debuggee");
        }
        if (args.length == 0) {
            boolean b1 = false;
            if (b1) {                                              // stopLine2
                System.out.println("In 2nd else");                 // bug 4870514 is that we stop here.
            }
        } else {
            System.out.println("In 2nd else");
        }
        System.out.println("Goodbye from LineNumberOnBraceTarg!");  // stopLine2 + 6
    }

    // This isn't part of the test; it is just here
    // so one can see what line numbers are generated for a finally.
    public void exampleOfThrow() {
        try {
            throw new Exception();
        } catch (Exception e) {
            System.out.println("caught exception");
        } finally {
            System.out.println("finally");
        }
    }

}

    /********** test program **********/

public class LineNumberOnBraceTest extends TestScaffold {
    ReferenceType targetClass;
    ThreadReference mainThread;

    LineNumberOnBraceTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new LineNumberOnBraceTest(args).startTests();
    }
    /********** test core **********/

    protected void runTests() throws Exception {
        /*
         * Get to the top of main()
         * to determine targetClass and mainThread
         */
        BreakpointEvent bpe = startToMain("LineNumberOnBraceTarg");
        targetClass = bpe.location().declaringType();
        mainThread = bpe.thread();

        resumeTo("LineNumberOnBraceTarg", LineNumberOnBraceTarg.stopLine);
        StepEvent stepev = stepOverLine(mainThread);       // step to 2nd if (args.length

        // Bug 4952629 is that javac outputs a line number
        // on the goto around the else which causes us to
        // be stopped at that goto instead of the println("Goodbye ...")

        int ln = stepev.location().lineNumber();
        System.out.println("Debuggee is stopped at line " + ln);
        if (ln != LineNumberOnBraceTarg.stopLine + 4) {
            failure("FAIL: Bug 4952629: Should be at line " +
                    (LineNumberOnBraceTarg.stopLine + 4) +
                    ", am at " + ln);
        } else {
            System.out.println("Passed test for 4952629");
        }

        // Test for bug 4870514
        System.out.println("Resuming to " + LineNumberOnBraceTarg.stopLine2);
        resumeTo("LineNumberOnBraceTarg", LineNumberOnBraceTarg.stopLine2);
        System.out.println("Stopped at " + LineNumberOnBraceTarg.stopLine2);
        stepev = stepOverLine(mainThread);
        ln = stepev.location().lineNumber();
        System.out.println("Debuggee is stopped at line " + ln);
        if (ln == LineNumberOnBraceTarg.stopLine2 + 1) {
            failure("FAIL: bug 4870514: Incorrectly stopped at " +
                    (LineNumberOnBraceTarg.stopLine2 + 1));
        } else {
            System.out.println("Passed test for 4870514");
        }


        /*
         * resume the target listening for events
         */
        listenUntilVMDisconnect();

        /*
         * deal with results of test
         * if anything has called failure("foo") testFailed will be true
         */
        if (!testFailed) {
            println("LineNumberOnBraceTest: passed");
        } else {
            throw new Exception("LineNumberOnBraceTest: failed");
        }
    }
}
