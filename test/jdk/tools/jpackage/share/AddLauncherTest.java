/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.lang.invoke.MethodHandles;
import jdk.jpackage.test.*;
import jdk.jpackage.test.Annotations.*;

/**
 * Test --add-launcher parameter. Output of the test should be
 * AddLauncherTest*.* installer. The output installer should provide the
 * same functionality as the default installer (see description of the default
 * installer in SimplePackageTest.java) plus install three extra application
 * launchers.
 */

/*
 * @test
 * @summary jpackage with --add-launcher
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest != null)
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile AddLauncherTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AddLauncherTest.test
 */

/*
 * @test
 * @summary jpackage with --add-launcher
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest == null)
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile AddLauncherTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AddLauncherTest
 */

public class AddLauncherTest {

    @Test
    public void test() {
        // Configure a bunch of additional launchers and also setup
        // file association to make sure it will be linked only to the main
        // launcher.

        PackageTest packageTest = new PackageTest().configureHelloApp();
        packageTest.addInitializer(cmd -> {
            cmd.addArguments("--arguments", "Duke", "--arguments", "is",
                    "--arguments", "the", "--arguments", "King");
        });

        new FileAssociations(
                MethodHandles.lookup().lookupClass().getSimpleName()).applyTo(
                packageTest);

        new AdditionalLauncher("Baz2")
                .setDefaultArguments()
                .applyTo(packageTest);

        new AdditionalLauncher("foo")
                .setDefaultArguments("yep!")
                .applyTo(packageTest);

        new AdditionalLauncher("Bar")
                .setDefaultArguments("one", "two", "three")
                .setIcon(GOLDEN_ICON)
                .applyTo(packageTest);

        packageTest.run();
    }

    @Test
    public void bug8230933() {
        PackageTest packageTest = new PackageTest().configureHelloApp();

        new AdditionalLauncher("default_icon")
                .applyTo(packageTest);

        new AdditionalLauncher("no_icon")
                .setNoIcon().applyTo(packageTest);

        new AdditionalLauncher("custom_icon")
                .setIcon(GOLDEN_ICON)
                .applyTo(packageTest);

        packageTest.run();
    }

    @Test
    // Regular app
    @Parameter("Hello")
    // Modular app
    @Parameter("com.other/com.other.CiaoBella")
    public void testJavaOptions(String javaAppDesc) {
        JPackageCommand cmd = JPackageCommand.helloAppImage(javaAppDesc)
        .addArguments("--arguments", "courageous")
        .addArguments("--java-options", "-Dparam1=xxx")
        .addArguments("--java-options", "-Dparam2=yyy")
        .addArguments("--java-options", "-Dparam3=zzz");

        new AdditionalLauncher("Jack")
                .addDefaultArguments("Jack of All Trades", "Master of None")
                .setJavaOptions("-Dparam1=Contractor")
                .applyTo(cmd);

        new AdditionalLauncher("Monday")
                .addDefaultArguments("Knock Your", "Socks Off")
                .setJavaOptions("-Dparam2=Surprise workers!")
                .applyTo(cmd);

        // Should inherit default arguments and java options from the main launcher
        new AdditionalLauncher("void").applyTo(cmd);

        cmd.executeAndAssertHelloAppImageCreated();
    }

    /**
     * Test usage of modular and non modular apps in additional launchers.
     */
    @Test
    @Parameter("true")
    @Parameter("fase")
    public void testMainLauncherIsModular(boolean mainLauncherIsModular) {
        final var nonModularAppDesc = JavaAppDesc.parse("a.b.c.Hello");
        final var modularAppDesc = JavaAppDesc.parse(
                "module.jar:com.that/com.that.main.Florence");

        final var nonModularJarCmd = JPackageCommand.helloAppImage(nonModularAppDesc);
        final var modularJarCmd = JPackageCommand.helloAppImage(modularAppDesc);

        final JPackageCommand cmd;
        if (mainLauncherIsModular) {
            // Create non modular jar.
            nonModularJarCmd.executePrerequisiteActions();

            cmd = modularJarCmd;
            cmd.addArguments("--description",
                    "Test modular app with multiple add-launchers where one is modular app and other is non modular app");
            cmd.addArguments("--input", nonModularJarCmd.getArgumentValue(
                    "--input"));
        } else {
            // Create modular jar.
            modularJarCmd.executePrerequisiteActions();

            cmd = nonModularJarCmd;
            cmd.addArguments("--description",
                    "Test non modular app with multiple add-launchers where one is modular app and other is non modular app");
            cmd.addArguments("--module-path", modularJarCmd.getArgumentValue(
                    "--module-path"));
            cmd.addArguments("--add-modules", modularAppDesc.moduleName());
            cmd.ignoreDefaultRuntime(true); // because of --add-modules
        }

        new AdditionalLauncher("ModularAppLauncher")
        .addRawProperties(Map.entry("module", JavaAppDesc.parse(
                modularAppDesc.toString()).setBundleFileName(null).toString()))
        .addRawProperties(Map.entry("main-jar", ""))
        .applyTo(cmd);

        new AdditionalLauncher("NonModularAppLauncher")
        // Use space ( ) character instead of equality sign (=) as
        // a key/value separator
        .setPersistenceHandler((path, properties) -> TKit.createTextFile(path,
                properties.stream().map(entry -> String.join(" ", entry.getKey(),
                        entry.getValue()))))
        .addRawProperties(Map.entry("main-class", nonModularAppDesc.className()))
        .addRawProperties(Map.entry("main-jar", nonModularAppDesc.jarFileName()))
        .applyTo(cmd);

        cmd.executeAndAssertHelloAppImageCreated();
    }

    private final static Path GOLDEN_ICON = TKit.TEST_SRC_ROOT.resolve(Path.of(
            "resources", "icon" + TKit.ICON_SUFFIX));
}
