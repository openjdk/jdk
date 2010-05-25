/*
 * Copyright (c) 2002, 2009, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.javadoc.*;
import java.util.*;
import java.io.*;


/**
 * Runs javadoc and then runs regression tests on the resulting output.
 * This class currently contains three tests:
 * <ul>
 * <li> String search: Reads each file, complete with newlines,
 *      into a string.  Lets you search for strings that contain
 *      newlines.  String matching is case-sensitive.
 *      You can run javadoc multiple times with different arguments,
 *      generating output into different destination directories, and
 *      then perform a different array of tests on each one.
 *      To do this, the run method accepts a test array for testing
 *      that a string is found, and a negated test array for testing
 *      that a string is not found.
 * <li> Run diffs: Iterate through the list of given file pairs
 *      and diff the pairs.
 * <li> Check exit code: Check the exit code of Javadoc and
 *      record whether the test passed or failed.
 * </ul>
 *
 * @author Doug Kramer
 * @author Jamie Ho
 * @since 1.4.2
 */
public abstract class JavadocTester {

    protected static final String FS = System.getProperty("file.separator");
    protected static final String PS = System.getProperty("path.separator");
    protected static final String NL = System.getProperty("line.separator");
    protected static final String SRC_DIR = System.getProperty("test.src", ".");
    protected static final String JAVA_VERSION = System.getProperty("java.version");
    protected static final String[][] NO_TEST = new String[][] {};

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the error output.
     */
    public static final String ERROR_OUTPUT = "ERROR_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the notice output.
     */
    public static final String NOTICE_OUTPUT = "NOTICE_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in the warning output.
     */
    public static final String WARNING_OUTPUT = "WARNING_OUTPUT";

    /**
     * Use this as the file name in the test array when you want to search
     * for a string in standard output.
     */
    public static final String STANDARD_OUTPUT = "STANDARD_OUTPUT";

    /**
     * The default doclet.
     */
    public static final String DEFAULT_DOCLET_CLASS = "com.sun.tools.doclets.formats.html.HtmlDoclet";
    public static final String DEFAULT_DOCLET_CLASS_OLD = "com.sun.tools.doclets.standard.Standard";

    /**
     * The writer to write error messages.
     */
    public StringWriter errors;

    /**
     * The writer to write notices.
     */
    public StringWriter notices;

    /**
     * The writer to write warnings.
     */
    public StringWriter warnings;

    /**
     * The buffer of warning output..
     */
    public StringBuffer standardOut;

    /**
     * The current subtest number.
     */
    private static int numTestsRun = 0;

    /**
     * The number of subtests passed.
     */
    private static int numTestsPassed = 0;

    /**
     * The current run of javadoc
     */
    private static int javadocRunNum = 0;

    /**
     * Whether or not to match newlines exactly.
     * Set this value to false if the match strings
     * contain text from javadoc comments containing
     * non-platform newlines.
     */
    protected boolean exactNewlineMatch = true;

    /**
     * Construct a JavadocTester.
     */
    public JavadocTester() {
    }

    /**
     * Return the bug id.
     * @return the bug id
     */
    public abstract String getBugId();

    /**
     * Return the name of the bug.
     * @return the name of the bug
     */
    public abstract String getBugName();

    /**
     * Execute the tests.
     *
     * @param tester           the tester to execute
     * @param args             the arguments to pass to Javadoc
     * @param testArray        the array of tests
     * @param negatedTestArray the array of negated tests
     * @return                 the return code for the execution of Javadoc
     */
    public static int run(JavadocTester tester, String[] args,
            String[][] testArray, String[][] negatedTestArray) {
        int returnCode = tester.runJavadoc(args);
        tester.runTestsOnHTML(testArray, negatedTestArray);
        return returnCode;
    }

    /**
     * Execute Javadoc using the default doclet.
     *
     * @param args  the arguments to pass to Javadoc
     * @return      the return code from the execution of Javadoc
     */
    public int runJavadoc(String[] args) {
        float javaVersion = Float.parseFloat(JAVA_VERSION.substring(0,3));
        String docletClass = javaVersion < 1.5 ?
            DEFAULT_DOCLET_CLASS_OLD : DEFAULT_DOCLET_CLASS;
        return runJavadoc(docletClass, args);
    }


    /**
     * Execute Javadoc.
     *
     * @param docletClass the doclet being tested.
     * @param args  the arguments to pass to Javadoc
     * @return      the return code from the execution of Javadoc
     */
    public int runJavadoc(String docletClass, String[] args) {
        javadocRunNum++;
        if (javadocRunNum == 1) {
            System.out.println("\n" + "Running javadoc...");
        } else {
            System.out.println("\n" + "Running javadoc (run "
                                    + javadocRunNum + ")...");
        }
        initOutputBuffers();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream prev = System.out;
        System.setOut(new PrintStream(stdout));
        int returnCode = com.sun.tools.javadoc.Main.execute(
                getBugName(),
                new PrintWriter(errors, true),
                new PrintWriter(warnings, true),
                new PrintWriter(notices, true),
                docletClass,
                getClass().getClassLoader(),
                args);
        System.setOut(prev);
        standardOut = new StringBuffer(stdout.toString());
        printJavadocOutput();
        return returnCode;
    }

