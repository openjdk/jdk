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
 * @bug 8035271
 * @summary javap incorrect indentation when LineNumberTable and LocalVariableTable written via ClassWriter
 * @run main ClassWriterTableIndentTest
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public class ClassWriterTableIndentTest {
    public static void main(String[] args) {
        new ClassWriterTableIndentTest().run();
    }

    public void run() {
        /*
         * Partial expected output within a larger file. There exists another "Code: " section above, and thus we
         * select the second occurrence in `findNthMatchPrecedingSpaces(output, "Code:", 1);`
         * ...
         *  public void emptyLoop();
         *    Code:
         *       ...
         *      LineNumberTable:
         *        line 143: 0
         *        line 145: 14
         * ...
         */
        List<String[]> runArgsList = List.of(new String[]{"-c", "-l"}, new String[]{"-v"});
        for (String[] runArgs : runArgsList) {
            String output = javap(runArgs);
            int methodIntent = findNthMatchPrecedingSpaces(output, "public void emptyLoop();", 0);
            int codeHeaderIndent = findNthMatchPrecedingSpaces(output, "Code:", 1);
            int detailIndent = findNthMatchPrecedingSpaces(output, "LineNumberTable:", 1);

            if (codeHeaderIndent - methodIntent != 2) {
                indentError(2, codeHeaderIndent - methodIntent, "Code block", "method header", runArgs);
            }

            if (detailIndent - codeHeaderIndent != 2) {
                indentError(2, detailIndent - codeHeaderIndent, "LineNumberTable", "code header", runArgs);
            }

            if (detailIndent - methodIntent != 4) {
                indentError(4, detailIndent - methodIntent, "LineNumberTable", "method header");
            }
        }
        if (errors > 0) {
            throw new Error(errors + " found.");
        }
    }

    private String javap(String... args) {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);

        String[] fullArgs = new String[args.length + 1];
        System.arraycopy(args, 0, fullArgs, 0, args.length);
        fullArgs[args.length] = System.getProperty("test.classes") + "/EmptyLoop8035271.class";

        int rc = com.sun.tools.javap.Main.run(fullArgs, out);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        out.close();
        System.out.println(sw);
        return sw.toString();
    }

    public static int findNthMatchPrecedingSpaces(String inputString, String searchString, int occurrence) {
        String[] lines = inputString.split(System.lineSeparator());
        int count = 0;
        for (String line : lines) {
            if (line.trim().startsWith(searchString)) {
                if (count == occurrence) {
                    return line.indexOf(searchString);
                }
                count++;
            }
        }
        throw new IllegalArgumentException("Could not find " + searchString + " in " + inputString);
    }

    void indentError(int expected, int actual, String toCompare, String referencePoint, String... args) {
        System.err.println(toCompare + " is not indented correctly with respect to " + referencePoint + ". Expected "
            + expected + " but got " + actual + " for args: " + Arrays.toString(args));
        errors++;
    }

    int errors;
}

class EmptyLoop8035271 {
    public void emptyLoop() {
        for (int i = 0; i < 10; i++) {
        }
    }
}