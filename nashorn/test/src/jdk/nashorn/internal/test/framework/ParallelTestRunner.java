/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.test.framework;

import static jdk.nashorn.internal.test.framework.TestConfig.TEST_FAILED_LIST_FILE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_ENABLE_STRICT_MODE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_EXCLUDES_FILE;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_EXCLUDE_LIST;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_FRAMEWORK;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_ROOTS;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.nashorn.internal.test.framework.TestFinder.TestFactory;

/**
 * Parallel test runner runs tests in multiple threads - but avoids any dependency
 * on third-party test framework library such as TestNG.
 */
@SuppressWarnings("javadoc")
public class ParallelTestRunner {

    // ParallelTestRunner-specific
    private static final String    TEST_JS_THREADS     = "test.js.threads";
    private static final String    TEST_JS_REPORT_FILE = "test.js.report.file";
    // test262 does a lot of eval's and the JVM hates multithreaded class definition, so lower thread count is usually faster.
    private static final int       THREADS = Integer.getInteger(TEST_JS_THREADS, Runtime.getRuntime().availableProcessors() > 4 ? 4 : 2);

    private final List<ScriptRunnable> tests    = new ArrayList<>();
    private final Set<String>      orphans  = new TreeSet<>();
    private final ExecutorService  executor = Executors.newFixedThreadPool(THREADS);

    // Ctrl-C handling
    private final CountDownLatch   finishedLatch = new CountDownLatch(1);
    private final Thread           shutdownHook  = new Thread() {
                                                       @Override
                                                       public void run() {
                                                           if (!executor.isTerminated()) {
                                                               executor.shutdownNow();
                                                               try {
                                                                   executor.awaitTermination(25, TimeUnit.SECONDS);
                                                                   finishedLatch.await(5, TimeUnit.SECONDS);
                                                               } catch (final InterruptedException e) {
                                                                   // empty
                                                               }
                                                           }
                                                       }
                                                   };

    public ParallelTestRunner() throws Exception {
        suite();
    }

    private static PrintStream outputStream() {
        final String reportFile = System.getProperty(TEST_JS_REPORT_FILE, "");
        PrintStream output = System.out;

        if (!reportFile.isEmpty()) {
            try {
                output = new PrintStream(new OutputStreamDelegator(System.out, new FileOutputStream(reportFile)));
            } catch (final IOException e) {
                System.err.println(e);
            }
        }

        return output;
    }

    public static final class ScriptRunnable extends AbstractScriptRunnable implements Callable<ScriptRunnable.Result> {
        private final Result                result   = new Result();

        public class Result {
            private boolean  passed = true;
            public String    expected;
            public String    out;
            public String    err;
            public Throwable exception;

            public ScriptRunnable getTest() {
                return ScriptRunnable.this;
            }

            public boolean passed() {
                return passed;
            }

            @Override
            public String toString() {
                return getTest().toString();
            }
        }

        public ScriptRunnable(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions, final List<String> scriptArguments) {
            super(framework, testFile, engineOptions, testOptions, scriptArguments);
        }

        @Override
        protected void log(final String msg) {
            System.err.println(msg);
        }

        @Override
        protected void fail(final String message) {
            throw new TestFailedError(message);
        }

        @Override
        protected void compile() throws IOException {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            final List<String> args = getCompilerArgs();
            int errors;
            try {
                errors = evaluateScript(out, err, args.toArray(new String[0]));
            } catch (final AssertionError e) {
                final PrintWriter writer = new PrintWriter(err);
                e.printStackTrace(writer);
                writer.flush();
                errors = 1;
            }
            if (errors != 0 || checkCompilerMsg) {
                result.err = err.toString();
                if (expectCompileFailure || checkCompilerMsg) {
                    final PrintStream outputDest = new PrintStream(new FileOutputStream(getErrorFileName()));
                    TestHelper.dumpFile(outputDest, new StringReader(new String(err.toByteArray())));
                    outputDest.println("--");
                }
                if (errors != 0 && !expectCompileFailure) {
                    fail(String.format("%d errors compiling %s", errors, testFile));
                }
                if (checkCompilerMsg) {
                    compare(getErrorFileName(), expectedFileName, true);
                }
            }
            if (expectCompileFailure && errors == 0) {
                fail(String.format("No errors encountered compiling negative test %s", testFile));
            }
        }

