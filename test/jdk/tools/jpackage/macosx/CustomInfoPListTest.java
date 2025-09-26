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
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=CustomInfoPListTest
 */

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageType;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import javax.xml.stream.XMLOutputFactory;

import jdk.jpackage.test.Annotations.Test;

import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

public class CustomInfoPListTest {

    private static final String BUNDLE_NAME_APP = "CustomAppName";
    private static final String BUNDLE_NAME_EMBEDDED_RUNTIME = "CustomEmbeddedRuntimeName";
    private static final String BUNDLE_NAME_RUNTIME = "CustomRuntimeName";

    // We do not need full Info.plist for testing
    private static String getInfoPListXML(String bundleName) {
        return toSupplier(() -> {
            var buf = new StringWriter();
            var xml = XMLOutputFactory.newInstance().createXMLStreamWriter(buf);
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeString(xml, "CFBundleName", bundleName);
                    writeString(xml, "CFBundleIdentifier", "CustomInfoPListTest");
                    writeString(xml, "CFBundleVersion", "1.0");
                }));
            }));
            xml.flush();
            xml.close();
            return buf.toString();
        }).get();
    }

    private static String getResourceDirWithCustomInfoPList(
                String bundleName, boolean includeRuntimePList) {
        final Path resources = TKit.createTempDirectory("resources");
        try {
            Files.writeString(resources.resolve("Info.plist"),
                    getInfoPListXML(bundleName));
            if (includeRuntimePList) {
                Files.writeString(resources.resolve("Runtime-Info.plist"),
                        getInfoPListXML(BUNDLE_NAME_EMBEDDED_RUNTIME));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return resources.toString();
    }

    @Test
    public void testApp() {
        JPackageCommand cmd = JPackageCommand.helloAppImage()
                .addArguments("--resource-dir",
                        getResourceDirWithCustomInfoPList(BUNDLE_NAME_APP, true));

        cmd.executeAndAssertHelloAppImageCreated();

        var appPList = MacHelper.readPListFromAppImage(cmd.outputBundle());
        TKit.assertEquals(BUNDLE_NAME_APP, appPList.queryValue("CFBundleName"), String.format(
                "Check value of %s plist key", "CFBundleName"));

        var runtimePList = MacHelper.readPListFromEmbeddedRuntime(cmd.outputBundle());
        TKit.assertEquals(BUNDLE_NAME_EMBEDDED_RUNTIME, runtimePList.queryValue("CFBundleName"), String.format(
                "Check value of %s plist key", "CFBundleName"));
    }

    @Test
    public void testRuntime() throws IOException {
        final var runtimeImage = MacHelper.createInputRuntimeImage();

        final var runtimeBundleWorkDir = TKit.createTempDirectory("runtime-bundle");

        final var unpackadeRuntimeBundleDir = runtimeBundleWorkDir.resolve("unpacked");

        var cmd = new JPackageCommand()
                .useToolProvider(true)
                .ignoreDefaultRuntime(true)
                .dumpOutput(true)
                .setPackageType(PackageType.MAC_DMG)
                .setArgumentValue("--name", "foo")
                .addArguments("--runtime-image", runtimeImage)
                .addArguments("--resource-dir",
                    getResourceDirWithCustomInfoPList(BUNDLE_NAME_RUNTIME, false))
                .addArguments("--dest", runtimeBundleWorkDir);

        cmd.execute();

        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            if (dmgImage.endsWith(cmd.name() + ".jdk")) {
                var runtimePList = MacHelper.readPListFromAppImage(dmgImage);
                TKit.assertEquals(BUNDLE_NAME_RUNTIME, runtimePList.queryValue("CFBundleName"),
                        String.format("Check value of %s plist key", "CFBundleName"));
            }
        });
    }
}
