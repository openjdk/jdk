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
import sun.hotspot.WhiteBox;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class' main method is called from {@link TestFramework} and represents the so-called "test VM". The class is
 * the heart of the framework and is responsible for executing all the specified tests in the test class. It uses the
 * Whitebox API and reflection to achieve this task.
 */
public class TestFrameworkExecution {
    private static final WhiteBox WHITE_BOX;

    static {
        try {
            WHITE_BOX = WhiteBox.getWhiteBox();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("\n" + """
                               ##########################################################
                                - Did you call a test-related interface method from
                                  TestFramework in main() of your test? Make sure to
                                  only call setup/run methods and no checks or
                                  assertions from main() of your test!
                                - Are you rerunning the test VM (TestFrameworkExecution)
                                  directly after a JTreg run? Make sure to start it
                                  from within JTwork/scratch and with the flag
                                  -DReproduce=true!
                               ##########################################################
                               """);
            throw e;
        }
    }

    /**
     * The default number of warm-up iterations used to warm up a {@link Test} annotated test method.
     * Use {@code -DWarmup=XY} to specify a different default value. An individual warm-up can also be
     * set by specifying a {@link Warmup} iteration for a test.
     */
    public static final int WARMUP_ITERATIONS = Integer.parseInt(System.getProperty("Warmup", "2000"));

    private static final boolean TIERED_COMPILATION = (Boolean)WHITE_BOX.getVMFlag("TieredCompilation");
    private static final CompLevel TIERED_COMPILATION_STOP_AT_LEVEL = CompLevel.forValue(((Long)WHITE_BOX.getVMFlag("TieredStopAtLevel")).intValue());
    static final boolean TEST_C1 = TIERED_COMPILATION && TIERED_COMPILATION_STOP_AT_LEVEL.getValue() < CompLevel.C2.getValue();

    // User defined settings
    static final boolean XCOMP = Platform.isComp();
    static final boolean VERBOSE = Boolean.getBoolean("Verbose");
    private static final boolean PRINT_TIMES = Boolean.getBoolean("PrintTimes");

    static final boolean USE_COMPILER = WHITE_BOX.getBooleanVMFlag("UseCompiler");
    static final boolean EXCLUDE_RANDOM = Boolean.getBoolean("ExcludeRandom");
    private static final String TESTLIST = System.getProperty("Test", "");
    private static final String EXCLUDELIST = System.getProperty("Exclude", "");
    private static final boolean DUMP_REPLAY = Boolean.getBoolean("DumpReplay");
    private static final boolean GC_AFTER = Boolean.getBoolean("GCAfter");
    private static final boolean SHUFFLE_TESTS = Boolean.parseBoolean(System.getProperty("ShuffleTests", "true"));
    // Use separate flag as VERIFY_IR could have been set by user but due to other flags it was disabled by flag VM.
    private static final boolean PRINT_VALID_IR_RULES = Boolean.getBoolean("ShouldDoIRVerification");
    protected static final long PerMethodTrapLimit = (Long)WHITE_BOX.getVMFlag("PerMethodTrapLimit");
    protected static final boolean ProfileInterpreter = (Boolean)WHITE_BOX.getVMFlag("ProfileInterpreter");
    private static final boolean FLIP_C1_C2 = Boolean.getBoolean("FlipC1C2");

    private final HashMap<Method, DeclaredTest> declaredTests = new HashMap<>();
    private final List<AbstractTest> allTests = new ArrayList<>();
    private final HashMap<String, Method> testMethodMap = new HashMap<>();
    private final List<String> excludeList;
    private final List<String> testList;
    private Set<Class<?>> helperClasses = null; // Helper classes that contain framework annotations to be processed.
    private final IREncodingPrinter irMatchRulePrinter;
    private final Class<?> testClass;
    private final Map<Executable, CompLevel> forceCompileMap = new HashMap<>();

    private TestFrameworkExecution(Class<?> testClass) {
        TestRun.check(testClass != null, "Test class cannot be null");
        this.testClass = testClass;
        this.testList = createTestFilterList(TESTLIST, testClass);
        this.excludeList = createTestFilterList(EXCLUDELIST, testClass);

        if (PRINT_VALID_IR_RULES) {
            irMatchRulePrinter = new IREncodingPrinter();
        } else {
            irMatchRulePrinter = null;
        }
    }

    /**
     * Parse "test1,test2,test3" into a list.
     */
    private static List<String> createTestFilterList(String list, Class<?> testClass) {
        List<String> filterList = null;
        if (!list.isEmpty()) {
            String classPrefix = testClass.getSimpleName() + ".";
            filterList = new ArrayList<>(Arrays.asList(list.split(",")));
            for (int i = filterList.size() - 1; i >= 0; i--) {
                String test = filterList.get(i);
                if (test.indexOf(".") > 0) {
                    if (test.startsWith(classPrefix)) {
                        test = test.substring(classPrefix.length());
                        filterList.set(i, test);
                    } else {
                        filterList.remove(i);
                    }
                }
            }
        }
        return filterList;
    }

    /**
     * Main entry point of the test VM.
     */
    public static void main(String[] args) {
        try {
            String testClassName = args[0];
            System.out.println("Framework main(), about to run tests in class " + testClassName);
            Class<?> testClass;
            testClass = getClassObject(testClassName, "test");

            TestFrameworkExecution framework = new TestFrameworkExecution(testClass);
            framework.addHelperClasses(args);
            framework.start();
        } finally {
            TestFrameworkSocket.closeClientSocket();
        }
    }

    protected static Class<?> getClassObject(String className, String classType) {
        Class<?> c;
        try {
            c = Class.forName(className);
        } catch (Exception e) {
            TestRun.fail("Could not find " + classType + " class", e);
            return null;
        }
        return c;
    }

    /**
     * Set up all helper classes and verify they are specified correctly.
     */
    private void addHelperClasses(String[] args) {
        Class<?>[] helperClasses = getHelperClasses(args);
        if (helperClasses != null) {
            TestRun.check(Arrays.stream(helperClasses).noneMatch(Objects::isNull), "A Helper class cannot be null");
            this.helperClasses = new HashSet<>();

            for (Class<?> helperClass : helperClasses) {
                if (Arrays.stream(testClass.getDeclaredClasses()).anyMatch(c -> c == helperClass)) {
                    // Nested class of test class is automatically treated as helper class
                    TestFormat.failNoThrow("Nested " + helperClass + " inside test " + testClass + " is implicitly"
                                           + " treated as helper class and does not need to be specified as such.");
                    continue;
                }
                TestRun.check(!this.helperClasses.contains(helperClass), "Cannot add the same class twice: " + helperClass);
                this.helperClasses.add(helperClass);
            }
        }
    }

    private static Class<?>[] getHelperClasses(String[] args) {
        if (args.length == 1) {
            return null;
        }
        Class<?>[] helperClasses = new Class<?>[args.length - 1]; // First argument is test class
        for (int i = 1; i < args.length; i++) {
            String helperClassName = args[i];
            helperClasses[i - 1] = getClassObject(helperClassName, "helper");
        }
        return helperClasses;
    }

