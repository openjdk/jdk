/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.IOUtils;
import static jdk.jpackage.test.Functional.ThrowingConsumer.toConsumer;
import static jdk.jpackage.test.PackageType.LINUX;
import static jdk.jpackage.test.PackageType.MAC_PKG;
import static jdk.jpackage.test.PackageType.WINDOWS;

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

        public Builder setAdditionalLauncherCallback(Consumer<AdditionalLauncher> v) {
            additionalLauncherCallback = v;
            return this;
        }

        public LauncherAsServiceVerifier create() {
            Objects.requireNonNull(expectedValue);
            return new LauncherAsServiceVerifier(launcherName, appOutputFileName,
                    expectedValue,
                    launcherName != null ? additionalLauncherCallback : null);
        }

        public Builder applyTo(PackageTest pkg) {
            create().applyTo(pkg);
            return this;
        }

        private String launcherName;
        private String expectedValue;
        private String appOutputFileName = "launcher-as-service.txt";
        private Consumer<AdditionalLauncher> additionalLauncherCallback;
    }

    public static Builder build() {
        return new Builder();
    }

    private LauncherAsServiceVerifier(String launcherName,
            String appOutputFileName,
            String expectedArgValue,
            Consumer<AdditionalLauncher> additionalLauncherCallback) {
        this.expectedValue = expectedArgValue;
        this.launcherName = launcherName;
        this.appOutputFileName = Path.of(appOutputFileName);
        this.additionalLauncherCallback = additionalLauncherCallback;
    }

    public void applyTo(PackageTest pkg) {
        if (launcherName == null) {
            pkg.forTypes(WINDOWS, () -> {
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

    static void verify(JPackageCommand cmd) {
        cmd.verifyIsOfType(SUPPORTED_PACKAGES);

        var launcherNames = getLaunchersAsServices(cmd);

        launcherNames.forEach(toConsumer(launcherName -> {
            verify(cmd, launcherName);
        }));

        if (WINDOWS.contains(cmd.packageType()) && !cmd.isRuntime()) {
            Path serviceInstallerPath = cmd.appLayout().launchersDirectory().resolve(
                    "service-installer.exe");
            if (launcherNames.isEmpty()) {
                TKit.assertPathExists(serviceInstallerPath, false);

            } else {
                TKit.assertFileExists(serviceInstallerPath);
            }
        }

        List<Path> servicesSpecificFiles = new ArrayList<>();
        List<Path> servicesSpecificFolders = new ArrayList<>();

        if (MAC_PKG.equals(cmd.packageType())) {
            servicesSpecificFiles.add(MacHelper.getUninstallCommand(cmd));

            if (cmd.isPackageUnpacked()) {
                servicesSpecificFolders.add(MacHelper.getServicePlistFilePath(
                        cmd, null).getParent());
            }
        } else if (LINUX.contains(cmd.packageType())) {
            if (cmd.isPackageUnpacked()) {
                servicesSpecificFolders.add(LinuxHelper.getServiceUnitFilePath(
                        cmd, null).getParent());
            }
        }

        if (launcherNames.isEmpty() || cmd.isRuntime()) {
            servicesSpecificFiles.forEach(path -> TKit.assertPathExists(path,
                    false));
            servicesSpecificFolders.forEach(path -> TKit.assertPathExists(path,
                    false));
        } else {
            servicesSpecificFiles.forEach(TKit::assertFileExists);
            servicesSpecificFolders.forEach(TKit::assertDirectoryExists);
        }
    }

    static void verifyUninstalled(JPackageCommand cmd) {
        cmd.verifyIsOfType(SUPPORTED_PACKAGES);

        var launcherNames = getLaunchersAsServices(cmd);
        for (var launcherName : launcherNames) {
            if (TKit.isLinux()) {
                TKit.assertPathExists(LinuxHelper.getServiceUnitFilePath(cmd,
                        launcherName), false);
            } else if (TKit.isOSX()) {
                TKit.assertPathExists(MacHelper.getServicePlistFilePath(cmd,
                        launcherName), false);
            }
        }

        if (TKit.isOSX()) {
            TKit.assertPathExists(MacHelper.getUninstallCommand(cmd).getParent(),
                    false);
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
                                    line -> {
                                        if (line.startsWith(
                                                "launcher-as-service=")) {
                                            return Boolean.parseBoolean(
                                                    line.substring(
                                                            "launcher-as-service=".length()));
                                        } else {
                                            return false;
                                        }
                                    })) {
                                launcherNames.add(launcherName);
                            }
                        }));

        return launcherNames;
    }

    private boolean canVerifyInstall(JPackageCommand cmd) throws IOException {
        String msg = String.format(
                "Not verifying contents of test output file [%s] for %s launcher",
                appOutputFilePathInitialize(),
                Optional.ofNullable(launcherName).orElse("the main"));
        if (cmd.isPackageUnpacked(msg) || cmd.isFakeRuntime(msg)) {
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
            cmd.addArguments("--java-options", "-Djpackage.test.appOutput="
                    + appOutputFilePathInitialize().toString());
            cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        });
        pkg.addInstallVerifier(cmd -> {
            if (canVerifyInstall(cmd)) {
                delayInstallVerify();
                Path outputFilePath = appOutputFilePathVerify(cmd);
                HelloApp.assertApp(cmd.appLauncherPath())
                        .addParam("jpackage.test.appOutput",
                                outputFilePath.toString())
                        .addDefaultArguments(expectedValue)
                        .verifyOutput();
                deleteOutputFile(outputFilePath);
            }
        });
        pkg.addInstallVerifier(cmd -> {
            verify(cmd, launcherName);
        });
    }

    private void applyToAdditionalLauncher(PackageTest pkg) {
        AdditionalLauncher al = new AdditionalLauncher(launcherName) {
            @Override
            protected void verify(JPackageCommand cmd) throws IOException {
                if (canVerifyInstall(cmd)) {
                    delayInstallVerify();
                    super.verify(cmd);
                    deleteOutputFile(appOutputFilePathVerify(cmd));
                }
                LauncherAsServiceVerifier.verify(cmd, launcherName);
            }
        }.setLauncherAsService()
                .addJavaOptions("-Djpackage.test.appOutput="
                        + appOutputFilePathInitialize().toString())
                .addJavaOptions("-Djpackage.test.noexit=true")
                .addDefaultArguments(expectedValue);

        Optional.ofNullable(additionalLauncherCallback).ifPresent(v -> v.accept(al));

        al.applyTo(pkg);
    }

    private static void deleteOutputFile(Path file) throws IOException {
        try {
            TKit.deleteIfExists(file);
        } catch (FileSystemException ex) {
            if (TKit.isLinux() || TKit.isOSX()) {
                // Probably "Operation no permitted" error. Try with "sudo" as the
                // file is created by a launcher started under root account.
                Executor.of("sudo", "rm", "-f").addArgument(file.toString()).
                        execute();
            } else {
                throw ex;
            }
        }
    }

    private static void verify(JPackageCommand cmd, String launcherName) throws
            IOException {
        if (LINUX.contains(cmd.packageType())) {
            verifyLinuxUnitFile(cmd, launcherName);
        } else if (MAC_PKG.equals(cmd.packageType())) {
            verifyMacDaemonPlistFile(cmd, launcherName);
        }
    }

    private static void verifyLinuxUnitFile(JPackageCommand cmd,
            String launcherName) throws IOException {

        var serviceUnitFile = LinuxHelper.getServiceUnitFilePath(cmd, launcherName);

        TKit.traceFileContents(serviceUnitFile, "unit file");

        var installedLauncherPath = cmd.pathToPackageFile(cmd.appLauncherPath(
                launcherName));

        var execStartValue = (Pattern.compile("\\s").matcher(
                installedLauncherPath.toString()).find() ? String.format(
                "\"%s\"", installedLauncherPath) : installedLauncherPath);
        TKit.assertTextStream("ExecStart=" + execStartValue)
                .label("unit file")
                .predicate(String::equals)
                .apply(Files.readAllLines(serviceUnitFile).stream());
    }

    private static void verifyMacDaemonPlistFile(JPackageCommand cmd,
            String launcherName) throws IOException {

        var servicePlistFile = MacHelper.getServicePlistFilePath(cmd, launcherName);

        TKit.traceFileContents(servicePlistFile, "property file");

        var installedLauncherPath = cmd.pathToPackageFile(cmd.appLauncherPath(
                launcherName));

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

    private Path appOutputFilePathInitialize() {
        final Path dir;
        if (TKit.isWindows()) {
            dir = Path.of("$ROOTDIR");
        } else {
            dir = Path.of("/tmp");
        }
        return dir.resolve(appOutputFileName);
    }

    private Path appOutputFilePathVerify(JPackageCommand cmd) {
        if (TKit.isWindows()) {
            return cmd.appInstallationDirectory().resolve(appOutputFileName);
        } else {
            return appOutputFilePathInitialize();
        }
    }

    private final String expectedValue;
    private final String launcherName;
    private final Path appOutputFileName;
    private final Consumer<AdditionalLauncher> additionalLauncherCallback;

    final static Set<PackageType> SUPPORTED_PACKAGES = Stream.of(LINUX, WINDOWS,
            Set.of(MAC_PKG)).flatMap(x -> x.stream()).collect(Collectors.toSet());
}
