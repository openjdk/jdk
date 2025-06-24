/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.test.ApplicationLayout.linuxUsrTreePackageImage;
import static jdk.jpackage.test.ApplicationLayout.platformAppImage;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.JPackageUserScript.WinGlobals;

final class ConfigFilesStasher {

    private enum RootDirExpr {
        CONFIG_DIR("fs.GetFolder(fs.GetParentFolderName(WScript.ScriptFullName))", "${0%/*}"),
        IMAGE_DIR("fs.GetFolder(shell.CurrentDirectory)", "${PWD}");

        private RootDirExpr(String jsExpr, String shellExpr) {
            this.expr = TKit.isWindows() ? jsExpr : shellExpr;
        }

        String expr() {
            return expr;
        }

        SrcPath srcPath(WildcardPath path) {
            return new SrcPath(this, path);
        }

        SrcPath srcPath(Path path) {
            return srcPath(WildcardPath.create(path));
        }

        private final String expr;
    }

    private static Path prepareStashDir(JPackageCommand cmd, Path stashRoot) throws IOException {
        final var pwd = Path.of("").toAbsolutePath();
        final var absTestWorkDir = TKit.workDir().toAbsolutePath();
        final var relativeTestWorkDir = pwd.relativize(absTestWorkDir).normalize();
        if (relativeTestWorkDir.getNameCount() >= absTestWorkDir.getNameCount()) {
            throw new UnsupportedOperationException();
        }

        final var stashTestRoot = stashRoot.resolve(relativeTestWorkDir);

        if (!absTestWorkDir.equals(currentTestWorkDir)) {
            currentTestWorkDir = absTestWorkDir;
            TKit.deleteDirectoryContentsRecursive(stashTestRoot);
        }

        final var stashTestBasedir = stashTestRoot.resolve(stashDirBasedir(cmd));
        Files.createDirectories(stashTestBasedir);

        final var stashDir = TKit.createUniquePath(stashTestBasedir.resolve("item"));

        Files.createDirectories(stashDir);

        return stashDir;
    }

    private static Path stashDirBasedir(JPackageCommand cmd) {
        if (cmd.isImagePackageType()) {
            return Path.of("app-image", OperatingSystem.current().name().toLowerCase());
        } else {
            return Path.of(cmd.packageType().getType());
        }
    }

    private static void stash(JPackageCommand cmd, Path stashRoot) throws IOException {
        final var script = createStashScript(cmd,  stashRoot);
        if (!script.isEmpty()) {
            if (cmd.isImagePackageType()) {
                final var stashScript = TKit.createTempFile("stash-script" + JPackageUserScript.scriptFilenameExtension());
                JPackageUserScript.create(stashScript, script);
                var pwd = cmd.outputBundle();
                if (TKit.isWindows()) {
                    pwd = WindowsHelper.toShortPath(pwd.toAbsolutePath()).orElse(pwd);
                }
                Executor.of(shell(), stashScript.toAbsolutePath().toString()).setDirectory(pwd).dumpOutput().execute();
            } else {
                JPackageUserScript.POST_IMAGE.create(cmd, script);
            }
        }
    }

