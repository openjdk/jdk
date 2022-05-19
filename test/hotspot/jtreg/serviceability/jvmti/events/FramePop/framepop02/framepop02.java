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
 * @summary converted from VM Testbase nsk/jvmti/FramePop/framepop002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function FramePop.
 *     The test do some nesting/recursive calls watching frame pop
 *     events to be uniquely identified by thread/class/method/frame_depth.
 * COMMENTS
 *     The test was created as a result of investigating the following bugs
 *     intended to write a regression test:
 *     4335224 Bug 4245697 not completely fixed jevent.u.frame.frame incorrect
 *     4504077 java: dbx should not hold on to a frameid after thread suspension
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} framepop02.java
 * @run main/othervm/native --enable-preview -agentlib:framepop02 framepop02 platform
 */
/*
 * @test
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} framepop02.java
 * @run main/othervm/native --enable-preview -agentlib:framepop02 framepop02 virtual
 */

public class framepop02 {

    final static int MAX_THREADS_LIMIT = 32;
    final static int NESTING_DEPTH = 20;
    final static String TEST_THREAD_NAME_BASE = "Test Thread #";

    static {
        System.loadLibrary("framepop02");
    }

    native static void getReady();
    native static void check();

    public static void main(String args[]) {
        boolean isVirtual = args.length > 0 && args[0].equals("virtual");
        final int THREADS_LIMIT = Math.min(Runtime.getRuntime().availableProcessors() + 1, MAX_THREADS_LIMIT);
        Thread[] t = new Thread[THREADS_LIMIT];
        getReady();
        Thread.Builder builder = (isVirtual ? Thread.ofVirtual() : Thread.ofPlatform())
                .name(TEST_THREAD_NAME_BASE, 0);
        System.out.println("Builder: " + builder);
        for (int i = 0; i < THREADS_LIMIT; i++) {
            t[i] = builder.start(new TestTask());
        }
        for (int i = 0; i < THREADS_LIMIT; i++) {
            try {
                t[i].join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
        }
        check();
    }

    static class TestTask implements Runnable {
        int nestingCount = 0;

        public void run() {
            if (nestingCount < NESTING_DEPTH) {
                nestingCount++;
                System.out.println(".");
                run();
            }
        }
    }
}