    private void checkHelperClass(Class<?> clazz) {
        checkAnnotationsInClass(clazz, "helper");
        for (Class<?> c : clazz.getDeclaredClasses()) {
            checkAnnotationsInClass(c, "nested (and helper)");
        }
    }

    private void checkAnnotationsInClass(Class<?> c, String clazzType) {
        Method[] methods = c.getDeclaredMethods();
        for (Method m : methods) {
            TestFormat.checkNoThrow(getAnnotation(m, Test.class) == null,
                                    "Cannot use @Test annotation in " + clazzType + " " + c + " at " + m);
            TestFormat.checkNoThrow(getAnnotation(m, Run.class) == null,
                                    "Cannot use @Run annotation in " + clazzType + " " + c + " at " + m);
            TestFormat.checkNoThrow(getAnnotation(m, Check.class) == null,
                                    "Cannot use @Check annotation in " + clazzType + " " + c + " at " + m);
        }
    }

    /**
     * Only called by internal tests testing the framework itself. Accessed by reflection. Not exposed to normal users.
     */
    private static void runTestsOnSameVM(Class<?> testClass) {
        if (testClass == null) {
            StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
            testClass = walker.getCallerClass();
        }
        TestFrameworkExecution framework = new TestFrameworkExecution(testClass);
        framework.start();
    }

    /**
     * Once everything is initialized and set up, start collecting tests and executing them afterwards.
     */
    private void start() {
        setupTests();
        checkForcedCompilationsCompleted();
        runTests();
    }

    private void setupTests() {
        for (Class<?> clazz : testClass.getDeclaredClasses()) {
            checkAnnotationsInClass(clazz, "inner");
        }
        if (DUMP_REPLAY) {
            addReplay();
        }
        // Make sure to first setup test methods and make them non-inlineable and only then process compile commands.
        setupDeclaredTests();
        processControlAnnotations(testClass);
        processHelperClasses();
        setupCheckedAndCustomRunTests();

        // All remaining tests are simple base tests without check or specific way to run them.
        addBaseTests();
        if (PRINT_VALID_IR_RULES) {
            irMatchRulePrinter.emit();
        }
        TestFormat.reportIfAnyFailures();
        declaredTests.clear();
        testMethodMap.clear();
    }

    private void addBaseTests() {
        declaredTests.forEach((m, test) -> {
            if (test.getAttachedMethod() == null) {
                try {
                    Arguments argumentsAnno = getAnnotation(m, Arguments.class);
                    TestFormat.check(argumentsAnno != null || m.getParameterCount() == 0, "Missing @Arguments annotation to define arguments of " + m);
                    BaseTest baseTest = new BaseTest(test, shouldExcludeTest(m.getName()));
                    allTests.add(baseTest);
                    if (PRINT_VALID_IR_RULES) {
                       irMatchRulePrinter.emitRuleEncoding(m, baseTest.isSkipped());
                    }
                } catch (TestFormatException e) {
                    // Failure logged. Continue and report later.
                }
            }
        });
    }

    /**
     * Check if user wants to exclude this test by checking the -DTest and -DExclude lists.
     */
    private boolean shouldExcludeTest(String testName) {
        boolean hasTestList = testList != null;
        boolean hasExcludeList = excludeList != null;
        if (hasTestList) {
            return !testList.contains(testName) || (hasExcludeList && excludeList.contains(testName));
        } else if (hasExcludeList) {
            return excludeList.contains(testName);
        }
        return false;
    }

    /**
     * Generate replay compilation files.
     */
    private void addReplay() {
        String directive = "[{ match: \"*.*\", DumpReplay: true }]";
        TestFramework.check(WHITE_BOX.addCompilerDirective(directive) == 1, "Failed to add DUMP_REPLAY directive");
    }

    private void processControlAnnotations(Class<?> clazz) {
        if (!XCOMP) {
            // Don't control compilations if -Xcomp is enabled.
            // Also apply compile commands to all inner classes of 'clazz'.
            ArrayList<Class<?>> classes = new ArrayList<>(Arrays.asList(clazz.getDeclaredClasses()));
            classes.add(clazz);
            for (Class<?> c : classes) {
                applyClassAnnotations(c);
                List<Executable> executables = new ArrayList<>(Arrays.asList(c.getDeclaredMethods()));
                Collections.addAll(executables, c.getDeclaredConstructors());
                for (Executable ex : executables) {
                    checkClassAnnotations(ex);
                    try {
                        applyIndependentCompilationCommands(ex);
                    } catch (TestFormatException e) {
                        // Failure logged. Continue and report later.
                    }
                }

                // Only force compilation now because above annotations affect inlining
                for (Executable ex : executables) {
                    try {
                        applyForceCompileCommand(ex);
                    } catch (TestFormatException e) {
                        // Failure logged. Continue and report later.
                    }
                }
            }
        }
    }

    private void applyClassAnnotations(Class<?> c) {
        ForceCompileClassInitializer anno = getAnnotation(c, ForceCompileClassInitializer.class);
        if (anno != null) {
            // Compile class initializer
            CompLevel level = anno.value();
            if (level == CompLevel.SKIP || level == CompLevel.WAIT_FOR_COMPILATION) {
                TestFormat.failNoThrow("Cannot define compilation level SKIP or WAIT_FOR_COMPILATION in " +
                                       "@ForceCompileClassInitializer at " + c);
                return;
            }
            level = restrictCompLevel(anno.value());
            if (level != CompLevel.SKIP) {
                // Make sure class is initialized to avoid compilation bailout of <clinit>
                getClassObject(c.getName(), "nested"); // calls Class.forName() to initialize 'c'
                TestFormat.checkNoThrow(WHITE_BOX.enqueueInitializerForCompilation(c, level.getValue()),
                                        "Failed to enqueue <clinit> of " + c + " for compilation. Did you specify "
                                        + "@ForceCompileClassInitializer without providing a static class initialization? "
                                        + "Make sure to provide any form of static initialization or remove the annotation.");
            }
        }
    }

    private void checkClassAnnotations(Executable ex) {
        TestFormat.checkNoThrow(getAnnotation(ex, ForceCompileClassInitializer.class) == null,
                                "@ForceCompileClassInitializer only allowed at classes but not at method " + ex);
    }

