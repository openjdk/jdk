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

/**
 * @test
 * @bug 8044609
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary javax.net.debug "ssl" options are not working and documented as expected.
 * @run junit DebugPropertyValues
 */

import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class DebugPropertyValues extends SSLSocketTemplate {

    static Path LOG_FILE;

    @BeforeAll
    static void setup() throws Exception {
        LOG_FILE = Path.of(System.getProperty("test.classes"), "logging.conf");
        Files.writeString(LOG_FILE, ".level = ALL\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
    }

    private static Stream<Arguments> patternMatches() {
        // "Plaintext before ENCRYPTION" comes from "ssl:record:plaintext" option
        // "matching alias:" comes from ssl:keymanager option
        // "trigger seeding of SecureRandom" comes from ssl:sslctx option
        // "jdk.tls.keyLimits:" comes from the plain "ssl" option
        return Stream.of(
                // all should print everything
                Arguments.of("all",
                        List.of("Plaintext before ENCRYPTION",
                                "trigger seeding of SecureRandom",
                                "adding as trusted certificates",
                                "supported_versions"),
                        null),
                // ssl should print most details expect verbose details
                Arguments.of("ssl",
                        List.of("adding as trusted certificates",
                                "trigger seeding of SecureRandom",
                                "supported_versions"),
                        List.of("Plaintext before ENCRYPTION")),
                // ssl:plaintext isn't valid. "plaintext" is sub-option for "record"
                Arguments.of("ssl:plaintext",
                        null,
                        List.of("Plaintext before ENCRYPTION",
                                "jdk.tls.keyLimits:",
                                "trigger seeding of SecureRandom",
                                "length =")),
                Arguments.of("ssl:record:plaintext",
                        List.of("Plaintext before ENCRYPTION",
                                "length ="),
                        List.of("matching alias:")),
                Arguments.of("ssl:trustmanager",
                        List.of("adding as trusted certificates"),
                        List.of("Plaintext before ENCRYPTION",
                                "length =")),
                Arguments.of("ssl:sslctx",
                        List.of("trigger seeding of SecureRandom"),
                        List.of("Plaintext before ENCRYPTION",
                                "length =")),
                // ssltypo contains "ssl" but it's an invalid option
                Arguments.of("ssltypo",
                        null,
                        List.of("Plaintext before ENCRYPTION",
                                "adding as trusted certificates",
                                "length =")),
                Arguments.of("", // empty invokes System.Logger use
                        List.of("FINE: adding as trusted certificates",
                        "FINE: WRITE: TLSv1.3 application_data",
                        "supported_versions"),
                        null)
                );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void checkDebugOutput(String params, List<String> expected,
                                 List<String> notExpected) throws Exception {
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(
                "-Djava.util.logging.config.file=" + LOG_FILE,
                "-Djavax.net.debug=" + params,
                "DebugPropertyValues"
        );
        outputAnalyzer.shouldHaveExitValue(0);
        if (expected != null) {
            for (String s : expected) {
                outputAnalyzer.shouldMatch(s);
            }
        }
        if (notExpected != null) {
            for (String s : notExpected) {
                outputAnalyzer.shouldNotMatch(s);
            }
        } else {
            outputAnalyzer.stderrShouldNotBeEmpty();
        }
    }

    public static void main(String[] args) throws Exception {
        new DebugPropertyValues().run();
    }
}
