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

import java.io.PrintStream;
/*
 * @test
 *
 * @summary converted from VM Testbase nsk/jvmti/MethodExit/mexit002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function MethodExit.
 *     The test checks the following:
 *       - if clazz, method and frame parameters of the function
 *         contain expected values for events generated upon exit
 *         from Java and native methods.
 *       - if GetFrameLocation indentifies the executable location
 *         in the returning method, immediately prior to the return.
 *     The test is the same as mexit01 one. The only difference is
 *     the METHOD_EXIT event enable is moved from method chain()
 *     to method check().
 * COMMENTS
 *     Ported from JVMDI.
 *     Fixed the 5004632 bug.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile mexit02a.jasm
 * @compile --enable-preview -source ${jdk.version} mexit02.java
 * @run main/othervm/native --enable-preview -agentlib:mexit02 mexit02
 */



public class mexit02 {

    static {
        try {
            System.loadLibrary("mexit02");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load mexit02 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    static volatile int result;
    native static int check();

    public static void main(String args[]) {
        testVirtualThread();
        testPlatformThread();
    }
    public static void testVirtualThread() {
        Thread thread = Thread.startVirtualThread(() -> {
            result = check();
        });
        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }
    public static void testPlatformThread() {
        result = check();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }
}
