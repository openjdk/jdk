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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaAppDesc;
import jdk.jpackage.test.Main;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary Runs multiple jpackage tool provider instances asynchronously
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror AsyncTest.java
 * @run main/othervm/timeout=1080 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AsyncTest
 */
public class AsyncTest {

    @Test
    public void test() throws Exception {

        // Create test jar only once.
        // Besides of saving time, this avoids asynchronous invocations of java tool provider that randomly fail.
        var appJar = HelloApp.createBundle(JavaAppDesc.parse("Hello!"), TKit.workDir());

        //
        // Run test cases from AsyncInnerTest class asynchronously.
        // Spawn a thread for every test case.
        // Input data for test cases will be cooked asynchronously but in a safe way because every test case has an isolated work directory.
        // Multiple jpackage tool provider instances will be invoked asynchronously.
        //

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            var testFuncNames = List.of("testAppImage", "testNativeBundle");

            var futures = executor.invokeAll(IntStream.range(0, JOB_COUNT).mapToObj(Integer::toString).<Workload>mapMulti((idx, consumer) -> {
                for (var testFuncName : testFuncNames) {
                    var id = String.format("%s(%s)", testFuncName, idx);
                    consumer.accept(new Workload(id, appJar));
                }
            }).toList());

            // Wait for all test cases completion.
            for (var future : futures) {
                future.get(3, TimeUnit.MINUTES);
            }

            var fatalError = Slot.<Exception>createEmpty();

            for (var future : futures) {
                var result = future.get();
                TKit.trace(String.format("[%s] OUTPUT BEGIN\n%s", result.testCaseId(), result.testOutput()));
                TKit.trace(String.format("[%s] OUTPUT END", result.testCaseId()));
                result.exception().filter(Predicate.not(TKit::isSkippedException)).ifPresent(fatalError::set);
            }

            if (fatalError.find().isPresent()) {
                throw fatalError.get();
            }
        }
    }

    public static final class AsyncInnerTest {

        @Test
        @ParameterSupplier("ids")
        public void testAppImage(int id) throws Exception {
            init(new JPackageCommand()).setPackageType(PackageType.IMAGE).executeAndAssertImageCreated();
        }

        @Test
        @ParameterSupplier("ids")
        public void testNativeBundle(int id) throws Exception {
            new PackageTest().addInitializer(AsyncTest::init).run(Action.CREATE_AND_UNPACK);
        }

        public static Collection<Object[]> ids() {
            return IntStream.range(0, JOB_COUNT).mapToObj(Integer::valueOf).map(v -> {
                return new Object[] {v};
            }).toList();
        }
    }

    private static JPackageCommand init(JPackageCommand cmd) {
        return cmd.useToolProvider(true).setFakeRuntime()
                .setDefaultInputOutput()
                .setArgumentValue("--input", APP_JAR.get().getParent())
                .setArgumentValue("--main-jar", APP_JAR.get().getFileName())
                .setArgumentValue("--name", "Foo");
    }


    private record Result(String testOutput, String testCaseId, Optional<Exception> exception) {

        Result {
            Objects.requireNonNull(testOutput);
            Objects.requireNonNull(testCaseId);
            Objects.requireNonNull(exception);
        }
    }


    private record Workload(
            String testCaseId,
            ByteArrayOutputStream outputSink,
            Path appJar) implements Callable<Result> {

        Workload {
            Objects.requireNonNull(testCaseId);
            Objects.requireNonNull(outputSink);
            Objects.requireNonNull(appJar);
        }

        Workload(String testCaseId, Path appJar) {
            this(testCaseId, new ByteArrayOutputStream(), appJar);
        }

        private String testOutput() {
            return new String(outputSink.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override
        public Result call() {
            var runArg = String.format("--jpt-run=%s", AsyncInnerTest.class.getName());

            Optional<Exception> err = Optional.empty();
            try {
                try (var out = new PrintStream(outputSink, false, System.out.charset())) {
                    ScopedValue.where(APP_JAR, appJar).run(() -> {
                        TKit.withOutput(() -> {
                            JPackageCommand.useToolProviderByDefault();
                            Main.main("--jpt-ignore-logfile", runArg, String.format("--jpt-include=%s", testCaseId));
                        }, out, out);
                    });
                }
            } catch (Exception ex) {
                err = Optional.of(ex);
            }
            return new Result(testOutput(), testCaseId, err);
        }
    }

    private static final int JOB_COUNT = 30;
    private static final ScopedValue<Path> APP_JAR = ScopedValue.newInstance();
}
