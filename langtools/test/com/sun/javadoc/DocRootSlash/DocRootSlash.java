/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4524350 4662945 4633447
 * @summary stddoclet: {@docRoot} inserts an extra trailing "/"
 * @author dkramer
 * @run main DocRootSlash
 */

import com.sun.javadoc.*;
import java.util.*;
import java.io.*;
import java.nio.*;
import java.util.regex.*;

/**
 * Runs javadoc and runs regression tests on the resulting HTML.
 * It reads each file, complete with newlines, into a string to easily
 * find strings that contain newlines.
 */
public class DocRootSlash
{
    private static final String BUGID = "4524350, 4662945, or 4633447";
    private static final String BUGNAME = "DocRootSlash";
    private static final String FS = System.getProperty("file.separator");
    private static final String PS = System.getProperty("path.separator");
    private static final String TMPDIR_STRING1 = "." + FS + "docs1" + FS;

    // Test number.  Needed because runResultsOnHTMLFile is run twice, and subtestNum
    // should increment across test runs.
    public static int subtestNum = 0;
    public static int numOfSubtestsPassed = 0;

    // Entry point
    public static void main(String[] args) {

        // Directory that contains source files that javadoc runs on
        String srcdir = System.getProperty("test.src", ".");

        runJavadoc(new String[] {"-d", TMPDIR_STRING1,
                                 "-Xdoclint:none",
                                 "-overview", (srcdir + FS + "overview.html"),
                                 "-header", "<A HREF=\"{@docroot}/package-list\">{&#064;docroot}</A> <A HREF=\"{@docRoot}/help-doc\">{&#064;docRoot}</A>",
                                 "-sourcepath", srcdir,
                                 "p1", "p2"});
        runTestsOnHTMLFiles(filenameArray);

        printSummary();
    }

    /** Run javadoc */
    public static void runJavadoc(String[] javadocArgs) {
        if (com.sun.tools.javadoc.Main.execute(javadocArgs) != 0) {
            throw new Error("Javadoc failed to execute");
        }
    }

    /**  The array of filenames to test */
    private static final String[] filenameArray = {
        TMPDIR_STRING1 + "p1" + FS + "C1.html" ,
        TMPDIR_STRING1 + "p1" + FS + "package-summary.html",
        TMPDIR_STRING1 + "overview-summary.html"
    };

    public static void runTestsOnHTMLFiles(String[] filenameArray) {
        String fileString;

        // Bugs 4524350 4662945
        for (int i = 0; i < filenameArray.length; i++ ) {

            // Read contents of file (whose filename is in filenames) into a string
            fileString = readFileToString(filenameArray[i]);

            System.out.println("\nSub-tests for file: " + filenameArray[i]
                                + " --------------");

            // Loop over all tests in a single file
            for ( int j = 0; j < 11; j++ ) {
                subtestNum += 1;

                // Compare actual to expected string for a single subtest
                compareActualToExpected(fileString);
            }
        }

        // Bug 4633447: Special test for overview-frame.html
        // Find two strings in file "overview-frame.html"
        String filename = TMPDIR_STRING1 + "overview-frame.html";
        fileString = readFileToString(filename);

        // Find first string <A HREF="./package-list"> in overview-frame.html
        subtestNum += 1;
        String stringToFind = "<A HREF=\"./package-list\">";
        String result;
        if ( fileString.indexOf(stringToFind) == -1 ) {
            result = "FAILED";
        } else {
            result = "succeeded";
            numOfSubtestsPassed += 1;
        }
        System.out.println("\nSub-test " + (subtestNum)
            + " for bug " + BUGID + " (" + BUGNAME + ") " + result + "\n"
            + "when searching for:\n"
            + stringToFind + "\n"
            + "in file " + filename);

        // Find second string <A HREF="./help-doc"> in overview-frame.html
        subtestNum += 1;
        stringToFind = "<A HREF=\"./help-doc\">";
        if ( fileString.indexOf(stringToFind) == -1 ) {
            result = "FAILED";
        } else {
            result = "succeeded";
            numOfSubtestsPassed += 1;
        }
        System.out.println("\nSub-test " + (subtestNum)
            + " for bug " + BUGID + " (" + BUGNAME + ") " + result + "\n"
            + "when searching for:\n"
            + stringToFind + "\n"
            + "in file " + filename);
    }

