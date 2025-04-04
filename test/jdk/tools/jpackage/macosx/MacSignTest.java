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

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.MacHelper;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage with --mac-sign
 * @library /test/jdk/tools/jpackage/helpers
 * @library base
 * @build SigningBase
 * @build jdk.jpackage.test.*
 * @build MacSignTest
 * @requires (jpackage.test.MacSignTests == "run")
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=MacSignTest
 *  --jpt-before-run=SigningBase.verifySignTestEnvReady
 */
public class MacSignTest {

    @Test
    public static void testAppContentWarning() throws IOException {

        // Create app content directory with the name known to fail signing.
        // This will trigger jpackage exit with status code "1".
        final var appContent = TKit.createTempDirectory("app-content").resolve("foo.1");
        Files.createDirectory(appContent);
        Files.createFile(appContent.resolve("file"));

        final List<CannedFormattedString> expectedStrings = new ArrayList<>();
        expectedStrings.add(JPackageStringBundle.MAIN.cannedFormattedString("message.codesign.failed.reason.app.content"));

        final var xcodeWarning = JPackageStringBundle.MAIN.cannedFormattedString("message.codesign.failed.reason.xcode.tools");
        if (!MacHelper.isXcodeDevToolsInstalled()) {
            expectedStrings.add(xcodeWarning);
        }

        // --app-content and --type app-image
        // Expect `message.codesign.failed.reason.app.content` message in the log.
        // This is not a fatal error, just a warning.
        // To make jpackage fail, specify bad additional content.
        final var cmd = JPackageCommand.helloAppImage()
                .ignoreDefaultVerbose(true)
                .validateOutput(expectedStrings.toArray(CannedFormattedString[]::new))
                .addArguments("--app-content", appContent)
                .addArguments("--mac-sign")
                .addArguments("--mac-signing-keychain", SigningBase.StandardKeychain.MAIN.spec().keychain().name())
                .addArguments("--mac-app-image-sign-identity", SigningBase.StandardCertificateRequest.APP_IMAGE.spec().name());

        if (MacHelper.isXcodeDevToolsInstalled()) {
            // Check there is no warning about missing xcode command line developer tools.
            cmd.validateOutput(TKit.assertTextStream(xcodeWarning.getValue()).negate());
        }

        cmd.execute(1);
    }
}
