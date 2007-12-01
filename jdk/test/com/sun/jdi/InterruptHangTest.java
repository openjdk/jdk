/**
 *  @test
 *  @bug 6459476
 *  @summary Debuggee is blocked,  looks like running
 *
 *  @author jjh
 *
 *  @run build TestScaffold VMConnection TargetListener TargetAdapter
 *  @run compile -g InterruptHangTest.java
 *  @run main InterruptHangTest
 */
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

/*
 * Debuggee has two threads.  Debugger keeps stepping in
 * the first thread.  The second thread keeps interrupting the first
 * thread.  If a long time goes by with the debugger not getting
 * a step event, the test fails.
 */
class InterruptHangTarg {
    public static String sync = "sync";
    public static void main(String[] args){
        int answer = 0;
        System.out.println("Howdy!");
        Interruptor interruptorThread = new Interruptor(Thread.currentThread());

        synchronized(sync) {
            interruptorThread.start();
            try {
                sync.wait();
            } catch (InterruptedException ee) {
                System.out.println("Debuggee interruptee: interrupted before starting loop");
            }
        }

        // Debugger will keep stepping thru this loop
        for (int ii = 0; ii < 200; ii++) {
            answer++;
            try {
                // Give other thread a chance to run
                Thread.sleep(100);
            } catch (InterruptedException ee) {
                System.out.println("Debuggee interruptee: interrupted at iteration: "
                                   + ii);
            }
        }
        // Kill the interrupter thread
        interruptorThread.interrupt();
        System.out.println("Goodbye from InterruptHangTarg!");
    }
}

class Interruptor extends Thread {
    Thread interruptee;
    Interruptor(Thread interruptee) {
        this.interruptee = interruptee;
    }

    public void run() {
        synchronized(InterruptHangTarg.sync) {
            InterruptHangTarg.sync.notify();
        }

        int ii = 0;
        while(true) {
            ii++;
            interruptee.interrupt();
            try {
                Thread.sleep(10);
            } catch (InterruptedException ee) {
                System.out.println("Debuggee Interruptor: finished after " +
                                   ii + " iterrupts");
                break;
            }

        }
    }
}

    /********** test program **********/

public class InterruptHangTest extends TestScaffold {
    ThreadReference mainThread;
    Thread timerThread;
    String sync = "sync";
    static int nSteps = 0;

    InterruptHangTest (String args[]) {
        super(args);
    }

    public static void main(String[] args)      throws Exception {
        new InterruptHangTest(args).startTests();
    }

    /********** event handlers **********/

    public void stepCompleted(StepEvent event) {
        synchronized(sync) {
            nSteps++;
        }
        println("Got StepEvent " + nSteps + " at line " +
                event.location().method() + ":" +
                event.location().lineNumber());
        if (nSteps == 1) {
            timerThread.start();
        }
    }

    /********** test core **********/

    protected void runTests() throws Exception {
        BreakpointEvent bpe = startToMain("InterruptHangTarg");
        mainThread = bpe.thread();
        EventRequestManager erm = vm().eventRequestManager();

        /*
         * Set event requests
         */
        StepRequest request = erm.createStepRequest(mainThread,
                                                    StepRequest.STEP_LINE,
                                                    StepRequest.STEP_OVER);
        request.enable();

        // Will be started by the step event handler
        timerThread = new Thread("test timer") {
                public void run() {
                    int mySteps = 0;
                    while (true) {
                        try {
                            Thread.sleep(20000);
                            synchronized(sync) {
                                System.out.println("steps = " + nSteps);
                                if (mySteps == nSteps) {
                                    // no step for 10 secs
                                    failure("failure: Debuggee appears to be hung");
                                    vm().exit(-1);
                                    break;
                                }
                            }
                            mySteps = nSteps;
                        } catch (InterruptedException ee) {
                            break;
                        }
                    }
                }
            };

        /*
         * resume the target listening for events
         */

        listenUntilVMDisconnect();
        timerThread.interrupt();

        /*
         * deal with results of test
         * if anything has called failure("foo") testFailed will be true
         */
        if (!testFailed) {
            println("InterruptHangTest: passed");
        } else {
            throw new Exception("InterruptHangTest: failed");
        }
    }
}
