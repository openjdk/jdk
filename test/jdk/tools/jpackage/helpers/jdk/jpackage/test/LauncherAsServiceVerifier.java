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
package jdk.jpackage.test;

import static jdk.jpackage.test.PackageType.LINUX;
import static jdk.jpackage.test.PackageType.MAC_PKG;
import static jdk.jpackage.test.PackageType.WINDOWS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.test.LauncherVerifier.Action;

public final class LauncherAsServiceVerifier {

    public static final class Builder {

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

        public Builder setAppOutputFileNamePrefix(String v) {
            appOutputFileNamePrefix = v;
            return this;
        }

        public Builder appendAppOutputFileNamePrefix(String v) {
            return setAppOutputFileNamePrefix(appOutputFileNamePrefix() + Objects.requireNonNull(v));
        }

        public Builder setAppOutputFileNamePrefixToAppName() {
            return setAppOutputFileNamePrefix(TKit.getCurrentDefaultAppName());
        }

        public Builder setAdditionalLauncherCallback(Consumer<AdditionalLauncher> v) {
            additionalLauncherCallback = v;
            return this;
        }

        public Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        public LauncherAsServiceVerifier create() {
            Objects.requireNonNull(expectedValue);
            return new LauncherAsServiceVerifier(
                    launcherName,
                    appOutputFileNamePrefix()
                            + Optional.ofNullable(appOutputFileName).orElse("launcher-as-service.txt"),
                    expectedValue,
                    Optional.ofNullable(additionalLauncherCallback));
        }

        public Builder applyTo(PackageTest test) {
            return applyTo(new ConfigurationTarget(test));
        }

        public Builder applyTo(ConfigurationTarget target) {
            create().applyTo(target);
            return this;
        }

        private String appOutputFileNamePrefix() {
            return Optional.ofNullable(appOutputFileNamePrefix).orElse("");
        }

        private String launcherName;
        private String expectedValue;
        private String appOutputFileName;
        private String appOutputFileNamePrefix;
        private Consumer<AdditionalLauncher> additionalLauncherCallback;
    }

    public static Builder build() {
        return new Builder();
    }

    private LauncherAsServiceVerifier(String launcherName,
            String appOutputFileName,
            String expectedArgValue,
            Optional<Consumer<AdditionalLauncher>> additionalLauncherCallback) {

        if (launcherName == null && additionalLauncherCallback.isPresent()) {
            throw new UnsupportedOperationException();
        }

        this.expectedValue = Objects.requireNonNull(expectedArgValue);
        this.launcherName = launcherName;
        this.appOutputFileName = Path.of(appOutputFileName);
        this.additionalLauncherCallback = additionalLauncherCallback;
    }

    public void applyTo(ConfigurationTarget target) {
        if (launcherName == null) {
            target.addInitializer(cmd -> {
                // Remove parameter added to jpackage command line in HelloApp.addTo()
                cmd.removeArgument("--win-console");
            });
            applyToMainLauncher(target);
        } else {
            applyToAdditionalLauncher(target);
        }
        target.test().ifPresent(pkg -> {
            pkg.addInstallVerifier(cmd -> {
                if (!MacHelper.isForAppStore(cmd)) {
                    verifyLauncherExecuted(cmd);
                }
            });
        });
    }

