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

import static java.util.Collections.unmodifiableSortedSet;
import static java.util.Map.entry;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/**
 * Test --resource-dir with custom "Info.plist" for the top-level bundle
 * and "Runtime-Info.plist" for the embedded runtime bundle
 */

/*
 * @test
 * @summary jpackage with --type image --resource-dir "Info.plist" and "Runtime-Info.plist"
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build CustomInfoPListTest
 * @requires (os.family == "mac")
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=CustomInfoPListTest
 */
public class CustomInfoPListTest {

    @Test
    @ParameterSupplier("customPLists")
    public void testAppImage(TestConfig cfg) throws Throwable {
        var cmd = cfg.init(JPackageCommand.helloAppImage());
        var verifier = cfg.createPListFilesVerifier(cmd.executePrerequisiteActions());
        cmd.executeAndAssertHelloAppImageCreated();
        verifier.accept(cmd);
    }

    @Test
    @ParameterSupplier("customPLists")
    public void testNativePackage(TestConfig cfg) {
        List<ThrowingConsumer<JPackageCommand>> verifier = new ArrayList<>();
        new PackageTest().configureHelloApp().addInitializer(cmd -> {
            cfg.init(cmd.setFakeRuntime());
        }).addRunOnceInitializer(() -> {
            verifier.add(cfg.createPListFilesVerifier(JPackageCommand.helloAppImage().executePrerequisiteActions()));
        }).addInstallVerifier(cmd -> {
            verifier.get(0).accept(cmd);
        }).run(Action.CREATE_AND_UNPACK);
    }

    @Test
    public void testRuntime() {
        final Path runtimeImage[] = new Path[1];

        var cfg = new TestConfig(Set.of(CustomPListType.RUNTIME));

        new PackageTest().addRunOnceInitializer(() -> {
            runtimeImage[0] = JPackageCommand.createInputRuntimeImage();
        }).addInitializer(cmd -> {
            cmd.ignoreDefaultRuntime(true)
                    .removeArgumentWithValue("--input")
                    .setArgumentValue("--runtime-image", runtimeImage[0]);
            cfg.init(cmd);
        }).addInstallVerifier(cmd -> {
            cfg.createPListFilesVerifier(cmd).accept(cmd);
        }).run(Action.CREATE_AND_UNPACK);
    }

    public static Collection<Object[]> customPLists() {
        return Stream.of(
                Set.of(CustomPListType.APP),
                Set.of(CustomPListType.APP_WITH_FA),
                Set.of(CustomPListType.EMBEDDED_RUNTIME),
                Set.of(CustomPListType.APP, CustomPListType.EMBEDDED_RUNTIME)
        ).map(TestConfig::new).map(cfg -> {
            return new Object[] { cfg };
        }).toList();
    }