    /**
     * Create new string writer buffers
     */
    private void initOutputBuffers() {
        errors   = new StringWriter();
        notices  = new StringWriter();
        warnings = new StringWriter();
    }

    /**
     * Run array of tests on the resulting HTML.
     * This method accepts a testArray for testing that a string is found
     * and a negatedTestArray for testing that a string is not found.
     *
     * @param testArray         the array of tests
     * @param negatedTestArray  the array of negated tests
     */
    public void runTestsOnHTML(String[][] testArray, String[][] negatedTestArray) {
        runTestsOnHTML(testArray, false);
        runTestsOnHTML(negatedTestArray, true);
    }

    /**
     * Run the array of tests on the resulting HTML.
     *
     * @param testArray the array of tests
     * @param isNegated true if test is negated; false otherwise
     */
    private void runTestsOnHTML(String[][] testArray , boolean isNegated) {
        for (int i = 0; i < testArray.length; i++) {

            numTestsRun++;

            System.out.print("Running subtest #" + numTestsRun + "... ");

            // Get string to find
            String stringToFind = testArray[i][1];

            // Read contents of file into a string
            String fileString;
            try {
                fileString = readFileToString(testArray[i][0]);
            } catch (Error e) {
                if (isNegated) {
                  numTestsPassed += 1;
                  System.out.println("Passed\n not found:\n"
                    + stringToFind + " in non-existent " + testArray[i][0] + "\n");
                  continue;
                }
                throw e;
            }
            // Find string in file's contents
            boolean isFound = findString(fileString, stringToFind);
            if ((isNegated && !isFound) || (!isNegated && isFound) ) {
                numTestsPassed += 1;
                System.out.println( "Passed" + "\n"
                                    + (isNegated ? "not found:" : "found:") + "\n"
                                    + stringToFind + " in " + testArray[i][0] + "\n");
            } else {
                System.out.println( "FAILED" + "\n"
                                    + "for bug " + getBugId()
                                    + " (" + getBugName() + ")" + "\n"
                                    + "when searching for:" + "\n"
                                    + stringToFind
                                    + " in " + testArray[i][0] + "\n");
            }
        }
    }

    /**
     * Iterate through the list of given file pairs and diff each file.
     *
     * @param filePairs the pairs of files to diff.
     * @throws an Error is thrown if any differences are found between
     * file pairs.
     */
    public void runDiffs(String[][] filePairs) throws Error {
        runDiffs(filePairs, true);
    }

    /**
     * Iterate through the list of given file pairs and diff each file.
     *
     * @param filePairs the pairs of files to diff.
     * @param throwErrorIFNoMatch flag to indicate whether or not to throw
     * an error if the files do not match.
     *
     * @throws an Error is thrown if any differences are found between
     * file pairs and throwErrorIFNoMatch is true.
     */
    public void runDiffs(String[][] filePairs, boolean throwErrorIfNoMatch) throws Error {
        for (int i = 0; i < filePairs.length; i++) {
            diff(filePairs[i][0], filePairs[i][1], throwErrorIfNoMatch);
        }
    }

    /**
     * Check the exit code of Javadoc and record whether the test passed
     * or failed.
     *
     * @param expectedExitCode The exit code that is required for the test
     * to pass.
     * @param actualExitCode The actual exit code from the previous run of
     * Javadoc.
     */
    public void checkExitCode(int expectedExitCode, int actualExitCode) {
        numTestsRun++;
        if (expectedExitCode == actualExitCode) {
            System.out.println( "Passed" + "\n" + " got return code " +
                actualExitCode);
            numTestsPassed++;
        } else {
            System.out.println( "FAILED" + "\n" + "for bug " + getBugId()
                + " (" + getBugName() + ")" + "\n" + "Expected return code " +
                expectedExitCode + " but got " + actualExitCode);
        }
    }

    /**
     * Print a summary of the test results.
     */
    protected void printSummary() {
        if ( numTestsRun != 0 && numTestsPassed == numTestsRun ) {
            // Test passed
            System.out.println("\n" + "All " + numTestsPassed
                                             + " subtests passed");
        } else {
            // Test failed
            throw new Error("\n" + (numTestsRun - numTestsPassed)
                                    + " of " + (numTestsRun)
                                    + " subtests failed for bug " + getBugId()
                                    + " (" + getBugName() + ")" + "\n");
        }
    }

    /**
     * Print the output stored in the buffers.
     */
    protected void printJavadocOutput() {
        System.out.println(STANDARD_OUTPUT + " : \n" + getStandardOutput());
        System.err.println(ERROR_OUTPUT + " : \n" + getErrorOutput());
        System.err.println(WARNING_OUTPUT + " : \n" + getWarningOutput());
        System.out.println(NOTICE_OUTPUT + " : \n" + getNoticeOutput());
    }

