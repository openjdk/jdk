/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;

import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8295803 8299689
 * @summary Tests System.console() works with standard input redirection.
 * @library /test/lib
 * @run main RedirectTest
 * @run main/othervm -Djdk.console=jdk.internal.le RedirectTest
 */
public class RedirectTest {
    private static final String SYSPROP = "jdk.console";

    public static void main(String... args) throws Throwable {
        if (args.length == 0) {
            // no arg will launch the child process that actually perform tests
            var pb = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYSPROP + "=" + System.getProperty(SYSPROP, ""),
                    "RedirectTest", "dummy");
            var input = new File(System.getProperty("test.src", "."), "input.txt");
            pb.redirectInput(input);
            var oa = ProcessTools.executeProcess(pb);
            if (oa.getExitValue() == 1) {
                System.out.println("System.console() returns null. Ignoring the test.");
            } else {
                var output = oa.asLines();
                var expected = Files.readAllLines(input.toPath());
                if (!output.equals(expected)) {
                    throw new RuntimeException("""
                        Standard out had unexpected strings:
                        Actual output: %s
                        Expected output: %s
                        """.formatted(output, expected));
                } else {
                    oa.shouldHaveExitValue(0);
                    System.out.println("Redirect succeeded.");
                }
            }
        } else {
            var con = System.console();
            if (con != null) {
                String line;
                while ((line = con.readLine()) != null) {
                    System.out.println(line);
                }
            } else {
                // Exit with 1
                System.exit(1);
            }
        }
    }
}