    public static void printSummary() {
        System.out.println("");
        if ( numOfSubtestsPassed == subtestNum ) {
            System.out.println("\nAll " + numOfSubtestsPassed + " subtests passed");
        } else {
            throw new Error("\n" + (subtestNum - numOfSubtestsPassed) + " of " + (subtestNum)
                            + " subtests failed for bug " + BUGID + " (" + BUGNAME + ")\n");
        }
    }

    // Read the contents of the file into a String
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

    /**
     * Regular expression pattern matching code adapted from Eric's
     * /java/pubs/dev/linkfix/src/LinkFix.java
     *
     * Prefix Pattern:
     * flag   (?i)            (case insensitive, so "a href" == "A HREF" and all combinations)
     * group1 (
     *          <a or <A
     *          \\s+          (one or more whitespace characters)
     *          href or HREF
     *          \"            (double quote)
     *        )
     * group2 ([^\"]*)        (link reference -- characters that don't include a quote)
     * group3 (\".*?>)        (" target="frameName">)
     * group4 (.*?)           (label - zero or more characters)
     * group5 (</a>)          (end tag)
     */
    static String prefix = "(?i)(<a\\s+href=";    // <a href=     (start group1)
    static String ref1   = "\")([^\"]*)(\".*?>)"; // doublequotes (end group1, group2, group3)
    static String ref2   = ")(\\S+?)([^<>]*>)";   // no quotes    (end group1, group2, group3)
    static String label  = "(.*?)";               // text label   (group4)
    static String end    = "(</a>)";              // </a>         (group5)

    /**
     * Compares the actual string to the expected string in the specified string
     * str   String to search through
     */
    static void compareActualToExpected(String str) {
        // Pattern must be compiled each run because subtestNum is incremented
        Pattern actualLinkPattern1 =
            Pattern.compile("Sub-test " + subtestNum + " Actual: " + prefix + ref1, Pattern.DOTALL);
        Pattern expectLinkPattern1 =
            Pattern.compile("Sub-test " + subtestNum + " Expect: " + prefix + ref1, Pattern.DOTALL);
        // Pattern linkPattern2 = Pattern.compile(prefix + ref2 + label + end, Pattern.DOTALL);

        CharBuffer charBuffer = CharBuffer.wrap(str);
        Matcher actualLinkMatcher1 = actualLinkPattern1.matcher(charBuffer);
        Matcher expectLinkMatcher1 = expectLinkPattern1.matcher(charBuffer);
        String result;
        if ( expectLinkMatcher1.find() && actualLinkMatcher1.find() ) {
            String expectRef = expectLinkMatcher1.group(2);
            String actualRef = actualLinkMatcher1.group(2);
            if ( actualRef.equals(expectRef) ) {
                result = "succeeded";
                numOfSubtestsPassed += 1;
                // System.out.println("pattern:   " + actualLinkPattern1.pattern());
                // System.out.println("actualRef: " + actualRef);
                // System.out.println("group0:    " + actualLinkMatcher1.group());
                // System.out.println("group1:    " + actualLinkMatcher1.group(1));
                // System.out.println("group2:    " + actualLinkMatcher1.group(2));
                // System.out.println("group3:    " + actualLinkMatcher1.group(3));
                // System.exit(0);
            } else {
                result = "FAILED";
            }
            System.out.println("\nSub-test " + (subtestNum)
                + " for bug " + BUGID + " (" + BUGNAME + ") " + result + "\n"
                + "Actual: \"" + actualRef + "\"" + "\n"
                + "Expect: \"" + expectRef + "\"");
        } else {
            System.out.println("Didn't find <A HREF> that fits the pattern: "
                  + expectLinkPattern1.pattern() );
        }
    }
}
