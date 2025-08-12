/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with --runtime-image
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror RuntimeImageTest.java
 * @run main/othervm/timeout=1400 jdk.jpackage.test.Main
 *  --jpt-run=RuntimeImageTest
 */

public class RuntimeImageTest {

    @Test
    public static void test() throws IOException {

        JPackageCommand cmd = JPackageCommand.helloAppImage();

        if (JPackageCommand.DEFAULT_RUNTIME_IMAGE == null) {
            final Path workDir = TKit.createTempDirectory("runtime").resolve("data");
            final Path jlinkOutputDir = workDir.resolve("temp.runtime");
            Files.createDirectories(jlinkOutputDir.getParent());

            new Executor()
            .setToolProvider(JavaTool.JLINK)
            .dumpOutput()
            .addArguments(
                    "--output", jlinkOutputDir.toString(),
                    "--add-modules", "java.desktop",
                    "--strip-debug",
                    "--no-header-files",
                    "--no-man-pages",
                    "--strip-native-commands")
            .execute();

            cmd.setArgumentValue("--runtime-image", jlinkOutputDir.toString());
        }

        cmd.executeAndAssertHelloAppImageCreated();
    }

    @Test
    public static void testStrippedFiles() throws IOException {
        final var cmd = JPackageCommand.helloAppImage().setFakeRuntime();

        final var runtimePath = Path.of(cmd.executePrerequisiteActions().getArgumentValue("--runtime-image"));

        Files.createDirectories(runtimePath.resolve("jmods"));
        Files.createDirectories(runtimePath.resolve("lib"));
        Files.createFile(runtimePath.resolve("lib/src.zip"));
        Files.createFile(runtimePath.resolve("src.zip"));

        Files.createDirectories(runtimePath.resolve("foo/bar/src.zip"));
        Files.createFile(runtimePath.resolve("foo/jmods"));
        Files.createFile(runtimePath.resolve("foo/src.zip"));
        Files.createDirectories(runtimePath.resolve("custom/jmods"));

        (new JPackageCommand()).addArguments(cmd.getAllArguments()).executeAndAssertHelloAppImageCreated();

        final var appRuntimeDir = cmd.appLayout().runtimeHomeDirectory();
        TKit.assertPathExists(appRuntimeDir.resolve("jmods"), false);
        TKit.assertPathExists(appRuntimeDir.resolve("lib/src.zip"), false);
        TKit.assertPathExists(appRuntimeDir.resolve("src.zip"), false);
        TKit.assertDirectoryExists(appRuntimeDir.resolve("foo/bar/src.zip"));
        TKit.assertDirectoryExists(appRuntimeDir.resolve("custom/jmods"));
        TKit.assertFileExists(appRuntimeDir.resolve("foo/jmods"));
        TKit.assertFileExists(appRuntimeDir.resolve("foo/src.zip"));
    }
}
