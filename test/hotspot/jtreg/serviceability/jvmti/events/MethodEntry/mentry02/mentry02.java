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
 * @summary converted from VM Testbase nsk/jvmti/MethodEntry/mentry002.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     Regression test for bug
 *         4248826 Method entry/exit events are not created for empty methods
 *         Release summary: 1.0_fcs
 *         Hardware version: generic
 *         O/S version (unbundled products): generic
 * COMMENTS
 *     The test reproduced the bug on winNT 1.0fcs-E build.
 *     Ported from JVMDI test /nsk/regression/b4248826.
 *
 * @requires vm.continuations
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} mentry02.java
 * @run main/othervm/native --enable-preview -agentlib:mentry02 mentry02
 */


public class mentry02 {

    final static int MAX_LOOP = 100;

    static {
        System.loadLibrary("mentry02");
    }

    static volatile int result;
    native static void getReady(int i);
    native static int check();

    public static void main(String args[]) {
        testVirtualThread();
        testPlatformThread();
    }
    public static void testVirtualThread() {
        Thread thread = Thread.startVirtualThread(() -> {
            getReady(MAX_LOOP);

            for (int i = 0; i < MAX_LOOP; i++) {
                emptyMethod();
            }
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
        getReady(MAX_LOOP);

        for (int i = 0; i < MAX_LOOP; i++) {
            emptyMethod();
        }
        result = check();
        if (result != 0) {
            throw new RuntimeException("check failed with result " + result);
        }
    }

    public static void emptyMethod() {}
}
