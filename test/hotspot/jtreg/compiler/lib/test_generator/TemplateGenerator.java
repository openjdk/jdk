/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.test_generator;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;


public class TemplateGenerator {

    static final String JAVA_HOME = System.getProperty("java.home");
    // static final String JAVA_HOME = "/Users/tholenst/dev/jdk/build/macosx-aarch64-debug/jdk";
    static final String[] FLAGS = {
            "-Xcomp",
            "-XX:CompileCommand=compileonly,GeneratedTest::test*",
            "-XX:-TieredCompilation",
            "-XX:-CreateCoredumpOnCrash"
    };
    private Method testMethod;
    static final int TIMEOUT = 60;
    static final int NUM_THREADS = Runtime.getRuntime().availableProcessors();
    static final int TESTS_PER_TASK = 10;
    static final int MAX_TASKS_IN_QUEUE = NUM_THREADS * 2;
    static final Random RAND = new Random();

    public void main() throws NoSuchMethodException {
        testMethod = TemplateGenerator.class.getMethod("genNewTest", BigInteger.class, int.class, int.class, int.class, String.class);

        ThreadPoolExecutor threadPool = initializeThreadPool();
        List<Object[]> values = generateParameterValues();
        submitTestTasks(values, threadPool);
        threadPool.shutdown();
    }

