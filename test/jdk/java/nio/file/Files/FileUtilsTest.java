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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jdk.test.lib.util.FileUtils;

public class FileUtilsTest {

    private static volatile int counter = 1;

    public static void main(String[] args) throws Exception {
        String a = "a";
        String z = "z";
        String abc = """
a
b
c""";
        // 1st line has a space character
        String sabc = " " + System.lineSeparator() + abc;
        String xyz = """
x
y
z""";
        var sList = Arrays.asList(" ");
        var aList = Arrays.asList(a);
        var abcList = Arrays.asList(abc.split(System.lineSeparator()));
        var sabcList = Arrays.asList(sabc.split(System.lineSeparator()));
        // Replace with same line
        test(aList, 1, 1, null, a, a);
        // Replace with different line
        test(aList, 1, 1, null, z, z);
        // Replace with same line based on line match
        test(aList, 1, 1, a, a, a);
        // Replace with different line based on line match
        test(aList, 1, 1, a, z, z);
        // Replace single line with multiple lines
        test(aList, 1, 1, null, xyz, "xyz");
        // Replace single line with multiple lines based on lines match
        test(aList, 1, 1, a, xyz, "xyz");
        // Replace all lines
        test(abcList, 1, 3, null, xyz, "xyz");
        // Replace all lines based on lines match
        test(abcList, 1, 3, abc, xyz, "xyz");
        // Replace all lines with single line based on lines match
        test(abcList, 1, 3, abc, z, z);
        // Replace single line
        test(abcList, 1, 1, null, z, "zbc");
        // Replace single line based on line match
        test(abcList, 1, 1, a, z, "zbc");
        // Replace multiple lines
        test(abcList, 1, 2, null, z, "zc");
        // Replace multiple lines based on line match
        test(abcList, 1, 2, "ab", z, "zc");
        // Replace multiple lines based on line match
        test(abcList, 1, 2, "ab", xyz, "xyzc");

        // Test with space characters
        // Replace with same space line
        test(sList, 1, 1, null, " ", " ");
        // Replace with same line based on line match
        test(sList, 1, 1, " ", a, a);
        // Replace with same space line
        test(sabcList, 1, 1, null, a, "aabc");
        // Replace space line with different line based on line match
        test(sabcList, 1, 1, " ", a, "aabc");
        // Replace range of lines with space to different lines based on line match
        test(sabcList, 1, 2, " a", xyz, "xyzbc");

        // Mismatched range with "replace" lines Tests
        // Replace all lines with mismatched line
        test(aList, 1, 1, z, z, z);
        // Replace all lines with mismatched lines
        test(abcList, 1, 3, xyz, xyz, "xyz");
        // Replace single line with mismatched line
        test(abcList, 1, 1, z, z, "zbc");
        // Replace a range of lines with mismatched lines
        test(abcList, 1, 3, "ab", xyz, "xyz");
    }

    private static List<String> toList(String str) {
        return str.chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.toList());
    }

    private static void test(List<String> content, int from, int to, String replace,
            String replaceTo, String expected) throws IOException {
        Path path = Files.write(Paths.get("Test-" + counter++), content);
        try {
            FileUtils.patch(path, from, to, replace, replaceTo);
        } catch (IOException e) {
            if (e.getMessage().equals("Removed not the same")) {
                System.out.printf("Expected failure: Lines %s-%s, don't match with lines to replace '%s'%n",
                        from, to, replace);
                return;
            }
            throw e;
        }
        var lines = Files.readAllLines(path);
        System.out.printf("Content:%s, Resulted Lines: %s, Expected: %s%n", content, lines, toList(expected));
        if (!lines.equals(toList(expected))) {
            throw new IOException("Unmatched result");
        }
    }

}
