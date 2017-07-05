/*
 * Copyright 2004-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */


/* @test
 * @bug 5012882 6299047
 * @summary Test jvmti hprof
 *
 * @compile -g HelloWorld.java ../DemoRun.java
 * @build HeapAllTest
 * @run main HeapAllTest HelloWorld
 */

public class HeapAllTest {

    public static void main(String args[]) throws Exception {
        DemoRun hprof;

        /* Run JVMTI hprof agent with heap=all */
        hprof = new DemoRun("hprof", "heap=all");
        hprof.runit(args[0]);

        /* Make sure patterns in output look ok */
        if (hprof.output_contains("ERROR")) {
            throw new RuntimeException("Test failed - ERROR seen in output");
        }

        /* Must be a pass. */
        System.out.println("Test passed - cleanly terminated");
    }
}
