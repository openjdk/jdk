/*
 * Copyright (c) 2010, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_CHECK_COMPILE_MSG;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_COMPARE;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_EXPECT_COMPILE_FAIL;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_EXPECT_RUN_FAIL;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_FORK;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_IGNORE_STD_ERROR;
import static jdk.nashorn.internal.test.framework.TestConfig.OPTIONS_RUN;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_FAIL_LIST;
import static jdk.nashorn.internal.test.framework.TestConfig.TEST_JS_SHARED_CONTEXT;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Abstract class to compile and run one .js script file.
 */
@SuppressWarnings("javadoc")
public abstract class AbstractScriptRunnable {
    // some test scripts need a "framework" script - whose features are used
    // in the test script. This optional framework script can be null.

    protected final String framework;
    // Script file that is being tested
    protected final File testFile;
    // build directory where test output, stderr etc are redirected
    protected final File buildDir;
    // should run the test or just compile?
    protected final boolean shouldRun;
    // is compiler error expected?
    protected final boolean expectCompileFailure;
    // is runtime error expected?
    protected final boolean expectRunFailure;
    // is compiler error captured and checked against known error strings?
    protected final boolean checkCompilerMsg;
    // .EXPECTED file compared for this or test?
    protected final boolean compare;
    // should test run in a separate process?
    protected final boolean fork;
    // ignore stderr output?
    protected final boolean ignoreStdError;
    // Foo.js.OUTPUT file where test stdout messages go
    protected final String outputFileName;
    // Foo.js.ERROR where test's stderr messages go.
    protected final String errorFileName;
    // copy of Foo.js.EXPECTED file
    protected final String copyExpectedFileName;
    // Foo.js.EXPECTED - output expected by running Foo.js
    protected final String expectedFileName;
    // options passed to Nashorn engine
    protected final List<String> engineOptions;
    // arguments passed to script - these are visible as "arguments" array to script
    protected final List<String> scriptArguments;
    // Tests that are forced to fail always
    protected final Set<String> failList = new HashSet<>();

    public AbstractScriptRunnable(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions, final List<String> scriptArguments) {
        this.framework = framework;
        this.testFile = testFile;
        this.buildDir = TestHelper.makeBuildDir(testFile);
        this.engineOptions = engineOptions;
        this.scriptArguments = scriptArguments;

        this.expectCompileFailure = testOptions.containsKey(OPTIONS_EXPECT_COMPILE_FAIL);
        this.shouldRun = testOptions.containsKey(OPTIONS_RUN);
        this.expectRunFailure = testOptions.containsKey(OPTIONS_EXPECT_RUN_FAIL);
        this.checkCompilerMsg = testOptions.containsKey(OPTIONS_CHECK_COMPILE_MSG);
        this.ignoreStdError = testOptions.containsKey(OPTIONS_IGNORE_STD_ERROR);
        this.compare = testOptions.containsKey(OPTIONS_COMPARE);
        this.fork = testOptions.containsKey(OPTIONS_FORK);

        final String testName = testFile.getName();
        this.outputFileName = buildDir + File.separator + testName + ".OUTPUT";
        this.errorFileName = buildDir + File.separator + testName + ".ERROR";
        this.copyExpectedFileName = buildDir + File.separator + testName + ".EXPECTED";
        this.expectedFileName = testFile.getPath() + ".EXPECTED";

        if (failListString != null) {
            final String[] failedTests = failListString.split(" ");
            for (final String failedTest : failedTests) {
                failList.add(failedTest.trim());
            }
        }
    }

    // run this test - compile or compile-and-run depending on option passed
    public void runTest() throws IOException {
        log(toString());
        Thread.currentThread().setName(testFile.getPath());
        if (shouldRun) {
            // Analysis of failing tests list -
            // if test is in failing list it must fail
            // to not wrench passrate (used for crashing tests).
            if (failList.contains(testFile.getName())) {
                fail(String.format("Test %s is forced to fail (see %s)", testFile, TEST_JS_FAIL_LIST));
            }

            execute();
        } else {
            compile();
        }
    }

    @Override
    public String toString() {
        return "Test(compile" + (expectCompileFailure ? "-" : "") + (shouldRun ? ", run" : "") + (expectRunFailure ? "-" : "") + "): " + testFile;
    }

    // compile-only command line arguments
    protected List<String> getCompilerArgs() {
        final List<String> args = new ArrayList<>();
        args.add("--compile-only");
        args.addAll(engineOptions);
        args.add(testFile.getPath());
        return args;
    }

