/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Classes used by CDS at runtime should be in the archived
 * @bug 8324259
 * @requires vm.cds
 * @requires vm.compMode != "Xcomp"
 * @comment Running this test with -Xcomp may load other classes which
 *          are not used in other modes
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @run driver DumpRuntimeClassesTest
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;

public class DumpRuntimeClassesTest {
    public static void main(String[] args) throws Exception {
        // build The app
        String appClass = "Hello";
        String classList = "hello.classlist";
        String archiveName = "hello.jsa";
        JarBuilder.build("hello", appClass);
        String appJar = TestCommon.getTestJar("hello.jar");

        // Dump class list
        CDSTestUtils.dumpClassList(classList, "-cp", appJar, appClass);

        // Dump archive
        CDSOptions opts = (new CDSOptions())
            .addPrefix("-cp", appJar, "-XX:SharedClassListFile=" + classList)
            .setArchiveName(archiveName);
        CDSTestUtils.createArchive(opts);

        // Run with archive and ensure all the classes used were in the archive
        CDSOptions runOpts = (new CDSOptions())
            .addPrefix("-cp", appJar, "-Xlog:class+load,cds=debug")
            .setArchiveName(archiveName)
            .setUseVersion(false)
            .addSuffix(appClass);
        CDSTestUtils.runWithArchive(runOpts)
            .shouldNotContain("source: jrt:/java.base");
    }
}
