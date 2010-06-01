/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4530730
 * @summary stddoclet: With frames off, window titles have "()" appended
 * @author dkramer
 * @run main WindowTitles
 */


import com.sun.javadoc.*;
import java.util.*;
import java.io.*;

// If needing regular expression pattern matching,
// see /java/pubs/dev/linkfix/src/LinkFix.java

/**
 * Runs javadoc and runs regression tests on the resulting HTML.
 * It reads each file, complete with newlines, into a string to easily
 * find strings that contain newlines.
 */
public class WindowTitles
{
    private static final String BUGID = "4530730";
    private static final String BUGNAME = "WindowTitles";
    private static final String FS = System.getProperty("file.separator");
    private static final String PS = System.getProperty("path.separator");
    private static final String LS = System.getProperty("line.separator");
    private static final String TMPDIR_STRING1 = "." + FS + "docs1" + FS;
    private static final String TMPDIR_STRING2 = "." + FS + "docs2" + FS;

    // Subtest number.  Needed because runResultsOnHTML is run twice, and subtestNum
    // should increment across subtest runs.
    public static int subtestNum = 0;
    public static int numSubtestsPassed = 0;

    // Entry point
    public static void main(String[] args) {

        // Directory that contains source files that javadoc runs on
        String srcdir = System.getProperty("test.src", ".");

        // Test for all cases except the split index page
        runJavadoc(new String[] {"-d", TMPDIR_STRING1,
                                 "-use",
                                 "-sourcepath", srcdir,
                                 "p1", "p2"});
        runTestsOnHTML(testArray);

        // Test only for the split-index case (and run on only one package)
        System.out.println("");  // blank line
        runJavadoc(new String[] {"-d", TMPDIR_STRING2,
                                 "-splitindex",
                                 "-sourcepath", System.getProperty("test.src", "."),
                                 "p1"});
        runTestsOnHTML(testSplitIndexArray);

        printSummary();
    }

    /** Run javadoc */
    public static void runJavadoc(String[] javadocArgs) {
        if (com.sun.tools.javadoc.Main.execute(javadocArgs) != 0) {
            throw new Error("Javadoc failed to execute");
        }
    }

    /**
     * Assign value for [ stringToFind, filename ]
     * NOTE: The standard doclet uses platform-specific line separators (LS)
     */
    private static final String[][] testArray = {

            { "<TITLE>" + LS + "Overview" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "overview-summary.html"                  },

            { "<TITLE>" + LS + "Class Hierarchy" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "overview-tree.html"                     },

            { "<TITLE>" + LS + "Overview List" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "overview-frame.html"                    },

            { "<TITLE>" + LS + "p1" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "package-summary.html"       },

            { "<TITLE>" + LS + "p1" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "package-frame.html"         },

            { "<TITLE>" + LS + "p1 Class Hierarchy" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "package-tree.html"          },

            { "<TITLE>" + LS + "Uses of Package p1" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "package-use.html"           },

            { "<TITLE>" + LS + "C1" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "C1.html"                    },

            { "<TITLE>" + LS + "All Classes" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "allclasses-frame.html"                  },

            { "<TITLE>" + LS + "All Classes" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "allclasses-noframe.html"                },

            { "<TITLE>" + LS + "Constant Field Values" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "constant-values.html"                   },

            { "<TITLE>" + LS + "Deprecated List" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "deprecated-list.html"                   },

            { "<TITLE>" + LS + "Serialized Form" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "serialized-form.html"                   },

            { "<TITLE>" + LS + "API Help" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "help-doc.html"                          },

            { "<TITLE>" + LS + "Index" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "index-all.html"                         },

            { "<TITLE>" + LS + "Uses of Class p1.C1" + LS + "</TITLE>",
                    TMPDIR_STRING1 + "p1" + FS + "class-use" + FS + "C1.html" },
        };

    /**
     * Assign value for [ stringToFind, filename ] for split index page
     */
    private static final String[][] testSplitIndexArray = {
            { "<TITLE>" + LS + "C-Index" + LS + "</TITLE>",
                    TMPDIR_STRING2 + "index-files" + FS + "index-1.html"       },
        };

    public static void runTestsOnHTML(String[][] testArray) {

        for (int i = 0; i < testArray.length; i++) {

            subtestNum += 1;

            // Read contents of file into a string
            String fileString = readFileToString(testArray[i][1]);

            // Get string to find
            String stringToFind = testArray[i][0];

            // Find string in file's contents
            if (findString(fileString, stringToFind) == -1) {
                System.out.println("\nSub-test " + (subtestNum)
                    + " for bug " + BUGID + " (" + BUGNAME + ") FAILED\n"
                    + "when searching for:\n"
                    + stringToFind);
            } else {
                numSubtestsPassed += 1;
                System.out.println("\nSub-test " + (subtestNum) + " passed:\n" + stringToFind);
            }
        }
    }

    public static void printSummary() {
        if ( numSubtestsPassed == subtestNum ) {
            System.out.println("\nAll " + numSubtestsPassed + " subtests passed");
        } else {
            throw new Error("\n" + (subtestNum - numSubtestsPassed) + " of " + (subtestNum)
                             + " subtests failed for bug " + BUGID + " (" + BUGNAME + ")\n");
        }
    }

    // Read the file into a String
    public static String readFileToString(String filename) {
        try {
            File file = new File(filename);
            if ( !file.exists() ) {
                System.out.println("\nFILE DOES NOT EXIST: " + filename);
            }
            BufferedReader in = new BufferedReader(new FileReader(file));

            // Create an array of characters the size of the file
            char[] allChars = new char[(int)file.length()];

            // Read the characters into the allChars array
            in.read(allChars, 0, (int)file.length());
            in.close();

            // Convert to a string
            String allCharsString = new String(allChars);

            return allCharsString;

        } catch (FileNotFoundException e) {
            System.err.println(e);
            return "";
        } catch (IOException e) {
            System.err.println(e);
            return "";
        }
    }

    public static int findString(String fileString, String stringToFind) {
        return fileString.indexOf(stringToFind);
    }
}
