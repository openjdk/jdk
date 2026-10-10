/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Exercise System.LoggerFinder service loading via sub-VM
 * launches using ProcessTools/OutputAnalyzer.
 *
 * @library /test/lib
 * @modules java.base/jdk.internal.logger.dynamic:+open java.base/sun.util.logging:+open
 * @build LoggerFinderProbeNoLoggingMain LoggerFinderProbeJulMain LoggerFinderProbeCustomMain LoggerFinderProbeDelayed FileHandlerTest
 * @build CustomLoggerFinder CustomSystemLogger LoggerFinderProbeDelayed MakeCustomLoggerFinderJar
 * @run driver MakeCustomLoggerFinderJar
 * @run junit LoggerFinderServiceLauncherTest
 */
import jdk.internal.logger.dynamic.DormantLogger;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LoggerFinderServiceLauncherTest {

    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final MethodHandle loggingTest;

    static Path CUSTOMLOG_FILE, FILEHANDLER_OUTPUT;

    static {
        try {
            loggingTest = lookup.findStatic(LoggerFinderServiceLauncherTest.class,
                    "loggingTestImpl", MethodType.methodType(boolean.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @BeforeAll
    static void setup() throws Exception {

        // A config file emulating environment with custom handler.
        CUSTOMLOG_FILE = Path.of("custom_logging.conf");
        FILEHANDLER_OUTPUT = Path.of("custom_output.txt");

        Files.writeString(CUSTOMLOG_FILE, ".level = INFO\n" +
                ".handlers = java.util.logging.FileHandler\n" +
                "java.util.logging.FileHandler.pattern = " +
                FILEHANDLER_OUTPUT.getFileName().toString() + "\n" +
                "java.util.logging.FileHandler.level = INFO\n" +
                "java.util.logging.FileHandler.formatter = java.util.logging.SimpleFormatter\n");
    }

    private static Path customJar() {
        String testClasses = System.getProperty("test.classes");
        if (testClasses == null) throw new RuntimeException("test.classes not set");
        return Path.of(testClasses, "custom-loggerfinder.jar");
    }

    private static String testClassPath() {
        String tcp = System.getProperty("test.class.path");
        if (tcp == null) throw new RuntimeException("test.class.path not set");
        return tcp;
    }

    // --- Suggestion #4: factor scenario/finder checks

    private static void assertFinder(OutputAnalyzer out, String scenario,
                                     String finderModule, String finderClass) {
        out.shouldContain("SCENARIO=" + scenario);
        if (finderModule != null) {
            out.shouldContain("FINDER_MODULE=" + finderModule);
        }
        out.shouldContain("FINDER_CLASS=" + finderClass);
    }

    private static String logLine(String loggerName, String level, String msg) {
        return loggerName + " LOG " + level + " " + msg;
    }

    private static void assertLogged(OutputAnalyzer out, String loggerName, String level, String msg) {
        out.shouldContain(logLine(loggerName, level, msg));
    }

    private static void assertNotLogged(OutputAnalyzer out, String loggerName, String level, String msg) {
        out.shouldNotContain(logLine(loggerName, level, msg));
    }

    private static final class Expect {
        final String logger;
        final String level;
        final String msg;

        Expect(String logger, String level, String msg) {
            this.logger = logger;
            this.level = level;
            this.msg = msg;
        }
    }

    /**
     * Helper method which ensures the logger is not being printed
     * more than once. Log should either be in File or Console output.
     */
    private void checkOutput(OutputAnalyzer output,
                             List<Expect> expectedInFile,
                             List<Expect> expectedInConsole) throws IOException {

        String content = Files.readString(FILEHANDLER_OUTPUT);

        for (Expect e : expectedInFile) {
            String s = logLine(e.logger, e.level, e.msg);
            if (!content.contains(s)) {
                throw new RuntimeException(s + " :text not found in file");
            }
            if (output.contains(s)) {
                throw new RuntimeException(s + " :text found in console");
            }
        }

        for (Expect e : expectedInConsole) {
            String s = logLine(e.logger, e.level, e.msg);
            if (content.contains(s)) {
                throw new RuntimeException(s + " :text found in file");
            }
            if (!output.contains(s)) {
                throw new RuntimeException(s + " :text not found in console");
            }
        }
    }


    /**
     * Helper for the common "testLoggers()" emission sequence.
     *
     * If loggerName contains "dormant", treat it as dormant logger
     */
    private static void checkLoggerOutput(OutputAnalyzer out, List<String> loggerNames) {
        for (String loggerName:loggerNames) {
            boolean dormant = loggerName.contains("dormant");

            // hello-2 is logged before enabling via jcmd in testLoggers()
            // hello-3 and hello-4 are logged after jcmd enable in testLoggers()
            // should-not-log-if-dormant occurs after level is set back to
            // OFF in testLoggers()
            if (dormant) {
                assertNotLogged(out, loggerName, "INFO", "hello-2");
                assertLogged(out, loggerName, "DEBUG", "hello-3");
                assertNotLogged(out, loggerName, "INFO", "should-not-log-if-dormant");
            } else {
                assertLogged(out, loggerName, "INFO", "hello-2");
                assertLogged(out, loggerName, "INFO", "should-not-log-if-dormant");
            }

            assertLogged(out, loggerName, "INFO", "hello-4");
        }
    }

    @Test
    public void noLoggingModule_noProviders_usesDefault() throws Exception {
        OutputAnalyzer out = runProbe(false,
                "LoggerFinderProbeNoLoggingMain",
                "--limit-modules", "java.base"
        );
        out.shouldHaveExitValue(0);

        assertFinder(out, "NO_JUL_LOGGING", "java.base",
                "jdk.internal.logger.DefaultLoggerFinder");
        checkLoggerOutput(out, List.of("noLoggingFramework",
                "dormantNoLoggingFramework"));
        out.shouldContain("INFO: hello-no-logging-framework");
    }

    @Test
    public void javaLoggingPresent_usesJulLoggerFinder() throws Exception {
        OutputAnalyzer out = runProbe(false,
                "LoggerFinderProbeJulMain"
        );
        out.shouldHaveExitValue(0);

        assertFinder(out, "JUL", "java.logging",
                "sun.util.logging.internal.LoggingProviderImpl");
        out.shouldContain("PRE_JCMD dormantLoggerFromJULLoggerWithHandler.isLoggable=false");
        out.shouldContain("POST_JCMD dormantLoggerFromJULLoggerWithNoHandler.isLoggable=true");
        checkLoggerOutput(out, List.of("regSys",
                "dormantLoggerFromJULLoggerWithNoHandler"));
    }

    @Test
    public void customProviderOnClasspath_usesCustomLoggerFinder() throws Exception {
        OutputAnalyzer out = runProbe(true,
                "LoggerFinderProbeCustomMain",
                "--limit-modules", "java.base"
        );
        out.shouldHaveExitValue(0);

        // finder module intentionally omitted (probe-specific / could be java.base in this launch)
        assertFinder(out, "CUSTOM", null, "CustomLoggerFinder");

        // The custom provider test uses some Handler testing
        // to verify correctness. No backend logger to check per se.
        assertNotLogged(out, "dormantFromCustomLogger", "INFO", "hello-2");
        assertNotLogged(out, "dormantFromCustomLogger", "INFO", "hello-4");
    }

    @Test
    public void delayedLoading_scenario() throws Exception {
        OutputAnalyzer out = runProbe(false,
                "LoggerFinderProbeDelayed"
        );
        out.shouldHaveExitValue(0);

        out.shouldContain("SCENARIO=DELAYED_LOGGING");
        out.shouldContain("Delayed loading. Logger type: class" +
                " jdk.internal.logger.LazyLoggers$JdkLazyLogger");
        checkLoggerOutput(out, List.of("dormantDelayedLogging"));
        assertLogged(out, "dormantDelayedLogging", "DEBUG", "hello-delayed");
    }

    @Test
    public void jcmdReportsUnregisteredDormantLogger() {
        String logger = "unregistered.dormant.logger";
        OutputAnalyzer out = jcmd("System.logging_level",
                "logger=" + logger + " level=TRACE");
        out.shouldContain("Dormant logger \"" + logger
                + "\" is not registered; no change made.");
    }

    @Test
    public void fileHandler_scenario() throws Exception {
        OutputAnalyzer out = runProbe(false,
                "FileHandlerTest",
                "-Djava.util.logging.config.file=" + CUSTOMLOG_FILE
        );
        out.shouldHaveExitValue(0);

        assertNotLogged(out, "dormantLogger", "INFO", "hello-2");

        checkOutput(out,
            // Logged to file
            List.of(
                new Expect("dormantLoggerFromJULLoggerWithNoHandler", "INFO", "hello-4"),
                new Expect("dormantLogger", "INFO", "hello-4"),
                new Expect("regSys", "INFO", "hello-2")
            ),
            // Logged to console
            List.of(
                new Expect("dormantLoggerFromJULLoggerWithNoHandler", "DEBUG", "hello-3"),
                new Expect("dormantLogger", "DEBUG", "hello-3")
            ));
    }

    private static OutputAnalyzer runProbe(boolean customCp, String mainClass, String... vmArgs) throws Exception {
        List<String> cmd = new ArrayList<>();
        if (customCp) {
            String sep = System.getProperty("path.separator");
            cmd.add("-Dtest.noclasspath=true");
            cmd.add("-cp");
            cmd.add(customJar() + sep + testClassPath());
        }
        for (String a : vmArgs) {
            cmd.add(a);
        }
        cmd.add("-Dtest.jdk=" + System.getProperty("java.home"));
        cmd.add("--add-exports");
        cmd.add("java.base/jdk.internal.logger.dynamic=ALL-UNNAMED");
        cmd.add("--add-exports");
        cmd.add("java.base/sun.util.logging=ALL-UNNAMED");
        cmd.add(mainClass);
        return ProcessTools.executeTestJava(cmd);
    }

    private static OutputAnalyzer jcmd(String... args) {
        String argsString = Arrays.stream(args).collect(Collectors.joining(" "));
        CommandExecutor executor = new PidJcmdExecutor();
        OutputAnalyzer oa = executor.execute(argsString);
        oa.shouldHaveExitValue(0);
        return oa;
    }

    static void testLoggers(List<System.Logger> loggerList) {
        testLoggers(loggerList, false);
    }

    static void testLoggers(List<System.Logger> loggerList, boolean triggerLoading) {
        for (System.Logger logger : loggerList) {
            System.err.println("PRE_JCMD " + logger.getName() + ".isLoggable="
                    + logger.isLoggable(System.Logger.Level.TRACE));

            logger.log(System.Logger.Level.INFO, logLine(logger.getName(), "INFO", "hello-2"));

            LoggerFinderServiceLauncherTest.jcmd("System.logging_level",
                    "logger=" + logger.getName() + " level=TRACE");

            // handler should now be installed as part of Dormant Logger retrieval
            // The Logger Level should be retained from JUL calls
            logger.log(System.Logger.Level.DEBUG, logLine(logger.getName(), "DEBUG", "hello-3"));
            logger.log(System.Logger.Level.INFO, logLine(logger.getName(), "INFO", "hello-4"));

            if (triggerLoading) {
                System.Logger delayed = getDormantLogger("test");
                // just something to trigger JUL
                Logger l = Logger.getLogger("test");
                logger.log(System.Logger.Level.DEBUG,
                        logLine(logger.getName(), "DEBUG", "hello-delayed"));
            }

            System.err.println("POST_JCMD " + logger.getName() + ".isLoggable="
                    + logger.isLoggable(System.Logger.Level.TRACE) + "," +
                    " logger class: " + logger.getClass());

            LoggerFinderServiceLauncherTest.jcmd("System.logging_level",
                    "logger=" + logger.getName() + " level=OFF");

            System.err.println("FINAL_JCMD- " + logger.getName() + ".isLoggable="
                    + logger.isLoggable(System.Logger.Level.TRACE));

            logger.log(System.Logger.Level.INFO,
                    logLine(logger.getName(), "INFO", "should-not-log-if-dormant"));
        }
    }

    static System.Logger getDormantLogger(String name) {
        return DormantLogger.of(
                name,
                LoggerFinderServiceLauncherTest.class.getModule(),
                new MutableCallSite(MethodHandles.constant(boolean.class, false)),
                loggingTest);
    }

    private static boolean loggingTestImpl() {
        // simple condition for test purposes
        return true;
    }

    static final class CapturingHandler extends Handler {
        volatile java.util.logging.LogRecord last;
        @Override
        public void publish(java.util.logging.LogRecord record) { if (record != null) last = record; }
        @Override public void flush() {}
        @Override public void close() {}
    }
}
