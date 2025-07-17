/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8309811
 * @requires vm.debug
 * @summary Test the output of -XX:+TraceBytecodes, -XX:TraceBytecodesAt, and -XX:TraceBytecodesStopAt
 * @run main/othervm -XX:+TraceBytecodes -XX:TraceBytecodesAt=1000000 -XX:TraceBytecodesStopAt=1001000 TraceBytecodes
 */

// CountBytecodes returns 1826384 bytecodes, so trace starting at a million to trace parallel threads.
// This is just a very simple sanity test. See the .jtr file for the output.
// Consider it OK if the VM doesn't crash. It should test a fair amount of the code in bytecodeTracer.cpp
public class TraceBytecodes extends Thread {
    public void run() {
        System.out.println("Hello TraceBytecodes");
    }

    private static TraceBytecodes[] threads = new TraceBytecodes[2];
    private static boolean success = true;

    private static boolean report_success() {
        for (int i = 0; i < 2; i++) {
          try {
            threads[i].join();
          } catch (InterruptedException e) {}
        }
        return success;
    }

    public static void main(String args[]) {
        threads[0] = new TraceBytecodes();
        threads[1] = new TraceBytecodes();
        for (int i = 0; i < 2; i++) {
            threads[i].setName("Loading Thread #" + (i + 1));
            threads[i].start();
            System.out.println("Thread " + (i + 1) + " was started...");
        }

        if (report_success()) {
           System.out.println("PASSED");
        } else {
            throw new RuntimeException("FAILED");
        }
    }
}
