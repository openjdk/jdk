/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.List;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Test jpackage signing options errors
 * @library /test/jdk/tools/jpackage/helpers
 * @library /test/lib
 * @library base
 * @build SigningBase
 * @build SigningCheck
 * @build jtreg.SkippedException
 * @build SigningOptionsTest
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningOptionsTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useExecutableByDefault
 */

/*
 * @test
 * @summary Test jpackage signing options errors
 * @library /test/jdk/tools/jpackage/helpers
 * @library /test/lib
 * @library base
 * @build SigningBase
 * @build SigningCheck
 * @build jtreg.SkippedException
 * @build SigningOptionsTest
 * @requires (os.family == "mac")
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=SigningOptionsTest
 *  --jpt-before-run=jdk.jpackage.test.JPackageCommand.useToolProviderByDefault
 */

public final class SigningOptionsTest {

    private final String expectedError;
    private final JPackageCommand cmd;

    private static final String TEST_DUKE = TKit.TEST_SRC_ROOT.resolve(
            "apps/dukeplug.png").toString();

    @Parameters
    public static Collection input() {
        return List.of(new Object[][]{
            // --mac-signing-key-user-name and --mac-app-image-sign-identity
            {"Hello",
                    new String[]{"--mac-sign",
                                 "--mac-signing-key-user-name", "test-key",
                                 "--mac-app-image-sign-identity", "test-identity"},
                    null,
                    "Mutually exclusive options",
                    Boolean.FALSE},
            // --mac-signing-key-user-name and --mac-installer-sign-identity
            {"Hello",
                    new String[]{"--mac-sign",
                                 "--mac-signing-key-user-name", "test-key",
                                 "--mac-installer-sign-identity", "test-identity"},
                    null,
                    "Mutually exclusive options",
                    Boolean.FALSE},
            // --mac-installer-sign-identity and --type app-image
            {"Hello",
                    new String[]{"--mac-sign",
                                 "--mac-installer-sign-identity", "test-identity"},
                    null,
                    "Option [--mac-installer-sign-identity] is not valid with type",
                    Boolean.FALSE},
            // --mac-installer-sign-identity and --type dmg
            {"Hello",
                    new String[]{"--type", "dmg",
                                 "--mac-sign",
                                 "--mac-installer-sign-identity", "test-identity"},
                    new String[]{"--type"},
                    "Option [--mac-installer-sign-identity] is not valid with type",
                    Boolean.FALSE},
            // --app-content and --type app-image
            // JDK-8340802: "codesign" may or may not fail if additional
            // content is specified based on macOS version. For example on
            // macOS 15 aarch64 "codesign" will not fail with additional content.
            // Since we only need to check that warning is displayed when
            // "codesign" fails and "--app-content" is provided, lets fail
            // "codesign" for some better reason like identity which does not
            // exists.
            {"Hello",
                    new String[]{"--app-content", TEST_DUKE,
                                 "--mac-sign",
                                 "--mac-app-image-sign-identity", "test-identity"},
                    null,
                    "\"codesign\" failed and additional application content" +
                    " was supplied via the \"--app-content\" parameter.",
                    Boolean.TRUE},
        });
    }

    public SigningOptionsTest(String javaAppDesc, String[] jpackageArgs,
                              String[] removeArgs, String expectedError,
                              Boolean checkRequirements) {
        this.expectedError = expectedError;

        if (checkRequirements) {
            SigningCheck.isXcodeDevToolsInstalled();
        }

        cmd = JPackageCommand.helloAppImage(javaAppDesc)
                .saveConsoleOutput(true).dumpOutput(true);
        if (jpackageArgs != null) {
            cmd.addArguments(jpackageArgs);
        } if (removeArgs != null) {
            for (String arg : removeArgs) {
                cmd.removeArgumentWithValue(arg);
            }
        }
    }

    @Test
    public void test() {
        List<String> output = cmd.execute(1).getOutput();
        TKit.assertNotNull(output, "output is null");
        TKit.assertTextStream(expectedError).apply(output.stream());
    }

}
