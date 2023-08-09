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

import java.nio.file.Path;
import java.util.Scanner;

import tests.Helper;

/*
 * @test
 * @summary Verify warnings are being produced when jlinking in jmod-less mode and files have been changed
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g)
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 *          jdk.jdeps/com.sun.tools.classfile
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g ModifiedFilesWarningTest
 */
public class ModifiedFilesWarningTest extends ModifiedFilesTest {

    public static void main(String[] args) throws Exception {
        ModifiedFilesWarningTest test = new ModifiedFilesWarningTest();
        test.run();
    }

    @Override
    String initialImageName() {
        return "java-base-jlink-with-mod-warn";
    }

    @Override
    void testAndAssert(Path modifiedFile, Helper helper, Path initialImage) throws Exception {
        CapturingHandler handler = new CapturingHandler();
        jlinkUsingImage(new JlinkSpecBuilder()
                                .helper(helper)
                                .imagePath(initialImage)
                                .name("java-base-jlink-with-mod-warn-target")
                                .addModule("java.base")
                                .validatingModule("java.base")
                                .extraJlinkOpt("--run-image-only-warnings") // only generate a warning
                                .build(), handler);
        // verify we get the warning message
        expectMatch(modifiedFile.toString(), handler.stdErr());
        expectMatch("WARNING: ", handler.stdErr());
        expectMatch("has been modified", handler.stdErr());
    }

    private static void expectMatch(String string, String lines) {
        boolean foundMatch = false;
        try (Scanner lineScan = new Scanner(lines)) {
            String line;
            while (lineScan.hasNextLine()) {
                line = lineScan.nextLine();
                if (line.contains(string)) {
                    foundMatch = true;
                    break;
                }
            }
        }
        if (!foundMatch) {
            throw new AssertionError(String.format("Expected to find '%s' in '%s'", string, lines));
        }
    }
}
