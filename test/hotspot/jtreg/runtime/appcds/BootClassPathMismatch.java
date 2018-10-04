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
 * @summary bootclasspath mismatch test.
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/Hello.java
 * @run driver BootClassPathMismatch
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;


public class BootClassPathMismatch {
    private static final String mismatchMessage = "shared class paths mismatch";

    public static void main(String[] args) throws Exception {
        JarBuilder.getOrCreateHelloJar();
        copyHelloToNewDir();

        BootClassPathMismatch test = new BootClassPathMismatch();
        test.testBootClassPathMismatch();
        test.testBootClassPathMismatchWithAppClass();
        test.testBootClassPathMismatchWithBadPath();
        test.testBootClassPathMatchWithAppend();
        test.testBootClassPathMatch();
    }

    /* Archive contains boot classes only, with Hello class on -Xbootclasspath/a path.
     *
     * Error should be detected if:
     * dump time: -Xbootclasspath/a:${testdir}/hello.jar
     * run-time : -Xbootclasspath/a:${testdir}/newdir/hello.jar
     *
     * or
     * dump time: -Xbootclasspath/a:${testdir}/newdir/hello.jar
     * run-time : -Xbootclasspath/a:${testdir}/hello.jar
     */
    public void testBootClassPathMismatch() throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String appClasses[] = {"Hello"};
        String testDir = TestCommon.getTestDir("newdir");
        String otherJar = testDir + File.separator + "hello.jar";

        TestCommon.dump(appJar, appClasses, "-Xbootclasspath/a:" + appJar);
        TestCommon.run(
                "-cp", appJar, "-Xbootclasspath/a:" + otherJar, "Hello")
            .assertAbnormalExit(mismatchMessage);

        TestCommon.dump(appJar, appClasses, "-Xbootclasspath/a:" + otherJar);
        TestCommon.run(
                "-cp", appJar, "-Xbootclasspath/a:" + appJar, "Hello")
            .assertAbnormalExit(mismatchMessage);
    }

    /* Archive contains boot classes only.
     *
     * Error should be detected if:
     * dump time: -Xbootclasspath/a:${testdir}/newdir/hello.jar
     * run-time : -Xbootclasspath/a:${testdir}/newdir/hello.jar1
     */
    public void testBootClassPathMismatchWithBadPath() throws Exception {
        String appClasses[] = {"Hello"};
        String testDir = TestCommon.getTestDir("newdir");
        String appJar = testDir + File.separator + "hello.jar";
        String otherJar = testDir + File.separator + "hello.jar1";

        TestCommon.dump(appJar, appClasses, "-Xbootclasspath/a:" + appJar);
        TestCommon.run(
                "-cp", appJar, "-Xbootclasspath/a:" + otherJar, "Hello")
            .assertAbnormalExit(mismatchMessage);
    }

    /* Archive contains boot classes only, with Hello loaded from -Xbootclasspath/a at dump time.
     *
     * No error if:
     * dump time: -Xbootclasspath/a:${testdir}/hello.jar
     * run-time : -Xbootclasspath/a:${testdir}/hello.jar
     */
    public void testBootClassPathMatch() throws Exception {
        String appJar = TestCommon.getTestJar("hello.jar");
        String appClasses[] = {"Hello"};
        TestCommon.dump(
            appJar, appClasses, "-Xbootclasspath/a:" + appJar);
        TestCommon.run(
                "-cp", appJar, "-verbose:class",
                "-Xbootclasspath/a:" + appJar, "Hello")
            .assertNormalExit("[class,load] Hello source: shared objects file");
    }

    /* Archive contains boot classes only, runtime add -Xbootclasspath/a path.
     *
     * No error:
     * dump time: No -Xbootclasspath/a
     * run-time : -Xbootclasspath/a:${testdir}/hello.jar
     */
    public void testBootClassPathMatchWithAppend() throws Exception {
      CDSOptions opts = new CDSOptions().setUseVersion(false);
      OutputAnalyzer out = CDSTestUtils.createArchive(opts);
      CDSTestUtils.checkDump(out);

      String appJar = JarBuilder.getOrCreateHelloJar();
      opts.addPrefix("-Xbootclasspath/a:" + appJar, "-showversion").addSuffix("Hello");
      CDSTestUtils.runWithArchiveAndCheck(opts);
    }

    /* Archive contains app classes, with Hello on -cp path at dump time.
     *
     * Error should be detected if:
     * dump time: <no bootclasspath specified>
     * run-time : -Xbootclasspath/a:${testdir}/hello.jar
     */
    public void testBootClassPathMismatchWithAppClass() throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();
        String appClasses[] = {"Hello"};
        TestCommon.dump(appJar, appClasses);
        TestCommon.run(
                "-cp", appJar, "-Xbootclasspath/a:" + appJar, "Hello")
            .assertAbnormalExit(mismatchMessage);
    }

    private static void copyHelloToNewDir() throws Exception {
        String classDir = System.getProperty("test.classes");
        String dstDir = classDir + File.separator + "newdir";
        try {
            Files.createDirectory(Paths.get(dstDir));
        } catch (FileAlreadyExistsException e) { }

        // copy as hello.jar
        Files.copy(Paths.get(classDir, "hello.jar"),
            Paths.get(dstDir, "hello.jar"),
            StandardCopyOption.REPLACE_EXISTING);

        // copy as hello.jar1
        Files.copy(Paths.get(classDir, "hello.jar"),
            Paths.get(dstDir, "hello.jar1"),
            StandardCopyOption.REPLACE_EXISTING);
    }
}
