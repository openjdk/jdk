/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.function.ExceptionBox.rethrowUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.test.PackageTest.PackageHandlers;

public class WindowsHelper {

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WINDOWS);
        return String.format("%s-%s%s", cmd.installerName(), cmd.version(),
                cmd.packageType().getSuffix());
    }

    static Path getInstallationDirectory(JPackageCommand cmd) {
        return getInstallationRootDirectory(cmd).resolve(
                getInstallationSubDirectory(cmd));
    }

    private static Path getInstallationRootDirectory(JPackageCommand cmd) {
        if (isUserLocalInstall(cmd)) {
            return USER_LOCAL;
        }
        return PROGRAM_FILES;
    }

    private static Path getInstallationSubDirectory(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WINDOWS);
        return Path.of(cmd.getArgumentValue("--install-dir", cmd::name));
    }

    private static int runMsiexecWithRetries(Executor misexec, Optional<Path> msiLog) {
        Executor.Result result = null;
        final boolean isUnpack = misexec.getExecutable().orElseThrow().equals(Path.of("cmd"));
        final List<String> origArgs = msiLog.isPresent() ? misexec.getAllArguments() : null;
        for (int attempt = 0; attempt < 8; ++attempt) {
            msiLog.ifPresent(v -> misexec.clearArguments().addArguments(origArgs).addArgument("/L*v").addArgument(v));
            result = misexec.executeWithoutExitCodeCheck();

            if (result.exitCode() == 1605) {
                // ERROR_UNKNOWN_PRODUCT, attempt to uninstall not installed
                // package
                return result.exitCode();
            }

            // The given Executor may either be of an msiexec command or an
            // unpack.bat script containing the msiexec command. In the later
            // case, when misexec returns 1618, the unpack.bat may return 1603
            if ((result.exitCode() == 1618) || (result.exitCode() == 1603 && isUnpack)) {
                // Another installation is already in progress.
                // Wait a little and try again.
                Long timeout = 1000L * (attempt + 3); // from 3 to 10 seconds
                ThrowingRunnable.toRunnable(() -> Thread.sleep(timeout)).run();
                continue;
            }
            break;
        }

        return result.exitCode();
    }

    static PackageHandlers createMsiPackageHandlers(boolean createMsiLog) {
        return new PackageHandlers(cmd -> installMsi(cmd, createMsiLog),
                cmd -> uninstallMsi(cmd, createMsiLog), WindowsHelper::unpackMsi);
    }

    private static Optional<Path> configureMsiLogFile(JPackageCommand cmd, boolean createMsiLog) {
        final Optional<Path> msiLogFile;
        if (createMsiLog) {
            msiLogFile = Optional.of(TKit.createTempFile(String.format("logs\\%s-msi.log",
                    cmd.packageType().getType())));
        } else {
            msiLogFile = Optional.empty();
        }

        cmd.winMsiLogFile(msiLogFile.orElse(null));

        return msiLogFile;
    }

    private static int runMsiInstaller(JPackageCommand cmd, boolean createMsiLog, boolean install) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);
        final var msiPath = TransientMsi.create(cmd).path();
        return runMsiexecWithRetries(Executor.of("msiexec", "/qn", "/norestart",
                install ? "/i" : "/x").addArgument(msiPath), configureMsiLogFile(cmd, createMsiLog));
    }

    private static int installMsi(JPackageCommand cmd, boolean createMsiLog) {
        return runMsiInstaller(cmd, createMsiLog, true);
    }

    private static void uninstallMsi(JPackageCommand cmd, boolean createMsiLog) {
        if (Files.exists(cmd.outputBundle())) {
            runMsiInstaller(cmd, createMsiLog, false);
        } else {
            configureMsiLogFile(cmd, false);
        }
    }

    private static Path unpackMsi(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);
        configureMsiLogFile(cmd, false);
        final Path unpackBat = destinationDir.resolve("unpack.bat");
        final Path unpackDir = destinationDir.resolve(
                TKit.removeRootFromAbsolutePath(
                        getInstallationRootDirectory(cmd)));

        final Path msiPath = TransientMsi.create(cmd).path();

        // Put msiexec in .bat file because can't pass value of TARGETDIR
        // property containing spaces through ProcessBuilder properly.
        // Set folder permissions to allow msiexec unpack msi bundle.
        TKit.createTextFile(unpackBat, List.of(
                String.format("icacls \"%s\" /inheritance:e /grant Users:M",
                        destinationDir),
                String.join(" ", List.of(
                "msiexec",
                "/a",
                String.format("\"%s\"", msiPath),
                "/qn",
                String.format("TARGETDIR=\"%s\"",
                        unpackDir.toAbsolutePath().normalize())))));
        runMsiexecWithRetries(Executor.of("cmd", "/c", unpackBat.toString()), Optional.empty());

        //
        // WiX3 uses "." as the value of "DefaultDir" field for "ProgramFiles64Folder" folder in msi's Directory table
        // WiX4 uses "PFiles64" as the value of "DefaultDir" field for "ProgramFiles64Folder" folder in msi's Directory table
        // msiexec creates "Program Files/./<App Installation Directory>" from WiX3 msi which translates to "Program Files/<App Installation Directory>"
        // msiexec creates "Program Files/PFiles64/<App Installation Directory>" from WiX4 msi
        // So for WiX4 msi we need to transform "Program Files/PFiles64/<App Installation Directory>" into "Program Files/<App Installation Directory>"
        //
        // WiX4 does the same thing for %LocalAppData%.
        //
        for (var extraPathComponent : List.of("PFiles64", "LocalApp")) {
            if (Files.isDirectory(unpackDir.resolve(extraPathComponent))) {
                Path installationSubDirectory = getInstallationSubDirectory(cmd);
                Path from = Path.of(extraPathComponent).resolve(installationSubDirectory);
                Path to = installationSubDirectory;

                ThrowingRunnable.toRunnable(() -> {
                    Files.createDirectories(unpackDir.resolve(to).getParent());
                }).run();

                // Files.move() occasionally results into java.nio.file.AccessDeniedException
                Executor.tryRunMultipleTimes(ThrowingRunnable.toRunnable(() -> {
                    TKit.trace(String.format("Convert [%s] into [%s] in [%s] directory", from, to, unpackDir));
                    final var dstDir = unpackDir.resolve(to);
                    TKit.deleteDirectoryRecursive(dstDir);
                    Files.move(unpackDir.resolve(from), dstDir);
                    TKit.deleteDirectoryRecursive(unpackDir.resolve(extraPathComponent));
                }), 3, 5);
            }
        }
        return destinationDir;
    }

    static PackageHandlers createExePackageHandlers(boolean createMsiLog) {
        return new PackageHandlers(cmd -> installExe(cmd, createMsiLog), WindowsHelper::uninstallExe, Optional.empty());
    }

    private static int runExeInstaller(JPackageCommand cmd, boolean createMsiLog, boolean install) {
        cmd.verifyIsOfType(PackageType.WIN_EXE);
        Executor exec = new Executor().setExecutable(cmd.outputBundle());
        if (install) {
            exec.addArgument("/qn").addArgument("/norestart");
        } else {
            exec.addArgument("uninstall");
        }
        return runMsiexecWithRetries(exec, configureMsiLogFile(cmd, createMsiLog));
    }

    private static int installExe(JPackageCommand cmd, boolean createMsiLog) {
        return runExeInstaller(cmd, createMsiLog, true);
    }

    private static void uninstallExe(JPackageCommand cmd) {
        if (Files.exists(cmd.outputBundle())) {
            runExeInstaller(cmd, false, false);
        } else {
            configureMsiLogFile(cmd, false);
        }
    }

    record TransientMsi(Path path) {
        static TransientMsi create(JPackageCommand cmd) {
            var outputMsiPath = cmd.outputBundle().normalize();
            if (isPathTooLong(outputMsiPath)) {
                return toSupplier(() -> {
                    var transientMsiPath = TKit.createTempDirectory("msi-copy").resolve("a.msi").normalize();
                    TKit.trace(String.format("Copy [%s] to [%s]", outputMsiPath, transientMsiPath));
                    Files.copy(outputMsiPath, transientMsiPath);
                    return new TransientMsi(transientMsiPath);
                }).get();
            } else {
                return new TransientMsi(outputMsiPath);
            }
        }
    }

    public enum WixType {
        WIX3,
        WIX4
    }

    public static WixType getWixTypeFromVerboseJPackageOutput(Executor.Result result) {
        return result.getOutput().stream().map(str -> {
            if (str.contains("[light.exe]")) {
                return WixType.WIX3;
            } else if (str.contains("[wix.exe]")) {
                return WixType.WIX4;
            } else {
                return null;
            }
        }).filter(Objects::nonNull).reduce((a, b) -> {
            throw new IllegalArgumentException("Invalid input: multiple invocations of WiX tools");
        }).orElseThrow(() -> new IllegalArgumentException("Invalid input: no invocations of WiX tools"));
    }

    static Optional<Path> toShortPath(Path path) {
        if (isPathTooLong(path)) {
            return Optional.of(ShortPathUtils.toShortPath(path));
        } else {
            return Optional.empty();
        }
    }

    static void verifyDesktopIntegration(JPackageCommand cmd,
            String launcherName) {
        new DesktopIntegrationVerifier(cmd, launcherName);
    }

    public static String getMsiProperty(JPackageCommand cmd, String propertyName) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);
        return Executor.of("cscript.exe", "//Nologo")
        .addArgument(TKit.TEST_SRC_ROOT.resolve("resources/query-msi-property.js"))
        .addArgument(cmd.outputBundle())
        .addArgument(propertyName)
        .dumpOutput()
        .executeAndGetOutput().stream().collect(Collectors.joining("\n"));
    }

    public static String getExecutableDesciption(Path pathToExeFile) {
        Executor exec = Executor.of("powershell",
                "-NoLogo",
                "-NoProfile",
                "-Command",
                "(Get-Item \\\""
                + pathToExeFile.toAbsolutePath()
                + "\\\").VersionInfo | select FileDescription");

        var lineIt = exec.dumpOutput().executeAndGetOutput().iterator();
        while (lineIt.hasNext()) {
            var line = lineIt.next();
            if (line.trim().equals("FileDescription")) {
                // Skip "---------------" and move to the description value
                lineIt.next();
                return lineIt.next().trim();
            }
        }

        throw new RuntimeException(String.format(
                "Failed to get file description of [%s]", pathToExeFile));
    }

    public static void killProcess(long pid) {
        Executor.of("taskkill", "/F", "/PID", Long.toString(pid)).dumpOutput(true).execute();
    }

    public static void killAppLauncherProcess(JPackageCommand cmd,
            String launcherName, int expectedCount) {
        var pids = findAppLauncherPIDs(cmd, launcherName);
        try {
            TKit.assertEquals(expectedCount, pids.length, String.format(
                    "Check [%d] %s app launcher processes found running",
                    expectedCount, Optional.ofNullable(launcherName).map(
                            str -> "[" + str + "]").orElse("<main>")));
        } finally {
            if (pids.length != 0) {
                killProcess(pids[0]);
            }
        }
    }

    private static long[] findAppLauncherPIDs(JPackageCommand cmd, String launcherName) {
        // Get the list of PIDs and PPIDs of app launcher processes. Run setWinRunWithEnglishOutput(true) for JDK-8344275.
        // powershell -NoLogo -NoProfile -NonInteractive -Command
        //   "Get-CimInstance Win32_Process -Filter \"Name = 'foo.exe'\" | select ProcessID,ParentProcessID"
        String command = "Get-CimInstance Win32_Process -Filter \\\"Name = '"
                + cmd.appLauncherPath(launcherName).getFileName().toString()
                + "'\\\" | select ProcessID,ParentProcessID";
        List<String> output = Executor.of("powershell", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", command)
                .dumpOutput(true).saveOutput().setWinRunWithEnglishOutput(true).executeAndGetOutput();

        if (output.size() < 1) {
            return new long[0];
        }

        String[] headers = Stream.of(output.get(1).split("\\s+", 2)).map(
                String::trim).map(String::toLowerCase).toArray(String[]::new);
        Pattern pattern;
        if (headers[0].equals("parentprocessid") && headers[1].equals(
                "processid")) {
            pattern = Pattern.compile("^\\s+(?<ppid>\\d+)\\s+(?<pid>\\d+)$");
        } else if (headers[1].equals("parentprocessid") && headers[0].equals(
                "processid")) {
            pattern = Pattern.compile("^\\s+(?<pid>\\d+)\\s+(?<ppid>\\d+)$");
        } else {
            throw new RuntimeException(
                    "Unrecognizable output of \'Get-CimInstance Win32_Process\' command");
        }

        List<long[]> processes = output.stream().skip(3).map(line -> {
            Matcher m = pattern.matcher(line);
            long[] pids = null;
            if (m.matches()) {
                pids = new long[]{Long.parseLong(m.group("pid")), Long.
                    parseLong(m.group("ppid"))};
            }
            return pids;
        }).filter(Objects::nonNull).toList();

        switch (processes.size()) {
            case 2 -> {
                final long parentPID;
                final long childPID;
                if (processes.get(0)[0] == processes.get(1)[1]) {
                    parentPID = processes.get(0)[0];
                    childPID = processes.get(1)[0];
                } else if (processes.get(1)[0] == processes.get(0)[1]) {
                    parentPID = processes.get(1)[0];
                    childPID = processes.get(0)[0];
                } else {
                    TKit.assertUnexpected("App launcher processes unrelated");
                    return null; // Unreachable
                }
                return new long[]{parentPID, childPID};
            }
            case 1 -> {
                return new long[]{processes.get(0)[0]};
            }
            default -> {
                TKit.assertUnexpected(String.format(
                        "Unexpected number of running processes [%d]",
                        processes.size()));
                return null; // Unreachable
            }
        }
    }

    private static boolean isUserLocalInstall(JPackageCommand cmd) {
        return cmd.hasArgument("--win-per-user-install");
    }

    private static boolean isPathTooLong(Path path) {
        return path.toString().length() > WIN_MAX_PATH;
    }

    private static class DesktopIntegrationVerifier {

        DesktopIntegrationVerifier(JPackageCommand cmd, String launcherName) {
            cmd.verifyIsOfType(PackageType.WINDOWS);

            name = Optional.ofNullable(launcherName).orElseGet(cmd::name);

            isUserLocalInstall = isUserLocalInstall(cmd);

            appInstalled = cmd.appLauncherPath(launcherName).toFile().exists();

            desktopShortcutPath = Path.of(name + ".lnk");

            startMenuShortcutPath = Path.of(cmd.getArgumentValue(
                    "--win-menu-group", () -> "Unknown"), name + ".lnk");

            if (name.equals(cmd.name())) {
                isWinMenu = cmd.hasArgument("--win-menu");
                isDesktop = cmd.hasArgument("--win-shortcut");
            } else {
                var props = AdditionalLauncher.getAdditionalLauncherProperties(cmd,
                        launcherName);
                isWinMenu = props.getPropertyBooleanValue("win-menu").orElseGet(
                        () -> cmd.hasArgument("--win-menu"));
                isDesktop = props.getPropertyBooleanValue("win-shortcut").orElseGet(
                        () -> cmd.hasArgument("--win-shortcut"));
            }

            verifyStartMenuShortcut();

            verifyDesktopShortcut();

            Stream.of(cmd.getAllArgumentValues("--file-associations")).map(
                    Path::of).forEach(this::verifyFileAssociationsRegistry);
        }

        private void verifyDesktopShortcut() {
            if (isDesktop) {
                if (isUserLocalInstall) {
                    verifyUserLocalDesktopShortcut(appInstalled);
                    verifySystemDesktopShortcut(false);
                } else {
                    verifySystemDesktopShortcut(appInstalled);
                    verifyUserLocalDesktopShortcut(false);
                }
            } else {
                verifySystemDesktopShortcut(false);
                verifyUserLocalDesktopShortcut(false);
            }
        }

        private void verifyShortcut(Path path, boolean exists) {
            if (exists) {
                TKit.assertFileExists(path);
            } else {
                TKit.assertPathExists(path, false);
            }
        }

        private void verifySystemDesktopShortcut(boolean exists) {
            Path dir = SpecialFolder.COMMON_DESKTOP.getPath();
            verifyShortcut(dir.resolve(desktopShortcutPath), exists);
        }

        private void verifyUserLocalDesktopShortcut(boolean exists) {
            Path dir = SpecialFolder.USER_DESKTOP.getPath();
            verifyShortcut(dir.resolve(desktopShortcutPath), exists);
        }

        private void verifyStartMenuShortcut() {
            if (isWinMenu) {
                if (isUserLocalInstall) {
                    verifyUserLocalStartMenuShortcut(appInstalled);
                    verifySystemStartMenuShortcut(false);
                } else {
                    verifySystemStartMenuShortcut(appInstalled);
                    verifyUserLocalStartMenuShortcut(false);
                }
            } else {
                verifySystemStartMenuShortcut(false);
                verifyUserLocalStartMenuShortcut(false);
            }
        }

        private void verifyStartMenuShortcut(Path shortcutsRoot, boolean exists) {
            Path shortcutPath = shortcutsRoot.resolve(startMenuShortcutPath);
            verifyShortcut(shortcutPath, exists);
            if (!exists) {
                final var parentDir = shortcutPath.getParent();
                if (Files.isDirectory(parentDir)) {
                    TKit.assertDirectoryNotEmpty(parentDir);
                } else {
                    TKit.assertPathExists(parentDir, false);
                }
            }
        }

        private void verifySystemStartMenuShortcut(boolean exists) {
            verifyStartMenuShortcut(SpecialFolder.COMMON_START_MENU_PROGRAMS.getPath(), exists);

        }

        private void verifyUserLocalStartMenuShortcut(boolean exists) {
            verifyStartMenuShortcut(SpecialFolder.USER_START_MENU_PROGRAMS.getPath(), exists);
        }

        private void verifyFileAssociationsRegistry(Path faFile) {
            try {
                TKit.trace(String.format(
                        "Get file association properties from [%s] file",
                        faFile));
                Map<String, String> faProps = Files.readAllLines(faFile).stream().filter(
                        line -> line.trim().startsWith("extension=") || line.trim().startsWith(
                        "mime-type=")).map(
                                line -> {
                                    String[] keyValue = line.trim().split("=", 2);
                                    return Map.entry(keyValue[0], keyValue[1]);
                                }).collect(Collectors.toMap(
                                entry -> entry.getKey(),
                                entry -> entry.getValue()));
                String suffix = faProps.get("extension");
                String contentType = faProps.get("mime-type");
                TKit.assertNotNull(suffix, String.format(
                        "Check file association suffix [%s] is found in [%s] property file",
                        suffix, faFile));
                TKit.assertNotNull(contentType, String.format(
                        "Check file association content type [%s] is found in [%s] property file",
                        contentType, faFile));
                verifyFileAssociations(appInstalled, "." + suffix, contentType);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private void verifyFileAssociations(boolean exists, String suffix,
                String contentType) {
            String contentTypeFromRegistry = queryRegistryValue(Path.of(
                    "HKLM\\Software\\Classes", suffix).toString(),
                    "Content Type");
            String suffixFromRegistry = queryRegistryValue(
                    "HKLM\\Software\\Classes\\MIME\\Database\\Content Type\\" + contentType,
                    "Extension");

            if (exists) {
                TKit.assertEquals(suffix, suffixFromRegistry,
                        "Check suffix in registry is as expected");
                TKit.assertEquals(contentType, contentTypeFromRegistry,
                        "Check content type in registry is as expected");
            } else {
                TKit.assertNull(suffixFromRegistry,
                        "Check suffix in registry not found");
                TKit.assertNull(contentTypeFromRegistry,
                        "Check content type in registry not found");
            }
        }

        private final Path desktopShortcutPath;
        private final Path startMenuShortcutPath;
        private final boolean isUserLocalInstall;
        private final boolean appInstalled;
        private final boolean isWinMenu;
        private final boolean isDesktop;
        private final String name;
    }

    static String queryRegistryValue(String keyPath, String valueName) {
        var status = Executor.of("reg", "query", keyPath, "/v", valueName)
                .saveOutput()
                .executeWithoutExitCodeCheck();
        if (status.exitCode() == 1) {
            // Should be the case of no such registry value or key
            String lookupString = "ERROR: The system was unable to find the specified registry key or value.";
            TKit.assertTextStream(lookupString)
                    .predicate(String::equals)
                    .orElseThrow(() -> new RuntimeException(String.format(
                            "Failed to find [%s] string in the output",
                            lookupString)));
            TKit.trace(String.format(
                    "Registry value [%s] at [%s] path not found", valueName,
                    keyPath));
            return null;
        }

        String value = status.assertExitCodeIsZero().getOutput().stream().skip(2).findFirst().orElseThrow();
        // Extract the last field from the following lines:
        //    (Default)    REG_SZ    test1
        //    string_val    REG_SZ    test2
        //    string_val_empty    REG_SZ
        //    bin_val    REG_BINARY    4242
        //    bin_val_empty    REG_BINARY
        //    dword_val    REG_DWORD    0x2a
        //    qword_val    REG_QWORD    0x2a
        //    multi_string_val    REG_MULTI_SZ    test3\0test4
        //    multi_string_val_empty    REG_MULTI_SZ
        //    expand_string_val    REG_EXPAND_SZ    test5
        //    expand_string_val_empty    REG_EXPAND_SZ
        String[] parts = value.split(" {4}REG_[A-Z_]+ {4}");
        if (parts.length == 1) {
            value = "";
        } else if (parts.length == 2) {
            value = parts[1];
        } else {
            throw new RuntimeException(String.format("Failed to extract registry value from string [%s]", value));
        }

        TKit.trace(String.format("Registry value [%s] at [%s] path is [%s]",
                valueName, keyPath, value));

        return value;
    }

    // See .NET special folders
    private enum SpecialFolderDotNet {
        Desktop,
        CommonDesktop,

        Programs,
        CommonPrograms;

        Path getPath() {
            final var str = Executor.of("powershell", "-NoLogo", "-NoProfile",
                    "-NonInteractive", "-Command",
                    String.format("[Environment]::GetFolderPath('%s')", name())
                    ).saveFirstLineOfOutput().execute().getFirstLineOfOutput();

            TKit.trace(String.format("Value of .NET special folder '%s' is [%s]", name(), str));

            return Path.of(str);
        }
    }

    private record RegValuePath(String keyPath, String valueName) {
        RegValuePath {
            Objects.requireNonNull(keyPath);
            Objects.requireNonNull(valueName);
        }

        Optional<String> findValue() {
            return Optional.ofNullable(queryRegistryValue(keyPath, valueName));
        }
    }

    private enum SpecialFolder {
        COMMON_START_MENU_PROGRAMS(SYSTEM_SHELL_FOLDERS_REGKEY, "Common Programs", SpecialFolderDotNet.CommonPrograms),
        USER_START_MENU_PROGRAMS(USER_SHELL_FOLDERS_REGKEY, "Programs", SpecialFolderDotNet.Programs),

        COMMON_DESKTOP(SYSTEM_SHELL_FOLDERS_REGKEY, "Common Desktop", SpecialFolderDotNet.CommonDesktop),
        USER_DESKTOP(USER_SHELL_FOLDERS_REGKEY, "Desktop", SpecialFolderDotNet.Desktop);

        SpecialFolder(String keyPath, String valueName) {
            reg = new RegValuePath(keyPath, valueName);
            alt = Optional.empty();
        }

        SpecialFolder(String keyPath, String valueName, SpecialFolderDotNet alt) {
            reg = new RegValuePath(keyPath, valueName);
            this.alt = Optional.of(alt);
        }

        Path getPath() {
            return CACHE.computeIfAbsent(this, k -> reg.findValue().map(Path::of).orElseGet(() -> {
                return alt.map(SpecialFolderDotNet::getPath).orElseThrow(() -> {
                    return new NoSuchElementException(String.format("Failed to find path to %s folder", name()));
                });
            }));
        }

        private final RegValuePath reg;
        private final Optional<SpecialFolderDotNet> alt;

        private static final Map<SpecialFolder, Path> CACHE = new ConcurrentHashMap<>();
    }

    private static final class ShortPathUtils {
        private ShortPathUtils() {
            try {
                var shortPathUtilsClass = Class.forName("jdk.jpackage.internal.ShortPathUtils");

                getShortPathWrapper = shortPathUtilsClass.getDeclaredMethod(
                        "getShortPathWrapper", String.class);
                // Note: this reflection call requires
                // --add-opens jdk.jpackage/jdk.jpackage.internal=ALL-UNNAMED
                getShortPathWrapper.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchMethodException
                    | SecurityException ex) {
                throw rethrowUnchecked(ex);
            }
        }

        static Path toShortPath(Path path) {
            return Path.of(toSupplier(() -> (String) INSTANCE.getShortPathWrapper.invoke(
                    null, path.toString())).get());
        }

        private final Method getShortPathWrapper;

        private static final ShortPathUtils INSTANCE = new ShortPathUtils();
    }

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "bin\\server\\jvm.dll"));

    // jtreg resets %ProgramFiles% environment variable by some reason.
    private static final Path PROGRAM_FILES = Path.of(Optional.ofNullable(
            System.getenv("ProgramFiles")).orElse("C:\\Program Files"));

    private static final Path USER_LOCAL = Path.of(System.getProperty(
            "user.home"),
            "AppData", "Local");

    private static final String SYSTEM_SHELL_FOLDERS_REGKEY = "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";
    private static final String USER_SHELL_FOLDERS_REGKEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";

    private static final int WIN_MAX_PATH = 260;
}
