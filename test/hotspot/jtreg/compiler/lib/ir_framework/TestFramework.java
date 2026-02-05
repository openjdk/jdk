/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework;

import compiler.lib.ir_framework.driver.FlagVMProcess;
import compiler.lib.ir_framework.driver.TestVMException;
import compiler.lib.ir_framework.driver.TestVMProcess;
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.parser.TestClassParser;
import compiler.lib.ir_framework.shared.*;
import compiler.lib.ir_framework.test.TestVM;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * To specify additional flags, use {@link #runWithFlags(String...)}, {@link #addFlags(String...)}, or
 * {@link #addScenarios(Scenario...)} where the scenarios can also be used to run different flag combinations
 * (instead of specifying multiple JTreg {@code @run} entries).
 * <p>
 * After annotating your test code with the framework specific annotations, the framework needs to be invoked from the
 * {@code main()} method of your JTreg test. There are two ways to do so. The first way is by calling the various
 * {@code runXX()} methods of {@link TestFramework}. The second way, which gives more control, is to create a new
 * {@code TestFramework} builder object on which {@link #start()} needs to be eventually called to start the testing.
 * <p>
 * The framework is called from the <i>Driver VM</i> in which the JTreg test is initially run by specifying {@code
 * @run driver} in the JTreg header. This strips all additionally specified JTreg VM and Javaoptions.
 * The framework creates a new <i>Flag VM</i> with all these flags added again in order to figure out which flags are
 * required to run the tests specified in the test class (e.g. {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly}
 * for IR matching).
 * <p>
 * After the Flag VM terminates, it starts a new <i>Test VM</i> which performs the execution of the specified
 * tests in the test class as described in {@link Test}, {@link Check}, and {@link Run}.
 * <p>
 * In a last step, once the Test VM has terminated without exceptions, IR matching is performed if there are any IR
 * rules and if no VM flags disable it (e.g. not running with {@code -Xint}, see {@link IR} for more details).
 * The IR regex matching is done on the output of {@code -XX:+PrintIdeal} and {@code -XX:+PrintOptoAssembly} by parsing
 * the hotspot_pid file of the Test VM. Failing IR rules are reported by throwing a {@link IRViolationException}.
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
     * <p>
     * A flag is whitelisted if it is a property flag (starting with -D), -ea, -esa, or if the flag name contains any of
     * the entries of this list as a substring (partial match).
     */
    public static final Set<String> JTREG_WHITELIST_FLAGS = new HashSet<>(
            Arrays.asList(
                    // The following substrings are part of more than one VM flag
                    "RAM",
                    "Heap",
                    "Trace",
                    "Print",
                    "Verify",
                    "UseNewCode",
                    "Xmn",
                    "Xms",
                    "Xmx",
                    "Xss",
                    // The following substrings are only part of one VM flag (=exact match)
                    "CreateCoredumpOnCrash",
                    "IgnoreUnrecognizedVMOptions",
                    "UnlockDiagnosticVMOptions",
                    "UnlockExperimentalVMOptions",
                    "BackgroundCompilation",
                    "Xbatch",
                    "TieredCompilation",
                    "CompileThreshold",
                    "Xmixed",
                    "server",
                    "AlignVector",
                    "UseAVX",
                    "UseSSE",
                    "UseSVE",
                    "Xlog",
                    "LogCompilation",
                    "UseCompactObjectHeaders",
                    "UseFMA",
                    // Riscv
                    "UseRVV",
                    "UseZbb",
                    "UseZfh",
                    "UseZicond",
                    "UseZvbb"
            )
    );

    public static final boolean VERBOSE = Boolean.getBoolean("Verbose");
    public static final boolean PRINT_RULE_MATCHING_TIME = Boolean.getBoolean("PrintRuleMatchingTime");
    public static final boolean TESTLIST = !System.getProperty("Test", "").isEmpty();
    public static final boolean EXCLUDELIST = !System.getProperty("Exclude", "").isEmpty();
    private static final boolean REPORT_STDOUT = Boolean.getBoolean("ReportStdout");
    // Only used for internal testing and should not be used for normal user testing.

    private static final String RERUN_HINT = """
                                               #############################################################
                                                - To only run the failed tests use -DTest, -DExclude,
                                                  and/or -DScenarios.
                                                - To also get the standard output of the Test VM run with
                                                  -DReportStdout=true or for even more fine-grained logging
                                                  use -DVerbose=true.
                                               #############################################################
                                             """ + System.lineSeparator();

    private boolean irVerificationPossible = Boolean.parseBoolean(System.getProperty("VerifyIR", "true"));
    private boolean shouldVerifyIR; // Should we perform IR matching?
    private static boolean toggleBool;

    private final Class<?> testClass;
    private Set<Class<?>> helperClasses;
    private List<Scenario> scenarios;
    private Set<Integer> scenarioIndices;
    private List<String> flags;
    private int defaultWarmup = -1;
    private boolean testClassesOnBootClassPath;
    private boolean allowNotCompilable = false;

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
        this(StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass());
    }

    /**
     * Creates an instance acting as a builder to test {@code testClass}.
     * Use this constructor if you want to use multiple run options (flags, helper classes, scenarios).
     * Use the associated add methods ({@link #addFlags(String...)}, {@link #addScenarios(Scenario...)},
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
     * Tests the class from which this method was invoked from. The Test VM is called with the specified {@code flags}.
     * <ul>
     *     <li><p>The {@code flags} override any set VM or Javaoptions flags by JTreg by default.<p>
     *            Use {@code -DPreferCommandLineFlags=true} if you want to prefer the JTreg VM and Javaoptions flags over
     *            the specified {@code flags} of this method.</li>
     *     <li><p>If you want to run your entire JTreg test with additional flags, use this method.</li>
     *     <li><p>If you want to run your entire JTreg test with additional flags but for another test class then the one
     *            from which this method was called from, use {@link #addFlags(String...)}, use this method.</li>
     *     <li><p>If you want to run your JTreg test with multiple flag combinations, use
     *            {@link #addScenarios(Scenario...)}</li>
     * </ul>
     *
     * @param flags VM flags to be used for the Test VM.
     */
    public static void runWithFlags(String... flags) {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        TestFramework framework = new TestFramework(walker.getCallerClass());
        framework.addFlags(flags);
        framework.start();
    }

    /**
     * Add VM flags to be used for the Test VM. These flags override any VM or Javaoptions set by JTreg by default.<p>
     * Use {@code -DPreferCommandLineFlags=true} if you want to prefer the VM or Javaoptions over the scenario flags.
     *
     * <p>
     * The testing can be started by invoking {@link #start()}
     *
     * @param flags VM options to be applied to the Test VM.
     * @return the same framework instance.
     */
    public TestFramework addFlags(String... flags) {
        TestRun.check(flags != null && Arrays.stream(flags).noneMatch(Objects::isNull), "A flag cannot be null");
        if (this.flags == null) {
            this.flags = new ArrayList<>();
        }
        this.flags.addAll(Arrays.asList(flags));
        return this;
    }

    /**
     * Add helper classes that can specify additional compile command annotations ({@link ForceCompile @ForceCompile},
     * {@link DontCompile @DontCompile}, {@link ForceInline @ForceInline}, {@link DontInline @DontInline}) to be applied
     * while testing {@code testClass} (also see description of {@link TestFramework}).
     *
     * <p>
     * Duplicates in {@code helperClasses} are ignored. If a class is used by the test class that does not specify any
     * compile command annotations, you do not need to include it with this method. If no helper class specifies any
     * compile commands, you do not need to call this method at all.
     *
     * <p>
     * The testing can be started by invoking {@link #start()}.
     *
     * @param helperClasses helper classes containing compile command annotations ({@link ForceCompile},
     *                      {@link DontCompile}, {@link ForceInline}, {@link DontInline}) to be applied
     *                      while testing {@code testClass} (also see description of {@link TestFramework}).
     * @return the same framework instance.
     */
    public TestFramework addHelperClasses(Class<?>... helperClasses) {
        TestRun.check(helperClasses != null && Arrays.stream(helperClasses).noneMatch(Objects::isNull),
                      "A Helper class cannot be null");
        if (this.helperClasses == null) {
            this.helperClasses = new HashSet<>();
        }

        this.helperClasses.addAll(Arrays.asList(helperClasses));
        return this;
    }

    /**
     * Add scenarios to be used for the Test VM. A Test VM is called for each scenario in {@code scenarios} by using the
     * specified VM flags in the scenario. The scenario flags override any flags set by {@link #addFlags(String...)}
     * and thus also override any VM or Javaoptions set by JTreg by default.<p>
     * Use {@code -DPreferCommandLineFlags=true} if you want to prefer the VM and Javaoptions over the scenario flags.
     *
     * <p>
     * The testing can be started by invoking {@link #start()}
     *
     * @param scenarios scenarios which specify specific flags for the Test VM.
     * @return the same framework instance.
     */
    public TestFramework addScenarios(Scenario... scenarios) {
        TestFormat.checkAndReport(scenarios != null && Arrays.stream(scenarios).noneMatch(Objects::isNull),
                                  "A scenario cannot be null");
        if (this.scenarios == null) {
            this.scenarios = new ArrayList<>();
            this.scenarioIndices = new HashSet<>();
        }

        for (Scenario scenario : scenarios) {
            int scenarioIndex = scenario.getIndex();
            if (!scenarioIndices.add(scenarioIndex)) {
                TestFormat.failNoThrow("Cannot define two scenarios with the same index " + scenarioIndex);
                continue;
            }
            this.scenarios.add(scenario);
        }
        TestFormat.throwIfAnyFailures();
        return this;
    }

    /**
     * Add the cross-product (cartesian product) of sets of flags as Scenarios. Unlike when constructing
     * scenarios directly a string can contain multiple flags separated with a space. This allows grouping
     * flags that have to be specified together. Further, an empty string in a set stands in for "no flag".
     * <p>
     * Passing a single set will create a scenario for each of the provided flags in the set (i.e. the same as
     * passing an additional set with an empty string only).
     * <p>
     * Example:
     * <pre>
     *     addCrossProductScenarios(Set.of("", "-Xint", "-Xbatch -XX:-TieredCompilation"),
     *                              Set.of("-XX:+UseNewCode", "-XX:UseNewCode2"))
     * </pre>
     *   produces the following Scenarios
     * <pre>
     *     Scenario(0, "-XX:+UseNewCode")
     *     Scenario(1, "-XX:+UseNewCode2")
     *     Scenario(2, "-Xint", "-XX:+UseNewCode")
     *     Scenario(3, "-Xint", "-XX:+UseNewCode2")
     *     Scenario(4, "-Xbatch -XX:-TieredCompilation", "-XX:+UseNewCode")
     *     Scenario(5, "-Xbatch -XX:-TieredCompilation", "-XX:+UseNewCode2")
     * </pre>
     *
     * @param flagSets sets of flags to generate the cross product for.
     * @return the same framework instance.
     */
    @SafeVarargs
    final public TestFramework addCrossProductScenarios(Set<String>... flagSets) {
        TestFormat.checkAndReport(flagSets != null &&
                                  Arrays.stream(flagSets).noneMatch(Objects::isNull) &&
                                  Arrays.stream(flagSets).flatMap(Set::stream).noneMatch(Objects::isNull),
                                  "Flags must not be null");
        if (flagSets.length == 0) {
            return this;
        }

        int initIdx = 0;
        if (this.scenarioIndices != null && !this.scenarioIndices.isEmpty()) {
            initIdx = this.scenarioIndices.stream().max(Comparator.comparingInt(Integer::intValue)).get() + 1;
        }
        AtomicInteger idx = new AtomicInteger(initIdx);

        Stream<List<String>> crossProduct = Arrays.stream(flagSets)
            .reduce(
                Stream.of(Collections.emptyList()), // Initialize Stream<List<String>> acc with a Stream containing an empty list of Strings.
                (Stream<List<String>> acc, Set<String> set) ->
                    acc.flatMap(lAcc -> // For each List<String>> lAcc in acc...
                        set.stream().map(flag -> { // ...and each flag in the current set...
                            List<String> newList = new ArrayList<>(lAcc); // ...create a new list containing lAcc...
                            newList.add(flag); // ...and append the flag.
                            return newList;
                        }) // This results in one List<List<String>> for each lAcc...
                    ), // ...that get flattened into one big List<List<String>>.
                Stream::concat); // combiner; if any reduction steps are executed in parallel, just concat two streams.

        Scenario[] newScenarios = crossProduct
            .map(flags -> new Scenario( // For each List<String> flags in crossProduct create a new Scenario.
                idx.getAndIncrement(),
                flags.stream() // Process flags
                     .map(s -> Set.of(s.split("[ ]"))) // Split multiple flags in the same string into separate strings.
                     .flatMap(Collection::stream) // Flatten the Stream<List<String>> into Stream<String>>.
                     .filter(s -> !s.isEmpty()) // Remove empty string flags.
                     .toList()
                     .toArray(new String[0])))
            .toList().toArray(new Scenario[0]);
        return addScenarios(newScenarios);
    }

    /**
     * Add test classes to boot classpath. This adds all classes found on path {@link jdk.test.lib.Utils#TEST_CLASSES}
     * to the boot classpath with "-Xbootclasspath/a". This is useful when trying to run tests in a privileged mode.
     */
    public TestFramework addTestClassesToBootClassPath() {
        this.testClassesOnBootClassPath = true;
        return this;
    }

    /**
     * Start the testing of the implicitly (by {@link #TestFramework()}) or explicitly (by {@link #TestFramework(Class)})
     * set test class.
     */
    public void start() {
        if (shouldInstallWhiteBox()) {
            installWhiteBox();
        }
        checkCompatibleFlags();
        checkIRRuleCompilePhasesFormat();
        disableIRVerificationIfNotFeasible();

        if (scenarios == null) {
            try {
                start(null);
            } catch (TestVMException e) {
                System.err.println(System.lineSeparator() + e.getExceptionInfo() + RERUN_HINT);
                throw e;
            } catch (IRViolationException e) {
                System.out.println(e.getCompilations());
                System.err.println(System.lineSeparator() + e.getExceptionInfo() + System.lineSeparator() + RERUN_HINT);
                throw e;
            }
        } else {
            startWithScenarios();
        }
    }

    private void checkIRRuleCompilePhasesFormat() {
        for (Method method : testClass.getDeclaredMethods()) {
            for (IR irAnno : method.getAnnotationsByType(IR.class)) {
                TestFormat.checkNoThrow(irAnno.phase().length > 0,
                                        "@IR rule " + irAnno + " must specify a non-empty list of compile " +
                                        "phases \"phase\" at " + method);
            }
        }
        TestFormat.throwIfAnyFailures();
    }

    /**
     * Try to load the Whitebox class from the user directory with a custom class loader. If the user has already built the
     * Whitebox, we can load it. Otherwise, the framework needs to install it.
     *
     * @return true if the framework needs to install the Whitebox
     */
    private boolean shouldInstallWhiteBox() {
        try {
            URL url = Path.of(System.getProperty("user.dir")).toUri().toURL();
            URLClassLoader userDirClassLoader =
                    URLClassLoader.newInstance(new URL[] {url}, TestFramework.class.getClassLoader().getParent());
            Class.forName(WhiteBox.class.getName(), false, userDirClassLoader);
        } catch (MalformedURLException e) {
            throw new TestFrameworkException("corrupted user.dir property", e);
        } catch (ClassNotFoundException e) {
            // We need to manually install the WhiteBox if we cannot load the WhiteBox class from the user directory.
            // This happens when the user test does not explicitly install the WhiteBox as part of the test.
            return true;
        }
        return false;
    }

    /**
     * Set a new default warm-up (overriding the framework default of 2000 at
     * {@link TestVM#WARMUP_ITERATIONS}) to be applied for all tests that do not specify an explicit
     * warm-up with {@link Warmup @Warmup}.
     *
     * @param defaultWarmup a new non-negative default warm-up.
     * @return the same framework instance.
     */
    public TestFramework setDefaultWarmup(int defaultWarmup) {
        TestFormat.checkAndReport(defaultWarmup >= 0, "Cannot specify a negative default warm-up");
        this.defaultWarmup = defaultWarmup;
        return this;
    }

    /**
     * In rare cases, methods may not be compilable because of a compilation bailout. By default, this leads to a
     * test failure. However, if such cases are expected in multiple methods in a test class, this flag can be set to
     * true, which allows any test to pass even if there is a compilation bailout. If only selected methods are prone
     * to bail out, it is preferred to use {@link Test#allowNotCompilable()} instead for more fine-grained control.
     * By setting this flag, any associated {@link IR} rule of a test is only executed if the test method was compiled,
     * and else it is ignored silently.
     */
    public TestFramework allowNotCompilable() {
        this.allowNotCompilable = true;
        return this;
    }

    /**
     * Get the VM output of the Test VM. Use {@code -DVerbose=true} to enable more debug information. If scenarios
     * were run, use {@link Scenario#getTestVMOutput()}.
     *
     * @return the last Test VM output.
     */
    public static String getLastTestVMOutput() {
        return TestVMProcess.getLastTestVMOutput();
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
        TestVM.compile(m, compLevel);
    }

    /**
     * Deoptimize {@code m}.
     *
     * @param m the method to be deoptimized.
     */
    public static void deoptimize(Method m) {
        TestVM.deoptimize(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled at any level.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled at any level;
     *         {@code false} otherwise.
     */
    public static boolean isCompiled(Method m) {
        return TestVM.isCompiled(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled with C1.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled with C1;
     *         {@code false} otherwise.
     */
    public static boolean isC1Compiled(Method m) {
        return TestVM.isC1Compiled(m);
    }

    /**
     * Returns a boolean indicating if {@code m} is compiled with C2.
     *
     * @param m the method to be checked.
     * @return {@code true} if {@code m} is compiled with C2;
     *         {@code false} otherwise.
     */
    public static boolean isC2Compiled(Method m) {
        return TestVM.isC2Compiled(m);
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
        return TestVM.isCompiledAtLevel(m, compLevel);
    }

    /**
     * Checks if {@code m} is compiled at any level.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is not compiled at any level.
     */
    public static void assertCompiled(Method m) {
        TestVM.assertCompiled(m);
    }

    /**
     * Checks if {@code m} is not compiled at any level.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is compiled at any level.
     */
    public static void assertNotCompiled(Method m) {
        TestVM.assertNotCompiled(m);
    }

    /**
     * Verifies that {@code m} is compiled with C1.
     *
     * @param m the method to be verified.
     * @throws TestRunException if {@code m} is not compiled with C1.
     */
    public static void assertCompiledByC1(Method m) {
        TestVM.assertCompiledByC1(m);
    }

    /**
     * Verifies that {@code m} is compiled with C2.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is not compiled with C2.
     */
    public static void assertCompiledByC2(Method m) {
        TestVM.assertCompiledByC2(m);
    }

    /**
     * Verifies that {@code m} is compiled at the specified {@code compLevel}.
     *
     * @param m the method to be checked.
     * @param compLevel the compilation level.
     * @throws TestRunException if {@code m} is not compiled at {@code compLevel}.
     */
    public static void assertCompiledAtLevel(Method m, CompLevel compLevel) {
        TestVM.assertCompiledAtLevel(m, compLevel);
    }

    /**
     * Verifies that {@code m} was deoptimized after being C1 compiled.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is was not deoptimized after being C1 compiled.
     */
    public static void assertDeoptimizedByC1(Method m) {
        TestVM.assertDeoptimizedByC1(m);
    }

    /**
     * Verifies that {@code m} was deoptimized after being C2 compiled.
     *
     * @param m the method to be checked.
     * @throws TestRunException if {@code m} is was not deoptimized after being C2 compiled.
     */
    public static void assertDeoptimizedByC2(Method m) {
        TestVM.assertDeoptimizedByC2(m);
    }

    /**
     * Returns a different boolean each time this method is invoked (switching between {@code false} and {@code true}).
     * The very first invocation returns {@code false}. Note that this method could be used by different tests and
     * thus the first invocation for a test could be {@code true} or {@code false} depending on how many times
     * other tests have already invoked this method.
     *
     * @return an inverted boolean of the result of the last invocation of this method.
     */
    public static boolean toggleBoolean() {
        toggleBool = !toggleBool;
        return toggleBool;
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
    private void disableIRVerificationIfNotFeasible() {
        if (!irVerificationPossible) {
            return;
        }

        boolean debugTest = Platform.isDebugBuild();
        boolean intTest = !Platform.isInt();
        boolean compTest = !Platform.isComp();
        boolean irTest = hasIRAnnotations();
        // No IR verification is done if additional non-whitelisted JTreg VM or Javaoptions flag is specified.
        List<String> nonWhiteListedFlags = anyNonWhitelistedJTregVMAndJavaOptsFlags();
        boolean nonWhiteListedTest = nonWhiteListedFlags.isEmpty();

        irVerificationPossible = debugTest && intTest && compTest && irTest && nonWhiteListedTest;
        if (irVerificationPossible) {
            return;
        }

        System.out.println("IR verification disabled due to the following reason(s):");
        if (!debugTest) {
            System.out.println("- Not running a debug build (required for PrintIdeal and PrintOptoAssembly)");
        }
        if (!intTest) {
            System.out.println("- Running with -Xint (no compilations)");
        }
        if (!compTest) {
            System.out.println("- Running with -Xcomp (use warm-up of 0 instead)");
        }
        if (!irTest) {
            System.out.println("- Test " + testClass + " not specifying any @IR annotations");
        }
        if (!nonWhiteListedTest) {
            System.out.println("- Using non-whitelisted JTreg VM or Javaoptions flag(s):");
            nonWhiteListedFlags.forEach((f) -> System.out.println("  - " + f));
        }

        System.out.println();
    }

    /**
     * For scenarios: Run the tests with the scenario settings and collect all exceptions to be able to run all
     * scenarios without prematurely throwing an exception. Format violations, however, are wrong for all scenarios
     * and thus is reported immediately on the first scenario execution.
     */
    private void startWithScenarios() {
        Map<Scenario, Exception> exceptionMap = new TreeMap<>(Comparator.comparingInt(Scenario::getIndex));
        for (Scenario scenario : scenarios) {
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
        builder.append(System.lineSeparator()).append(System.lineSeparator());
        for (Map.Entry<Scenario, Exception> entry : exceptionMap.entrySet()) {
            Exception e = entry.getValue();
            Scenario scenario = entry.getKey();
            String errorMsg = "";
            if (scenario != null) {
                errorMsg = getScenarioTitleAndFlags(scenario);
            }
            if (e instanceof IRViolationException irException) {
                // For IR violations, only show the actual violations and not the (uninteresting) stack trace.
                if (scenario != null) {
                    System.out.println("Scenario #" + scenario.getIndex());
                }
                System.out.println(irException.getCompilations());
                builder.append(errorMsg).append(System.lineSeparator()).append(irException.getExceptionInfo());
            } else if (e instanceof TestVMException testVMException) {
                builder.append(errorMsg).append(System.lineSeparator()).append(testVMException.getExceptionInfo());
            } else {
                // Print stack trace otherwise
                StringWriter errors = new StringWriter();
                e.printStackTrace(new PrintWriter(errors));
                builder.append(errors);
            }
            builder.append(System.lineSeparator());
        }
        System.err.println(builder);
        if (!VERBOSE && !REPORT_STDOUT && !TESTLIST && !EXCLUDELIST) {
            // Provide a hint to the user how to get additional output/debugging information.
            System.err.println(RERUN_HINT);
        }
        throw new TestRunException(failedScenarios + ". Please check stderr for more information.");
    }

    private static String getScenarioTitleAndFlags(Scenario scenario) {
        StringBuilder builder = new StringBuilder();
        String title = "Scenario #" + scenario.getIndex();
        builder.append(title).append(System.lineSeparator()).append("=".repeat(title.length()))
               .append(System.lineSeparator());
        builder.append("Scenario flags: [").append(String.join(", ", scenario.getFlags())).append("]")
               .append(System.lineSeparator());
        return builder.toString();
    }

    /**
     * Execute a separate Flag VM with White Box access to determine all Test VM flags. The Flag VM sends an encoding of
     * all required flags for the Test VM to the Driver VM over a socket. Once the Flag VM exits, this Driver VM parses the
     * Test VM flags, which also determine if IR matching should be done, and then starts the Test VM to execute all tests.
     */
    private void start(Scenario scenario) {
        if (scenario != null && !scenario.isEnabled()) {
            System.out.println("Disabled scenario #" + scenario.getIndex() + "! This scenario is not present in set flag " +
                               "-DScenarios and is therefore not executed.");
            return;
        }
        shouldVerifyIR = irVerificationPossible;
        try {
            // Use TestFramework flags and scenario flags for new VMs.
            List<String> additionalFlags = new ArrayList<>();
            if (flags != null) {
                additionalFlags.addAll(flags);
            }
            if (scenario != null) {
                List<String> scenarioFlags = scenario.getFlags();
                String scenarioFlagsString = scenarioFlags.isEmpty() ? "" : " - [" + String.join(", ", scenarioFlags) + "]";
                System.out.println("Scenario #" + scenario.getIndex() + scenarioFlagsString + ":");
                additionalFlags.addAll(scenarioFlags);
            }
            String frameworkAndScenarioFlags = additionalFlags.isEmpty() ?
                    "" : " - [" + String.join(", ", additionalFlags) + "]";

            if (shouldVerifyIR) {
                // Only need to use Flag VM if an IR verification is possibly done.
                System.out.println("Run Flag VM:");
                FlagVMProcess flagVMProcess = new FlagVMProcess(testClass, additionalFlags);
                shouldVerifyIR = flagVMProcess.shouldVerifyIR();
                if (shouldVerifyIR) {
                    // Add more flags for the Test VM which are required to do IR verification.
                    additionalFlags.addAll(flagVMProcess.getTestVMFlags());
                } // else: Flag VM found a reason to not do IR verification.
            } else {
                System.out.println("Skip Flag VM due to not performing IR verification.");
            }

            System.out.println("Run Test VM" + frameworkAndScenarioFlags + ":");
            runTestVM(additionalFlags);
        } finally {
            if (scenario != null) {
                scenario.setTestVMOutput(TestVMProcess.getLastTestVMOutput());
            }
            System.out.println();
        }
    }

    private boolean hasIRAnnotations() {
        return Arrays.stream(testClass.getDeclaredMethods()).anyMatch(m -> m.getAnnotationsByType(IR.class).length > 0);
    }

    private void checkCompatibleFlags() {
        for (String flag : Utils.getTestJavaOpts()) {
            if (flag.contains("-agentpath")) {
                throw new SkippedException("Can't run test with agent.");
            }
        }
    }

    private List<String> anyNonWhitelistedJTregVMAndJavaOptsFlags() {
        List<String> flags = Arrays.stream(Utils.getTestJavaOpts())
                                   .map(s -> s.replaceFirst("-XX:[+|-]?|-(?=[^D|^e])", ""))
                                   .toList();
        List<String> nonWhiteListedFlags = new ArrayList<>();
        for (String flag : flags) {
            if (flag.contains("agentpath")) {
                throw new SkippedException("Can't run test with -javaagent");
            }
            // Property flags (prefix -D), -ea and -esa are whitelisted.
            if (!flag.startsWith("-D") && !flag.startsWith("-e") && JTREG_WHITELIST_FLAGS.stream().noneMatch(flag::contains)) {
                // Found VM flag that is not whitelisted
                nonWhiteListedFlags.add(flag);
            }
        }
        return nonWhiteListedFlags;
    }

    private void runTestVM(List<String> additionalFlags) {
        TestVMProcess testVMProcess = new TestVMProcess(additionalFlags, testClass, helperClasses, defaultWarmup,
                                                        allowNotCompilable, testClassesOnBootClassPath);
        if (shouldVerifyIR) {
            try {
                TestClassParser testClassParser = new TestClassParser(testClass, allowNotCompilable);
                Matchable testClassMatchable = testClassParser.parse(testVMProcess.getHotspotPidFileName(),
                                                                     testVMProcess.getApplicableIRRules());
                IRMatcher matcher = new IRMatcher(testClassMatchable);
                matcher.match();
            } catch (IRViolationException e) {
                e.addCommandLine(testVMProcess.getCommandLine());
                throw e;
            }
        } else {
            System.out.println("IR verification disabled either due to no @IR annotations, through explicitly setting " +
                               "-DVerify=false, due to not running a debug build, using a non-whitelisted JTreg VM or " +
                               "Javaopts flag like -Xint, or running the Test VM with other VM flags added by user code " +
                               "that make the IR verification impossible (e.g. -XX:-UseCompile, " +
                               "-XX:TieredStopAtLevel=[1,2,3], etc.).");
        }
    }

    public static void check(boolean test, String failureMessage) {
        if (!test) {
            throw new TestFrameworkException(failureMessage);
        }
    }
}
