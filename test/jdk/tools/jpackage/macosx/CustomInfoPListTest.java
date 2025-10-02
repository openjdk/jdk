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
 * @requires (jpackage.test.SQETest == null)
 * @run main/othervm/timeout=1440 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=CustomInfoPListTest
 */

import jdk.jpackage.test.TKit;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageType;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Executor;

import javax.xml.stream.XMLOutputFactory;

import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;

import jdk.jpackage.internal.util.XmlUtils;
import jdk.jpackage.internal.util.PListReader;

import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writeString;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

public class CustomInfoPListTest {

    private static final String APP_PLIST_KEY = "CustomAppPList";
    private static final String EMBEDDED_RUNTIME_PLIST_KEY = "CustomEmbeddedRuntimePList";
    private static final String RUNTIME_PLIST_KEY = "CustomRuntimePList";

    private static final Map<String, String> appKeyValue = new HashMap<>();
    private static final Map<String, String> embeddedRuntimeKeyValue = new HashMap<>();
    private static final Map<String, String> runtimeKeyValue = new HashMap<>();

    static {
        appKeyValue.put("CFBundleExecutable", "AppCustomInfoPListTest");
        appKeyValue.put("CFBundleIconFile", "AppCustomInfoPListTest.icns");
        appKeyValue.put("CFBundleIdentifier", "Hello");
        appKeyValue.put("CFBundleName", "AppCustomInfoPListTest");
        appKeyValue.put("CFBundleShortVersionString", "1.0");
        appKeyValue.put("LSApplicationCategoryType", "public.app-category.utilities");
        appKeyValue.put("CFBundleVersion", "1.0");
        appKeyValue.put("NSHumanReadableCopyright", JPackageStringBundle.MAIN.cannedFormattedString(
                "param.copyright.default", new Date()).getValue());
        appKeyValue.put("UTTypeIdentifier", "Hello.foo");
        appKeyValue.put("UTTypeDescription", "bar");

        embeddedRuntimeKeyValue.put("CFBundleIdentifier", "Hello");
        embeddedRuntimeKeyValue.put("CFBundleName", "AppCustomInfoPListTest");
        embeddedRuntimeKeyValue.put("CFBundleShortVersionString", "1.0");
        embeddedRuntimeKeyValue.put("CFBundleVersion", "1.0");

        runtimeKeyValue.put("CFBundleIdentifier", "foo");
        runtimeKeyValue.put("CFBundleName", "foo");
        runtimeKeyValue.put("CFBundleShortVersionString", "1.0");
        runtimeKeyValue.put("CFBundleVersion", "1.0");
    }