    /**
     * Exclude a method from compilation randomly and returns the compilation level on which a compilation is still
     * possible.
     */
    static CompLevel excludeRandomly(Executable ex) {
        Random random = Utils.getRandomInstance();
        boolean exclude = random.nextBoolean();
        CompLevel level = CompLevel.ANY;
        if (exclude) {
            String levelName;
            switch (random.nextInt() % 3) {
                case 1 -> {
                    level = CompLevel.C1;
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.C2.getValue(), false);
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.C2.getValue(), true);
                    levelName = "C2";
                }
                case 2 -> {
                    level = CompLevel.C2;
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.C1.getValue(), false);
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.C1.getValue(), true);
                    levelName = "C1";
                }
                default -> {
                    level = CompLevel.SKIP;
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.ANY.getValue(), false);
                    WHITE_BOX.makeMethodNotCompilable(ex, CompLevel.ANY.getValue(), true);
                    levelName = "C1 and C2";
                }
            }
            System.out.println("Excluding from " + levelName + " compilation: " + ex);
        }
        return level;
    }

    private void applyIndependentCompilationCommands(Executable ex) {
        ForceInline forceInlineAnno = getAnnotation(ex, ForceInline.class);
        DontInline dontInlineAnno = getAnnotation(ex, DontInline.class);
        ForceCompile forceCompileAnno = getAnnotation(ex, ForceCompile.class);
        DontCompile dontCompileAnno = getAnnotation(ex, DontCompile.class);
        checkCompilationCommandAnnotations(ex, forceInlineAnno, dontInlineAnno, forceCompileAnno, dontCompileAnno);
        // First handle inline annotations
        if (dontInlineAnno != null) {
            WHITE_BOX.testSetDontInlineMethod(ex, true);
        } else if (forceInlineAnno != null) {
            WHITE_BOX.testSetForceInlineMethod(ex, true);
        }
        if (dontCompileAnno != null) {
            CompLevel compLevel = dontCompileAnno.value();
            TestFormat.check(compLevel == CompLevel.C1 || compLevel == CompLevel.C2 || compLevel == CompLevel.ANY,
                             "Can only specify compilation level C1 (no individual C1 levels), " +
                             "C2 or ANY (no compilation, same as specifying anything) in @DontCompile at " + ex);
            dontCompileAtLevel(ex, compLevel);
        }
        if (EXCLUDE_RANDOM && getAnnotation(ex, Test.class) == null && forceCompileAnno == null && dontCompileAnno == null) {
            // Randomly exclude helper methods from compilation
            excludeRandomly(ex);
        }
    }

    private void checkCompilationCommandAnnotations(Executable ex, ForceInline forceInlineAnno, DontInline dontInlineAnno, ForceCompile forceCompileAnno, DontCompile dontCompileAnno) {
        Test testAnno = getAnnotation(ex, Test.class);
        Run runAnno = getAnnotation(ex, Run.class);
        Check checkAnno = getAnnotation(ex, Check.class);
        TestFormat.check((testAnno == null && runAnno == null && checkAnno == null) || Stream.of(forceCompileAnno, dontCompileAnno, forceInlineAnno, dontInlineAnno).noneMatch(Objects::nonNull),
                         "Cannot use explicit compile command annotations (@ForceInline, @DontInline," +
                         "@ForceCompile or @DontCompile) together with @Test, @Check or @Run: " + ex + ". Use compLevel in @Test for fine tuning.");
        if (Stream.of(forceInlineAnno, dontCompileAnno, dontInlineAnno).filter(Objects::nonNull).count() > 1) {
            // Failure
            TestFormat.check(dontCompileAnno == null || dontInlineAnno == null,
                             "@DontInline is implicitely done with @DontCompile annotation at " + ex);
            TestFormat.fail("Cannot mix @ForceInline, @DontInline and @DontCompile at the same time at " + ex);
        }
        TestFormat.check(forceInlineAnno == null || dontInlineAnno == null, "Cannot have @ForceInline and @DontInline at the same time at " + ex);
        if (forceCompileAnno != null && dontCompileAnno != null) {
            CompLevel forceCompile = forceCompileAnno.value();
            CompLevel dontCompile = dontCompileAnno.value();
            TestFormat.check(dontCompile != CompLevel.ANY,
                             "Cannot have @DontCompile(CompLevel.ANY) and @ForceCompile at the same time at " + ex);
            TestFormat.check(forceCompile != CompLevel.ANY,
                             "Cannot have @ForceCompile(CompLevel.ANY) and @DontCompile at the same time at " + ex);
            TestFormat.check(!CompLevel.overlapping(dontCompile, forceCompile),
                             "Overlapping compilation levels with @ForceCompile and @DontCompile at " + ex);
        }
    }

    /**
     * Exlude the method from compilation and make sure it is not inlined.
     */
    private void dontCompileAndDontInlineMethod(Method m) {
        WHITE_BOX.makeMethodNotCompilable(m, CompLevel.ANY.getValue(), true);
        WHITE_BOX.makeMethodNotCompilable(m, CompLevel.ANY.getValue(), false);
        WHITE_BOX.testSetDontInlineMethod(m, true);
    }

    private void dontCompileAtLevel(Executable ex, CompLevel compLevel) {
        if (VERBOSE) {
            System.out.println("dontCompileAtLevel " + ex + " , level = " + compLevel.name());
        }
        WHITE_BOX.makeMethodNotCompilable(ex, compLevel.getValue(), true);
        WHITE_BOX.makeMethodNotCompilable(ex, compLevel.getValue(), false);
        if (compLevel == CompLevel.ANY) {
            WHITE_BOX.testSetDontInlineMethod(ex, true);
        }
    }

    private void applyForceCompileCommand(Executable ex) {
        ForceCompile forceCompileAnno = getAnnotation(ex, ForceCompile.class);
        if (forceCompileAnno != null) {
            CompLevel complevel = forceCompileAnno.value();
            TestFormat.check(complevel != CompLevel.SKIP && complevel != CompLevel.WAIT_FOR_COMPILATION,
                             "Cannot define compilation level SKIP or WAIT_FOR_COMPILATION in @ForceCompile at " + ex);
            complevel = restrictCompLevel(forceCompileAnno.value());
            if (EXCLUDE_RANDOM) {
                complevel = CompLevel.join(complevel, excludeRandomly(ex));
            }
            if (FLIP_C1_C2) {
                complevel = flipCompLevel(complevel);
            }
            if (complevel != CompLevel.SKIP) {
                enqueueForCompilation(ex, complevel);
                forceCompileMap.put(ex, complevel);
            }
        }
    }

    static void enqueueForCompilation(Executable ex, CompLevel requestedCompLevel) {
        if (TestFrameworkExecution.VERBOSE) {
            System.out.println("enqueueForCompilation " + ex + ", level = " + requestedCompLevel);
        }
        CompLevel compLevel = restrictCompLevel(requestedCompLevel);
        if (compLevel != CompLevel.SKIP) {
            WHITE_BOX.enqueueMethodForCompilation(ex, compLevel.getValue());
        } else {
            System.out.println("Skipped compilation on level " + requestedCompLevel + " due to VM flags not allowing it.");
        }
    }

    /**
     * Setup @Test annotated method an add them to the declaredTests map to have a convenient way of accessing them
     * once setting up a framework test (base  checked, or custom run test).
     */
    private void setupDeclaredTests() {
        for (Method m : testClass.getDeclaredMethods()) {
            Test testAnno = getAnnotation(m, Test.class);
            try {
                if (testAnno != null) {
                    addDeclaredTest(m);
                } else {
                    TestFormat.checkNoThrow(!m.isAnnotationPresent(IR.class), "Found @IR annotation on non-@Test method " + m);
                    TestFormat.checkNoThrow(!m.isAnnotationPresent(Warmup.class) || getAnnotation(m, Run.class) != null,
                                     "Found @Warmup annotation on non-@Test or non-@Run method " + m);
                }
            } catch (TestFormatException e) {
                // Failure logged. Continue and report later.
            }
        }
        TestFormat.checkNoThrow(!declaredTests.isEmpty(), "Did not specify any @Test methods in " + testClass);
    }

    private void addDeclaredTest(Method m) {
        Test testAnno = getAnnotation(m, Test.class);
        checkTestAnnotations(m, testAnno);
        Warmup warmup = getAnnotation(m, Warmup.class);
        int warmupIterations = WARMUP_ITERATIONS;
        if (warmup != null) {
            warmupIterations = warmup.value();
            TestFormat.checkNoThrow(warmupIterations >= 0, "Cannot have negative value for @Warmup at " + m);
        }

        if (!XCOMP) {
            // Don't inline test methods. Don't care when -Xcomp set.
            WHITE_BOX.testSetDontInlineMethod(m, true);
        }
        CompLevel compLevel = restrictCompLevel(testAnno.compLevel());
        if (FLIP_C1_C2) {
            compLevel = flipCompLevel(compLevel);
        }
        if (EXCLUDE_RANDOM) {
            compLevel = CompLevel.join(compLevel, excludeRandomly(m));
        }
        DeclaredTest test = new DeclaredTest(m, ArgumentValue.getArguments(m), compLevel, warmupIterations);
        declaredTests.put(m, test);
        testMethodMap.put(m.getName(), m);
    }

    private void checkTestAnnotations(Method m, Test testAnno) {
        TestFormat.check(!testMethodMap.containsKey(m.getName()),
                         "Cannot overload two @Test methods: " + m + ", " + testMethodMap.get(m.getName()));
        TestFormat.check(testAnno != null, m + " must be a method with a @Test annotation");

        Check checkAnno = getAnnotation(m, Check.class);
        Run runAnno = getAnnotation(m, Run.class);
        TestFormat.check(checkAnno == null && runAnno == null,
                         m + " has invalid @Check or @Run annotation while @Test annotation is present.");

        TestFormat.checkNoThrow(Arrays.stream(m.getParameterTypes()).noneMatch(AbstractInfo.class::isAssignableFrom),
                                "Cannot " + AbstractInfo.class + " or any of its subclasses as parameter type at " +
                                "@Test method " + m);

        TestFormat.checkNoThrow(!AbstractInfo.class.isAssignableFrom(m.getReturnType()),
                                "Cannot " + AbstractInfo.class + " or any of its subclasses as return type at " +
                                "@Test method " + m);
    }


    /**
     * Get the appropriate level as permitted by the test scenario and VM flags.
     */
    private static CompLevel restrictCompLevel(CompLevel compLevel) {
        if (!USE_COMPILER) {
            return CompLevel.SKIP;
        }
        if (compLevel == CompLevel.ANY) {
            // Use highest available compilation level by default (usually C2).
            compLevel = TIERED_COMPILATION_STOP_AT_LEVEL;
        }
        if (!TIERED_COMPILATION && compLevel.getValue() < CompLevel.C2.getValue()) {
            return CompLevel.SKIP;
        }
        if (TIERED_COMPILATION && compLevel.getValue() > TIERED_COMPILATION_STOP_AT_LEVEL.getValue()) {
            return CompLevel.SKIP;
        }
        return compLevel;
    }

    private static CompLevel flipCompLevel(CompLevel compLevel) {
        switch (compLevel) {
            case C1, C1_LIMITED_PROFILE, C1_FULL_PROFILE -> {
                return CompLevel.C2;
            }
            case C2 -> {
                return CompLevel.C1;
            }
        }
        return compLevel;
    }

    /**
     * Verify that the helper classes do not contain illegal framework annotations and then apply the actions as
     * specified by the different helper class annotations.
     */
    private void processHelperClasses() {
        if (helperClasses != null) {
            for (Class<?> helperClass : helperClasses) {
                // Process the helper classes and apply the explicit compile commands
                TestFormat.checkNoThrow(helperClass != testClass,
                                        "Cannot specify test " + testClass + " as helper class, too.");
                checkHelperClass(helperClass);
                processControlAnnotations(helperClass);
            }
        }
    }

    /**
     * First set up checked (with @Check) and custom run tests (with @Run). All remaining unmatched/unused @Test methods
     * are treated as base tests and set up as such later.
     */
    private void setupCheckedAndCustomRunTests() {
        for (Method m : testClass.getDeclaredMethods()) {
            Check checkAnno = getAnnotation(m, Check.class);
            Run runAnno = getAnnotation(m, Run.class);
            Arguments argumentsAnno = getAnnotation(m, Arguments.class);
            try {
                TestFormat.check(argumentsAnno == null || (checkAnno == null && runAnno == null),
                                 "Cannot have @Argument annotation in combination with @Run or @Check at " + m);
                if (checkAnno != null) {
                    addCheckedTest(m, checkAnno, runAnno);
                } else if (runAnno != null) {
                    addCustomRunTest(m, runAnno);
                }
            } catch (TestFormatException e) {
                // Failure logged. Continue and report later.
            }
        }
    }

    /**
     * Set up a checked test by first verifying the correct format of the @Test and @Check method and then adding it
     * to the allTests list which keeps track of all framework tests that are eventually executed.
     */
    private void addCheckedTest(Method m, Check checkAnno, Run runAnno) {
        Method testMethod = testMethodMap.get(checkAnno.test());
        DeclaredTest test = declaredTests.get(testMethod);
        checkCheckedTest(m, checkAnno, runAnno, testMethod, test);
        test.setAttachedMethod(m);
        CheckedTest.Parameter parameter = getCheckedTestParameter(m, testMethod);
        dontCompileAndDontInlineMethod(m);
        CheckedTest checkedTest = new CheckedTest(test, m, checkAnno, parameter, shouldExcludeTest(testMethod.getName()));
        allTests.add(checkedTest);
        if (PRINT_VALID_IR_RULES) {
            // Only need to emit IR verification information if IR verification is actually performed.
            irMatchRulePrinter.emitRuleEncoding(m, checkedTest.isSkipped());
        }
    }

    private void checkCheckedTest(Method m, Check checkAnno, Run runAnno, Method testMethod, DeclaredTest test) {
        TestFormat.check(runAnno == null, m + " has invalid @Run annotation while @Check annotation is present.");
        TestFormat.check(testMethod != null, "Did not find associated test method \"" + m.getDeclaringClass().getName()
                                             + "." + checkAnno.test() + "\" for @Check at " + m);
        TestFormat.check(test != null, "Missing @Test annotation for associated test method " + testMethod + " for @Check at " + m);
        Method attachedMethod = test.getAttachedMethod();
        TestFormat.check(attachedMethod == null,
                         "Cannot use @Test " + testMethod + " for more than one @Run or one @Check method. Found: " + m + ", " + attachedMethod);
    }

    /**
     * Only allow parameters as specified in {@link Check}.
     */
    private CheckedTest.Parameter getCheckedTestParameter(Method m, Method testMethod) {
        boolean firstParameterTestInfo = m.getParameterCount() > 0 && m.getParameterTypes()[0].equals(TestInfo.class);
        boolean secondParameterTestInfo = m.getParameterCount() > 1 && m.getParameterTypes()[1].equals(TestInfo.class);
        CheckedTest.Parameter parameter = null;
        Class<?> testReturnType = testMethod.getReturnType();
        switch (m.getParameterCount()) {
            case 0 -> parameter = CheckedTest.Parameter.NONE;
            case 1 -> {
                TestFormat.checkNoThrow(firstParameterTestInfo || m.getParameterTypes()[0] == testReturnType,
                                        "Single-parameter version of @Check method " + m + " must match return type of @Test " + testMethod);
                parameter = firstParameterTestInfo ? CheckedTest.Parameter.TEST_INFO_ONLY : CheckedTest.Parameter.RETURN_ONLY;
            }
            case 2 -> {
                TestFormat.checkNoThrow(m.getParameterTypes()[0] == testReturnType && secondParameterTestInfo,
                                        "Two-parameter version of @Check method " + m + " must provide as first parameter the same"
                                        + " return type as @Test method " + testMethod + " and as second parameter an object of " + TestInfo.class);
                parameter = CheckedTest.Parameter.BOTH;
            }
            default -> TestFormat.failNoThrow("@Check method " + m + " must provide either a none, single or two-parameter variant.");
        }
        return parameter;
    }

    /**
     * Set up a custom run test by first verifying the correct format of the @Test and @Run method and then adding it
     * to the allTests list which keeps track of all framework tests that are eventually executed.
     */
    private void addCustomRunTest(Method m, Run runAnno) {
        checkRunMethod(m, runAnno);
        List<DeclaredTest> tests = new ArrayList<>();
        boolean shouldExcludeTest = true;
        for (String testName : runAnno.test()) {
            try {
                Method testMethod = testMethodMap.get(testName);
                DeclaredTest test = declaredTests.get(testMethod);
                checkCustomRunTest(m, testName, testMethod, test, runAnno.mode());
                test.setAttachedMethod(m);
                tests.add(test);
                // Only exclude custom run test if all test methods excluded
                shouldExcludeTest &= shouldExcludeTest(testMethod.getName());
            } catch (TestFormatException e) {
                // Logged, continue.
            }
        }
        if (tests.isEmpty()) {
            return; // There was a format violation. Return.
        }
        dontCompileAndDontInlineMethod(m);
        CustomRunTest customRunTest = new CustomRunTest(m, getAnnotation(m, Warmup.class), runAnno, tests, shouldExcludeTest);
        allTests.add(customRunTest);
        if (PRINT_VALID_IR_RULES) {
            tests.forEach(test -> irMatchRulePrinter.emitRuleEncoding(test.getTestMethod(), customRunTest.isSkipped()));
        }
    }

    /**
     * Only allow parameters as specified in {@link Run}.
     */
    private void checkCustomRunTest(Method m, String testName, Method testMethod, DeclaredTest test, RunMode runMode) {
        TestFormat.check(testMethod != null, "Did not find associated @Test method \""  + m.getDeclaringClass().getName()
                                             + "." + testName + "\" specified in @Run at " + m);
        TestFormat.check(test != null,
                         "Missing @Test annotation for associated test method " + testName + " for @Run at " + m);
        Method attachedMethod = test.getAttachedMethod();
        TestFormat.check(attachedMethod == null,
                         "Cannot use @Test " + testMethod + " for more than one @Run/@Check method. Found: "
                         + m + ", " + attachedMethod);
        TestFormat.check(!test.hasArguments(),
                         "Cannot use @Arguments at test method " + testMethod + " in combination with @Run method " + m);
        Warmup warmupAnno = getAnnotation(testMethod, Warmup.class);
        TestFormat.checkNoThrow(warmupAnno == null,
                         "Cannot set @Warmup at @Test method " + testMethod + " when used with its @Run method "
                         + m + ". Use @Warmup at @Run method instead.");
        Test testAnno = getAnnotation(testMethod, Test.class);
        TestFormat.checkNoThrow(runMode != RunMode.STANDALONE || testAnno.compLevel() == CompLevel.ANY,
                                "Setting explicit compilation level for @Test method " + testMethod + " has no effect "
                                + "when used with STANDALONE @Run method " + m);
    }

    private void checkRunMethod(Method m, Run runAnno) {
        TestFormat.check(runAnno.test().length > 0, "@Run method " + m + " must specify at least one test method");
        TestFormat.checkNoThrow(m.getParameterCount() == 0 || (m.getParameterCount() == 1 && m.getParameterTypes()[0].equals(RunInfo.class)),
                                "@Run method " + m + " must specify either no parameter or exactly one " + RunInfo.class + " parameter.");
        Warmup warmupAnno = getAnnotation(m, Warmup.class);
        TestFormat.checkNoThrow(warmupAnno == null || runAnno.mode() != RunMode.STANDALONE,
                                "Cannot set @Warmup at @Run method " + m + " when used with RunMode.STANDALONE. The @Run method is only invoked once.");
    }

    private static <T extends Annotation> T getAnnotation(AnnotatedElement element, Class<T> c) {
        T[] annos =  element.getAnnotationsByType(c);
        TestFormat.check(annos.length < 2, element + " has duplicated annotations");
        return Arrays.stream(annos).findFirst().orElse(null);
    }

    /**
     * Ensure that all compilations that were enforced (added to compilation queue) by framework annotations are
     * completed. Wait if necessary for a short amount of time for their completion.
     */
    private void checkForcedCompilationsCompleted() {
        if (forceCompileMap.isEmpty()) {
            return;
        }
        final long started = System.currentTimeMillis();
        long elapsed;
        do {
            forceCompileMap.entrySet().removeIf(entry -> WHITE_BOX.getMethodCompilationLevel(entry.getKey()) == entry.getValue().getValue());
            if (forceCompileMap.isEmpty()) {
                // All @ForceCompile methods are compiled at the requested level.
                return;
            }
            // Retry again if not yet compiled.
            forceCompileMap.forEach(TestFrameworkExecution::enqueueForCompilation);
            elapsed = System.currentTimeMillis() - started;
        } while (elapsed < 5000);
        StringBuilder builder = new StringBuilder();
        forceCompileMap.forEach((key, value) -> builder.append("- ").append(key).append(" at CompLevel.").append(value).append("\n"));
        TestRun.fail("Could not force compile the following @ForceCompile methods:\n" + builder.toString());
    }

    /**
     * Once all framework tests are collected, they are run in this method.
     */
    private void runTests() {
        TreeMap<Long, String> durations = (PRINT_TIMES || VERBOSE) ? new TreeMap<>() : null;
        long startTime = System.nanoTime();
        List<AbstractTest> testList;
        boolean testFilterPresent = testFilterPresent();
        if (testFilterPresent) {
            // Only run the specified tests by the user filters -DTest and/or -DExclude.
            testList = allTests.stream().filter(test -> !test.isSkipped()).collect(Collectors.toList());
            if (testList.isEmpty()) {
                // Throw an exception to inform the user about an empty specified test set with -DTest and/or -DExclude
                throw new NoTestsRunException();
            }
        } else {
            testList = allTests;
        }

        if (SHUFFLE_TESTS) {
            // Execute tests in random order (execution sequence affects profiling). This is done by default.
            Collections.shuffle(testList);
        }
        StringBuilder builder = new StringBuilder();
        int failures = 0;

        // Execute all tests and keep track of each exception that is thrown. These are then reported once all tests
        // are executing. This prevents a premature exit without running all tests.
        for (AbstractTest test : testList) {
            if (VERBOSE) {
                System.out.println("Run " + test.toString());
            } else if (testFilterPresent) {
                TestFrameworkSocket.write("Run " + test.toString(), "testfilter", true);
            }
            try {
                test.run();
            } catch (TestRunException e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                builder.append(test.toString()).append(":\n").append(sw.toString()).append("\n\n");
                failures++;
            }
            if (PRINT_TIMES || VERBOSE) {
                long endTime = System.nanoTime();
                long duration = (endTime - startTime);
                durations.put(duration, test.getName());
                if (VERBOSE) {
                    System.out.println("Done " + test.getName() + ": " + duration + " ns = " + (duration / 1000000) + " ms");
                }
            }
            if (GC_AFTER) {
                System.out.println("doing GC");
                System.gc();
            }
        }

        // Print execution times
        if (VERBOSE || PRINT_TIMES) {
            System.out.println("\n\nTest execution times:");
            for (Map.Entry<Long, String> entry : durations.entrySet()) {
                System.out.format("%-10s%15d ns\n", entry.getValue() + ":", entry.getKey());
            }
        }

        if (failures > 0) {
            // Finally, report all occurred exceptions in a nice format.
            String msg = "\n\nTest Failures (" + failures + ")\n" +
                         "----------------" + "-".repeat(String.valueOf(failures).length());
            throw new TestRunException(msg + "\n" + builder.toString());
        }
    }

    private boolean testFilterPresent() {
        return testList != null || excludeList != null;
    }

    enum TriState {
        Maybe,
        Yes,
        No
    }

    static void compile(Method m, CompLevel compLevel) {
        TestRun.check(compLevel != CompLevel.SKIP && compLevel != CompLevel.WAIT_FOR_COMPILATION,
                         "Invalid compilation request with level " + compLevel);
        enqueueForCompilation(m, compLevel);
    }

    static void deoptimize(Method m) {
        WHITE_BOX.deoptimizeMethod(m);
    }

    static boolean isCompiled(Method m) {
        return compiledAtLevel(m, CompLevel.ANY) == TriState.Yes;
    }

    static boolean isC1Compiled(Method m) {
        return compiledByC1(m) == TriState.Yes;
    }

    static boolean isC2Compiled(Method m) {
        return compiledByC2(m) == TriState.Yes;
    }

    static boolean isCompiledAtLevel(Method m, CompLevel compLevel) {
        return compiledAtLevel(m, compLevel) == TriState.Yes;
    }

    static void assertDeoptimizedByC1(Method m) {
        if (notUnstableDeoptAssertion(m, CompLevel.C1)) {
            TestRun.check(compiledByC1(m) != TriState.Yes || PerMethodTrapLimit == 0 || !ProfileInterpreter,
                          m + " should have been deoptimized by C1");
        }
    }

    static void assertDeoptimizedByC2(Method m) {
        if (notUnstableDeoptAssertion(m, CompLevel.C2)) {
            TestRun.check(compiledByC2(m) != TriState.Yes || PerMethodTrapLimit == 0 || !ProfileInterpreter,
                          m + " should have been deoptimized by C2");
        }
    }

    /**
     * Some VM flags could make the deopt assertions unstable.
     */
    private static boolean notUnstableDeoptAssertion(Method m, CompLevel level) {
        return (USE_COMPILER && !XCOMP && !TEST_C1 &&
               (!EXCLUDE_RANDOM || WHITE_BOX.isMethodCompilable(m, level.getValue(), false)));
    }

    static void assertCompiledByC1(Method m) {
        TestRun.check(compiledByC1(m) != TriState.No, m + " should have been C1 compiled");
    }

    static void assertCompiledByC2(Method m) {
        TestRun.check(compiledByC2(m) != TriState.No, m + " should have been C2 compiled");
    }

    static void assertCompiledAtLevel(Method m, CompLevel level) {
        TestRun.check(compiledAtLevel(m, level) != TriState.No, m + " should have been compiled at level " + level.name());
    }

    static void assertNotCompiled(Method m) {
        TestRun.check(!isC1Compiled(m), m + " should not have been compiled by C1");
        TestRun.check(!isC2Compiled(m), m + " should not have been compiled by C2");
    }

    static void assertCompiled(Method m) {
        TestRun.check(compiledByC1(m) != TriState.No || compiledByC2(m) != TriState.No, m + " should have been compiled");
    }

    private static TriState compiledByC1(Method m) {
        TriState triState = compiledAtLevel(m, CompLevel.C1);
        if (triState != TriState.No) {
            return triState;
        }
        triState = compiledAtLevel(m, CompLevel.C1_LIMITED_PROFILE);
        if (triState != TriState.No) {
            return triState;
        }
        triState = compiledAtLevel(m, CompLevel.C1_FULL_PROFILE);
        return triState;
    }

    private static TriState compiledByC2(Method m) {
        return compiledAtLevel(m, CompLevel.C2);
    }

    private static TriState compiledAtLevel(Method m, CompLevel level) {
        if (WHITE_BOX.isMethodCompiled(m, false)) {
            switch (level) {
                case C1, C1_LIMITED_PROFILE, C1_FULL_PROFILE, C2 -> {
                    if (WHITE_BOX.getMethodCompilationLevel(m, false) == level.getValue()) {
                        return TriState.Yes;
                    }
                }
                case ANY -> {
                    return TriState.Yes;
                }
                default -> TestRun.fail("compiledAtLevel() should not be called with " + level);
            }
        }
        if (!USE_COMPILER || XCOMP || TEST_C1 ||
            (EXCLUDE_RANDOM && !WHITE_BOX.isMethodCompilable(m, level.getValue(), false))) {
            return TriState.Maybe;
        }
        return TriState.No;
    }
}

