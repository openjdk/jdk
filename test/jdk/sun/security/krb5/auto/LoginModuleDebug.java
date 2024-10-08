/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8327818 8051959
 * @summary reimplement debug option in Krb5LoginModule
 * @library /test/lib
 * @run junit LoginModuleDebug
 */
import com.sun.security.auth.module.Krb5LoginModule;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;
import javax.security.auth.Subject;

public class LoginModuleDebug {
    static final String DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}";

    private static Stream<Arguments> patternMatches() {
        return Stream.of(
                // debug option set to true - no extra info
                Arguments.of("debug",
                        "true",
                        "krb5loginmodule:",
                        "krb5loginmodule\\["),
                // debug option set to false
                Arguments.of("debug",
                        "false",
                        "",
                        "krb5loginmodule"),
                // no debug option
                Arguments.of("foo",
                        "bar",
                        "",
                        "krb5loginmodule"),
                // thread info only
                Arguments.of("debug",
                        "true+thread",
                        "krb5loginmodule\\[.*\\|main|\\.*java.*]:",
                        "\\|" + DATE_REGEX + ".*\\]:"),
                // timestamp info only
                Arguments.of("debug",
                        "true+timestamp",
                        "krb5loginmodule\\[" + DATE_REGEX + ".*\\]",
                        "\\|main\\]:"),
                // both thread and timestamp
                Arguments.of("debug",
                        "true+timestamp+thread",
                        "krb5loginmodule\\[.*\\|main|" + DATE_REGEX + ".*\\]:",
                        "krb5loginmodule:")
        );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void shouldContain(String value, String key, String expected, String notExpected) throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "LoginModuleDebug",
                value, key);
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldMatch(expected)
                .shouldNotMatch(notExpected);
        // let's also run with java debug property enabled
        outputAnalyzer = ProcessTools.executeTestJava(
                "-Djava.security.debug=all",
                "LoginModuleDebug",
                value, key);
        outputAnalyzer.shouldHaveExitValue(0)
                .shouldMatch(expected)
                .shouldNotMatch(notExpected);
    }

    public static void main(String[] args) throws Exception {
        System.err.println(args.length);
        if (args.length == 2) {
            // something to trigger "krb5" debug output
            new Krb5LoginModule().initialize(
                    new Subject(), null, Map.of(), Map.of(args[0], args[1]));
        }
    }
}