    private static List<String> createStashScript(JPackageCommand cmd, Path stashRoot) throws IOException {
        if ((cmd.packageType() == PackageType.WIN_EXE)) {
            // Skip for exe, msi is sufficient.
            return List.of();
        }

        if (!cmd.isImagePackageType() && Files.exists(setupDirectory(cmd, "--resource-dir").resolve(JPackageUserScript.POST_IMAGE.scriptName(cmd)))) {
            // The command already has a "post-image" script configured, don't disturb it.
            return List.of();
        }

        final var stashDir = prepareStashDir(cmd, stashRoot);

        final List<String> script = new ArrayList<>();
        if (TKit.isWindows()) {
            List.of(WinGlobals.JS_LIST_DIR_RECURSIVE, WinGlobals.JS_SHELL, WinGlobals.JS_FS).forEach(v -> {
                v.appendTo(script::addAll);
            });
        } else {
            script.addAll(List.of("set -e", "set -o pipefail"));
        }

        script.addAll(listAppImage(stashDir.resolve("app-image-listing.txt")));

        if (!cmd.isImagePackageType()) {
            script.addAll(copyDirExpr(RootDirExpr.CONFIG_DIR, Path.of(""), stashDir.resolve("config")));
        }

        if (cmd.packageType() == PackageType.LINUX_DEB) {
            script.addAll(copyDirExpr(RootDirExpr.IMAGE_DIR, Path.of("DEBIAN"), stashDir.resolve("DEBIAN")));
        }

        if (cmd.packageType() == PackageType.LINUX_RPM) {
            script.addAll(copyDirExpr(RootDirExpr.CONFIG_DIR, Path.of("../SPECS"), stashDir.resolve("SPECS")));
        }

        if (cmd.isRuntime()) {
            // Runtume packages don't have "Info.plist", though they should. See JDK-8351073
            return script;
        }

        final var appLayout = appImageAppLayout(cmd);

        script.addAll(copyFileExpr(RootDirExpr.IMAGE_DIR, WildcardPath.create(appLayout.appDirectory(), "*.cfg"), stashDir.resolve("cfg-files")));

        if (TKit.isLinux()) {
            for (final var wildcard : List.of("*.desktop", "*-MimeInfo.xml")) {
                script.addAll(copyFileExpr(RootDirExpr.IMAGE_DIR, WildcardPath.create(appLayout.desktopIntegrationDirectory(), wildcard), stashDir.resolve("desktop")));
            }
            if (isWithServices(cmd)) {
                script.addAll(copyDirExpr(RootDirExpr.IMAGE_DIR, Path.of("lib/systemd/system"), stashDir.resolve("systemd")));
            }
        }

        if (cmd.packageType() == PackageType.MAC_PKG) {
            if (isWithServices(cmd)) {
                script.addAll(copyDirExpr(RootDirExpr.CONFIG_DIR, Path.of("../services"), stashDir.resolve("services")));
                script.addAll(copyDirExpr(RootDirExpr.CONFIG_DIR, Path.of("../support"), stashDir.resolve("support")));
            }
        }

        if (TKit.isOSX()) {
            script.addAll(copyFileExpr(RootDirExpr.IMAGE_DIR, WildcardPath.create(appLayout.contentDirectory().resolve("Info.plist")), stashDir.resolve("Info.plist")));
        }

        return script;
    }

    private static String shell() {
        if (OperatingSystem.isWindows()) {
            return "cscript";
        }
        return Optional.ofNullable(System.getenv("SHELL")).orElseGet(() -> "sh");
    }

    private static ApplicationLayout appImageAppLayout(JPackageCommand cmd) {
        if (cmd.isRuntime()) {
            throw new UnsupportedOperationException();
        }

        if (cmd.isImagePackageType()) {
            return platformAppImage();
        }

        if (PackageType.LINUX.contains(cmd.packageType())) {
            final var installDir = cmd.getArgumentValue("--install-dir");
            if (Optional.ofNullable(installDir).map(Set.of("/usr", "/usr/local")::contains).orElse(false)) {
                return linuxUsrTreePackageImage(Path.of("/").relativize(Path.of(installDir)), LinuxHelper.getPackageName(cmd));
            } else {
                return platformAppImage().resolveAt(Path.of("/").relativize(cmd.appInstallationDirectory()));
            }
        } else {
            return platformAppImage();
        }
    }

