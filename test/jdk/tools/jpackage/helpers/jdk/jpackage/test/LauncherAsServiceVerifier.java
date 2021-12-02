/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;


import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;


public final class LauncherAsServiceVerifier {

    public LauncherAsServiceVerifier(String expectedArgValue) {
        this(null, "launcher-as-service.txt", expectedArgValue);
    }

    public LauncherAsServiceVerifier(String launcherName, String appOutputFileName,
            String expectedArgValue) {
        this.expectedValue = expectedArgValue;
        this.launcherName = launcherName;
        this.appOutputPath = appOutputDir().resolve(appOutputFileName);
    }

    public void applyTo(PackageTest pkg) {
        if (launcherName == null) {
            pkg.forTypes(PackageType.WINDOWS, () -> {
                pkg.addInitializer(cmd -> {
                    cmd.removeArgument("--win-console");
                });
            });
            applyToMainLauncher(pkg);
        } else {
            applyToAdditionalLauncher(pkg);
        }
    }

    private boolean canVerifyInstall(JPackageCommand cmd) throws IOException {
        String msg = String.format("Not verifying contents of test output file [%s] for %s launcher",
                appOutputPath,
                Optional.ofNullable(launcherName).orElse("the main"));
        if (cmd.isPackageUnpacked(msg)) {
            return false;
        }
        jdk.jpackage.test.CfgFile cfgFile = CfgFile.readFromFile(cmd.appLauncherCfgPath(launcherName));
        if (!expectedValue.equals(cfgFile.getValueUnchecked("ArgOptions",
                "arguments"))) {
            TKit.trace(String.format("%s because different version of the package is installed",
                    msg));
            return false;
        }
        return true;
    }

    private void applyToMainLauncher(PackageTest pkg) {
        pkg.addInitializer(cmd -> {
            cmd.addArgument("--launcher-as-service");
            cmd.addArguments("--arguments",
                    JPackageCommand.escapeAndJoin(expectedValue));
            cmd.addArguments("--java-options",
                    "-Djpackage.test.appOutput=" + appOutputPath.toString());
            cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        });
        pkg.addInstallVerifier(cmd -> {
            if (canVerifyInstall(cmd)) {
                delayInstallVerify();
                HelloApp.assertApp(cmd.appLauncherPath()).addParam("jpackage.test.appOutput",
                        appOutputPath.toString()).addDefaultArguments(expectedValue).verifyOutput();
            }
        });
    }

    private void applyToAdditionalLauncher(PackageTest pkg) {
        new AdditionalLauncher(launcherName) {
            @Override
            protected void verify(JPackageCommand cmd) throws IOException {
                if (canVerifyInstall(cmd)) {
                    delayInstallVerify();
                    super.verify(cmd);
                }
            }
        }.setLauncherAsService()
        .addJavaOptions("-Djpackage.test.appOutput=" + appOutputPath.toString())
        .addJavaOptions("-Djpackage.test.noexit=true")
        .addDefaultArguments(expectedValue)
        .applyTo(pkg);
    }

    private static void delayInstallVerify() {
        // Sleep a bit to let system launch the service
        Functional.ThrowingRunnable.toRunnable(() -> Thread.sleep(5 * 1000)).run();
    }

    private static Path appOutputDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    private final String expectedValue;
    private final String launcherName;
    private final Path appOutputPath;
}
