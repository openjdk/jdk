/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary AppCDS handling of directories in -cp
 * @requires vm.cds
 * @library /test/lib
 * @run main DirClasspathTest
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;

public class DirClasspathTest {
    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("user.dir"));
        File emptydir = new File(dir, "emptydir");
        emptydir.mkdir();

        // Empty dir in -cp: should be OK
        OutputAnalyzer output;
        if (!Platform.isWindows()) {
            // This block fails on Windows because of JDK-8192927
            output = TestCommon.dump(emptydir.getPath(), TestCommon.list("DoesntMatter"), "-Xlog:class+path=info");
            TestCommon.checkDump(output);
        }

        // Non-empty dir in -cp: should fail
        // <dir> is not empty because it has at least one subdirectory, i.e., <emptydir>
        output = TestCommon.dump(dir.getPath(), TestCommon.list("DoesntMatter"), "-Xlog:class+path=info");
        output.shouldNotHaveExitValue(0);
        output.shouldContain("CDS allows only empty directories in archived classpaths");
    }
}
