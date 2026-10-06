/*
 * Copyright (c) 2002, 2026, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdb.read.read001;

import jdk.test.lib.thread.ThreadWrapper;
import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdb.*;

import java.io.*;

/* This is debuggee aplication */
public class read001a {

    static boolean testedStaticFieldBoolean = true;
    double testedInstanceFieldDouble = (double)3.1414926;

    public static void main(String args[]) {
        read001a _read001a = new read001a();
        System.exit(read001.JCK_STATUS_BASE + _read001a.runIt(args, System.out));
    }

    void lastBreak () {}

    void testedInstanceMethod() {
        int testedLocalVarInt = 0;
        String testedLocalVarString = "foo";

        lastBreak();

        testedLocalVarString = "bar";
    }

    public int runIt(String args[], PrintStream out) {
        JdbArgumentHandler argumentHandler = new JdbArgumentHandler(args);
        Log log = new Log(out, argumentHandler);
        int localInt = 0;

        read001aTestedClass obj = new read001aTestedClass();
        read001aTestedThread thread = new read001aTestedThread("TestedThreadInstance");

        synchronized (thread.finishingMonitor) {
            synchronized (thread.startingMonitor) {
                try {
                    thread.start();
                    thread.startingMonitor.wait();
                } catch (InterruptedException e) {
                    throw new Failure("Interrupted while starting tested thread: " + e);
                }
            }

            testedInstanceMethod();
        }

        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new Failure("Interrupted while finishing tested thread: " + e);
        }

        log.display("Debuggee PASSED");
        return read001.PASSED;
    }
}

class read001aTestedClass {
    char instanceFiledChar = 'x';
}

class read001aTestedThread extends ThreadWrapper {

    Object startingMonitor = new Object();
    Object finishingMonitor = new Object();

    public read001aTestedThread(String name) {
        super(name);
    }

    // Each tested thread calls this once at startup. The debugger sets a
    // breakpoint here: receiving the event is what makes a virtual thread
    // visible to jdb with the default debug agent behavior, so the test
    // does not need the -trackallthreads option.
    static void threadStarted() {}

    public void run() {
        threadStarted();
        synchronized (startingMonitor) {
            startingMonitor.notifyAll();
        }

        synchronized (finishingMonitor) {
        }
    }
}
