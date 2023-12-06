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

package jdk.jpackage.tests;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test jpackage output for erroneous input with --type "app-image" and --app-image
 * @library ../../../../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile PredefinedAppImageErrorTest.java
 *
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.tests.PredefinedAppImageErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useExecutableByDefault
 *
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.tests.PredefinedAppImageErrorTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useToolProviderByDefault
 */

public final class PredefinedAppImageErrorTest {

    private final String expectedError;
    private final JPackageCommand cmd;

    @Parameters
    public static Collection input() throws IOException {
        return List.of(new Object[][]{
            // --mac-sign is required
            {"Hello",
                    null,
                    new String[]{"--input", "--dest", "--name", "--main-jar", "--main-class"},
                    TKit.isOSX() ?
                            "--mac-sign option is required" :
                            "Option [--app-image] is not valid with type [app-image]"
                            },
            // --mac-app-store is required
            {"Hello",
                    new String[]{"--mac-sign", "--mac-app-store", "--mac-app-image-sign-identity", "test"},
                    new String[]{"--input", "--dest", "--name", "--main-jar", "--main-class"},
                    TKit.isOSX() ?
                            "Option [--mac-app-store] is not valid" :
                            "Option [--mac-sign] is not valid on this platform"
                            },
        });
    }

    public PredefinedAppImageErrorTest(String javaAppDesc, String[] jpackageArgs,
                String[] removeArgs,
                String expectedError) {
        this.expectedError = expectedError;

        cmd = JPackageCommand.helloAppImage(javaAppDesc)
                .saveConsoleOutput(true).dumpOutput(true);
        if (jpackageArgs != null) {
            cmd.addArguments(jpackageArgs);
        }
        if (removeArgs != null) {
            for (String arg : removeArgs) {
                cmd.removeArgumentWithValue(arg);
            }
        }
    }

    @Test
    public void test() throws IOException {
        getDummyAppImage(cmd);

        List<String> output = cmd.execute(1).getOutput();
        TKit.assertNotNull(output, "output is null");
        TKit.assertTextStream(expectedError).apply(output.stream());
    }

    private void getDummyAppImage(JPackageCommand cmd) throws IOException {
        Path dummyAppFolder
            = TKit.createTempDirectory("DummyAppImage").toAbsolutePath();

        Path dummyAppFile
            = dummyAppFolder.resolve("DummyAppFile").toAbsolutePath();
        Files.createFile(dummyAppFile);

        cmd.addArguments("--app-image", dummyAppFolder.toString());
        cmd.createJPackageXMLFile("PredefinedAppImageErrorTest", "Hello");
    }

}