    private static ThreadPoolExecutor initializeThreadPool() {
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_TASKS_IN_QUEUE));
        threadPool.setRejectedExecutionHandler((r, executor) -> {
            try {
                executor.getQueue().put(r);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread was interrupted", e);
            }
        });
        return threadPool;
    }

    public List<Object[]> generateParameterValues() {
        Integer[] integerValues = {
                -2, -1, 0, 1, 2,
                Integer.MIN_VALUE - 2, Integer.MIN_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2,
                Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, Integer.MAX_VALUE + 2
        };

        Integer[] integerValuesNonZero = {
                -2, -1, 1, 2,
                Integer.MIN_VALUE - 2, Integer.MIN_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2,
                Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE + 1, Integer.MAX_VALUE + 2
        };

        Long[] longValues = {
                -2L, -1L, 0L, 1L, 2L,
                Integer.MIN_VALUE - 2L, Integer.MIN_VALUE - 1L, (long) Integer.MIN_VALUE, Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 2L,
                Integer.MAX_VALUE - 2L, Integer.MAX_VALUE - 1L, (long) Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 2L,
                Long.MIN_VALUE - 2L, Long.MIN_VALUE - 1L, Long.MIN_VALUE, Long.MIN_VALUE + 1L, Long.MIN_VALUE + 2L,
                Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE, Long.MAX_VALUE + 1L, Long.MAX_VALUE + 2L
        };

        Long[] longValuesNonZero = {
                -2L, -1L, 1L, 2L,
                Integer.MIN_VALUE - 2L, Integer.MIN_VALUE - 1L, (long) Integer.MIN_VALUE, Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 2L,
                Integer.MAX_VALUE - 2L, Integer.MAX_VALUE - 1L, (long) Integer.MAX_VALUE, Integer.MAX_VALUE + 1L, Integer.MAX_VALUE + 2L,
                Long.MIN_VALUE - 2L, Long.MIN_VALUE - 1L, Long.MIN_VALUE, Long.MIN_VALUE + 1L, Long.MIN_VALUE + 2L,
                Long.MAX_VALUE - 2L, Long.MAX_VALUE - 1L, Long.MAX_VALUE, Long.MAX_VALUE + 1L, Long.MAX_VALUE + 2L
        };

        List<Object[]> values = new ArrayList<>();
        for (Annotation[] annotations : testMethod.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {
                switch (annotation) {
                    case IntParam p -> {
                        if (p.values().length == 0) {
                            values.add(p.nonZero() ? integerValuesNonZero : integerValues);
                        } else {
                            Integer[] boxed = IntStream.of(p.values()).boxed().toArray(Integer[]::new);
                            values.add(boxed);
                        }
                    }
                    case LongParam p -> {
                        if (p.values().length == 0) {
                            values.add(p.nonZero() ? longValues : longValuesNonZero);
                        } else {
                            // If specific values are provided, use them instead.
                            Long[] boxed = LongStream.of(p.values()).boxed().toArray(Long[]::new);
                            values.add(boxed);
                        }
                    }
                    case StringParam p -> values.add(p.values());
                    case null, default -> throw new RuntimeException("Unexpected annotation: %s".formatted(annotation));
                }
            }
        }
        return values;
    }

    private static BigInteger computeTotalTestCases(List<Object[]> values) {
        BigInteger numCases = BigInteger.ONE;

        for (Object[] vals : values) {
            numCases = numCases.multiply(BigInteger.valueOf(vals.length));
        }

        if (numCases.signum() < 0) {
            throw new RuntimeException("Negative number of cases (overflow?)");
        }
        System.out.printf("%d cases%n", numCases);
        return numCases;
    }

    private void submitTestTasks(List<Object[]> values, ThreadPoolExecutor threadPool) {
        BigInteger num = BigInteger.ZERO;
        BigInteger numCases = computeTotalTestCases(values);
        while (num.compareTo(numCases) < 0) {
            ArrayList<Object[]> testCases = new ArrayList<>();

            for (int j = 0; j < TESTS_PER_TASK; ++j) {
                BigInteger randomNumber = new BigInteger(numCases.bitLength(), RAND).mod(numCases);
                if (randomNumber.compareTo(numCases) >= 0) {
                    throw new RuntimeException("Invalid test case number %s".formatted(randomNumber));
                }
                testCases.add(getTestCase(values, randomNumber, numCases));
            }

            Runnable task = () -> doWork(testCases);
            threadPool.submit(task);
        }
    }

    public static Object[] getTestCase(List<Object[]> values, BigInteger num, BigInteger numCases) {
        int numParams = values.size();
        Object[] res = new Object[numParams + 1];
        res[0] = num;

        BigInteger remainder = num;
        BigInteger divisor = numCases;

        for (int i = 0; i < numParams; ++i) {
            Object[] vals = values.get(i);
            divisor = divisor.divide(BigInteger.valueOf(vals.length));
            BigInteger valIdx = remainder.divide(divisor);
            int idx = valIdx.intValue();
            remainder = remainder.mod(divisor);
            res[i + 1] = vals[idx];
        }

        return res;
    }

    public void doWork(ArrayList<Object[]> testCases) {
        try {
            CodeSegments segments = prepareCodeSegments(testCases);
            String javaCode = assembleJavaCode(segments);
            String fileName = writeJavaCodeToFile(javaCode);
            ProcessOutput processOutput = executeJavaFile(fileName);
            checkExecutionOutput(processOutput, segments.num);
        } catch (Exception e) {
            throw new RuntimeException("Couldn't find test method: ", e);
        }
    }

    private CodeSegments prepareCodeSegments(ArrayList<Object[]> testCases) {
        String statics = "";
        StringBuilder calls = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        int num = 0;

        for (Object[] testCase : testCases) {
            try {
                Result res = (Result) testMethod.invoke(null, testCase);
                statics = res.statics;
                calls.append(res.call);
                methods.append(res.method);
                methods.append("\n");
                num++;
            } catch (Exception e) {
                throw new RuntimeException("Test generation failed", e);
            }
        }
        return new CodeSegments(statics, calls.toString(), methods.toString(), num);
    }

    public static String fillTemplate(String template, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String pattern = "\\{%s}".formatted(entry.getKey());
            String replacement = entry.getValue();

            // Find the pattern in the template and determine its indentation level
            int index = template.indexOf(pattern);
            if (index != -1) {
                int lineStart = template.lastIndexOf('\n', index);
                String indentation = template.substring(lineStart + 1, index).replaceAll("\\S", "");

                // Add indentation to all lines of the replacement (except the first one)
                String indentedReplacement = replacement.lines()
                        .collect(Collectors.joining("\n%s".formatted(indentation)));

                template = template.replace(pattern, indentedReplacement);
            }
        }
        return template;
    }

    public static Result genNewTest(BigInteger num, @IntParam int init, @IntParam int limit, @IntParam(nonZero = true) int stride, @StringParam(values = {"+", "-"}) String arithm) {
        Result res = new Result();
        res.statics = """
                static long lFld;
                static A a = new A();
                static boolean flag;
                static class A {
                    int i;
                }
                """;

        res.call = "test\\{num}();\n";
        res.call = fillTemplate(res.call, Map.of(
                "num", num.toString()
        ));


        String thing = "synchronized (new Object()) { }\n";

        res.method = """
                 public static void test\\{num}() {
                     long limit = lFld;
                     for (int i =\\{init}; i < \\{limit}; i \\{arithm}= \\{stride}) {
                         // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
                         for (long j = 0; j < limit; j+=2147483648L) {
                             a.i += 34; // NullCheck with a trap on the false path as a reason to peel
                             \\{thing}
                             if (j > 0) { // After peeling: condition always true, loop is folded away.
                                 break;
                             }
                         }
                     }
                 }
                """;

        Map<String, String> replacements = Map.ofEntries(
                Map.entry("num", num.toString()),
                Map.entry("init", Integer.toString(init)),
                Map.entry("limit", Integer.toString(limit)),
                Map.entry("arithm", arithm),
                Map.entry("stride", Integer.toString(stride)),
                Map.entry("thing", thing)
        );
        res.method = fillTemplate(res.method, replacements);

        return res;
    }

    private static String assembleJavaCode(CodeSegments segments) {
        String template = """
                import java.util.Objects;

                public class GeneratedTest {
                    \\{statics}

                    public static void main(String args[]) throws Exception {
                        \\{calls}
                        System.out.println("Passed");
                    }

                    \\{methods}
                }
                """;

        Map<String, String> replacements = Map.ofEntries(
                Map.entry("statics", segments.statics),
                Map.entry("calls", segments.calls),
                Map.entry("methods", segments.methods)
        );

        return fillTemplate(template, replacements);
    }

    private static String writeJavaCodeToFile(String javaCode) throws IOException {
        String fileName = String.format("GeneratedTest%d.java", Thread.currentThread().threadId());
        BufferedWriter writer = new BufferedWriter(new FileWriter(fileName));
        writer.write(javaCode);
        writer.close();
        return fileName;
    }

    private static ProcessOutput executeJavaFile(String fileName) throws IOException, InterruptedException {
        ProcessBuilder builder = getProcessBuilder(fileName);

        Process process = builder.start();
        boolean exited = process.waitFor(TIMEOUT, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
            throw new RuntimeException("Process timeout: execution took too long.");
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();

        return new ProcessOutput(exitCode, output);
    }

    private static ProcessBuilder getProcessBuilder(String fileName) {
        List<String> command = new ArrayList<>();

        String javaPath = "%s/bin/java".formatted(JAVA_HOME);
        command.add(javaPath);
        command.addAll(Arrays.asList(FLAGS));
        command.add("-cp");
        command.add(".");
        command.add(fileName);

        ProcessBuilder builder = new ProcessBuilder(command);

        builder.redirectErrorStream(true);
        return builder;
    }

    private static void checkExecutionOutput(ProcessOutput output, int num) {
        System.out.printf("Processed %d test cases.%n", num);
        if (output.getExitCode() == 0 && Objects.requireNonNull(output.getOutput()).contains("Passed")) {
            System.out.println("All tests passed successfully.");
        } else {
            System.err.println("Some tests failed:");
            System.err.println(output.getOutput());
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface IntParams {
        IntParam[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Repeatable(IntParams.class)
    public @interface IntParam {
        boolean nonZero() default false;
        int[] values() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface LongParams {
        LongParam[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Repeatable(LongParams.class)
    public @interface LongParam {
        boolean nonZero() default false;
        long[] values() default {};
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface StringParams {
        StringParam[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Repeatable(StringParams.class)
    public @interface StringParam {
        String[] values() default {};
    }

    public static class Result {
        public String statics;
        public String call;
        public String method;
    }

    public static class CodeSegments {
        private final String statics;
        private final String calls;
        private final String methods;
        private final int num;

        public CodeSegments(String statics, String calls, String methods, int num) {
            this.statics = statics;
            this.calls = calls;
            this.methods = methods;
            this.num = num;
        }
    }

    public record ProcessOutput(int exitCode, String output) {
        public String getOutput() {
            return output;
        }

        public int getExitCode() {
            return exitCode;
        }
    }
}
