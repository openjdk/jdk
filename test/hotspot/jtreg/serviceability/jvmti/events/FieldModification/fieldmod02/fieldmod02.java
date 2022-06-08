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
 * @summary converted from VM Testbase nsk/jvmti/FieldModification/fieldmod002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercise JVMTI event callback function FieldModification.
 *     The test checks if the parameters of the function contain the
 *     expected values for fields modified from JNI code.
 * COMMENTS
 *     Fixed according to 4669812 bug.
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} fieldmod02.java
 * @run main/othervm/native --enable-preview -agentlib:fieldmod02 fieldmod02
 */


public class fieldmod02 {

    static {
        try {
            System.loadLibrary("fieldmod02");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load fieldmod02 library");
            System.err.println("java.library.path:"
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    static volatile int result;
    native static void getReady();
    native static int check(Object obj);

    public static void main(String args[]) {
        testPlatformThread();
        testVirtualThread();
    }
    public static void testVirtualThread() {
        Thread thread = Thread.startVirtualThread(() -> {
            fieldmod02a t = new fieldmod02a();
            getReady();
            result = check(t);
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
        fieldmod02a t = new fieldmod02a();
        getReady();
        result = check(t);
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }
}

class fieldmod02a {
    static boolean staticBoolean;
    static byte staticByte;
    static short staticShort;
    static int staticInt;
    static long staticLong;
    static float staticFloat;
    static double staticDouble;
    static char staticChar;
    static Object staticObject;
    static int staticArrInt[];
    boolean instanceBoolean;
    byte instanceByte;
    short instanceShort;
    int instanceInt;
    long instanceLong;
    float instanceFloat;
    double instanceDouble;
    char instanceChar;
    Object instanceObject;
    int instanceArrInt[];
}
