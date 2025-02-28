/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8350582
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary javax.net.debug "ssl" option parsed incorrectly
 * @run junit DebugPropertyValuesTest
 */

import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class DebugPropertyValuesTest extends SSLSocketTemplate {

    private static final Path LOG_FILE = Path.of("logging.conf");

    @BeforeAll
    static void setup() throws Exception {
        Files.writeString(LOG_FILE, ".level = ALL\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
    }

    private static Stream<Arguments> patternMatches() {
        // "Plaintext before ENCRYPTION" comes from "ssl:record:plaintext" option
        // "handshake, length =" comes from "ssl:record" option
        // "matching alias:" comes from ssl:keymanager option
        // "trigger seeding of SecureRandom" comes from ssl:sslctx option
        // "jdk.tls.keyLimits:" comes from the plain "ssl" option
        return Stream.of(
                // all should print everything
                Arguments.of(List.of("-Djavax.net.debug=all"),
                        List.of("Plaintext before ENCRYPTION",
                                "trigger seeding of SecureRandom",
                                "adding as trusted certificates",
                                "supported_versions"),
                        null),
                // ssl should print most details expect verbose details
                Arguments.of(List.of("-Djavax.net.debug=ssl"),
                        List.of("adding as trusted certificates",
                                "trigger seeding of SecureRandom",
                                "supported_versions"),
                        List.of("Plaintext before ENCRYPTION")),
                // allow expand option for more verbose output
                Arguments.of(List.of("-Djavax.net.debug=ssl,handshake,expand"),
                        List.of("\"logger\".*: \"javax.net.ssl\",",
                                "\"message\".*: \"Produced ClientHello handshake message",
                                "adding as trusted certificates",
                                "trigger seeding of SecureRandom",
                                "supported_versions"),
                        List.of("Plaintext before ENCRYPTION")),
                // filtering on record option, with expand
                Arguments.of(List.of("-Djavax.net.debug=ssl:record,expand"),
                        List.of("\"logger\".*: \"javax.net.ssl\",",
                                "\"message\".*: \"READ: TLSv1.2 application_data"),
                        List.of("Plaintext before ENCRYPTION",
                                "\"message\".*: \"Produced ClientHello handshake message:")),
                // "all ssl" mode only true if "ssl" is javax.net.debug value
                // this test is equivalent to ssl:record mode
                Arguments.of(List.of("-Djavax.net.debug=ssl,record"),
                        List.of("handshake, length =",
                                "WRITE:"),
                        List.of("matching alias:",
                                "Plaintext before ENCRYPTION")),
                // ignore bad sub-option. treat like "ssl"
                Arguments.of(List.of("-Djavax.net.debug=ssl,typo"),
                        List.of("adding as trusted certificates",
                                "trigger seeding of SecureRandom",
                                "supported_versions"),
                        List.of("Plaintext before ENCRYPTION")),
                // ssltypo contains "ssl". Treat like "ssl"
                Arguments.of(List.of("-Djavax.net.debug=ssltypo"),
                        List.of("adding as trusted certificates",
                                "trigger seeding of SecureRandom",
                                "supported_versions"),
                        List.of("Plaintext before ENCRYPTION")),
                // plaintext is valid for record option
                Arguments.of(List.of("-Djavax.net.debug=ssl:record:plaintext"),
                        List.of("Plaintext before ENCRYPTION",
                                "length ="),
                        List.of("matching alias:")),
                Arguments.of(List.of("-Djavax.net.debug=ssl:trustmanager"),
                        List.of("adding as trusted certificates"),
                        List.of("Plaintext before ENCRYPTION")),
                Arguments.of(List.of("-Djavax.net.debug=ssl:sslctx"),
                        List.of("trigger seeding of SecureRandom"),
                        List.of("Plaintext before ENCRYPTION")),
                // help message test. Should exit without running test
                Arguments.of(List.of("-Djavax.net.debug=help"),
                        List.of("print the help messages",
                                "debugging can be widened with:"),
                        List.of("Plaintext before ENCRYPTION",
                                "adding as trusted certificates")),
                // add in javax.net.debug sanity test
                Arguments.of(List.of("-Djavax.net.debug=ssl:trustmanager",
                                "-Djava.security.debug=all"),
                        List.of("adding as trusted certificates",
                                "properties: Initial security property:",
                                "certpath: Cert path validation succeeded"),
                        List.of("Plaintext before ENCRYPTION")),
                // empty invokes System.Logger use
                Arguments.of(List.of("-Djavax.net.debug",
                        "-Djava.util.logging.config.file=" + LOG_FILE),
                        List.of("FINE: adding as trusted certificates",
                        "FINE: WRITE: TLSv1.3 application_data",
                        "supported_versions"),
                        null)
                );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void checkDebugOutput(List<String> params, List<String> expected,
                                 List<String> notExpected) throws Exception {

        List<String> args = new ArrayList<>(params);
        args.add("DebugPropertyValuesTest");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(args);
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
        new DebugPropertyValuesTest().run();
    }
}
