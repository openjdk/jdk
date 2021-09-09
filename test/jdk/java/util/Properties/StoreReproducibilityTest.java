/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
 * @bug 8231640
 * @library /test/lib
 * @run driver StoreReproducibilityTest
 */
public class StoreReproducibilityTest {

    private static final String ENV_SOURCE_DATE_EPOCH = "SOURCE_DATE_EPOCH";
    private static final String dateCommentFormat = "EEE MMM dd HH:mm:ss zzz yyyy";

    public static void main(final String[] args) throws Exception {
        // no security manager enabled
        testWithoutSecurityManager();
        // security manager enabled and security policy explicitly allows read permissions on getenv.SOURCE_DATE_EPOCH
        testWithSecMgrExplicitPermission();
        // security manager enabled and no explicit getenv.SOURCE_DATE_EPOCH permission
        testWithSecMgrNoSpecificPermission();
        // invalid/unparsable value for SOURCE_DATE_EPOCH
        testInvalidSourceDateEpochValue();
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed an environment variable value for
     * {@code SOURCE_DATE_EPOCH} environment variable and the date comment written out to the file
     * is expected to use this value.
     * The program is launched multiple times with the same value for {@code SOURCE_DATE_EPOCH}
     * and the output written out by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code SOURCE_DATE_EPOCH}.
     * The launched Java program is run without any security manager
     */
    private static void testWithoutSecurityManager() throws Exception {
        final List<Path> storedFiles = new ArrayList<>();
        final String sourceDateEpoch = "243535322";
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            processBuilder.environment().put(ENV_SOURCE_DATE_EPOCH, sourceDateEpoch);
            executeJavaProcess(processBuilder);
            assertExpectedSourceEpochDate(tmpFile, sourceDateEpoch);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sourceDateEpoch);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed an environment variable value for
     * {@code SOURCE_DATE_EPOCH} environment variable and the date comment written out to the file
     * is expected to use this value.
     * The launched Java program is run with the default security manager and is granted
     * a {@code read} permission on {@code getenv.SOURCE_DATE_EPOCH}.
     * The program is launched multiple times with the same value for {@code SOURCE_DATE_EPOCH}
     * and the output written out by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code SOURCE_DATE_EPOCH}.
     */
    private static void testWithSecMgrExplicitPermission() throws Exception {
        final Path policyFile = Files.createTempFile("8231640", ".policy");
        Files.write(policyFile, Collections.singleton("""
                grant {
                    // test writes/stores to a file, so FilePermission
                    permission java.io.FilePermission "<<ALL FILES>>", "read,write";
                    // explicitly grant read on SOURCE_DATE_EPOCH to verifies store() APIs work fine
                    permission java.lang.RuntimePermission "getenv.SOURCE_DATE_EPOCH", "read";
                };
                """));
        final List<Path> storedFiles = new ArrayList<>();
        final String sourceDateEpoch = "1234342423";
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    "-Djava.security.manager",
                    "-Djava.security.policy=" + policyFile.toString(),
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            processBuilder.environment().put(ENV_SOURCE_DATE_EPOCH, sourceDateEpoch);
            executeJavaProcess(processBuilder);
            assertExpectedSourceEpochDate(tmpFile, sourceDateEpoch);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sourceDateEpoch);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed an environment variable value for
     * {@code SOURCE_DATE_EPOCH} environment variable and the date comment written out to the file
     * is expected to use this value.
     * The launched Java program is run with the default security manager and is NOT granted
     * any explicit permission for {@code getenv.SOURCE_DATE_EPOCH}.
     * The program is launched multiple times with the same value for {@code SOURCE_DATE_EPOCH}
     * and the output written out by each run of this program is verified to be exactly the same.
     * Additionally, the date comment that's written out is verified to be the expected date that
     * corresponds to the passed {@code SOURCE_DATE_EPOCH}.
     */
    private static void testWithSecMgrNoSpecificPermission() throws Exception {
        final Path policyFile = Files.createTempFile("8231640", ".policy");
        Files.write(policyFile, Collections.singleton("""
                grant {
                    // test writes/stores to a file, so FilePermission
                    permission java.io.FilePermission "<<ALL FILES>>", "read,write";
                    // no other grants, not even "read" on SOURCE_DATE_EPOCH. test should still
                    // work fine and the date comment should correspond to the value of SOURCE_DATE_EPOCH
                };
                """));
        final List<Path> storedFiles = new ArrayList<>();
        final String sourceDateEpoch = "1234342423";
        for (int i = 0; i < 5; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            storedFiles.add(tmpFile);
            final ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    "-Djava.security.manager",
                    "-Djava.security.policy=" + policyFile.toString(),
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            processBuilder.environment().put(ENV_SOURCE_DATE_EPOCH, sourceDateEpoch);
            executeJavaProcess(processBuilder);
            assertExpectedSourceEpochDate(tmpFile, sourceDateEpoch);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
        }
        assertAllFileContentsAreSame(storedFiles, sourceDateEpoch);
    }

    /**
     * Launches a Java program which is responsible for using Properties.store() to write out the
     * properties to a file. The launched Java program is passed an invalid value for
     * the {@code SOURCE_DATE_EPOCH} environment variable.
     * It is expected and verified in this test that such an invalid value for the environment variable
     * will cause the date comment to be the "current date". The launched program is expected to complete
     * without any errors.
     */
    private static void testInvalidSourceDateEpochValue() throws Exception {
        final String sourceDateEpoch = "foo-bar";
        for (int i = 0; i < 2; i++) {
            final Path tmpFile = Files.createTempFile("8231640", ".props");
            final ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    StoreTest.class.getName(),
                    tmpFile.toString(),
                    i % 2 == 0 ? "--use-outputstream" : "--use-writer");
            processBuilder.environment().put(ENV_SOURCE_DATE_EPOCH, sourceDateEpoch);
            final Date processLaunchedAt = new Date();
            // launch with a second delay so that we can then verify that the date comment
            // written out by the program is "after" this date
            Thread.sleep(1000);
            executeJavaProcess(processBuilder);
            if (!StoreTest.propsToStore.equals(loadProperties(tmpFile))) {
                throw new RuntimeException("Unexpected properties stored in " + tmpFile);
            }
            assertCurrentDate(tmpFile, processLaunchedAt);
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
     * Verifies that the date comment in the {@code destFile} is of the expected GMT format
     * and the time represented by it corresponds to the passed {@code sourceEpochDate}
     */
    private static void assertExpectedSourceEpochDate(final Path destFile,
                                                      final String sourceEpochDate) throws Exception {
        final String dateComment = findNthComment(destFile, 2);
        if (dateComment == null) {
            throw new RuntimeException("Date comment not found in stored properties " + destFile
                    + " when " + ENV_SOURCE_DATE_EPOCH + " was set " +
                    "(to " + sourceEpochDate + ")");
        }
        System.out.println("Found date comment " + dateComment + " in file " + destFile);
        long parsedSecondsSinceEpoch;
        try {
            var d = DateTimeFormatter.ofPattern(dateCommentFormat)
                    .withLocale(Locale.ROOT)
                    .withZone(ZoneOffset.UTC).parse(dateComment);
            parsedSecondsSinceEpoch = Duration.between(Instant.ofEpochSecond(0), Instant.from(d)).toSeconds();
        } catch (DateTimeParseException pe) {
            throw new RuntimeException("Unexpected date " + dateComment + " in stored properties " + destFile
                    + " when " + ENV_SOURCE_DATE_EPOCH + " was set " +
                    "(to " + sourceEpochDate + ")", pe);

        }
        final long expected = Long.parseLong(sourceEpochDate);
        if (parsedSecondsSinceEpoch != expected) {
            throw new RuntimeException("Expected " + expected + " seconds since epoch but found "
                    + parsedSecondsSinceEpoch);
        }
    }

    /**
     * Verifies that the date comment in the {@code destFile} can be parsed and the time
     * represented by it is {@link Date#after(Date)} the passed {@code date}
     */
    private static void assertCurrentDate(final Path destFile, final Date date) throws Exception {
        final String dateComment = findNthComment(destFile, 2);
        if (dateComment == null) {
            throw new RuntimeException("Date comment not found in stored properties " + destFile);
        }
        System.out.println("Found date comment " + dateComment + " in file " + destFile);
        final Date parsedDate;
        try {
            parsedDate = new SimpleDateFormat(dateCommentFormat).parse(dateComment);
        } catch (ParseException pe) {
            throw new RuntimeException("Unexpected date " + dateComment + " in stored properties " + destFile);
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
            String line = null;
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
                                                     final String sourceDateEpoch) throws Exception {
        final byte[] file1Contents = Files.readAllBytes(files.get(0));
        for (int i = 1; i < files.size(); i++) {
            final byte[] otherFileContents = Files.readAllBytes(files.get(i));
            if (!Arrays.equals(file1Contents, otherFileContents)) {
                throw new RuntimeException("Properties.store() did not generate reproducible content when "
                        + ENV_SOURCE_DATE_EPOCH + " was set (to " + sourceDateEpoch + ")");
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
