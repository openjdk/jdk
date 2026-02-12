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
 * @summary 8372634
 *
 * @requires vm.flagless
 * @library /test/lib /
 *
 * @run driver compiler.inlining.TestSubtypeCheckTypeInfo
 */
package compiler.inlining;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestSubtypeCheckTypeInfo {
    static final Class<TestSubtypeCheckTypeInfo> THIS_CLASS = TestSubtypeCheckTypeInfo.class;
    static final String TEST_CLASS_NAME = THIS_CLASS.getName();

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-XX:+IgnoreUnrecognizedVMOptions", "-showversion",
                "-XX:-TieredCompilation", "-Xbatch", "-XX:CICompilerCount=1",
                "-XX:+PrintCompilation", "-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining",
                "-XX:CompileCommand=quiet",
                "-XX:CompileCommand=compileonly," +  TEST_CLASS_NAME + "::test*",
                "-XX:CompileCommand=delayinline," +  TEST_CLASS_NAME + "::lateInline*",
                TestSubtypeCheckTypeInfo.Launcher.class.getName());

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        // The test is applicable only to C2 (present in Server VM).
        if (analyzer.getStderr().contains("Server VM")) {
            List<String> output = analyzer.asLinesWithoutVMWarnings();

            parseOutput(output);
            System.out.println("TEST PASSED");
        }
    }

    static class Launcher {
        public static void main(String[] args) {
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOf);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfCondPre);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfCondPost);

            runTestCase(TestSubtypeCheckTypeInfo::testIsInstance);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceCondPre);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceCondPost);

            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfLate);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfLateCondPre);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfLateCondPost);

            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceLate);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceLateCondPre);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceLateCondPost);

            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfCondLate);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfCondLatePre);
            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfCondLatePost);

            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceCondLate);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceCondLatePre);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceCondLatePost);

            runTestCase(TestSubtypeCheckTypeInfo::testInstanceOfNulls);
            runTestCase(TestSubtypeCheckTypeInfo::testIsInstanceNulls);
        }
    }

    /* =========================================================== */

    @InlineSuccess
    // @ 8   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testInstanceOf(A o, boolean cond) {
        if (o instanceof B) {
            o.m();
        }
    }

    @InlineSuccess
    // @ 12   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testInstanceOfCondPre(A o, boolean cond) {
        if (cond && (o instanceof B)) {
            o.m();
        }
    }

    @InlineSuccess
    // @ 12   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testInstanceOfCondPost(A o, boolean cond) {
        if ((o instanceof B) && cond) {
            o.m();
        }
    }

    /* =========================================================== */

    @InlineSuccess
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 3   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 10   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testIsInstance(A o, boolean cond) {
        if (B.class.isInstance(o)) {
            o.m();
        }
    }

    @InlineSuccess
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 7   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 14   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testIsInstanceCondPre(A o, boolean cond) {
        if (cond && B.class.isInstance(o)) {
            o.m();
        }
    }

    @InlineSuccess
    // @ 3   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 14   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testIsInstanceCondPost(A o, boolean cond) {
        if (B.class.isInstance(o) && cond) {
            o.m();
        }
    }

    /* =========================================================== */

    @InlineSuccess
    // @ 5   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testInstanceOfLate(A o, boolean cond) {
        // if (o instanceof B) { o.m(); }
        lateInline(o, o instanceof B);
    }

    @InlineFailure
    // @ 17   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfLateCondPre(A o, boolean cond) {
        // if (cond && o instanceof B) { o.m(); }
        lateInline(o, cond && (o instanceof B));
    }

    @InlineFailure
    // @ 17   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfLateCondPost(A o, boolean cond) {
        // if ((o instanceof B) && cond) { o.m(); }
        lateInline(o, (o instanceof B) && cond);
    }

    /* =========================================================== */

    @InlineSuccess
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 4   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 7   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$B::m (1 bytes)   inline (hot)
    static void testIsInstanceLate(A o, boolean cond) {
        // if (B.class.isInstance(o)) { o.m(); }
        lateInline(o, B.class.isInstance(o));
    }

    @InlineFailure
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 8   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 19   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceLateCondPre(A o, boolean cond) {
        // if (cond && B.class.isInstance(o)) { o.m(); }
        lateInline(o, cond && (B.class.isInstance(o)));
    }

    @InlineFailure
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 4   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 19   compiler.inlining.TestSubtypeCheckTypeInfo::lateInline (9 bytes)   inline (hot)   late inline succeeded
    //   @ 5   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceLateCondPost(A o, boolean cond) {
        // if (B.class.isInstance(o) && cond) { o.m(); }
        lateInline(o, (B.class.isInstance(o) && cond));
    }

    /* =========================================================== */

    @InlineFailure
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineInstanceOfCondPre (17 bytes)   inline (hot)   late inline succeeded
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfCondLate(A a, boolean cond) {
        if (lateInlineInstanceOfCondPre(a, true)) {
            a.m();
        }
    }

    @InlineFailure
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineInstanceOfCondPre (17 bytes)   inline (hot)   late inline succeeded
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfCondLatePre(A a, boolean cond) {
        if (lateInlineInstanceOfCondPre(a, cond)) {
            a.m();
        }
    }

    @InlineFailure
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineInstanceOfCondPost (17 bytes)   inline (hot)   late inline succeeded
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfCondLatePost(A a, boolean cond) {
        if (lateInlineInstanceOfCondPost(a, cond)) {
            a.m();
        }
    }

    /* =========================================================== */

    @InlineFailure
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineIsInstanceCondPre (19 bytes)   inline (hot)   late inline succeeded
    //   @ 7   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceCondLate(A a, boolean cond) {
        if (lateInlineIsInstanceCondPre(a, true)) {
            a.m();
        }
    }

    @InlineFailure
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineIsInstanceCondPre (19 bytes)   inline (hot)   late inline succeeded
    //   @ 7   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceCondLatePre(A a, boolean cond) {
        if (lateInlineIsInstanceCondPre(a, cond)) {
            a.m();
        }
    }

    @InlineFailure
    // Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 2   compiler.inlining.TestSubtypeCheckTypeInfo::lateInlineIsInstanceCondPost (19 bytes)   inline (hot)   late inline succeeded
    //   @ 3   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 9   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceCondLatePost(A a, boolean cond) {
        if (lateInlineIsInstanceCondPost(a, cond)) {
            a.m();
        }
    }

    /* =========================================================== */

    @InlineFailure
    // @ 20   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testInstanceOfNulls(A o, boolean cond) {
        A recv = (cond ? o : null);
        if (recv instanceof B) {
            o.m();
        }
    }

    @InlineFailure
    //Inlining _isInstance on constant Class compiler/inlining/TestSubtypeCheckTypeInfo$B
    // @ 13   java.lang.Class::isInstance (0 bytes)   (intrinsic)
    // @ 20   compiler.inlining.TestSubtypeCheckTypeInfo$A::m (0 bytes)   failed to inline: virtual call
    static void testIsInstanceNulls(A o, boolean cond) {
        A recv = (cond ? o : null);
        if (B.class.isInstance(recv)) {
            o.m();
        }
    }

    /* =========================================================== */

    static abstract class A {
        public abstract void m();
    }
    static abstract class B extends A {
        public void m() {}
    }

    static class C extends A {
        public void m() {}
    }

    static void lateInline(A o, boolean cond) {
        if (cond) {
            o.m();
        }
    }

    static boolean lateInlineInstanceOfCondPre(A o, boolean cond) {
        return cond && (o instanceof B);
    }

    static boolean lateInlineInstanceOfCondPost(A o, boolean cond) {
        return (o instanceof B) && cond;
    }

    static boolean lateInlineIsInstanceCondPre(A o, boolean cond) {
        return cond && B.class.isInstance(o);
    }
    static boolean lateInlineIsInstanceCondPost(A o, boolean cond) {
        return B.class.isInstance(o) && cond;
    }

    /* =========================================================== */

    static final String INLINE_SUCCESS_MESSAGE = "B::m (1 bytes)   inline (hot)";
    static final String INLINE_FAILURE_MESSAGE = "A::m (0 bytes)   failed to inline: virtual call";

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface InlineSuccess {
        String[] shouldContain()    default INLINE_SUCCESS_MESSAGE;
        String[] shouldNotContain() default INLINE_FAILURE_MESSAGE;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface InlineFailure {
        String[] shouldContain()    default INLINE_FAILURE_MESSAGE;
        String[] shouldNotContain() default INLINE_SUCCESS_MESSAGE;
    }

    /* =========================================================== */

    // Parse compilation log (-XX:+PrintCompilation -XX:+PrintInlining output).
    static void parseOutput(List<String> output) {
        Pattern compilation = Pattern.compile("^\\d+\\s+\\d+.*");
        StringBuilder inlineTree = new StringBuilder();
        Set<String> passedTests = new HashSet();
        Set<String> failedTests = new HashSet();
        for (String line : output) {
            // Detect start of next compilation.
            if (compilation.matcher(line).matches()) {
                // Parse output for previous compilation.
                validateInliningOutput(inlineTree.toString(), passedTests, failedTests);
                inlineTree = new StringBuilder(); // reset
            }
            inlineTree.append(line);
        }
        // Process last compilation
        validateInliningOutput(inlineTree.toString(), passedTests, failedTests);

        if (!failedTests.isEmpty()) {
            String msg = String.format("TEST FAILED: %d test cases failed", failedTests.size());
            throw new AssertionError(msg);
        } else if (passedTests.size() != totalTestCount()) {
            String msg = String.format("TEST FAILED: %d out of %d test cases passed", passedTests.size(), totalTestCount());
            throw new AssertionError(msg);
        }
    }

    // Sample:
    //    213   42    b        compiler.inlining.TestSubtypeCheckTypeInfo::testIsInstanceCondLatePost (13 bytes)
    static final Pattern TEST_CASE = Pattern.compile("^\\d+\\s+\\d+\\s+b\\s+" + TEST_CLASS_NAME + "::(\\w+) .*");

    static boolean validateInliningOutput(String inlineTree, Set<String> passedTests, Set<String> failedTests) {
        Matcher m = TEST_CASE.matcher(inlineTree);
        if (m.matches()) {
            String testName = m.group(1);
            System.out.print(testName);
            try {
                Method testMethod = TestSubtypeCheckTypeInfo.class.getDeclaredMethod(testName, A.class, boolean.class);
                if (validate(inlineTree, testMethod.getAnnotation(InlineSuccess.class)) &&
                    validate(inlineTree, testMethod.getAnnotation(InlineFailure.class))) {
                    System.out.println(": SUCCESS");
                    passedTests.add(testName);
                    return true;
                } else {
                    failedTests.add(testName);
                    return false;
                }
            } catch (NoSuchMethodException e) {
                System.out.println(": FAILURE: Missing test info for " + testName + ": " + inlineTree);
                throw new InternalError(e);
            }
        } else {
            return false; // not a test method; ignored
        }
    }

    static boolean validate(String message, InlineSuccess ann) {
        if (ann != null) {
            return validatePatterns(message, ann.shouldContain(), ann.shouldNotContain());
        }
        return true; // no patterns to validate
    }

    static boolean validate(String message, InlineFailure ann) {
        if (ann != null) {
            return validatePatterns(message, ann.shouldContain(), ann.shouldNotContain());
        }
        return true; // no patterns to validate
    }

    static boolean validatePatterns(String message, String[] shouldContain, String[] shouldNotContain) {
        for (String pattern : shouldContain) {
            if (!message.contains(pattern)) {
                System.out.printf(": FAILURE: '%s' not found in '%s'\n", pattern, message);
                return false;
            }
        }
        for (String pattern : shouldNotContain) {
            if (message.contains(pattern)) {
                System.out.printf(": FAILURE: '%s' found in '%s'\n", pattern, message);
                return false;
            }
        }
        return true;
    }

    static int totalTestCount() {
        int count = 0;
        for (Method m : THIS_CLASS.getDeclaredMethods()) {
            if (m.isAnnotationPresent(InlineSuccess.class) || m.isAnnotationPresent(InlineFailure.class)) {
                String testName = m.getName();
                if (testName.startsWith("test")) {
                    count++;
                } else {
                    throw new InternalError("wrong test name: " + testName);
                }
            }
        }
        return count;
    }

    /* =========================================================== */

    interface TestCase {
        void run(A o, boolean cond);
    }

    static void runTestCase(TestCase t) {
        A[] receivers = new A[] { new B() {}, new B() {}, new B() {}, new C() {}, new C() {}};
        for (int i = 0; i < 20_000; i++) {
            // Pollute type profile and branch frequencies.
            A recv = receivers[i % receivers.length];
            boolean cond = (i % 2 == 0);
            t.run(recv, cond);
        }
    }
}
