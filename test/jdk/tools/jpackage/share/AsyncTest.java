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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
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
import java.util.spi.ToolProvider;
import java.util.stream.IntStream;
import jdk.jpackage.internal.util.function.ThrowingRunnable;
import jdk.jpackage.internal.util.Slot;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
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
        APP_JAR.set(HelloApp.createBundle(JavaAppDesc.parse("Hello!"), TKit.workDir()));

        //
        // Run test cases from InternalAsyncTest class asynchronously.
        // Spawn a thread for every test case.
        // Input data for test cases will be cooked asynchronously but in a safe way because every test case has an isolated work directory.
        // Multiple jpackage tool provider instances will be invoked asynchronously.
        //

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            var testFuncNames = List.of("testAppImage", "testNativeBundle");

            var runArg = String.format("--jpt-run=%s", AsyncInnerTest.class.getName());

            var futures = executor.invokeAll(IntStream.range(0, JOB_COUNT).mapToObj(Integer::toString).<Workload>mapMulti((idx, consumer) -> {
                for (var testFuncName : testFuncNames) {
                    var id = String.format("%s(%s)", testFuncName, idx);
                    consumer.accept(new Workload(() -> {
                        Main.main(runArg, String.format("--jpt-include=%s", id));
                    }, id));
                }
            }).toList());

            // Wait for all test cases completion.
            for (var future : futures) {
                future.get(3, TimeUnit.MINUTES);
            }

            var fatalError = Slot.<Exception>createEmpty();

            for (var future : futures) {
                var result = future.get();
                TKit.trace(String.format("[%s] STDOUT BEGIN\n%s", result.id(), result.stdoutBuffer()));
                TKit.trace(String.format("[%s] STDOUT END", result.id()));
                TKit.trace(String.format("[%s] STDERR BEGIN\n%s", result.id(), result.stderrBuffer()));
                TKit.trace(String.format("[%s] STDERR END", result.id()));
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


    private record Result(String stdoutBuffer, String stderrBuffer, String id, Optional<Exception> exception) {

        Result {
            Objects.requireNonNull(stdoutBuffer);
            Objects.requireNonNull(stderrBuffer);
            Objects.requireNonNull(id);
            Objects.requireNonNull(exception);
        }
    }


    private record Workload(
            ByteArrayOutputStream stdoutBuffer,
            ByteArrayOutputStream stderrBuffer,
            ThrowingRunnable<? extends Exception> runnable,
            String id) implements Callable<Result>  {

        Workload {
            Objects.requireNonNull(stdoutBuffer);
            Objects.requireNonNull(stderrBuffer);
            Objects.requireNonNull(runnable);
            Objects.requireNonNull(id);
        }

        Workload(ThrowingRunnable<? extends Exception> runnable, String id) {
            this(new ByteArrayOutputStream(), new ByteArrayOutputStream(), runnable, id);
        }

        private String stdoutBufferAsString() {
            return new String(stdoutBuffer.toByteArray(), StandardCharsets.UTF_8);
        }

        private String stderrBufferAsString() {
            return new String(stderrBuffer.toByteArray(), StandardCharsets.UTF_8);
        }

        @Override
        public Result call() {
            // Reset the current test inherited in the state from the parent thread.
            TKit.state(DEFAULT_STATE);

            var defaultToolProvider = JavaTool.JPACKAGE.asToolProvider();

            JPackageCommand.useToolProviderByDefault(new ToolProvider() {

                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    try (var bufOut = new PrintWriter(stdoutBuffer, true, StandardCharsets.UTF_8);
                            var bufErr = new PrintWriter(stderrBuffer, true, StandardCharsets.UTF_8)) {
                        return defaultToolProvider.run(bufOut, bufErr, args);
                    }
                }

                @Override
                public String name() {
                    return defaultToolProvider.name();
                }
            });

            Optional<Exception> err = Optional.empty();
            try (var bufOut = new PrintStream(stdoutBuffer, true, StandardCharsets.UTF_8);
                    var bufErr = new PrintStream(stderrBuffer, true, StandardCharsets.UTF_8)) {
                TKit.withStackTraceStream(() -> {
                    TKit.withMainLogStream(runnable, bufOut);
                }, bufErr);
            } catch (Exception ex) {
                err = Optional.of(ex);
            }
            return new Result(stdoutBufferAsString(), stderrBufferAsString(), id, err);
        }
    }


    private static final int JOB_COUNT = 30;
    private static final TKit.State DEFAULT_STATE = TKit.state();
    private static final InheritableThreadLocal<Path> APP_JAR = new InheritableThreadLocal<>();
}
