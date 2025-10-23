/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static jdk.internal.util.OperatingSystem.MACOS;
import static jdk.internal.util.OperatingSystem.WINDOWS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;


/**
 * Tests generation of packages with additional content in app image.
 */

/*
 * @test
 * @summary jpackage with --app-content option
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build AppContentTest
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppContentTest
 */
public class AppContentTest {

    private static final ContentFactory TEST_JAVA = createTestSrcContent("apps/PrintEnv.java");
    private static final ContentFactory TEST_DUKE = createTextFileContent("duke.txt", "Hi Duke!");
    private static final ContentFactory TEST_DIR = createTestSrcContent("apps");
    private static final ContentFactory TEST_BAD = new NonExistantPath();

    // On OSX `--app-content` paths will be copied into the "Contents" folder
    // of the output app image.
    // "codesign" imposes restrictions on the directory structure of "Contents" folder.
    // In particular, random files should be placed in "Contents/Resources" folder
    // otherwise "codesign" will fail to sign.
    // Need to prepare arguments for `--app-content` accordingly.
    private static final boolean copyInResources = TKit.isOSX();

    private static final String RESOURCES_DIR = "Resources";

    public static Collection<Object[]> test() {
        return Stream.of(
                build().add(TEST_JAVA).add(TEST_DUKE),
                build().add(TEST_JAVA).add(TEST_BAD),
                build().startGroup().add(TEST_JAVA).add(TEST_DUKE).endGroup().add(TEST_DIR),
                // Same directory specified multiple times.
                build().add(TEST_DIR).add(TEST_DIR),
                // Same file specified multiple times.
                build().add(TEST_JAVA).add(TEST_JAVA),
                // Two files with the same name but different content.
                build().add(createTextFileContent("welcome.txt", "Welcome")).add(createTextFileContent("welcome.txt", "Benvenuti")),
                // Same name: one is a directory, another is a file.
                build().add(createTextFileContent("a/b", "Foo")).add(createTextFileContent("a", "Bar")),
                // Same name: one is a file, another is a directory.
                build().add(createTextFileContent("a", "Bar")).add(createTextFileContent("a/b", "Foo"))
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    public static Collection<Object[]> testSymlink() {
        return Stream.of(
                build().add(TEST_JAVA)
                        .add(new SymlinkContentFactory("Links", "duke-link", "duke-target"))
                        .add(new SymlinkContentFactory("", "a/b/foo-link", "c/bar-target"))
        ).map(TestSpec.Builder::create).map(v -> {
            return new Object[] {v};
        }).toList();
    }

    @Test
    @ParameterSupplier
    @ParameterSupplier(value="testSymlink", ifNotOS = WINDOWS)
    public void test(TestSpec testSpec) throws Exception {
        testSpec.test(new ConfigurationTarget(new PackageTest().configureHelloApp()));
    }

    @Test
    @ParameterSupplier("test")
    @ParameterSupplier(value="testSymlink", ifNotOS = WINDOWS)
    public void testAppImage(TestSpec testSpec) throws Exception {
        testSpec.test(new ConfigurationTarget(JPackageCommand.helloAppImage()));
    }

    @Test(ifOS = MACOS)
    @Parameter({"apps", "warning.non.standard.contents.sub.dir"})
    @Parameter({"apps/dukeplug.png", "warning.app.content.is.not.dir"})
    public void testWarnings(String testPath, String warningId) throws Exception {
        final var appContentValue = TKit.TEST_SRC_ROOT.resolve(testPath);
        final var expectedWarning = JPackageStringBundle.MAIN.cannedFormattedString(
                warningId, appContentValue);

        JPackageCommand.helloAppImage()
            .addArguments("--app-content", appContentValue)
            .setFakeRuntime()
            .validateOutput(expectedWarning)
            .executeIgnoreExitCode();
    }

    public record TestSpec(List<List<ContentFactory>> contentFactories) {
        public TestSpec {
            contentFactories.stream().flatMap(List::stream).forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return contentFactories.stream().map(group -> {
                return group.stream().map(ContentFactory::toString).collect(joining(","));
            }).collect(joining("; "));
        }

        void test(ConfigurationTarget target) {
            final int expectedJPackageExitCode;
            if (contentFactories.stream().flatMap(List::stream).anyMatch(TEST_BAD::equals)) {
                expectedJPackageExitCode = 1;
            } else {
                expectedJPackageExitCode = 0;
            }

            final List<List<Content>> allContent = new ArrayList<>();

            target.addInitializer(JPackageCommand::setFakeRuntime)
            .addRunOnceInitializer(_ -> {
                contentFactories.stream().map(group -> {
                    return group.stream().map(ContentFactory::create).toList();
                }).forEach(allContent::add);
            }).addInitializer(cmd -> {
                allContent.stream().map(group -> {
                    return Stream.of("--app-content", group.stream()
                            .map(Content::paths)
                            .flatMap(List::stream)
                            .map(Path::toString)
                            .collect(joining(",")));
                    }).flatMap(x -> x).forEachOrdered(cmd::addArgument);
            });

            target.cmd().ifPresent(cmd -> {
                if (expectedJPackageExitCode == 0) {
                    cmd.executeAndAssertImageCreated();
                } else {
                    cmd.execute(expectedJPackageExitCode);
                }
            });

            target.addInstallVerifier(cmd -> {
                var appContentRoot = getAppContentRoot(cmd);

                var verifiers = allContent.stream().flatMap(List::stream).flatMap(content -> {
                    return StreamSupport.stream(content.verifiers(appContentRoot).spliterator(), false);
                }).collect(toMap(PathVerifier::path, x -> x, (_, second) -> {
                    // Override
                    return second;
                }, TreeMap::new)).values();

                verifiers.forEach(PathVerifier::verify);
            });

            target.test().ifPresent(test -> {
                test.setExpectedExitCode(expectedJPackageExitCode).run();
            });
        }

        static final class Builder {
            TestSpec create() {
                return new TestSpec(groups);
            }

            final class GroupBuilder {
                GroupBuilder add(ContentFactory cf) {
                    group.add(Objects.requireNonNull(cf));
                    return this;
                }

                Builder endGroup() {
                    if (!group.isEmpty()) {
                        groups.add(group);
                    }
                    return Builder.this;
                }

                private final List<ContentFactory> group = new ArrayList<>();
            }

            Builder add(ContentFactory cf) {
                return startGroup().add(cf).endGroup();
            }

            GroupBuilder startGroup() {
                return new GroupBuilder();
            }

            private final List<List<ContentFactory>> groups = new ArrayList<>();
        }
    }

    private static TestSpec.Builder build() {
        return new TestSpec.Builder();
    }

    private static Path getAppContentRoot(JPackageCommand cmd) {
        final Path contentDir = cmd.appLayout().contentDirectory();
        if (copyInResources) {
            return contentDir.resolve(RESOURCES_DIR);
        } else {
            return contentDir;
        }
    }

    private static Path createAppContentRoot() {
        if (copyInResources) {
            return TKit.createTempDirectory("app-content").resolve(RESOURCES_DIR);
        } else {
            return TKit.createTempDirectory("app-content");
        }
    }

    @FunctionalInterface
    private interface ContentFactory {
        Content create();
    }

    private interface Content {
        List<Path> paths();
        Iterable<PathVerifier> verifiers(Path appContentRoot);
    }

    private static abstract class PathVerifier {

        PathVerifier(Path path) {
            this.path = Objects.requireNonNull(path);
        }

        final Path path() {
            return path;
        }

        abstract void verify();

        private final Path path;
    }

    private static PathVerifier verifyRegularFile(Path path, Path srcFile) {
        Objects.requireNonNull(srcFile);
        return new PathVerifier(path) {
            @Override
            void verify() {
                TKit.assertSameFileContent(srcFile, path());
            }
        };
    }

    private static PathVerifier verifyDirectory(Path path) {
        return new PathVerifier(path) {
            @Override
            void verify() {
                TKit.assertDirectoryExists(path);
            }
        };
    }

    private record FileContent(Path path, int level) implements Content {

        FileContent {
            Objects.requireNonNull(path);
            if (level < 0) {
                throw new IllegalArgumentException();
            }
            if (path.getNameCount() <= level) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public List<Path> paths() {
            return List.of(appContentOptionValue());
        }

        @Override
        public Iterable<PathVerifier> verifiers(Path appContentRoot) {
            List<PathVerifier> verifiers = new ArrayList<>();

            var appContentPath = appContentRoot.resolve(pathInAppContentRoot());

            if (Files.isDirectory(path)) {
                try (var walk = Files.walk(path)) {
                    verifiers.addAll(walk.map(srcFile -> {
                        var dstFile = appContentPath.resolve(path.relativize(srcFile));
                        if (Files.isRegularFile(srcFile)) {
                            return verifyRegularFile(dstFile, srcFile);
                        } else {
                            return verifyDirectory(dstFile);
                        }
                    }).toList());
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            } else if (Files.isRegularFile(path)) {
                verifiers.add(verifyRegularFile(appContentPath, path));
            } else {
                verifiers.add(new PathVerifier(appContentPath) {
                    @Override
                    public void verify() {
                        TKit.assertPathExists(path(), false);
                    }
                });
            }

            if (level > 0) {
                var cur = appContentPath;
                for (int i = 0; i != level; i++) {
                    cur = cur.getParent();
                    verifiers.add(verifyDirectory(cur));
                }
            }

            return verifiers;
        }

        private Path appContentOptionValue() {
            var cur = path;
            for (int i = 0; i != level; i++) {
                cur = cur.getParent();
            }
            return cur;
        }

        private Path pathInAppContentRoot() {
            return StreamSupport.stream(path.spliterator(), false)
                    .skip(path.getNameCount() - level - 1)
                    .reduce(Path::resolve).orElseThrow();
        }

        private static boolean isDirectoryEmpty(Path path) {
            try (var files = Files.list(path)) {
                return files.findAny().isEmpty();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    /**
     * Non-existing content.
     */
    private static final class NonExistantPath implements ContentFactory {
        @Override
        public Content create() {
            var nonExistant = Path.of("non-existant-" + Integer.toHexString(new Random().ints(100, 200).findFirst().getAsInt()));
            return new FileContent(nonExistant, 0);
        }

        @Override
        public String toString() {
            return "*non-existant*";
        }
    }

    /**
     * Creates a content from {@link TKit#TEST_SRC_ROOT} directory.
     *
     * @param path source path relative to {@link TKit#TEST_SRC_ROOT} directory
     */
    private static ContentFactory createTestSrcContent(Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException();
        }

        return new FileContentFactory(() -> {
            return TKit.TEST_SRC_ROOT.resolve(path);
        }, path);
    }

    private static ContentFactory createTestSrcContent(String path) {
        return createTestSrcContent(Path.of(path));
    }

    /**
     * Creates a content from a text file.
     *
     * @param path the path where to copy the text file in app image's content directory
     * @param lines the content of the source text file
     */
    private static ContentFactory createTextFileContent(Path path, String ... lines) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException();
        }

        return new FileContentFactory(() -> {
            var srcPath = TKit.createTempDirectory("content").resolve(path);
            Files.createDirectories(srcPath.getParent());
            TKit.createTextFile(srcPath, Stream.of(lines));
            return srcPath;
        }, path);
    }

    private static ContentFactory createTextFileContent(String path, String ... lines) {
        return createTextFileContent(Path.of(path), lines);
    }

    /**
     * Symlink content factory.
     *
     * @path basedir the directory where to write the content in app image's content
     *       directory
     * @param symlink   the path to the symlink relative to {@code basedir} path
     * @param symlinked the path to the source file for the symlink
     */
    private record SymlinkContentFactory(Path basedir, Path symlink, Path symlinked) implements ContentFactory {
        SymlinkContentFactory {
            for (final var path : List.of(basedir, symlink, symlinked)) {
                if (path.isAbsolute()) {
                    throw new IllegalArgumentException();
                }
            }
        }

        SymlinkContentFactory(String basedir, String symlink, String symlinked) {
            this(Path.of(basedir), Path.of(symlink), Path.of(symlinked));
        }

        @Override
        public Content create() {
            final var appContentRoot = createAppContentRoot();

            final var symlinkPath = appContentRoot.resolve(symlinkPath());
            final var symlinkedPath = appContentRoot.resolve(symlinkedPath());
            try {
                Files.createDirectories(symlinkPath.getParent());
                Files.createDirectories(symlinkedPath.getParent());
                // Create the target file for the link.
                Files.writeString(symlinkedPath, symlinkedPath().toString());
                // Create the link.
                Files.createSymbolicLink(symlinkPath, symlinkTarget());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }

            List<Path> contentPaths;
            if (copyInResources) {
                contentPaths = List.of(appContentRoot);
            } else if (basedir.equals(Path.of(""))) {
                contentPaths = Stream.of(symlinkPath(), symlinkedPath()).map(path -> {
                    return path.getName(0);
                }).map(appContentRoot::resolve).toList();
            } else {
                contentPaths = List.of(appContentRoot.resolve(basedir));
            }

            return new Content() {
                @Override
                public List<Path> paths() {
                    return contentPaths;
                }

                @Override
                public Iterable<PathVerifier> verifiers(Path appContentRoot) {
                    return List.of(
                            verifyRegularFile(appContentRoot.resolve(symlinkedPath()), symlinkedPath),
                            new PathVerifier(appContentRoot.resolve(symlinkPath())) {
                                @Override
                                void verify() {
                                    TKit.assertSymbolicLinkTarget(path(), symlinkTarget());
                                }
                            }
                    );
                }
            };
        }

        @Override
        public String toString() {
            return String.format("symlink:[%s]->[%s][%s]", symlinkPath(), symlinkedPath(), symlinkTarget());
        }

        private Path symlinkPath() {
            return basedir.resolve(symlink);
        }

        private Path symlinkedPath() {
            return basedir.resolve(symlinked);
        }

        private Path symlinkTarget() {
            return Optional.ofNullable(symlinkPath().getParent()).map(dir -> {
                return dir.relativize(symlinkedPath());
            }).orElseGet(this::symlinkedPath);
        }
    }

    private static final class FileContentFactory implements ContentFactory {

        FileContentFactory(ThrowingSupplier<Path> factory, Path pathInAppContentRoot) {
            this.factory = ThrowingSupplier.toSupplier(factory);
            this.pathInAppContentRoot = pathInAppContentRoot;
            if (pathInAppContentRoot.isAbsolute()) {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Content create() {
            Path srcPath = factory.get();
            if (!srcPath.endsWith(pathInAppContentRoot)) {
                throw new IllegalArgumentException();
            }

            Path dstPath;
            if (!copyInResources) {
                dstPath = srcPath;
            } else {
                var contentDir = createAppContentRoot();
                dstPath = contentDir.resolve(srcPath.getFileName());
                try {
                    FileUtils.copyRecursive(srcPath, dstPath);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
            return new FileContent(dstPath, pathInAppContentRoot.getNameCount() - 1);
        }

        @Override
        public String toString() {
            return pathInAppContentRoot.toString();
        }

        private final Supplier<Path> factory;
        private final Path pathInAppContentRoot;
    }

}
