/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id
 * @enablePreview
 * @requires os.family == "windows"
 * @library /test/lib
 * @run testng UncaughtNativeExceptionTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

public class UncaughtNativeExceptionTest {
    private static class Crasher {
        public static void main(String[] args) throws Throwable {
            System.loadLibrary("NativeException");
            throwException();
        }

        static native void throwException();
    }

    // check that we actually report the native exception,
    // and don't terminate abruptly due to stack overflow error
    @Test
    public void testNativeExceptionReporting() throws Exception {
        OutputAnalyzer output = ProcessTools.executeTestJvm(
                // executeTestJvm doesn't seem to forward 'java.library.path'
                "-Djava.library.path=" + System.getProperty("java.library.path"),
                Crasher.class.getName());

        File hsErrFile = HsErrFileUtils.openHsErrFileFromOutput(output);
        Path hsErrPath = hsErrFile.toPath();
        assertTrue(Files.exists(hsErrPath));

        Pattern[] positivePatterns = {
            Pattern.compile(".*Internal Error \\(0x2a\\).*")
        };
        HsErrFileUtils.checkHsErrFileContent(hsErrFile, positivePatterns, null, true /* check end marker */, false /* verbose */);
    }
}
