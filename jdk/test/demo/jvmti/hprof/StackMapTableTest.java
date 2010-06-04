/*
 * Copyright (c) 2005, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6266289 6299047 6855180 6855551
 * @summary Test jvmti hprof and java_crw_demo with StackMapTable attributes
 *
 * @compile ../DemoRun.java
 * @compile -source 7 -g:lines HelloWorld.java
 * @build StackMapTableTest
 * @run main StackMapTableTest HelloWorld
 */

import java.util.*;

public class StackMapTableTest {

    public static void main(String args[]) throws Exception {
        DemoRun hprof;
        List<String> options = new LinkedList<String>();

        options.add("cpu=samples");
        options.add("cpu=times");
        options.add("heap=sites");
        options.add("");

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
