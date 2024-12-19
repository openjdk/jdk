/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.PackageTest.PackageHandlers;


public final class LinuxHelper {
    private static String getReleaseSuffix(JPackageCommand cmd) {
        String value = null;
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
        String desktopFileName = String.format("%s-%s.desktop", getPackageName(
                cmd), Optional.ofNullable(launcherName).orElseGet(
                        () -> cmd.name()).replaceAll("\\s+", "_"));
        return cmd.appLayout().destktopIntegrationDirectory().resolve(
                desktopFileName);
    }

    static Path getServiceUnitFilePath(JPackageCommand cmd, String launcherName) {
        cmd.verifyIsOfType(PackageType.LINUX);
        return cmd.pathToUnpackedPackageFile(
                Path.of("/lib/systemd/system").resolve(getServiceUnitFileName(
                        getPackageName(cmd),
                        Optional.ofNullable(launcherName).orElseGet(cmd::name))));
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        String format = null;
        switch (packageType) {
            case LINUX_DEB:
                format = "%s_%s%s_%s";
                break;

            case LINUX_RPM:
                format = "%s-%s%s.%s";
                break;
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

        Executor exec = null;
        switch (packageType) {
            case LINUX_DEB:
                exec = Executor.of("dpkg", "--contents").addArgument(packageFile);
                break;

            case LINUX_RPM:
                exec = Executor.of("rpm", "-qpl").addArgument(packageFile);
                break;
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
                        "Depends").split(",")).map(String::strip).collect(
                        Collectors.toList());

            case LINUX_RPM:
                return Executor.of("rpm", "-qp", "-R")
                .addArgument(cmd.outputBundle())
                .executeAndGetOutput();
        }
        // Unreachable
        return null;
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
        }
        // Unrechable
        return null;
    }

    static PackageHandlers createDebPackageHandlers() {
        PackageHandlers deb = new PackageHandlers();
        deb.installHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.LINUX_DEB);
            Executor.of("sudo", "dpkg", "-i")
            .addArgument(cmd.outputBundle())
            .execute();
        };
        deb.uninstallHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.LINUX_DEB);
            var packageName = getPackageName(cmd);
            String script = String.format("! dpkg -s %s || sudo dpkg -r %s",
                    packageName, packageName);
            Executor.of("sh", "-c", script).execute();
        };
        deb.unpackHandler = (cmd, destinationDir) -> {
            cmd.verifyIsOfType(PackageType.LINUX_DEB);
            Executor.of("dpkg", "-x")
            .addArgument(cmd.outputBundle())
            .addArgument(destinationDir)
            .execute();
            return destinationDir;
        };
        return deb;
    }

    static PackageHandlers createRpmPackageHandlers() {
        PackageHandlers rpm = new PackageHandlers();
        rpm.installHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.LINUX_RPM);
            Executor.of("sudo", "rpm", "-U")
            .addArgument(cmd.outputBundle())
            .execute();
        };
        rpm.uninstallHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.LINUX_RPM);
            var packageName = getPackageName(cmd);
            String script = String.format("! rpm -q %s || sudo rpm -e %s",
                    packageName, packageName);
            Executor.of("sh", "-c", script).execute();
        };
        rpm.unpackHandler = (cmd, destinationDir) -> {
            cmd.verifyIsOfType(PackageType.LINUX_RPM);
            Executor.of("sh", "-c", String.format(
                    "rpm2cpio '%s' | cpio -idm --quiet",
                    JPackageCommand.escapeAndJoin(
                            cmd.outputBundle().toAbsolutePath().toString())))
            .setDirectory(destinationDir)
            .execute();
            return destinationDir;
        };

        return rpm;
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

        }

        return 0;
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
        String packageName = LinuxHelper.getPackageName(cmd);
        Long packageSize = LinuxHelper.getInstalledPackageSizeKB(cmd);
        TKit.trace("InstalledPackageSize: " + packageSize);
        TKit.assertNotEquals(0L, packageSize, String.format(
                "Check installed size of [%s] package in not zero", packageName));

        final boolean checkPrerequisites;
        if (cmd.isRuntime()) {
            Path runtimeDir = cmd.appRuntimeDirectory();
            Set<Path> expectedCriticalRuntimePaths = CRITICAL_RUNTIME_FILES.stream().map(
                    runtimeDir::resolve).collect(Collectors.toSet());
            Set<Path> actualCriticalRuntimePaths = getPackageFiles(cmd).filter(
                    expectedCriticalRuntimePaths::contains).collect(
                            Collectors.toSet());
            checkPrerequisites = expectedCriticalRuntimePaths.equals(
                    actualCriticalRuntimePaths);
        } else {
            // AppImagePackageTest.testEmpty() will have no dependencies,
            // but will have more then 0 and less than 5K content size when --icon is used.
            checkPrerequisites = packageSize > 5;
        }

        List<String> prerequisites = LinuxHelper.getPrerequisitePackages(cmd);
        if (checkPrerequisites) {
            final String vitalPackage = "libc";
            TKit.assertTrue(prerequisites.stream().filter(
                    dep -> dep.contains(vitalPackage)).findAny().isPresent(),
                    String.format(
                            "Check [%s] package is in the list of required packages %s of [%s] package",
                            vitalPackage, prerequisites, packageName));
        } else {
            TKit.trace(String.format(
                    "Not cheking %s required packages of [%s] package",
                    prerequisites, packageName));
        }
    }

    static void addBundleDesktopIntegrationVerifier(PackageTest test,
            boolean integrated) {
        final String xdgUtils = "xdg-utils";

        Function<List<String>, String> verifier = (lines) -> {
            // Lookup for xdg commands
            return lines.stream().filter(line -> {
                Set<String> words = Stream.of(line.split("\\s+")).collect(
                        Collectors.toSet());
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
                Set<Scriptlet> requiredScriptlets = Stream.of(Scriptlet.values()).sorted().collect(
                        Collectors.toSet());
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
            // Verify .desktop files.
            try (var files = Files.list(cmd.appLayout().destktopIntegrationDirectory())) {
                List<Path> desktopFiles = files
                        .filter(path -> path.getFileName().toString().endsWith(".desktop"))
                        .toList();
                if (!integrated) {
                    TKit.assertStringListEquals(List.of(),
                            desktopFiles.stream().map(Path::toString).collect(
                                    Collectors.toList()),
                            "Check there are no .desktop files in the package");
                }
                for (var desktopFile : desktopFiles) {
                    verifyDesktopFile(cmd, desktopFile);
                }
            }
        });
    }

    private static void verifyDesktopFile(JPackageCommand cmd, Path desktopFile)
            throws IOException {
        TKit.trace(String.format("Check [%s] file BEGIN", desktopFile));

        var launcherName = Stream.of(List.of(cmd.name()), cmd.addLauncherNames()).flatMap(List::stream).filter(name -> {
            return getDesktopFile(cmd, name).equals(desktopFile);
        }).findAny();
        if (!cmd.hasArgument("--app-image")) {
            TKit.assertTrue(launcherName.isPresent(),
                    "Check the desktop file corresponds to one of app launchers");
        }

        List<String> lines = Files.readAllLines(desktopFile);
        TKit.assertEquals("[Desktop Entry]", lines.get(0), "Check file header");

        Map<String, String> data = lines.stream()
        .skip(1)
        .peek(str -> TKit.assertTextStream("=").predicate(String::contains).apply(Stream.of(str)))
        .map(str -> {
            String components[] = str.split("=(?=.+)");
            if (components.length == 1) {
                return Map.entry(str.substring(0, str.length() - 1), "");
            }
            return Map.entry(components[0], components[1]);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> {
            TKit.assertUnexpected("Multiple values of the same key");
            return null;
        }));

        final Set<String> mandatoryKeys = new HashSet(Set.of("Name", "Comment",
                "Exec", "Icon", "Terminal", "Type", "Categories"));
        mandatoryKeys.removeAll(data.keySet());
        TKit.assertTrue(mandatoryKeys.isEmpty(), String.format(
                "Check for missing %s keys in the file", mandatoryKeys));

        for (var e : Map.of("Type", "Application", "Terminal", "false").entrySet()) {
            String key = e.getKey();
            TKit.assertEquals(e.getValue(), data.get(key), String.format(
                    "Check value of [%s] key", key));
        }

        // Verify the value of `Exec` key is escaped if required
        String launcherPath = data.get("Exec");
        if (Pattern.compile("\\s").matcher(launcherPath).find()) {
            TKit.assertTrue(launcherPath.startsWith("\"")
                    && launcherPath.endsWith("\""),
                    "Check path to the launcher is enclosed in double quotes");
            launcherPath = launcherPath.substring(1, launcherPath.length() - 1);
        }

        if (launcherName.isPresent()) {
            TKit.assertEquals(launcherPath, cmd.pathToPackageFile(
                    cmd.appLauncherPath(launcherName.get())).toString(),
                    String.format(
                            "Check the value of [Exec] key references [%s] app launcher",
                            launcherName.get()));
        }

        for (var e : List.<Map.Entry<Map.Entry<String, Optional<String>>, Function<ApplicationLayout, Path>>>of(
                Map.entry(Map.entry("Exec", Optional.of(launcherPath)), ApplicationLayout::launchersDirectory),
                Map.entry(Map.entry("Icon", Optional.empty()), ApplicationLayout::destktopIntegrationDirectory))) {
            var path = e.getKey().getValue().or(() -> Optional.of(data.get(
                    e.getKey().getKey()))).map(Path::of).get();
            TKit.assertFileExists(cmd.pathToUnpackedPackageFile(path));
            Path expectedDir = cmd.pathToPackageFile(e.getValue().apply(cmd.appLayout()));
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
            ThrowingConsumer<Path> consumer) {
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

                String desktopFileName = queryMimeTypeDefaultHandler(mimeType);

                Path systemDesktopFile = getSystemDesktopFilesFolder().resolve(
                        desktopFileName);
                Path appDesktopFile = cmd.appLayout().destktopIntegrationDirectory().resolve(
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

                String desktopFileName = queryMimeTypeDefaultHandler(fa.getMime());

                TKit.assertNull(desktopFileName, String.format(
                        "Check there is no default handler for [%s] mime type",
                        fa.getMime()));
            });
        });

        test.addBundleVerifier(cmd -> {
            final Path mimeTypeIconFileName = fa.getLinuxIconFileName();
            if (mimeTypeIconFileName != null) {
                // Verify there are xdg registration commands for mime icon file.
                Path mimeTypeIcon = cmd.appLayout().destktopIntegrationDirectory().resolve(
                        mimeTypeIconFileName);

                Map<Scriptlet, List<String>> scriptlets = getScriptlets(cmd);
                scriptlets.entrySet().stream().forEach(e -> verifyIconInScriptlet(
                        e.getKey(), e.getValue(), mimeTypeIcon));
            }
        });
    }

    private static String queryFileMimeType(Path file) {
        return Executor.of("xdg-mime", "query", "filetype").addArgument(file)
                .executeAndGetFirstLineOfOutput();
    }

    private static String queryMimeTypeDefaultHandler(String mimeType) {
        return Executor.of("xdg-mime", "query", "default", mimeType)
                .executeAndGetFirstLineOfOutput();
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
        }

        // Unreachable
        return null;
    }

    private static Map<Scriptlet, List<String>> getDebScriptlets(
            JPackageCommand cmd, Set<Scriptlet> scriptlets) {
        Map<Scriptlet, List<String>> result = new HashMap<>();
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

        Map<Scriptlet, List<String>> result = new HashMap<>();
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
                        v -> v.rpm).collect(Collectors.joining("|"))));

        static final Map<String, Scriptlet> RPM_MAP = Stream.of(values()).collect(
                Collectors.toMap(v -> v.rpm, v -> v));
    };

    public static String getDefaultPackageArch(PackageType type) {
        if (archs == null) {
            archs = new HashMap<>();
        }

        String arch = archs.get(type);
        if (arch == null) {
            Executor exec = null;
            switch (type) {
                case LINUX_DEB:
                    exec = Executor.of("dpkg", "--print-architecture");
                    break;

                case LINUX_RPM:
                    exec = Executor.of("rpmbuild", "--eval=%{_target_cpu}");
                    break;
            }
            arch = exec.executeAndGetFirstLineOfOutput();
            archs.put(type, arch);
        }
        return arch;
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

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "lib/server/libjvm.so"));

    private static Map<PackageType, String> archs;

    private static final Pattern XDG_CMD_ICON_SIZE_PATTERN = Pattern.compile("\\s--size\\s+(\\d+)\\b");

    // Values grabbed from https://linux.die.net/man/1/xdg-icon-resource
    private static final Set<Integer> XDG_CMD_VALID_ICON_SIZES = Set.of(16, 22, 32, 48, 64, 128);

    private static final Method getServiceUnitFileName = initGetServiceUnitFileName();
}
