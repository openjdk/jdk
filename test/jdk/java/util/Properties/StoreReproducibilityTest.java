/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;

/*
 * @test
 * @summary Tests that the Properties.store() APIs generate output that is reproducible
 * @bug 8231640 8282023 8316540
 * @comment The test launches several processes and in the presence of -Xcomp it's too slow
 *          and thus causes timeouts
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib
 * @run driver StoreReproducibilityTest
 */
public class StoreReproducibilityTest {

    private static final String DATE_FORMAT_PATTERN = "EEE MMM dd HH:mm:ss zzz uuuu";
    private static final String SYS_PROP_JAVA_PROPERTIES_DATE = "java.properties.date";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN, Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    public static void main(final String[] args) throws Exception {
        // no security manager enabled
        testWithoutSecurityManager();
        // security manager enabled and security policy explicitly allows
        // read permissions on java.properties.date system property
        testWithSecMgrExplicitPermission();
        // security manager enabled and no explicit permission on java.properties.date system property
        testWithSecMgrNoSpecificPermission();
        // free form non-date value for java.properties.date system property
        testNonDateSysPropValue();
        // blank value for java.properties.date system property
        testBlankSysPropValue();
        // empty value for java.properties.date system property
        testEmptySysPropValue();
        // value for java.properties.date system property contains line terminator characters
        testMultiLineSysPropValue();
        // value for java.properties.date system property contains backslash character
        testBackSlashInSysPropValue();
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed a value for the
     * {@code java.properties.date} system property and the date comment written out
     * to the file is expected to use this value.
     * The program is launched multiple times with the same value for {@code java.properties.date}
     * and the output written by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code java.properties.date}.
     * The launched Java program is run without any security manager
     */
    private static void testWithoutSecurityManager() throws Exception {
        final List<Path> storedFiles = new ArrayList<>();
        final String sysPropVal = FORMATTER.format(Instant.ofEpochSecond(243535322));
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            executeJavaProcess(processBuilder);
            assertExpectedComment(tmpFile, sysPropVal);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sysPropVal);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed a value for the
     * {@code java.properties.date} system property and the date comment written out to the file
     * is expected to use this value.
     * The launched Java program is run with the default security manager and is granted
     * a {@code read} permission on {@code java.properties.date}.
     * The program is launched multiple times with the same value for {@code java.properties.date}
     * and the output written by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code java.properties.date}.
     */
    private static void testWithSecMgrExplicitPermission() throws Exception {
        final Path policyFile = Files.createTempFile("8231640", ".policy");
        Files.write(policyFile, Collections.singleton("""
                grant {
                    // test writes/stores to a file, so FilePermission
                    permission java.io.FilePermission "<<ALL FILES>>", "read,write";
                    // explicitly grant read permission on java.properties.date system property
                    // to verify store() APIs work fine
                    permission java.util.PropertyPermission "java.properties.date", "read";
                };
                """));
        final List<Path> storedFiles = new ArrayList<>();
        final String sysPropVal = FORMATTER.format(Instant.ofEpochSecond(1234342423));
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                    "-Djava.security.manager",
                    "-Djava.security.policy=" + policyFile,
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            executeJavaProcess(processBuilder);
            assertExpectedComment(tmpFile, sysPropVal);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sysPropVal);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed a value for the
     * {@code java.properties.date} system property and the date comment written out to the file
     * is expected to use this value.
     * The launched Java program is run with the default security manager and is NOT granted
     * any explicit permission for {@code java.properties.date} system property.
     * The program is launched multiple times with the same value for {@code java.properties.date}
     * and the output written by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code java.properties.date}.
     */
    private static void testWithSecMgrNoSpecificPermission() throws Exception {
        final Path policyFile = Files.createTempFile("8231640", ".policy");
        Files.write(policyFile, Collections.singleton("""
                grant {
                    // test writes/stores to a file, so FilePermission
                    permission java.io.FilePermission "<<ALL FILES>>", "read,write";
                    // no other grants, not even "read" java.properties.date system property.
                    // test should still work fine and the date comment should correspond to the value of
                    // java.properties.date system property.
                };
                """));
        final List<Path> storedFiles = new ArrayList<>();
        final String sysPropVal = FORMATTER.format(Instant.ofEpochSecond(1234342423));
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                    "-Djava.security.manager",
                    "-Djava.security.policy=" + policyFile,
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            executeJavaProcess(processBuilder);
            assertExpectedComment(tmpFile, sysPropVal);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sysPropVal);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed a {@link String#isBlank() blank} value
     * for the {@code java.properties.date} system property.
     * It is expected and verified in this test that such a value for the system property
     * will cause a comment line to be written out with only whitespaces.
     * The launched program is expected to complete without any errors.
     */
    private static void testBlankSysPropValue() throws Exception {
        final List<Path> storedFiles = new ArrayList<>();
        final String sysPropVal = "      \t";
        for (int i = 0; i < 2; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            executeJavaProcess(processBuilder);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
            String blankCommentLine = findNthComment(tmpFile, 2);
            if (blankCommentLine == null) {
                throw new RuntimeException("Comment line representing the value of "
                        + SYS_PROP_JAVA_PROPERTIES_DATE + " system property is missing in file " + tmpFile);
            }
            if (!blankCommentLine.isBlank()) {
                throw new RuntimeException("Expected comment line to be blank but was " + blankCommentLine);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sysPropVal);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed a {@link String#isEmpty() empty} value
     * for the {@code java.properties.date} system property.
     * It is expected and verified in this test that such a value for the system property
     * will cause the current date and time to be written out as a comment.
     * The launched program is expected to complete without any errors.
     */
    private static void testEmptySysPropValue() throws Exception {
        for (int i = 0; i < 2; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=",
                    "-Duser.timezone=UTC",
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            Date launchedAt = new Date();
            // wait for a second before launching so that we can then expect
            // the date written out by the store() APIs to be "after" this launch date
            Thread.sleep(1000);
            executeJavaProcess(processBuilder);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
            assertCurrentDate(tmpFile, launchedAt);
        }
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed the {@code java.properties.date}
     * system property with a value that doesn't represent a formatted date.
     * It is expected and verified in this test that such a value for the system property
     * will cause the comment to use that value verbatim. The launched program is expected to complete
     * without any errors.
     */
    private static void testNonDateSysPropValue() throws Exception {
        final String sysPropVal = "foo-bar";
        final List<Path> storedFiles = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                    "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            executeJavaProcess(processBuilder);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
            assertExpectedComment(tmpFile, sysPropVal);
        }
        assertAllFileContentsAreSame(storedFiles, sysPropVal);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed the {@code java.properties.date}
     * system property with a value that has line terminator characters.
     * It is expected and verified in this test that such a value for the system property
     * will cause the comment written out to be multiple separate comments. The launched program is expected
     * to complete without any errors.
     */
    private static void testMultiLineSysPropValue() throws Exception {
        final String[] sysPropVals = {"hello-world\nc=d", "hello-world\rc=d", "hello-world\r\nc=d"};
        for (final String sysPropVal : sysPropVals) {
            final List<Path> storedFiles = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final Path tmpFile = Files.createTempFile("8231640", ".props");
                storedFiles.add(tmpFile);
                final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                        "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                        StoreTest.class.getName(),
                        tmpFile.toString(),
                        i % 2 == 0 ? "--use-outputstream" : "--use-writer");
                executeJavaProcess(processBuilder);
                if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                    throw new RuntimeException("Unexpected properties stored in " + tmpFile);
                }
                // verify this results in 2 separate comment lines in the stored file
                String commentLine1 = findNthComment(tmpFile, 2);
                String commentLine2 = findNthComment(tmpFile, 3);
                if (commentLine1 == null || commentLine2 == null) {
                    throw new RuntimeException("Did not find the expected multi-line comments in " + tmpFile);
                }
                if (!commentLine1.equals("hello-world")) {
                    throw new RuntimeException("Unexpected comment line " + commentLine1 + " in " + tmpFile);
                }
                if (!commentLine2.equals("c=d")) {
                    throw new RuntimeException("Unexpected comment line " + commentLine2 + " in " + tmpFile);
                }
            }
            assertAllFileContentsAreSame(storedFiles, sysPropVal);
        }
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed the {@code java.properties.date}
     * system property with a value that has backslash character.
     * It is expected and verified in this test that such a value for the system property
     * will not cause any malformed comments or introduce any new properties in the stored content.
     * The launched program is expected to complete without any errors.
     */
    private static void testBackSlashInSysPropValue() throws Exception {
        final String[] sysPropVals = {"\\hello-world", "hello-world\\", "hello-world\\c=d",
                "newline-plus-backslash\\\nc=d"};
        for (final String sysPropVal : sysPropVals) {
            final List<Path> storedFiles = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                final Path tmpFile = Files.createTempFile("8231640", ".props");
                storedFiles.add(tmpFile);
                final ProcessBuilder processBuilder = ProcessTools.createTestJavaProcessBuilder(
                        "-D" + SYS_PROP_JAVA_PROPERTIES_DATE + "=" + sysPropVal,
                        StoreTest.class.getName(),
                        tmpFile.toString(),
                        i % 2 == 0 ? "--use-outputstream" : "--use-writer");
                executeJavaProcess(processBuilder);
                if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                    throw new RuntimeException("Unexpected properties stored in " + tmpFile);
                }
                String commentLine1 = findNthComment(tmpFile, 2);
                if (commentLine1 == null) {
                    throw new RuntimeException("Did not find the expected comment line in " + tmpFile);
                }
                if (sysPropVal.contains("newline-plus-backslash")) {
                    if (!commentLine1.equals("newline-plus-backslash\\")) {
                        throw new RuntimeException("Unexpected comment line " + commentLine1 + " in " + tmpFile);
                    }
                    // we expect this specific system property value to be written out into 2 separate comment lines
                    String commentLine2 = findNthComment(tmpFile, 3);
                    if (commentLine2 == null) {
                        throw new RuntimeException(sysPropVal + " was expected to be split into 2 comment line, " +
                                "but wasn't, in " + tmpFile);
                    }
                    if (!commentLine2.equals("c=d")) {
                        throw new RuntimeException("Unexpected comment line " + commentLine2 + " in " + tmpFile);
                    }
                } else {
                    if (!commentLine1.equals(sysPropVal)) {
                        throw new RuntimeException("Unexpected comment line " + commentLine1 + " in " + tmpFile);
                    }
                }
            }
            assertAllFileContentsAreSame(storedFiles, sysPropVal);
        }
    }

    // launches the java process and waits for it to exit. throws an exception if exit value is non-zero
    private static void executeJavaProcess(ProcessBuilder pb) throws Exception {
        final OutputAnalyzer outputAnalyzer = ProcessTools.executeProcess(pb);
        try {
            outputAnalyzer.shouldHaveExitValue(0);
        } finally {
            // print out any stdout/err that was generated in the launched program
            outputAnalyzer.reportDiagnosticSummary();
        }
    }

    // Properties.load() from the passed file and return the loaded Properties instance
    private static Properties loadProperties(final Path file) throws IOException {
        final Properties props = new Properties();
        props.load(Files.newBufferedReader(file));
        return props;
    }

    /**
     * Verifies that the comment in the {@code destFile} is same as {@code expectedComment},
     * instead of the default date comment.
     */
    private static void assertExpectedComment(final Path destFile,
                                              final String expectedComment) throws Exception {
        final String actualComment = findNthComment(destFile, 2);
        if (actualComment == null) {
            throw new RuntimeException("Comment \"" + expectedComment + "\" not found in stored properties " + destFile);
        }
        if (!expectedComment.equals(actualComment)) {
            throw new RuntimeException("Expected comment \"" + expectedComment + "\" but found \"" + actualComment + "\" " +
                    "in stored properties " + destFile);
        }
    }

    /**
     * Verifies that the date comment in the {@code destFile} can be parsed using the
     * "EEE MMM dd HH:mm:ss zzz uuuu" format and the time represented by it is {@link Date#after(Date) after}
     * the passed {@code date}
     * The JVM runtime to invoke this method should set the time zone to UTC, i.e, specify
     * "-Duser.timezone=UTC" at the command line. Otherwise, it will fail with some time
     * zones that have ambiguous short names, such as "IST"
     */
    private static void assertCurrentDate(final Path destFile, final Date date) throws Exception {
        final String dateComment = findNthComment(destFile, 2);
        if (dateComment == null) {
            throw new RuntimeException("Date comment not found in stored properties " + destFile);
        }
        System.out.println("Found date comment " + dateComment + " in file " + destFile);
        final Date parsedDate;
        try {
            Instant instant = Instant.from(FORMATTER.parse(dateComment));
            parsedDate = new Date(instant.toEpochMilli());
        } catch (DateTimeParseException pe) {
            throw new RuntimeException("Unexpected date " + dateComment + " in stored properties " + destFile, pe);
        }
        if (!parsedDate.after(date)) {
            throw new RuntimeException("Expected date comment " + dateComment + " to be after " + date
                    + " but was " + parsedDate);
        }
    }

    // returns the "Nth" comment from the file. Comment index starts from 1.
    private static String findNthComment(Path file, int commentIndex) throws IOException {
        List<String> comments = new ArrayList<>();
        try (final BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    comments.add(line.substring(1));
                    if (comments.size() == commentIndex) {
                        return comments.get(commentIndex - 1);
                    }
                }
            }
        }
        return null;
    }

    // verifies the byte equality of the contents in each of the files
    private static void assertAllFileContentsAreSame(final List<Path> files,
                                                     final String sysPropVal) throws Exception {
        final byte[] file1Contents = Files.readAllBytes(files.get(0));
        for (int i = 1; i < files.size(); i++) {
            final byte[] otherFileContents = Files.readAllBytes(files.get(i));
            if (!Arrays.equals(file1Contents, otherFileContents)) {
                throw new RuntimeException("Properties.store() did not generate reproducible content when " +
                        SYS_PROP_JAVA_PROPERTIES_DATE + " was set to " + sysPropVal);
            }
        }
    }

    static class StoreTest {
        private static final Properties propsToStore = new Properties();

        static {
            propsToStore.setProperty("a", "b");
        }

        /**
         * Uses Properties.store() APIs to store the properties into file
         */
        public static void main(final String[] args) throws Exception {
            final Path destFile = Path.of(args[0]);
            final String comment = "some user specified comment";
            System.out.println("Current default timezone is " + TimeZone.getDefault());
            if (args[1].equals("--use-outputstream")) {
                try (var os = Files.newOutputStream(destFile)) {
                    propsToStore.store(os, comment);
                }
            } else {
                try (var br = Files.newBufferedWriter(destFile)) {
                    propsToStore.store(br, comment);
                }
            }
        }
    }
}
