/*
 * Copyright (c) 2022, Red Hat, Inc.
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

import jdk.test.lib.util.JarUtils;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.util.Calendar;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import static java.util.Calendar.WEDNESDAY;

/*
 * @test
 * @bug 8297684 8269039
 * @summary Checking custom CalendarDataProvider with SPI contained in signed jar does
 *          not produce NPE.
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.timestamp
 *          java.base/sun.security.x509
 *          java.base/sun.security.util
 *          java.base/sun.security.tools.keytool
 *          jdk.jartool/jdk.security.jarsigner
 * @library /test/lib
 * @library provider
 * @build baz.CalendarDataProviderImpl
 * @run main/timeout=600 TestSPISigned
 */
public class TestSPISigned {

    private static final String TEST_CLASSES = System.getProperty("test.classes", ".");
    private static final String TEST_SRC = System.getProperty("test.src", ".");

    private static final Path META_INF_DIR = Paths.get(TEST_SRC, "provider", "meta");
    private static final Path PROVIDER_PARENT = Paths.get(TEST_CLASSES, "..");
    private static final Path PROVIDER_DIR = PROVIDER_PARENT.resolve("provider");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path UNSIGNED_JAR = MODS_DIR.resolve("unsigned-with-locale.jar");
    private static final Path SIGNED_JAR = MODS_DIR.resolve("signed-with-locale.jar");

    public static void main(String[] args) throws Throwable {
        if (args.length == 1) {
            String arg = args[0];
            if ("run-test".equals(arg)) {
                System.out.println("Debug: Running test");
                String provProp = System.getProperty("java.locale.providers");
                if (!"SPI".equals(provProp)) {
                   throw new RuntimeException("Test failed! Expected -Djava.locale.providers=SPI to be set for test run");
                }
                doRunTest();
            } else {
               throw new RuntimeException("Test failed! Expected 'run-test' arg for test run");
            }
        } else {
            // Set up signed jar with custom calendar data provider
            //
            // 1. Create jar with custom CalendarDataProvider
            JarUtils.createJarFile(UNSIGNED_JAR, PROVIDER_DIR);
            JarUtils.updateJarFile(UNSIGNED_JAR, META_INF_DIR);
            // create signer's keypair
            SecurityTools.keytool("-genkeypair -keyalg RSA -keystore ks " +
                                  "-storepass changeit -dname CN=test -alias test")
                     .shouldHaveExitValue(0);
            // sign jar
            SecurityTools.jarsigner("-keystore ks -storepass changeit " +
                                "-signedjar " + SIGNED_JAR + " " + UNSIGNED_JAR + " test")
                     .shouldHaveExitValue(0);
            // run test, which must not throw a NPE
            List<String> testRun = new ArrayList<>();
            testRun.add("-Djava.locale.providers=SPI");
            testRun.add("-cp");
            String classPath = System.getProperty("java.class.path");
            classPath = classPath + File.pathSeparator + SIGNED_JAR.toAbsolutePath().toString();
            testRun.add(classPath);
            testRun.add(TestSPISigned.class.getSimpleName());
            testRun.add("run-test");
            OutputAnalyzer out = ProcessTools.executeTestJava(testRun);
            out.shouldHaveExitValue(0);
            out.shouldContain("DEBUG: Getting xx language");
        }
    }

    private static void doRunTest() {
        Locale locale = new Locale("xx", "YY");
        Calendar kcal = Calendar.getInstance(locale);
        try {
            check(WEDNESDAY, kcal.getFirstDayOfWeek());
            check(7, kcal.getMinimalDaysInFirstWeek());
        } catch (Throwable ex) {
            throw new RuntimeException("Test failed with signed jar and " +
                    " argument java.locale.providers=SPI", ex);
        }
    }

    private static <T> void check(T expected, T actual) {
        Asserts.assertEquals(expected, actual, "Expected calendar from SPI to be in effect");
    }

}
