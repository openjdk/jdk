/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.PackagingPipeline.PackageTaskID;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedExitCodeException;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedResultException;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.ScriptSpec;
import jdk.jpackage.test.stdmock.JPackageMockUtils;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LinuxDebPackagerTest {

    /**
     * Exercise {@link LinuxDebPackager#buildPackage()}.
     */
    @ParameterizedTest
    @MethodSource
    void test_buildPackage(TestSpec testSpec, @TempDir Path workDir) {
        testSpec.run(workDir);
    }

    record TestSpec(ScriptSpec scriptSpec, Optional<Class<? extends Exception>> expectedErrorType) {

        TestSpec {
            Objects.requireNonNull(scriptSpec);
            Objects.requireNonNull(expectedErrorType);
        }

        TestSpec(ScriptSpec scriptSpec) {
            this(scriptSpec, Optional.empty());
        }

        TestSpec(ScriptSpec scriptSpec, Class<? extends Exception> expectedErrorType) {
            this(scriptSpec, Optional.of(expectedErrorType));
        }

        void run(Path workDir) {

            var script = scriptSpec.create();

            ExecutorFactory executorFactory = JPackageMockUtils.buildJPackage()
                    .script(script).listener(System.out::println).createExecutorFactory();

            var objectFactory = ObjectFactory.build()
                    .executorFactory(executorFactory)
                    .retryExecutorFactory(new RetryExecutorFactory() {
                        @Override
                        public <T, E extends Exception> RetryExecutor<T, E> retryExecutor(Class<? extends E> exceptionType) {
                            return RetryExecutorFactory.DEFAULT.<T, E>retryExecutor(exceptionType).setSleepFunction(_ -> {
                                // Don't "sleep" to make the test run faster.
                            });
                        }
                    })
                    .create();

            Globals.main(() -> {
                Globals.instance().objectFactory(objectFactory);

                expectedErrorType.ifPresentOrElse(v -> {
                    var ex = assertThrows(Exception.class, () -> {
                        runPackagingMock(workDir);
                    });

                    var cause = ExceptionBox.unbox(ex);

                    assertEquals(v, cause.getClass());
                }, () -> {
                    assertDoesNotThrow(() -> {
                        runPackagingMock(workDir);
                    });
                });

                return 0;
            });

            assertEquals(List.of(), script.incompleteMocks());
        }
    }

    private static Collection<TestSpec> test_buildPackage() {

        Collection<TestSpec> testCases = new ArrayList<>();

        testCases.add(new TestSpec(
                ScriptSpec.build()
                        .build(new CommandMockSpec("fakeroot", CommandActionSpecs.build().exit().create()))
                        .detailedDescription().add()
                        .create()));

        testCases.add(new TestSpec(
                ScriptSpec.build()
                        .build(new CommandMockSpec("fakeroot", CommandActionSpecs.build().exit(1).create()))
                        .detailedDescription().add()
                        .create(),
                UnexpectedExitCodeException.class));

        testCases.add(new TestSpec(
                ScriptSpec.build()
                        .build(new CommandMockSpec("fakeroot", CommandActionSpecs.build()
                                .stderr("semop(1): encountered an error: Invalid argument")
                                .exit(1).create()))
                        .repeat(4).detailedDescription().add()
                        .create(),
                UnexpectedResultException.class));

        testCases.add(new TestSpec(
                ScriptSpec.build()
                        .build(new CommandMockSpec("fakeroot", CommandActionSpecs.build()
                                .stderr("semop(1): encountered an error: Invalid argument")
                                .exit(1).create()))
                        .repeat(3).detailedDescription().add()
                        .build(new CommandMockSpec("fakeroot", CommandActionSpecs.build().exit().create()))
                        .detailedDescription().add()
                        .create()));

        return testCases;
    }

    private static LinuxDebSystemEnvironment dummySysEnv() {

        var linuxSysEnv = new LinuxSystemEnvironment.Stub(false, LINUX_DEB, new LinuxPackageArch("acme"), _ -> {
            throw new AssertionError();
        });
        var debMixin = new LinuxDebSystemEnvironmentMixin.Stub(Path.of("dpkg"), Path.of("dpkg-deb"), Path.of("fakeroot"));

        return LinuxSystemEnvironment.mixin(
                LinuxDebSystemEnvironment.class,
                Result.ofValue(linuxSysEnv),
                () -> Result.ofValue(debMixin)).orElseThrow();
    }

    private static void runPackagingMock(Path workDir) {

        var app = new ApplicationBuilder()
                .appImageLayout(RuntimeLayout.DEFAULT)
                .name("foo").create();

        var sysEnv = dummySysEnv();

        var pkg = new LinuxDebPackageBuilder(
                new LinuxPackageBuilder(new PackageBuilder(app, LINUX_DEB))
                        .arch(sysEnv.packageArch())
        ).create();

        var buildEnv = new BuildEnvBuilder(workDir.resolve("build-root")).appImageDirFor(pkg).create();

        var packager = new LinuxDebPackager(buildEnv, pkg, workDir, dummySysEnv());

        var pipelineBuilder = LinuxPackagingPipeline.build(Optional.of(pkg));
        packager.accept(pipelineBuilder);

        // Disable actions of tasks we don't care about.
        pipelineBuilder.configuredTasks().filter(taskBuilder -> {
            return (taskBuilder.task() != PackageTaskID.CREATE_PACKAGE_FILE);
        }).forEach(taskBuilder -> {
            taskBuilder.noaction().add();
        });

        pipelineBuilder.create().execute(buildEnv, pkg, workDir);
    }
}
