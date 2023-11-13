/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.Functional;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.Executor;

/**
 * Test --runtime-image parameter with runtime image containing symbolic links.
 * This test only for macOS and Linux.
 */

/*
 * @test
 * @summary jpackage with --runtime-image
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @requires (os.family != "windows")
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile RuntimeImageSymbolicLinksTest.java
 * @run main/othervm/timeout=1400 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=RuntimeImageSymbolicLinksTest
 */

public class RuntimeImageSymbolicLinksTest {

    @Test
    public static void test() throws Exception {
        final Path workDir = TKit.createTempDirectory("runtime").resolve("data");
        final Path jlinkOutputDir = workDir.resolve("temp.runtime");
        Files.createDirectories(jlinkOutputDir.getParent());

        new Executor()
        .setToolProvider(JavaTool.JLINK)
        .dumpOutput()
        .addArguments(
                "--output", jlinkOutputDir.toString(),
                "--add-modules", "ALL-MODULE-PATH",
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--strip-native-commands")
        .execute();

        // Add symbolic links to generated runtime image
        // Release file
        Path releaseLink = jlinkOutputDir.resolve("releaseLink");
        Path releaseTarget = Path.of("release");
        TKit.assertFileExists(jlinkOutputDir.resolve("release"));
        Files.createSymbolicLink(releaseLink, releaseTarget);
        // Legal directory
        Path legalLink = jlinkOutputDir.resolve("legalLink");
        Path legalTarget = Path.of("legal");
        TKit.assertDirectoryExists(jlinkOutputDir.resolve("legal"));
        Files.createSymbolicLink(legalLink, legalTarget);

        JPackageCommand cmd = JPackageCommand.helloAppImage()
            .setArgumentValue("--runtime-image", jlinkOutputDir.toString());

        cmd.executeAndAssertHelloAppImageCreated();

        ApplicationLayout appLayout = cmd.appLayout();
        Path runtimeDir = appLayout.runtimeHomeDirectory();

        // Make sure that links are exist
        releaseLink = runtimeDir.resolve("releaseLink");
        TKit.assertSymbolicLinkExists(releaseLink);
        legalLink = runtimeDir.resolve("legalLink");
        TKit.assertSymbolicLinkExists(legalLink);
    }

}
