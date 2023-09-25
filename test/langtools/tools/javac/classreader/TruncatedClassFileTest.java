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
 * @test
 * @bug 8302514
 * @summary Verify truncated class files are detected and reported as truncated
 */

import com.sun.tools.javac.Main;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;

public class TruncatedClassFileTest {

    // We want a bunch of stuff in this file to make the classfile complicated
    private static final String A_SOURCE = """
        public class ClassA {
            public static final boolean z = true;
            public static final byte b = 7;
            public static final char c = '*';
            public static final short s = -12;
            public static final int i = 123;
            public static final float f = 123f;
            public static final long j = 0x1234567812345678L;
            public static final double d = Math.PI;
            public static final String str = new String("test123");
            @SuppressWarnings("blah")
            public ClassA() {
                new Thread();
            }
        }
    """;

    // This file will get compiled against a trunctated version of A.class
    private static final String B_SOURCE = """
        public class ClassB {
            public ClassB() {
                new ClassA();
            }
        }
    """;

    private static final File A_SOURCE_FILE = new File("ClassA.java");
    private static final File B_SOURCE_FILE = new File("ClassB.java");
    private static final File A_CLASS_FILE = new File("ClassA.class");

    private static void createSourceFile(File file, String content) throws IOException {
        try (PrintStream output = new PrintStream(new FileOutputStream(file))) {
            output.println(content);
        }
    }

    public static void main(String... args) throws Exception {

        // Create A.java and B.java
        createSourceFile(A_SOURCE_FILE, A_SOURCE);
        createSourceFile(B_SOURCE_FILE, B_SOURCE);

        // Compile A.java
        createSourceFile(A_SOURCE_FILE, A_SOURCE);
        int ret = Main.compile(new String[] { A_SOURCE_FILE.toString() });
        if (ret != 0)
            throw new AssertionError("compilation of " + A_SOURCE_FILE + " failed");
        A_SOURCE_FILE.delete();

        // Read A.class
        final byte[] classfile = Files.readAllBytes(A_CLASS_FILE.toPath());

        // Now compile B.java with truncated versions of A.class
        for (int length = 0; length < classfile.length; length++) {

            // Write out truncated class file A.class
            try (FileOutputStream output = new FileOutputStream(A_CLASS_FILE)) {
                output.write(classfile, 0, length);
            }

            // Try to compile file B.java
            final StringWriter diags = new StringWriter();
            final String[] params = new String[] {
                "-classpath",
                ".",
                "-XDrawDiagnostics",
                B_SOURCE_FILE.toString()
            };
            ret = Main.compile(params, new PrintWriter(diags, true));
            if (ret == 0)
                throw new AssertionError("compilation with truncated class file (" + length + ") succeeded?");
            final String errmsg = "compiler.misc.bad.class.truncated.at.offset: " + length;
            if (!diags.toString().contains(errmsg))
                throw new AssertionError("error message not found for truncated class file (" + length + "): " + diags);
        }
    }
}
