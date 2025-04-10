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

import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @bug 8330276 8351435
 * @summary Tests Console methods that have Locale as an argument
 * @library /test/lib
 * @modules jdk.internal.le jdk.localedata
 */
public class LocaleTest {
    private static Calendar TODAY  = new GregorianCalendar(2024, Calendar.APRIL, 22);
    private static String FORMAT = "%1$tY-%1$tB-%1$te %1$tA";
    // We want to limit the expected strings within US-ASCII charset, as
    // the native encoding is determined as such, which is used by
    // the `Process` class under jtreg environment.
    private static List<String> EXPECTED = List.of(
        String.format(Locale.UK, FORMAT, TODAY),
        String.format(Locale.FRANCE, FORMAT, TODAY),
        String.format(Locale.GERMANY, FORMAT, TODAY),
        String.format(Locale.of("es"), FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY),
        String.format((Locale)null, FORMAT, TODAY)
    );

    public static void main(String... args) throws Throwable {
        if (args.length == 0) {
            // no arg will launch the child process that actually perform tests
            var pb = ProcessTools.createTestJavaProcessBuilder(
                    "-Djdk.console=jdk.internal.le",
                    "LocaleTest", "dummy");
            var input = new File(System.getProperty("test.src", "."), "input.txt");
            pb.redirectInput(input);
            var oa = ProcessTools.executeProcess(pb);
            if (oa.getExitValue() == -1) {
                System.out.println("System.console() returns null. Ignoring the test.");
            } else {
                var output = oa.asLines();
                var resultText =
                    """
                    Actual output: %s
                    Expected output: %s
                    """.formatted(output, EXPECTED);
                if (!output.equals(EXPECTED)) {
                    throw new RuntimeException("Standard out had unexpected strings:\n" + resultText);
                } else {
                    oa.shouldHaveExitValue(0);
                    System.out.println("Formatting with explicit Locale succeeded.\n" + resultText);
                }
            }
        } else {
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
}