/**
 * Abstract super class for base, checked and custom run tests.
 */
abstract class AbstractTest {
    protected static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();
    protected static final int TEST_COMPILATION_TIMEOUT = Integer.parseInt(System.getProperty("TestCompilationTimeout", "10000"));
    protected static final int WAIT_FOR_COMPILATION_TIMEOUT = Integer.parseInt(System.getProperty("WaitForCompilationTimeout", "10000"));
    protected static final boolean VERIFY_OOPS = (Boolean)WHITE_BOX.getVMFlag("VerifyOops");

    protected final int warmupIterations;
    protected final boolean skip;

    AbstractTest(int warmupIterations, boolean skip) {
        this.warmupIterations = warmupIterations;
        this.skip = skip;
    }

    protected boolean shouldCompile(DeclaredTest test) {
        return test.getCompLevel() != CompLevel.SKIP;
    }

    abstract String getName();

    /**
     * Should test be executed?
     */
    public boolean isSkipped() {
        return skip;
    }

    /**
     * See {@link CompLevel#WAIT_FOR_COMPILATION}.
     */
    protected static boolean isWaitForCompilation(DeclaredTest test) {
        return test.getCompLevel() == CompLevel.WAIT_FOR_COMPILATION;
    }

    protected static Object createInvocationTarget(Method method) {
        Class<?> clazz = method.getDeclaringClass();
        Object invocationTarget;
        if (Modifier.isStatic(method.getModifiers())) {
            invocationTarget = null;
        } else {
            try {
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                invocationTarget = constructor.newInstance();
            } catch (Exception e) {
                throw new TestRunException("Could not create instance of " + clazz
                                           + ". Make sure there is a constructor without arguments.", e);
            }
        }
        return invocationTarget;
    }

