/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/**
 * Test packaging of files with paths containing multiple dollar ($$, $$$)
 * character sequences.
 */

/*
 * @test
 * @summary Test case for JDK-8248254
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build Win8282351Test
 * @requires (os.family == "windows")
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=Win8282351Test
 */
public class Win8282351Test {

    @Test
    public void test() throws IOException {
        Path appimageOutput = TKit.createTempDirectory("appimage");

        JPackageCommand appImageCmd = JPackageCommand.helloAppImage()
                .setFakeRuntime().setArgumentValue("--dest", appimageOutput);

        String[] filesWithDollarCharsInNames = new String[]{
            "Pane$$anon$$greater$1.class",
            "$",
            "$$",
            "$$$",
            "$$$$",
            "$$$$$",
            "foo$.class",
            "1$b$$a$$$r$$$$.class"
        };

        String[] dirsWithDollarCharsInNames = new String[]{
            Path.of("foo", String.join("/", filesWithDollarCharsInNames)).toString()
        };

        String name = appImageCmd.name() + "$-$$-$$$";

        new PackageTest()
                .addRunOnceInitializer(() -> {
                    appImageCmd.execute();
                    for (var path : filesWithDollarCharsInNames) {
                        createImageFile(appImageCmd, Path.of(path));
                    }

                    for (var path : dirsWithDollarCharsInNames) {
                        Files.createDirectories(
                                appImageCmd.outputBundle().resolve(path));
                    }
                })
                .addInitializer(cmd -> {
                    cmd.setArgumentValue("--name", name);
                    cmd.addArguments("--app-image", appImageCmd.outputBundle());
                    cmd.removeArgumentWithValue("--input");
                    cmd.addArgument("--win-menu");
                    cmd.addArgument("--win-shortcut");
                })
                .addInstallVerifier(cmd -> {
                    for (var path : filesWithDollarCharsInNames) {
                        verifyImageFile(appImageCmd, Path.of(path));
                    }

                    for (var path : dirsWithDollarCharsInNames) {
                        TKit.assertDirectoryExists(
                                appImageCmd.outputBundle().resolve(path));
                    }
                }).run(Action.CREATE_AND_UNPACK);
    }

    private static void createImageFile(JPackageCommand cmd, Path name) throws
            IOException {
        Files.writeString(cmd.outputBundle().resolve(name), name.toString());
    }

    private static void verifyImageFile(JPackageCommand cmd, Path name) throws
            IOException {
        TKit.assertEquals(name.toString(), Files.readString(
                (cmd.outputBundle().resolve(name))), String.format(
                "Test contents of [%s] image file are euqal to [%s]", name, name));
    }
}
