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
 * @summary converted from VM Testbase nsk/jvmti/GetStackTrace/getstacktr003.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetStackTrace for a non current thread.
 *     The test checks the following:
 *       - if function returns the expected frame of a Java method
 *       - if function returns the expected frame of a JNI method
 *       - if function returns the expected number of frames.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} getstacktr03.java
 * @run main/othervm/native --enable-preview -agentlib:getstacktr03 getstacktr03
 */

public class getstacktr03 {

    static {
        System.loadLibrary("getstacktr03");
    }

    native static void chain();
    native static int check(Thread thread);

    public static Object lockIn = new Object();
    public static Object lockOut = new Object();

    public static void main(String args[]) {
        Thread thread = Thread.ofPlatform().unstarted(new Task());
        test(thread);

        Thread vthread = Thread.ofVirtual().unstarted(new Task());
        test(vthread);
    }

    public static void test(Thread thr) {
        synchronized (lockIn) {
            thr.start();
            try {
                lockIn.wait();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }

        synchronized (lockOut) {
            check(thr);
            lockOut.notify();
        }

        try {
            thr.join();
        } catch (InterruptedException e) {
            throw new Error("Unexpected " + e);
        }
    }

    static void dummy() {
        synchronized (lockOut) {
            synchronized (lockIn) {
                lockIn.notify();
            }
            try {
                lockOut.wait();
            } catch (InterruptedException e) {
                throw new Error("Unexpected " + e);
            }
        }
    }
    static class Task implements Runnable {
        @Override
        public void run() {
            getstacktr03.chain();
        }
    }
}
