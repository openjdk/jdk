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
 * @run testng ReproducibleJar
 */

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.time.ZoneId;
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
    private static final String unix2038RolloverTime = "2038-01-19T03:14:07Z";
    private static final Instant unix2038Rollover = Instant.parse(unix2038RolloverTime);
    private static final File dirOuter = new File("outer");
    private static final File dirInner = new File(dirOuter, "inner");
    private static final File fileInner = new File(dirInner, "foo.txt");
    private static final File jarFileSourceDate1 = new File("JarEntryTimeSourceDate1.jar");
    private static final File jarFileSourceDate2 = new File("JarEntryTimeSourceDate2.jar");

    private static final String[] sourceDates =
                               {"1980-01-01T00:00:02+00:00",
                                "1986-06-24T01:02:03+00:00",
                                "2022-03-15T00:00:00+00:00",
                                "2022-03-15T00:00:00+06:00",
                                "2021-12-25T09:30:00-08:00[America/Los_Angeles]",
                                "2021-12-31T23:59:59Z",
                                "2024-06-08T14:24Z",
                                "2026-09-24T16:26-05:00",
                                "2038-11-26T06:06:06+00:00",
                                "2098-02-18T00:00:00-08:00",
                                "2099-12-31T23:59:59+00:00"};

    private static final String[] badSourceDates =
                                {"1976-06-24T01:02:03+00:00",
                                 "1980-01-01T00:00:01+00:00",
                                 "2100-01-01T00:00:00+00:00",
                                 "2138-02-18T00:00:00-11:00",
                                 "2006-04-06T12:38:00",
                                 "2012-08-24T16"};

    @BeforeMethod
    public void runBefore() throws Throwable {
        cleanup(dirInner);
        cleanup(dirOuter);
        jarFileSourceDate1.delete();
        jarFileSourceDate2.delete();
        TimeZone.setDefault(TZ);
    }

    @AfterMethod
    public void runAfter() throws Throwable {
        cleanup(dirInner);
        cleanup(dirOuter);
        jarFileSourceDate1.delete();
        jarFileSourceDate2.delete();
        TimeZone.setDefault(TZ);
    }

    @Test
    public void testSourceDate() throws Throwable {
        if (isInTransition()) return;

        // Test --date source date
        for (String sourceDate : sourceDates) {
            createOuterInnerDirs(dirOuter, dirInner);
            Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                           "--create",
                           "--file", jarFileSourceDate1.getName(),
                           "--date", sourceDate,
                           dirOuter.getName()), 0);
            Assert.assertTrue(jarFileSourceDate1.exists());

            // Extract jarFileSourceDate1 and check last modified values
            extractJar(jarFileSourceDate1);
            Assert.assertTrue(dirOuter.exists());
            Assert.assertTrue(dirInner.exists());
            Assert.assertTrue(fileInner.exists());
            LocalDateTime expectedLdt = ZonedDateTime.parse(sourceDate,
                                             DateTimeFormatter.ISO_DATE_TIME)
                                             .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            System.out.format("Checking jar entries local date time for --date %s, is %s%n",
                              sourceDate, expectedLdt);
            long sourceDateEpochMillis = TimeUnit.MILLISECONDS.convert(
                expectedLdt.toEpochSecond(ZoneId.systemDefault().getRules().getOffset(expectedLdt)),
                TimeUnit.SECONDS);
            checkFileTime(dirOuter.lastModified(), sourceDateEpochMillis);
            checkFileTime(dirInner.lastModified(), sourceDateEpochMillis);
            checkFileTime(fileInner.lastModified(), sourceDateEpochMillis);

            cleanup(dirInner);
            cleanup(dirOuter);
            jarFileSourceDate1.delete();
        }
    }

    @Test
    public void testInvalidSourceDate() throws Throwable {
        // Negative Tests --date out of range or wrong format source date
        createOuterInnerDirs(dirOuter, dirInner);
        for (String sourceDate : badSourceDates) {
            Assert.assertNotEquals(JAR_TOOL.run(System.out, System.err,
                           "--create",
                           "--file", jarFileSourceDate1.getName(),
                           "--date", sourceDate,
                           dirOuter.getName()), 0);
        }
    }

    @Test
    public void testJarsReproducible() throws Throwable {
        // Test jars are reproducible across timezones
        TimeZone tzAsia = TimeZone.getTimeZone("Asia/Shanghai");
        TimeZone tzLA   = TimeZone.getTimeZone("America/Los_Angeles");
        for (String sourceDate : sourceDates) {
            createOuterInnerDirs(dirOuter, dirInner);
            TimeZone.setDefault(tzAsia);
            Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                           "--create",
                           "--file", jarFileSourceDate1.getName(),
                           "--date", sourceDate,
                           dirOuter.getName()), 0);
            Assert.assertTrue(jarFileSourceDate1.exists());

            try {
                // Sleep 5 seconds to ensure jar timestamps might be different if they could be
                Thread.sleep(5000);
            } catch(InterruptedException ex) {}

            TimeZone.setDefault(tzLA);
            Assert.assertEquals(JAR_TOOL.run(System.out, System.err,
                           "--create",
                           "--file", jarFileSourceDate2.getName(),
                           "--date", sourceDate,
                           dirOuter.getName()), 0);
            Assert.assertTrue(jarFileSourceDate2.exists());

            // Check jars are identical
            checkSameContent(jarFileSourceDate1, jarFileSourceDate2);

            cleanup(dirInner);
            cleanup(dirOuter);
            jarFileSourceDate1.delete();
            jarFileSourceDate2.delete();
        }
    }

    static void createOuterInnerDirs(File dirOuter, File dirInner) throws Throwable {
        /* Create a directory structure
         * outer/
         *     inner/
         *         foo.txt
         */
        Assert.assertTrue(dirOuter.mkdir());
        Assert.assertTrue(dirInner.mkdir());
        try (PrintWriter pw = new PrintWriter(fileInner)) {
            pw.println("hello, world");
        }

        Assert.assertTrue(dirOuter.exists());
        Assert.assertTrue(dirInner.exists());
        Assert.assertTrue(fileInner.exists());
    }

    static void checkFileTime(long now, long original) throws Throwable {
        if (isTimeSettingChanged()) {
            return;
        }

        if (Math.abs(now - original) > PRECISION) {
            // If original time is after UNIX 2038 32bit rollover
            // and the now time is exactly the rollover time, then assume
            // running on a file system that only supports to 2038 (e.g.XFS) and pass test
            if (FileTime.fromMillis(original).toInstant().isAfter(unix2038Rollover) &&
                FileTime.fromMillis(now).toInstant().equals(unix2038Rollover)) {
                System.out.println("Checking file time after Unix 2038 rollover," +
                                   " and extracted file time is " + unix2038RolloverTime + ", " +
                                   " Assuming restricted file system, pass file time check.");
            } else {
                throw new AssertionError("checkFileTime failed," +
                                         " extracted to " +  FileTime.fromMillis(now) +
                                         ", expected to be close to " + FileTime.fromMillis(original));
            }
        }
    }

    static void checkSameContent(File f1, File f2) throws Throwable {
        byte[] ba1 = Files.readAllBytes(f1.toPath());
        byte[] ba2 = Files.readAllBytes(f2.toPath());
        if (!Arrays.equals(ba1, ba2)) {
            throw new AssertionError("jar content differs:" + f1 + " != " + f2);
        }
    }

    private static boolean isTimeSettingChanged() {
        TimeZone currentTZ = TimeZone.getDefault();
        boolean currentDST = currentTZ.inDaylightTime(new Date());
        if (!currentTZ.equals(TZ) || currentDST != DST) {
            System.out.println("Timezone or DST has changed during ReproducibleJar testcase execution. Test skipped");
            return true;
        } else {
            return false;
        }
    }

    private static boolean isInTransition() {
        boolean inTransition = false;

        var date = new Date();
        var defZone = ZoneId.systemDefault();
        if (defZone.getRules().getTransition(
                date.toInstant().atZone(defZone).toLocalDateTime()) != null) {
            System.out.println("ReproducibleJar testcase being run during Zone offset transition.  Test skipped.");
            inTransition = true;
        }

        return inTransition;
    }

    static boolean cleanup(File dir) throws Throwable {
        boolean rc = true;
        File[] x = dir.listFiles();
        if (x != null) {
            for (int i = 0; i < x.length; i++) {
                rc &= x[i].delete();
            }
        }
        return rc & dir.delete();
    }

    static void extractJar(File jarFile) throws Throwable {
        String javahome = System.getProperty("java.home");
        String jarcmd = javahome + File.separator + "bin" + File.separator + "jar";
        String[] args;
        args = new String[] {
                jarcmd,
                "xf",
                jarFile.getName() };
        Process p = Runtime.getRuntime().exec(args);
        Assert.assertTrue(p != null && (p.waitFor() == 0));
    }
}
