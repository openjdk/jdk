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
package jdk.jpackage.internal.cli;

import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.util.Result;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.TKit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs some ErrorTest test cases from an IDE on a mockup jpackage tool
 * provider.
 * <p>
 * ErrorTest is comprised of test cases that pass faulty command line arguments
 * to jpackage and expect it to fail. It is natural to reuse these test cases to
 * test jpackage command line validation.
 * <p>
 * ErrorTest provides better test coverage, but it is a jtreg test, it runs
 * slowly and there is no integration of jtreg tests in IDEs. This JUnit test
 * class leverages ErrorTest test cases to test jpackage command line validation
 * from an IDE.
 *
 * <p>
 * The scenario breaks down into two components:
 * <ol>
 * <li>Load ErrorTest jtreg test
 * <li>Setup custom jpackage tool provider
 * </ol>
 *
 * <h1>1. Load ErrorTest jtreg test</h1>
 * <p>
 * Build OptionsValidationFailTest, ErrorTest and its nested classes such that
 * they reside in the unnamed module together with the classes from the
 * "jdk.jpackage.test" package.
 * <p>
 * There is no straightforward way to access classes in the unnamed package
 * outside their module. However, these classes (class files) can be accessed as
 * resources. So the workaround is to read class files of classes in the unnamed
 * package and load them using a custom class loader.
 * <p>
 * jpackage jtreg tests are in the unnamed package according to jtreg
 * recommendations, see
 * <a href="https://openjdk.org/jtreg/faq.html#how-should-i-organize-tests-libraries-and-other-test-related-files">jtreg FAQ</a>
 */
@EnabledIf("jtregErrorTestAvailable")
public class OptionsValidationFailTest {

    private static ToolProvider createToolProvider() {
        return new ToolProvider() {

            @Override
            public String name() {
                return "jpackage-mockup";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {

                var errorReporter = new Main.ErrorReporter(ex -> {
                    ex.printStackTrace(err);
                }, err::println, false);

                return parse(args).peekErrors(errors -> {
                    final var firstErr = errors.stream().findFirst().orElseThrow();
                    errorReporter.reportError(firstErr);
                }).map(builder -> {
                    var result = new OptionsProcessor(builder, bundlingEnv).validate();
                    if (result.hasValue()) {
                        return 0;
                    } else {
                        result.peekErrors(errors -> {
                            errors.forEach(errorReporter::reportError);
                        });
                        return 1;
                    }
                }).value().orElse(1);
            }

            private Result<JOptSimpleOptionsBuilder.OptionsBuilder> parse(String... args) {
                return Utils.buildParser(OperatingSystem.current(), bundlingEnv).create().apply(args);
            }

            private final CliBundlingEnvironment bundlingEnv = new CliBundlingEnvironment() {

                @Override
                public Optional<BundlingOperationDescriptor> defaultOperation() {
                    switch (OperatingSystem.current()) {
                        case WINDOWS -> {
                            return Optional.of(StandardBundlingOperation.CREATE_WIN_EXE.descriptor());
                        }
                        case LINUX -> {
                            return Optional.of(StandardBundlingOperation.CREATE_LINUX_RPM.descriptor());
                        }
                        case MACOS -> {
                            return Optional.of(StandardBundlingOperation.CREATE_MAC_PKG.descriptor());
                        }
                        default -> {
                            throw new UnsupportedOperationException();
                        }
                    }
                }

                @Override
                public void createBundle(BundlingOperationDescriptor op, Options cmdline) {
                    throw new AssertionError();
                }

            };
        };
    }

    @TestFactory
    Stream<DynamicTest> getTestCasesFromErrorTest() throws Exception {
        final var jpackageTestsUnnamedModule = JUnitAdapter.class.getModule();

        final var testClassloader = new InMemoryClassLoader(Stream.of(
                "ErrorTest",
                "ErrorTest$ArgumentGroup",
                "ErrorTest$PackageTypeSpec",
                "ErrorTest$TestSpec$Builder",
                "ErrorTest$TestSpec",
                "ErrorTest$Token",
                "ErrorTest$UnsupportedPlatformOption"
        ).collect(Collectors.toMap(x -> x, className -> {
            try (final var in = Objects.requireNonNull(jpackageTestsUnnamedModule.getResourceAsStream(className + ".class"))) {
                final var buffer= new ByteArrayOutputStream();
                in.transferTo(buffer);
                return buffer.toByteArray();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        })));

        final var thisModule = getClass().getModule();
        if (thisModule.isNamed()) {
            for (final var m : List.of(testClassloader.getUnnamedModule(), jpackageTestsUnnamedModule)) {
                thisModule.addOpens("jdk.jpackage.internal", m);
            }
        }

        final var defaultExcludesFile = TKit.TEST_SRC_ROOT.resolve(String.format(
                "junit/share/jdk.jpackage/%s.excludes", OptionsValidationFailTest.class.getName().replace(".", "/")));

        final var defaultExcludes = Files.readAllLines(defaultExcludesFile).stream().map(testDesc -> {
            return "--jpt-exclude=" + testDesc;
        });

        final var defaultIncludes = Stream.of(
//              "ErrorTest.test(NATIVE; args-add=[--runtime-image, @@JAVA_HOME@@, --app-image, app-image]; errors=[ERR_MutuallyExclusiveOptions+[--runtime-image, --app-image]])"
        ).map(testDesc -> {
            return "--jpt-include=" + testDesc;
        });

        final var jpackageToolProviderMock = createToolProvider();

        return JUnitAdapter.createJPackageTests(testClassloader, Stream.of(
                defaultExcludes,
                defaultIncludes,
                Stream.of("--jpt-run=ErrorTest")
        ).flatMap(x -> x).toArray(String[]::new)).map(dynamicTest -> {
            return DynamicTest.dynamicTest(dynamicTest.getDisplayName(), () -> {
                TKit.withNewState(() -> {
                    JPackageCommand.useToolProviderByDefault(jpackageToolProviderMock);
                    try {
                        dynamicTest.getExecutable().execute();
                    } catch (Throwable t) {
                        throw ExceptionBox.toUnchecked(ExceptionBox.unbox(t));
                    }
                });
            });
        });
    }

    private static boolean jtregErrorTestAvailable() {
        final var jpackageTestsUnnamedModule = JUnitAdapter.class.getModule();

        try (final var in = jpackageTestsUnnamedModule.getResourceAsStream("ErrorTest.class")) {
            return in != null;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final class InMemoryClassLoader extends ClassLoader {
        InMemoryClassLoader(Map<String, byte[]> classes) {
            super(InMemoryClassLoader.class.getClassLoader());
            this.classes = Objects.requireNonNull(classes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            final var classBytes = classes.get(name);
            if (classBytes != null) {
                return defineClass(name, classBytes, 0, classBytes.length);
            } else {
                return getParent().loadClass(name);
            }
        }

        private final Map<String, byte[]> classes;
    }

    static {
        // Ensure JUnitAdapter class is initialized to get the value of the "test.src"
        // property set when the test is executed by a test runner other than jtreg.
        toRunnable(() -> MethodHandles.lookup().ensureInitialized(JUnitAdapter.class)).run();
    }
}
