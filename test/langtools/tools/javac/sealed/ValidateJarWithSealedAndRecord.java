/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282446 8282508
 * @summary Jar validation fails when sealed classes and records are involved
 * @run main ValidateJarWithSealedAndRecord
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.spi.ToolProvider;

public class ValidateJarWithSealedAndRecord {

    public static void main(String args[]) throws Exception {
        ValidateJarWithSealedAndRecord theTest = new ValidateJarWithSealedAndRecord();
        theTest.run();
    }

    void run() throws Exception {
        generateFilesNeeded();
    }

    void writeFile(String f, String contents) throws IOException {
        PrintStream s = new PrintStream(new FileOutputStream(f));
        s.println(contents);
        s.close();
    }

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar").orElseThrow(() -> new RuntimeException("jar tool not found"));

    void generateFilesNeeded() throws Exception {
        writeFile("Foo.java",
                """
                        public sealed interface Foo {
                            record Bar() implements Foo {}
                        }
                        """
                        );
        com.sun.tools.javac.Main.compile(new String[]{"-d", "out", "Foo.java"});
        JAR_TOOL.run(System.out, System.err, new String[] {"--create", "--file", "foo.jar", "-C", "out", "."});
        /* we need to create a fresh instance with clean options in other case the tool will
         * keep a copy of the options we just passed above
         */
        if (JAR_TOOL.run(System.out, System.err, new String[]{"--validate", "--file", "foo.jar"}) != 0) {
            throw new AssertionError("jar file couldn't be validated");
        }
    }
}
