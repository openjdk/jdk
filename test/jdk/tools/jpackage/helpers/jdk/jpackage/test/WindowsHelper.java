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

import static jdk.jpackage.internal.util.function.ExceptionBox.toUnchecked;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    static Path getInstallationSubDirectory(JPackageCommand cmd) {
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

    static void verifyDeployedDesktopIntegration(JPackageCommand cmd, boolean installed) {
        WinShortcutVerifier.verifyDeployedShortcuts(cmd, installed);
        DesktopIntegrationVerifier.verify(cmd, installed);
    }

    public static String getMsiProperty(JPackageCommand cmd, String propertyName) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);
        return MsiDatabaseCache.INSTANCE.findProperty(cmd.outputBundle(), propertyName).orElseThrow();
    }

    static Collection<MsiDatabase.Shortcut> getMsiShortcuts(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);
        return MsiDatabaseCache.INSTANCE.listShortcuts(cmd.outputBundle());
    }

    public static String getExecutableDescription(Path pathToExeFile) {
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

    static boolean isUserLocalInstall(JPackageCommand cmd) {
        return cmd.hasArgument("--win-per-user-install");
    }

    private static boolean isPathTooLong(Path path) {
        return path.toString().length() > WIN_MAX_PATH;
    }


    private static class DesktopIntegrationVerifier {

        static void verify(JPackageCommand cmd, boolean installed) {
            cmd.verifyIsOfType(PackageType.WINDOWS);
            for (var faFile : cmd.getAllArgumentValues("--file-associations")) {
                verifyFileAssociationsRegistry(Path.of(faFile), installed);
            }
        }

        private static void verifyFileAssociationsRegistry(Path faFile, boolean installed) {

            TKit.trace(String.format(
                    "Get file association properties from [%s] file",
                    faFile));

            var faProps = new Properties();

            try (var reader = Files.newBufferedReader(faFile)) {
                faProps.load(reader);
                String suffix = faProps.getProperty("extension");
                String contentType = faProps.getProperty("mime-type");
                TKit.assertNotNull(suffix, String.format(
                        "Check file association suffix [%s] is found in [%s] property file",
                        suffix, faFile));
                TKit.assertNotNull(contentType, String.format(
                        "Check file association content type [%s] is found in [%s] property file",
                        contentType, faFile));
                verifyFileAssociations(installed, "." + suffix, contentType);

            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private static void verifyFileAssociations(boolean exists, String suffix,
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
        CommonPrograms,

        ProgramFiles,

        LocalApplicationData,
        ;

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

    enum SpecialFolder {
        COMMON_START_MENU_PROGRAMS(
                SYSTEM_SHELL_FOLDERS_REGKEY,
                "Common Programs",
                "ProgramMenuFolder",
                SpecialFolderDotNet.CommonPrograms),
        USER_START_MENU_PROGRAMS(
                USER_SHELL_FOLDERS_REGKEY,
                "Programs",
                "ProgramMenuFolder",
                SpecialFolderDotNet.Programs),

        COMMON_DESKTOP(
                SYSTEM_SHELL_FOLDERS_REGKEY,
                "Common Desktop",
                "DesktopFolder",
                SpecialFolderDotNet.CommonDesktop),
        USER_DESKTOP(
                USER_SHELL_FOLDERS_REGKEY,
                "Desktop",
                "DesktopFolder",
                SpecialFolderDotNet.Desktop),

        PROGRAM_FILES("ProgramFiles64Folder", SpecialFolderDotNet.ProgramFiles),

        LOCAL_APPLICATION_DATA("LocalAppDataFolder", SpecialFolderDotNet.LocalApplicationData),
        ;

        SpecialFolder(String keyPath, String valueName, String msiPropertyName) {
            reg = Optional.of(new RegValuePath(keyPath, valueName));
            alt = Optional.empty();
            this.msiPropertyName = Objects.requireNonNull(msiPropertyName);
        }

        SpecialFolder(String keyPath, String valueName, String msiPropertyName, SpecialFolderDotNet alt) {
            reg = Optional.of(new RegValuePath(keyPath, valueName));
            this.alt = Optional.of(alt);
            this.msiPropertyName = Objects.requireNonNull(msiPropertyName);
        }

        SpecialFolder(String msiPropertyName, SpecialFolderDotNet alt) {
            reg = Optional.empty();
            this.alt = Optional.of(alt);
            this.msiPropertyName = Objects.requireNonNull(msiPropertyName);
        }

        static Optional<SpecialFolder> findMsiProperty(String pathComponent, boolean allUsers) {
            Objects.requireNonNull(pathComponent);
            String regPath;
            if (allUsers) {
                regPath = SYSTEM_SHELL_FOLDERS_REGKEY;
            } else {
                regPath = USER_SHELL_FOLDERS_REGKEY;
            }
            return Stream.of(values())
                    .filter(v -> v.msiPropertyName.equals(pathComponent))
                    .filter(v -> {
                        return v.reg.map(r -> r.keyPath().equals(regPath)).orElse(true);
                    })
                    .findFirst();
        }

        String getMsiPropertyName() {
            return msiPropertyName;
        }

        Path getPath() {
            return CACHE.computeIfAbsent(this, k -> reg.flatMap(RegValuePath::findValue).map(Path::of).orElseGet(() -> {
                return alt.map(SpecialFolderDotNet::getPath).orElseThrow(() -> {
                    return new NoSuchElementException(String.format("Failed to find path to %s folder", name()));
                });
            }));
        }

        private final Optional<RegValuePath> reg;
        private final Optional<SpecialFolderDotNet> alt;
        // One of "System Folder Properties" from https://learn.microsoft.com/en-us/windows/win32/msi/property-reference
        private final String msiPropertyName;

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
                throw toUnchecked(ex);
            }
        }

        static Path toShortPath(Path path) {
            return Path.of(toSupplier(() -> (String) INSTANCE.getShortPathWrapper.invoke(
                    null, path.toString())).get());
        }

        private final Method getShortPathWrapper;

        private static final ShortPathUtils INSTANCE = new ShortPathUtils();
    }


    private static final class MsiDatabaseCache {

        Optional<String> findProperty(Path msiPath, String propertyName) {
            return ensureTables(msiPath, MsiDatabase.Table.FIND_PROPERTY_REQUIRED_TABLES).findProperty(propertyName);
        }

        Collection<MsiDatabase.Shortcut> listShortcuts(Path msiPath) {
            return ensureTables(msiPath, MsiDatabase.Table.LIST_SHORTCUTS_REQUIRED_TABLES).listShortcuts();
        }

        MsiDatabase ensureTables(Path msiPath, Set<MsiDatabase.Table> tableNames) {
            Objects.requireNonNull(msiPath);
            try {
                synchronized (items) {
                    var value = Optional.ofNullable(items.get(msiPath)).map(SoftReference::get).orElse(null);
                    if (value != null) {
                        var lastModifiedTime = Files.getLastModifiedTime(msiPath).toInstant();
                        if (lastModifiedTime.isAfter(value.timestamp())) {
                            value = null;
                        } else {
                            tableNames = Comm.compare(value.db().tableNames(), tableNames).unique2();
                        }
                    }

                    if (!tableNames.isEmpty()) {
                        var idtOutputDir = TKit.createTempDirectory("msi-db");
                        var db = MsiDatabase.load(msiPath, idtOutputDir, tableNames);
                        if (value != null) {
                            value = new MsiDatabaseWithTimestamp(db.append(value.db()), value.timestamp());
                        } else {
                            value = new MsiDatabaseWithTimestamp(db, Files.getLastModifiedTime(msiPath).toInstant());
                        }
                        items.put(msiPath, new SoftReference<>(value));
                    }

                    return value.db();
                }
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        private record MsiDatabaseWithTimestamp(MsiDatabase db, Instant timestamp) {

            MsiDatabaseWithTimestamp {
                Objects.requireNonNull(db);
                Objects.requireNonNull(timestamp);
            }
        }

        private final Map<Path, SoftReference<MsiDatabaseWithTimestamp>> items = new HashMap<>();

        static final MsiDatabaseCache INSTANCE = new MsiDatabaseCache();
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
