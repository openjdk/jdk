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

/**
 * @test
 * @bug 8330998
 * @summary Verify that even if the stdout is redirected java.io.Console will
 *          use it for writing.
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/* Note this test only tests the case where neither of stdin, stdout and stderr is
 * connected to a terminal. Automatically testing case where stdin and stderr are
 * connected to a terminal, and stdout is not is not done due to technical
 * difficulties.
 *
 * For manual test, create file `/tmp/ConsoleTest.java` with the following content:
public class ConsoleTest {
     public static void main(String... args) {
         System.console().printf("Hello!");
     }
}
 * and run it manually from terminal while redirecting stdout only to a file, like:
 * $ java /tmp/ConsoleTest.java >/tmp/stdout
 *
 * This should print nothing to the console, and /tmp/stdout should contain "Hello!".
 */
public class RedirectedStdOut {
    public static void main(String... args) throws Exception {
        new RedirectedStdOut().run();
    }

    void run() throws Exception {
        String testJDK = System.getProperty("test.jdk");
        Path javaLauncher = Path.of(testJDK, "bin", "java");
        AtomicReference<byte[]> out = new AtomicReference<>();
        AtomicReference<byte[]> err = new AtomicReference<>();
        Process launched = new ProcessBuilder(javaLauncher.toString(), "RedirectedStdOut$ConsoleTest").start();
        Thread outReader = Thread.ofVirtual().unstarted(() -> {
            try {
                out.set(launched.getInputStream().readAllBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        outReader.start();

        Thread errReader = Thread.ofVirtual().unstarted(() -> {
            try {
                err.set(launched.getErrorStream().readAllBytes());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        errReader.start();

        int r = launched.waitFor();

        if (r != 0) {
            throw new AssertionError("Unexpected return value: " + r);
        }

        outReader.join();
        errReader.join();

        String expectedOut = "Hello!";
        String actualOut = new String(out.get());

        if (!Objects.equals(expectedOut, actualOut)) {
            throw new AssertionError("Unexpected stdout content. " +
                                     "Expected: '" + expectedOut + "'" +
                                     ", got: '" + actualOut + "'");
        }

        String expectedErr = "";
        String actualErr = new String(err.get());

        if (!Objects.equals(expectedErr, actualErr)) {
            throw new AssertionError("Unexpected stderr content. " +
                                     "Expected: '" + expectedErr + "'" +
                                     ", got: '" + actualErr + "'");
        }
    }

    public static class ConsoleTest {
        public static void main(String... args) {
            System.console().printf("Hello!");
        }
    }
}
