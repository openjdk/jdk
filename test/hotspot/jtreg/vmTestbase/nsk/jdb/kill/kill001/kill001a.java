/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdb.kill.kill001;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdb.*;
import nsk.share.jdi.JDIThreadFactory;

import java.io.*;
import java.util.*;

/* This is debuggee aplication */
public class kill001a {
    public static void main(String args[]) {
       kill001a _kill001a = new kill001a();
       System.exit(kill001.JCK_STATUS_BASE + _kill001a.runIt(args, System.out));
    }

    static void breakHere () {}

    static final String MYTHREAD         = nsk.jdb.kill.kill001.kill001.MYTHREAD;
    static final int numThreads          = 5;   // number of threads. one lock per thread.
    static Object lock                   = new Object();
    static Object waitnotify             = new Object();
    public static volatile int killed    = 0;
    static final String message          = "kill001a's Exception";
    static int waitTime;

    static JdbArgumentHandler argumentHandler;
    static Log log;

    static final Throwable[] exceptions = {
                    new ThreadDeath(),
                    new NullPointerException(message),
                    new SecurityException(message),
                    new com.sun.jdi.IncompatibleThreadStateException(message),
                    new MyException(message)
    };


    public int runIt(String args[], PrintStream out) {
        argumentHandler = new JdbArgumentHandler(args);
        log = new Log(out, argumentHandler);
        waitTime = argumentHandler.getWaitTime() * 60 * 1000;

        int i;
        Thread holder [] = new Thread[numThreads];

        for (i = 0; i < numThreads ; i++) {
            String name = MYTHREAD + "-" + i;
            holder[i] = JDIThreadFactory.newThread(new MyThread(name, exceptions[i]), name);
        }

        // lock monitor to prevent threads from finishing after they started
        synchronized (lock) {
            synchronized (waitnotify) {
                for (i = 0; i < numThreads ; i++) {
                    holder[i].start();
                    try {
                        waitnotify.wait();
                    } catch (InterruptedException e) {
                        log.complain("Main thread was interrupted while waiting for start of " + MYTHREAD + "-" + i);
                        return kill001.FAILED;
                    }
                }
            }

            breakHere();  // a break to get thread ids and then to kill MyThreads.
        }

        // wait during waitTime until all MyThreads will be killed
        long oldTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - oldTime) <= kill001a.waitTime) {
            boolean waited = false;
            for (i = 0; i < numThreads ; i++) {
                if (holder[i].isAlive()) {
                    waited = true;
                    try {
                        synchronized(waitnotify) {
                            waitnotify.wait(1000);
                        }
                    } catch (InterruptedException e) {
                        log.complain("Main thread was interrupted while waiting for killing of " + MYTHREAD + "-" + i);
                    }
                }
            }
            if (!waited) {
                break;
            }
        }
        breakHere(); // a break to check if MyThreads were killed
        log.display("killed == " + killed);

        for (i = 0; i < numThreads ; i++) {
            if (holder[i].isAlive()) {
                log.complain("Debuggee FAILED - thread " + i + " is alive");
                return kill001.FAILED;
            }
        }

        log.display("Debuggee PASSED");
        return kill001.PASSED;
    }
}

class MyException extends Exception {
    MyException (String message) {
        super(message);
    }
}

class MyThread extends Thread {
    String name;
    Throwable expectedException;
    public boolean exceptionThrown = true;

    public MyThread(String n, Throwable e) {
        name = n;
        expectedException = e;
    }


    static public int[] trash;

    void methodForException() {
        trash = new int[10];
        for (int i = 0; ;i++) {
            trash[i % trash.length] = i;
        }
    }

    public void run() {
        // Concatenate strings in advance to avoid lambda calculations later
        String ThreadFinished = "Thread finished: " + this.name;
        String CaughtExpected = "Thread " + this.name + " caught expected async exception: " + expectedException;
        String CaughtUnexpected = "WARNING: Thread " + this.name + " caught unexpected exception:";

        kill001a.log.display("Thread started: " + this.name);

        synchronized (kill001a.waitnotify) {
            kill001a.waitnotify.notify();
        }

        try {
            synchronized (kill001a.lock) { }
            // We need some code that does an invoke here to make sure the async exception
            // gets thrown before we leave the try block.
            // The methodForException should work until exception is thrown.
            methodForException();
        } catch (Throwable t) {
            if (t == expectedException) {
                kill001a.log.display(CaughtExpected);
                // Need to make sure the increment is atomic
                synchronized (kill001a.lock) {
                    kill001a.killed++;
                }
            } else {
                kill001a.log.display(CaughtUnexpected);
                kill001a.log.display(t);
                t.printStackTrace(kill001a.log.getOutStream());
            }
        }
        kill001a.log.display(ThreadFinished);
    }
}