    /**
     * Run the associated test.
     */
    public void run() {
        if (skip) {
            return;
        }
        onStart();
        for (int i = 0; i < warmupIterations; i++) {
            invokeTest();
        }
        onWarmupFinished();
        compileTest();
        // Always run the test as a last step of the test execution.
        invokeTest();
    }

    protected void onStart() {
        // Do nothing by default.
    }

    abstract protected void invokeTest();

    abstract protected void onWarmupFinished();

    abstract protected void compileTest();

    protected void compileMethod(DeclaredTest test) {
        final Method testMethod = test.getTestMethod();
        TestRun.check(WHITE_BOX.isMethodCompilable(testMethod, test.getCompLevel().getValue(), false),
                      "Method " + testMethod + " not compilable at level " + test.getCompLevel()
                      + ". Did you use compileonly without including all @Test methods?");
        TestRun.check(WHITE_BOX.isMethodCompilable(testMethod),
                      "Method " + testMethod + " not compilable at level " + test.getCompLevel()
                      + ". Did you use compileonly without including all @Test methods?");
        if (TestFramework.VERBOSE) {
            System.out.println("Compile method " + testMethod + " after warm-up...");
        }

        final boolean maybeCodeBufferOverflow = (TestFrameworkExecution.TEST_C1 && VERIFY_OOPS);
        final long started = System.currentTimeMillis();
        long elapsed = 0;
        enqueueMethodForCompilation(test);

        do {
            if (!WHITE_BOX.isMethodQueuedForCompilation(testMethod)) {
                if (elapsed > 0) {
                    if (TestFrameworkExecution.VERBOSE) {
                        System.out.println(testMethod + " is not in queue anymore due to compiling it simultaneously on " +
                                           "a different level. Enqueue again.");
                    }
                    enqueueMethodForCompilation(test);
                }
            }
            if (maybeCodeBufferOverflow && elapsed > 1000 && !WHITE_BOX.isMethodCompiled(testMethod, false)) {
                // Let's disable VerifyOops temporarily and retry.
                WHITE_BOX.setBooleanVMFlag("VerifyOops", false);
                WHITE_BOX.clearMethodState(testMethod);
                enqueueMethodForCompilation(test);
                WHITE_BOX.setBooleanVMFlag("VerifyOops", true);
            }

            if (WHITE_BOX.getMethodCompilationLevel(testMethod, false) == test.getCompLevel().getValue()) {
                break;
            }
            elapsed = System.currentTimeMillis() - started;
        } while (elapsed < TEST_COMPILATION_TIMEOUT);
        TestRun.check(elapsed < TEST_COMPILATION_TIMEOUT,
                      "Could not compile " + testMethod + " after " + TEST_COMPILATION_TIMEOUT/1000 + "s");
        checkCompilationLevel(test);
    }