    // We do not need full and valid Info.plist for testing
    private static void createInfoPListFile(String key, Path plistFile) {
        try {
            XmlUtils.createXml(plistFile, xml -> {
                writePList(xml, toXmlConsumer(() -> {
                    writeDict(xml, toXmlConsumer(() -> {
                        writeString(xml, "CustomInfoPListTestKey", key);
                        if (key.equals(APP_PLIST_KEY)) {
                            // Application
                            writeString(xml, "CFBundleExecutable", "DEPLOY_LAUNCHER_NAME");
                            writeString(xml, "CFBundleIconFile", "DEPLOY_ICON_FILE");
                            writeString(xml, "CFBundleIdentifier", "DEPLOY_BUNDLE_IDENTIFIER");
                            writeString(xml, "CFBundleName", "DEPLOY_BUNDLE_NAME");
                            writeString(xml, "CFBundleShortVersionString", "DEPLOY_BUNDLE_SHORT_VERSION");
                            writeString(xml, "LSApplicationCategoryType", "DEPLOY_APP_CATEGORY");
                            writeString(xml, "CFBundleVersion", "DEPLOY_BUNDLE_CFBUNDLE_VERSION");
                            writeString(xml, "NSHumanReadableCopyright", "DEPLOY_BUNDLE_COPYRIGHT");
                            writeString(xml, "CustomInfoPListFA", "DEPLOY_FILE_ASSOCIATIONS");
                        } else if (key.equals(EMBEDDED_RUNTIME_PLIST_KEY) || key.equals(RUNTIME_PLIST_KEY)) {
                            // Embedded runtime and runtime
                            writeString(xml, "CFBundleIdentifier", "CF_BUNDLE_IDENTIFIER");
                            writeString(xml, "CFBundleName", "CF_BUNDLE_NAME");
                            writeString(xml, "CFBundleShortVersionString", "CF_BUNDLE_SHORT_VERSION_STRING");
                            writeString(xml, "CFBundleVersion", "CF_BUNDLE_VERSION");
                        }
                    }));
                }));
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String getResourceDirWithCustomInfoPList(
                String key, boolean includeMainPList, boolean includeRuntimePList) {
        final Path resources = TKit.createTempDirectory("resources");
        if (includeMainPList) {
            createInfoPListFile(key, resources.resolve("Info.plist"));
        }
        if (includeRuntimePList) {
            createInfoPListFile(EMBEDDED_RUNTIME_PLIST_KEY, resources.resolve("Runtime-Info.plist"));
        }
        return resources.toString();
    }

    private static void validateInfoPListFileKey(PListReader plistFile, Optional<String> key) {
        if (key.isPresent()) {
            TKit.assertEquals(key.get(), plistFile.queryValue("CustomInfoPListTestKey"), String.format(
                    "Check value of %s plist key", "CustomInfoPListTestKey"));
        } else {
            boolean exceptionThrown = false;
            try {
                plistFile.queryValue("CustomInfoPListTestKey");
            } catch (NoSuchElementException ex) {
                exceptionThrown = true;
            }
            TKit.assertTrue(exceptionThrown, "NoSuchElementException exception not thrown");
        }
    }

    private static void validateInfoPList(PListReader plistFile, Map<String, String> values) {
        values.forEach((key, value) -> {
            TKit.assertEquals(value, plistFile.queryValue(key), String.format(
                    "Check value of %s plist key", key));
        });
    }

    @Test
    @Parameter({"TRUE", "FALSE"})
    @Parameter({"FALSE", "TRUE"})
    @Parameter({"TRUE", "TRUE"})
    public void testApp(boolean includeMainPList, boolean includeRuntimePList) {
        final Path propFile = TKit.workDir().resolve("fa.properties");
        TKit.createPropertiesFile(propFile, Map.of(
                "mime-type", "application/x-jpackage-foo",
                "extension", "foo",
                "description", "bar"
            ));

        JPackageCommand cmd = JPackageCommand.helloAppImage()
                .addArguments("--resource-dir",
                        getResourceDirWithCustomInfoPList(APP_PLIST_KEY,
                                includeMainPList, includeRuntimePList))
                .addArguments("--file-associations", propFile);

        cmd.executeAndAssertHelloAppImageCreated();

        var appPList = MacHelper.readPListFromAppImage(cmd.outputBundle());
        if (includeMainPList) {
            validateInfoPListFileKey(appPList, Optional.of(APP_PLIST_KEY));
            validateInfoPList(appPList, appKeyValue);
        } else {
            validateInfoPListFileKey(appPList, Optional.empty());
        }

        var runtimePList = MacHelper.readPListFromEmbeddedRuntime(cmd.outputBundle());
        if (includeRuntimePList) {
            validateInfoPListFileKey(runtimePList, Optional.of(EMBEDDED_RUNTIME_PLIST_KEY));
            validateInfoPList(runtimePList, embeddedRuntimeKeyValue);
        } else {
            validateInfoPListFileKey(runtimePList, Optional.empty());
        }
    }

    @Test
    public void testRuntime() throws IOException {
        final var runtimeImage = JPackageCommand.createInputRuntimeImage();

        final var runtimeBundleWorkDir = TKit.createTempDirectory("runtime-bundle");

        var cmd = new JPackageCommand()
                .useToolProvider(true)
                .ignoreDefaultRuntime(true)
                .dumpOutput(true)
                .setPackageType(PackageType.MAC_DMG)
                .setArgumentValue("--name", "foo")
                .addArguments("--runtime-image", runtimeImage)
                .addArguments("--resource-dir",
                    getResourceDirWithCustomInfoPList(RUNTIME_PLIST_KEY, true, false))
                .addArguments("--dest", runtimeBundleWorkDir);

        cmd.execute();

        MacHelper.withExplodedDmg(cmd, dmgImage -> {
            if (dmgImage.endsWith(cmd.appInstallationDirectory().getFileName())) {
                var runtimePList = MacHelper.readPListFromAppImage(dmgImage);
                validateInfoPListFileKey(runtimePList, Optional.of(RUNTIME_PLIST_KEY));
                validateInfoPList(runtimePList, runtimeKeyValue);
            }
        });
    }
}
