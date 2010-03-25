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
 * @bug 5083441 6299047
 * @summary Test jvmti hprof
 *
 * @compile -g:lines HelloWorld.java ../DemoRun.java
 * @build OptionsTest
 * @run main OptionsTest HelloWorld
 */

import java.util.*;

public class OptionsTest {

    public static void main(String args[]) throws Exception {
        DemoRun hprof;
        List<String> options = new LinkedList<String>();

        options.add("cpu=samples,depth=0");
        options.add("cpu=times,depth=0");
        options.add("cpu=old,depth=0");
        options.add("depth=0");

        for(String option: options) {
            /* Run JVMTI hprof agent with various options */
            hprof = new DemoRun("hprof", option);
            hprof.runit(args[0]);

            /* Make sure patterns in output look ok */
            if (hprof.output_contains("ERROR")) {
                throw new RuntimeException("Test failed with " + option
                                           + " - ERROR seen in output");
            }
        }

        /* Must be a pass. */
        System.out.println("Test passed - cleanly terminated");
    }
}
