/*
 * Copyright (c) 2023, Red Hat, Inc.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import tests.Helper;

/*
 * @test
 * @summary Test --save-jlink-argfiles plugin in jmod-less mode
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g SaveJlinkOptsTest
 */
public class SaveJlinkOptsTest extends AbstractJmodLessTest {

    public static void main(String[] args) throws Exception {
        SaveJlinkOptsTest test = new SaveJlinkOptsTest();
        test.run();
    }

    @Override
    void runTest(Helper helper) throws Exception {
        String vendorVersion = "jmodless";
        Path jlinkOptsFile = createJlinkOptsFile(List.of("--compress", "zip-6", "--vendor-version", vendorVersion));
        Path finalImage = createJavaImageJmodLess(new BaseJlinkSpecBuilder()
                                                        .addExtraOption("--save-jlink-argfiles")
                                                        .addExtraOption(jlinkOptsFile.toAbsolutePath().toString())
                                                        .addModule("jdk.jlink")
                                                        .name("java-base-with-jlink-opts")
                                                        .helper(helper)
                                                        .validatingModule("java.base")
                                                        .build());
        verifyVendorVersion(finalImage, vendorVersion);
    }

    /**
     * Create a temporary file for use via --save-jlink-argfiles-file
     * @param options The options to save in the file.
     * @return The path to the temporary file
     */
    private static Path createJlinkOptsFile(List<String> options) throws Exception {
        Path tmpFile = Files.createTempFile("JLinkTestJmodsLess", "jlink-options-file");
        tmpFile.toFile().deleteOnExit();
        String content = options.stream().collect(Collectors.joining("\n"));
        Files.writeString(tmpFile, content, StandardOpenOption.TRUNCATE_EXISTING);
        return tmpFile;
    }

    private void verifyVendorVersion(Path finalImage, String vendorVersion) throws Exception {
        OutputAnalyzer out = runJavaCmd(finalImage, List.of("--version"));
        String stdOut = out.getStdout();
        if (!stdOut.contains(vendorVersion)) {
            if (DEBUG) {
                System.err.println(stdOut);
            }
            throw new AssertionError("Expected vendor version '" + vendorVersion + "' in jlinked image.");
        }
    }

}
