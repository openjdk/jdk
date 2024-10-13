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
 * @modules jdk.compiler/com.sun.tools.javac.code
 * @summary Compiler should disallow non-standard UTF-8 string encodings
 */

import com.sun.tools.javac.code.Source;
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
        interface CLASSNAME {
            void ABC\u00e8();       // encodes to: 41 42 43 c3 a8
        }
    """;

    // We change e1 80 80 -> e1 80 40 (illegal third byte not of the form 0x10xxxxxx)
    private static final String SOURCE_1 = """
        interface CLASSNAME {
            void ABC\u1000();       // encodes to: 41 42 43 e1 80 80
        }
    """;

    // We change c4 80 -> c1 81 (illegal two-byte encoding for one-byte value)
    private static final String SOURCE_2 = """
        interface CLASSNAME {
            void ABC\u0100();       // encodes to: 41 42 43 c4 00
        }
    """;

    // We change e1 80 80 -> e0 84 80 (illegal three-byte encoding for two-byte value)
    private static final String SOURCE_3 = """
        interface CLASSNAME {
            void ABC\u1000();       // encodes to: 41 42 43 e1 80 80
        }
    """;

    // We change 44 -> 00 (illegal one-byte encoding of 0x0000)
    private static final String SOURCE_4 = """
        interface CLASSNAME {
            void ABCD();            // encodes to: 41 42 43 44
        }
    """;

    // We change 43 44 -> e1 80 (illegal truncated three-byte encoding)
    private static final String SOURCE_5 = """
        interface CLASSNAME {
            void ABCD();            // encodes to: 41 42 43 44
        }
    """;

    // This is the source file that references one of the above
    private static final String REF_SOURCE = """
        interface RefClass extends CLASSNAME {
        }
    """;

    private static TestCase[] TEST_CASES = new TestCase[] {
        new TestCase(0, SOURCE_0, "414243c3a8",     "414243c3e8"),
        new TestCase(1, SOURCE_1, "414243e18080",   "414243e18040"),
        new TestCase(2, SOURCE_2, "414243c480",     "414243c181"),
        new TestCase(3, SOURCE_3, "414243e18080",   "414243e08480"),
        new TestCase(4, SOURCE_4, "41424344",       "41424300"),
        new TestCase(5, SOURCE_5, "41424344",       "4142e180"),
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

    private static void compileRefClass(File file, boolean expectSuccess, String expectedError) {
        final StringWriter diags = new StringWriter();
        final String[] params = new String[] {
            "-classpath",
            ".",
            "-XDrawDiagnostics",
            file.toString()
        };
        System.err.println("compiling: " + file);
        int ret = Main.compile(params, new PrintWriter(diags, true));
        System.err.println("exit value: " + ret);
        String output = diags.toString().trim();
        if (!output.isEmpty())
            System.err.println("output:\n" + output);
        else
            System.err.println("no output");
        if (!expectSuccess && ret == 0)
            throw new AssertionError("compilation succeeded, but expected failure");
        else if (expectSuccess && ret != 0)
            throw new AssertionError("compilation failed, but expected success");
        if (expectedError != null && !diags.toString().contains(expectedError))
            throw new AssertionError("expected output \"" + expectedError + "\" not found");
    }

    public static void main(String... args) throws Exception {

        // Create source files
        for (TestCase test : TEST_CASES)
            test.createSourceFile();

        // Compile source files
        for (TestCase test : TEST_CASES) {
            int ret = Main.compile(new String[] { test.sourceFile().toString() });
            if (ret != 0)
                throw new AssertionError("compilation of " + test.sourceFile() + " failed");
        }

        // We should get warnings in JDK 21 and errors in any later release
        final boolean expectSuccess = Source.DEFAULT.compareTo(Source.JDK21) <= 0;

        // Now compile REF_SOURCE against each classfile without and then with the modification.
        // When compiling without the modification, everything should be normal.
        // When compiling with the modification, an error should be generated.
        for (TestCase test : TEST_CASES) {
            System.err.println("==== TEST " + test.index() + " ====");

            // Create reference source file
            final File refSource = new File("RefClass.java");
            createSourceFile(REF_SOURCE.replaceAll("CLASSNAME", test.className()), refSource);

            // Do a normal compilation
            compileRefClass(refSource, true, null);

            // Now corrupt the class file
            System.err.println("modifying: " + test.classFile());
            final File classFile = test.classFile();
            final byte[] data1 = Files.readAllBytes(classFile.toPath());
            final byte[] data2 = test.modify(data1);
            writeFile(classFile, data2);

            // Do a corrupt compilation
            compileRefClass(refSource, expectSuccess, "compiler.misc.bad.utf8.byte.sequence.at");
        }
    }

// TestCase

    static class TestCase {

        final int index;
        final String source;
        final String match;
        final String replace;

        TestCase(int index, String source, String match, String replace) {
            this.index = index;
            this.source = source.replaceAll("CLASSNAME", className());
            this.match = match;
            this.replace = replace;
        }

        byte[] modify(byte[] input) {
            final byte[] output = string2bytes(bytes2string(input).replaceAll(match, replace));
            if (Arrays.equals(output, input))
                throw new AssertionError("modification of " + classFile() + " failed");
            return output;
        }

        int index() {
            return index;
        }

        String className() {
            return "Class" + index;
        }

        File sourceFile() {
            return new File(className() + ".java");
        }

        File classFile() {
            return new File(className() + ".class");
        }

        void createSourceFile() throws IOException {
            InvalidModifiedUtf8Test.createSourceFile(source, sourceFile());
        }
    }
}
