/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Shared classes can still be used when archived heap regions cannot be
 *          mapped due to out of range, and -Xshare:on should not fail. Test on
 *          linux 64-bit only since the HeapBaseMinAddress value is platform specific.
 *          The value used in the test may cause different behavior on other platforms.
 * @requires vm.cds.archived.java.heap
 * @requires os.family == "linux"
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules java.base/jdk.internal.misc
 * @modules java.management
 *          jdk.jartool/sun.tools.jar
 * @compile ../test-classes/Hello.java
 * @run main RangeNotWithinHeap
 */

import jdk.test.lib.process.OutputAnalyzer;

public class RangeNotWithinHeap {
    public static void main(String[] args) throws Exception {
        JarBuilder.getOrCreateHelloJar();
        String appJar = TestCommon.getTestJar("hello.jar");
        String appClasses[] = TestCommon.list("Hello");

        OutputAnalyzer output = TestCommon.dump(appJar, appClasses,
                    "-XX:HeapBaseMinAddress=0x600000000", "-Xmx6G", "-Xlog:gc+heap=trace");
        TestCommon.checkDump(output, "oa0 space:");

        // Force archive region out of runtime java heap
        output = TestCommon.exec(appJar, "Hello");
        TestCommon.checkExec(output, "Hello World");
        output = TestCommon.exec(appJar,
                    "-XX:HeapBaseMinAddress=0x600000000", "-Xmx2G", "-Xlog:gc+heap=trace,cds", "Hello");
        TestCommon.checkExec(output, "Hello World");
        try {
            output.shouldContain(
                "UseSharedSpaces: Unable to allocate region, range is not within java heap.");
        } catch (Exception e) {
            // In rare case the heap data is not used.
            if (output.getOutput().contains("Cached heap data from the CDS archive is being ignored")) {
                return;
            }
            // Check for common shared class data mapping failures.
            TestCommon.checkCommonExecExceptions(output, e);
        }
    }
}
