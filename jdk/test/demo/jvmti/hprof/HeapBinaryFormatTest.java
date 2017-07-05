/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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


/* @test
 * @bug 4965057 6313381
 * @summary Test jvmti hprof format=b
 *
 * @compile -g:source HelloWorld.java ../DemoRun.java
 * @build HeapBinaryFormatTest
 * @run main HeapBinaryFormatTest HelloWorld
 */

public class HeapBinaryFormatTest {

    public static void main(String args[]) throws Exception {
        DemoRun hprof;

        /* Run JVMTI hprof agent to get binary format dump */
        hprof = new DemoRun("hprof", "heap=dump,format=b,logflags=4");
        hprof.runit(args[0]);

        /* Make sure patterns in output look ok */
        if (hprof.output_contains("ERROR")) {
            throw new RuntimeException("Test failed - ERROR seen in output");
        }

        /* Try a variation */
        String vm_opts[] = new String[1];
        vm_opts[0] = "-Xmx2100m";
        /* Crashes on small Linux machines: (like fyi)
           How can I tell how much real memory is on a machine?
           hprof.runit(args[0], vm_opts);
        */

        /* Make sure patterns in output look ok */
        if (hprof.output_contains("ERROR")) {
            throw new RuntimeException("Test failed - ERROR seen in output");
        }

        /* Must be a pass. */
        System.out.println("Test passed - cleanly terminated");
    }
}
