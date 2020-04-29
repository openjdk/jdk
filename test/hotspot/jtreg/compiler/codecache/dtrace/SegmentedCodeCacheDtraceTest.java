/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test SegmentedCodeCacheDtraceTest
 * @key randomness
 * @bug 8015774
 * @summary testing of dtrace for segmented code cache
 * @requires os.family=="solaris"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 *
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=600 -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     compiler.codecache.dtrace.SegmentedCodeCacheDtraceTest
 */

package compiler.codecache.dtrace;

import compiler.testlibrary.CompilerUtils;
import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jtreg.SkippedException;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SegmentedCodeCacheDtraceTest {

    private static final String WORKER_CLASS_NAME
            = SegmentedCodeCacheDtraceTestWorker.class.getName();
    private static final String JAVA_OPTS = " -XX:+DTraceMethodProbes "
            + "-Xbootclasspath/a:" + System.getProperty("test.classes") + " "
            + "-XX:+UnlockDiagnosticVMOptions "
            + "-XX:+WhiteBoxAPI -XX:+SegmentedCodeCache "
            + "-XX:CompileCommand=compileonly,"
            + WORKER_CLASS_NAME + "::* "
            + " -classpath " + System.getProperty("test.class.path") + " "
            + String.join(" ", Utils.getTestJavaOpts());
    private static final String DTRACE_SCRIPT
            = "SegmentedCodeCacheDtraceTestScript.d";
    private static final List<Executable> MLIST =
            SegmentedCodeCacheDtraceTestWorker.TESTED_METHODS_LIST;
    private static final int WORKER_METHODS_COUNT = MLIST.size();

    private void runTest(TestCombination tc) {
        String params = MLIST.stream()
                .map(Executable::getName)
                .map(x -> tc.data.get(x).compileLevel + " " + tc.data.get(x).isInlined)
                .collect(Collectors.joining(" "));
        DtraceRunner runner = new DtraceRunner();
        runner.runDtrace(JDKToolFinder.getTestJDKTool("java"), JAVA_OPTS,
                WORKER_CLASS_NAME, params, Paths.get(System.getProperty("test.src"),
                        DTRACE_SCRIPT).toString(),
                DtraceRunner.PERMIT_DESTRUCTIVE_ACTIONS_DTRACE_OPTION,
                new SegmentedCodeCacheDtraceResultsAnalyzer());
    }

    private static TestCombination generateUniqueCombination(
            int[] availableLevels, Set<TestCombination> combinations) {
        int len = availableLevels.length;
        /* first, check if we're out of combinations. */
        int maxCombinationsCount
                = (1 << WORKER_METHODS_COUNT)
                * (int) Math.pow(len, WORKER_METHODS_COUNT);
        if (combinations.size() == maxCombinationsCount) {
            return null;
        }
        Random r = Utils.getRandomInstance();
        while (combinations.size() < maxCombinationsCount) {
            int levels[] = new int[WORKER_METHODS_COUNT];
            boolean inlines[] = new boolean[WORKER_METHODS_COUNT];
            for (int i = 0; i < WORKER_METHODS_COUNT; i++) {
                levels[i] = availableLevels[r.nextInt(len)];
                inlines[i] = r.nextBoolean();
            }
            TestCombination tc = new TestCombination(levels, inlines);
            if (combinations.add(tc)) {
                return tc;
            }
        }
        return null;
    }

    public static void main(String args[]) {
        if (!DtraceRunner.dtraceAvailable()) {
            throw new SkippedException("There is no dtrace avaiable.");
        }
        int iterations
                = Integer.getInteger("jdk.test.lib.iterations", 1);
        int[] availableLevels = CompilerUtils.getAvailableCompilationLevels();
        // adding one more entry(zero) for interpeter
        availableLevels
                = Arrays.copyOf(availableLevels, availableLevels.length + 1);
        Set<TestCombination> combinations = new HashSet<>();
        for (int i = 0; i < iterations; i++) {
            TestCombination tc
                    = generateUniqueCombination(availableLevels, combinations);
            if (tc == null) {
                System.out.println("INFO: no more combinations available");
                return;
            } else {
                System.out.println("INFO: Running testcase for: " + tc);
                new SegmentedCodeCacheDtraceTest().runTest(tc);
            }
        }
    }

    private static class MethodData {

        public final int compileLevel;
        public final boolean isInlined;
        public final String name;

        public MethodData(String name, int compileLevel, boolean isInlined) {
            this.name = name;
            this.compileLevel = compileLevel;
            this.isInlined = isInlined;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof MethodData)) {
                return false;
            }
            MethodData md = (MethodData) o;
            return md.compileLevel == compileLevel
                    && md.isInlined == isInlined
                    && md.name.equals(name);
        }

        @Override
        public int hashCode() {
            return 100 * name.hashCode() + 10 * compileLevel + (isInlined ? 1 : 0);
        }

        @Override
        public String toString() {
            return name + " " + compileLevel + " " + isInlined;
        }
    }

    private static class TestCombination {

        private final Map<String, MethodData> data;

        public TestCombination(int compLevels[], boolean inlines[]) {
            Map<String, MethodData> d = new HashMap<>();
            for (int i = 0; i < MLIST.size(); i++) {
                d.put(MLIST.get(i).getName(), new MethodData(MLIST.get(i).getName(),
                        compLevels[i], inlines[i]));
            }
            data = Collections.unmodifiableMap(d);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof TestCombination)) {
                return false;
            }
            TestCombination second = (TestCombination) o;
            return second.data.equals(data);
        }

        @Override
        public int hashCode() {
            int sum = 0;
            for (MethodData md : data.values()) {
                sum += md.hashCode();
            }
            return sum;
        }

        private String getMethodDescString(MethodData md) {
            return (md == null)
                    ? null
                    : String.format("Method %s compilation level %d and %s",
                            md.name, md.compileLevel,
                            md.isInlined ? "inlined" : "not inlined");
        }

        @Override
        public String toString() {
            return data.values().stream().map(m -> getMethodDescString(m))
                    .collect(Collectors.joining(Utils.NEW_LINE,
                                    "Combination: ", ""));
        }
    }

    private class SegmentedCodeCacheDtraceResultsAnalyzer
            implements DtraceResultsAnalyzer {

        private static final int EXPECTED_MATCH_COUNT = 2;

        private final Pattern checkPattern;

        public SegmentedCodeCacheDtraceResultsAnalyzer() {
            String workerClassRegExp = "\\s*" + WORKER_CLASS_NAME + "\\.";
            String delimeter = "\\(\\)V\\*?" + workerClassRegExp;
            String suffix = "test\\(\\)V\\*?" + workerClassRegExp
                    + "main\\(\\[Ljava\\/lang\\/String;\\)V";
            StringBuilder sb = new StringBuilder(workerClassRegExp);
            // method order is important, so, going from list tail to head,
            // accoring to call order representation in stacktrace
            for (int i = MLIST.size() - 1; i > -1; i--) {
                sb.append(MLIST.get(i).getName()).append(delimeter);
            }
            sb.append(suffix);
            checkPattern = Pattern.compile(sb.toString());
            /* such pattern match should pass on a stacktrace like
             CPU     ID                    FUNCTION:NAME
             0  53573 __1cNSharedRuntimeTdtrace_method_entry6FpnKJavaThread_pnGMethod__i_:method-entry ustack:

             libjvm.so`__1cNSharedRuntimeTdtrace_method_entry6FpnKJavaThread_pnGMethod__i_+0x39c
             SegmentedCodeCacheDtraceTestWorker.baz()V*
             SegmentedCodeCacheDtraceTestWorker.bar()V
             SegmentedCodeCacheDtraceTestWorker.foo()V*
             SegmentedCodeCacheDtraceTestWorker.test()V
             SegmentedCodeCacheDtraceTestWorker.main([Ljava/lang/String;)V
             0xffffffff6b0004b8
             libjvm.so`__1cJJavaCallsLcall_helper6FpnJJavaValue_pnMmethodHandle_pnRJavaCallArguments_pnGThread__v_+0x94c
             libjvm.so`__1cRjni_invoke_static6FpnHJNIEnv__pnJJavaValue_pnI_jobject_nLJNICallType_pnK_jmethodID_pnSJNI_ArgumentPusher_pnGThread__v_+0xa64
             libjvm.so`jni_CallStaticVoidMethod+0x508
             libjli.so`JavaMain+0x584
             libc.so.1`_lwp_start
             jstack:

             libjvm.so`__1cNSharedRuntimeTdtrace_method_entry6FpnKJavaThread_pnGMethod__i_+0x39c
             SegmentedCodeCacheDtraceTestWorker.baz()V*
             SegmentedCodeCacheDtraceTestWorker.bar()V
             SegmentedCodeCacheDtraceTestWorker.foo()V*
             SegmentedCodeCacheDtraceTestWorker.test()V
             SegmentedCodeCacheDtraceTestWorker.main([Ljava/lang/String;)V
             0xffffffff6b0004b8
             libjvm.so`__1cJJavaCallsLcall_helper6FpnJJavaValue_pnMmethodHandle_pnRJavaCallArguments_pnGThread__v_+0x94c
             libjvm.so`__1cRjni_invoke_static6FpnHJNIEnv__pnJJavaValue_pnI_jobject_nLJNICallType_pnK_jmethodID_pnSJNI_ArgumentPusher_pnGThread__v_+0xa64
             libjvm.so`jni_CallStaticVoidMethod+0x508
             libjli.so`JavaMain+0x584
             libc.so.1`_lwp_start
             */
        }

        protected List<String> loadLog(String dtraceOutFile) throws IOException {
            return Files.readAllLines(Paths.get(dtraceOutFile));
        }

        @Override
        public void analyze(OutputAnalyzer oa, String dtraceOutFilePath) {
            oa.shouldHaveExitValue(0);
            List<String> dOut;
            try {
                dOut = loadLog(dtraceOutFilePath);
            } catch (IOException e) {
                throw new Error("Can't load log", e);
            }
            StringBuilder allDtraceOutput = new StringBuilder();
            for (String entry : dOut) {
                allDtraceOutput.append(entry);
            }
            int matchCount = getMatchCount(allDtraceOutput.toString());
            Asserts.assertEQ(matchCount, EXPECTED_MATCH_COUNT,
                    "Unexpected output match amount. expected: "
                    + EXPECTED_MATCH_COUNT + " but found " + matchCount);
        }

        protected int getMatchCount(String source) {
            Matcher m = checkPattern.matcher(source);
            int matchCount = 0;
            while (m.find()) {
                matchCount++;
            }
            return matchCount;
        }
    }
}
