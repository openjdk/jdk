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

/*
 * @test
 * @bug 8276766
 * @summary Test jar --date source date of entries and that jars are
 *          reproducible
 * @modules jdk.jartool
 * @run testng/othervm ReproducibleJar
 */

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.spi.ToolProvider;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ReproducibleJar {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() ->
                    new RuntimeException("jar tool not found")
            );

    // ZipEntry's mod date has 2 seconds precision: give extra time to
    // allow for e.g. rounding/truncation and networked/samba drives.
    private static final long PRECISION = 10000L;

    private static final TimeZone TZ = TimeZone.getDefault();
    private static final boolean DST = TZ.inDaylightTime(new Date());
    private static final String UNIX_2038_ROLLOVER_TIME = "2038-01-19T03:14:07Z";
    private static final Instant UNIX_2038_ROLLOVER = Instant.parse(UNIX_2038_ROLLOVER_TIME);
    private static final File DIR_OUTER = new File("outer");
    private static final File DIR_INNER = new File(DIR_OUTER, "inner");
    private static final File FILE_INNER = new File(DIR_INNER, "foo.txt");
    private static final File JAR_FILE_SOURCE_DATE1 = new File("JarEntryTimeSourceDate1.jar");
    private static final File JAR_FILE_SOURCE_DATE2 = new File("JarEntryTimeSourceDate2.jar");

    // Valid --date values for jar
    @DataProvider
    private Object[][] validSourceDates() {
        return new Object[][]{
                {"1980-01-01T00:00:02+00:00"},
                {"1986-06-24T01:02:03+00:00"},
                {"2022-03-15T00:00:00+00:00"},
                {"2022-03-15T00:00:00+06:00"},
                {"2021-12-25T09:30:00-08:00[America/Los_Angeles]"},
                {"2021-12-31T23:59:59Z"},
                {"2024-06-08T14:24Z"},
                {"2026-09-24T16:26-05:00"},
                {"2038-11-26T06:06:06+00:00"},
                {"2098-02-18T00:00:00-08:00"},
                {"2099-12-31T23:59:59+00:00"}
        };
    }

    // Invalid --date values for jar
    @DataProvider
    private Object[][] invalidSourceDates() {
        return new Object[][]{
                {"1976-06-24T01:02:03+00:00"},
                {"1980-01-01T00:00:01+00:00"},
                {"2100-01-01T00:00:00+00:00"},
                {"2138-02-18T00:00:00-11:00"},
                {"2006-04-06T12:38:00"},
                {"2012-08-24T16"}
        };
    }

    @BeforeMethod
    public void runBefore() throws IOException {
        runAfter();
        createOuterInnerDirs();
    }

    @AfterMethod
    public void runAfter() {
        cleanup(DIR_INNER);
        cleanup(DIR_OUTER);
        JAR_FILE_SOURCE_DATE1.delete();
        JAR_FILE_SOURCE_DATE2.delete();
        TimeZone.setDefault(TZ);
    }

    /**
     * Test jar tool with various valid --date <timestamps>
     */
    @Test(dataProvider = "validSourceDates")
    public void testValidSourceDate(String sourceDate) {
        if (isInTransition()) return;

        // Test --date source date
        Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                "--create",
                "--file", JAR_FILE_SOURCE_DATE1.getName(),
                "--date", sourceDate,
                DIR_OUTER.getName()), 0);
        Assert.assertTrue(JAR_FILE_SOURCE_DATE1.exists());

        // Extract JAR_FILE_SOURCE_DATE1 and check last modified values
        Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                "--extract",
                "--file", JAR_FILE_SOURCE_DATE1.getName()), 0);
        Assert.assertTrue(DIR_OUTER.exists());
        Assert.assertTrue(DIR_INNER.exists());
        Assert.assertTrue(FILE_INNER.exists());
        LocalDateTime expectedLdt = ZonedDateTime.parse(sourceDate,
                        DateTimeFormatter.ISO_DATE_TIME)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        System.out.format("Checking jar entries local date time for --date %s, is %s%n",
                sourceDate, expectedLdt);
        long sourceDateEpochMillis = TimeUnit.MILLISECONDS.convert(
                expectedLdt.toEpochSecond(ZoneId.systemDefault().getRules()
                        .getOffset(expectedLdt)), TimeUnit.SECONDS);
        checkFileTime(DIR_OUTER.lastModified(), sourceDateEpochMillis);
        checkFileTime(DIR_INNER.lastModified(), sourceDateEpochMillis);
        checkFileTime(FILE_INNER.lastModified(), sourceDateEpochMillis);
    }

    /**
     * Test jar tool with various invalid --date <timestamps>
     */
    @Test(dataProvider = "invalidSourceDates")
    public void testInvalidSourceDate(String sourceDate) {
        // Negative Tests --date out of range or wrong format source date
        Assert.assertNotEquals(JAR_TOOL.run(System.out, System.err,
                "--create",
                "--file", JAR_FILE_SOURCE_DATE1.getName(),
                "--date", sourceDate,
                DIR_OUTER.getName()), 0);
    }

    /**
     * Test jar produces deterministic reproducible output
     */
    @Test(dataProvider = "validSourceDates")
    public void testJarsReproducible(String sourceDate) throws IOException {
        // Test jars are reproducible across timezones
        TimeZone tzAsia = TimeZone.getTimeZone("Asia/Shanghai");
        TimeZone tzLA = TimeZone.getTimeZone("America/Los_Angeles");
        TimeZone.setDefault(tzAsia);
        Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                "--create",
                "--file", JAR_FILE_SOURCE_DATE1.getName(),
                "--date", sourceDate,
                DIR_OUTER.getName()), 0);
        Assert.assertTrue(JAR_FILE_SOURCE_DATE1.exists());

        try {
            // Sleep 5 seconds to ensure jar timestamps might be different if they could be
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
        }

        TimeZone.setDefault(tzLA);
        Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                "--create",
                "--file", JAR_FILE_SOURCE_DATE2.getName(),
                "--date", sourceDate,
                DIR_OUTER.getName()), 0);
        Assert.assertTrue(JAR_FILE_SOURCE_DATE2.exists());

        // Check jars are identical
        Assert.assertEquals(Files.readAllBytes(JAR_FILE_SOURCE_DATE1.toPath()),
                Files.readAllBytes(JAR_FILE_SOURCE_DATE2.toPath()));
    }

    /**
     * Create the standard directory structure used by the test:
     * outer/
     * inner/
     * foo.txt
     */
    static void createOuterInnerDirs() throws IOException {
        Assert.assertTrue(DIR_OUTER.mkdir());
        Assert.assertTrue(DIR_INNER.mkdir());
        try (PrintWriter pw = new PrintWriter(FILE_INNER)) {
            pw.println("hello, world");
        }
        Assert.assertTrue(DIR_OUTER.exists());
        Assert.assertTrue(DIR_INNER.exists());
        Assert.assertTrue(FILE_INNER.exists());
    }

    /**
     * Check the extracted and original millis since Epoch file times are
     * within the zip precision time period.
     */
    static void checkFileTime(long now, long original) {
        if (isTimeSettingChanged()) {
            return;
        }

        if (Math.abs(now - original) > PRECISION) {
            // If original time is after UNIX 2038 32bit rollover
            // and the now time is exactly the rollover time, then assume
            // running on a file system that only supports to 2038 (e.g.XFS) and pass test
            if (FileTime.fromMillis(original).toInstant().isAfter(UNIX_2038_ROLLOVER) &&
                    FileTime.fromMillis(now).toInstant().equals(UNIX_2038_ROLLOVER)) {
                System.out.println("Checking file time after Unix 2038 rollover," +
                        " and extracted file time is " + UNIX_2038_ROLLOVER_TIME + ", " +
                        " Assuming restricted file system, pass file time check.");
            } else {
                throw new AssertionError("checkFileTime failed," +
                        " extracted to " + FileTime.fromMillis(now) +
                        ", expected to be close to " + FileTime.fromMillis(original));
            }
        }
    }

    /**
     * Has the timezone or DST changed during the test?
     */
    private static boolean isTimeSettingChanged() {
        TimeZone currentTZ = TimeZone.getDefault();
        boolean currentDST = currentTZ.inDaylightTime(new Date());
        if (!currentTZ.equals(TZ) || currentDST != DST) {
            System.out.println("Timezone or DST has changed during " +
                    "ReproducibleJar testcase execution. Test skipped");
            return true;
        } else {
            return false;
        }
    }

    /**
     * Is the Zone currently within the transition change period?
     */
    private static boolean isInTransition() {
        var inTransition = false;
        var date = new Date();
        var defZone = ZoneId.systemDefault();
        if (defZone.getRules().getTransition(
                date.toInstant().atZone(defZone).toLocalDateTime()) != null) {
            System.out.println("ReproducibleJar testcase being run during Zone offset transition.  Test skipped.");
            inTransition = true;
        }
        return inTransition;
    }

    /**
     * Remove the directory and its contents
     */
    static void cleanup(File dir) {
        File[] x = dir.listFiles();
        if (x != null) {
            for (File f : x) {
                f.delete();
            }
        }
        dir.delete();
    }
}