    private void enqueueMethodForCompilation(DeclaredTest test) {
        TestFrameworkExecution.enqueueForCompilation(test.getTestMethod(), test.getCompLevel());
    }

    protected void checkCompilationLevel(DeclaredTest test) {
        CompLevel level = CompLevel.forValue(WHITE_BOX.getMethodCompilationLevel(test.getTestMethod()));
        TestRun.check(level == test.getCompLevel(),  "Compilation level should be " + test.getCompLevel().name()
                                                     + " (requested) but was " + level.name() + " for " + test.getTestMethod());
    }

    final protected void waitForCompilation(DeclaredTest test) {
        final Method testMethod = test.getTestMethod();
        final boolean maybeCodeBufferOverflow = (TestFrameworkExecution.TEST_C1 && VERIFY_OOPS);
        final long started = System.currentTimeMillis();
        boolean stateCleared = false;
        long elapsed;
        do {
            elapsed = System.currentTimeMillis() - started;
            int level = WHITE_BOX.getMethodCompilationLevel(testMethod);
            if (maybeCodeBufferOverflow && elapsed > 5000
                && (!WHITE_BOX.isMethodCompiled(testMethod, false) || level != test.getCompLevel().getValue())) {
                retryDisabledVerifyOops(testMethod, stateCleared);
                stateCleared = true;
            } else {
                invokeTest();
            }

            boolean isCompiled = WHITE_BOX.isMethodCompiled(testMethod, false);
            if (TestFrameworkExecution.VERBOSE) {
                System.out.println("Is " + testMethod + " compiled? " + isCompiled);
            }
            if (isCompiled || TestFrameworkExecution.XCOMP) {
                // Don't wait for compilation if -Xcomp is enabled.
                return;
            }
        } while (elapsed < WAIT_FOR_COMPILATION_TIMEOUT);
        TestRun.fail(testMethod + " not compiled after waiting for " + WAIT_FOR_COMPILATION_TIMEOUT/1000 + " s");
    }

