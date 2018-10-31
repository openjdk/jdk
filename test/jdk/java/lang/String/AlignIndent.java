/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Unit tests for String#align and String#indent
 * @run main AlignIndent
 */

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AlignIndent {
    static final List<String> ENDS = List.of("", "\n", "   \n", "\n\n", "\n\n\n");
    static final List<String> MIDDLES = List.of(
            "",
            "xyz",
            "   xyz",
            "      xyz",
            "xyz   ",
            "   xyz   ",
            "      xyz   ",
            "xyz\u2022",
            "   xyz\u2022",
            "xyz\u2022   ",
            "   xyz\u2022   ",
            "   // comment"
    );

    public static void main(String[] args) {
        test1();
        test2();
        test3();
        test4();
    }

    /*
     * Test String#align() functionality.
     */
    static void test1() {
        for (String prefix : ENDS) {
            for (String suffix : ENDS) {
                for (String middle : MIDDLES) {
                    {
                        String input = prefix + "   abc   \n" + middle + "\n   def   \n" + suffix;
                        String output = input.align();

                        String[] inLines = input.split("\\R");
                        String[] outLines = output.split("\\R");

                        String[] inLinesBody = getBody(inLines);

                        if (inLinesBody.length < outLines.length) {
                            report("String::align()", "Result has more lines than expected", input, output);
                        } else if (inLinesBody.length > outLines.length) {
                            report("String::align()", "Result has fewer lines than expected", input, output);
                        }

                        int indent = -1;
                        for (int i = 0; i < inLinesBody.length; i++) {
                            String in = inLinesBody[i];
                            String out = outLines[i];
                            if (!out.isBlank()) {
                                int offset = in.indexOf(out);
                                if (offset == -1) {
                                    report("String::align()", "Portions of line are missing", input, output);
                                }
                                if (indent == -1) {
                                    indent = offset;
                                } else if (offset != indent) {
                                    report("String::align()",
                                            "Inconsistent indentation in result", input, output);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Test String#align(int n) functionality.
     */
    static void test2() {
        for (int adjust : new int[] {-8, -7, -4, -3, -2, -1, 0, 1, 2, 3, 4, 7, 8}) {
            for (String prefix : ENDS) {
                for (String suffix : ENDS) {
                    for (String middle : MIDDLES) {
                        {
                            String input = prefix + "   abc   \n" + middle + "\n   def   \n" + suffix;
                            String output = input.align(adjust);
                            String expected = input.align().indent(adjust);

                            if (!output.equals(expected)) {
                                report("String::align(int n)",
                                        "Result inconsistent with align().indent(n)", expected, output);
                            }
                        }
                    }
                }
            }
        }
    }

    /*
     * Test String#indent(int n) functionality.
     */
    static void test3() {
        for (int adjust : new int[] {-8, -7, -4, -3, -2, -1, 0, 1, 2, 3, 4, 7, 8}) {
            for (String prefix : ENDS) {
                for (String suffix : ENDS) {
                    for (String middle : MIDDLES) {
                        String input = prefix + "   abc   \n" + middle + "\n   def   \n" + suffix;
                        String output = input.indent(adjust);

                        Stream<String> stream = input.lines();
                        if (adjust > 0) {
                            final String spaces = " ".repeat(adjust);
                            stream = stream.map(s -> s.isBlank() ? s : spaces + s);
                        } else if (adjust < 0) {
                            stream = stream.map(s -> s.substring(Math.min(-adjust, indexOfNonWhitespace(s))));
                        }
                        String expected = stream.collect(Collectors.joining("\n", "", "\n"));

                        if (!output.equals(expected)) {
                            report("String::indent(int n)",
                                    "Result indentation not as expected", expected, output);
                        }
                    }
                }
            }
        }
    }

    /*
     * JDK-8212694: Using Raw String Literals with align() and Integer.MIN_VALUE causes out of memory error
     */
    static void test4() {
        try {
            String str = "\n    A\n".align(Integer.MIN_VALUE);
        } catch (OutOfMemoryError ex) {
            System.err.println("align(Integer.MIN_VALUE) not clipping indentation");
            throw new RuntimeException();
        }
    }

    public static int indexOfNonWhitespace(String s) {
        int left = 0;
        while (left < s.length()) {
            char ch = s.charAt(left);
            if (ch != ' ' && ch != '\t' && !Character.isWhitespace(ch)) {
                break;
            }
            left++;
        }
        return left;
    }


    private static String[] getBody(String[] inLines) {
        int from = -1, to = -1;
        for (int i = 0; i < inLines.length; i++) {
            String line = inLines[i];
            if (!line.isBlank()) {
                if (from == -1) {
                    from = i;
                }
                to = i + 1;
            }
        }
        return Arrays.copyOfRange(inLines, from, to);
    }

    /*
     * Report difference in result.
     */
    static void report(String test, String message, String input, String output) {
        System.err.println("Testing " + test + ": " + message);
        System.err.println();
        System.err.println("Input: length = " + input.length());
        System.err.println("_".repeat(40));
        System.err.print(input.replaceAll(" ", "."));
        System.err.println("_".repeat(40));
        System.err.println();
        System.err.println("Output: length = " + output.length());
        System.err.println("_".repeat(40));
        System.err.print(output.replaceAll(" ", "."));
        System.err.println("_".repeat(40));
        throw new RuntimeException();
    }
}
