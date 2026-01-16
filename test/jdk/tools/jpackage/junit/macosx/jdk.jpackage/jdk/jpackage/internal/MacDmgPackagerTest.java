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

import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.MacPackagingPipeline.MacBuildApplicationTaskID;
import jdk.jpackage.internal.PackagingPipeline.BuildApplicationTaskID;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.RuntimeBuilder;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedResultException;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.RetryExecutor;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.mock.CommandActionSpec;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.MockIllegalStateException;
import jdk.jpackage.test.mock.ScriptSpec;
import jdk.jpackage.test.mock.ScriptSpecInDir;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class MacDmgPackagerTest {

    /**
     * Exercise branches in {@link MacDmgPackager#buildDMG()}.
     */
    @ParameterizedTest
    @MethodSource
    public void test(DmgScript scriptSpec, @TempDir Path workDir) {
        scriptSpec.run(workDir);
    }

    private static List<DmgScript> test() {
        var data = new ArrayList<DmgScript>();

        var succeed = CommandActionSpecs.build().exit().create();
        var fail = CommandActionSpecs.build().exit(1).create();

        // Test create
        for (var createFullSucceed : List.of(true, false)) {
            var dmgScript = new DmgScript();

            var scriptBuilder = ScriptSpec.build();

            if (createFullSucceed) {
                // `hdiutil create -srcfolder` succeeds
                scriptBuilder.add(new CommandMockSpec("hdiutil", "hdiutil-create", dmgScript.hdiutilCreate().exit().create()));
            } else {
                // `hdiutil create -srcfolder` fails
                scriptBuilder.add(new CommandMockSpec("hdiutil", "hdiutil-create", fail));
                scriptBuilder.add(new CommandMockSpec("hdiutil", "hdiutil-create-empty", dmgScript.hdiutilCreateEmpty().exit().create()));
            }

            scriptBuilder
                    // `hdiutil attach` succeeds
                    .add(new CommandMockSpec("hdiutil", "hdiutil-attach", succeed))
                    // `osascript` succeeds
                    .add(new CommandMockSpec("osascript", succeed))
                    // `hdiutil detach` succeeds
                    .add(new CommandMockSpec("hdiutil", "hdiutil-detach", dmgScript.hdiutilDetach().exit().create()))
                    // `hdiutil convert` succeeds
                    .add(new CommandMockSpec("hdiutil", "hdiutil-convert", dmgScript.hdiutilConvert().exit().create()));

            data.add(dmgScript.scriptSpec(scriptBuilder.create()));
        }

        // Test detach
        for (var detachResult : DetachResult.values()) {
            var dmgScript = new DmgScript();

            var scriptBuilder = ScriptSpec.build()
                    .add(new CommandMockSpec("hdiutil", "hdiutil-create", dmgScript.hdiutilCreate().exit().create()))
                    .add(new CommandMockSpec("hdiutil", "hdiutil-attach", succeed))
                    .add(new CommandMockSpec("osascript", succeed));

            switch (detachResult) {
                case ALL_FAIL -> {
                    dmgScript.expect(UnexpectedResultException.class);
                    scriptBuilder.build(new CommandMockSpec("hdiutil", "hdiutil-detach", fail)).repeat(9).add();
                }
                case LAST_SUCCEED -> {
                    scriptBuilder
                            .build(new CommandMockSpec("hdiutil", "hdiutil-detach", fail)).repeat(8).add()
                            .add(new CommandMockSpec("hdiutil", "hdiutil-detach", dmgScript.hdiutilDetach().exit().create()))
                            .add(new CommandMockSpec("hdiutil", "hdiutil-convert", dmgScript.hdiutilConvert().exit().create()));
                }
                case FIRST_SUCCEED_WITH_EXIT_1 -> {
                    scriptBuilder
                            .build(new CommandMockSpec("hdiutil", "hdiutil-detach", dmgScript.hdiutilDetach().exit(1).create()))
                                    .detailedDescription().add()
                            .add(new CommandMockSpec("hdiutil", "hdiutil-convert", dmgScript.hdiutilConvert().exit().create()));
                }
                case FIRST_SUCCEED_MOUNT_POINT_REMAINS -> {
                    scriptBuilder
                            .build(new CommandMockSpec("hdiutil", "hdiutil-detach", dmgScript.hdiutilDetach(false).exit().create()))
                                    .detailedDescription().add()
                            .add(new CommandMockSpec("hdiutil", "hdiutil-convert", dmgScript.hdiutilConvert().exit().create()));
                }
            }

            data.add(dmgScript.scriptSpec(scriptBuilder.create()));
        }

        return data;
    }

    private enum DetachResult {
        ALL_FAIL,
        LAST_SUCCEED,
        // The first `hdiutil detach` attempt exits with exit code "1" but deletes the mounted directory.
        FIRST_SUCCEED_WITH_EXIT_1,
        // The first `hdiutil detach` attempt exits with exit code "0" and the mounted directory stays undeleted.
        FIRST_SUCCEED_MOUNT_POINT_REMAINS,
        ;
    }

    private static MacDmgSystemEnvironment createSysEnv(ScriptSpec scriptSpec) {
        return new MacDmgSystemEnvironment(
                Path.of("hdiutil"),
                Path.of("osascript"),
                Stream.of("SetFile").map(Path::of).filter(scriptSpec.commandNames()::contains).findFirst()
        );
    }

    private static RuntimeBuilder createRuntimeBuilder() {
        return new RuntimeBuilder() {
            @Override
            public void create(AppImageLayout appImageLayout) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static void runPackagingMock(Path workDir, MacDmgSystemEnvironment sysEnv) {

        var app = new ApplicationBuilder()
                .appImageLayout(MacPackagingPipeline.APPLICATION_LAYOUT)
                .runtimeBuilder(createRuntimeBuilder())
                .name("foo")
                .create();

        var macApp = new MacApplicationBuilder(app).create();

        var macDmgPkg = new MacDmgPackageBuilder(new MacPackageBuilder(new PackageBuilder(macApp, MAC_DMG))).create();

        var buildEnv = new BuildEnvBuilder(workDir.resolve("build-root")).appImageDirFor(macDmgPkg).create();

        var packager = new MacDmgPackager(buildEnv, macDmgPkg, workDir, sysEnv);

        var pipelineBuilder = MacPackagingPipeline.build(Optional.of(packager.pkg()));
        packager.accept(pipelineBuilder);

        // Disable actions of tasks filling an application image.
        Stream.concat(
                Stream.of(BuildApplicationTaskID.values()),
                Stream.of(MacBuildApplicationTaskID.values())
        ).forEach(taskId -> {
            pipelineBuilder.task(taskId).noaction().add();
        });

        var contentMock = new ContentMock();

        // Fill application image with content mock.
        pipelineBuilder.task(BuildApplicationTaskID.CONTENT).applicationAction(env -> {
            contentMock.create(env.resolvedLayout().contentDirectory());
        }).add();

        pipelineBuilder.create().execute(buildEnv, packager.pkg(), packager.outputDir());

        var outputDmg = packager.outputDir().resolve(packager.pkg().packageFileNameWithSuffix());

        contentMock.verifyStoredInFile(outputDmg);
    }

    private static final class DmgScript extends ScriptSpecInDir {

        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append(super.toString());
            Optional.ofNullable(expectedErrorType).ifPresent(type -> {
                sb.append("; ").append(type.getCanonicalName());
            });
            return sb.toString();
        }

        @Override
        public DmgScript scriptSpec(ScriptSpec v) {
            super.scriptSpec(v);
            return this;
        }

        DmgScript expect(Class<? extends Exception> v) {
            expectedErrorType = v;
            return this;
        }

        void run(Path workDir) {

            var script = dir(Objects.requireNonNull(workDir)).create();

            ExecutorFactory executorFactory = MockUtils.buildJPackage()
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
                if (expectedErrorType == null) {
                    runPackagingMock(workDir, createSysEnv(scriptSpec()));
                } else {
                    var ex = assertThrows(Exception.class, () -> {
                        runPackagingMock(workDir, createSysEnv(scriptSpec()));
                    });
                    var cause = ExceptionBox.unbox(ex);
                    assertEquals(expectedErrorType, cause.getClass());
                }
                return 0;
            });

            assertEquals(List.of(), script.incompleteMocks());
        }

        CommandActionSpecs.Builder hdiutilCreate(boolean empty) {
            CommandActionSpec action = CommandActionSpec.create("create", context -> {
                var dstDmg = Path.of(context.optionValue("-ov"));
                assertTrue(isPathInDir(dstDmg));

                var volumeName = context.optionValue("-volname");

                if (empty) {
                    createDmg(new CreateDmgResult(dstDmg, volumeName, Optional.empty()));
                    Files.createFile(dstDmg);
                } else {
                    var srcDir = Path.of(context.optionValue("-srcfolder"));
                    assertTrue(isPathInDir(srcDir));

                    createDmg(new CreateDmgResult(dstDmg, volumeName, Optional.of(srcDir)));

                    try (var walk = Files.walk(srcDir)) {
                        var paths = walk.map(srcDir::relativize).map(Path::toString).toList();
                        Files.write(dstDmg, paths);
                    }
                }
            });
            return CommandActionSpecs.build().action(action);
        }

        CommandActionSpecs.Builder hdiutilCreate() {
            return hdiutilCreate(false);
        }

        CommandActionSpecs.Builder hdiutilCreateEmpty() {
            return hdiutilCreate(true);
        }

        CommandActionSpecs.Builder hdiutilDetach() {
            return hdiutilDetach(true);
        }

        CommandActionSpecs.Builder hdiutilDetach(boolean deleteMountPoint) {
            var sb = new StringBuilder();
            sb.append("detach");
            if (!deleteMountPoint) {
                sb.append("(rm-mount-point)");
            }
            CommandActionSpec action = CommandActionSpec.create(sb.toString(), context -> {
                var mountPoint = Path.of(context.args().getLast());
                assertTrue(isPathInDir(mountPoint));

                try (var walk = Files.walk(mountPoint)) {
                    var dstDmg = dmg().dmg();
                    var paths = Stream.concat(
                            walk.map(mountPoint::relativize),
                            Files.readAllLines(dstDmg).stream().filter(Predicate.not(String::isEmpty)).map(Path::of)
                    ).sorted().map(Path::toString).toList();
                    Files.write(dstDmg, paths);
                }

                if (deleteMountPoint) {
                    FileUtils.deleteRecursive(mountPoint);
                }
            });
            return CommandActionSpecs.build().action(action);
        }

        CommandActionSpecs.Builder hdiutilConvert() {
            CommandActionSpec action = CommandActionSpec.create("convert", context -> {
                var srcDmg = Path.of(context.args().get(1));
                assertTrue(isPathInDir(srcDmg));

                var dstDmg = Path.of(context.args().getLast());
                assertTrue(isPathInDir(dstDmg));

                Files.copy(srcDmg, dstDmg);
            });
            return CommandActionSpecs.build().action(action);
        }

        private void createDmg(CreateDmgResult v) {
            if (dmg != null) {
                throw new MockIllegalStateException("The DMG already set");
            } else {
                dmg = Objects.requireNonNull(v);
            }
        }

        private CreateDmgResult dmg() {
            if (dmg == null) {
                throw new MockIllegalStateException("The DMG not set");
            } else {
                return dmg;
            }
        }

        private record CreateDmgResult(Path dmg, String volumeName, Optional<Path> srcFolder) {
            CreateDmgResult {
                Objects.requireNonNull(dmg);
                Objects.requireNonNull(volumeName);
                Objects.requireNonNull(srcFolder);
            }
        }

        private CreateDmgResult dmg;
        private Class<? extends Exception> expectedErrorType;
    }

    private static final class ContentMock {

        void create(Path dir) throws IOException {
            Files.createDirectories(dir.resolve("foo/bar"));
            Files.writeString(dir.resolve("foo/bar/buz"), "Hello!");
            if (!OperatingSystem.isWindows()) {
                Files.createSymbolicLink(dir.resolve("symlink"), Path.of("foo"));
            }
        }

        void verifyStoredInFile(Path file) {
            try {
                var expectedPaths = Stream.of(
                        Stream.of(Path.of("")),
                        DMG_ICON_FILES.stream(),
                        Stream.of(
                                Stream.of("foo/bar/buz"),
                                Stream.of("symlink").filter(_ -> {
                                    return !OperatingSystem.isWindows();
                                })
                        ).flatMap(x -> x).map(Path::of).map(Path.of("foo.app/Contents")::resolve)
                ).flatMap(x -> x).mapMulti(EXPAND_PATH).sorted().distinct().toList();
                var actualPaths = Files.readAllLines(file).stream().map(Path::of).toList();
                assertEquals(expectedPaths, actualPaths);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    private final static BiConsumer<Path, Consumer<Path>> EXPAND_PATH = (path, sink) -> {
        do {
            sink.accept(path);
            path = path.getParent();
        } while (path != null);
    };

    private final static List<Path> DMG_ICON_FILES = Stream.of(
            ".VolumeIcon.icns",
            ".background/background.tiff"
    ).map(Path::of).collect(Collectors.toUnmodifiableList());
}
