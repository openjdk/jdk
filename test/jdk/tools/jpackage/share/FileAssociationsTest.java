/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.Map.entry;

import java.nio.file.Path;
import java.util.List;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/**
 * Test --file-associations parameter. Output of the test should be
 * fileassociationstest*.* installer. The output installer should provide the
 * same functionality as the default installer (see description of the default
 * installer in SimplePackageTest.java) plus configure file associations. After
 * installation files with ".jptest1" and ".jptest2" suffixes should be
 * associated with the test app.
 *
 * Suggested test scenario is to create empty file with ".jptest1" suffix,
 * double click on it and make sure that test application was launched in
 * response to double click event with the path to test .jptest1 file on the
 * commend line. The same applies to ".jptest2" suffix.
 *
 * On Linux use "echo > foo.jptest1" and not "touch foo.jptest1" to create test
 * file as empty files are always interpreted as plain text and will not be
 * opened with the test app. This is a known bug.
 *
 * Icon associated with the main launcher should be associated with files with
 * ".jptest1" suffix. Different icon should be associated with files with with
 * ".jptest2" suffix. Icon for files with ".jptest1" suffix is platform specific
 * and is one of 'icon.*' files in test/jdk/tools/jpackage/resources directory.
 */

/*
 * @test
 * @summary jpackage with --file-associations
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires jpackage.test.SQETest == null
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror FileAssociationsTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=FileAssociationsTest
 */

/*
 * @test
 * @summary jpackage with --file-associations
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires jpackage.test.SQETest != null
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror FileAssociationsTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=FileAssociationsTest.test
 */

public class FileAssociationsTest {
    @Test
    @Parameter("true")
    @Parameter("false")
    public static void test(boolean includeDescription) {
        PackageTest packageTest = new PackageTest();

        // Not supported
        packageTest.excludeTypes(PackageType.MAC_DMG);

        FileAssociations fa = new FileAssociations("jptest1");
        if (!includeDescription) {
            fa.setDescription(null);
        }
        fa.applyTo(packageTest);

        Path icon = TKit.TEST_SRC_ROOT.resolve(Path.of("resources", "icon"
                + TKit.ICON_SUFFIX));

        icon = TKit.createRelativePathCopy(icon);

        new FileAssociations("jptest2")
                .setFilename("fa2")
                .setIcon(icon)
                .applyTo(packageTest);

        packageTest.run();
    }

    @Test
    public static void testNoMime() {
        final Path propFile = TKit.workDir().resolve("fa.properties");

        initPackageTest().addRunOnceInitializer(() -> {
            TKit.createPropertiesFile(propFile, List.of(
                    entry("extension", "foo"),
                    entry("description", "bar")
            ));
        }).addInitializer(cmd -> {
            cmd.addArguments("--file-associations", propFile);
            cmd.validateErr(
                    JPackageCommand.makeError("error.no-content-types-for-file-association", 1),
                    JPackageCommand.makeAdvice("error.no-content-types-for-file-association.advice", 1));
        }).run();
    }

    @Test
    public static void testTooManyMimes() {
        final Path propFile = TKit.workDir().resolve("fa.properties");

        initPackageTest().addRunOnceInitializer(() -> {
            TKit.createPropertiesFile(propFile, List.of(
                    entry("mime-type", "application/x-jpackage-foo, application/x-jpackage-bar"),
                    entry("extension", "foo"),
                    entry("description", "bar")
            ));
        }).addInitializer(cmd -> {
            cmd.addArguments("--file-associations", propFile);
            cmd.validateErr(
                    JPackageCommand.makeError("error.too-many-content-types-for-file-association", 1),
                    JPackageCommand.makeAdvice("error.too-many-content-types-for-file-association.advice", 1));
        }).run();
    }

    private static PackageTest initPackageTest() {
        return new PackageTest()
                .excludeTypes(PackageType.MAC)
                .configureHelloApp()
                .addInitializer(JPackageCommand::setFakeRuntime)
                .setExpectedExitCode(1);
    }
}
