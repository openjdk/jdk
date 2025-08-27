/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Predicate;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static jdk.test.lib.Utils.*;

/**
 * @test
 * @bug 8330276 8351435 8361613
 * @summary Tests Console methods that have Locale as an argument
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.process.ProcessTools
 * @modules jdk.localedata
 * @run junit LocaleTest
 */
public class LocaleTest {
    private static final Calendar TODAY = new GregorianCalendar(2024, Calendar.APRIL, 22);
    private static final String FORMAT = "%1$tY-%1$tB-%1$te %1$tA";
    // We want to limit the expected strings within US-ASCII charset, as
    // the native encoding is determined as such, which is used by
    // the `Process` class under jtreg environment.
    private static final List<String> EXPECTED = List.of(
        String.format(Locale.UK, FORMAT, TODAY),
        String.format(Locale.FRANCE, FORMAT, TODAY),
        String.format(Locale.GERMANY, FORMAT, TODAY),
        String.format(Locale.of("es"), FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY)
    );

    @Test
    void testLocale() throws Exception {
        // check "expect" command availability
        var expect = Paths.get("/usr/bin/expect");
        Assumptions.assumeTrue(Files.exists(expect) && Files.isExecutable(expect),
            "'" + expect + "' not found. Test ignored.");

        // invoking "expect" command
        OutputAnalyzer oa = ProcessTools.executeProcess(
            "expect",
            "-n",
            TEST_SRC + "/locale.exp",
            TEST_CLASSES,
            TEST_JDK + "/bin/java",
            getClass().getName());

        var stdout =
            oa.stdoutAsLines().stream().filter(Predicate.not(String::isEmpty)).toList();
        var resultText =
            """
            Actual output: %s
            Expected output: %s
            """.formatted(stdout, EXPECTED);
        if (!stdout.equals(EXPECTED)) {
            throw new RuntimeException("Standard out had unexpected strings:\n" + resultText);
        } else {
            oa.shouldHaveExitValue(0);
            System.out.println("Formatting with explicit Locale succeeded.\n" + resultText);
        }
    }

    public static void main(String... args) throws Throwable {
        var con = System.console();
        if (con != null) {
            // tests these additional methods that take a Locale
            con.format(Locale.UK, FORMAT, TODAY);
            con.printf("\n");
            con.printf(Locale.FRANCE, FORMAT, TODAY);
            con.printf("\n");
            con.readLine(Locale.GERMANY, FORMAT, TODAY);
            con.printf("\n");
            con.readPassword(Locale.of("es"), FORMAT, TODAY);
            con.printf("\n");

            // tests null locale
            con.format((Locale)null, FORMAT, TODAY);
            con.printf("\n");
            con.printf((Locale)null, FORMAT, TODAY);
            con.printf("\n");
            con.readLine((Locale)null, FORMAT, TODAY);
            con.printf("\n");
            con.readPassword((Locale)null, FORMAT, TODAY);
        } else {
            // Exit with -1
            System.exit(-1);
        }
    }
}
