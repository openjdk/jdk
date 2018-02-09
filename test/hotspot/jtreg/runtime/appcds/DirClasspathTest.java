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
 * @summary AppCDS handling of directories in -cp
 * @requires vm.cds
 * @library /test/lib
 * @run main DirClasspathTest
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class DirClasspathTest {
    private static final int MAX_PATH = 260;

    public static void main(String[] args) throws Exception {
        File dir = new File(System.getProperty("user.dir"));
        File emptydir = new File(dir, "emptydir");
        emptydir.mkdir();

        // Empty dir in -cp: should be OK
        OutputAnalyzer output;
        String classList[] = {"java/lang/Object"};
        output = TestCommon.dump(emptydir.getPath(), classList, "-Xlog:class+path=info");
        TestCommon.checkDump(output);

        // Long path to empty dir in -cp: should be OK
        Path classDir = Paths.get(System.getProperty("test.classes"));
        Path destDir = classDir;
        int subDirLen = MAX_PATH - classDir.toString().length() - 2;
        if (subDirLen > 0) {
            char[] chars = new char[subDirLen];
            Arrays.fill(chars, 'x');
            String subPath = new String(chars);
            destDir = Paths.get(System.getProperty("test.classes"), subPath);
        }
        File longDir = destDir.toFile();
        longDir.mkdir();
        File subDir = new File(longDir, "subdir");
        subDir.mkdir();
        output = TestCommon.dump(subDir.getPath(), classList, "-Xlog:class+path=info");
        TestCommon.checkDump(output);

        // Non-empty dir in -cp: should fail
        // <dir> is not empty because it has at least one subdirectory, i.e., <emptydir>
        output = TestCommon.dump(dir.getPath(), classList, "-Xlog:class+path=info");
        output.shouldNotHaveExitValue(0);
        output.shouldContain("CDS allows only empty directories in archived classpaths");

        // Long path to non-empty dir in -cp: should fail
        // <dir> is not empty because it has at least one subdirectory, i.e., <emptydir>
        output = TestCommon.dump(longDir.getPath(), classList, "-Xlog:class+path=info");
        output.shouldNotHaveExitValue(0);
        output.shouldContain("CDS allows only empty directories in archived classpaths");
    }
}
