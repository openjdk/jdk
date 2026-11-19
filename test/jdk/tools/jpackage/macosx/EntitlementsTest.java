/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.util.PListWriter.writeBoolean;
import static jdk.jpackage.internal.util.PListWriter.writeDict;
import static jdk.jpackage.internal.util.PListWriter.writePList;
import static jdk.jpackage.internal.util.XmlUtils.createXml;
import static jdk.jpackage.internal.util.XmlUtils.toXmlConsumer;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.MacHelper.SignKeyOption;
import jdk.jpackage.test.MacSign;
import jdk.jpackage.test.TKit;

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
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror SigningBase.java
 * @compile -Xlint:all -Werror EntitlementsTest.java
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=EntitlementsTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class EntitlementsTest {

    private static void createEntitlementsFile(Path file, boolean microphone) throws IOException {
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

    public enum EntitlementsSource implements Consumer<JPackageCommand> {
        CMDLINE(cmd -> {
            var macEntitlementsFile = TKit.createTempFile("foo.plist");
            createEntitlementsFile(macEntitlementsFile, true);
            cmd.addArguments("--mac-entitlements", macEntitlementsFile);
        }),
        RESOURCE_DIR(cmd -> {
            if (!cmd.hasArgument("--resource-dir")) {
                cmd.setArgumentValue("--resource-dir", TKit.createTempDirectory("resources"));
            }

            var resourcesDir = Path.of(cmd.getArgumentValue("--resource-dir"));
            createEntitlementsFile(resourcesDir.resolve(cmd.name() + ".entitlements"), false);
        }),
        ;

        EntitlementsSource(ThrowingConsumer<JPackageCommand, ? extends Exception> initializer) {
            this.initializer = toConsumer(initializer);
        }

        @Override
        public void accept(JPackageCommand cmd) {
            initializer.accept(cmd);
        }

        private final Consumer<JPackageCommand> initializer;
    }

    @Test
    @Parameter({"CMDLINE"})
    @Parameter({"RESOURCE_DIR"})
    @Parameter({"CMDLINE", "RESOURCE_DIR"})
    public static void test(EntitlementsSource... entitlementsSources) {
        MacSign.withKeychain(toConsumer(keychain -> {
            test(keychain, Stream.of(entitlementsSources));
        }), SigningBase.StandardKeychain.MAIN.keychain());
    }

    @Test
    @Parameter({"CMDLINE"})
    @Parameter({"RESOURCE_DIR"})
    @Parameter({"CMDLINE", "RESOURCE_DIR"})
    public static void testAppStore(EntitlementsSource... entitlementsSources) {
        MacSign.withKeychain(toConsumer(keychain -> {
            test(keychain, Stream.concat(Stream.of(cmd -> {
                cmd.addArguments("--mac-app-store");
                // Ignore externally supplied runtime as it may have the "bin"
                // directory that will cause jpackage to bail out.
                cmd.ignoreDefaultRuntime(true);
            }), Stream.of(entitlementsSources)));
        }), SigningBase.StandardKeychain.MAIN.keychain());
    }

    private static void test(MacSign.ResolvedKeychain keychain, Stream<Consumer<JPackageCommand>> mutators) {

        var cmd = JPackageCommand.helloAppImage();

        cmd.mutate(MacHelper.useKeychain(keychain)).mutate(new SignKeyOption(
                SignKeyOption.Type.SIGN_KEY_IDENTITY,
                SigningBase.StandardCertificateRequest.CODESIGN,
                keychain
        )::addTo);

        cmd.mutate(new AdditionalLauncher("x")::applyTo);

        mutators.forEach(cmd::mutate);

        cmd.executeAndAssertHelloAppImageCreated();
    }
}
