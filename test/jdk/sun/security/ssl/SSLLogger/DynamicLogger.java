/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8888888
 * @library /test/lib /javax/net/ssl/templates ../../
 * @summary dynamic logger
 * @run junit DynamicLogger
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.ProcessTools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class DynamicLogger extends SSLSocketTemplate {

    static Path ALLLOG_FILE, CUSTOMLOG_FILE, FILEHANDLER_OUTPUT,
            NOLOG_FILE, DEFAULT_FILE;
    static String jcmdLoggingLevel = "";
    static String jcmdLoggingOutput = "logger";
    static final String LOG_SPLITTER_STRING = "jcmd setLevel call";

    private static final String PLAIN_BEFORE_ENCRYPTION =
            "Plaintext before ENCRYPTION";
    private static final String SSL_CONSOLE_FORMAT =
            "X509TrustManagerImpl\\.java";
    private static final String TRIGGER_SEEDING =
            "trigger seeding of SecureRandom";
    private static final String TRUSTED_CERTS =
            "adding as trusted certificates";
    private static final String SUPPORTED_VERSIONS = "supported_versions";
    private static final String FINE_CERTS =
            "FINE: adding as trusted certificates";
    private static final String CLIENT_HELLO =
            "Produced ClientHello handshake message";
    private static final String LOGGER_LOG =
            "sun.security.ssl.SSLExtensions consumeOnTrade";
    private static final String CLIENT_VERSION = "\"client version\"";
    private static final String WRITE_APP =
            "FINE: WRITE: TLSv1.3 application_data";
    private static final String SERVER_HELLO =
            "FINE: Produced ServerHello handshake message:";
    private static final String INFO_PROTOCOLS =
            "INFO: No available application protocols";
    private static final String WARNING = "WARNING:";
    private static final String FINE = "FINE:";
    private static final String ALL = "ALL:";

    // Frequently used groups of log patterns
    private static final List<String> ALL_DEBUG_LIST = List.of(
            PLAIN_BEFORE_ENCRYPTION, TRIGGER_SEEDING,
            TRUSTED_CERTS, SUPPORTED_VERSIONS
    );
    private static final List<String> ALL_DEBUG_LIST_WITH_CONSOLE_LOGGER
            = List.of(PLAIN_BEFORE_ENCRYPTION, SSL_CONSOLE_FORMAT,
            TRIGGER_SEEDING, TRUSTED_CERTS, SUPPORTED_VERSIONS
    );
    private static final List<String> FINE_LIST = List.of(
            FINE_CERTS, CLIENT_HELLO, LOGGER_LOG, CLIENT_VERSION,
            WRITE_APP, SUPPORTED_VERSIONS, SERVER_HELLO
    );
    private static final List<String> DIRECT_OUTPUT_LIST = List.of(
            TRUSTED_CERTS, CLIENT_HELLO, CLIENT_VERSION,
            "WRITE: TLSv1.3 application_data", SUPPORTED_VERSIONS
    );
    private static final List<String> INFO_LIST = List.of(
            INFO_PROTOCOLS, LOGGER_LOG, SUPPORTED_VERSIONS, WARNING
    );
    private static final List<String> INFO_PLUS_LIST = List.of(
            FINE, FINE_CERTS, CLIENT_HELLO, CLIENT_VERSION, WRITE_APP,
            SERVER_HELLO, ALL
    );

    @BeforeAll
    static void setup() throws Exception {
        ALLLOG_FILE = Path.of("all_logging.conf");
        NOLOG_FILE = Path.of("off_logging.conf");
        // A config file emulating environment with custom handler
        // installed for "javax.net.net" Logger
        CUSTOMLOG_FILE = Path.of("custom_logging.conf");
        FILEHANDLER_OUTPUT = Path.of("custom_output.txt");
        // the JDK default (INFO level)
        DEFAULT_FILE = Path.of(System.getProperty("java.home"),
                "conf", "logging.properties");
        Files.writeString(ALLLOG_FILE, ".level = ALL\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
        Files.writeString(NOLOG_FILE, ".level = OFF\n" +
                "handlers= java.util.logging.ConsoleHandler\n" +
                "java.util.logging.ConsoleHandler.level = ALL\n");
        Files.writeString(CUSTOMLOG_FILE, "javax.net.ssl.level = ALL\n" +
                "javax.net.ssl.handlers = java.util.logging.FileHandler\n" +
                "java.util.logging.FileHandler.pattern = " +
                FILEHANDLER_OUTPUT.getFileName().toString() + "\n" +
                "java.util.logging.FileHandler.level = ALL\n" +
                "java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter");
    }

    private static Stream<Arguments> patternMatches() {
        return Stream.of(
                // all should print everything
                // no System.Logger mode in use
                Arguments.of(
                        List.of("-Djavax.net.debug=all"),
                        ALL_DEBUG_LIST_WITH_CONSOLE_LOGGER,
                        List.of()
                ),
                // empty value invokes System.Logger use
                Arguments.of(
                        List.of("-Djavax.net.debug",
                                "-Djava.util.logging.config.file=" + ALLLOG_FILE),
                        FINE_LIST,
                        List.of()
                ),
                // empty value invokes System.Logger use
                // but a logging conf of OFF should produce no output
                Arguments.of(
                    List.of("-Djavax.net.debug",
                            "-Djava.util.logging.config.file=" + NOLOG_FILE),
                    List.of(),
                    FINE_LIST
                ),
                // empty value invokes System.Logger use
                // but a default logging conf of INFO should
                // produce no SSL output below INFO Level
                // Some of these outputs should be re-examined,
                // feel more like FINE, FINEST category
                Arguments.of(
                    List.of("-Djavax.net.debug",
                            "-Djava.util.logging.config.file=" + DEFAULT_FILE),
                    //SYSTEM_LOGGER_INFO_LIST,
                    INFO_LIST,
                    INFO_PLUS_LIST
                ),
                // without forcing load of j.u.l config,
                // System Logger Level = INFO
                Arguments.of(
                        List.of("-Djavax.net.debug"),
                        INFO_LIST,
                        INFO_PLUS_LIST
                ),
                // dormant Logger should be in use
                // test with jcmd, ALL level logging with default
                // logging file defined
                Arguments.of(
                    List.of("-Dtest.jcmd=ALL",
                            "-Djava.util.logging.config.file=" + DEFAULT_FILE),
                    FINE_LIST,
                    List.of()
                ),
                // the customer log config uses FileHandler. As a result
                // we don't expect a new ConsoleHandler to be created
                // and should see no output
                Arguments.of(
                        List.of("-Dtest.jcmd=ALL",
                                "-Djava.util.logging.config.file=" +
                                        CUSTOMLOG_FILE),
                        List.of(),
                        FINE_LIST
                ),
                // dormant Logger should be in use
                // test with jcmd, ALL level logging
                Arguments.of(
                        List.of("-Dtest.jcmd=ALL"),
                        FINE_LIST,
                        List.of()
                ),
                // route dynamic logger output directly to System.out
                Arguments.of(
                        List.of("-Dtest.jcmd=ALL",
                                "-Dtest.jcmd.output=stdout"),
                        DIRECT_OUTPUT_LIST,
                        List.of()
                ),
                // route dynamic logger output directly to System.err
                Arguments.of(
                        List.of("-Dtest.jcmd=ALL",
                                "-Dtest.jcmd.output=stderr"),
                        DIRECT_OUTPUT_LIST,
                        List.of()
                ),
                // test with jcmd, INFO level logging
                Arguments.of(
                        List.of("-Dtest.jcmd=INFO"),
                        INFO_LIST,
                        INFO_PLUS_LIST
                ),
                // test with jcmd, INFO level logging
                // force loading of j.u.l.Logger before 1st invocation
                Arguments.of(
                        List.of("-Dtest.jcmd=INFO", "-DloadJULAtCount=0"),
                        INFO_LIST,
                        INFO_PLUS_LIST
                ),
                // test with jcmd, INFO level logging
                // force loading of j.u.l.Logger after 1st invocation
                Arguments.of(
                        List.of("-Dtest.jcmd=INFO", "-DloadJULAtCount=1"),
                        INFO_LIST,
                        INFO_PLUS_LIST
                ),
                // test with jcmd, ALL level logging
                // force loading of j.u.l.Logger after 1st invocation
                // ensure that the jul loading doesn't reset Logging level
                // back to INFO etc.
                Arguments.of(
                        List.of("-Dtest.jcmd=ALL", "-DloadJULAtCount=1"),
                        ALL_DEBUG_LIST,
                        List.of()
                )
        );
    }

    @ParameterizedTest
    @MethodSource("patternMatches")
    public void checkDebugOutput(List<String> params, List<String> expected,
                                 List<String> notExpected) throws Exception {

        List<String> args = new ArrayList<>(params);
        args.add("-Dtest.jdk=" + System.getProperty("java.home"));
        args.add("DynamicLogger");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeTestJava(args);
        outputAnalyzer.shouldHaveExitValue(0);
        if (args.contains("-Dtest.jcmd")) {
            // special case using jcmd to control logging
            checkJCMDControl(outputAnalyzer, expected, notExpected);
            return;
        }
        if (args.contains(CUSTOMLOG_FILE.getFileName().toString())) {
            // the FileHandler was in use, let's make sure
            // the file contains data
            checkLogFile();
        }
        if (!expected.isEmpty()) {
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

    private void checkLogFile() throws IOException {
        String content = Files.readString(FILEHANDLER_OUTPUT);
        if (!content.contains("FINE: adding as trusted certificates")) {
            throw new RuntimeException("text not found in file");
        }
    }

    private void checkJCMDControl(OutputAnalyzer outputAnalyzer,
                                  List<String> expected,
                                  List<String> notExpected) {
        String[] parts = outputAnalyzer.getOutput().split(LOG_SPLITTER_STRING, 2);
        if (parts.length != 2) {
            throw new RuntimeException("unexpected");
        }
        for (String s : expected) {
            Pattern p = Pattern.compile(s);
            // parts[0]: output with reduced logging (default)
            // parts[1]: output with logging = ALL (set via jcmd)
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
        if (!jcmdLoggingLevel.isEmpty()) {
            // this string used to split output file for checking later
            if ("stdout".equals(jcmdLoggingOutput)) {
                System.out.println(LOG_SPLITTER_STRING);
            } else {
                System.err.println(LOG_SPLITTER_STRING);
            }
            jcmd("System.logging_level", "logger=javax.net.ssl",
                    "level=" + jcmdLoggingLevel,
                    "output=" + jcmdLoggingOutput)
                .shouldContain("Dormant logger \"javax.net.ssl\" updated: level="
                        + jcmdLoggingLevel + ", output=" + jcmdLoggingOutput + ".");
        }
        super.doClientSide();

    }

    public static OutputAnalyzer jcmd(String... args) {
        String argsString = Arrays.stream(args).collect(Collectors.joining(" "));
        CommandExecutor executor = new PidJcmdExecutor();
        OutputAnalyzer oa = executor.execute(argsString);
        oa.shouldHaveExitValue(0);
        return oa;
    }

    public static void main(String[] args) throws Exception {
        Logger testLogger;
        int loadJULAtCount = Integer.parseInt(
                System.getProperty("loadJULAtCount", "-1"));
        if (loadJULAtCount == 0) {
            testLogger = Logger.getLogger("testLogger");
        }
        (new DynamicLogger()).run();
        // run 2nd time with verbose logging enabled
        jcmdLoggingLevel = System.getProperty("test.jcmd", "");
        jcmdLoggingOutput = System.getProperty("test.jcmd.output", "logger");
        if (loadJULAtCount == 1) {
            testLogger = Logger.getLogger("testLogger");
        }
        (new DynamicLogger()).run();
        jcmdLoggingLevel = "";
        jcmdLoggingOutput = "logger";
    }
}