        @Override
        protected void execute() {
            final List<String> args = getRuntimeArgs();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final ByteArrayOutputStream err = new ByteArrayOutputStream();

            try {
                final int errors = evaluateScript(out, err, args.toArray(new String[0]));

                if (errors != 0 || err.size() > 0) {
                    if (expectRunFailure) {
                        return;
                    }
                    if (!ignoreStdError) {

                        try (OutputStream outputFile = new FileOutputStream(getOutputFileName()); OutputStream errorFile = new FileOutputStream(getErrorFileName())) {
                            outputFile.write(out.toByteArray());
                            errorFile.write(err.toByteArray());
                        }

                        result.out = out.toString();
                        result.err = err.toString();
                        fail(err.toString());
                    }
                }

                if (compare) {
                    final File expectedFile = new File(expectedFileName);
                    try {
                        BufferedReader expected;
                        if (expectedFile.exists()) {
                            expected = new BufferedReader(new FileReader(expectedFile));
                        } else {
                            expected = new BufferedReader(new StringReader(""));
                        }
                        compare(new BufferedReader(new StringReader(out.toString())), expected, false);
                    } catch (final Throwable ex) {
                        if (expectedFile.exists()) {
                            copyExpectedFile();
                        }
                        try (OutputStream outputFile = new FileOutputStream(getOutputFileName()); OutputStream errorFile = new FileOutputStream(getErrorFileName())) {
                            outputFile.write(out.toByteArray());
                            errorFile.write(err.toByteArray());
                        }
                        ex.printStackTrace();
                        throw ex;
                    }
                }
            } catch (final IOException e) {
                if (!expectRunFailure) {
                    fail("Failure running test " + testFile + ": " + e.getMessage());
                } // else success
            }
        }

        private void compare(final String fileName, final String expected, final boolean compareCompilerMsg) throws IOException {
            final File expectedFile = new File(expected);

            BufferedReader expectedReader;
            if (expectedFile.exists()) {
                expectedReader = new BufferedReader(new InputStreamReader(new FileInputStream(expectedFileName)));
            } else {
                expectedReader = new BufferedReader(new StringReader(""));
            }

            final BufferedReader actual = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));