    /**
     * If it takes too long, try to disable Verify Oops.
     */
    private void retryDisabledVerifyOops(Method testMethod, boolean stateCleared) {
        System.out.println("Temporarily disabling VerifyOops");
        try {
            WHITE_BOX.setBooleanVMFlag("VerifyOops", false);
            if (!stateCleared) {
                WHITE_BOX.clearMethodState(testMethod);
            }
            invokeTest();
        } finally {
            WHITE_BOX.setBooleanVMFlag("VerifyOops", true);
            System.out.println("Re-enabled VerifyOops");
        }
    }
}

/**
 * A base test only consists of a single @Test method. See {@link Test} for more details and its precise definition.
 */
class BaseTest extends AbstractTest {
    private final DeclaredTest test;
    protected final Method testMethod;
    protected final TestInfo testInfo;
    protected final Object invocationTarget;
    private final boolean shouldCompile;
    private final boolean waitForCompilation;

    public BaseTest(DeclaredTest test, boolean skip) {
        super(test.getWarmupIterations(), skip);
        this.test = test;
        this.testMethod = test.getTestMethod();
        this.testInfo = new TestInfo(test);
        this.invocationTarget = createInvocationTarget(testMethod);
        this.shouldCompile = shouldCompile(test);
        this.waitForCompilation = isWaitForCompilation(test);
    }

    @Override
    public String toString() {
        return "Base Test: @Test " + testMethod.getName();
    }

    @Override
    public String getName() {
        return testMethod.getName();
    }

    @Override
    protected void onStart() {
        test.printFixedRandomArguments();
    }

    @Override
    public void onWarmupFinished() {
        testInfo.setWarmUpFinished();
    }

    @Override
    protected void invokeTest() {
        verify(invokeTestMethod());
    }

    private Object invokeTestMethod() {
        try {
            if (test.hasArguments()) {
                return testMethod.invoke(invocationTarget, test.getArguments());
            } else {
                return testMethod.invoke(invocationTarget);
            }
        } catch (Exception e) {
            throw new TestRunException("There was an error while invoking @Test method " + testMethod
                                       + ". Used arguments: " + test.getArgumentsString(), e);
        }
    }

