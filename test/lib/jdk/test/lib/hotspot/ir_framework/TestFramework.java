/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework;

import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.helpers.ClassFileInstaller;
import sun.hotspot.WhiteBox;

import java.io.*;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class represents the main entry point to the test framework whose main purpose is to perform regex-based checks on
 * the C2 IR shape emitted by the VM flags {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly}. The framework can
 * also be used for other non-IR matching (and non-compiler) tests by providing easy to use annotations for commonly used
 * testing patterns and compiler control flags.
 * <p>
 * The framework offers various annotations to control how your test code should be invoked and being checked. There are
 * three kinds of tests depending on how much control is needed over the test invocation:
 * <b>Base tests</b> (see {@link Test}), <b>checked tests</b> (see {@link Check}), and <b>custom run tests</b>
 * (see {@link Run}). Each type of test needs to define a unique <i>test method</i> that specifies a {@link Test @Test}
 * annotation which represents the test code that is eventually executed by the test framework. More information about
 * the usage and how to write different tests can be found in {@link Test}, {@link Check}, and {@link Run}.
 * <p>
 * Each test method can specify an arbitrary number of IR rules. This is done by using {@link IR @IR} annotations which
 * can define regex strings that are matched on the output of {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly}.
 * The matching is done after the test method was (optionally) warmed up and compiled. More information about the usage
 * and how to write different IR rules can be found at {@link IR}.
 * <p>
 * This framework should be used with the following JTreg setup in your Test.java file in package <i>some.package</i>:
 * <pre>
 * {@literal @}library /test/lib
 * {@literal @}run driver some.package.Test
 * </pre>
 * Note that even though the framework uses the Whitebox API internally, it is not required to build and enabel it in the
 * JTreg test if the test itself is not utilizing any Whitebox features directly.
 * <p>
 * To specify additional flags, use {@link #runWithFlags(String...)}, {@link #addFlags(String...)},
 * {@link #addScenarios(Scenario...)}, or {@link #runWithScenarios(Scenario...)} where the scenarios can also be used
 * to run different flag combinations (instead of specifying multiple JTreg {@code @run} entries).
 * <p>
 * After annotating your test code with the framework specific annotations, the framework needs to be invoked from the
 * {@code main()} method of your JTreg test. There are two ways to do so. The first way is by calling the various
 * {@code runXX()} methods of {@link TestFramework}. The second way, which gives more control, is to create a new
 * {@code TestFramework} builder object on which {@link #start()} needs to be eventually called to start the testing.
 * <p>
 * The framework is called from the <i>driver VM</i> in which the JTreg test is initially run by specifying {@code
 * @run driver} in the JTreg header. This strips all additionally specified JTreg VM and Javaoptions.
 * The framework creates a new <i>flag VM</i> with all these flags added again in order to figure out which flags are
 * required to run the tests specified in the test class (e.g. {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly}
 * for IR matching).
 * <p>
 * After the flag VM terminates, it starts a new <i>test VM</i> which performs the execution of the specified
 * tests in the test class as described in {@link Test}, {@link Check}, and {@link Run}.
 * <p>
 * In a last step, once the test VM has terminated without exceptions, IR matching is performed if there are any IR
 * rules and if no VM flags disable it (e.g. not running with {@code -Xint}, see {@link IR} for more details).
 * The IR regex matching is done on the output of {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly} by parsing
 * the hotspot_pid file of the test VM. Failing IR rules are reported by throwing a {@link IRViolationException}.
 *
 * @see Test
 * @see Check
 * @see Run
 * @see IR
 */
public class TestFramework {
    /**
     * JTreg can define additional VM (-Dtest.vm.opts) and Javaoptions (-Dtest.java.opts) flags. IR verification is only
     * performed when all these additional JTreg flags (does not include additionally added framework and scenario flags
     * by user code) are whitelisted.
     *
     * A flag is whitelisted if it is a property flag (starting with -D), -ea, -esa, or if the flag name contains any of
     * the entries of this list as a substring (partial match).
     */
    public static final Set<String> JTREG_WHITELIST_FLAGS = new HashSet<>(
            Arrays.asList(
                    // The following substrings are part of more than one VM flag
                    "RAM",
                    "G1",
                    "Heap",
                    "Trace",
                    "Print",
                    "Verify",
                    "TLAB",
                    "UseNewCode",
                    // The following substrings are only part of one VM flag (=exact match)
                    "CreateCoredumpOnCrash",
                    "UnlockDiagnosticVMOptions",
                    "BackgroundCompilation",
                    "Xbatch",
                    "TieredCompilation",
                    "UseSerialGC",
                    "UseParallelGC",
                    "UseG1GC",
                    "UseZGC",
                    "UseShenandoahGC"
            )
    );

    static final boolean VERBOSE = Boolean.getBoolean("Verbose");
    static final boolean TESTLIST = !System.getProperty("Test", "").isEmpty();
    static final boolean EXCLUDELIST = !System.getProperty("Exclude", "").isEmpty();
    static final String TEST_VM_FLAGS_START = "##### TestFrameworkPrepareFlags - used by TestFramework #####";
    static final String TEST_VM_FLAGS_DELIMITER = " ";
    static final String TEST_VM_FLAGS_END = "----- END -----";

    private static final int WARMUP_ITERATIONS = Integer.getInteger("Warmup", -1);
    private static final boolean PREFER_COMMAND_LINE_FLAGS = Boolean.getBoolean("PreferCommandLineFlags");
    private static final boolean EXCLUDE_RANDOM = Boolean.getBoolean("ExcludeRandom");
    private static final boolean REPORT_STDOUT = Boolean.getBoolean("ReportStdout");
    private final boolean VERIFY_VM = Boolean.getBoolean("VerifyVM") && Platform.isDebugBuild();
    private boolean VERIFY_IR = Boolean.parseBoolean(System.getProperty("VerifyIR", "true"));
    private boolean shouldVerifyIR; // Should we perform IR matching?
    private static String lastTestVMOutput;

    private final Class<?> testClass;
    private List<Class<?>> helperClasses = null;
    private List<Scenario> scenarios = null;
    private final List<String> flags = new ArrayList<>();
    private int defaultWarmup = -1;
    private TestFrameworkSocket socket;
    private Scenario scenario;

    /*
     * Public interface methods
     */

    /**
     * Creates an instance acting as a builder to test the class from which this constructor was invoked from.
     * Use this constructor if you want to use multiple run options (flags, helper classes, scenarios).
     * Use the associated add methods ({@link #addFlags(String...)}, {@link #addScenarios(Scenario...)},
     * {@link #addHelperClasses(Class...)}) to set up everything and then start the testing by invoking {@link #start()}.
     */
    public TestFramework() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        this.testClass = walker.getCallerClass();
        if (VERBOSE) {
            System.out.println("Test class: " + testClass);
        }
    }

    /**
     * Creates an instance acting as a builder to test {@code testClass}.
     * Use this constructor if you want to use multiple run options (flags, helper classes, scenarios).
     * Use the associated add methods ({@link #addFlags(String...)}, @link #addScenarios(Scenario...)},
     * {@link #addHelperClasses(Class...)}) to set up everything and then start the testing by invoking {@link #start()}.
     *
     * @param testClass the class to be tested by the framework.
     * @see #TestFramework()
     */
    public TestFramework(Class<?> testClass) {
        TestRun.check(testClass != null, "Test class cannot be null");
        this.testClass = testClass;
        if (VERBOSE) {
            System.out.println("Test class: " + testClass);
        }
    }

    /**
     * Default flags that are added used for the test VM.
     */
    private static String[] getDefaultFlags() {
        return new String[] {"-XX:-BackgroundCompilation", "-XX:CompileCommand=quiet"};
    }

    /**
     * Additional verification flags that are used if -DVerifyVM=true is with a debug build.
     */
    private static String[] getVerifyFlags() {
        return new String[] {
                "-XX:+UnlockDiagnosticVMOptions", "-XX:+VerifyOops", "-XX:+VerifyStack", "-XX:+VerifyLastFrame",
                "-XX:+VerifyBeforeGC", "-XX:+VerifyAfterGC", "-XX:+VerifyDuringGC", "-XX:+VerifyAdapterSharing"
        };
    }

    /**
     * Tests the class from which this method was invoked from.
     */
    public static void run() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        run(walker.getCallerClass());
    }

    /**
     * Tests {@code testClass}.
     *
     * @param testClass the class to be tested by the framework.
     * @see #run()
     */
    public static void run(Class<?> testClass) {
        TestFramework framework = new TestFramework(testClass);
        framework.start();
    }

    /**
     * Tests the class from which this method was invoked from. The test VM is called with the specified {@code flags}.
     * <ul>
     *     <li><p>The {@code flags} override any set VM or Javaoptions flags by JTreg by default.<p>
     *            Use {@code -DPreferCommandLineFlags=true} if you want to prefer the JTreg VM and  Javaoptions flags over
     *            the specified {@code flags} of this method.</li>
     *     <li><p>If you want to run your entire JTreg test with additional flags, use this method.</li>
     *     <li><p>If you want to run your JTreg test with multiple flag combinations, use
     *            {@link #runWithScenarios(Scenario...)}</li>
     * </ul>
     *
     * @param flags VM flags to be used for the test VM.
     */
    public static void runWithFlags(String... flags) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        runWithFlags(walker.getCallerClass(), flags);
    }

    /**
     * Tests {@code testClass}. The test VM is called with the specified {@code flags}.
     * <ul>
     *     <li><p>The {@code flags} override any set VM or Javaoptions flags by JTreg by default.<p>
     *            Use {@code -DPreferCommandLineFlags=true} if you want to prefer the JTreg VM and  Javaoptions flags over
     *            the specified {@code flags} of this method.</li>
     *     <li><p>If you want to run your entire JTreg test with additional flags, use this method.</li>
     *     <li><p>If you want to run your JTreg test with multiple flag combinations, use
     *            {@link #runWithScenarios(Scenario...)}</li>
     * </ul>
     *
     * @param testClass the class to be tested by the framework.
     * @param flags VM flags to be used for the test VM.
     *
     * @see #runWithFlags(String...)
     */
    public static void runWithFlags(Class<?> testClass, String... flags) {
        TestFramework framework = new TestFramework(testClass);
        framework.addFlags(flags);
        framework.start();
    }

    /**
     * Tests {@code testClass} which uses {@code helperClasses} that can specify additional compile command annotations
     * ({@link ForceCompile @ForceCompile}, {@link DontCompile @DontCompile}, {@link ForceInline @ForceInline},
     * {@link DontInline @DontInline}) to be applied while testing {@code testClass} (also see description of
     * {@link TestFramework}).
     * <ul>
     *     <li><p>If a helper class is not in the same file as the test class, make sure that JTreg compiles it by using
     *     {@literal @}compile in the JTreg header comment block.</li>
     *     <li><p>If a class is used by the test class that does not specify any compile command annotations, you do not
     *     need to include it in {@code helperClasses}. If no helper class specifies any compile commands, consider
     *     using {@link #run()} or {@link #run(Class)}.</li>
     * </ul>
     *
     * @param testClass the class to be tested by the framework.
     * @param helperClasses helper classes containing compile command annotations ({@link ForceCompile},
     *                      {@link DontCompile}, {@link ForceInline}, {@link DontInline}) to be applied
     *                      while testing {@code testClass} (also see description of {@link TestFramework}).
     */
    public static void runWithHelperClasses(Class<?> testClass, Class<?>... helperClasses) {
        TestFramework framework = new TestFramework(testClass);
        framework.addHelperClasses(helperClasses);
        framework.start();
    }

    /**
     * Tests the class from which this method was invoked from. A test VM is called for each scenario in {@code scenarios}
     * by using the specified flags in the scenario.
     * <ul>
     *     <li><p>If there is only one scenario, consider using {@link #runWithFlags(String...)}.</li>
     *     <li><p>The scenario flags override any VM or Javaoptions set by JTreg by default.<p>
     *            Use {@code -DPreferCommandLineFlags=true} if you want to prefer the Java and VM options over the
     *            scenario flags.</li>
     * </ul>
     *
     * @param scenarios scenarios which specify specific flags for the test VM.
     */
    public static void runWithScenarios(Scenario... scenarios) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        runWithScenarios(walker.getCallerClass(), scenarios);
    }

    /**
     * Tests {@code testClass} A test VM is called for each scenario in {@code scenarios} by using the specified flags
     * in the scenario.
     * <ul>
     *     <li><p>If there is only one scenario, consider using {@link #runWithFlags(String...)}.</li>
     *     <li><p>The scenario flags override any VM or Javaoptions set by JTreg by default.<p>
     *            Use {@code -DPreferCommandLineFlags=true} if you want to prefer the Java and VM options over the
     *            scenario flags.</li>
     * </ul>
     *
     * @param testClass the class to be tested by the framework.
     * @param scenarios scenarios which specify specific flags for the test VM.
     *
     * @see #runWithScenarios(Scenario...)
     */
    public static void runWithScenarios(Class<?> testClass, Scenario... scenarios) {
        TestFramework framework = new TestFramework(testClass);
        framework.addScenarios(scenarios);
        framework.start();
    }

    /**
     * Add VM flags to be used for the test VM. These flags override any VM or Javaoptions set by JTreg by default.<p>
     * Use {@code -DPreferCommandLineFlags=true} if you want to prefer the VM or Javaoptions over the scenario flags.
     *
     * <p>
     * The testing can be started by invoking {@link #start()}
     *
     * @param flags VM options to be applied to the test VM.
     * @return the same framework instance.
     */
    public TestFramework addFlags(String... flags) {
        TestRun.check(flags != null && Arrays.stream(flags).noneMatch(Objects::isNull), "A flag cannot be null");
        this.flags.addAll(Arrays.asList(flags));
        return this;
    }

    /**
     * Add helper classes that can specify additional compile command annotations ({@link ForceCompile @ForceCompile},
     * {@link DontCompile @DontCompile}, {@link ForceInline @ForceInline}, {@link DontInline @DontInline}) to be applied
     * while testing {@code testClass} (also see description of {@link TestFramework}).
     * <ul>
     *     <li><p>If a helper class is not in the same file as the test class, make sure that JTreg compiles it by using
     *            {@code @compile} in the JTreg header comment block.</li>
     *     <li><p>If a class is used by the test class that does not specify any compile command annotations, you do not
     *            need to include it with this method. If no helper class specifies any compile commands, you do
     *            not need to call this method at all.</li>
     * </ul>
     *
     * <p>
     * The testing can be started by invoking {@link #start()}
     *
     * @param helperClasses helper classes containing compile command annotations ({@link ForceCompile},
     *                      {@link DontCompile}, {@link ForceInline}, {@link DontInline}) to be applied
     *                      while testing {@code testClass} (also see description of {@link TestFramework}).
     * @return the same framework instance.
     */
    public TestFramework addHelperClasses(Class<?>... helperClasses) {
        TestRun.check(helperClasses != null && Arrays.stream(helperClasses).noneMatch(Objects::isNull), "A Helper class cannot be null");
        if (this.helperClasses == null) {
            this.helperClasses = new ArrayList<>();
        }

        for (Class<?> helperClass : helperClasses) {
            TestRun.check(!this.helperClasses.contains(helperClass), "Cannot add the same class twice: " + helperClass);
            this.helperClasses.add(helperClass);
        }
        return this;
    }

    /**
     * Add scenarios to be used for the test VM. A test VM is called for each scenario in {@code scenarios} by using the
     * specified VM flags in the scenario. The scenario flags override any flags set by {@link #addFlags(String...)}
     * and thus also override any VM or Javaoptions set by JTreg by default.<p>
     * Use {@code -DPreferCommandLineFlags=true} if you want to prefer the VM and Javaoptions over the scenario flags.
     *
     * <p>
     * The testing can be started by invoking {@link #start()}
     *
     * @param scenarios scenarios which specify specific flags for the test VM.
     * @return the same framework instance.
     */
    public TestFramework addScenarios(Scenario... scenarios) {
        TestRun.check(scenarios != null && Arrays.stream(scenarios).noneMatch(Objects::isNull), "A scenario cannot be null");
        if (this.scenarios == null) {
            this.scenarios = new ArrayList<>(Arrays.asList(scenarios));
        } else {
            this.scenarios.addAll(Arrays.asList(scenarios));
        }
        return this;
    }

    /**
     * Start the testing of the implicitly (by {@link #TestFramework()}) or explicitly (by {@link #TestFramework(Class)})
     * set test class.
     */
    public void start() {
        installWhiteBox();
        maybeDisableIRVerificationCompletely();

        if (scenarios == null) {
            try {
                start(null);
            } catch (TestVMException e) {
                System.err.println("\n" + e.getExceptionInfo());
                throw e;
            } catch (IRViolationException e) {
                System.out.println("Compilation(s) of failed match(es):");
                System.out.println(e.getCompilations());
                System.err.println("\n" + e.getExceptionInfo());
                throw e;
            }
        } else {
            startWithScenarios();
        }
    }

    /**
     * Set a new default warm-up (overriding the framework default of 2000 at
     * {@link TestFrameworkExecution#WARMUP_ITERATIONS}) to be applied for all tests that do not specify an explicit
     * warm-up with {@link Warmup @Warmup}.
     *
     * @param defaultWarmup a new non-negative default warm-up.
     * @return the same framework instance.
     */
    public TestFramework setDefaultWarmup(int defaultWarmup) {
        TestFormat.check(defaultWarmup >= 0, "Cannot specify a negative default warm-up");
        this.defaultWarmup = defaultWarmup;
        return this;
    }

    /**
     * Get the VM output of the test VM. Use {@code -DVerbose=true} to enable more debug information. If scenarios
     * were run, use {@link Scenario#getTestVMOutput()}.
     *
     * @return the last test VM output.
     */
    public static String getLastTestVMOutput() {
        return lastTestVMOutput;
    }

    /*
     * The following methods are only intended to be called from actual @Test methods and not from the main() method of
     * a JTreg test. Calling these methods from main() results in a linking exception (Whitebox not yet loaded and enabled).
     */

    /**
     * Compile {@code m} at compilation level {@code compLevel}. {@code m} is first enqueued and might not be compiled,
     * yet, upon returning from this method.
     *
     * @param m the method to be compiled.
     * @param compLevel the (valid) compilation level at which the method should be compiled.
     * @throws TestRunException if compilation level is {@link CompLevel#SKIP} or {@link CompLevel#WAIT_FOR_COMPILATION}.
     */
    public static void compile(Method m, CompLevel compLevel) {
        TestFrameworkExecution.compile(m, compLevel);
    }

    /**
     * Deoptimize {@code m}.
     *
     * @param m the method to be deoptimized.
     */
    public static void deoptimize(Method m) {
        TestFrameworkExecution.deoptimize(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled at any level.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled at any level;
     *         {@code false} otherwise.
     */
    public static boolean isCompiled(Method m) {
        return TestFrameworkExecution.isCompiled(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled with C1.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled with C1;
     *         {@code false} otherwise.
     */
    public static boolean isC1Compiled(Method m) {
        return TestFrameworkExecution.isC1Compiled(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled with C2.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled with C2;
     *         {@code false} otherwise.
     */
    public static boolean isC2Compiled(Method m) {
        return TestFrameworkExecution.isC2Compiled(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled at the specified {@code compLevel}.
     *
     * @param m the method to be checked.
     * @param compLevel the compilation level.
     * @return {@code true} if {@code m} is compiled at {@code compLevel};
     *         {@code false} otherwise.
     */
    public static boolean isCompiledAtLevel(Method m, CompLevel compLevel) {
        return TestFrameworkExecution.isCompiledAtLevel(m, compLevel);
    }

    /**
     * Checks if {@code m} is compiled at any level.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is not compiled at any level.
     */
    public static void assertCompiled(Method m) {
        TestFrameworkExecution.assertCompiled(m);
    }

    /**
     * Checks if {@code m} is not compiled at any level.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is compiled at any level.
     */
    public static void assertNotCompiled(Method m) {
        TestFrameworkExecution.assertNotCompiled(m);
    }

    /**
     * Verifies that {@code m} is compiled with C1.
     *
     * @param m the method to be verified.
     * @throws TestRunException if {@code m} is not compiled with C1.
     */
    public static void assertCompiledByC1(Method m) {
        TestFrameworkExecution.assertCompiledByC1(m);
    }

    /**
     * Verifies that {@code m} is compiled with C2.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is not compiled with C2.
     */
    public static void assertCompiledByC2(Method m) {
        TestFrameworkExecution.assertCompiledByC2(m);
    }

    /**
     * Verifies that {@code m} is compiled at the specified {@code compLevel}.
     *
     * @param m the method to be checked.
     * @param compLevel the compilation level.
     * @throws TestRunException if {@code m} is not compiled at {@code compLevel}.
     */
    public static void assertCompiledAtLevel(Method m, CompLevel compLevel) {
        TestFrameworkExecution.assertCompiledAtLevel(m, compLevel);
    }

    /**
     * Verifies that {@code m} was deoptimized after being C1 compiled.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is was not deoptimized after being C1 compiled.
     */
    public static void assertDeoptimizedByC1(Method m) {
        TestFrameworkExecution.assertDeoptimizedByC1(m);
    }

    /**
     * Verifies that {@code m} was deoptimized after being C2 compiled.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is was not deoptimized after being C2 compiled.
     */
    public static void assertDeoptimizedByC2(Method m) {
        TestFrameworkExecution.assertDeoptimizedByC2(m);
    }

    /*
     * End of public interface methods
     */

    /**
     * Used to move Whitebox class to the right folder in the JTreg test
     */
    private void installWhiteBox() {
        try {
            ClassFileInstaller.main(WhiteBox.class.getName());
        } catch (Exception e) {
            throw new Error("failed to install whitebox classes", e);
        }
    }

    /**
     * Disable IR verification completely in certain cases.
     */
    private void maybeDisableIRVerificationCompletely() {
        if (VERIFY_IR) {
            VERIFY_IR = hasIRAnnotations();
            if (!VERIFY_IR) {
                System.out.println("IR verification disabled due to test " + testClass + " not specifying any @IR annotations");
                return;
            }

            VERIFY_IR = Platform.isDebugBuild() && !Platform.isInt() && !Platform.isComp();
            if (!VERIFY_IR) {
                System.out.println("IR verification disabled due to not running a debug build (required for PrintIdeal" +
                                   "and PrintOptoAssembly), running with -Xint, or -Xcomp (use warm-up of 0 instead)");
                return;
            }

            // No IR verification is done if additional non-whitelisted JTreg VM or Javaoptions flag is specified.
            VERIFY_IR = onlyWhitelistedJTregVMAndJavaOptsFlags();
            if (!VERIFY_IR) {
                System.out.println("IR verification disabled due to using non-whitelisted JTreg VM or Javaoptions flag(s).\n");
            }
        }
    }

    /**
     * For scenarios: Run the tests with the scenario settings and collect all exceptions to be able to run all
     * scenarios without prematurely throwing an exception. Format violations, however, are wrong for all scenarios
     * and thus is reported immediately on the first scenario execution.
     */
    private void startWithScenarios() {
        Map<Scenario, Exception> exceptionMap = new TreeMap<>(Comparator.comparingInt(Scenario::getIndex));
        Set<Integer> scenarioIndices = new HashSet<>();
        for (Scenario scenario : scenarios) {
            int scenarioIndex = scenario.getIndex();
            TestFormat.check(!scenarioIndices.contains(scenarioIndex),
                             "Cannot define two scenarios with the same index " + scenarioIndex);
            scenarioIndices.add(scenarioIndex);
            try {
                start(scenario);
            } catch (TestFormatException e) {
                // Test format violation is wrong for all the scenarios. Only report once.
                throw e;
            } catch (Exception e) {
                exceptionMap.put(scenario, e);
            }
        }
        if (!exceptionMap.isEmpty()) {
            reportScenarioFailures(exceptionMap);
        }
    }

    private void reportScenarioFailures(Map<Scenario, Exception> exceptionMap) {
        String failedScenarios = "The following scenarios have failed: #"
                                 + exceptionMap.keySet().stream()
                                               .map(s -> String.valueOf(s.getIndex()))
                                               .collect(Collectors.joining(", #"));
        StringBuilder builder = new StringBuilder(failedScenarios);
        builder.append("\n\n");
        for (Map.Entry<Scenario, Exception> entry : exceptionMap.entrySet()) {
            Exception e = entry.getValue();
            Scenario scenario = entry.getKey();
            String errorMsg = "";
            if (scenario != null) {
                errorMsg = getScenarioTitleAndFlags(scenario);
            }
            if (e instanceof IRViolationException) {
                IRViolationException irException = (IRViolationException) e;
                // For IR violations, only show the actual violations and not the (uninteresting) stack trace.
                System.out.println((scenario != null ? "Scenario #" + scenario.getIndex() + " - " : "")
                                   + "Compilation(s) of failed matche(s):");
                System.out.println(irException.getCompilations());
                builder.append(errorMsg).append("\n").append(irException.getExceptionInfo()).append(e.getMessage());
            } else if (e instanceof TestVMException) {
                builder.append(errorMsg).append("\n").append(((TestVMException) e).getExceptionInfo());
            } else {
                // Print stack trace otherwise
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                builder.append(errors.toString());
            }
            builder.append("\n");
        }
        System.err.println(builder.toString());
        if (!VERBOSE && !REPORT_STDOUT && !TESTLIST && !EXCLUDELIST) {
            // Provide a hint to the user how to get additional output/debugging information.
            System.err.println(JVMOutput.getRerunHint());
        }
        TestRun.fail(failedScenarios + ". Please check stderr for more information.");
    }

    private static String getScenarioTitleAndFlags(Scenario scenario) {
        StringBuilder builder = new StringBuilder();
        String title = "Scenario #" + scenario.getIndex();
        builder.append(title).append("\n").append("=".repeat(title.length())).append("\n");
        builder.append("Scenario flags: [").append(String.join(", ", scenario.getFlags())).append("]\n");
        return builder.toString();
    }

    /**
     * Execute a separate "flag" VM with White Box access to determine all test VM flags. The flag VM sends an encoding of
     * all required flags for the test VM to the driver VM over a socket. Once the flag VM exits, this driver VM parses the
     * test VM flags, which also determine if IR matching should be done, and then starts the test VM to execute all tests.
     */
    private void start(Scenario scenario) {
        if (scenario != null && !scenario.isEnabled()) {
            System.out.println("Disabled scenario #" + scenario.getIndex() + "! This scenario is not present in set flag " +
                               "-DScenarios and is therefore not executed.");
            return;
        }
        shouldVerifyIR = VERIFY_IR;
        socket = TestFrameworkSocket.getSocket();
        this.scenario = scenario;
        try {
            // Use TestFramework flags and scenario flags for new VMs.
            List<String> additionalFlags = new ArrayList<>(flags);
            if (scenario != null) {
                List<String> scenarioFlags = scenario.getFlags();
                String scenarioFlagsString = scenarioFlags.isEmpty() ? "" : " - [" + String.join(", ", scenarioFlags) + "]";
                System.out.println("Scenario #" + scenario.getIndex() + scenarioFlagsString + ":");
                additionalFlags.addAll(scenarioFlags);
            }
            socket.start();
            if (shouldVerifyIR) {
                System.out.println("Run Flag VM:");
                runFlagVM(additionalFlags);
            } else {
                System.out.println("Skip Flag VM due to not performing IR verification.");
            }

            String flagsString = additionalFlags.isEmpty() ? "" : " - [" + String.join(", ", additionalFlags) + "]";
            System.out.println("Run Test VM" + flagsString + ":");
            runTestVM(additionalFlags);
        } finally {
            System.out.println();
            socket.close();
        }
    }

    private boolean hasIRAnnotations() {
        return Arrays.stream(testClass.getDeclaredMethods()).anyMatch(m -> m.getAnnotationsByType(IR.class) != null);
    }

    private boolean onlyWhitelistedJTregVMAndJavaOptsFlags() {
        List<String> flags = Arrays.stream(Utils.getTestJavaOpts())
                                   .map(s -> s.replaceFirst("-XX:[+|-]?|-(?=[^D|^e])", ""))
                                   .collect(Collectors.toList());
        for (String flag : flags) {
            // Property flags (prefix -D), -ea and -esa are whitelisted.
            if (!flag.startsWith("-D") && !flag.startsWith("-e") && JTREG_WHITELIST_FLAGS.stream().noneMatch(flag::contains)) {
                // Found VM flag that is not whitelisted
                return false;
            }
        }
        return true;
    }

    private void runFlagVM(List<String> additionalFlags) {
        ArrayList<String> cmds = prepareFlagVMFlags(additionalFlags);
        OutputAnalyzer oa;
        try {
            // Run "flag" VM with White Box access to determine the test VM flags and if IR verification should be done.
            oa = ProcessTools.executeTestJvm(cmds);
        } catch (Exception e) {
            throw new TestRunException("Failed to execute TestFramework flag VM", e);
        }
        checkFlagVMExitCode(oa);
    }

    /**
     * The "flag" VM needs White Box access to prepare all test VM flags. It sends these as encoding over a socket to the
     * driver VM which afterwards parses the flags and adds them to the test VM.
     */
    private ArrayList<String> prepareFlagVMFlags(List<String> additionalFlags) {
        ArrayList<String> cmds = new ArrayList<>();
        cmds.add("-Dtest.jdk=" + Utils.TEST_JDK);
        // Set java.library.path so JNI tests which rely on jtreg nativepath setting work
        cmds.add("-Djava.library.path=" + Utils.TEST_NATIVE_PATH);
        cmds.add("-cp");
        cmds.add(Utils.TEST_CLASS_PATH);
        cmds.add("-Xbootclasspath/a:.");
        cmds.add("-XX:+UnlockDiagnosticVMOptions");
        cmds.add("-XX:+WhiteBoxAPI");
        cmds.add(socket.getPortPropertyFlag());
        // TestFramework and scenario flags might have an influence on the later used test VM flags. Add them as well.
        cmds.addAll(additionalFlags);
        cmds.add(TestFrameworkPrepareFlags.class.getCanonicalName());
        cmds.add(testClass.getCanonicalName());
        return cmds;
    }

    private void checkFlagVMExitCode(OutputAnalyzer oa) {
        String flagVMOutput = oa.getOutput();
        final int exitCode = oa.getExitValue();
        if (VERBOSE && exitCode == 0) {
            System.out.println("--- OUTPUT TestFramework flag VM ---");
            System.out.println(flagVMOutput);
        }

        if (exitCode != 0) {
            System.err.println("--- OUTPUT TestFramework flag VM ---");
            System.err.println(flagVMOutput);
            throw new RuntimeException("\nTestFramework flag VM exited with " + exitCode);
        }
    }

    private void runTestVM(List<String> additionalFlags) {
        List<String> cmds = prepareTestVMFlags(additionalFlags);
        socket.start();

        OutputAnalyzer oa;
        ProcessBuilder process = ProcessTools.createJavaProcessBuilder(cmds);
        try {
            // Calls 'main' of TestFrameworkExecution to run all specified tests with commands 'cmds'.
            // Use executeProcess instead of executeTestJvm as we have already added the JTreg VM and
            // Java options in prepareTestVMFlags().
            oa = ProcessTools.executeProcess(process);
        } catch (Exception e) {
            fail("Error while executing Test VM", e);
            return;
        }
        JVMOutput output = new JVMOutput(oa, scenario, process);
        lastTestVMOutput = oa.getOutput();
        if (scenario != null) {
            scenario.setTestVMOutput(lastTestVMOutput);
        }
        String socketOutput = "";
        if (shouldVerifyIR || TESTLIST || EXCLUDELIST) {
            // Socket has only output to read if IR verification is done and/or if a test list was provided by user
            socketOutput = socket.getOutputPrintStdout();
        }
        checkTestVMExitCode(output);
        if (shouldVerifyIR) {
            try {
                new IRMatcher(output.getHotspotPidFileName(), socketOutput, testClass);
            } catch (IRViolationException e) {
                e.setExceptionInfo(output.getExceptionInfo(scenario != null));
                throw e;
            }
        } else {
            System.out.println("IR verification disabled either due to no @IR annotations, through explicitly setting " +
                               "-DVerify=false, due to not running a debug build, using a non-whitelisted JTreg VM or " +
                               "Javaopts flag like -Xint, or running the test VM with other VM flags added by user code " +
                               "that make the IR verification impossible (e.g. -XX:-UseCompile, " +
                               "-XX:TieredStopAtLevel=[1,2,3], etc.).");
        }
    }

    private List<String> prepareTestVMFlags(List<String> additionalFlags) {
        ArrayList<String> cmds = new ArrayList<>();
        // Set java.library.path so JNI tests which rely on jtreg nativepath setting work
        cmds.add("-Djava.library.path=" + Utils.TEST_NATIVE_PATH);
        // Need White Box access in test VM.
        cmds.add("-Xbootclasspath/a:.");
        cmds.add("-XX:+UnlockDiagnosticVMOptions");
        cmds.add("-XX:+WhiteBoxAPI");
        String[] jtregVMFlags = Utils.getTestJavaOpts();
        if (!PREFER_COMMAND_LINE_FLAGS) {
            cmds.addAll(Arrays.asList(jtregVMFlags));
        }
        cmds.addAll(additionalFlags);
        cmds.addAll(getTestVMFlags());

        if (PREFER_COMMAND_LINE_FLAGS) {
            // Prefer flags set via the command line over the ones set by scenarios.
            cmds.addAll(Arrays.asList(jtregVMFlags));
        }

        if (WARMUP_ITERATIONS < 0 && defaultWarmup != -1) {
            // Only use the set warmup for the framework if not overridden by a valid -DWarmup property set by a test.
            cmds.add("-DWarmup=" + defaultWarmup);
        }

        // Add server property flag that enables test VM to print encoding for IR verification last and debug messages.
        cmds.add(socket.getPortPropertyFlag());

        cmds.add(TestFrameworkExecution.class.getName());
        cmds.add(testClass.getName());
        if (helperClasses != null) {
            helperClasses.forEach(c -> cmds.add(c.getName()));
        }
        return cmds;
    }

    /**
     * Parse the test VM flags as prepared by the flag VM. Additionally check the property flag DShouldDoIRVerification
     * to determine if IR matching should be done or not.
     */
    private List<String> getTestVMFlags() {
        List<String> flagList = new ArrayList<>();

        if (VERIFY_VM) {
            flagList.addAll(Arrays.asList(getVerifyFlags()));
        }

        flagList.addAll(Arrays.asList(getDefaultFlags()));

        if (shouldVerifyIR) {
            String flags = socket.getOutput();
            if (VERBOSE) {
                System.out.println("Read sent data from flag VM from socket:");
                System.out.println(flags);
            }
            String patternString = "(?<=" + TestFramework.TEST_VM_FLAGS_START + "\\R)" + "(.*DShouldDoIRVerification=(true|false).*)\\R"
                                   + "(?=" + IREncodingPrinter.END + ")";
            Pattern pattern = Pattern.compile(patternString);
            Matcher matcher = pattern.matcher(flags);
            check(matcher.find(), "Invalid flag encoding emitted by flag VM");
            // Maybe we run with flags that make IR verification impossible
            shouldVerifyIR = Boolean.parseBoolean(matcher.group(2));
            flagList.addAll(Arrays.asList(matcher.group(1).split(TEST_VM_FLAGS_DELIMITER)));
        }
        return flagList;
    }

    private void checkTestVMExitCode(JVMOutput vmOutput) {
        final int exitCode = vmOutput.getExitCode();
        if (EXCLUDE_RANDOM || REPORT_STDOUT || (VERBOSE && exitCode == 0)) {
            System.out.println("--- OUTPUT TestFramework test VM ---");
            System.out.println(vmOutput.getOutput());
        }

        if (exitCode != 0) {
            throwTestVMException(vmOutput);
        }
    }

    private void throwTestVMException(JVMOutput vmOutput) {
        String stdErr = vmOutput.getStderr();
        if (stdErr.contains("TestFormat.reportIfAnyFailures")) {
            Pattern pattern = Pattern.compile("Violations \\(\\d+\\)[\\s\\S]*(?=/============/)");
            Matcher matcher = pattern.matcher(stdErr);
            TestFramework.check(matcher.find(), "Must find violation matches");
            throw new TestFormatException("\n\n" + matcher.group());
        } else if (stdErr.contains("NoTestsRunException")) {
            shouldVerifyIR = false;
            throw new NoTestsRunException(">>> No tests run due to empty set specified with -DTest and/or -DExclude. " +
                                          "Make sure to define a set of at least one @Test method");
        } else {
            throw new TestVMException(vmOutput.getExceptionInfo(scenario != null));
        }
    }

    static void check(boolean test, String failureMessage) {
        if (!test) {
            fail(failureMessage);
        }
    }

    static void fail(String failureMessage) {
        throw new TestFrameworkException("Internal Test Framework exception - please file a bug:\n" + failureMessage);
    }

    static void fail(String failureMessage, Throwable e) {
        throw new TestFrameworkException("Internal Test Framework exception - please file a bug:\n" + failureMessage, e);
    }
}

/**
 * Class to encapsulate information about the test VM output, the run process and the scenario.
 */
class JVMOutput {

    private final Scenario scenario;
    private final OutputAnalyzer oa;
    private final ProcessBuilder process;
    private final String hotspotPidFileName;

    JVMOutput(OutputAnalyzer oa, Scenario scenario, ProcessBuilder process) {
        this.oa = oa;
        this.scenario = scenario;
        this.process = process;
        this.hotspotPidFileName = String.format("hotspot_pid%d.log", oa.pid());
    }

    public Scenario getScenario() {
        return scenario;
    }

    public String getCommandLine() {
        return "Command Line:\n" + String.join(" ", process.command()) + "\n\n";
    }

    public int getExitCode() {
        return oa.getExitValue();
    }

    public String getOutput() {
        return oa.getOutput();
    }

    public String getStdout() {
        return oa.getStdout();
    }

    public String getStderr() {
        return oa.getStderr();
    }

    public String getHotspotPidFileName() {
        return hotspotPidFileName;
    }

    /**
     * Get more detailed information about the exception in a pretty format.
     */
    public String getExceptionInfo(boolean stripRerunHint) {
        int exitCode = getExitCode();
        String stdErr = getStderr();
        String rerunHint = "";
        String stdOut = "";
        if (exitCode == 134) {
            stdOut = "\n\nStandard Output\n---------------\n" + getOutput();
        } else if (!stripRerunHint) {
            rerunHint = getRerunHint();
        }
        if (exitCode == 0) {
            // IR exception
            return getCommandLine() + rerunHint;
        } else {
            return "TestFramework test VM exited with code " + exitCode + "\n"
                   + stdOut + "\n" + getCommandLine() + "\n\nError Output\n------------\n" + stdErr + "\n\n" + rerunHint;
        }
    }

    public static String getRerunHint() {
        return """
                 #############################################################
                  - To only run the failed tests use -DTest, -DExclude,
                    and/or -DScenarios.
                  - To also get the standard output of the test VM run with\s
                    -DReportStdout=true or for even more fine-grained logging
                    use -DVerbose=true.
                 #############################################################
               """ + "\n";
    }
}

/**
 * Dedicated socket to send data from the flag and test VM back to the driver VM.
 */
class TestFrameworkSocket {
    static final String SERVER_PORT_PROPERTY = "ir.framework.server.port";

    // Static fields used by flag and test VM only.
    private static final int SERVER_PORT = Integer.getInteger(SERVER_PORT_PROPERTY, -1);

    private static final boolean REPRODUCE = Boolean.getBoolean("Reproduce");
    private static final String HOSTNAME = null;
    private static final String STDOUT_PREFIX = "[STDOUT]";
    private static Socket clientSocket = null;
    private static PrintWriter clientWriter = null;

    private final String serverPortPropertyFlag;
    private FutureTask<String> socketTask;
    private ServerSocket serverSocket;

    private static TestFrameworkSocket singleton = null;

    private TestFrameworkSocket() {
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException e) {
            TestFramework.fail("Failed to create TestFramework server socket", e);
        }
        int port = serverSocket.getLocalPort();
        if (TestFramework.VERBOSE) {
            System.out.println("TestFramework server socket uses port " + port);
        }
        serverPortPropertyFlag = "-D" + SERVER_PORT_PROPERTY + "=" + port;
    }

    public static TestFrameworkSocket getSocket() {
        if (singleton == null || singleton.serverSocket.isClosed()) {
            singleton = new TestFrameworkSocket();
            return singleton;
        }
        return singleton;
    }

    public String getPortPropertyFlag() {
        return serverPortPropertyFlag;
    }

    public void start() {
        socketTask = initSocketTask();
        Thread socketThread = new Thread(socketTask);
        socketThread.start();
    }

    /**
     * Waits for client sockets (created by flag or test VM) to connect. Return the messages received by the clients.
     */
    private FutureTask<String> initSocketTask() {
        return new FutureTask<>(() -> {
            try (Socket clientSocket = serverSocket.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
            ) {
                StringBuilder builder = new StringBuilder();
                String next;
                while ((next = in.readLine()) != null) {
                    builder.append(next).append("\n");
                }
                return builder.toString();
            } catch (IOException e) {
                TestFramework.fail("Server socket error", e);
                return null;
            }
        });
    }

    public void close() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            TestFramework.fail("Could not close socket", e);
        }
    }

    /**
     * Only called by flag and test VM to write to server socket.
     */
    public static void write(String msg, String type) {
        write(msg, type, false);
    }

    /**
     * Only called by flag and test VM to write to server socket.
     */
    public static void write(String msg, String type, boolean stdout) {
        if (REPRODUCE) {
            System.out.println("Debugging Test VM: Skip writing due to -DReproduce");
            return;
        }
        TestFramework.check(SERVER_PORT != -1, "Server port was not set correctly for flag and/or test VM "
                                              + "or method not called from flag or test VM");
        try {
            // Keep the client socket open until the flag or test VM terminates (calls closeClientSocket before exiting
            // main()).
            if (clientSocket == null) {
                clientSocket = new Socket(HOSTNAME, SERVER_PORT);
                clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
            }
            if (stdout) {
                msg = STDOUT_PREFIX + msg;
            }
            clientWriter.println(msg);
        } catch (Exception e) {
            // When the test VM is directly run, we should ignore all messages that would normally be sent to the
            // driver VM.
            String failMsg = "\n\n" + """
                             ###########################################################
                              Did you directly run the test VM (TestFrameworkExecution)
                              to reproduce a bug?
                              => Append the flag -DReproduce=true and try again!
                             ###########################################################
                             """;
            TestRun.fail(failMsg, e);
        }
        if (TestFramework.VERBOSE) {
            System.out.println("Written " + type + " to socket:");
            System.out.println(msg);
        }
    }

    /**
     * Closes (and flushes) the printer to the socket and the socket itself. Is called as last thing before exiting
     * the main() method of the flag and the test VM.
     */
    public static void closeClientSocket() {
        if (clientSocket != null) {
            try {
                clientWriter.close();
                clientSocket.close();
            } catch (IOException e) {
                throw new RuntimeException("Could not close TestFrameworkExecution socket", e);
            }
        }
    }

    /**
     * Get the socket output of the flag VM.
     */
    public String getOutput() {
        try {
            return socketTask.get();

        } catch (Exception e) {
            TestFramework.fail("Could not read from socket task", e);
            return null;
        }
    }

    /**
     * Get the socket output from the test VM by stripping all lines starting with a [STDOUT] output and printing them
     * to the standard output.
     */
    public String getOutputPrintStdout() {
        try {
            String output = socketTask.get();
            if (TestFramework.TESTLIST || TestFramework.EXCLUDELIST) {
                StringBuilder builder = new StringBuilder();
                Scanner scanner = new Scanner(output);
                System.out.println("\nRun flag defined test list");
                System.out.println("--------------------------");
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith(STDOUT_PREFIX)) {
                        line = "> " + line.substring(STDOUT_PREFIX.length());
                        System.out.println(line);
                    } else {
                        builder.append(line).append("\n");
                    }
                }
                System.out.println();
                return builder.toString();
            }
            return output;

        } catch (Exception e) {
            TestFramework.fail("Could not read from socket task", e);
            return null;
        }
    }
}
