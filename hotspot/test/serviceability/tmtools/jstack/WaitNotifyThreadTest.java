/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary  Call Object.wait() method. Check that monitor information
 *           presented in the stack is correct. Call notifyAll method
 *           monitor info have to disappear from the stack.
 *           Repeats the same scenario calling interrupt() method
 * @modules java.base/jdk.internal.misc
 * @library /test/lib/share/classes
 * @library ../share
 * @build common.*
 *
 * @run main/othervm -XX:+UsePerfData WaitNotifyThreadTest
 */
import common.ToolResults;
import java.util.Iterator;
import utils.*;

public class WaitNotifyThreadTest {

    private Object monitor = new Object();
    private final String OBJECT = "a java.lang.Object";
    private final String OBJECT_WAIT = "java.lang.Object.wait";

    interface Action {

        void doAction(Thread thread);
    }

    class ActionNotify implements Action {

        @Override
        public void doAction(Thread thread) {
            //Notify the waiting thread, so it stops waiting and sleeps
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
    }

    class ActionInterrupt implements Action {

        @Override
        public void doAction(Thread thread) {
            // Interrupt the thread
            thread.interrupt();
        }
    }

    class WaitThread extends Thread {

        @Override
        public void run() {
            try {
                synchronized (monitor) {
                    monitor.wait();
                }
            } catch (InterruptedException x) {

            }
            Utils.sleep();
        }
    }

    public static void main(String[] args) throws Exception {
        new WaitNotifyThreadTest().doTest();
    }

    private void doTest() throws Exception {

        // Verify stack trace consistency when notifying the thread
        doTest(new ActionNotify());

        // Verify stack trace consistency when interrupting the thread
        doTest(new ActionInterrupt());
    }

    private void doTest(Action action) throws Exception {

        final String WAITING_THREAD_NAME = "MyWaitingThread";

        // Start athread that just waits
        WaitThread waitThread = new WaitThread();
        waitThread.setName(WAITING_THREAD_NAME);
        waitThread.start();

        // Collect output from the jstack tool
        JstackTool jstackTool = new JstackTool(ProcessHandle.current().getPid());
        ToolResults results = jstackTool.measure();

        // Analyze the jstack output for the patterns needed
        JStack jstack1 = new DefaultFormat().parse(results.getStdoutString());
        ThreadStack ti1 = jstack1.getThreadStack(WAITING_THREAD_NAME);
        analyzeThreadStackWaiting(ti1);

        action.doAction(waitThread);

        // Collect output from the jstack tool again
        results = jstackTool.measure();

        // Analyze the output again
        JStack jstack2 = new DefaultFormat().parse(results.getStdoutString());
        ThreadStack ti2 = jstack2.getThreadStack(WAITING_THREAD_NAME);
        analyzeThreadStackNoWaiting(ti2);

    }

    private void analyzeThreadStackWaiting(ThreadStack ti1) {
        Iterator<MethodInfo> it = ti1.getStack().iterator();

        String monitorAddress = null;
        while (it.hasNext()) {
            MethodInfo mi = it.next();
            if (mi.getName().startsWith(OBJECT_WAIT) && mi.getCompilationUnit() == null /*native method*/) {
                if (mi.getLocks().size() == 1) {
                    MonitorInfo monInfo = mi.getLocks().getFirst();
                    if (monInfo.getType().equals("waiting on") && compareMonitorClass(monInfo)) {
                        monitorAddress = monInfo.getMonitorAddress();
                    } else {
                        System.err.println("Error: incorrect monitor info: " + monInfo.getType() + ", " + monInfo.getMonitorClass());
                        throw new RuntimeException("Incorrect lock record in "
                                + OBJECT_WAIT + " method");
                    }

                } else {
                    throw new RuntimeException(OBJECT_WAIT
                            + " method has to contain one lock record bu it contains " + mi.getLocks().size());
                }
            }

            if (mi.getName().startsWith("WaitThread.run")) {
                if (monitorAddress == null) {
                    throw new RuntimeException("Cannot found monitor info associated with " + OBJECT_WAIT + " method");
                }

                int numLocks = mi.getLocks().size();
                for (int i = 0; i < numLocks - 1; ++i) {
                    assertMonitorInfo("waiting to re-lock in wait()", mi.getLocks().get(i), monitorAddress);
                }
                assertMonitorInfo("locked", mi.getLocks().getLast(), monitorAddress);
            }
        }

    }

    private void assertMonitorInfo(String expectedMessage, MonitorInfo monInfo, String monitorAddress) {
        if (monInfo.getType().equals(expectedMessage)
                && compareMonitorClass(monInfo)
                && monInfo.getMonitorAddress().equals(
                        monitorAddress)) {
            System.out.println("Correct monitor info found");
        } else {
            System.err.println("Error: incorrect monitor info: " + monInfo.getType() + ", " + monInfo.getMonitorClass() + ", " + monInfo.getMonitorAddress());
            System.err.println("Expected: " + expectedMessage + ", a java.lang.Object, " + monitorAddress);
            throw new RuntimeException("Incorrect lock record in 'run' method");
        }
    }

    private boolean compareMonitorClass(MonitorInfo monInfo) {
        // If monitor class info is present in the jstack output
        // then compare it with the class of the actual monitor object
        // If there is no monitor class info available then return true
        return OBJECT.equals(monInfo.getMonitorClass()) || (monInfo.getMonitorClass() == null);
    }

    private void analyzeThreadStackNoWaiting(ThreadStack ti2) {
        Iterator<MethodInfo> it = ti2.getStack().iterator();

        while (it.hasNext()) {
            MethodInfo mi = it.next();
            if (mi.getLocks().size() != 0) {
                throw new RuntimeException("Unexpected lock record in "
                        + mi.getName() + " method");
            }
        }
    }

}