    /**
     * Read the file and return it as a string.
     *
     * @param fileName  the name of the file to read
     * @return          the file in string format
     */
    public String readFileToString(String fileName) throws Error {
        if (fileName.equals(ERROR_OUTPUT)) {
            return getErrorOutput();
        } else if (fileName.equals(NOTICE_OUTPUT)) {
            return getNoticeOutput();
        } else if (fileName.equals(WARNING_OUTPUT)) {
            return getWarningOutput();
        } else if (fileName.equals(STANDARD_OUTPUT)) {
            return getStandardOutput();
        }
        try {
            File file = new File(fileName);
            if ( !file.exists() ) {
                System.out.println("\n" + "FILE DOES NOT EXIST: " + fileName);
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
            throw new Error("File not found: " + fileName);
        } catch (IOException e) {
            System.err.println(e);
            throw new Error("Error reading file: " + fileName);
        }
    }

    /**
     * Compare the two given files.
     *
     * @param file1 the first file to compare.
     * @param file2 the second file to compare.
     * @param throwErrorIFNoMatch flag to indicate whether or not to throw
     * an error if the files do not match.
     * @return true if the files are the same and false otherwise.
     */
    public boolean diff(String file1, String file2, boolean throwErrorIFNoMatch) throws Error {
        String file1Contents = readFileToString(file1);
        String file2Contents = readFileToString(file2);
        numTestsRun++;
        if (file1Contents.trim().compareTo(file2Contents.trim()) == 0) {
            System.out.println("Diff successful: " + file1 + ", " + file2);
            numTestsPassed++;
            return true;
        } else if (throwErrorIFNoMatch) {
            throw new Error("Diff failed: " + file1 + ", " + file2);
        } else {
            return false;
        }
    }

    /**
     * Search for the string in the given file and return true
     * if the string was found.
     * If exactNewlineMatch is false, newlines will be normalized
     * before the comparison.
     *
     * @param fileString    the contents of the file to search through
     * @param stringToFind  the string to search for
     * @return              true if the string was found
     */
    private boolean findString(String fileString, String stringToFind) {
        if (exactNewlineMatch) {
            return fileString.indexOf(stringToFind) >= 0;
        } else {
            return fileString.replace(NL, "\n").indexOf(stringToFind.replace(NL, "\n")) >= 0;
        }
    }


    /**
     * Return the standard output.
     * @return the standard output
     */
    public String getStandardOutput() {
        return standardOut.toString();
    }

    /**
     * Return the error output.
     * @return the error output
     */
    public String getErrorOutput() {
        return errors.getBuffer().toString();
    }

    /**
     * Return the notice output.
     * @return the notice output
     */
    public String getNoticeOutput() {
        return notices.getBuffer().toString();
    }

    /**
     * Return the warning output.
     * @return the warning output
     */
    public String getWarningOutput() {
        return warnings.getBuffer().toString();
    }

    /**
     * A utility to copy a directory from one place to another.
     * We may possibly want to move this to our doclet toolkit in
     * the near future and maintain it from there.
     *
     * @param targetDir the directory to copy.
     * @param destDir the destination to copy the directory to.
     */
    public static void copyDir(String targetDir, String destDir) {
        if (targetDir.endsWith("SCCS")) {
            return;
        }
        try {
            File targetDirObj = new File(targetDir);
            File destDirParentObj = new File(destDir);
            File destDirObj = new File(destDirParentObj, targetDirObj.getName());
            if (! destDirParentObj.exists()) {
                destDirParentObj.mkdir();
            }
            if (! destDirObj.exists()) {
                destDirObj.mkdir();
            }
            String[] files = targetDirObj.list();
            for (int i = 0; i < files.length; i++) {
                File srcFile = new File(targetDirObj, files[i]);
                File destFile = new File(destDirObj, files[i]);
                if (srcFile.isFile()) {
                    System.out.println("Copying " + srcFile + " to " + destFile);
                        copyFile(destFile, srcFile);
                } else if(srcFile.isDirectory()) {
                    copyDir(srcFile.getAbsolutePath(), destDirObj.getAbsolutePath());
                }
            }
        } catch (IOException exc) {
            throw new Error("Could not copy " + targetDir + " to " + destDir);
        }
    }

    /**
     * Copy source file to destination file.
     *
     * @throws SecurityException
     * @throws IOException
     */
    public static void copyFile(File destfile, File srcfile)
        throws IOException {
        byte[] bytearr = new byte[512];
        int len = 0;
        FileInputStream input = new FileInputStream(srcfile);
        File destDir = destfile.getParentFile();
        destDir.mkdirs();
        FileOutputStream output = new FileOutputStream(destfile);
        try {
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        } catch (FileNotFoundException exc) {
        } catch (SecurityException exc) {
        } finally {
            input.close();
            output.close();
        }
    }
}