    static void verify(JPackageCommand cmd) {
        cmd.verifyIsOfType(SUPPORTED_PACKAGES);

        var partitionedLauncherNames = partitionLaunchers(cmd);

        var launcherAsServiceNames = partitionedLauncherNames.get(true);

        for (var launcherAsService : List.of(true, false)) {
            partitionedLauncherNames.get(launcherAsService).forEach(launcherName -> {
                verify(cmd, launcherName, launcherAsService);
            });
        }

        if (WINDOWS.contains(cmd.packageType()) && !cmd.isRuntime()) {
            Path serviceInstallerPath = cmd.appLayout().launchersDirectory().resolve(
                    "service-installer.exe");
            if (launcherAsServiceNames.isEmpty()) {
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
                        cmd, "foo").getParent());
            }
        } else if (LINUX.contains(cmd.packageType())) {
            if (cmd.isPackageUnpacked()) {
                servicesSpecificFolders.add(LinuxHelper.getServiceUnitFilePath(
                        cmd, "foo").getParent());
            }
        }

        if (launcherAsServiceNames.isEmpty() || cmd.isRuntime()) {
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
        return Objects.requireNonNull(partitionLaunchers(cmd).get(true));
    }

    private static Map<Boolean, List<String>> partitionLaunchers(JPackageCommand cmd) {
        if (cmd.isRuntime()) {
            return Map.of(true, List.of(), false, List.of());
        } else {
            return cmd.launcherNames(true).stream().collect(Collectors.partitioningBy(launcherName -> {
                return launcherAsService(cmd, launcherName);
            }));
        }
    }

    static boolean launcherAsService(JPackageCommand cmd, String launcherName) {
        if (MacHelper.isForAppStore(cmd)) {
            return false;
        } else if (cmd.isMainLauncher(launcherName)) {
            return PropertyFinder.findLauncherProperty(cmd, null,
                    PropertyFinder.cmdlineBooleanOption("--launcher-as-service"),
                    PropertyFinder.nop(),
                    PropertyFinder.nop()
            ).map(Boolean::parseBoolean).orElse(false);
        } else {
            return PropertyFinder.findLauncherProperty(cmd, launcherName,
                    PropertyFinder.nop(),
                    PropertyFinder.launcherPropertyFile("launcher-as-service"),
                    PropertyFinder.appImageFileLauncher(cmd, launcherName, "service").defaultValue(Boolean.FALSE.toString())
            ).map(Boolean::parseBoolean).orElseGet(() -> {
                return launcherAsService(cmd, null);
            });
        }
    }

    private boolean canVerifyInstall(JPackageCommand cmd) {
        cmd.verifyIsOfType(SUPPORTED_PACKAGES);

        String msg = String.format(
                "Not verifying contents of test output file [%s] for %s launcher",
                appOutputFilePathInitialize(),
                Optional.ofNullable(launcherName).orElse("the main"));
        if (cmd.isPackageUnpacked(msg) || cmd.isFakeRuntime(msg)) {
            return false;
        }
        var cfgFile = CfgFile.load(cmd.appLauncherCfgPath(launcherName));
        if (!expectedValue.equals(cfgFile.getValueUnchecked("ArgOptions",
                "arguments"))) {
            TKit.trace(String.format(
                    "%s because different version of the package is installed",
                    msg));
            return false;
        }
        return true;
    }

    private void applyToMainLauncher(ConfigurationTarget target) {
        target.addInitializer(cmd -> {
            cmd.addArgument("--launcher-as-service");
            cmd.addArguments("--arguments",
                    JPackageCommand.escapeAndJoin(expectedValue));
            cmd.addArguments("--java-options", "-Djpackage.test.appOutput="
                    + appOutputFilePathInitialize().toString());
            cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
        });
    }

    private void applyToAdditionalLauncher(ConfigurationTarget target) {
        var al = new AdditionalLauncher(launcherName)
                .setProperty("launcher-as-service", true)
                .addJavaOptions("-Djpackage.test.appOutput=" + appOutputFilePathInitialize().toString())
                .addJavaOptions("-Djpackage.test.noexit=true")
                .addDefaultArguments(expectedValue)
                .withoutVerifyActions(Action.EXECUTE_LAUNCHER);

        additionalLauncherCallback.ifPresent(v -> v.accept(al));

        target.add(al);
    }

    public void verifyLauncherExecuted(JPackageCommand cmd) {
        if (canVerifyInstall(cmd)) {
            delayInstallVerify();
            Path outputFilePath = appOutputFilePathVerify(cmd);
            HelloApp.assertApp(cmd.appLauncherPath(launcherName))
                    .addParam("jpackage.test.appOutput", outputFilePath.toString())
                    .addDefaultArguments(expectedValue)
                    .verifyOutput();
            deleteOutputFile(outputFilePath);
        }
    }

    private static void deleteOutputFile(Path file) {
        try {
            TKit.deleteIfExists(file);
        } catch (FileSystemException ex) {
            if (TKit.isLinux() || TKit.isOSX()) {
                // Probably "Operation no permitted" error. Try with "sudo" as the
                // file is created by a launcher started under root account.
                Executor.of("sudo", "rm", "-f").addArgument(file.toString()).execute();
            } else {
                throw new UncheckedIOException(ex);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void verify(JPackageCommand cmd, String launcherName, boolean launcherAsService) {
        if (LINUX.contains(cmd.packageType())) {
            if (launcherAsService) {
                verifyLinuxUnitFile(cmd, launcherName);
            } else {
                var serviceUnitFile = LinuxHelper.getServiceUnitFilePath(cmd, launcherName);
                TKit.assertPathExists(serviceUnitFile, false);
            }
        } else if (MAC_PKG.equals(cmd.packageType())) {
            if (launcherAsService) {
                verifyMacDaemonPlistFile(cmd, launcherName);
            } else {
                var servicePlistFile = MacHelper.getServicePlistFilePath(cmd, launcherName);
                TKit.assertPathExists(servicePlistFile, false);
            }
        }
    }

    private static void verifyLinuxUnitFile(JPackageCommand cmd, String launcherName) {

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
                .apply(ThrowingFunction.<Path, List<String>>toFunction(Files::readAllLines).apply(serviceUnitFile));
    }

    private static void verifyMacDaemonPlistFile(JPackageCommand cmd, String launcherName) {

        var servicePlistFile = MacHelper.getServicePlistFilePath(cmd, launcherName);

        TKit.traceFileContents(servicePlistFile, "property file");

        var installedLauncherPath = cmd.pathToPackageFile(cmd.appLauncherPath(
                launcherName));

        var servicePlist = MacHelper.readPList(servicePlistFile);

        var args = servicePlist.queryStringArrayValue("ProgramArguments");
        TKit.assertEquals(1, args.size(),
                "Check number of array elements in 'ProgramArguments' property in the property file");
        TKit.assertEquals(installedLauncherPath.toString(), args.get(0),
                "Check path to launcher in 'ProgramArguments' property in the property file");

        var expectedLabel = PathUtils.replaceSuffix(servicePlistFile.getFileName(), "").toString();
        TKit.assertEquals(expectedLabel, servicePlist.queryValue("Label"),
                "Check value of 'Label' property in the property file");
    }

    private static void delayInstallVerify() {
        // Sleep a bit to let system launch the service
        ThrowingRunnable.toRunnable(() -> Thread.sleep(5 * 1000)).run();
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
    private final Optional<Consumer<AdditionalLauncher>> additionalLauncherCallback;

    static final Set<PackageType> SUPPORTED_PACKAGES = Stream.of(
            LINUX,
            WINDOWS,
            Set.of(MAC_PKG)
    ).flatMap(Collection::stream).collect(Collectors.toSet());
}
