/*
 * Copyright (c) 2006, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6377205
 * @summary Test jvmti demo minst
 *
 * @compile ../DemoRun.java
 * @compile MinstExample.java
 * @build MinstTest
 * @run main MinstTest MinstExample
 */

public class MinstTest {

    public static void main(String args[]) throws Exception {
        DemoRun demo;

        /* Run demo that uses JVMTI minst agent (no options) */
        demo = new DemoRun("minst", "exclude=java/*,exclude=javax/*,exclude=com/*,exclude=sun/*" /* options to minst */ );
        demo.runit(args[0]);

        /* Make sure patterns in output look ok */
        if (demo.output_contains("ERROR")) {
            throw new RuntimeException("Test failed - ERROR seen in output");
        }

        /* Must be a pass. */
        System.out.println("Test passed - cleanly terminated");
    }
}