            compare(actual, expectedReader, compareCompilerMsg);
        }

        private void copyExpectedFile() {
            if (!new File(expectedFileName).exists()) {
                return;
            }
            // copy expected file overwriting existing file and preserving last
            // modified time of source
            try {
                Files.copy(FileSystems.getDefault().getPath(expectedFileName), FileSystems.getDefault().getPath(getCopyExpectedFileName()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (final IOException ex) {
                fail("failed to copy expected " + expectedFileName + " to " + getCopyExpectedFileName() + ": " + ex.getMessage());
            }
        }

        @Override
        public Result call() {
            try {
                runTest();
            } catch (final Throwable ex) {
                result.exception = ex;
                result.passed = false;
                ex.printStackTrace();
            }
            return result;
        }

        private String getOutputFileName() {
            buildDir.mkdirs();
            return outputFileName;
        }

        private String getErrorFileName() {
            buildDir.mkdirs();
            return errorFileName;
        }

        private String getCopyExpectedFileName() {
            buildDir.mkdirs();
            return copyExpectedFileName;
        }
    }

    private void suite() throws Exception {
        Locale.setDefault(new Locale(""));
        System.setOut(outputStream());

        final TestFactory<ScriptRunnable> testFactory = new TestFactory<ScriptRunnable>() {
            @Override
            public ScriptRunnable createTest(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions, final List<String> arguments) {
                return new ScriptRunnable(framework, testFile, engineOptions, testOptions, arguments);
            }

            @Override
            public void log(final String msg) {
                System.err.println(msg);
            }
        };

        TestFinder.findAllTests(tests, orphans, testFactory);

        Collections.sort(tests, new Comparator<ScriptRunnable>() {
            @Override
            public int compare(final ScriptRunnable o1, final ScriptRunnable o2) {
                return o1.testFile.compareTo(o2.testFile);
            }
        });
    }

    @SuppressWarnings("resource")
    public boolean run() throws IOException {
        final int testCount = tests.size();
        int passCount = 0;
        int doneCount = 0;
        System.out.printf("Found %d tests.\n", testCount);
        final long startTime = System.nanoTime();

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        final List<Future<ScriptRunnable.Result>> futures = new ArrayList<>();
        for (final ScriptRunnable test : tests) {
            futures.add(executor.submit(test));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(60, TimeUnit.MINUTES);
        } catch (final InterruptedException ex) {
            // empty
        }

        final List<ScriptRunnable.Result> results = new ArrayList<>();
        for (final Future<ScriptRunnable.Result> future : futures) {
            if (future.isDone()) {
                try {
                    final ScriptRunnable.Result result = future.get();
                    results.add(result);
                    doneCount++;
                    if (result.passed()) {
                        passCount++;
                    }
                } catch (CancellationException | ExecutionException ex) {
                    ex.printStackTrace();
                } catch (final InterruptedException ex) {
                    assert false : "should not reach here";
                }
            }
        }

        Collections.sort(results, new Comparator<ScriptRunnable.Result>() {
            @Override
            public int compare(final ScriptRunnable.Result o1, final ScriptRunnable.Result o2) {
                return o1.getTest().testFile.compareTo(o2.getTest().testFile);
            }
        });

        boolean hasFailed = false;
        final String failedList = System.getProperty(TEST_FAILED_LIST_FILE);
        final boolean hasFailedList = failedList != null;
        final boolean hadPreviouslyFailingTests = hasFailedList && new File(failedList).length() > 0;
        final FileWriter failedFileWriter = hasFailedList ? new FileWriter(failedList) : null;
        try {
            final PrintWriter failedListWriter = failedFileWriter == null ? null : new PrintWriter(failedFileWriter);
            for (final ScriptRunnable.Result result : results) {
                if (!result.passed()) {
                    if (hasFailed == false) {
                        hasFailed = true;
                        System.out.println();
                        System.out.println("FAILED TESTS");
                    }

                    System.out.println(result.getTest());
                    if(failedFileWriter != null) {
                        failedListWriter.println(result.getTest().testFile.getPath());
                    }
                    if (result.exception != null) {
                        final String exceptionString = result.exception instanceof TestFailedError ? result.exception.getMessage() : result.exception.toString();
                        System.out.print(exceptionString.endsWith("\n") ? exceptionString : exceptionString + "\n");
                        System.out.print(result.out != null ? result.out : "");
                    }
                }
            }
        } finally {
            if(failedFileWriter != null) {
                failedFileWriter.close();
            }
        }
        final double timeElapsed = (System.nanoTime() - startTime) / 1e9; // [s]
        System.out.printf("Tests run: %d/%d tests, passed: %d (%.2f%%), failed: %d. Time elapsed: %.0fmin %.0fs.\n", doneCount, testCount, passCount, 100d * passCount / doneCount, doneCount - passCount, timeElapsed / 60, timeElapsed % 60);
        System.out.flush();

        finishedLatch.countDown();

        if (hasFailed) {
            throw new AssertionError("TEST FAILED");
        }

        if(hasFailedList) {
            new File(failedList).delete();
        }

        if(hadPreviouslyFailingTests) {
            System.out.println();
            System.out.println("Good job on getting all your previously failing tests pass!");
            System.out.println("NOW re-running all tests to make sure you haven't caused any NEW test failures.");
            System.out.println();
        }

        return hadPreviouslyFailingTests;
    }

    public static void main(final String[] args) throws Exception {
        parseArgs(args);

        while (new ParallelTestRunner().run()) {
            //empty
        }
    }

    private static void parseArgs(final String[] args) {
        if (args.length > 0) {
            String roots = "";
            String reportFile = "";
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--roots") && i != args.length - 1) {
                    roots += args[++i] + " ";
                } else if (args[i].equals("--report-file") && i != args.length - 1) {
                    reportFile = args[++i];
                } else if (args[i].equals("--test262")) {
                    try {
                        setTest262Properties();
                    } catch (final IOException ex) {
                        System.err.println(ex);
                    }
                }
            }
            if (!roots.isEmpty()) {
                System.setProperty(TEST_JS_ROOTS, roots.trim());
            }
            if (!reportFile.isEmpty()) {
                System.setProperty(TEST_JS_REPORT_FILE, reportFile);
            }
        }
    }

    private static void setTest262Properties() throws IOException {
        System.setProperty(TEST_JS_ROOTS, "test/test262/test/suite/");
        System.setProperty(TEST_JS_FRAMEWORK, "test/script/test262.js test/test262/test/harness/framework.js test/test262/test/harness/sta.js");
        System.setProperty(TEST_JS_EXCLUDES_FILE, "test/test262/test/config/excludelist.xml");
        System.setProperty(TEST_JS_ENABLE_STRICT_MODE, "true");

        final Properties projectProperties = new Properties();
        projectProperties.load(new FileInputStream("project.properties"));
        String excludeList = projectProperties.getProperty("test262-test-sys-prop.test.js.exclude.list", "");
        final Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        for (;;) {
            final Matcher matcher = pattern.matcher(excludeList);
            if (!matcher.find()) {
                break;
            }
            final String propertyValue = projectProperties.getProperty(matcher.group(1), "");
            excludeList = excludeList.substring(0, matcher.start()) + propertyValue + excludeList.substring(matcher.end());
        }
        System.setProperty(TEST_JS_EXCLUDE_LIST, excludeList);
    }

    public static final class OutputStreamDelegator extends OutputStream {
        private final OutputStream[] streams;

        public OutputStreamDelegator(final OutputStream... streams) {
            this.streams = streams;
        }

        @Override
        public void write(final int b) throws IOException {
            for (final OutputStream stream : streams) {
                stream.write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            for (final OutputStream stream : streams) {
                stream.flush();
            }
        }
    }
}

final class TestFailedError extends Error {
    private static final long serialVersionUID = 1L;

    public TestFailedError(final String message) {
        super(message);
    }

    public TestFailedError(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TestFailedError(final Throwable cause) {
        super(cause);
    }
}
