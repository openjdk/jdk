/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jvmti/GetFrameCount/framecnt002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test checks that JVMTI function GetFrameCount returns proper
 *     error codes when is called:
 *       - with NULL passed as the second actual parameter,
 *       - for a thread which is not is not alive.
 * COMMENTS
 *     Ported from JVMDI.
 *     Updating test to meet JVMTI spec 0.2.30:
 *     - check not alive thread
 *     - check JVMTI_ERROR_THREAD_NOT_ALIVE instead of JVMTI_ERROR_THREAD_NOT_SUSPENDED
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:framecnt02 framecnt02
 */

import java.io.PrintStream;

public class framecnt02 {

    native static void checkFrames(Thread thr, int thr_num);
    native static int getRes();

    static {
        try {
            System.loadLibrary("framecnt02");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load framecnt02 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    final static int JCK_STATUS_BASE = 95;

    static int flag = 0;

    public static void main(String args[]) {


        // produce JCK-like exit status.
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream ref) {
        Thread currThread = Thread.currentThread();
        framecnt02a tested_thread_thr1 = new framecnt02a();
        checkFrames(tested_thread_thr1, 1);
        checkFrames(currThread, 0);
        return getRes();
    }
}

class framecnt02a extends Thread {
    public void run() {
    }
}
