/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8275731
 * @summary Enum objects that are stored in the archived module graph should match
 *          the static final fields in the Enum class.
 * @modules java.management
 * @requires vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build ArchivedEnumApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar ArchivedEnumApp.jar ArchivedEnumApp
 * @run driver ArchivedEnumTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ArchivedEnumTest {
    public static void main(String[] args) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("ArchivedEnumApp.jar");

        OutputAnalyzer out = TestCommon.testDump(appJar,
                                                 TestCommon.list("ArchivedEnumApp"));
        // Note: You can get the following line to fail by commenting out
        // the ADD_EXCL(...) lines in cdsHeapVerifier.cpp
        out.shouldNotContain("object points to a static field that may be reinitialized at runtime");

        TestCommon.run("-cp", appJar,
                       "-Xlog:cds=debug",
                       "-Xlog:cds+heap",
                       "ArchivedEnumApp").assertNormalExit("Success");
    }
}
