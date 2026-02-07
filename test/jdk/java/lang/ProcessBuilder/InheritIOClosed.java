/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary child process should not hang even if parent process has closed System.out/err
 * @bug 8366736
 * @run main/othervm InheritIOClosed close dontclose
 * @run main/othervm InheritIOClosed dontclose close
 * @run main/othervm InheritIOClosed close close
 */

import java.nio.file.Path;

public class InheritIOClosed {
    private final static Path JAVA_EXE =
            Path.of(System.getProperty("java.home"), "bin", "java");

    private static final String s = "1234567890".repeat(10);

    public static void main(String[] args) throws Exception {
        if (args.length == 2) {
            // Main test process
            if (args[0].equals("close")) {
                System.out.close();
            }
            if (args[1].equals("close")) {
                System.err.close();
            }

            ProcessBuilder pb = new ProcessBuilder().inheritIO()
                .command(JAVA_EXE.toString(),
                         "-cp",
                         System.getProperty("java.class.path"),
                         InheritIOClosed.class.getName());
            Process process = pb.start();
            process.waitFor();

            System.out.println("Done");
        } else {
            // Child process -- print to System.out/err. Without the fix in
            // JDK-8366736, this process will hang on Windows.
            for (int i = 0; i < 100; i++) {
                System.out.println(s);
            }
            for (int i = 0; i < 100; i++) {
                System.err.println(s);
            }
        }
    }
}
