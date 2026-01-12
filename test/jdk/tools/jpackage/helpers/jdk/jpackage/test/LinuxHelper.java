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
package jdk.jpackage.test;

import static java.util.Collections.unmodifiableSortedSet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.LauncherShortcut.InvokeShortcutSpec;
import jdk.jpackage.test.PackageTest.PackageHandlers;


public final class LinuxHelper {
    private static String getReleaseSuffix(JPackageCommand cmd) {
        final String value;
        final PackageType packageType = cmd.packageType();
        switch (packageType) {
            case LINUX_DEB:
                value = Optional.ofNullable(cmd.getArgumentValue(
                        "--linux-app-release", () -> null)).map(v -> "-" + v).orElse(
                        "");
                break;

            case LINUX_RPM:
                value = "-" + cmd.getArgumentValue("--linux-app-release",
                        () -> "1");
                break;

            default:
                value = null;
        }
        return value;
    }

    public static String getPackageName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);
        return cmd.getArgumentValue("--linux-package-name",
                () -> cmd.installerName().toLowerCase());
    }

    public static Path getDesktopFile(JPackageCommand cmd) {
        return getDesktopFile(cmd, null);
    }

    public static Path getDesktopFile(JPackageCommand cmd, String launcherName) {
        cmd.verifyIsOfType(PackageType.LINUX);
        var desktopFileName = getLauncherDesktopFileName(cmd, launcherName);
        return cmd.appLayout().desktopIntegrationDirectory().resolve(
                desktopFileName);
    }

    static Path getServiceUnitFilePath(JPackageCommand cmd, String launcherName) {
        cmd.verifyIsOfType(PackageType.LINUX);
        return cmd.pathToUnpackedPackageFile(
                Path.of("/lib/systemd/system").resolve(getServiceUnitFileName(
                        getPackageName(cmd),
                        Optional.ofNullable(launcherName).orElseGet(cmd::mainLauncherName))));
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        final String format;
        switch (packageType) {
            case LINUX_DEB:
                format = "%s_%s%s_%s";
                break;

            case LINUX_RPM:
                format = "%s-%s%s.%s";
                break;

            default:
                throw new UnsupportedOperationException();
        }

        final String releaseSuffix = getReleaseSuffix(cmd);
        final String version = cmd.version();

        return String.format(format, getPackageName(cmd), version, releaseSuffix,
                getDefaultPackageArch(packageType)) + packageType.getSuffix();
    }

    public static Stream<Path> getPackageFiles(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        final Path packageFile = cmd.outputBundle();

        final Executor exec;
        switch (packageType) {
            case LINUX_DEB:
                exec = Executor.of("dpkg", "--contents").addArgument(packageFile);
                break;

            case LINUX_RPM:
                exec = Executor.of("rpm", "-qpl").addArgument(packageFile);
                break;

            default:
                throw new UnsupportedOperationException();
        }

        Stream<String> lines = exec.executeAndGetOutput().stream();
        if (packageType == PackageType.LINUX_DEB) {
            // Typical text lines produced by dpkg look like:
            // drwxr-xr-x root/root         0 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/
            // -rw-r--r-- root/root    574912 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/libmlib_image.so
            // Need to skip all fields but absolute path to file.
            lines = lines.map(line -> line.substring(line.indexOf(" ./") + 2));
        }
        return lines.map(Path::of);
    }

    public static List<String> getPrerequisitePackages(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);
        var packageType = cmd.packageType();
        switch (packageType) {
            case LINUX_DEB:
                return Stream.of(getDebBundleProperty(cmd.outputBundle(),
                        "Depends").split(",")).map(String::strip).toList();

            case LINUX_RPM:
                return Executor.of("rpm", "-qp", "-R")
                .addArgument(cmd.outputBundle())
                .executeAndGetOutput();

            default:
                throw new UnsupportedOperationException();
        }
    }

    public static String getBundleProperty(JPackageCommand cmd,
            String propertyName) {
        return getBundleProperty(cmd,
                Map.of(PackageType.LINUX_DEB, propertyName,
                        PackageType.LINUX_RPM, propertyName));
    }

    public static String getBundleProperty(JPackageCommand cmd,
            Map<PackageType, String> propertyName) {
        cmd.verifyIsOfType(PackageType.LINUX);
        var packageType = cmd.packageType();
        switch (packageType) {
            case LINUX_DEB:
                return getDebBundleProperty(cmd.outputBundle(), propertyName.get(
                        packageType));

            case LINUX_RPM:
                return getRpmBundleProperty(cmd.outputBundle(), propertyName.get(
                        packageType));

            default:
                throw new UnsupportedOperationException();
        }
    }

    private static Path getFaIconFileName(JPackageCommand cmd, String mimeType) {
        return Path.of(mimeType.replace('/', '-') + ".png");
    }

    static Path getLauncherDesktopFileName(JPackageCommand cmd, String launcherName) {
        return Path.of(String.format("%s-%s.desktop", getPackageName(cmd),
                Optional.ofNullable(launcherName).orElseGet(cmd::name).replaceAll("\\s+", "_")));
    }

    static Path getLauncherIconFileName(JPackageCommand cmd, String launcherName) {
        return Path.of(String.format("%s.png",
                Optional.ofNullable(launcherName).orElseGet(cmd::name).replaceAll("\\s+", "_")));
    }

    static PackageHandlers createDebPackageHandlers() {
        return new PackageHandlers(LinuxHelper::installDeb, LinuxHelper::uninstallDeb, LinuxHelper::unpackDeb);
    }

    private static int installDeb(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX_DEB);
        return Executor.of("sudo", "dpkg", "-i")
                .addArgument(cmd.outputBundle())
                .execute().getExitCode();
    }

    private static void uninstallDeb(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX_DEB);
        var packageName = getPackageName(cmd);
        String script = String.format("! dpkg -s %s || sudo dpkg -r %s",
                packageName, packageName);
        Executor.of("sh", "-c", script).execute();
    }

    private static Path unpackDeb(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.LINUX_DEB);
        Executor.of("dpkg", "-x")
        .addArgument(cmd.outputBundle())
        .addArgument(destinationDir)
        .execute(0);
        return destinationDir;
    }

    static PackageHandlers createRpmPackageHandlers() {
        return new PackageHandlers(LinuxHelper::installRpm, LinuxHelper::uninstallRpm, LinuxHelper::unpackRpm);
    }

    private static int installRpm(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX_RPM);
        return Executor.of("sudo", "rpm", "-U")
                .addArgument(cmd.outputBundle())
                .execute().getExitCode();
    }

    private static void uninstallRpm(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX_RPM);
        var packageName = getPackageName(cmd);
        String script = String.format("! rpm -q %s || sudo rpm -e %s",
                packageName, packageName);
        Executor.of("sh", "-c", script).execute();
    }

    private static Path unpackRpm(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.LINUX_RPM);
        Executor.of("sh", "-c", String.format(
                "rpm2cpio '%s' | cpio -idm --quiet",
                JPackageCommand.escapeAndJoin(
                        cmd.outputBundle().toAbsolutePath().toString())))
        .setDirectory(destinationDir)
        .execute(0);
        return destinationDir;
    }

    static Path getLauncherPath(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final String launcherName = cmd.name();
        final String launcherRelativePath = Path.of("/bin", launcherName).toString();

        return getPackageFiles(cmd).filter(path -> path.toString().endsWith(
                launcherRelativePath)).findFirst().or(() -> {
            TKit.assertUnexpected(String.format(
                    "Failed to find %s in %s package", launcherName,
                    getPackageName(cmd)));
            return null;
        }).get();
    }

    static long getInstalledPackageSizeKB(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final Path packageFile = cmd.outputBundle();
        switch (cmd.packageType()) {
            case LINUX_DEB:
                Long estimate = Long.parseLong(getDebBundleProperty(packageFile,
                        "Installed-Size"));
                if (estimate == 0L) {
                    // if the estimate in KB is 0, check if it is really empty
                    // or just < 1KB as with AppImagePackageTest.testEmpty()
                    if (getPackageFiles(cmd).count() > 01L) {
                        // there is something there so round up to 1 KB
                        estimate = 01L;
                    }
                }
                return estimate;

            case LINUX_RPM:
                String size = getRpmBundleProperty(packageFile, "Size");
                return (Long.parseLong(size) + 1023L) >> 10; // in KB rounded up

            default:
                throw new UnsupportedOperationException();
        }
    }

    static String getDebBundleProperty(Path bundle, String fieldName) {
        return Executor.of("dpkg-deb", "-f")
                .addArgument(bundle)
                .addArgument(fieldName)
                .executeAndGetFirstLineOfOutput();
    }

    static String getRpmBundleProperty(Path bundle, String fieldName) {
        return Executor.of("rpm", "-qp", "--queryformat", String.format("%%{%s}", fieldName))
                .addArgument(bundle)
                .executeAndGetFirstLineOfOutput();
    }

    static void verifyPackageBundleEssential(JPackageCommand cmd) {
        String packageName = getPackageName(cmd);
        long packageSize = getInstalledPackageSizeKB(cmd);
        TKit.trace("InstalledPackageSize: " + packageSize);
        TKit.assertNotEquals(0, packageSize, String.format(
                "Check installed size of [%s] package in not zero", packageName));

        final boolean checkPrerequisites;
        if (cmd.isRuntime()) {
            Path runtimeDir = cmd.appRuntimeDirectory();
            Set<Path> expectedCriticalRuntimePaths = CRITICAL_RUNTIME_FILES.stream().map(
                    runtimeDir::resolve).collect(toSet());
            Set<Path> actualCriticalRuntimePaths = getPackageFiles(cmd).filter(
                    expectedCriticalRuntimePaths::contains).collect(toSet());
            checkPrerequisites = expectedCriticalRuntimePaths.equals(
                    actualCriticalRuntimePaths);
        } else {
            // AppImagePackageTest.testEmpty() will have no dependencies,
            // but will have more then 0 and less than 5K content size when --icon is used.
            checkPrerequisites = packageSize > 5;
        }

        List<String> prerequisites = getPrerequisitePackages(cmd);
        if (checkPrerequisites) {
            final String vitalPackage = "libc";
            TKit.assertTrue(prerequisites.stream().filter(
                    dep -> dep.contains(vitalPackage)).findAny().isPresent(),
                    String.format(
                            "Check [%s] package is in the list of required packages %s of [%s] package",
                            vitalPackage, prerequisites, packageName));
        } else {
            TKit.trace(String.format(
                    "Not checking %s required packages of [%s] package",
                    prerequisites, packageName));
        }
    }

    public static Collection<? extends InvokeShortcutSpec> getInvokeShortcutSpecs(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final var desktopFiles = getDesktopFiles(cmd);

        return desktopFiles.stream().map(desktopFile -> {
            var systemDesktopFile = getSystemDesktopFilesFolder().resolve(desktopFile.getFileName());
            return new InvokeShortcutSpec.Stub(
                    launcherNameFromDesktopFile(cmd, desktopFile),
                    LauncherShortcut.LINUX_SHORTCUT,
                    new DesktopFile(systemDesktopFile, false).findQuotedValue("Path").map(Path::of),
                    List.of("gtk-launch", PathUtils.replaceSuffix(systemDesktopFile.getFileName(), "").toString()));
        }).toList();
    }

    static void addBundleDesktopIntegrationVerifier(PackageTest test, boolean integrated) {
        final String xdgUtils = "xdg-utils";

        Function<List<String>, String> verifier = (lines) -> {
            // Lookup for xdg commands
            return lines.stream().filter(line -> {
                Set<String> words = Stream.of(line.split("\\s+")).collect(toSet());
                return words.contains("xdg-desktop-menu") || words.contains(
                        "xdg-mime") || words.contains("xdg-icon-resource");
            }).findFirst().orElse(null);
        };

        test.addBundleVerifier(cmd -> {
            // Verify dependencies.
            List<String> prerequisites = getPrerequisitePackages(cmd);
            boolean xdgUtilsFound = prerequisites.contains(xdgUtils);
            TKit.assertTrue(xdgUtilsFound == integrated, String.format(
                    "Check [%s] is%s in the list of required packages %s",
                    xdgUtils, integrated ? "" : " NOT", prerequisites));

            Map<Scriptlet, List<String>> scriptlets = getScriptlets(cmd);
            if (integrated) {
                var requiredScriptlets = Stream.of(Scriptlet.values()).sorted().toList();
                TKit.assertTrue(scriptlets.keySet().containsAll(
                        requiredScriptlets), String.format(
                                "Check all required scriptlets %s found in the package. Package scriptlets: %s",
                                requiredScriptlets, scriptlets.keySet()));
            }

            // Lookup for xdg commands in scriptlets.
            scriptlets.entrySet().forEach(scriptlet -> {
                String lineWithXsdCommand = verifier.apply(scriptlet.getValue());
                String assertMsg = String.format(
                        "Check if [%s] scriptlet uses xdg commands",
                        scriptlet.getKey());
                if (integrated) {
                    TKit.assertNotNull(lineWithXsdCommand, assertMsg);
                } else {
                    TKit.assertNull(lineWithXsdCommand, assertMsg);
                }
            });
        });

        test.addInstallVerifier(cmd -> {
            if (!integrated) {
                TKit.assertStringListEquals(
                        List.of(),
                        getDesktopFiles(cmd).stream().map(Path::toString).toList(),
                        "Check there are no .desktop files in the package");
            }
        });
    }

    static void verifyDesktopIntegrationFiles(JPackageCommand cmd, boolean installed) {
        verifyDesktopFiles(cmd, installed);
        if (installed) {
            verifyAllIconsReferenced(cmd);
        }
    }

    private static void verifyDesktopFiles(JPackageCommand cmd, boolean installed) {
        final var desktopFiles = getDesktopFiles(cmd);
        try {
            if (installed) {
                var predefinedAppImage = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of).map(AppImageFile::load);
                for (var desktopFile : desktopFiles) {
                    verifyDesktopFile(cmd, predefinedAppImage, desktopFile);
                }

                if (!cmd.isPackageUnpacked("Not verifying system .desktop files")) {
                    for (var desktopFile : desktopFiles) {
                        Path systemDesktopFile = getSystemDesktopFilesFolder().resolve(desktopFile.getFileName());
                            TKit.assertFileExists(systemDesktopFile);
                            TKit.assertStringListEquals(
                                    Files.readAllLines(desktopFile),
                                    Files.readAllLines(systemDesktopFile),
                                    String.format("Check [%s] and [%s] files are equal", desktopFile, systemDesktopFile));
                    }
                }
            } else {
                for (var desktopFile : getDesktopFiles(cmd)) {
                    Path systemDesktopFile = getSystemDesktopFilesFolder().resolve(desktopFile.getFileName());
                    TKit.assertPathExists(systemDesktopFile, false);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Collection<Path> getDesktopFiles(JPackageCommand cmd) {

        var unpackedDir = cmd.appLayout().desktopIntegrationDirectory();

        return relativePackageFilesInSubdirectory(cmd, ApplicationLayout::desktopIntegrationDirectory)
                .filter(path -> {
                    return path.getNameCount() == 1;
                })
                .filter(path -> {
                    return ".desktop".equals(PathUtils.getSuffix(path));
                })
                .map(unpackedDir::resolve)
                .toList();
    }

    private static Stream<Path> relativePackageFilesInSubdirectory(
            JPackageCommand cmd, Function<ApplicationLayout, Path> subdirFunc) {

        var unpackedDir = subdirFunc.apply(cmd.appLayout());
        var packageDir = cmd.pathToPackageFile(unpackedDir);

        return getPackageFiles(cmd).filter(path -> {
            return path.startsWith(packageDir);
        }).map(packageDir::relativize);
    }

    private static void verifyAllIconsReferenced(JPackageCommand cmd) {

        var installCmd = Optional.ofNullable(cmd.unpackedPackageDirectory()).map(_ -> {
            return cmd.createMutableCopy().setUnpackedPackageLocation(null);
        }).orElse(cmd);

        var installedIconFiles = relativePackageFilesInSubdirectory(
                installCmd,
                ApplicationLayout::desktopIntegrationDirectory
        ).filter(path -> {
            return ".png".equals(PathUtils.getSuffix(path));
        }).map(installCmd.appLayout().desktopIntegrationDirectory()::resolve).collect(toSet());

        var referencedIcons = getDesktopFiles(cmd).stream().map(path -> {
            return new DesktopFile(path, false);
        }).<Path>mapMulti((desktopFile, sink) -> {
            desktopFile.findQuotedValue("Icon").map(Path::of).ifPresent(sink);
            desktopFile.find("MimeType").ifPresent(str -> {
                Stream.of(str.split(";"))
                        .map(mimeType -> {
                            return getFaIconFileName(cmd, mimeType);
                        })
                        .map(installCmd.appLayout().desktopIntegrationDirectory()::resolve)
                        .forEach(sink);
            });
        }).collect(toSet());

        var unreferencedIconFiles = Comm.compare(installedIconFiles, referencedIcons).unique1().stream().sorted().toList();

        // Verify that all package icon (.png) files are referenced from package .desktop files.
        TKit.assertEquals(List.of(), unreferencedIconFiles, "Check there are no unreferenced icon files in the package");
    }

    private static String launcherNameFromDesktopFile(JPackageCommand cmd, Path desktopFile) {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(desktopFile);

        return Stream.concat(Stream.of(cmd.mainLauncherName()), cmd.addLauncherNames(true).stream()).filter(name-> {
            return getDesktopFile(cmd, name).equals(desktopFile);
        }).findAny().orElseThrow(() -> {
            TKit.assertUnexpected(String.format("Failed to find launcher corresponding to [%s] file", desktopFile));
            // Unreachable
            return null;
        });
    }

    private static void verifyDesktopFile(JPackageCommand cmd, Optional<AppImageFile> predefinedAppImage, Path desktopFile) throws IOException {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(predefinedAppImage);
        Objects.requireNonNull(desktopFile);

        TKit.trace(String.format("Check [%s] file BEGIN", desktopFile));

        var launcherName = launcherNameFromDesktopFile(cmd, desktopFile);

        var data = new DesktopFile(desktopFile, true);

        final Set<String> mandatoryKeys = new TreeSet<>(Set.of("Name", "Comment",
                "Exec", "Icon", "Terminal", "Type", "Categories"));
        mandatoryKeys.removeAll(data.keySet());
        TKit.assertTrue(mandatoryKeys.isEmpty(), String.format(
                "Check for missing %s keys in the file", mandatoryKeys));

        final var launcherDescription = LauncherVerifier.launcherDescription(cmd, launcherName);

        for (var e : List.of(
                Map.entry("Type", "Application"),
                Map.entry("Terminal", "false"),
                Map.entry("Comment", launcherDescription),
                Map.entry("Categories", Optional.ofNullable(cmd.getArgumentValue("--linux-menu-group")).orElse("Utility"))
        )) {
            String key = e.getKey();
            TKit.assertEquals(e.getValue(), data.find(key).orElseThrow(), String.format(
                    "Check value of [%s] key", key));
        }

        String launcherPath = data.findQuotedValue("Exec").orElseThrow();

        TKit.assertEquals(
                launcherPath,
                cmd.pathToPackageFile(cmd.appLauncherPath(launcherName)).toString(),
                String.format("Check the value of [Exec] key references [%s] app launcher", launcherName));

        var appLayout = cmd.appLayout();

        LauncherShortcut.LINUX_SHORTCUT.expectShortcut(cmd, predefinedAppImage, launcherName).map(shortcutWorkDirType -> {
            switch (shortcutWorkDirType) {
                case DEFAULT -> {
                    return (Path)null;
                }
                case APP_DIR -> {
                    return cmd.pathToPackageFile(appLayout.appDirectory());
                }
                default -> {
                    throw new AssertionError();
                }
            }
        }).map(Path::toString).ifPresentOrElse(shortcutWorkDir -> {
            var actualShortcutWorkDir = data.find("Path");
            TKit.assertTrue(actualShortcutWorkDir.isPresent(), "Check [Path] key exists");
            TKit.assertEquals(actualShortcutWorkDir.get(), shortcutWorkDir, "Check the value of [Path] key");
        }, () -> {
            TKit.assertTrue(data.find("Path").isEmpty(), "Check there is no [Path] key");
        });

        for (var e : List.<Map.Entry<Map.Entry<String, Optional<String>>, Function<ApplicationLayout, Path>>>of(
                Map.entry(Map.entry("Exec", Optional.of(launcherPath)), ApplicationLayout::launchersDirectory),
                Map.entry(Map.entry("Icon", Optional.empty()), ApplicationLayout::desktopIntegrationDirectory))) {
            var path = e.getKey().getValue().or(() -> {
                return data.findQuotedValue(e.getKey().getKey());
            }).map(Path::of).get();
            TKit.assertFileExists(cmd.pathToUnpackedPackageFile(path));
            Path expectedDir = cmd.pathToPackageFile(e.getValue().apply(appLayout));
            TKit.assertTrue(path.getParent().equals(expectedDir), String.format(
                    "Check the value of [%s] key references a file in [%s] folder",
                    e.getKey().getKey(), expectedDir));
        }

        TKit.trace(String.format("Check [%s] file END", desktopFile));
    }

    static void initFileAssociationsTestFile(Path testFile) {
        try {
            // Write something in test file.
            // On Ubuntu and Oracle Linux empty files are considered
            // plain text. Seems like a system bug.
            //
            // $ >foo.jptest1
            // $ xdg-mime query filetype foo.jptest1
            // text/plain
            // $ echo > foo.jptest1
            // $ xdg-mime query filetype foo.jptest1
            // application/x-jpackage-jptest1
            //
            Files.write(testFile, Arrays.asList(""));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static Path getSystemDesktopFilesFolder() {
        return Stream.of("/usr/share/applications",
                "/usr/local/share/applications").map(Path::of).filter(dir -> {
            return Files.exists(dir.resolve("defaults.list"));
        }).findFirst().orElseThrow(() -> new RuntimeException(
                "Failed to locate system .desktop files folder"));
    }

    private static void withTestFileAssociationsFile(FileAssociations fa,
            ThrowingConsumer<Path, ? extends Exception> consumer) {
        boolean iterated[] = new boolean[] { false };
        PackageTest.withFileAssociationsTestRuns(fa, (testRun, testFiles) -> {
            if (!iterated[0]) {
                iterated[0] = true;
                consumer.accept(testFiles.get(0));
            }
        });
    }

    static void addFileAssociationsVerifier(PackageTest test, FileAssociations fa) {
        test.addInstallVerifier(cmd -> {
            if (cmd.isPackageUnpacked("Not running file associations checks")) {
                return;
            }

            withTestFileAssociationsFile(fa, testFile -> {
                String mimeType = queryFileMimeType(testFile);

                TKit.assertEquals(fa.getMime(), mimeType, String.format(
                        "Check mime type of [%s] file", testFile));

                String desktopFileName = queryMimeTypeDefaultHandler(mimeType).orElse(null);

                Path systemDesktopFile = getSystemDesktopFilesFolder().resolve(
                        desktopFileName);
                Path appDesktopFile = cmd.appLayout().desktopIntegrationDirectory().resolve(
                        desktopFileName);

                TKit.assertFileExists(systemDesktopFile);
                TKit.assertFileExists(appDesktopFile);

                TKit.assertStringListEquals(Files.readAllLines(appDesktopFile),
                        Files.readAllLines(systemDesktopFile), String.format(
                        "Check [%s] file is a copy of [%s] file",
                        systemDesktopFile, appDesktopFile));
            });
        });

        test.addUninstallVerifier(cmd -> {
            withTestFileAssociationsFile(fa, testFile -> {
                String mimeType = queryFileMimeType(testFile);

                TKit.assertNotEquals(fa.getMime(), mimeType, String.format(
                        "Check mime type of [%s] file", testFile));

                String desktopFileName = queryMimeTypeDefaultHandler(fa.getMime()).orElse(null);

                TKit.assertNull(desktopFileName, String.format(
                        "Check there is no default handler for [%s] mime type",
                        fa.getMime()));
            });
        });

        test.addBundleVerifier(cmd -> {
            Optional.of(fa).filter(FileAssociations::hasIcon)
                    .map(FileAssociations::getMime)
                    .map(mimeType -> {
                        return getFaIconFileName(cmd, mimeType);
                    }).ifPresent(mimeTypeIconFileName -> {
                        // Verify there are xdg registration commands for mime icon file.
                        Path mimeTypeIcon = cmd.appLayout().desktopIntegrationDirectory().resolve(
                                mimeTypeIconFileName);

                        Map<Scriptlet, List<String>> scriptlets = getScriptlets(cmd);
                        scriptlets.entrySet().stream().forEach(e -> verifyIconInScriptlet(
                                e.getKey(), e.getValue(), mimeTypeIcon));
                    });
        });
    }

    private static String queryFileMimeType(Path file) {
        return Executor.of("xdg-mime", "query", "filetype").addArgument(file)
                .executeAndGetFirstLineOfOutput();
    }

    private static Optional<String> queryMimeTypeDefaultHandler(String mimeType) {
        return Executor.of("xdg-mime", "query", "default", mimeType)
                .discardStderr()
                .saveFirstLineOfOutput()
                .execute().getOutput().stream().findFirst();
    }

    private static void verifyIconInScriptlet(Scriptlet scriptletType,
            List<String> scriptletBody, Path iconPathInPackage) {
        final String dashMime = PathUtils.replaceSuffix(
                iconPathInPackage.getFileName(), null).toString();
        final String xdgCmdName = "xdg-icon-resource";

        Stream<String> scriptletBodyStream = scriptletBody.stream()
                .filter(str -> Pattern.compile(
                        "\\b" + dashMime + "\\b").matcher(str).find());
        if (scriptletType == Scriptlet.PostInstall) {
            scriptletBodyStream = scriptletBodyStream.filter(str -> str.
                    startsWith(xdgCmdName));
            scriptletBodyStream = scriptletBodyStream.filter(str -> List.of(
                    str.split("\\s+")).contains(iconPathInPackage.toString()));
        } else {
            scriptletBodyStream = scriptletBodyStream.filter(str -> str.
                    contains(xdgCmdName)).filter(str -> str.startsWith(
                    "do_if_file_belongs_to_single_package"));
        }

        scriptletBodyStream.peek(xdgCmd -> {
            Matcher m = XDG_CMD_ICON_SIZE_PATTERN.matcher(xdgCmd);
            TKit.assertTrue(m.find(), String.format(
                    "Check icon size is specified as a number in [%s] xdg command of [%s] scriptlet",
                    xdgCmd, scriptletType));
            int iconSize = Integer.parseInt(m.group(1));
            TKit.assertTrue(XDG_CMD_VALID_ICON_SIZES.contains(iconSize),
                    String.format(
                            "Check icon size [%s] is one of %s values",
                            iconSize, XDG_CMD_VALID_ICON_SIZES));
        })
        .findFirst().orElseGet(() -> {
            TKit.assertUnexpected(String.format(
                    "Failed to find [%s] command in [%s] scriptlet for [%s] icon file",
                    xdgCmdName, scriptletType, iconPathInPackage));
            return null;
        });
    }

    private static Map<Scriptlet, List<String>> getScriptlets(
            JPackageCommand cmd, Scriptlet... scriptlets) {
        cmd.verifyIsOfType(PackageType.LINUX);

        Set<Scriptlet> scriptletSet = Set.of(
                scriptlets.length == 0 ? Scriptlet.values() : scriptlets);
        switch (cmd.packageType()) {
            case LINUX_DEB:
                return getDebScriptlets(cmd, scriptletSet);

            case LINUX_RPM:
                return getRpmScriptlets(cmd, scriptletSet);

            default:
                throw new UnsupportedOperationException();
        }
    }

    private static Map<Scriptlet, List<String>> getDebScriptlets(
            JPackageCommand cmd, Set<Scriptlet> scriptlets) {
        Map<Scriptlet, List<String>> result = new TreeMap<>();
        TKit.withTempDirectory("dpkg-control-files", tempDir -> {
            // Extract control Debian package files into temporary directory
            Executor.of("dpkg", "-e")
                    .addArgument(cmd.outputBundle())
                    .addArgument(tempDir)
                    .execute();

            for (Scriptlet scriptlet : scriptlets) {
                Path controlFile = Path.of(scriptlet.deb);
                result.put(scriptlet, Files.readAllLines(tempDir.resolve(
                        controlFile)));
            }
        });
        return result;
    }

    private static Map<Scriptlet, List<String>> getRpmScriptlets(
            JPackageCommand cmd, Set<Scriptlet> scriptlets) {
        List<String> output = Executor.of("rpm", "-qp", "--scripts",
                cmd.outputBundle().toString()).executeAndGetOutput();

        Map<Scriptlet, List<String>> result = new TreeMap<>();
        List<String> curScriptletBody = null;
        for (String str : output) {
            Matcher m = Scriptlet.RPM_HEADER_PATTERN.matcher(str);
            if (m.find()) {
                Scriptlet scriptlet = Scriptlet.RPM_MAP.get(m.group(1));
                if (scriptlets.contains(scriptlet)) {
                    curScriptletBody = new ArrayList<>();
                    result.put(scriptlet, curScriptletBody);
                } else if (curScriptletBody != null) {
                    curScriptletBody = null;
                }
            } else if (curScriptletBody != null) {
                curScriptletBody.add(str);
            }
        }

        return result;
    }

    private static enum Scriptlet {
        PostInstall("postinstall", "postinst"),
        PreUninstall("preuninstall", "prerm");

        Scriptlet(String rpm, String deb) {
            this.rpm = rpm;
            this.deb = deb;
        }

        private final String rpm;
        private final String deb;

        static final Pattern RPM_HEADER_PATTERN = Pattern.compile(String.format(
                "(%s) scriptlet \\(using /bin/sh\\):", Stream.of(values()).map(
                        v -> v.rpm).collect(joining("|"))));

        static final Map<String, Scriptlet> RPM_MAP = Stream.of(values()).collect(
                toMap(v -> v.rpm, v -> v));
    }

    public static String getDefaultPackageArch(PackageType type) {
        if (archs == null) {
            archs = new HashMap<>();
        }

        String arch = archs.get(type);
        if (arch == null) {
            final Executor exec;
            switch (type) {
                case LINUX_DEB:
                    exec = Executor.of("dpkg", "--print-architecture");
                    break;

                case LINUX_RPM:
                    exec = Executor.of("rpmbuild", "--eval=%{_target_cpu}");
                    break;

                default:
                    throw new UnsupportedOperationException();
            }
            arch = exec.executeAndGetFirstLineOfOutput();
            archs.put(type, arch);
        }
        return arch;
    }

    private static String getServiceUnitFileName(String packageName, String launcherName) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(launcherName);
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


    private static final class DesktopFile {
        DesktopFile(Path path, boolean verify) {
            try {
                List<String> lines = Files.readAllLines(path);
                if (verify) {
                    TKit.assertEquals("[Desktop Entry]", lines.getFirst(), "Check file header");
                }

                var stream = lines.stream().skip(1).filter(Predicate.not(String::isEmpty));
                if (verify) {
                    stream = stream.peek(str -> {
                        TKit.assertTextStream("=").predicate(String::contains).apply(List.of(str));
                    });
                }

                data = stream.map(str -> {
                    String components[] = str.split("=(?=.+)");
                    if (components.length == 1) {
                        return Map.entry(str.substring(0, str.length() - 1), "");
                    } else {
                        return Map.entry(components[0], components[1]);
                    }
                }).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        Set<String> keySet() {
            return data.keySet();
        }

        Optional<String> find(String property) {
            return Optional.ofNullable(data.get(Objects.requireNonNull(property)));
        }

        Optional<String> findQuotedValue(String property) {
            return find(property).map(value -> {
                if (Pattern.compile("\\s").matcher(value).find()) {
                    boolean quotesMatched = value.startsWith("\"") && value.endsWith("\"");
                    if (!quotesMatched) {
                        TKit.assertTrue(quotesMatched,
                                String.format("Check the value of key [%s] is enclosed in double quotes", property));
                    }
                    return value.substring(1, value.length() - 1);
                } else {
                    return value;
                }
            });
        }

        private final Map<String, String> data;
    }


    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "lib/server/libjvm.so"));

    private static Map<PackageType, String> archs;

    private static final Pattern XDG_CMD_ICON_SIZE_PATTERN = Pattern.compile("\\s--size\\s+(\\d+)\\b");

    // Values grabbed from https://linux.die.net/man/1/xdg-icon-resource
    private static final Set<Integer> XDG_CMD_VALID_ICON_SIZES = unmodifiableSortedSet(
            new TreeSet<>(List.of(16, 22, 32, 48, 64, 128)));

    private static final Method getServiceUnitFileName = initGetServiceUnitFileName();
}
