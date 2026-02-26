/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.spi.ToolProvider;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test how jpackage handles errors writing output bundle
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror OutputErrorTest.java
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=OutputErrorTest
 */

public final class OutputErrorTest {

    @Test
    @Parameter("DIR")
    // "Locked file error" reliably works only on Windows
    @Parameter(value = "LOCKED_FILE", ifOS = OperatingSystem.WINDOWS)
    public void testPackage(ExistingOutputBundleType existingOutputBundleType) {

        new PackageTest().configureHelloApp().addInitializer(cmd -> {

            cmd.setFakeRuntime();
            cmd.setArgumentValue("--dest", TKit.createTempDirectory("output"));
            cmd.removeOldOutputBundle(false);
            cmd.validateErr(JPackageCommand.makeError(
                    "error.output-bundle-cannot-be-overwritten", cmd.outputBundle().toAbsolutePath()));

            var outputBundle = cmd.outputBundle();

            switch (existingOutputBundleType) {
                case DIR -> {
                    Files.createDirectories(outputBundle);
                    Files.writeString(outputBundle.resolve("foo.txt"), "Hello");
                }
                case LOCKED_FILE -> {
                    Files.writeString(outputBundle, "Hello");
                    cmd.useToolProvider(createToolProviderWithLockedFile(
                            JavaTool.JPACKAGE.asToolProvider(), outputBundle));
                }
            }

        }).setExpectedExitCode(1).run();
    }

    enum ExistingOutputBundleType {
        DIR,
        LOCKED_FILE,
        ;
    }

    private static ToolProvider createToolProviderWithLockedFile(ToolProvider tp, Path lockedFile) {
        Objects.requireNonNull(tp);
        if (!Files.isRegularFile(lockedFile)) {
            throw new IllegalArgumentException();
        }

        return new ToolProvider() {

            @Override
            public String name() {
                return "jpackage-mock";
            }

            @SuppressWarnings("try")
            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                try {
                    var lastModifiedTime = Files.getLastModifiedTime(lockedFile);
                    try (var fos = new FileOutputStream(lockedFile.toFile()); var lock = fos.getChannel().lock()) {
                        Files.setLastModifiedTime(lockedFile, lastModifiedTime);
                        return tp.run(out, err, args);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        };
    }
}
