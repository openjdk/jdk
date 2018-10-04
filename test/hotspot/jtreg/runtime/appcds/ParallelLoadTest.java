/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Load app classes from CDS archive in parallel threads
 * @library /test/lib
 * @requires vm.cds
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/ParallelLoad.java
 * @compile test-classes/ParallelClasses.java
 * @run driver ParallelLoadTest
 */

public class ParallelLoadTest {
    public static final int MAX_CLASSES = 40;

    public static void main(String[] args) throws Exception {
        JarBuilder.build("parallel_load", getClassList(true));
        String appJar = TestCommon.getTestJar("parallel_load.jar");
        TestCommon.test(appJar, getClassList(false), "ParallelLoad");
    }

    private static String[] getClassList(boolean includeWatchdog) {
        int extra = includeWatchdog ? 3 : 2;
        String[] classList = new String[MAX_CLASSES + extra];

        int i;
        for (i=0; i<MAX_CLASSES; i++) {
            classList[i] = "ParallelClass" + i;
        }

        classList[i++] = "ParallelLoad";
        classList[i++] = "ParallelLoadThread";
        if (includeWatchdog)
            classList[i++] = "ParallelLoadWatchdog";

        return classList;
    }
}
