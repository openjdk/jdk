/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4772857
 * @summary Unit test for Charset.defaultCharset
 * @requires os.family == "linux"
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.Asserts
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.JDKToolLauncher
 *        jdk.test.lib.Platform
 *        jdk.test.lib.process.*
 *        Default
 * @run junit DefaultCharsetTest
 */

import java.util.Map;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultCharsetTest {

    private static final ProcessBuilder pb
            = ProcessTools.createTestJavaProcessBuilder(Default.class.getName());
    private static final Map<String, String> env = pb.environment();
    private static String UNSUPPORTED = null;

    @BeforeAll
    public static void checkSupports() throws Exception {
        UNSUPPORTED = runWithLocale("nonexist");
    }

    public static Stream<Arguments> locales() {
        return Stream.of(
                Arguments.of("en_US", "iso-8859-1"),
                Arguments.of("ja_JP.utf8", "utf-8"),
                Arguments.of("tr_TR", "iso-8859-9"),
                Arguments.of("C", "us-ascii"),
                Arguments.of("ja_JP", "x-euc-jp-linux"),
                Arguments.of("ja_JP.eucjp", "x-euc-jp-linux"),
                Arguments.of("ja_JP.ujis", "x-euc-jp-linux"),
                Arguments.of("ja_JP.utf8", "utf-8")
        );
    }

    @ParameterizedTest
    @MethodSource("locales")
    public void testDefaultCharset(String locale, String expectedCharset) throws Exception {
        String actual = runWithLocale(locale);
        if (UNSUPPORTED.equals(actual)) {
            System.out.println(locale + ": Locale not supported, skipping...");
        } else {
            assertTrue(actual.equalsIgnoreCase(expectedCharset),
                       String.format("LC_ALL = %s, got defaultCharset = %s, "
                               + "NOT as expected %s",
                               locale, actual, expectedCharset));
        }
    }

    private static String runWithLocale(String locale) throws Exception {
        env.remove("LC_ALL");
        env.put("LC_ALL", locale);
        return ProcessTools.executeProcess(pb)
                           .shouldHaveExitValue(0)
                           .getStdout()
                           .replace(System.lineSeparator(), "");
    }
}
