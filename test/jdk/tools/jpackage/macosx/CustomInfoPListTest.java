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

import static java.util.Map.entry;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
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
    public void testAppImage(CustomPListType[] customPLists) throws IOException {
        var cmd = init(JPackageCommand.helloAppImage(), customPLists);
        cmd.executeAndAssertHelloAppImageCreated();
        verifyPListFiles(cmd, customPLists);
    }

    @Test
    @ParameterSupplier("customPLists")
    public void testNativePackage(CustomPListType[] customPLists) {
        new PackageTest().forTypes(PackageType.MAC_PKG).configureHelloApp().addInitializer(cmd -> {
            init(cmd.setFakeRuntime(), customPLists);
        }).addInstallVerifier(cmd -> {
            verifyPListFiles(cmd, customPLists);
        }).run(Action.CREATE_AND_UNPACK);
    }

    @Test
    public void testRuntime() {
        final Path runtimeImage[] = new Path[1];

        new PackageTest().addRunOnceInitializer(() -> {
            runtimeImage[0] = JPackageCommand.createInputRuntimeImage();
        }).addInitializer(cmd -> {
            cmd.ignoreDefaultRuntime(true)
                    .removeArgumentWithValue("--input")
                    .setArgumentValue("--runtime-image", runtimeImage[0]);
            init(cmd, CustomPListType.RUNTIME);
        }).addInstallVerifier(cmd -> {
            verifyPListFiles(cmd, CustomPListType.RUNTIME);
        }).run(Action.CREATE_AND_UNPACK);
    }

    public static Collection<Object[]> customPLists() {
        return Stream.of(
                List.of(CustomPListType.APP),
                List.of(CustomPListType.APP_WITH_FA),
                List.of(CustomPListType.EMBEDDED_RUNTIME),
                List.of(CustomPListType.APP, CustomPListType.EMBEDDED_RUNTIME)
        ).map(list -> {
            return list.toArray(CustomPListType[]::new);
        }).map(arr -> {
            return new Object[] { arr };
        }).toList();
    }

    private static JPackageCommand init(JPackageCommand cmd, CustomPListType... customPLists) throws IOException {
        if (Stream.of(customPLists).anyMatch(Predicate.isEqual(CustomPListType.APP_WITH_FA))) {
            final Path propFile = TKit.createTempFile("fa.properties");
            var map = Map.ofEntries(
                    entry("mime-type", "application/x-jpackage-foo"),
                    entry("extension", "foo"),
                    entry("description", "bar")
            );
            TKit.createPropertiesFile(propFile, map);
            cmd.setArgumentValue("--file-associations", propFile);
        }

        cmd.setArgumentValue("--resource-dir", TKit.createTempDirectory("resources"));
        for (var customPList : customPLists) {
            customPList.createInputPListFile(cmd);
        }
        return cmd;
    }

    private static void verifyPListFiles(JPackageCommand cmd, CustomPListType... customPLists) throws IOException {
        for (var customPList : customPLists) {
            customPList.verifyPListFile(cmd);
        }
    }


    public enum CustomPListType {
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

            final var actualPListPath = actualPListPath(cmd);
            final var actualPList = MacHelper.readPList(actualPListPath);

            Function<PListReader, List<String>> toStringList = plistReader -> {
                return MacHelper.flatMapPList(plistReader).entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(e -> {
                    return String.format("%s: %s", e.getKey(), e.getValue());
                }).toList();
            };

            var expected = toStringList.apply(expectedPList);
            var actual = toStringList.apply(actualPList);

            TKit.assertStringListEquals(expected, actual, String.format("Check contents of [%s] plist file is as expected", actualPListPath));
        }

        private Path actualPListPath(JPackageCommand cmd) {
            final Path bundleRoot;
            switch (this) {
                case EMBEDDED_RUNTIME, RUNTIME -> {
                    bundleRoot = cmd.appRuntimeDirectory();
                }
                default -> {
                    bundleRoot = cmd.appLayout().contentDirectory().getParent();
                }
            }
            return bundleRoot.resolve("Contents/Info.plist");
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
