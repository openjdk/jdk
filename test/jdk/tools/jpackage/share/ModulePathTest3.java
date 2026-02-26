/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.xml.xpath.XPathExpressionException;
import jdk.jpackage.internal.util.MacBundle;
import jdk.jpackage.test.AppImageFile;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;


/*
 * @test
 * @summary jpackage for app's module linked in external runtime
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror ModulePathTest3.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=ModulePathTest3
 */

public final class ModulePathTest3 {

    public ModulePathTest3(RuntimeType runtimeType) {
        this.runtimeType = Objects.requireNonNull(runtimeType);
    }

    /**
     * Test case for JDK-8248254.
     * App's module in runtime directory.
     */
    @Test
    public void test8248254() throws XPathExpressionException, IOException {
        testIt("me.mymodule/me.mymodule.Main");
    }

    private void testIt(String mainAppDesc) throws XPathExpressionException,
            IOException {
        final JavaAppDesc appDesc = JavaAppDesc.parse(mainAppDesc);
        final Path moduleOutputDir = TKit.createTempDirectory("modules");
        HelloApp.createBundle(appDesc, moduleOutputDir);

        final Path workDir = TKit.createTempDirectory("runtime").resolve("data");
        final Path jlinkOutputDir;
        switch (runtimeType) {
            case IMAGE -> {
                jlinkOutputDir = workDir;
            }
            case MAC_BUNDLE -> {
                var macBundle = new MacBundle(workDir);

                // Create macOS bundle structure sufficient to pass jpackage validation.
                Files.createDirectories(macBundle.homeDir().getParent());
                Files.createDirectories(macBundle.macOsDir());
                Files.createFile(macBundle.infoPlistFile());
                jlinkOutputDir = macBundle.homeDir();
            }
            default -> {
                throw new AssertionError();
            }
        }

        new Executor()
        .setToolProvider(JavaTool.JLINK)
        .dumpOutput()
        .addArguments(
                "--add-modules", appDesc.moduleName(),
                "--output", jlinkOutputDir.toString(),
                "--module-path", moduleOutputDir.resolve(appDesc.jarFileName()).toString(),
                "--strip-debug",
                "--no-header-files",
                "--no-man-pages",
                "--strip-native-commands")
        .execute();

        JPackageCommand cmd = new JPackageCommand()
        .setDefaultAppName()
        .setPackageType(PackageType.IMAGE)
        .setDefaultInputOutput()
        .removeArgumentWithValue("--input")
        .addArguments("--module", appDesc.moduleName() + "/" + appDesc.className())
        .setArgumentValue("--runtime-image", workDir);

        cmd.executeAndAssertHelloAppImageCreated();

        if (appDesc.moduleVersion() != null) {
            String actualVersion = AppImageFile.load(cmd.outputBundle()).version();
            TKit.assertEquals(appDesc.moduleVersion(), actualVersion,
                    "Check application version");
        }
    }

    @Parameters
    public static Collection<Object[]> data() {
        final List<RuntimeType> testCases = new ArrayList<>();
        testCases.add(RuntimeType.IMAGE);
        if (TKit.isOSX()) {
            // On OSX jpackage should accept both runtime root and runtime home
            // directories.
            testCases.add(RuntimeType.MAC_BUNDLE);
        }

        return testCases.stream().map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public enum RuntimeType {
        IMAGE,
        MAC_BUNDLE,
        ;
    }

    private final RuntimeType runtimeType;
}
