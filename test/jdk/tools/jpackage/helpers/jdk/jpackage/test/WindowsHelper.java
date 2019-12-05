/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WindowsHelper {

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WINDOWS);
        return String.format("%s-%s%s", cmd.name(), cmd.version(),
                cmd.packageType().getSuffix());
    }

    static Path getInstallationDirectory(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WINDOWS);
        Path installDir = Path.of(
                cmd.getArgumentValue("--install-dir", () -> cmd.name()));
        if (isUserLocalInstall(cmd)) {
            return USER_LOCAL.resolve(installDir);
        }
        return PROGRAM_FILES.resolve(installDir);
    }

    private static boolean isUserLocalInstall(JPackageCommand cmd) {
        return cmd.hasArgument("--win-per-user-install");
    }

    static class AppVerifier {

        AppVerifier(JPackageCommand cmd) {
            cmd.verifyIsOfType(PackageType.WINDOWS);
            this.cmd = cmd;
            verifyStartMenuShortcut();
            verifyDesktopShortcut();
            verifyFileAssociationsRegistry();
        }

        private void verifyDesktopShortcut() {
            boolean appInstalled = cmd.appLauncherPath().toFile().exists();
            if (cmd.hasArgument("--win-shortcut")) {
                if (isUserLocalInstall(cmd)) {
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

        private Path desktopShortcutPath() {
            return Path.of(cmd.name() + ".lnk");
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
            verifyShortcut(dir.resolve(desktopShortcutPath()), exists);
        }

        private void verifyUserLocalDesktopShortcut(boolean exists) {
            Path dir = Path.of(
                    queryRegistryValueCache(USER_SHELL_FOLDERS_REGKEY, "Desktop"));
            verifyShortcut(dir.resolve(desktopShortcutPath()), exists);
        }

        private void verifyStartMenuShortcut() {
            boolean appInstalled = cmd.appLauncherPath().toFile().exists();
            if (cmd.hasArgument("--win-menu")) {
                if (isUserLocalInstall(cmd)) {
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

        private Path startMenuShortcutPath() {
            return Path.of(cmd.getArgumentValue("--win-menu-group",
                    () -> "Unknown"), cmd.name() + ".lnk");
        }

        private void verifyStartMenuShortcut(Path shortcutsRoot, boolean exists) {
            Path shortcutPath = shortcutsRoot.resolve(startMenuShortcutPath());
            verifyShortcut(shortcutPath, exists);
            if (!exists) {
                TKit.assertPathExists(shortcutPath.getParent(), false);
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

        private void verifyFileAssociationsRegistry() {
            Stream.of(cmd.getAllArgumentValues("--file-associations")).map(
                    Path::of).forEach(this::verifyFileAssociationsRegistry);
        }

        private void verifyFileAssociationsRegistry(Path faFile) {
            boolean appInstalled = cmd.appLauncherPath().toFile().exists();
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

        private final JPackageCommand cmd;
    }

    private static String queryRegistryValue(String keyPath, String valueName) {
        Executor.Result status = new Executor()
                .setExecutable("reg")
                .saveOutput()
                .addArguments("query", keyPath, "/v", valueName)
                .execute();
        if (status.exitCode == 1) {
            // Should be the case of no such registry value or key
            String lookupString = "ERROR: The system was unable to find the specified registry key or value.";
            status.getOutput().stream().filter(line -> line.equals(lookupString)).findFirst().orElseThrow(
                    () -> new RuntimeException(String.format(
                            "Failed to find [%s] string in the output",
                            lookupString)));
            TKit.trace(String.format(
                    "Registry value [%s] at [%s] path not found", valueName,
                    keyPath));
            return null;
        }

        String value = status.assertExitCodeIsZero().getOutput().stream().skip(2).findFirst().orElseThrow();
        // Extract the last field from the following line:
        //     Common Desktop    REG_SZ    C:\Users\Public\Desktop
        value = value.split("    REG_SZ    ")[1];

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
