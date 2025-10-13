/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.test.MacHelper.flatMapPList;
import static jdk.jpackage.test.MacHelper.readPListFromAppImage;
import static jdk.jpackage.test.MacHelper.writeFaPListFragment;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/**
 * Tests generation of app image with --file-associations and mac additional file
 * association arguments. Test will verify that arguments correctly propagated to
 * Info.plist.
 */

/*
 * @test
 * @summary jpackage with --file-associations and mac specific file association args
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build MacFileAssociationsTest
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=480 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacFileAssociationsTest
 */
public class MacFileAssociationsTest {

    @Test
    public static void test() throws Exception {
        final Path propFile = TKit.createTempFile("fa.properties");
        Map<String, String> map = Map.ofEntries(
                entry("mime-type", "application/x-jpackage-foo"),
                entry("extension", "foo"),
                entry("description", "bar"),
                entry("mac.CFBundleTypeRole", "Viewer"),
                entry("mac.LSHandlerRank", "Default"),
                entry("mac.NSDocumentClass", "SomeClass"),
                entry("mac.LSTypeIsPackage", "true"),
                entry("mac.LSSupportsOpeningDocumentsInPlace", "false"),
                entry("mac.UISupportsDocumentBrowser", "false"),
                entry("mac.NSExportableTypes", "public.png, public.jpg"),
                entry("mac.UTTypeConformsTo", "public.image, public.data"));
        TKit.createPropertiesFile(propFile, map);

        final var cmd = JPackageCommand.helloAppImage().setFakeRuntime();
        cmd.addArguments("--file-associations", propFile);
        cmd.executeAndAssertHelloAppImageCreated();

        Function<Map.Entry<String, String>, String> toString = e -> {
            return String.format("%s => %s", e.getKey(), e.getValue());
        };

        final var actualFaProperties = flatMapPList(readPListFromAppImage(cmd.outputBundle())).entrySet().stream().filter(e -> {
            return Stream.of("/CFBundleDocumentTypes", "/UTExportedTypeDeclarations").anyMatch(e.getKey()::startsWith);
        }).sorted(Comparator.comparing(Map.Entry::getKey)).map(toString).toList();

        final var expectedFaProperties = flatMapPList(new PListReader(createXml(xml -> {
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeFaPListFragment(cmd, xml);
                }));
            }));
        }).getNode())).entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).map(toString).toList();

        TKit.assertStringListEquals(expectedFaProperties, actualFaProperties, "Check fa properties in the Info.plist file as expected");
    }
}
