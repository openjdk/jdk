/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 */

/*
 * @test
 * @bug     6467152
 * @ignore until 6716076 is fixed
 * @summary deadlock occurs in LogManager initialization and JVM termination
 * @author  Serguei Spitsyn / Hittachi
 *
 * @build    LoggingDeadlock2
 * @run  main/timeout=15 LoggingDeadlock2
 *
 * There is a clear deadlock between LogManager.<clinit> and
 * Cleaner.run() methods.
 * T1 thread:
 *   The LogManager.<clinit> creates LogManager.manager object,
 *   sets shutdown hook with the Cleaner class and then waits
 *   to lock the LogManager.manager monitor.
 * T2 thread:
 *   It is started by the System.exit() as shutdown hook thread.
 *   It locks the LogManager.manager monitor and then calls the
 *   static methods of the LogManager class (in this particular
 *   case it is a trick of the inner classes implementation).
 *   It is waits when the LogManager.<clinit> is completed.
 *
 * This is a regression test for this bug.
 */

import java.util.logging.LogManager;

public class LoggingDeadlock2 implements Runnable {
    static final java.io.PrintStream out = System.out;
    static Object lock = new Object();
    static int c = 0;
    public static void main(String arg[]) {
        out.println("\nThis test checks that there is no deadlock.");
        out.println("If not crashed or timed-out then it is passed.");
        try {
            new Thread(new LoggingDeadlock2()).start();
            synchronized(lock) {
                c++;
                if (c == 2) lock.notify();
                else lock.wait();
            }
            LogManager log = LogManager.getLogManager();
            out.println("Test passed");
        }
        catch(Exception e) {
            e.printStackTrace();
            out.println("Test FAILED"); // Not expected
        }
    }

    public void run() {
        try {
            synchronized(lock) {
                c++;
                if (c == 2) lock.notify();
                else lock.wait();
            }
            System.exit(1);
        }
        catch(Exception e) {
            e.printStackTrace();
            out.println("Test FAILED"); // Not expected
        }
    }
}
