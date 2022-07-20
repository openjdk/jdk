/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
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

import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * @test
 * @summary jpackage with --win-codepage
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile WinCodepageTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinCodepageTest
 */
public class WinCodepageTest {

    @Test
    public static void testDefault() {
        // Default codepage is determined by the Codepage attribute in a
        // jpackage primary l10n .wxl file. Primary file is chosen based on
        // user.languages and user.country system properties.
        // Neither of en, de, ja or zh_CN files use 1251 codepage, so the
        // failure below is expected to happen always.
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> addFileWithName(cmd, FILENAME_1251))
                .addInitializer(cmd -> cmd.saveConsoleOutput(true))
                .setExpectedExitCode(1).addBundleVerifier((cmd, result) -> {
                    TKit.assertTextStream("error LGHT0311")
                            .apply(result.getOutput().stream());
                })
                .run();
    }

    @Test
    public static void test1251() {
        // success with 1251 file
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> addFileWithName(cmd, FILENAME_1251))
                .addInitializer(cmd -> cmd.addArguments("--win-codepage", "1251"))
                .addInstallVerifier(cmd -> {
                    final Path appDir = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
                    TKit.assertFileExists(appDir.resolve("app").resolve(FILENAME_1251));
                })
                .run();

        // failure with both 1251 and 1257 files
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    addFileWithName(cmd, FILENAME_1251);
                    addFileWithName(cmd, FILENAME_1257);
                })
                .addInitializer(cmd -> cmd.addArguments("--win-codepage", "1251"))
                .addInitializer(cmd -> cmd.saveConsoleOutput(true))
                .setExpectedExitCode(1).addBundleVerifier((cmd, result) -> {
                    TKit.assertTextStream("error LGHT0311")
                            .apply(result.getOutput().stream());
                })
                .run();
    }

    @Test
    public static void testUtf8() {
        // utf-8 is not officially supported by Windows Installer
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    addFileWithName(cmd, FILENAME_1251);
                    addFileWithName(cmd, FILENAME_1257);
                })
                .addInitializer(cmd -> cmd.addArguments("--win-codepage", "utf-8"))
                .addInstallVerifier(cmd -> {
                    final Path appDir = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
                    TKit.assertFileExists(appDir.resolve("app").resolve(FILENAME_1251));
                    TKit.assertFileExists(appDir.resolve("app").resolve(FILENAME_1257));
                })
                .run();
    }

    private static void addFileWithName(JPackageCommand cmd, String name) throws IOException {
        Path input = Path.of(cmd.getArgumentValue("--input"));
        Files.createDirectories(input);
        Path helloBgTxt = input.resolve(name);
        Files.writeString(helloBgTxt, "hello", StandardCharsets.UTF_8);
    }

    // Hello in Bulgarian
    private static final String FILENAME_1251 = "\u0417\u0434\u0440\u0430\u0432\u0435\u0439\u0442\u0435.txt";
    // Hello in Swedish
    private static final String FILENAME_1257 = "Hall\u00e5.txt";
}
