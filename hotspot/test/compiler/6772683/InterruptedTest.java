/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/*
 * @test
 * @bug 6772683
 * @summary Thread.isInterrupted() fails to return true on multiprocessor PC
 * @run main/othervm InterruptedTest
 */

public class InterruptedTest {

    public static void main(String[] args) throws Exception {
        Thread workerThread = new Thread("worker") {
            public void run() {
                System.out.println("Worker thread: running...");
                while (!Thread.currentThread().isInterrupted()) {
                }
                System.out.println("Worker thread: bye");
            }
        };
        System.out.println("Main thread: starts a worker thread...");
        workerThread.start();
        System.out.println("Main thread: waits at most 5s for the worker thread to die...");
        workerThread.join(5000); // Wait 5 sec to let run() method to be compiled
        int ntries = 0;
        while (workerThread.isAlive() && ntries < 5) {
            System.out.println("Main thread: interrupts the worker thread...");
            workerThread.interrupt();
            if (workerThread.isInterrupted()) {
                System.out.println("Main thread: worker thread is interrupted");
            }
            ntries++;
            System.out.println("Main thread: waits for the worker thread to die...");
            workerThread.join(1000); // Wait 1 sec and try again
        }
        if (ntries == 5) {
          System.out.println("Main thread: the worker thread dod not die");
          System.exit(97);
        }
        System.out.println("Main thread: bye");
    }

}
