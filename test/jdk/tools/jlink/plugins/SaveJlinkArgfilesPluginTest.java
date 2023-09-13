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
 */

/*
 * @test
 * @summary Test --save-jlink-argfiles plugin
 * @requires vm.jvmci
 * @library ../../lib
 * @library /test/lib
 * @enablePreview
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.compiler
 * @build tests.*
 * @run main SaveJlinkArgfilesPluginTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.process.ProcessTools;

import tests.Helper;

public class SaveJlinkArgfilesPluginTest {

    public static void main(String[] args) throws Throwable {

        Helper helper = Helper.newHelper();
        if (helper == null) {
            System.err.println("Test not run");
            return;
        }

        String exe = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        Path argfile1 = Path.of("argfile1");
        Path argfile2 = Path.of("argfile2");

        Files.writeString(argfile1, "--add-modules jdk.internal.vm.ci --add-options=-Dfoo=xyzzy");
        Files.writeString(argfile2, "--vendor-version=\"XyzzyVM 3.14.15\" --vendor-bug-url=https://bugs.xyzzy.com/");

        var module = "base";
        helper.generateDefaultJModule(module);
        var image = helper.generateDefaultImage(new String[] {
                "--add-modules", "jdk.jlink,jdk.jdeps,jdk.internal.opt,jdk.compiler,java.compiler,jdk.zipfs,jdk.internal.vm.ci",
                "--keep-packaged-modules", "images/base.image/jmods",
                "--save-jlink-argfiles", argfile1 + File.pathSeparator + argfile2
            }, module)
            .assertSuccess();
        helper.checkImage(image, module, null, null);

        String launcher = image.resolve("bin/java" + exe).toString();
        var oa = ProcessTools.executeProcess(launcher, "-XshowSettings:properties", "--version");

        // Check that the primary image creation ignored the saved args
        oa.shouldHaveExitValue(0);
        oa.shouldNotMatch("java.vendor.url.bug = https://bugs.xyzzy.com/");
        oa.shouldNotMatch("java.vendor.version = XyzzyVM 3.14.15");
        oa.shouldNotMatch("foo = xyzzy");

        // Check that --save-jlink-argfiles fails if jdk.jlink not in the output image
        launcher = image.resolve("bin/jlink" + exe).toString();
        oa = ProcessTools.executeProcess(launcher, "--output=ignore", "--save-jlink-argfiles=" + argfile1);
        oa.shouldHaveExitValue(1);
        oa.stdoutShouldMatch("--save-jlink-argfiles requires jdk.jlink to be in the output image");

        // Create a secondary image
        Path image2 = Path.of("image2").toAbsolutePath();
        launcher = image.resolve("bin/jlink" + exe).toString();
        oa = ProcessTools.executeProcess(launcher, "--output", image2.toString(), "--add-modules=java.base");
        oa.shouldHaveExitValue(0);

        // Ensure the saved `--add-options` and `--vendor-*` options
        // were applied when creating the secondary image.
        launcher = image2.resolve(Path.of("bin", "java" + exe)).toString();
        oa = ProcessTools.executeProcess(launcher, "-XshowSettings:properties", "--version");
        oa.shouldHaveExitValue(0);
        oa.stdoutShouldMatch(" XyzzyVM 3.14.15 ");
        oa.stderrShouldMatch("java.vendor.url.bug = https://bugs.xyzzy.com/");
        oa.stderrShouldMatch("java.vendor.version = XyzzyVM 3.14.15");
        oa.stderrShouldMatch("foo = xyzzy");

        // Ensure the saved `--add-modules` option
        // was applied when creating the secondary image.
        oa = ProcessTools.executeProcess(launcher.toString(), "-d", "jdk.internal.vm.ci");
        oa.shouldHaveExitValue(0);
    }
}
