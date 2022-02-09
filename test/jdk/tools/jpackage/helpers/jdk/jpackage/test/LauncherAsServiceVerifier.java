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
package jdk.jpackage.test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import jdk.jpackage.internal.IOUtils;

public final class LauncherAsServiceVerifier {

    public final static class Builder {

        public Builder setExpectedValue(String v) {
            expectedValue = v;
            return this;
        }

        public Builder setLauncherName(String v) {
            launcherName = v;
            return this;
        }

        public Builder setAppOutputFileName(String v) {
            appOutputFileName = v;
            return this;
        }

        public LauncherAsServiceVerifier create() {
            Objects.requireNonNull(expectedValue);
            return new LauncherAsServiceVerifier(launcherName, appOutputFileName,
                    expectedValue);
        }

        public Builder applyTo(PackageTest pkg) {
            create().applyTo(pkg);
            return this;
        }

        private String launcherName;
        private String expectedValue;
        private String appOutputFileName = "launcher-as-service.txt";
    }

    public static Builder build() {
        return new Builder();
    }

    private LauncherAsServiceVerifier(String launcherName,
            String appOutputFileName,
            String expectedArgValue) {
        this.expectedValue = expectedArgValue;
        this.launcherName = launcherName;
        this.appOutputPath = appOutputDir().resolve(appOutputFileName);
    }

    public void applyTo(PackageTest pkg) {
        if (launcherName == null) {
            pkg.forTypes(PackageType.WINDOWS, () -> {
                pkg.addInitializer(cmd -> {
                    // Remove parameter added to jpackage command line in HelloApp.addTo()
                    cmd.removeArgument("--win-console");
                });
            });
            applyToMainLauncher(pkg);
        } else {
            applyToAdditionalLauncher(pkg);
        }
    }

    static List<String> getLaunchersAsServices(JPackageCommand cmd) {
        List<String> launcherNames = new ArrayList<>();

        if (cmd.hasArgument("--launcher-as-service")) {
            launcherNames.add(null);
        }

        AdditionalLauncher.forEachAdditionalLauncher(cmd,
                Functional.ThrowingBiConsumer.toBiConsumer(
                        (launcherName, propFilePath) -> {
                            if (Files.readAllLines(propFilePath).stream().anyMatch(
                                    "launcher-as-service"::equals)) {
                                launcherNames.add(launcherName);
                            }
                        }));

        return launcherNames;
    }

    private boolean canVerifyInstall(JPackageCommand cmd) throws IOException {
        String msg = String.format(
                "Not verifying contents of test output file [%s] for %s launcher",
                appOutputPath,
                Optional.ofNullable(launcherName).orElse("the main"));
        if (cmd.isPackageUnpacked(msg)) {
            return false;
        }
        var cfgFile = CfgFile.readFromFile(cmd.appLauncherCfgPath(launcherName));
        if (!expectedValue.equals(cfgFile.getValueUnchecked("ArgOptions",
                "arguments"))) {
            TKit.trace(String.format(
                    "%s because different version of the package is installed",
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
                HelloApp.assertApp(cmd.appLauncherPath())
                        .addParam("jpackage.test.appOutput", appOutputPath.toString())
                        .addDefaultArguments(expectedValue)
                        .verifyOutput();
            }
        });
        pkg.addInstallVerifier(this::verify);
    }

    private void applyToAdditionalLauncher(PackageTest pkg) {
        new AdditionalLauncher(launcherName) {
            @Override
            protected void verify(JPackageCommand cmd) throws IOException {
                if (canVerifyInstall(cmd)) {
                    delayInstallVerify();
                    super.verify(cmd);
                }
                LauncherAsServiceVerifier.this.verify(cmd);
            }
        }.setLauncherAsService()
        .addJavaOptions("-Djpackage.test.appOutput=" + appOutputPath.toString())
        .addJavaOptions("-Djpackage.test.noexit=true")
        .addDefaultArguments(expectedValue)
        .applyTo(pkg);
    }

    private void verify(JPackageCommand cmd) throws IOException {
        if (TKit.isWindows()) {
            TKit.assertFileExists(cmd.appLayout().launchersDirectory().resolve(
                    "service-installer.exe"));
        } else {
            var installedLauncherPath = Optional.ofNullable(
                    cmd.unpackedPackageDirectory()).map(
                    path -> (UnaryOperator<Path>) (v -> {
                        return Path.of("/").resolve(path.relativize(v));
                    })).orElseGet(UnaryOperator::identity).apply(
                    cmd.appLauncherPath(launcherName));
            if (TKit.isLinux()) {
                verifyLinuxUnitFile(cmd, installedLauncherPath);
            } else if (TKit.isOSX()) {
                verifyMacDaemonPlistFile(cmd, installedLauncherPath);
            }
        }
    }

    private void verifyLinuxUnitFile(JPackageCommand cmd,
            Path installedLauncherPath) throws IOException {

        var serviceUnitFile = cmd.pathToUnpackedPackageFile(Path.of(
                "/lib/systemd/system").resolve(getServiceUnitFileName(
                        LinuxHelper.getPackageName(cmd), Optional.ofNullable(
                        launcherName).orElseGet(cmd::name))));

        TKit.traceFileContents(serviceUnitFile, "unit file");

        var execStartValue = (Pattern.compile("\\s").matcher(
                installedLauncherPath.toString()).find() ? String.format(
                "\"%s\"", installedLauncherPath) : installedLauncherPath);
        TKit.assertTextStream("ExecStart=" + execStartValue)
                .label("unit file")
                .predicate(String::equals)
                .apply(Files.readAllLines(serviceUnitFile).stream());
    }

    private void verifyMacDaemonPlistFile(JPackageCommand cmd,
            Path installedLauncherPath) throws IOException {

        var servicePlistFile = MacHelper.getServicePlistFilePath(cmd, launcherName);

        TKit.traceFileContents(servicePlistFile, "property file");

        var servicePlist = MacHelper.readPList(servicePlistFile);

        var args = servicePlist.queryArrayValue("ProgramArguments");
        TKit.assertEquals(1, args.size(),
                "Check number of array elements in 'ProgramArguments' property in the property file");
        TKit.assertEquals(installedLauncherPath.toString(), args.get(0),
                "Check path to launcher in 'ProgramArguments' property in the property file");

        var expectedLabel = IOUtils.replaceSuffix(servicePlistFile.getFileName(), "").toString();
        TKit.assertEquals(expectedLabel, servicePlist.queryValue("Label"),
                "Check value of 'Label' property in the property file");
    }

    private static void delayInstallVerify() {
        // Sleep a bit to let system launch the service
        Functional.ThrowingRunnable.toRunnable(() -> Thread.sleep(5 * 1000)).run();
    }

    private static Path appOutputDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    private static String getServiceUnitFileName(String packageName,
            String launcherName) {
        try {
            return getServiceUnitFileName.invoke(null, packageName, launcherName).toString();
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Method initGetServiceUnitFileName() {
        try {
            return Class.forName(
                    "jdk.jpackage.internal.LinuxLaunchersAsServices").getMethod(
                            "getServiceUnitFileName", String.class, String.class);
        } catch (ClassNotFoundException ex) {
            if (TKit.isLinux()) {
                throw new RuntimeException(ex);
            } else {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private final String expectedValue;
    private final String launcherName;
    private final Path appOutputPath;

    private final static Method getServiceUnitFileName = initGetServiceUnitFileName();
}
