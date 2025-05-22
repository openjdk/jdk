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
 * @bug 8034066
 * @summary javap incorrect indentation when CodeWriter called via ClassWriter
 * @run main ClassWriterCodeIndentTest
 * @modules jdk.jdeps/com.sun.tools.javap
 */

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassWriterCodeIndentTest {
    public static void main(String[] args) {
        new ClassWriterCodeIndentTest().run();
    }

    public void run() {
        /*
         * Partial expected output within a larger file. There exists another "Code: " section above, and thus we
         * select the second occurrence in `findNthMatchPrecedingSpaces(output, "Code:", 1);`
         * ...
         *    Code:
         *         0: iconst_0
         *         1: istore_1
         *      StackMap locals:  this int
         *      StackMap stack:
         * ...
         */
        String output = javap();

        int codeHeaderIndent = findNthMatchPrecedingSpaces(output, "Code:", 1);
        int detailIndent = findNthMatchPrecedingSpaces(output, "StackMap ", 0);
        int bytecodeIndent = findNthMatchPrecedingSpaces(output, "0: iconst_0", 0);

        if (detailIndent - codeHeaderIndent != 2) {
            error("Details are not indented correctly with respect to code header.");
        }

        if (bytecodeIndent - codeHeaderIndent != 5) {
            error("Bytecode is not indented correctly with respect to code header.");
        }

        if (errors > 0) {
            throw new Error(errors + " found.");
        }
    }

    String javap() {
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        int rc = com.sun.tools.javap.Main.run(new String[]{"-c", "-XDdetails:stackMaps",
            System.getProperty("test.classes") + "/EmptyLoop.class"}, out);
        if (rc != 0)
            throw new Error("javap failed. rc=" + rc);
        out.close();
        System.out.println(sw.toString());
        return sw.toString();
    }

    public static int findNthMatchPrecedingSpaces(String inputString, String searchString, int occurrence) {
        String regex = "^(\\s*)" + Pattern.quote(searchString);
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(inputString);

        int count = 0;
        while (matcher.find()) {
            if (count == occurrence) {
                return matcher.group(1).length();
            }
            count++;
        }

        throw new Error("Could not find " + searchString + " in " + inputString);
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}

class EmptyLoop {
    public void emptyLoop() {
        for (int i = 0; i < 10; i++) {
        }
    }
}