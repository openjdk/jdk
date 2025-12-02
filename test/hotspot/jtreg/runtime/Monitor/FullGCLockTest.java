/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test id=FullGCLockTest-ThreadsLockThrottle_lock
 * @summary This tests that the FullGCALot_lock rank is nosafepoint.
 * @run main/othervm -XX:+GCALotAtAllSafepoints -XX:+FullGCALot FullGCLockTest threads
 */

/*
 * @test id=FullGCLockTest-MethodCompileQueue_lock
 * @summary This tests that the FullGCALot_lock rank is nosafepoint.
 * @run main/othervm -Xcomp -XX:FullGCALotStart=10000 -XX:+GCALotAtAllSafepoints -XX:+FullGCALot FullGCLockTest nothing
 */

public class FullGCLockTest {
    final static int COUNT = 10;

    static class StartThread extends Thread {
        public void run() {
            System.out.println("Count is " + COUNT);
        }
    }

    public static void main(java.lang.String[] argv) {
        System.out.println(argv[0]);
        if (argv[0].equals("threads")) {
            for (int i = 0; i < COUNT; i++) {
                Thread t = new StartThread();
                t.start();
            }
        }
    }
}


