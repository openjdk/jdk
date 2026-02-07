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
 * @bug 8350582 8340312 8369995 8044609 8372004
 * @library /test/lib /javax/net/ssl/templates
 * @summary Correct the parsing of the ssl value in javax.net.debug
 * @run junit DebugPropertyValuesTest
 */

// A test to verify debug output for different javax.net.debug scenarios

import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class DebugPropertyValuesTest extends SSLSocketTemplate {

    private static final Path LOG_FILE = Path.of("logging.conf");
    private static final HashMap<String, List<String>> debugMessages =
            new HashMap<>();
    private static final String DATE_REGEX = "\\d{4}-\\d{2}-\\d{2}";

    static {
        debugMessages.put("handshake",
                List.of("Produced ClientHello handshake message",
                        "supported_versions"));
        debugMessages.put("handshake-expand",
                List.of("\"logger\".*: \"javax.net.ssl\",",
                        "\"specifics\"   : \\[",
                        "\"message\".*: \"Produced ClientHello handshake message"));
        debugMessages.put("keymanager", List.of("Choosing key:"));
        debugMessages.put("packet", List.of("Raw write"));
        debugMessages.put("plaintext",
                List.of("Plaintext before ENCRYPTION"));
        debugMessages.put("record", List.of("handshake, length =", "WRITE:"));
        debugMessages.put("record-expand",
                List.of("\"logger\".*: \"javax.net.ssl\",",
                        "\"message\".*: \"READ: TLSv1.2 application_data"));
        debugMessages.put("session", List.of("Session initialized:"));
        debugMessages.put("ssl", List.of("jdk.tls.keyLimits:"));
        debugMessages.put("sslctx",
                List.of("trigger seeding of SecureRandom"));
        debugMessages.put("trustmanager",
                List.of("adding as trusted certificates"));
        debugMessages.put("verbose",
                List.of("Ignore unsupported cipher suite:"));
        debugMessages.put("help",
                List.of("print this help message and exit",
                        "verbose handshake message printing"));
        debugMessages.put("java.security.debug",
                List.of("properties\\[.*\\|main\\|.*" + DATE_REGEX + ".*\\]:",
                        "certpath\\[.*\\|main\\|.*" + DATE_REGEX + ".*\\]:"));
        // "ALL" shouldn't be seen as a valid Level
        debugMessages.put("javax.net.debug.logger.ALL", List.of("ALL:"));
        debugMessages.put("javax.net.debug.logger",
                List.of("FINE: adding as trusted certificates:"
                            + System.lineSeparator() +
                            "  \"certificate\" : \\{",
                        "sun.security.ssl.SSLSocketImpl close",
                        "FINE: Produced ClientHello handshake message:" +
                            System.lineSeparator() +
                            "\"ClientHello\": \\{",
                        "FINE: WRITE: TLSv1.3 application_data"));
    }

    @BeforeAll
    static void setup() throws Exception {
        Files.writeString(LOG_FILE, ".level = ALL\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
    }

    private static Stream<Arguments> patternMatches() {
        return Stream.of(
                // all should print everything
                Arguments.of(List.of("-Djavax.net.debug=all"),
                        List.of("handshake", "keymanager", "packet",
                                "plaintext", "record", "session", "ssl",
                                "sslctx", "trustmanager", "verbose")),
                // ssl should print most details except verbose details
                Arguments.of(List.of("-Djavax.net.debug=ssl"),
                        List.of("handshake", "keymanager",
                                "record", "session", "ssl",
                                "sslctx", "trustmanager", "verbose")),
                // allow expand option for more verbose output
                Arguments.of(
                        List.of("-Djavax.net.debug=ssl,handshake,expand"),
                        List.of("handshake", "handshake-expand",
                                "ssl", "verbose")),
                // filtering on record option, with expand
                Arguments.of(List.of("-Djavax.net.debug=ssl:record,expand"),
                        List.of("record", "record-expand", "ssl")),
                // reverse the input params of last example. should be same result
                Arguments.of(List.of("-Djavax.net.debug=expand,record:ssl"),
                        List.of("record", "record-expand", "ssl")),
                // this test is equivalent to ssl:record mode
                Arguments.of(List.of("-Djavax.net.debug=ssl,record"),
                        List.of("record", "ssl")),
                // example of test where no "ssl" value is passed
                // handshake debugging with verbose mode
                // No debug logs should be printed
                Arguments.of(List.of("-Djavax.net.debug=handshake:verbose"),
                        List.of()),
                // another example of test where no "ssl" value is passed
                Arguments.of(List.of("-Djavax.net.debug=record"),
                        List.of()),
                // ignore bad sub-option. treat like "ssl"
                Arguments.of(List.of("-Djavax.net.debug=ssl,typo"),
                        List.of("handshake", "keymanager",
                                "record", "session", "ssl",
                                "sslctx", "trustmanager", "verbose")),
                // ssltypo contains "ssl". Treat like "ssl"
                Arguments.of(List.of("-Djavax.net.debug=ssltypo"),
                        List.of("handshake", "keymanager",
                                "record", "session", "ssl",
                                "sslctx", "trustmanager", "verbose")),
                // plaintext is valid for record option
                Arguments.of(List.of("-Djavax.net.debug=ssl:record:plaintext"),
                        List.of("plaintext", "record", "ssl")),
                Arguments.of(List.of("-Djavax.net.debug=ssl:trustmanager"),
                        List.of("ssl", "trustmanager")),
                Arguments.of(List.of("-Djavax.net.debug=ssl:sslctx"),
                        List.of("ssl", "sslctx")),
                // help message test. Should exit without running test
                Arguments.of(List.of("-Djavax.net.debug=help"),
                        List.of("help")),
                // add in javax.net.debug sanity test
                Arguments.of(List.of("-Djavax.net.debug=ssl:trustmanager",
                                "-Djava.security.debug=all"),
                        List.of("java.security.debug", "ssl", "trustmanager")),
                // empty invokes System.Logger use
                Arguments.of(List.of("-Djavax.net.debug",
                        "-Djava.util.logging.config.file=" + LOG_FILE),
                        List.of("handshake", "javax.net.debug.logger",
                                "keymanager", "packet", "plaintext",
                                "record", "session", "ssl",
                                "sslctx", "trustmanager", "verbose"))
        );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void checkDebugOutput(List<String> params,
                                 List<String> expected) throws Exception {

        List<String> args = new ArrayList<>(params);
        args.add("DebugPropertyValuesTest");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(args);
        outputAnalyzer.shouldHaveExitValue(0);
        for (String s : debugMessages.keySet()) {
            List<String> patterns = debugMessages.get(s);
            if (expected.contains(s)) {
                for (String output : patterns) {
                    outputAnalyzer.shouldMatch(output);
                }
            } else {
                // some debug messages overlap with each other. Only fail if
                // all the messages in the list were unexpected
                boolean allUnexpected = true;
                for (String output : patterns) {
                    if (!outputAnalyzer.contains(output)) {
                        allUnexpected = false;
                        break;
                    }
                }
                if (allUnexpected) {
                    throw new AssertionError("Unexpected output for key: " + s);
                }
            }
        }
    }
}
