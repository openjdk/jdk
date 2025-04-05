/*
 * Copyright (c) 2025, Red Hat, Inc.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;

/*
 * @test
 * @summary Verify no warnings are being produced on a modified file which
 *          gets the SHA override from a file
 * @requires (vm.compMode != "Xcomp" & os.maxMemory >= 2g & os.family == "linux")
 * @library ../../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.* jdk.test.lib.process.OutputAnalyzer
 *        jdk.test.lib.process.ProcessTools
 * @run main/othervm -Xmx1g ModifiedFilesWithShaOverrideFileTest
 */
public class ModifiedFilesWithShaOverrideFileTest extends ModifiedFilesWithShaOverrideBase {

    public static void main(String[] args) throws Exception {
        ModifiedFilesWithShaOverrideFileTest test = new ModifiedFilesWithShaOverrideFileTest();
        test.run();
    }

    @Override
    String initialImageName() {
        return "java-base-jlink-with-sha-override-file";
    }

    @Override
    public String getTargetName() {
        return "java-base-jlink-with-sha-override-file-target";
    }

    @Override
    public String getShaOverrideOption(Path modifiedFile, Path initialImage) {
        String strippedSha = buildSHA512(modifiedFile);
        Path relativePath = initialImage.relativize(modifiedFile);
        // Modified file is libjvm.so, which is in java.base
        String overrideVal = String.format("%s|%s|%s", "java.base", relativePath.toString(), strippedSha);
        // Write a file in JAVA_HOME of the linkable runtime with the sha
        // override.
        File overrideFile = initialImage.resolve("test_sha_override.txt").toFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(overrideFile))) {
            pw.println(overrideVal);
        } catch (IOException e) {
            throw new AssertionError("Test failed unexpectedly: ", e);
        }
        return SHA_OVERRIDE_FLAG + "=@${java.home}/test_sha_override.txt";
    }
}
