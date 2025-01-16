/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8326979
 * @run main InvalidModuleDescriptor
 * @summary jdeps should print the exception message of the cause of FindException
 *          instead of FindException
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.spi.ToolProvider;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

public class InvalidModuleDescriptor {
    private static final Path TEST_CLASSES = Paths.get(System.getProperty("test.classes"));
    private static final ToolProvider JDEPS = ToolProvider.findFirst("jdeps").orElseThrow();
    private static final ToolProvider JAR = ToolProvider.findFirst("jar").orElseThrow();

    public static void main(String... args) throws IOException {
        // create an automatic module with an invalid module descriptor (containing unnamed package)
        Path jarFile = Paths.get("hi.jar");
        String moduleName = "hi";
        int rc = createAutomaticModule(jarFile, moduleName);
        if (rc != 0) {
            throw new RuntimeException("Fail to create automatic module");
        }

        // jdeps should fail with an error without stack trace
        String expectedError = "Error: InvalidModuleDescriptor.class found in top-level directory (unnamed package not allowed in module)";
        rc = runJdeps(expectedError, "--module-path", jarFile.toString(), "-m", moduleName);
        if (rc == 0) {
            throw new RuntimeException("Expected jdeps to fail");
        }
    }

    // create an automatic module with an invalid module descriptor
    static int createAutomaticModule(Path jarFile, String moduleName) throws IOException {
        Path manifest = Paths.get("manifest");
        Files.writeString(manifest, "Automatic-Module-Name: " + moduleName, CREATE_NEW);
        return JAR.run(System.out, System.out, "--create", "--file", jarFile.toString(),
                       "-m", manifest.toString(),
                       "-C", TEST_CLASSES.toString(), "InvalidModuleDescriptor.class");
    }

    static int runJdeps(String expected, String... args) {
        StringWriter output = new StringWriter();
        StringWriter error = new StringWriter();
        try (PrintWriter pwout = new PrintWriter(output);
             PrintWriter pwerr = new PrintWriter(error)) {
            int rc = JDEPS.run(pwout, pwerr, args);
            if (!output.toString().contains(expected)) {
                System.out.println(output);
                System.out.println(error);
                throw new RuntimeException("Mismatched output");
            }
            return rc;
        }
    }
}
