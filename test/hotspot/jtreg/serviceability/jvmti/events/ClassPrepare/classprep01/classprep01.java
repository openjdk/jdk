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
 * @summary converted from VM Testbase nsk/jvmti/ClassPrepare/classprep001.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     The test exercises JVMTI event callback function ClassPrepare.
 *     The test checks if class prepare event is generated when
 *     class preparation is complete, and, at this point, class
 *     fields, methods, and implemented interfaces are available,
 *     and no code from the class has been executed.
 * COMMENTS
 *     Fixed according to the bug 4651181.
 *     Ported from JVMDI.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} classprep01.java
 * @run main/othervm/native --enable-preview -agentlib:classprep01 classprep01
 */

public class classprep01 {

    static {
        System.loadLibrary("classprep01");
    }

    native static void getReady();
    native static int check();

    static volatile int result;
    public static void main(String args[]) {
        testPlatformThread();
        testVirtualThread();
    }
    public static void testVirtualThread() {
        Thread thread = Thread.startVirtualThread(() -> {
            getReady();
            new TestClassVirtual().run();
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
        getReady();
        new TestClass().run();
        result = check();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }


    interface TestInterface {
        int constant = Integer.parseInt("10");
        void run();
    }

     static class TestClass implements TestInterface {
        static int i = 0;
        int count = 0;
        static {
            i++;
        }
        public void run() {
            count++;
        }
    }


    interface TestInterfaceVirtual {
        int constant = Integer.parseInt("10");
        void run();
    }

    static class TestClassVirtual implements TestInterfaceVirtual {
        static int i = 0;
        int count = 0;
        static {
            i++;
        }
        public void run() {
            count++;
        }
    }


}