    // shared context or not?
    protected static final boolean sharedContext = Boolean.getBoolean(TEST_JS_SHARED_CONTEXT);
    protected static final String failListString = System.getProperty(TEST_JS_FAIL_LIST);
    // VM options when a @fork test is executed by a separate process
    protected static final String[] forkJVMOptions;
    static {
        final String vmOptions = System.getProperty(TestConfig.TEST_FORK_JVM_OPTIONS);
        forkJVMOptions = (vmOptions != null)? vmOptions.split(" ") : new String[0];
    }

    private static final ThreadLocal<ScriptEvaluator> EVALUATORS = new ThreadLocal<>();

    /**
     * Create a script evaluator or return from cache
     * @return a ScriptEvaluator object
     */
    protected ScriptEvaluator getEvaluator() {
        synchronized (AbstractScriptRunnable.class) {
            ScriptEvaluator evaluator = EVALUATORS.get();
            if (evaluator == null) {
                if (sharedContext) {
                    final String[] args;
                    if (framework.indexOf(' ') > 0) {
                        args = framework.split("\\s+");
                    } else {
                        args = new String[] { framework };
                    }
                    evaluator = new SharedContextEvaluator(args);
                    EVALUATORS.set(evaluator);
                } else {
                    evaluator = new SeparateContextEvaluator();
                    EVALUATORS.set(evaluator);
                }
            }
            return evaluator;
        }
    }

    /**
     * Evaluate one or more scripts with given output and error streams
     *
     * @param out OutputStream for script output
     * @param err OutputStream for script errors
     * @param args arguments for script evaluation
     * @return success or error code from script execution
     */
    protected int evaluateScript(final OutputStream out, final OutputStream err, final String[] args) {
        try {
            return getEvaluator().run(out, err, args);
        } catch (final IOException e) {
            throw new UnsupportedOperationException("I/O error in initializing shell - cannot redirect output to file", e);
        }
    }

    // arguments to be passed to compile-and-run this script
    protected List<String> getRuntimeArgs() {
        final ArrayList<String> args = new ArrayList<>();
        // add engine options first
        args.addAll(engineOptions);

        // framework script if any
        if (framework != null) {
            if (framework.indexOf(' ') > 0) {
                args.addAll(Arrays.asList(framework.split("\\s+")));
            } else {
                args.add(framework);
            }
        }

        // test script
        args.add(testFile.getPath());

        // script arguments
        if (!scriptArguments.isEmpty()) {
            args.add("--");
            args.addAll(scriptArguments);
        }

        return args;
    }

    // compares actual test output with .EXPECTED output
    protected void compare(final BufferedReader actual, final BufferedReader expected, final boolean compareCompilerMsg) throws IOException {
        int lineCount = 0;
        while (true) {
            final String es = expected.readLine();
            String as = actual.readLine();
            if (compareCompilerMsg) {
                while (as != null && as.startsWith("--")) {
                    as = actual.readLine();
                }
            }
            ++lineCount;

            if (es == null && as == null) {
                if (expectRunFailure) {
                    fail("Expected runtime failure");
                } else {
                    break;
                }
            } else if (expectRunFailure && ((es == null) || as == null || !es.equals(as))) {
                break;
            } else if (es == null) {
                fail("Expected output for " + testFile + " ends prematurely at line " + lineCount);
            } else if (as == null) {
                fail("Program output for " + testFile + " ends prematurely at line " + lineCount);
            } else if (es.equals(as)) {
                continue;
            } else if (compareCompilerMsg && equalsCompilerMsgs(es, as)) {
                continue;
            } else {
                fail("Test " + testFile + " failed at line " + lineCount + " - " + " \n  expected: '" + escape(es) + "'\n     found: '" + escape(as) + "'");
            }
        }
    }

    // logs the message
    protected abstract void log(String msg);
    // throw failure message
    protected abstract void fail(String msg);
    // compile this script but don't run it
    protected abstract void compile() throws IOException;
    // compile and run this script
    protected abstract void execute();

    private static boolean equalsCompilerMsgs(final String es, final String as) {
        final int split = es.indexOf(':');
        // Replace both types of separators ('/' and '\') with the one from
        // current environment
        return (split >= 0) && as.equals(es.substring(0, split).replaceAll("[/\\\\]", Matcher.quoteReplacement(File.separator)) + es.substring(split));
    }

    private static void escape(final String value, final StringBuilder out) {
        final int len = value.length();
        for (int i = 0; i < len; i++) {
            final char ch = value.charAt(i);
            if (ch == '\n') {
                out.append("\\n");
            } else if (ch < ' ' || ch == 127) {
                out.append(String.format("\\%03o", (int) ch));
            } else if (ch > 127) {
                out.append(String.format("\\u%04x", (int) ch));
            } else {
                out.append(ch);
            }
        }
    }

    private static String escape(final String value) {
        final StringBuilder sb = new StringBuilder();
        escape(value, sb);
        return sb.toString();
    }
}
