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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import jdk.jpackage.internal.IOUtils;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Functional.ThrowingSupplier;
import jdk.jpackage.test.PackageTest.PackageHandlers;
import jdk.jpackage.internal.RetryExecutor;
import org.xml.sax.SAXException;
import org.w3c.dom.NodeList;

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

    public static PListWrapper readPListFromAppImage(Path appImage) {
        return readPList(appImage.resolve("Contents/Info.plist"));
    }

    public static PListWrapper readPList(Path path) {
        TKit.assertReadableFileExists(path);
        return ThrowingSupplier.toSupplier(() -> readPList(Files.readAllLines(
                path))).get();
    }

    public static PListWrapper readPList(List<String> lines) {
        return readPList(lines.stream());
    }

    public static PListWrapper readPList(Stream<String> lines) {
        return ThrowingSupplier.toSupplier(() -> new PListWrapper(lines
                // Skip leading lines before xml declaration
                .dropWhile(Pattern.compile("\\s?<\\?xml\\b.+\\?>").asPredicate().negate())
                .collect(Collectors.joining()))).get();
    }

    static PackageHandlers createDmgPackageHandlers() {
        PackageHandlers dmg = new PackageHandlers();

        dmg.installHandler = cmd -> {
            withExplodedDmg(cmd, dmgImage -> {
                Executor.of("sudo", "cp", "-r")
                .addArgument(dmgImage)
                .addArgument(getInstallationDirectory(cmd).getParent())
                .execute();
            });
        };
        dmg.unpackHandler = (cmd, destinationDir) -> {
            Path unpackDir = destinationDir.resolve(
                    TKit.removeRootFromAbsolutePath(
                            getInstallationDirectory(cmd)).getParent());
            try {
                Files.createDirectories(unpackDir);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            withExplodedDmg(cmd, dmgImage -> {
                Executor.of("cp", "-r")
                .addArgument(dmgImage)
                .addArgument(unpackDir)
                .execute();
            });
            return destinationDir;
        };
        dmg.uninstallHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.MAC_DMG);
            Executor.of("sudo", "rm", "-rf")
            .addArgument(cmd.appInstallationDirectory())
            .execute();
        };

        return dmg;
    }

    static PackageHandlers createPkgPackageHandlers() {
        PackageHandlers pkg = new PackageHandlers();

        pkg.installHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.MAC_PKG);
            Executor.of("sudo", "/usr/sbin/installer", "-allowUntrusted", "-pkg")
                    .addArgument(cmd.outputBundle())
                    .addArguments("-target", "/")
                    .execute();
        };
        pkg.unpackHandler = (cmd, destinationDir) -> {
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
                    return ".pkg".equals(IOUtils.getSuffix(file.getFileName()));
                }).forEach(ThrowingConsumer.toConsumer(pkgDir -> {
                    // Installation root of the package is stored in
                    // /pkg-info@install-location attribute in $pkgDir/PackageInfo xml file
                    var doc = createDocumentBuilder().parse(
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
        };
        pkg.uninstallHandler = cmd -> {
            cmd.verifyIsOfType(PackageType.MAC_PKG);

            if (Files.exists(getUninstallCommand(cmd))) {
                Executor.of("sudo", "/bin/sh",
                        getUninstallCommand(cmd).toString()).execute();
            } else {
                Executor.of("sudo", "rm", "-rf")
                        .addArgument(cmd.appInstallationDirectory())
                        .execute();
            }
        };

        return pkg;
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
        return Path.of(cmd.getArgumentValue("--install-dir",
                () -> cmd.isRuntime() ? "/Library/Java/JavaVirtualMachines" : "/Applications")).resolve(
                        cmd.name() + (cmd.isRuntime() ? "" : ".app"));
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

    public static final class PListWrapper {
        public String queryValue(String keyName) {
            XPath xPath = XPathFactory.newInstance().newXPath();
            // Query for the value of <string> element preceding <key> element
            // with value equal to `keyName`
            String query = String.format(
                    "//string[preceding-sibling::key = \"%s\"][1]", keyName);
            return ThrowingSupplier.toSupplier(() -> (String) xPath.evaluate(
                    query, doc, XPathConstants.STRING)).get();
        }

        public Boolean queryBoolValue(String keyName) {
            XPath xPath = XPathFactory.newInstance().newXPath();
            // Query boolean element preceding <key> element
            // with value equal to `keyName`
            String query = String.format(
                    "name(//*[preceding-sibling::key = \"%s\"])", keyName);
            String value = ThrowingSupplier.toSupplier(() -> (String) xPath.evaluate(
                    query, doc, XPathConstants.STRING)).get();
            return Boolean.valueOf(value);
        }

        public List<String> queryArrayValue(String keyName) {
            XPath xPath = XPathFactory.newInstance().newXPath();
            // Query string array preceding <key> element with value equal to `keyName`
            String query = String.format(
                    "//array[preceding-sibling::key = \"%s\"]", keyName);
            NodeList list = ThrowingSupplier.toSupplier(() -> (NodeList) xPath.evaluate(
                    query, doc, XPathConstants.NODESET)).get();
            if (list.getLength() != 1) {
                throw new RuntimeException(
                        String.format("Unable to find <array> element for key = \"%s\"]",
                                keyName));
            }

            NodeList childList = list.item(0).getChildNodes();
            List<String> values = new ArrayList(childList.getLength());
            for (int i = 0; i < childList.getLength(); i++) {
                if (childList.item(i).getNodeName().equals("string")) {
                    values.add(childList.item(i).getTextContent());
                }
            }
            return values;
        }

        private PListWrapper(String xml) throws ParserConfigurationException,
                SAXException, IOException {
            doc = createDocumentBuilder().parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));
        }

        private final org.w3c.dom.Document doc;
    }

    private static DocumentBuilder createDocumentBuilder() throws
                ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        dbf.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        return dbf.newDocumentBuilder();
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

    static final Set<Path> CRITICAL_RUNTIME_FILES = Set.of(Path.of(
            "Contents/Home/lib/server/libjvm.dylib"));

    private static final Method getServicePListFileName = initGetServicePListFileName();

    private static final Set<Path> APP_BUNDLE_CONTENTS = Stream.of(
            "Info.plist",
            "MacOS",
            "app",
            "runtime",
            "Resources",
            "PkgInfo",
            "_CodeSignature"
    ).map(Path::of).collect(toSet());

    private static final Set<Path> RUNTIME_BUNDLE_CONTENTS = Stream.of(
            "Home"
    ).map(Path::of).collect(toSet());
}
