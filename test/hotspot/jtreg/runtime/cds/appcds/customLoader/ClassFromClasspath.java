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
 */

import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Tests that if a class is listed as unregistered it will be archived
 * as such even if it is on classpath (i.e. it will not be loaded by the app
 * loader).
 *
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/ClassFromClasspathApp.java test-classes/CustomLoadee3.java test-classes/CustomLoadee3Child.java
 * @run main ClassFromClasspath
 */
public class ClassFromClasspath {
    public static void main(String[] args) throws Exception {
        final String appJar = JarBuilder.build("app", "ClassFromClasspathApp", "CustomLoadee3", "CustomLoadee3Child");

        final String classlist[] = new String[] {
            "java/lang/Object id: 0",
            "CustomLoadee3 id: 1",
            "CustomLoadee3 id: 2 super: 0 source: " + appJar,
            "CustomLoadee3Child id: 3 super: 2 source: " + appJar
        };
        // Note: if you get a HotSpot crash here in a debug build it is probably because of an
        // assertion in HotSpot CDS code that performs the same check as this test
        TestCommon.testDump(appJar, classlist, "-Xlog:cds+class=debug")
            .shouldContain("app   CustomLoadee3\n")
            .shouldContain("unreg CustomLoadee3\n")
            .shouldContain("unreg CustomLoadee3Child\n");

        final OutputAnalyzer out = TestCommon.exec(appJar, "-Xlog:class+load", "ClassFromClasspathApp");
        TestCommon.checkExec(
            out,
            "CustomLoadee3 source: shared objects file",
            "CustomLoadee3Child source: shared objects file"
        );
    }
}
