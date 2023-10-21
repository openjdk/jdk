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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.spi.ToolProvider;

import jdk.internal.util.OperatingSystem;

/*
 * @test
 * @bug 8206890
 * @summary verify that the image created through jlink uses the byte order of the target platform
 * @modules java.base/jdk.internal.util
 * @comment the test asserts the presence of locale specific error message in the test's output,
 *          so we explicitly use en_US locale
 * @run main/othervm -Duser.language=en -Duser.country=US JLinkEndianTest
 */
public class JLinkEndianTest {
    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));


    public static void main(final String[] args) throws Exception {
        testEndianMismatch();
        testCorrectEndian();
    }

    /**
     * Launches jlink with "--endian" option whose value doesn't match the target platform.
     * Asserts that the jlink process fails with an error.
     */
    private static void testEndianMismatch() throws Exception {
        // we use a --endian value which doesn't match the current platform's endianness.
        // this should cause the jlink image generation against the current platform to fail
        final String endianOptVal = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? "big" : "little";
        final String[] args = new String[]{
                "-v",
                "--endian", endianOptVal,
                "--add-modules", "java.base",
                "--output", "image-should-not-have-been-created"
        };
        final StringWriter jlinkStdout = new StringWriter();
        final StringWriter jlinkStderr = new StringWriter();
        System.out.println("Launching jlink with args: " + Arrays.toString(args));
        final int exitCode = JLINK_TOOL.run(new PrintWriter(jlinkStdout),
                new PrintWriter(jlinkStderr), args);
        System.out.println(jlinkStdout);
        System.err.println(jlinkStderr);
        if (exitCode == 0) {
            throw new AssertionError("jlink command was expected to fail but completed with" +
                    " exit code: " + exitCode);
        }
        // verify the failure was due to the expected error (message)
        if (!jlinkStdout.toString().contains("does not match endianness of target platform")) {
            throw new AssertionError("jlink process' stderr didn't contain the expected" +
                    " error message");
        }
    }

    /**
     * Launches jlink with "--endian" option whose value matches the target platform's endianness.
     * Asserts that the jlink process successfully creates the image.
     */
    private static void testCorrectEndian() throws Exception {
        // we use a --endian value which matches the current platform
        final String endianOptVal = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? "little" : "big";
        final Path imageOutDir = Path.of("correct-endian-image");
        final String[] args = new String[]{
                "-v",
                "--endian", endianOptVal,
                "--add-modules", "java.base",
                "--output", imageOutDir.toString()
        };
        final StringWriter jlinkStdout = new StringWriter();
        final StringWriter jlinkStderr = new StringWriter();
        System.out.println("Launching jlink with args: " + Arrays.toString(args));
        final int exitCode = JLINK_TOOL.run(new PrintWriter(jlinkStdout),
                new PrintWriter(jlinkStderr), args);
        System.out.println(jlinkStdout);
        System.err.println(jlinkStderr);
        if (exitCode != 0) {
            throw new AssertionError("jlink command was expected to succeed but completed with" +
                    " exit code: " + exitCode);
        }
        // trivially verify <image-dir>/bin/java exists
        final Path javaBinary = OperatingSystem.isWindows()
                ? Path.of(imageOutDir.toString(), "bin", "java.exe")
                : Path.of(imageOutDir.toString(), "bin", "java");
        if (!Files.exists(javaBinary)) {
            throw new AssertionError("jlink image generation was expected to create "
                    + javaBinary + ", but that file is missing");
        }
    }
}
