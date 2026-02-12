/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.JPackageStringBundle.MAIN;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageOutputValidator;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with --resource-dir
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (os.family == "linux")
 * @compile -Xlint:all -Werror LinuxResourceTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=LinuxResourceTest
 */

public class LinuxResourceTest {
    @Test
    public static void testHardcodedProperties() throws IOException {
        new PackageTest()
        .forTypes(PackageType.LINUX)
        .configureHelloApp()
        .addInitializer(cmd -> {
            cmd
            .setFakeRuntime()
            .saveConsoleOutput(true)
            .addArguments("--resource-dir", TKit.createTempDirectory("resources"));
        })
        .forTypes(PackageType.LINUX_DEB)
        .addInitializer(cmd -> {
            Path controlFile = Path.of(cmd.getArgumentValue("--resource-dir"), "control");

            final var packageProp = property("Package", "dont-install-me");
            final var verProp = property("Version", "1.2.3-R2");
            final var arhProp = property("Architecture", "bar");

            TKit.createTextFile(controlFile, List.of(
                    packageProp.format(),
                    verProp.format(),
                    "Section: APPLICATION_SECTION",
                    "Maintainer: APPLICATION_MAINTAINER",
                    "Priority: optional",
                    arhProp.format(),
                    "Provides: dont-install-me",
                    "Description: APPLICATION_DESCRIPTION",
                    "Installed-Size: APPLICATION_INSTALLED_SIZE",
                    "Depends: PACKAGE_DEFAULT_DEPENDENCIES"
            ));

            new JPackageOutputValidator()
                    .expectMatchingStrings(MAIN.cannedFormattedString(
                            "message.using-custom-resource",
                            String.format("[%s]", MAIN.cannedFormattedString("resource.deb-control-file").getValue()),
                            controlFile.getFileName()))
                    .matchTimestamps()
                    .stripTimestamps()
                    .applyTo(cmd);

            packageProp.expectedValue(LinuxHelper.getPackageName(cmd)).token("APPLICATION_PACKAGE").resourceDirFile(controlFile).validateOutput(cmd);
            verProp.expectedValue(cmd.version()).token("APPLICATION_VERSION_WITH_RELEASE").resourceDirFile(controlFile).validateOutput(cmd);
            arhProp.expectedValue(LinuxHelper.getDefaultPackageArch(cmd.packageType())).token("APPLICATION_ARCH").resourceDirFile(controlFile).validateOutput(cmd);
        })
        .forTypes(PackageType.LINUX_RPM)
        .addInitializer(cmd -> {
            Path specFile = Path.of(cmd.getArgumentValue("--resource-dir"),
                    LinuxHelper.getPackageName(cmd) + ".spec");

            final var packageProp = property("Name", "dont-install-me");
            final var verProp = property("Version", "1.2.3");
            final var releaseProp = property("Release", "R2");

            TKit.createTextFile(specFile, List.of(
                    packageProp.format(),
                    verProp.format(),
                    releaseProp.format(),
                    "Summary: APPLICATION_SUMMARY",
                    "License: APPLICATION_LICENSE_TYPE",
                    "Prefix: %{dirname:APPLICATION_DIRECTORY}",
                    "Provides: dont-install-me",
                    "%define _build_id_links none",
                    "%description",
                    "APPLICATION_DESCRIPTION",
                    "%prep",
                    "%build",
                    "%install",
                    "rm -rf %{buildroot}",
                    "install -d -m 755 %{buildroot}APPLICATION_DIRECTORY",
                    "cp -r %{_sourcedir}APPLICATION_DIRECTORY/* %{buildroot}APPLICATION_DIRECTORY",
                    "%files",
                    "APPLICATION_DIRECTORY"
            ));

            new JPackageOutputValidator()
                    .expectMatchingStrings(MAIN.cannedFormattedString(
                            "message.using-custom-resource",
                            String.format("[%s]", MAIN.cannedFormattedString("resource.rpm-spec-file").getValue()),
                            specFile.getFileName()))
                    .matchTimestamps()
                    .stripTimestamps()
                    .applyTo(cmd);

            packageProp.expectedValue(LinuxHelper.getPackageName(cmd)).token("APPLICATION_PACKAGE").resourceDirFile(specFile).validateOutput(cmd);
            verProp.expectedValue(cmd.version()).token("APPLICATION_VERSION").resourceDirFile(specFile).validateOutput(cmd);
            releaseProp.expectedValue("1").token("APPLICATION_RELEASE").resourceDirFile(specFile).validateOutput(cmd);
        })
        .run(Action.CREATE);
    }

    private static final class PropertyValidator {

        PropertyValidator name(String v) {
            name = v;
            return this;
        }

        PropertyValidator customValue(String v) {
            customValue = v;
            return this;
        }

        PropertyValidator expectedValue(String v) {
            expectedValue = v;
            return this;
        }

        PropertyValidator token(String v) {
            token = v;
            return this;
        }

        PropertyValidator resourceDirFile(Path v) {
            resourceDirFile = v;
            return this;
        }

        String format() {
            Objects.requireNonNull(name);
            Objects.requireNonNull(customValue);
            return String.format("%s: %s", name, customValue);
        }

        void validateOutput(JPackageCommand cmd) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(customValue);
            Objects.requireNonNull(expectedValue);
            Objects.requireNonNull(token);
            Objects.requireNonNull(resourceDirFile);

            final var customResourcePath = customResourcePath();

            new JPackageOutputValidator()
                    .expectMatchingStrings(
                            MAIN.cannedFormattedString("error.unexpected-package-property", name, expectedValue, customValue, customResourcePath),
                            MAIN.cannedFormattedString("error.unexpected-package-property.advice", token, customValue, name, customResourcePath)
                    )
                    .matchTimestamps()
                    .stripTimestamps()
                    .applyTo(cmd);
        }

        private Path customResourcePath() {
            return resourceDirFile.getFileName();
        }

        private String name;
        private String customValue;
        private String expectedValue;
        private String token;
        private Path resourceDirFile;
    }

    private static PropertyValidator property(String name, String customValue) {
        return new PropertyValidator().name(name).customValue(customValue);
    }
}
