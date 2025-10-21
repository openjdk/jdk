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

import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.PListWriter.writeBoolean;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;

import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;

/*
 * Test generates signed app-image with custom entitlements file from the
 * "--mac-entitlements" parameter and the resource directory. Following cases
 * are covered:
 * - Custom entitlements file in the resource directory.
 * - Custom entitlements file specified with the "--mac-entitlements" parameter.
 * - Custom entitlements file in the resource directory and specified with the
 * "--mac-entitlements" parameter.
 */

/*
 * @test
 * @summary jpackage with --type app-image "--mac-entitlements" parameter
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build EntitlementsTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=EntitlementsTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class EntitlementsTest {

    void createEntitlementsFile(Path file, boolean microphone) throws IOException {
        createXml(file, xml -> {
            writePList(xml, toXmlConsumer(() -> {
                writeDict(xml, toXmlConsumer(() -> {
                    writeBoolean(xml, "com.apple.security.cs.allow-jit", true);
                    writeBoolean(xml, "com.apple.security.cs.allow-unsigned-executable-memory", true);
                    writeBoolean(xml, "com.apple.security.cs.disable-library-validation", true);
                    writeBoolean(xml, "com.apple.security.cs.allow-dyld-environment-variables", true);
                    writeBoolean(xml, "com.apple.security.cs.debugger", true);
                    writeBoolean(xml, "com.apple.security.device.audio-input", true);
                    writeBoolean(xml, "com.apple.security.device.microphone", microphone);
                }));
            }));
        });
    }

    @Test
    // ({"--mac-app-store", doMacEntitlements", "doResources"})
    @Parameter({"false", "true", "false"})
    @Parameter({"false", "false", "true"})
    @Parameter({"false", "true", "true"})
    @Parameter({"true", "true", "false"})
    @Parameter({"true", "false", "true"})
    @Parameter({"true", "true", "true"})
    public void test(boolean appStore, boolean doMacEntitlements, boolean doResources) throws Exception {
        final Path macEntitlementsFile;
        final Path resourcesDir;

        if (doMacEntitlements) {
            macEntitlementsFile = TKit.createTempFile("EntitlementsTest.plist");
            createEntitlementsFile(macEntitlementsFile, true);
        } else {
            macEntitlementsFile = null;
        }

        if (doResources) {
            resourcesDir = TKit.createTempDirectory("resources");
            createEntitlementsFile(resourcesDir.resolve("EntitlementsTest.entitlements"), false);
        } else {
            resourcesDir = null;
        }

        JPackageCommand cmd = JPackageCommand.helloAppImage()
                .addArguments("--mac-sign", "--mac-signing-keychain",
                        SigningBase.getKeyChain(), "--mac-app-image-sign-identity",
                        SigningBase.getAppCert(SigningBase.CertIndex.ASCII_INDEX.value()));
        if (appStore) {
            cmd.addArguments("--mac-app-store");
        }
        if (doMacEntitlements) {
            cmd.addArguments("--mac-entitlements",
                    macEntitlementsFile.toAbsolutePath().toString());
        }
        if (doResources) {
            cmd.addArguments("--resource-dir",
                    resourcesDir.toAbsolutePath().toString());
        }

        cmd.executeAndAssertHelloAppImageCreated();
    }
}
