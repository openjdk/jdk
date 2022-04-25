/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.jvmti.DebugeeClass;
import java.io.PrintStream;

/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/ThreadEnd/threadend002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function THREAD_END.
 *     The test enables this event during OnLoad phase. The test fails
 *     if no THREAD_END event is received
 * COMMENTS
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:threadend02=-waittime=5 threadend02
 */

public class threadend02 extends DebugeeClass {

    // run test from command line
    public static void main(String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new threadend02().runIt(argv, out);
    }

    // run debuggee
    public int runIt(String argv[], PrintStream out) {

        int status = threadend02.checkStatus(DebugeeClass.TEST_PASSED);

        threadend02Thread thrd = new threadend02Thread();
        thrd.start();

        try {
            thrd.join();
        } catch(InterruptedException e) {
            System.out.println("Unexpected exception " + e);
            e.printStackTrace();
            return DebugeeClass.TEST_FAILED;
        }

        int currStatus = threadend02.checkStatus(DebugeeClass.TEST_PASSED);
        if (currStatus != DebugeeClass.TEST_PASSED)
            status = currStatus;

        return status;
    }

    class threadend02Thread extends Thread {

        public void run() {
            System.out.println("thread finished");
        }
    }

}
