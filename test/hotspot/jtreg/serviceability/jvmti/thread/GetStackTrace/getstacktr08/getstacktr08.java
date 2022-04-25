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
 * @summary converted from VM Testbase nsk/jvmti/GetStackTrace/getstacktr008.
 * VM Testbase keywords: [quick, jpda, jvmti, noras, redefine]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetStackTrace.
 *     The test starts a new thread, does some nested calls with a native
 *     call in the middle, and stops at breakpoint.
 *     Then the test does the following:
 *         - checks the stack on expected frames
 *         - steps
 *         - checks the stack on expected frames
 *         - pops frame
 *         - checks the stack on expected frames
 *         - redefines class
 *         - checks the stack on expected frames
 *         - checks the stack on expected frames just before
 *           returning from the native call.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} getstacktr08.java
 * @run main/othervm/native --enable-preview -agentlib:getstacktr08 getstacktr08
 */


import java.io.File;
import java.io.InputStream;

public class getstacktr08 {

    final static String fileName =
        TestThread.class.getName().replace('.', File.separatorChar) + ".class";

    static {
        System.loadLibrary("getstacktr08");
    }

    native static void getReady(Class clz, byte bytes[]);
    native static void nativeChain(Class clz);

    public static void main(String args[]) throws Exception {
        ClassLoader cl = getstacktr08.class.getClassLoader();
        Thread thread = Thread.ofPlatform().unstarted(new TestThread());

        InputStream in = cl.getSystemResourceAsStream(fileName);
        byte[] bytes = new byte[in.available()];
        in.read(bytes);
        in.close();

        getReady(TestThread.class, bytes);

        thread.start();
        thread.join();
        /* PopFrame not implemented for virtual threads yet.
        Thread vThread = Thread.ofVirtual().unstarted(new TestThread());
        getReady(TestThread.class, bytes);
        vThread.start();
        vThread.join();
        */

    }

    static class TestThread implements Runnable {
        public void run() {
            chain1();
        }

        static void chain1() {
            chain2();
        }

        static void chain2() {
            chain3();
        }

        static void chain3() {
            nativeChain(TestThread.class);
        }

        static void chain4() {
            chain5();
        }
        static void chain5() {
            checkPoint();
        }

        // dummy method to be breakpointed in agent
        static void checkPoint() {
        }
    }
}
