/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4519200
 * @summary Confirm a Thread.stop before start complies with the spec
 * @author  Pete Soper
 *
 * Confirm that a thread that had its stop method invoked before start
 * does properly terminate with expected exception behavior. NOTE that
 * arbitrary application threads could return from their run methods faster
 * than the VM can throw an async exception.
 */
public class StopBeforeStart {

    private static final int JOIN_TIMEOUT=10000;

    private class MyThrowable extends Throwable {
    }

    private class Catcher implements Thread.UncaughtExceptionHandler {
        private boolean nullaryStop;
        private Throwable theThrowable;
        private Throwable expectedThrowable;
        private boolean exceptionThrown;

        Catcher(boolean nullaryStop) {
            this.nullaryStop = nullaryStop;
            if (!nullaryStop) {
                expectedThrowable = new MyThrowable();
            }
        }

        public void uncaughtException(Thread t, Throwable th) {
            exceptionThrown = true;
            theThrowable = th;
        }

        void check(String label) throws Throwable {
            if (!exceptionThrown) {
                throw new RuntimeException(label +
                        " test:" + " missing uncaught exception");
            }

            if (nullaryStop) {
                if (! (theThrowable instanceof ThreadDeath)) {
                    throw new RuntimeException(label +
                        " test:" + " expected ThreadDeath in uncaught handler");
                }
            } else if (theThrowable != expectedThrowable) {
                throw new RuntimeException(label +
                        " test:" + " wrong Throwable in uncaught handler");
            }
        }
    }

    private class MyRunnable implements Runnable {
        public void run() {
            while(true)
                ;
        }
    }

    private class MyThread extends Thread {
        public void run() {
            while(true)
                ;
        }
    }


    public static void main(String args[]) throws Throwable {
        (new StopBeforeStart()).doit();
        System.out.println("Test passed");
    }

    private void doit() throws Throwable {

        runit(false, new Thread(new MyRunnable()),"Thread");
        runit(true, new Thread(new MyRunnable()),"Thread");
        runit(false, new MyThread(),"Runnable");
        runit(true, new MyThread(),"Runnable");
    }

    private void runit(boolean nullaryStop, Thread thread,
                        String type) throws Throwable {

        Catcher c = new Catcher(nullaryStop);
        thread.setUncaughtExceptionHandler(c);

        if (nullaryStop) {
            thread.stop();
        } else {
            thread.stop(c.expectedThrowable);
        }

        thread.start();
        thread.join(JOIN_TIMEOUT);

        if (thread.getState() != Thread.State.TERMINATED) {

            thread.stop();

            // Under high load this could be a false positive
            throw new RuntimeException(type +
                        " test:" + " app thread did not terminate");
        }

        c.check(type);
    }
}
