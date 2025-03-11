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
 * //test
 * @bug 8888888
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary dynamic logger
 * @run junit DynamicLogger
 */

import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformLoggingMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class DynamicLogger extends SSLSocketTemplate {

    static Path LOG_FILE;
    static boolean verboseLogging;
    static final String LOG_SPLITTER_STRING = "MXBean setLevel call";

    @BeforeAll
    static void setup() throws Exception {
        LOG_FILE = Path.of(System.getProperty("test.classes"), "logging.conf");
        Files.writeString(LOG_FILE, ".level = ALL\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
    }

    private static Stream<Arguments> patternMatches() {
        return Stream.of(
                // all should print everything
                // no System.Logger mode in use
                Arguments.of(List.of("-Djavax.net.debug=all"),
                        List.of("Plaintext before ENCRYPTION",
                                "X509TrustManagerImpl\\.java",
                                "trigger seeding of SecureRandom",
                                "adding as trusted certificates",
                                "supported_versions"),
                        null),
                // sanity check that help mode is working
                Arguments.of(List.of("-Djavax.net.debug=help"),
                        List.of("expand debugging information",
                                "debugging can be widened with:"),
                        List.of("Plaintext before ENCRYPTION",
                                "adding as trusted certificates")),
                // empty value invokes System.Logger use
                Arguments.of(List.of("-Djavax.net.debug",
                        "-Djava.util.logging.config.file=" + LOG_FILE),
                        List.of("FINE: adding as trusted certificates",
                        "Produced ClientHello handshake message",
                        "sun.security.ssl.SSLLogger log",
                        "\"client version\"",
                        "FINE: WRITE: TLSv1.3 application_data",
                        "supported_versions",
                        "FINE: Produced ServerHello handshake message \\("),
                        null),
                // without forcing load of j.u.l config,
                // System Logger Level = INFO
                Arguments.of(List.of("-Djavax.net.debug"),
                        List.of("INFO: No available application protocols",
                                "sun.security.ssl.SSLLogger log"),
                        List.of("FINE: adding as trusted certificates",
                                "Produced ClientHello handshake message",
                                "\"client version\"",
                                "FINE: WRITE: TLSv1.3 application_data",
                                "FINE: Produced ServerHello handshake message \\(")),
                // dormant Logger should be in use
                // test with PlatformLoggingMXBean
                Arguments.of(List.of("-Dtest.MXBean"),
                        List.of("FINE: adding as trusted certificates",
                                "Produced ClientHello handshake message",
                                "sun.security.ssl.SSLLogger log",
                                "\"client version\"",
                                "FINE: WRITE: TLSv1.3 application_data",
                                "supported_versions",
                                "FINE: Produced ServerHello handshake message \\("),
                        null)
        );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void checkDebugOutput(List<String> params, List<String> expected,
                                 List<String> notExpected) throws Exception {

        List<String> args = new ArrayList<>(params);
        args.add("DynamicLogger");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(args);
        outputAnalyzer.shouldHaveExitValue(0);
        if (args.contains("-Dtest.MXBean")) {
            // special case using MXBean to control logging
            checkMXBeanControl(outputAnalyzer, expected, notExpected);
            return;
        }
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

    private void checkMXBeanControl(OutputAnalyzer outputAnalyzer,
                        List<String> expected, List<String> notExpected) {
        String[] parts = outputAnalyzer.getOutput().split(LOG_SPLITTER_STRING, 2);
        if (parts.length != 2) {
            throw new RuntimeException("unexpected");
        }
        for (String s : expected) {
            Pattern p = Pattern.compile(s);
            // parts[0]: output with logging = INFO (default)
            // parts[1]: output with logging = ALL (set via MXBean)
            if (p.matcher(parts[0]).find()) {
                throw new RuntimeException("unexpected pattern found:" + s);
            }
            if (!p.matcher(parts[1]).find()) {
                throw new RuntimeException("expected to find pattern:" + s);
            }
        }
    }

    @Override
    protected void doClientSide() throws Exception {
        if (verboseLogging) {
            // this string used to split output file for checking later
            System.err.println(LOG_SPLITTER_STRING);
            PlatformLoggingMXBean mxbean = ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class);
            mxbean.setLoggerLevel("javax.net.ssl", "ALL");
        }
        super.doClientSide();

    }

    public static void main(String[] args) throws Exception {
        new DynamicLogger().run();
        if (System.getProperty("test.MXBean") != null) {
            // scenario where Logging Level is modified dynamically
            // during test run (and re-tested)
            verboseLogging = true;
            new DynamicLogger().run();
        }

    }
}