    private static boolean isWithServices(JPackageCommand cmd) {
        boolean[] withServices = new boolean[1];
        withServices[0] = cmd.hasArgument("--launcher-as-service");
        if (!withServices[0]) {
            AdditionalLauncher.forEachAdditionalLauncher(cmd, (launcherName, propertyFilePath) -> {
                try {
                    final var launcherAsService = new AdditionalLauncher.PropertyFile(propertyFilePath)
                            .getPropertyBooleanValue("launcher-as-service").orElse(false);
                    if (launcherAsService) {
                        withServices[0] = true;
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }
        return withServices[0];
    }

    private static List<String> listAppImage(Path to) {
        final var fromPath = RootDirExpr.IMAGE_DIR.srcPath(Path.of(""));
        if (TKit.isWindows()) {
            final String toStr = to.toString().replace('\\', '/');
            return List.of(
                    String.format("WScript.Echo('Save listing of %s into [%s]')",
                            fromPath.label(), toStr),
                    String.format("var o = fs.CreateTextFile('%s', true)", toStr),
                    String.format("listDir(fs.GetFolder(%s), fs.GetFolder(%s))", fromPath.expr(), fromPath.expr()),
                    "o.Close()"
            );
        } else {
            return List.of(
                    String.format("printf 'Save listing of %%s into [%%s]\\n' '%s' '%s'",
                            fromPath.label(), to),
                    String.format("(cd %s; find . > '%s')", fromPath.expr(), to)
            );
        }
    }

    private static List<String> copyDirExpr(RootDirExpr root, Path from, Path to) {
        return copyExpr(root, WildcardPath.create(from), to, new Expr("fs.CopyFolder(%s, '%s')", "cp -R %s '%s'"));
    }

    private static List<String> copyFileExpr(RootDirExpr root, WildcardPath from, Path to) {
        return copyExpr(root, from, to, new Expr("fs.CopyFile(%s, '%s')", "cp %s '%s'"));
    }

    private static List<String> copyExpr(RootDirExpr root, WildcardPath from, Path to, Expr copyFormatExpr) {
        final String format = new Expr(
                "WScript.Echo('Copy %s into [%s]')\n%s",
                "printf 'Copy %%s into [%%s]\\n' '%s' '%s'\n%s"
        ).value();

        final var toStr = to.toString().replace('\\', '/');
        final var fromPath = root.srcPath(from);

        final String copyExpr;
        if (from.wildcardName().isEmpty()) {
            copyExpr = String.format(copyFormatExpr.value(), fromPath.expr(), toStr);
        } else {
            // To copy wildcard names, the destination directory must exist.
            final var mkdirExpr = (new Expr(
                    String.format("if (!fs.FolderExists('%s')) fs.CreateFolder('%s')", toStr, toStr),
                    String.format("mkdir -p '%s'", toStr)
            ).value());

            final var copyExprBody = Stream.of(
                    mkdirExpr,
                    String.format(copyFormatExpr.value(), fromPath.expr(), toStr + '/')
            ).map(str -> {
                return "  " + str;
            }).collect(joining("\n"));

            // Copying will fail for a wildcard expression without matches, make it conditional.
            copyExpr = new Expr(
                    String.format("if (fs.FolderExists(%s) || fs.FileExists(%s)) {\n%s\n}", fromPath.expr(), fromPath.expr(), copyExprBody),
                    String.format("v=$(printf '%%s' %s)\nif [ \"${v%%/'%s'}\" = \"$v\" ]; then\n%s\nfi", fromPath.expr(), from.wildcardName(), copyExprBody)
            ).value();
        }

        return List.of(String.format(format, fromPath.label(), toStr, copyExpr));
    }

    private record WildcardPath(Path basedir, String wildcardName) {
        WildcardPath {
            Objects.requireNonNull(basedir);
            Objects.requireNonNull(wildcardName);
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(basedir);
            if (!wildcardName.isEmpty()) {
                sb.append(File.separator);
                sb.append(wildcardName);
            }
            return sb.toString();
        }

        static WildcardPath create(Path path) {
            return new WildcardPath(path, "");
        }

        static WildcardPath create(Path basedir, String wildcardName) {
            return new WildcardPath(basedir, wildcardName);
        }
    }

    private record Expr(String jsValue, String shellValue) {
        Expr {
            Objects.requireNonNull(jsValue);
            Objects.requireNonNull(shellValue);
        }

        String value() {
            if (TKit.isWindows()) {
                return jsValue;
            } else {
                return shellValue;
            }
        }
    }

    private record SrcPath(RootDirExpr root, WildcardPath path) {
        SrcPath {
            Objects.requireNonNull(root);
            if (path.basedir().isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        String label() {
            if (isPathEmpty()) {
                return String.format("${%s}", root.name());
            } else {
                return String.format("${%s}/%s", root.name(), path.toString().replace('\\', '/'));
            }
        }

        String expr() {
            if (isPathEmpty()) {
                return new Expr(root.expr(), String.format("\"%s\"", root.expr())).value();
            } else {
                final String shellExpr;
                if (path.wildcardName().isEmpty()) {
                    shellExpr = String.format("\"%s/%s\"", root.expr(), path);
                } else {
                    shellExpr = String.format("\"%s/%s\"/%s", root.expr(), path.basedir(), path.wildcardName());
                }
                return new Expr(String.format("%s + '/%s'", root.expr(), path.toString().replace('\\', '/')), shellExpr).value();
            }
        }

        private boolean isPathEmpty() {
            return path.toString().isEmpty();
        }
    }

    private static Path setupDirectory(JPackageCommand cmd, String argName) {
        if (!cmd.hasArgument(argName)) {
            // Use absolute path as jpackage can be executed in another directory
            cmd.setArgumentValue(argName, TKit.createTempDirectory("stash-script-resource-dir").toAbsolutePath());
        }

        return Path.of(cmd.getArgumentValue(argName));
    }

    private static Path currentTestWorkDir;

    static final Consumer<JPackageCommand> INSTANCE = Optional.ofNullable(TKit.getConfigProperty("stash-root"))
            .map(Path::of).map(Path::toAbsolutePath).<Consumer<JPackageCommand>>map(stashRoot -> {
                return toConsumer(cmd -> {
                    stash(cmd, stashRoot);
                });
            }).orElse(cmd -> {});
}
