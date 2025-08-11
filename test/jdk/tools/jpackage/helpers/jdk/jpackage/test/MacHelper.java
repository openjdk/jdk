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

import static java.util.stream.Collectors.toSet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.RetryExecutor;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.test.PackageTest.PackageHandlers;

public final class MacHelper {

    public static void withExplodedDmg(JPackageCommand cmd,
            ThrowingConsumer<Path> consumer) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);

        // Explode DMG assuming this can require interaction, thus use `yes`.
        String attachCMD[] = {
            "sh", "-c",
            String.join(" ", "yes", "|", "/usr/bin/hdiutil", "attach",
                        JPackageCommand.escapeAndJoin(
                                cmd.outputBundle().toString()), "-plist")};
        RetryExecutor attachExecutor = new RetryExecutor();
        try {
            // 10 times with 6 second delays.
            attachExecutor.setMaxAttemptsCount(10)
                    .setAttemptTimeoutMillis(6000)
                    .setWriteOutputToFile(true)
                    .saveOutput(true)
                    .execute(attachCMD);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        Path mountPoint = null;
        try {
            var plist = readPList(attachExecutor.getOutput());
            mountPoint = Path.of(plist.queryValue("mount-point"));

            // code here used to copy just <runtime name> or <app name>.app
            // We now have option to include arbitrary content, so we copy
            // everything in the mounted image.
            String[] children = mountPoint.toFile().list();
            for (String child : children) {
                Path childPath = mountPoint.resolve(child);
                TKit.trace(String.format("Exploded [%s] in [%s] directory",
                        cmd.outputBundle(), childPath));
                ThrowingConsumer.toConsumer(consumer).accept(childPath);
            }
        } finally {
            String detachCMD[] = {
                "/usr/bin/hdiutil",
                "detach",
                "-verbose",
                mountPoint.toAbsolutePath().toString()};
            // "hdiutil detach" might not work right away due to resource busy error, so
            // repeat detach several times.
            RetryExecutor detachExecutor = new RetryExecutor();
            // Image can get detach even if we got resource busy error, so stop
            // trying to detach it if it is no longer attached.
            final Path mp = mountPoint;
            detachExecutor.setExecutorInitializer(exec -> {
                if (!Files.exists(mp)) {
                    detachExecutor.abort();
                }
            });
            try {
                // 10 times with 6 second delays.
                detachExecutor.setMaxAttemptsCount(10)
                        .setAttemptTimeoutMillis(6000)
                        .setWriteOutputToFile(true)
                        .saveOutput(true)
                        .execute(detachCMD);
            } catch (IOException ex) {
                if (!detachExecutor.isAborted()) {
                    // Now force to detach if it still attached
                    if (Files.exists(mountPoint)) {
                        Executor.of("/usr/bin/hdiutil", "detach",
                                    "-force", "-verbose")
                                 .addArgument(mountPoint).execute();
                    }
                }
            }
        }
    }

    public static PListReader readPListFromAppImage(Path appImage) {
        return readPList(appImage.resolve("Contents/Info.plist"));
    }

    public static PListReader readPList(Path path) {
        TKit.assertReadableFileExists(path);
        return ThrowingSupplier.toSupplier(() -> readPList(Files.readAllLines(
                path))).get();
    }

    public static PListReader readPList(List<String> lines) {
        return readPList(lines.stream());
    }

    public static PListReader readPList(Stream<String> lines) {
        return ThrowingSupplier.toSupplier(() -> new PListReader(lines
                // Skip leading lines before xml declaration
                .dropWhile(Pattern.compile("\\s?<\\?xml\\b.+\\?>").asPredicate().negate())
                .collect(Collectors.joining()).getBytes(StandardCharsets.UTF_8))).get();
    }

    public static boolean signPredefinedAppImage(JPackageCommand cmd) {
        Objects.requireNonNull(cmd);
        if (!TKit.isOSX()) {
            throw new UnsupportedOperationException();
        }
        return cmd.hasArgument("--mac-sign") && cmd.hasArgument("--app-image");
    }

    public static boolean appImageSigned(JPackageCommand cmd) {
        Objects.requireNonNull(cmd);
        if (!TKit.isOSX()) {
            throw new UnsupportedOperationException();
        }

        if (Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of).map(AppImageFile::load).map(AppImageFile::macSigned).orElse(false)) {
            // The external app image is signed, so the app image is signed too.
            return true;
        }

        if (!cmd.hasArgument("--mac-sign")) {
            return false;
        }

        return (cmd.hasArgument("--mac-signing-key-user-name") || cmd.hasArgument("--mac-app-image-sign-identity"));
    }

    static PackageHandlers createDmgPackageHandlers() {
        return new PackageHandlers(MacHelper::installDmg, MacHelper::uninstallDmg, MacHelper::unpackDmg);
    }

    private static int installDmg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        withExplodedDmg(cmd, dmgImage -> {
            Executor.of("sudo", "cp", "-R")
                    .addArgument(dmgImage)
                    .addArgument(getInstallationDirectory(cmd).getParent())
                    .execute(0);
        });
        return 0;
    }

    private static void uninstallDmg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        Executor.of("sudo", "rm", "-rf")
        .addArgument(cmd.appInstallationDirectory())
        .execute();
    }

    private static Path unpackDmg(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.MAC_DMG);
        Path unpackDir = destinationDir.resolve(
                TKit.removeRootFromAbsolutePath(
                        getInstallationDirectory(cmd)).getParent());
        try {
            Files.createDirectories(unpackDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        withExplodedDmg(cmd, dmgImage -> {
            Executor.of("cp", "-R")
            .addArgument(dmgImage)
            .addArgument(unpackDir)
            .execute();
        });
        return destinationDir;
    }

    static PackageHandlers createPkgPackageHandlers() {
        return new PackageHandlers(MacHelper::installPkg, MacHelper::uninstallPkg, MacHelper::unpackPkg);
    }

    private static int installPkg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return Executor.of("sudo", "/usr/sbin/installer", "-allowUntrusted", "-pkg")
                .addArgument(cmd.outputBundle())
                .addArguments("-target", "/")
                .execute().getExitCode();
    }

    private static void uninstallPkg(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        if (Files.exists(getUninstallCommand(cmd))) {
            Executor.of("sudo", "/bin/sh",
                    getUninstallCommand(cmd).toString()).execute();
        } else {
            Executor.of("sudo", "rm", "-rf")
                    .addArgument(cmd.appInstallationDirectory())
                    .execute();
        }
    }

    private static Path unpackPkg(JPackageCommand cmd, Path destinationDir) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);

        var dataDir = destinationDir.resolve("data");

        Executor.of("pkgutil", "--expand")
                .addArgument(cmd.outputBundle())
                .addArgument(dataDir) // We need non-existing folder
                .execute();

        final Path unpackRoot = destinationDir.resolve("unpacked");

        // Unpack all ".pkg" files from $dataDir folder in $unpackDir folder
        try (var dataListing = Files.list(dataDir)) {
            dataListing.filter(file -> {
                return ".pkg".equals(PathUtils.getSuffix(file.getFileName()));
            }).forEach(ThrowingConsumer.toConsumer(pkgDir -> {
                // Installation root of the package is stored in
                // /pkg-info@install-location attribute in $pkgDir/PackageInfo xml file
                var doc = XmlUtils.initDocumentBuilder().parse(
                        new ByteArrayInputStream(Files.readAllBytes(
                                pkgDir.resolve("PackageInfo"))));
                var xPath = XPathFactory.newInstance().newXPath();

                final String installRoot = (String) xPath.evaluate(
                        "/pkg-info/@install-location", doc,
                        XPathConstants.STRING);

                final Path unpackDir = unpackRoot.resolve(
                        TKit.removeRootFromAbsolutePath(Path.of(installRoot)));

                Files.createDirectories(unpackDir);

                Executor.of("tar", "-C")
                        .addArgument(unpackDir)
                        .addArgument("-xvf")
                        .addArgument(pkgDir.resolve("Payload"))
                        .execute();
            }));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return unpackRoot;
    }

    static void verifyBundleStructure(JPackageCommand cmd) {
        final Path bundleRoot;
        if (cmd.isImagePackageType()) {
            bundleRoot = cmd.outputBundle();
        } else {
            bundleRoot = cmd.pathToUnpackedPackageFile(
                    cmd.appInstallationDirectory());
        }

        TKit.assertDirectoryContent(bundleRoot).match(Path.of("Contents"));

        final var contentsDir = bundleRoot.resolve("Contents");
        final var expectedContentsItems = cmd.isRuntime() ? RUNTIME_BUNDLE_CONTENTS : APP_BUNDLE_CONTENTS;

        var contentsVerifier = TKit.assertDirectoryContent(contentsDir);
        if (!cmd.hasArgument("--app-content")) {
            contentsVerifier.match(expectedContentsItems);
        } else {
            // Additional content added to the bundle.
            // Verify there is no period (.) char in the names of additional directories if any.
            contentsVerifier.contains(expectedContentsItems);
            contentsVerifier = contentsVerifier.removeAll(expectedContentsItems);
            contentsVerifier.match(contentsVerifier.items().stream().filter(path -> {
                if (Files.isDirectory(contentsDir.resolve(path))) {
                    return !path.getFileName().toString().contains(".");
                } else {
                    return true;
                }
            }).collect(toSet()));
        }
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC);
        return String.format("%s-%s%s", getPackageName(cmd), cmd.version(),
                cmd.packageType().getSuffix());
    }

    static Path getInstallationDirectory(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC);

        final var defaultInstallLocation = Path.of(
                cmd.isRuntime() ? "/Library/Java/JavaVirtualMachines" : "/Applications");

        final Path installLocation;
        if (cmd.packageType() == PackageType.MAC_DMG) {
            installLocation = defaultInstallLocation;
        } else {
            installLocation = cmd.getArgumentValue("--install-dir", () -> defaultInstallLocation, Path::of);
        }

        return installLocation.resolve(cmd.name() + (cmd.isRuntime() ? ".jdk" : ".app"));
    }

    static Path getUninstallCommand(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return cmd.pathToUnpackedPackageFile(Path.of(
                "/Library/Application Support", getPackageName(cmd),
                "uninstall.command"));
    }

    static Path getServicePlistFilePath(JPackageCommand cmd, String launcherName) {
        cmd.verifyIsOfType(PackageType.MAC_PKG);
        return cmd.pathToUnpackedPackageFile(
                Path.of("/Library/LaunchDaemons").resolve(
                        getServicePListFileName(getPackageId(cmd),
                                Optional.ofNullable(launcherName).orElseGet(
                                        cmd::name))));
    }

    private static String getPackageName(JPackageCommand cmd) {
        return cmd.getArgumentValue("--mac-package-name", cmd::installerName);
    }

    private static String getPackageId(JPackageCommand cmd) {
        return cmd.getArgumentValue("--mac-package-identifier", () -> {
            return cmd.getArgumentValue("--main-class", cmd::name, className -> {
                return JavaAppDesc.parse(className).packageName();
            });
        });
    }

    public static boolean isXcodeDevToolsInstalled() {
        return Inner.XCODE_DEV_TOOLS_INSTALLED;
    }

    private static String getServicePListFileName(String packageName,
            String launcherName) {
        try {
            return getServicePListFileName.invoke(null, packageName,
                    launcherName).toString();
        } catch (InvocationTargetException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Method initGetServicePListFileName() {
        try {
            return Class.forName(
                    "jdk.jpackage.internal.MacLaunchersAsServices").getMethod(
                            "getServicePListFileName", String.class, String.class);
        } catch (ClassNotFoundException ex) {
            if (TKit.isOSX()) {
                throw new RuntimeException(ex);
            } else {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class Inner {
        private static final boolean XCODE_DEV_TOOLS_INSTALLED =
                Executor.of("/usr/bin/xcrun", "--help").executeWithoutExitCodeCheck().getExitCode() == 0;
    }

    private static Set<Path> createBundleContents(String... customItems) {
        return Stream.concat(Stream.of(customItems), Stream.of(
                "MacOS",
                "Info.plist",
                "_CodeSignature"
        )).map(Path::of).collect(toSet());
    }

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "Contents/Home/lib/server/libjvm.dylib"));

    private static final Method getServicePListFileName = initGetServicePListFileName();

    private static final Set<Path> APP_BUNDLE_CONTENTS = createBundleContents(
            "app",
            "runtime",
            "Resources",
            "PkgInfo"
    );

    private static final Set<Path> RUNTIME_BUNDLE_CONTENTS = createBundleContents(
            "Home"
    );
}
