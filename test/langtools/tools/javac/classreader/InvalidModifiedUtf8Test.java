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
 * @bug 8303623
 * @summary Compiler should disallow non-standard UTF-8 string encodings
 */

import com.sun.tools.javac.Main;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Arrays;

public class InvalidModifiedUtf8Test {

    //
    // What this test does (repeatedly):
    //  1. Compile a Java source file for ClassX normally
    //  2. Modify the UTF-8 inside the ClassX classfile so that it is
    //     still valid structurally but uses a non-standard way of
    //     encoding some character (according to "Modified UTF-8").
    //  3. Compile a Java source file for RefClass that references ClassX
    //  4. Verify that the compiler gives a "bad UTF-8" error
    //

    // We change c3 a8 -> c3 e8 (illegal second byte not of the form 0x10xxxxxx)
    private static final String SOURCE_0 = """
        interface Class0 {
            void ABC\u00e8();       // encodes to: 41 42 43 c3 a8
        }
    """;

    // We change e1 80 80 -> e1 80 40 (illegal third byte not of the form 0x10xxxxxx)
    private static final String SOURCE_1 = """
        interface Class1 {
            void ABC\u1000();       // encodes to: 41 42 43 e1 80 80
        }
    """;

    // We change c4 80 -> c1 81 (illegal two-byte encoding for one-byte value)
    private static final String SOURCE_2 = """
        interface Class2 {
            void ABC\u0100();       // encodes to: 41 42 43 c4 00
        }
    """;

    // We change e1 80 80 -> e0 84 80 (illegal three-byte encoding for two-byte value)
    private static final String SOURCE_3 = """
        interface Class3 {
            void ABC\u1000();       // encodes to: 41 42 43 e1 80 80
        }
    """;

    // We change 44 -> 00 (illegal one-byte encoding of 0x0000)
    private static final String SOURCE_4 = """
        interface Class4 {
            void ABCD();            // encodes to: 41 42 43 44
        }
    """;

    // This is the source file that references one of the above
    private static final String REF_SOURCE = """
        interface RefClass extends CLASSNAME {
        }
    """;

    private static String[] SOURCES = new String[] {
        SOURCE_0,
        SOURCE_1,
        SOURCE_2,
        SOURCE_3,
        SOURCE_4,
    };
    private static String[][] MODIFICATIONS = new String[][] {
        {   "414243c3a8",
            "414243c3e8"    },
        {   "414243e18080",
            "414243e18040"  },
        {   "414243c480",
            "414243c181"    },
        {   "414243e18080",
            "414243e08480"  },
        {   "41424344",
            "41424300"      },
    };

    private static File[] SOURCE_FILES = new File[] {
        new File("Class0.java"),
        new File("Class1.java"),
        new File("Class2.java"),
        new File("Class3.java"),
        new File("Class4.java"),
    };
    private static File[] CLASS_FILES = new File[] {
        new File("Class0.class"),
        new File("Class1.class"),
        new File("Class2.class"),
        new File("Class3.class"),
        new File("Class4.class"),
    };

    public static String bytes2string(byte[] array) {
        char[] buf = new char[array.length * 2];
        for (int i = 0; i < array.length; i++) {
            int value = array[i] & 0xff;
            buf[i * 2] = Character.forDigit(value >> 4, 16);
            buf[i * 2 + 1] = Character.forDigit(value & 0xf, 16);
        }
        return new String(buf);
    }

    public static byte[] string2bytes(String string) {
        byte[] buf = new byte[string.length() / 2];
        for (int i = 0; i < string.length(); i += 2) {
            int value = Integer.parseInt(string.substring(i, i + 2), 16);
            buf[i / 2] = (byte)value;
        }
        return buf;
    }

    private static void createSourceFile(String content, File file) throws IOException {
        System.err.println("creating: " + file);
        try (PrintStream output = new PrintStream(new FileOutputStream(file))) {
            output.println(content);
        }
    }

    private static void writeFile(File file, byte[] content) throws IOException {
        System.err.println("writing: " + file);
        try (FileOutputStream output = new FileOutputStream(file)) {
            Files.write(file.toPath(), content);
        }
    }

    private static void compileRefClass(File file, String expectedError) {
        final StringWriter diags = new StringWriter();
        final String[] params = new String[] {
            "-classpath",
            ".",
            "-XDrawDiagnostics",
            file.toString()
        };
        System.err.println("compiling: " + file);
        int ret = Main.compile(params, new PrintWriter(diags, true));
        if (expectedError != null && ret == 0)
            throw new AssertionError("compilation succeeded, but expected error \"" + expectedError + "\"");
        else if (expectedError == null && ret != 0)
            throw new AssertionError("compilation failed, but expected success:\n" + diags);
        if (expectedError != null && !diags.toString().contains(expectedError))
            throw new AssertionError("error message not found:\n  expected: \"" + expectedError + "\"\n  found:" + diags);
    }

    public static void main(String... args) throws Exception {

        // Create source files
        for (int i = 0; i < 5; i++)
            createSourceFile(SOURCES[i], SOURCE_FILES[i]);

        // Compile source files
        for (int i = 0; i < 5; i++) {
            final File sourceFile = SOURCE_FILES[i];
            int ret = Main.compile(new String[] { sourceFile.toString() });
            if (ret != 0)
                throw new AssertionError("compilation of " + sourceFile + " failed");
        }

        // Now compile REF_SOURCE against each classfile without and then with the modification.
        // When compiling without the modification, everything should be normal.
        // When compiling with the modification, an error should be generated.
        for (int i = 0; i < 5; i++) {
            final String className = "Class" + i;
            final File classFile = CLASS_FILES[i];

            // Create reference source file
            final File refSource = new File("RefClass.java");
            createSourceFile(REF_SOURCE.replaceAll("CLASSNAME", className), refSource);

            // Do a normal compilation
            compileRefClass(refSource, null);

            // Now corrupt the class file
            System.err.println("modifying: " + classFile);
            final String[] mod = MODIFICATIONS[i];
            final byte[] data1 = Files.readAllBytes(classFile.toPath());
            final byte[] data2 = string2bytes(bytes2string(data1).replaceAll(mod[0], mod[1]));
            if (Arrays.equals(data2, data1))
                throw new AssertionError("modification of " + classFile + " failed");
            writeFile(classFile, data2);

            // Do a corrupt compilation
            compileRefClass(refSource, "compiler.misc.bad.utf8.byte.sequence.at");
        }
    }
}