    private static List<String> toStringList(PListReader plistReader) {
        return MacHelper.flatMapPList(plistReader).entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> {
            return String.format("%s: %s", e.getKey(), e.getValue());
        }).toList();
    }


    public record TestConfig(Set<CustomPListType> customPLists) {

        public TestConfig {
            Objects.requireNonNull(customPLists);
            if (customPLists.isEmpty()) {
                throw new IllegalArgumentException();
            }
            customPLists = unmodifiableSortedSet(new TreeSet<>(customPLists));
        }

        @Override
        public String toString() {
            return customPLists.stream()
                    .map(CustomPListType::toString)
                    .collect(Collectors.joining("+"));
        }

        JPackageCommand init(JPackageCommand cmd) throws IOException {
            if (customPLists.contains(CustomPListType.APP_WITH_FA)) {
                final Path propFile = TKit.createTempFile("fa.properties");
                final var props = List.of(
                        entry("mime-type", "application/x-jpackage-foo"),
                        entry("extension", "foo"),
                        entry("description", "bar")
                );
                TKit.createPropertiesFile(propFile, props);
                cmd.setArgumentValue("--file-associations", propFile);
            }

            cmd.setArgumentValue("--resource-dir", TKit.createTempDirectory("resources"));
            for (var customPList : customPLists) {
                customPList.createInputPListFile(cmd);
            }
            return cmd;
        }

        ThrowingConsumer<JPackageCommand> createPListFilesVerifier(JPackageCommand cmd) throws IOException {
            ThrowingConsumer<JPackageCommand> defaultVerifier = otherCmd -> {
                for (var customPList : customPLists) {
                    customPList.verifyPListFile(otherCmd);
                }
            };

            var defaultPListFiles = CustomPListType.defaultRoles(customPLists);

            if (defaultPListFiles.isEmpty()) {
                return defaultVerifier;
            } else {
                var vanillaCmd = new JPackageCommand().setFakeRuntime()
                        .addArguments(cmd.getAllArguments())
                        .setPackageType(PackageType.IMAGE)
                        .removeArgumentWithValue("--resource-dir")
                        .setArgumentValue("--dest", TKit.createTempDirectory("vanilla"));
                vanillaCmd.executeIgnoreExitCode().assertExitCodeIsZero();

                return otherCmd -> {
                    defaultVerifier.accept(otherCmd);
                    for (var defaultPListFile : defaultPListFiles) {
                        final var expectedPListPath = defaultPListFile.path(vanillaCmd);
                        final var expectedPList = MacHelper.readPList(expectedPListPath);

                        final var actualPListPath = defaultPListFile.path(otherCmd);
                        final var actualPList = MacHelper.readPList(actualPListPath);

                        var expected = toStringList(expectedPList);
                        var actual = toStringList(actualPList);

                        TKit.assertStringListEquals(expected, actual, String.format(
                                "Check contents of [%s] and [%s] plist files are the same", expectedPListPath, actualPListPath));
                    }
                };
            }
        }
    }


    private enum PListRole {
        MAIN,
        EMBEDDED_RUNTIME,
        ;

        Path path(JPackageCommand cmd) {
            final Path bundleRoot;
            if (cmd.isRuntime() || this == EMBEDDED_RUNTIME) {
                bundleRoot = cmd.appRuntimeDirectory();
            } else {
                bundleRoot = cmd.appLayout().contentDirectory().getParent();
            }
            return bundleRoot.resolve("Contents/Info.plist");
        }
    }


    private enum CustomPListType {
        APP(
                CustomPListFactory.PLIST_INPUT::writeAppPlist,
                CustomPListFactory.PLIST_OUTPUT::writeAppPlist,
                "Info.plist"),

        APP_WITH_FA(APP),

        EMBEDDED_RUNTIME(
                CustomPListFactory.PLIST_INPUT::writeEmbeddedRuntimePlist,
                CustomPListFactory.PLIST_OUTPUT::writeEmbeddedRuntimePlist,
                "Runtime-Info.plist"),

        RUNTIME(
                CustomPListFactory.PLIST_INPUT::writeRuntimePlist,
                CustomPListFactory.PLIST_OUTPUT::writeRuntimePlist,
                "Info.plist"),
        ;

        private CustomPListType(
                ThrowingBiConsumer<JPackageCommand, XMLStreamWriter> inputPlistWriter,
                ThrowingBiConsumer<JPackageCommand, XMLStreamWriter> outputPlistWriter,
                String outputPlistFilename) {
            this.inputPlistWriter = ThrowingBiConsumer.toBiConsumer(inputPlistWriter);
            this.outputPlistWriter = ThrowingBiConsumer.toBiConsumer(outputPlistWriter);
            this.outputPlistFilename = outputPlistFilename;
        }

        private CustomPListType(CustomPListType other) {
            this.inputPlistWriter = other.inputPlistWriter;
            this.outputPlistWriter = other.outputPlistWriter;
            this.outputPlistFilename = other.outputPlistFilename;
        }

        void createInputPListFile(JPackageCommand cmd) throws IOException {
            createXml(Path.of(cmd.getArgumentValue("--resource-dir")).resolve(outputPlistFilename), xml -> {
                inputPlistWriter.accept(cmd, xml);
            });
        }

        void verifyPListFile(JPackageCommand cmd) throws IOException {
            final var expectedPList = new PListReader(createXml(xml -> {
                    outputPlistWriter.accept(cmd, xml);
                }).getNode());

            final var actualPListPath = role().path(cmd);
            final var actualPList = MacHelper.readPList(actualPListPath);

            var expected = toStringList(expectedPList);
            var actual = toStringList(actualPList);

            TKit.assertStringListEquals(expected, actual, String.format("Check contents of [%s] plist file is as expected", actualPListPath));
        }

        PListRole role() {
            if (this == EMBEDDED_RUNTIME) {
                return PListRole.EMBEDDED_RUNTIME;
            } else {
                return PListRole.MAIN;
            }
        }

        static Set<PListRole> defaultRoles(Collection<CustomPListType> customPLists) {
            var result = new HashSet<>(Set.of(PListRole.values()));
            customPLists.stream().<PListRole>mapMulti((customPList, acc) -> {
                if (customPList == CustomPListType.RUNTIME) {
                    List.of(PListRole.values()).forEach(acc::accept);
                } else {
                    acc.accept(customPList.role());
                }
            }).forEach(result::remove);
            return Collections.unmodifiableSet(result);
        }

        private final BiConsumer<JPackageCommand, XMLStreamWriter> inputPlistWriter;
        private final BiConsumer<JPackageCommand, XMLStreamWriter> outputPlistWriter;
        private final String outputPlistFilename;
    }


    private enum CustomPListFactory {
        PLIST_INPUT,
        PLIST_OUTPUT,
        ;

        private void writeAppPlist(JPackageCommand cmd, XMLStreamWriter xml) throws XMLStreamException, IOException {
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeString(xml, "CustomAppProperty", "App");
                    writeString(xml, "CFBundleExecutable", value("DEPLOY_LAUNCHER_NAME", cmd.name()));
                    writeString(xml, "CFBundleIconFile", value("DEPLOY_ICON_FILE", cmd.name() + ".icns"));
                    writeString(xml, "CFBundleIdentifier", value("DEPLOY_BUNDLE_IDENTIFIER", "Hello"));
                    writeString(xml, "CFBundleName", value("DEPLOY_BUNDLE_NAME", cmd.name()));
                    writeString(xml, "CFBundleShortVersionString", value("DEPLOY_BUNDLE_SHORT_VERSION", cmd.version()));
                    writeString(xml, "LSApplicationCategoryType", value("DEPLOY_APP_CATEGORY", "public.app-category.utilities"));
                    writeString(xml, "CFBundleVersion", value("DEPLOY_BUNDLE_CFBUNDLE_VERSION", cmd.version()));
                    writeString(xml, "NSHumanReadableCopyright", value("DEPLOY_BUNDLE_COPYRIGHT",
                            JPackageStringBundle.MAIN.cannedFormattedString("param.copyright.default", new Date()).getValue()));
                    if (cmd.hasArgument("--file-associations")) {
                        if (this == PLIST_INPUT) {
                            xml.writeCharacters("DEPLOY_FILE_ASSOCIATIONS");
                        } else {
                            MacHelper.writeFaPListFragment(cmd, xml);
                        }
                    }
                }));
            }));
        }

        void writeEmbeddedRuntimePlist(JPackageCommand cmd, XMLStreamWriter xml) throws XMLStreamException, IOException {
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeString(xml, "CustomEmbeddedRuntimeProperty", "Embedded runtime");
                    writeString(xml, "CFBundleIdentifier", value("CF_BUNDLE_IDENTIFIER", "Hello"));
                    writeString(xml, "CFBundleName", value("CF_BUNDLE_NAME", cmd.name()));
                    writeString(xml, "CFBundleShortVersionString", value("CF_BUNDLE_SHORT_VERSION_STRING", cmd.version()));
                    writeString(xml, "CFBundleVersion", value("CF_BUNDLE_VERSION", cmd.version()));
                }));
            }));
        }

        void writeRuntimePlist(JPackageCommand cmd, XMLStreamWriter xml) throws XMLStreamException, IOException {
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeString(xml, "CustomRuntimeProperty", "Runtime");
                    writeString(xml, "CFBundleIdentifier", value("CF_BUNDLE_IDENTIFIER", cmd.name()));
                    writeString(xml, "CFBundleName", value("CF_BUNDLE_NAME", cmd.name()));
                    writeString(xml, "CFBundleShortVersionString", value("CF_BUNDLE_SHORT_VERSION_STRING", cmd.version()));
                    writeString(xml, "CFBundleVersion", value("CF_BUNDLE_VERSION", cmd.version()));
                    writeString(xml, "CustomInfoPListFA", "DEPLOY_FILE_ASSOCIATIONS");
                }));
            }));
        }

        private String value(String input, String output) {
            if (this == PLIST_INPUT) {
                return input;
            } else {
                return output;
            }
        }
    }
}