    @Override
    protected void compileTest() {
        if (shouldCompile) {
            if (waitForCompilation) {
                waitForCompilation(test);
            } else {
                compileMethod(test);
            }
        }
    }

    /**
     * Verify the result
     */
    public void verify(Object result) { /* no verification in BaseTests */ }
}

/**
 * A checked test is an extension of a base test with additional verification done in a @Check method.
 * See {@link Check} for more details and its precise definition.
 */
class CheckedTest extends BaseTest {
    private final Method checkMethod;
    private final CheckAt checkAt;
    private final Parameter parameter;
    private final Object checkInvocationTarget;

    enum Parameter {
        NONE, RETURN_ONLY, TEST_INFO_ONLY, BOTH
    }

    public CheckedTest(DeclaredTest test, Method checkMethod, Check checkSpecification, Parameter parameter, boolean excludedByUser) {
        super(test, excludedByUser);
        // Make sure we can also call non-public or public methods in package private classes
        checkMethod.setAccessible(true);
        this.checkMethod = checkMethod;
        this.checkAt = checkSpecification.when();
        this.parameter = parameter;
        // Use the same invocation target
        if (Modifier.isStatic(checkMethod.getModifiers())) {
            this.checkInvocationTarget = null;
        } else {
            // Use the same invocation target as the test method if check method is non-static.
            this.checkInvocationTarget = this.invocationTarget != null ? this.invocationTarget : createInvocationTarget(checkMethod);
        }
    }

    @Override
    public String toString() {
        return "Checked Test: @Check " + checkMethod.getName() + " - @Test: " + testMethod.getName();
    }

    @Override
    public String getName() {
        return checkMethod.getName();
    }

    @Override
    public void verify(Object result) {
        boolean shouldVerify = false;
        switch (checkAt) {
            case EACH_INVOCATION -> shouldVerify = true;
            case COMPILED -> shouldVerify = !testInfo.isWarmUp();
        }
        if (shouldVerify) {
            try {
                switch (parameter) {
                    case NONE -> checkMethod.invoke(checkInvocationTarget);
                    case RETURN_ONLY -> checkMethod.invoke(checkInvocationTarget, result);
                    case TEST_INFO_ONLY -> checkMethod.invoke(checkInvocationTarget, testInfo);
                    case BOTH -> checkMethod.invoke(checkInvocationTarget, result, testInfo);
                }
            } catch (Exception e) {
                throw new TestRunException("There was an error while invoking @Check method " + checkMethod, e);
            }
        }
    }
}

/**
 * A custom run test allows the user to have full control over how the @Test method is invoked by specifying
 * a dedicated @Run method. See {@link Run} for more details and its precise definition.
 */
class CustomRunTest extends AbstractTest {
    private final Method runMethod;
    private final RunMode mode;
    private final Object runInvocationTarget;
    private final List<DeclaredTest> tests;
    private final RunInfo runInfo;

    public CustomRunTest(Method runMethod, Warmup warmUpAnno, Run runSpecification, List<DeclaredTest> tests, boolean skip) {
        // Make sure we can also call non-public or public methods in package private classes
        super(warmUpAnno != null ? warmUpAnno.value() : TestFrameworkExecution.WARMUP_ITERATIONS, skip);
        TestFormat.checkNoThrow(warmupIterations >= 0, "Cannot have negative value for @Warmup at " + runMethod);
        runMethod.setAccessible(true);
        this.runMethod = runMethod;
        this.runInvocationTarget = createInvocationTarget(runMethod);
        this.mode = runSpecification.mode();
        this.tests = tests;
        this.runInfo = new RunInfo(tests);
    }

    @Override
    public String toString() {
        String s = "Custom Run Test: @Run: " + runMethod.getName() + " - @Test";
        if (tests.size() == 1) {
            s += ": " + tests.get(0).getTestMethod().getName();
        } else {
            s += "s: {" + tests.stream().map(t -> t.getTestMethod().getName())
                                             .collect(Collectors.joining(",")) + "}";
        }
        return s;
    }

    @Override
    String getName() {
        return runMethod.getName();
    }

    @Override
    public void run() {
        if (skip) {
            return;
        }
        switch (mode) {
            case STANDALONE -> {
                runInfo.setWarmUpFinished();
                invokeTest();
            }// Invoke once but do not apply anything else.
            case NORMAL -> super.run();
        }
    }

    @Override
    public void onWarmupFinished() {
        runInfo.setWarmUpFinished();
    }

    @Override
    protected void compileTest() {
        if (tests.size() == 1) {
            compileSingleTest();
        } else {
            compileMultipleTests();
        }
    }

    private void compileSingleTest() {
        DeclaredTest test = tests.get(0);
        if (shouldCompile(test)) {
            if (isWaitForCompilation(test)) {
                waitForCompilation(test);
            } else {
                compileMethod(test);
            }
        }
    }

    private void compileMultipleTests() {
        boolean anyWaitForCompilation = false;
        boolean anyCompileMethod = false;
        ExecutorService executor = Executors.newFixedThreadPool(tests.size());
        for (DeclaredTest test : tests) {
            if (shouldCompile(test)) {
                if (isWaitForCompilation(test)) {
                    anyWaitForCompilation = true;
                    executor.execute(() -> waitForCompilation(test));
                } else {
                    anyCompileMethod = true;
                    executor.execute(() -> compileMethod(test));
                }
            }
        }
        executor.shutdown();
        int timeout;
        if (anyCompileMethod && anyWaitForCompilation) {
            timeout = Math.max(WAIT_FOR_COMPILATION_TIMEOUT, TEST_COMPILATION_TIMEOUT) + 5000;
        } else if (anyWaitForCompilation) {
            timeout = WAIT_FOR_COMPILATION_TIMEOUT + 5000;
        } else {
            timeout = TEST_COMPILATION_TIMEOUT + 5000;
        }
        try {
            executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            TestRun.fail("Some compilations did not complete after " + timeout + "ms for @Run method " + runMethod);
        }
    }

    /**
     * Do not directly run the test but rather the run method that is responsible for invoking the actual test.
     */
    @Override
    protected void invokeTest() {
        try {
            if (runMethod.getParameterCount() == 1) {
                runMethod.invoke(runInvocationTarget, runInfo);
            } else {
                runMethod.invoke(runInvocationTarget);
            }
        } catch (Exception e) {
            throw new TestRunException("There was an error while invoking @Run method " + runMethod, e);
        }
    }

    @Override
    protected void checkCompilationLevel(DeclaredTest test) {
        CompLevel level = CompLevel.forValue(WhiteBox.getWhiteBox().getMethodCompilationLevel(test.getTestMethod()));
        if (level != test.getCompLevel()) {
            String message = "Compilation level should be " + test.getCompLevel().name() + " (requested) but was "
                             + level.name() + " for " + test.getTestMethod() + ".";
            switch (mode) {
                case STANDALONE -> TestFramework.fail("Should not be called for STANDALONE method " + runMethod);
                case NORMAL -> message = message + "\nCheck your @Run method " + runMethod + " to ensure that "
                                         + test.getTestMethod() + " is called at least once in each iteration.";
            }
            TestRun.fail(message);
        }
    }
}
