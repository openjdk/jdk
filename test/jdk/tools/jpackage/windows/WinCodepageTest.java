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
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinCodepageTest
 */
public class WinCodepageTest {

    @Test
    public static void testFailure() {
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(WinCodepageTest::addFileWithNonAsciiName)
                .addInitializer(cmd -> cmd.saveConsoleOutput(true))
                .setExpectedExitCode(1).addBundleVerifier((cmd, result) -> {
                    TKit.assertTextStream("error LGHT0311")
                            .apply(result.getOutput().stream());
                })
                .run();
    }

    @Test
    public static void testSuccess() {
        new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(WinCodepageTest::addFileWithNonAsciiName)
                .addInitializer(cmd -> cmd.addArguments("--win-codepage", NON_ASCII_FILE_NAME_CODEPAGE))
                .addInstallVerifier(cmd -> {
                    final Path appDir = cmd.pathToUnpackedPackageFile(cmd.appInstallationDirectory());
                    TKit.assertFileExists(appDir.resolve("app").resolve(NON_ASCII_FILE_NAME));
                })
                .run();
    }

    private static void addFileWithNonAsciiName(JPackageCommand cmd) throws IOException {
        Path input = Path.of(cmd.getArgumentValue("--input"));
        Files.createDirectories(input);
        Path helloBgTxt = input.resolve(NON_ASCII_FILE_NAME);
        Files.writeString(helloBgTxt, "hello", StandardCharsets.UTF_8);
    }

    // Hello in Bulgarian
    private static final String NON_ASCII_FILE_NAME = "\u0417\u0434\u0440\u0430\u0432\u0435\u0439\u0442\u0435.txt";
    private static final String NON_ASCII_FILE_NAME_CODEPAGE = "1251";
}
