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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Functional.ThrowingRunnable;
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

    // Tests have problems on windows where path in the temp dir are too long
    // for the wix tools.  We can't use a tempDir outside the TKit's WorkDir, so
    // we minimize both the tempRoot directory name (above) and the tempDir name
    // (below) to the extension part (which is necessary to differenciate between
    // the multiple PackageTypes that will be run for one JPackageCommand).
    // It might be beter if the whole work dir name was shortened from:
    // jtreg_open_test_jdk_tools_jpackage_share_jdk_jpackage_tests_BasicTest_java.
    public static Path getTempDirectory(JPackageCommand cmd, Path tempRoot) {
        String ext = cmd.outputBundle().getFileName().toString();
        int i = ext.lastIndexOf(".");
        if (i > 0 && i < (ext.length() - 1)) {
            ext = ext.substring(i+1);
        }
        return tempRoot.resolve(ext);
    }

    private static void runMsiexecWithRetries(Executor misexec) {
        Executor.Result result = null;
        for (int attempt = 0; attempt < 8; ++attempt) {
            result = misexec.executeWithoutExitCodeCheck();

            if (result.exitCode == 1605) {
                // ERROR_UNKNOWN_PRODUCT, attempt to uninstall not installed
                // package
                return;
            }

            // The given Executor may either be of an msiexec command or an
            // unpack.bat script containing the msiexec command. In the later
            // case, when misexec returns 1618, the unpack.bat may return 1603
            if ((result.exitCode == 1618) || (result.exitCode == 1603)) {
                // Another installation is already in progress.
                // Wait a little and try again.
                Long timeout = 1000L * (attempt + 3); // from 3 to 10 seconds
                ThrowingRunnable.toRunnable(() -> Thread.sleep(timeout)).run();
                continue;
            }
            break;
        }

        result.assertExitCodeIsZero();
    }

    static PackageHandlers createMsiPackageHandlers() {
        BiConsumer<JPackageCommand, Boolean> installMsi = (cmd, install) -> {
            cmd.verifyIsOfType(PackageType.WIN_MSI);
            runMsiexecWithRetries(Executor.of("msiexec", "/qn", "/norestart",
                    install ? "/i" : "/x").addArgument(cmd.outputBundle().normalize()));
        };

        PackageHandlers msi = new PackageHandlers();
        msi.installHandler = cmd -> installMsi.accept(cmd, true);
        msi.uninstallHandler = cmd -> {
            if (Files.exists(cmd.outputBundle())) {
                installMsi.accept(cmd, false);
            }
        };
        msi.unpackHandler = (cmd, destinationDir) -> {
            cmd.verifyIsOfType(PackageType.WIN_MSI);
            final Path unpackBat = destinationDir.resolve("unpack.bat");
            final Path unpackDir = destinationDir.resolve(
                    TKit.removeRootFromAbsolutePath(
                            getInstallationRootDirectory(cmd)));

            // Put msiexec in .bat file because can't pass value of TARGETDIR
            // property containing spaces through ProcessBuilder properly.
            // Set folder permissions to allow msiexec unpack msi bundle.
            TKit.createTextFile(unpackBat, List.of(
                    String.format("icacls \"%s\" /inheritance:e /grant Users:M",
                            destinationDir),
                    String.join(" ", List.of(
                    "msiexec",
                    "/a",
                    String.format("\"%s\"", cmd.outputBundle().normalize()),
                    "/qn",
                    String.format("TARGETDIR=\"%s\"",
                            unpackDir.toAbsolutePath().normalize())))));
            runMsiexecWithRetries(Executor.of("cmd", "/c", unpackBat.toString()));

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
                    TKit.trace(String.format("Convert [%s] into [%s] in [%s] directory", from, to,
                            unpackDir));
                    ThrowingRunnable.toRunnable(() -> {
                        Files.createDirectories(unpackDir.resolve(to).getParent());
                        Files.move(unpackDir.resolve(from), unpackDir.resolve(to));
                        TKit.deleteDirectoryRecursive(unpackDir.resolve(extraPathComponent));
                    }).run();
                }
            }
            return destinationDir;
        };
        return msi;
    }

    static PackageHandlers createExePackageHandlers() {
        BiConsumer<JPackageCommand, Boolean> installExe = (cmd, install) -> {
            cmd.verifyIsOfType(PackageType.WIN_EXE);
            Executor exec = new Executor().setExecutable(cmd.outputBundle());
            if (install) {
                exec.addArgument("/qn").addArgument("/norestart");
            } else {
                exec.addArgument("uninstall");
            }
            runMsiexecWithRetries(exec);
        };

        PackageHandlers exe = new PackageHandlers();
        exe.installHandler = cmd -> installExe.accept(cmd, true);
        exe.uninstallHandler = cmd -> {
            if (Files.exists(cmd.outputBundle())) {
                installExe.accept(cmd, false);
            }
        };
        return exe;
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

    private static boolean isUserLocalInstall(JPackageCommand cmd) {
        return cmd.hasArgument("--win-per-user-install");
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
            Path dir = Path.of(queryRegistryValueCache(
                    SYSTEM_SHELL_FOLDERS_REGKEY, "Common Desktop"));
            verifyShortcut(dir.resolve(desktopShortcutPath), exists);
        }

        private void verifyUserLocalDesktopShortcut(boolean exists) {
            Path dir = Path.of(
                    queryRegistryValueCache(USER_SHELL_FOLDERS_REGKEY, "Desktop"));
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
                TKit.assertPathNotEmptyDirectory(shortcutPath.getParent());
            }
        }

        private void verifySystemStartMenuShortcut(boolean exists) {
            verifyStartMenuShortcut(Path.of(queryRegistryValueCache(
                    SYSTEM_SHELL_FOLDERS_REGKEY, "Common Programs")), exists);

        }

        private void verifyUserLocalStartMenuShortcut(boolean exists) {
            verifyStartMenuShortcut(Path.of(queryRegistryValueCache(
                    USER_SHELL_FOLDERS_REGKEY, "Programs")), exists);
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
        if (status.exitCode == 1) {
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

    private static String queryRegistryValueCache(String keyPath,
            String valueName) {
        String key = String.format("[%s][%s]", keyPath, valueName);
        String value = REGISTRY_VALUES.get(key);
        if (value == null) {
            value = queryRegistryValue(keyPath, valueName);
            REGISTRY_VALUES.put(key, value);
        }

        return value;
    }

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "bin\\server\\jvm.dll"));

    // jtreg resets %ProgramFiles% environment variable by some reason.
    private final static Path PROGRAM_FILES = Path.of(Optional.ofNullable(
            System.getenv("ProgramFiles")).orElse("C:\\Program Files"));

    private final static Path USER_LOCAL = Path.of(System.getProperty(
            "user.home"),
            "AppData", "Local");

    private final static String SYSTEM_SHELL_FOLDERS_REGKEY = "HKLM\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";
    private final static String USER_SHELL_FOLDERS_REGKEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders";

    private static final Map<String, String> REGISTRY_VALUES = new HashMap<>();
}
