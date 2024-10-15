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
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jdk.jpackage.test.Annotations;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test jpackage command line with overlapping input and output paths
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile InOutPathTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.tests.InOutPathTest
 */
public final class InOutPathTest {

    @Annotations.Parameters
    public static Collection input() {
        List<Object[]> data = new ArrayList<>();

        for (boolean appImage : List.of(true, false)) {
            data.addAll(List.of(new Object[][]{
                {appImage, wrap(InOutPathTest::outputDirInInputDir)},
                {appImage, wrap(InOutPathTest::tempDirInInputDir)},
                {appImage, wrap(cmd -> {
                    outputDirInInputDir(cmd);
                    tempDirInInputDir(cmd);
                })},}));
        }

        return data;
    }

    public InOutPathTest(Boolean appImage, Envelope configure) {
        this.appImage = appImage;
        this.configure = configure.value;
    }

    @Test
    public void test() throws Throwable {
        runTest(appImage, configure);
    }

    private static Envelope wrap(ThrowingConsumer<JPackageCommand> v) {
        return new Envelope(v);
    }

    private static void runTest(boolean appImage,
            ThrowingConsumer<JPackageCommand> configure) throws Throwable {
        ThrowingConsumer<JPackageCommand> configureWrapper = cmd -> {
            cmd.setFakeRuntime();
            configure.accept(cmd);
        };

        if (appImage) {
            JPackageCommand cmd = JPackageCommand.helloAppImage();
            configureWrapper.accept(cmd);
            cmd.executeAndAssertHelloAppImageCreated();
        } else {
            new PackageTest().configureHelloApp().addInitializer(
                    configureWrapper).run();
        }
    }

    private static void outputDirInInputDir(JPackageCommand cmd) throws
            IOException {
        // Set output dir as a subdir of input dir
        Path outputDir = cmd.inputDir().resolve("out");
        TKit.createDirectories(outputDir);
        cmd.setArgumentValue("--dest", outputDir);
    }

    private static void tempDirInInputDir(JPackageCommand cmd) {
        // Set temp dir as a subdir of input dir
        Path tmpDir = cmd.inputDir().resolve("tmp");
        cmd.setArgumentValue("--temp", tmpDir);
    }

    private final static record Envelope(ThrowingConsumer<JPackageCommand> value) {

    }

    private final boolean appImage;
    private final ThrowingConsumer<JPackageCommand> configure;
}
