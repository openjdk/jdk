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

/* @test
 * @bug 8285452
 * @summary Unit Test for a common Test API in jdk.test.lib.util.FileUtils
 * @library .. /test/lib
 * @run main FileUtilsTest
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.Asserts;
import jdk.test.lib.util.FileUtils;

public class FileUtilsTest {

    public static void main(String[] args) throws Exception {
        // Replace with same line
        test("a", 1, 1, null, "a", "a\n");
        // Replace with different line
        test("a", 1, 1, null, "z", "z\n");
        // Replace with same line based on line match
        test("a", 1, 1, "a", "a", "a\n");
        // Replace with different line based on line match
        test("a", 1, 1, "a", "z", "z\n");
        // Replace single line with multiple lines
        test("a", 1, 1, null, "x\ny\nz", "x\ny\nz\n");
        // Replace single line with multiple lines based on lines match
        test("a", 1, 1, "a", "x\ny\nz", "x\ny\nz\n");
        // Replace all lines
        test("a\nb\nc", 1, 3, null, "x\ny\nz", "x\ny\nz\n");
        // Replace all lines based on lines match
        test("a\nb\nc", 1, 3, "a\nb\nc", "x\ny\nz", "x\ny\nz\n");
        // Replace all lines with single line based on lines match
        test("a\nb\nc", 1, 3, "a\nb\nc", "z", "z\n");
        // Replace single line
        test("a\nb\nc", 1, 1, null, "z", "z\nb\nc\n");
        // Replace single line based on line match
        test("a\nb\nc", 1, 1, "a", "z", "z\nb\nc\n");
        // Replace multiple lines
        test("a\nb\nc", 1, 2, null, "z", "z\nc\n");
        // Replace multiple lines based on line match
        test("a\nb\nc", 1, 2, "a\nb", "z", "z\nc\n");
        // Replace multiple lines based on line match
        test("a\nb\nc", 1, 2, "a\nb", "x\ny\nz", "x\ny\nz\nc\n");

        // Test with space characters
        // Replace with space line
        test("\n", 1, 1, null, " ", " \n");
        // Replace empty line with another line based on line match
        test("\n", 1, 1, " ", "a", "a\n");
        // Replace with space line
        test(" \na\nb\nc", 1, 1, null, "a", "a\na\nb\nc\n");
        // Replace empty line with different line based on line match
        test(" \na\nb\nc", 1, 1, " ", "a", "a\na\nb\nc\n");
        // Replace range of lines with space to different lines based on line match
        test(" \na\nb\nc", 1, 2, " \na", "x\ny\nz", "x\ny\nz\nb\nc\n");

        test("a\nb\nc\n", 1, 2, "a\nb", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, " a\nb", "1\n2", "1\n2\nc\n"); // free to add spaces around a line in from
        test("a\nb\nc\n", 1, 2, "a \nb", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "a\n b ", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "\na\nb", "1\n2", "1\n2\nc\n"); // free to add empty lines at both ends of from
        test("a\nb\nc\n", 1, 2, "a\nb\n", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "\na\nb\n", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "\n\na\nb\n\n", "1\n2", "1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "a\nb", "1\n2\n", "1\n2\nc\n"); // ok to add a new line at end of to
        test("a\nb\nc\n", 1, 2, "a\nb", "1\n2\n\n", "1\n2\n\nc\n"); // more will change result
        test("a\nb\nc\n", 1, 2, "a\nb", "\n1\n2", "\n1\n2\nc\n");
        test("a\nb\nc\n", 1, 2, "a\n\nb", "1\n2\n", null); // no extra new line inside
        test("a\nb\nc\n", 1, 2, "ab", "1\n2\n", null); // new line inside must preserve
        test("a\nb\nc\n", 1, 2, "a", "1\n2\n", null); // must be all
        test("a\nb\nc\n", 1, 2, "b", "1\n2\n", null); // must be all

        test("a\nb\nc\n", 1, 2, "a\nb", "", "c\n"); // just remove
        test("a\nb\nc\n", 1, 0, "", "1\n2", "1\n2\na\nb\nc\n"); // just add

        // Mismatched range with "replace" lines Tests
        // Replace all lines with mismatched line
        test("a", 1, 1, "z", "z", null);
        // Replace all lines with mismatched lines
        test("a\nb\nc", 1, 3, "x\ny\nz", "x\ny\nz", null);
        // Replace single line with mismatched line
        test("a\nb\nc", 1, 1, "z", "z", null);
        // Replace a range of lines with mismatched lines
        test("a\nb\nc", 1, 3, "ab", "x\ny\nz", null);
    }

    private static void test(String content, int from, int to, String replace,
                String replaceTo, String expected) throws IOException {
        String name = "Test-" + new Exception().getStackTrace()[1].getLineNumber();
        Path path = Files.writeString(Paths.get(name), content);
        String output = null;
        try {
            FileUtils.patch(path, from, to, replace, replaceTo);
            output = Files.readString(path);
        } catch (IOException e) {
            // output is null
        }
        Asserts.assertEQ(output, (expected != null) ? expected.replaceAll("\n", System.lineSeparator()) : null);
    }
}