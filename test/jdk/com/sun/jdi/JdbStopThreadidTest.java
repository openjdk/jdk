/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8219143
 * @summary Tests that using the "stop in" threadid option will properly cause the
 * breakpoint to only be triggered when hit in the specified thread.
 *
 * @library /test/lib
 * @run compile -g JdbStopThreadidTest.java
 * @run main/othervm JdbStopThreadidTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import lib.jdb.Jdb;
import lib.jdb.JdbCommand;
import lib.jdb.JdbTest;

import java.util.regex.*;

class JdbStopThreadidTestTarg {
    static Object lockObj = new Object();

    public static void main(String[] args) {
        test();
    }

    private static void test() {
        JdbStopThreadidTestTarg test = new JdbStopThreadidTestTarg();
        MyTask myTask1 = test.new MyTask();
        MyTask myTask2 = test.new MyTask();
        MyTask myTask3 = test.new MyTask();
        Thread myThread1 = DebuggeeWrapper.newThread(myTask1, "MYTHREAD-1");
        Thread myThread2 = DebuggeeWrapper.newThread(myTask2, "MYTHREAD-2");
        Thread myThread3 = DebuggeeWrapper.newThread(myTask3, "MYTHREAD-3");

        synchronized (lockObj) {
            myThread1.start();
            myThread2.start();
            myThread3.start();
            // Wait for all threads to have started. Note they all block on lockObj after starting.
            while (!myTask1.started || !myTask2.started || ! myTask3.started) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                }
            }
            // Stop here so the test can setup the breakpoint in MYTHREAD-2
            brkMethod();
        }

        // Wait for all threads to finish before exiting
        try {
            myThread1.join();
            myThread2.join();
            myThread3.join();
        } catch (InterruptedException e) {
        }
    }

    static void brkMethod() {
    }

    public static void print(Object obj) {
        System.out.println(obj);
    }

    class MyTask implements Runnable {
        volatile boolean started = false;

        public void run() {
            started = true;
            synchronized (JdbStopThreadidTestTarg.lockObj) {
            }
            brkMethod();
        }

        void brkMethod() {
        }
    }
}

public class JdbStopThreadidTest extends JdbTest {
    public static void main(String argv[]) {
        new JdbStopThreadidTest().run();
    }

    private JdbStopThreadidTest() {
        super(DEBUGGEE_CLASS);
    }

    private static final String DEBUGGEE_CLASS = JdbStopThreadidTestTarg.class.getName();
    private static final String DEBUGGEE_THREAD_CLASS = JdbStopThreadidTestTarg.class.getName() + "$MyTask";
    private static Pattern threadidPattern = Pattern.compile("Thread\\)(\\S+)\\s+MYTHREAD-2");

    @Override
    protected void runCases() {
        jdb.command(JdbCommand.stopIn(DEBUGGEE_CLASS, "brkMethod"));
        jdb.command(JdbCommand.run().waitForPrompt("Breakpoint hit: \"thread=main\"", true));
        jdb.command(JdbCommand.threads());

        // Find the threadid for MYTHREAD-2 in the "threads" command output
        String output = jdb.getJdbOutput();
        Matcher m = threadidPattern.matcher(output);
        String threadid = null;
        if (m.find()) {
            threadid = m.group(1);
        } else {
            throw new RuntimeException("FAILED: Did not match threadid pattern.");
        }

        // Setup a breakpoint in MYTHREAD-2.
        jdb.command(JdbCommand.stopInThreadid(DEBUGGEE_THREAD_CLASS, "brkMethod", threadid));

        // Continue until MYTHREAD-2 breakpoint is hit. If we hit any other breakpoint before
        // then (we aren't suppose to), then this test will fail.
        jdb.command(JdbCommand.cont().waitForPrompt("Breakpoint hit: \"thread=MYTHREAD-2\", \\S+MyTask.brkMethod", true));
        // Continue until the application exits. Once again, hitting a breakpoint will cause
        // a failure because we are not suppose to hit one.
        jdb.contToExit(1);
        new OutputAnalyzer(getJdbOutput()).shouldContain(Jdb.APPLICATION_EXIT);
    }
}
