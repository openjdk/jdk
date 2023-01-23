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
 * @summary converted from VM Testbase nsk/jvmti/ThreadStart/threadstart003.
 * VM Testbase keywords: [quick, jpda, jvmti, noras]
 * VM Testbase readme:
 * DESCRIPTION
 *     Regression test for bug
 *         4266590 Thread start events are sent from the wrong thread
 *         Release summary: kestrel
 *         Hardware version: generic
 *         O/S version (unbundled products): generic
 * COMMENTS
 *     The test reproduced the bug on winNT 1.3.0-E build.
 *     Ported from JVMDI.
 *
 * @library /test/lib
 * @run main/othervm/native -agentlib:threadstart03 threadstart03
 */


public class threadstart03 {

    final static String ThreadName = "testedThread";

    static {
        System.loadLibrary("threadstart03");
    }

    native static int check(Thread thr, String name);

    public static void main(String args[]) {
        int result = check(new Thread(ThreadName), ThreadName);
        if (result != 0) {
            throw new RuntimeException("Unexpected status: " + result);
        }
    }

}
