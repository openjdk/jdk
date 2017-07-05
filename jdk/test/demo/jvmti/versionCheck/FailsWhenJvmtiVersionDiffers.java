/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5039613
 * @summary Test jvmti demo versionCheck
 *
 * @compile ../DemoRun.java ../Hello.java
 * @build FailsWhenJvmtiVersionDiffers
 * @run main FailsWhenJvmtiVersionDiffers Hello
 */

public class FailsWhenJvmtiVersionDiffers {

    public static void main(String args[]) throws Exception {
        DemoRun demo;

        /* Run demo that uses JVMTI versionCheck agent (no options) */
        demo = new DemoRun("versionCheck", "" /* options to versionCheck */ );
        demo.runit(args[0]);

        /* Make sure patterns in output look ok */
        if (demo.output_contains("ERROR")) {
            System.out.println(
             "NOTE: The jmvti.h file doesn't match the JVMTI in the VM.\n"
            +"      This may or may not be a serious issue.\n"
            +"      Check the jtr file for details.\n"
            +"      Call your local serviceability representative for help."
            );
            throw new RuntimeException("Test failed - ERROR seen in output");
        }

        /* Must be a pass. */
        System.out.println("Test passed - cleanly terminated");
    }
}
