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
 * @summary converted from VM Testbase nsk/jvmti/GetStackTrace/getstacktr001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI function GetStackTrace for the current thread.
 *     The test checks the following:
 *       - if function returns the expected frame of a Java method
 *       - if function returns the expected frame of a JNI method
 *       - if function returns the expected number of expected_frames.
 *     Test verifies stack trace for platform and virtual threads.
 *     Test doesn't check stacktrace of JDK implementation, only checks 1st JDK frame.
 * COMMENTS
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} GetStackTraceCurrentThreadTest.java
 * @run main/othervm/native --enable-preview -agentlib:GetStackTraceCurrentThreadTest GetStackTraceCurrentThreadTest
 */

public class GetStackTraceCurrentThreadTest {

    static {
        System.loadLibrary("GetStackTraceCurrentThreadTest");
    }

    native static void chain();
    native static void check(Thread thread);

    public static void main(String args[]) throws Exception {
        Thread.ofPlatform().start(new Task()).join();
        Thread.ofVirtual().start(new Task()).join();
    }

    public static void dummy() {
        check(Thread.currentThread());
    }

}

class Task implements Runnable {
    @Override
    public void run() {
        GetStackTraceCurrentThreadTest.chain();
    }
}
