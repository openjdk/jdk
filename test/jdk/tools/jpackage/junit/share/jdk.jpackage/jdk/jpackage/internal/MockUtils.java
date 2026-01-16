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

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;

import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.spi.ToolProvider;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.CliBundlingEnvironment;
import jdk.jpackage.internal.cli.Main;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.mock.ToolProviderCommandMock;
import jdk.jpackage.test.mock.VerbatimCommandMock;

/**
 * Bridges "jdk.jpackage.internal" and "jdk.jpackage.test.mock" packages.
 */
public final class MockUtils {

    private MockUtils() {
    }

    public static JPackageToolProviderBuilder buildJPackage() {
        return new JPackageToolProviderBuilder();
    }

    public static final class JPackageToolProviderBuilder {

        public ToolProvider create() {
            return createJPackageToolProvider(os(), createObjectFactory());
        }

        public Consumer<Globals> createGlobalsMutator() {
            var objectFactory = createObjectFactory();
            return globals -> {
                globals.objectFactory(objectFactory);
            };
        }

        public void applyToGlobals() {
            createGlobalsMutator().accept(Globals.instance());
        }

        ExecutorFactory createExecutorFactory() {
            var commandMocksExecutorFactory = Optional.ofNullable(script).map(MockUtils::withCommandMocks).map(mapper -> {
                return mapper.apply(ExecutorFactory.DEFAULT);
            }).orElse(ExecutorFactory.DEFAULT);

            var recordingExecutorFactory = Optional.ofNullable(listener).map(MockUtils::withCommandListener).map(mapper -> {
                return mapper.apply(commandMocksExecutorFactory);
            }).orElse(commandMocksExecutorFactory);

            return recordingExecutorFactory;
        }

        ObjectFactory createObjectFactory() {
            var executorFactory = createExecutorFactory();
            if (executorFactory == ExecutorFactory.DEFAULT) {
                return ObjectFactory.DEFAULT;
            } else {
                return ObjectFactory.build().executorFactory(executorFactory).create();
            }
        }

        public JPackageToolProviderBuilder listener(Consumer<List<String>> v) {
            listener = v;
            return this;
        }

        public JPackageToolProviderBuilder script(Script v) {
            script = v;
            return this;
        }

        public JPackageToolProviderBuilder os(OperatingSystem v) {
            os = v;
            return this;
        }

        private OperatingSystem os() {
            return Optional.ofNullable(os).orElseGet(OperatingSystem::current);
        }

        private Consumer<List<String>> listener;
        private OperatingSystem os;
        private Script script;
    }

    public static ToolProvider createJPackageToolProvider(OperatingSystem os, Script script) {
        return buildJPackage()
                .os(Objects.requireNonNull(os))
                .script(Objects.requireNonNull(script))
                .create();
    }

    public static ToolProvider createJPackageToolProvider(Script script) {
        return createJPackageToolProvider(OperatingSystem.current(), script);
    }

    private static UnaryOperator<ExecutorFactory> withCommandListener(Consumer<List<String>> listener) {
        Objects.requireNonNull(listener);
        return executorFactory -> {
            Objects.requireNonNull(executorFactory);
            return () -> {
                var executor = executorFactory.executor();

                Optional<UnaryOperator<Executor>> oldMapper = executor.mapper();

                UnaryOperator<Executor> newMapper = exec -> {
                    listener.accept(exec.commandLine());
                    return exec;
                };

                return executor.mapper(oldMapper.map(newMapper::compose).orElse(newMapper)::apply);
            };
        };
    }

    private static UnaryOperator<ExecutorFactory> withCommandMocks(Script script) {
        return executorFactory -> {
            Objects.requireNonNull(executorFactory);
            return () -> {
                var executor = executorFactory.executor();

                Optional<UnaryOperator<Executor>> oldMapper = executor.mapper();

                UnaryOperator<Executor> newMapper = exec -> {
                    var commandLine = exec.commandLine();
                    var mock = Objects.requireNonNull(script.map(commandLine));
                    switch (mock) {
                        case VerbatimCommandMock.INSTANCE -> {
                            // No mock for this command line.
                            return exec;
                        }
                        case ToolProviderCommandMock tp -> {
                            // Create a copy of the executor with the old mapper to prevent further recursion.
                            var copy = exec.copy().mapper(oldMapper.orElse(null));
                            copy.toolProvider(tp);
                            copy.args().clear();
                            copy.args(commandLine.subList(1, commandLine.size()));
                            return copy;
                        }
                        default -> {
                            // Unreachable because there are no other cases for this switch.
                            throw ExceptionBox.reachedUnreachable();
                        }
                    }
                };

                return executor.mapper(oldMapper.map(newMapper::compose).orElse(newMapper)::apply);
            };
        };
    }

    public static CliBundlingEnvironment createBundlingEnvironment(OperatingSystem os) {
        Objects.requireNonNull(os);

        String bundlingEnvironmentClassName;
        switch (os) {
            case WINDOWS -> {
                bundlingEnvironmentClassName = "WinBundlingEnvironment";
            }
            case LINUX -> {
                bundlingEnvironmentClassName = "LinuxBundlingEnvironment";
            }
            case MACOS -> {
                bundlingEnvironmentClassName = "MacBundlingEnvironment";
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }

        return toSupplier(() -> {
            var ctor = Class.forName(String.join(".",
                    DefaultBundlingEnvironment.class.getPackageName(),
                    bundlingEnvironmentClassName
            )).getConstructor();
            return (CliBundlingEnvironment)ctor.newInstance();
        }).get();
    }

    static ToolProvider createJPackageToolProvider(OperatingSystem os, ObjectFactory of) {
        Objects.requireNonNull(os);
        Objects.requireNonNull(of);

        var impl = new Main.Provider(DefaultBundlingEnvironment.runOnce(() -> {
            return createBundlingEnvironment(os);
        }));

        return new ToolProvider() {

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                return Globals.main(() -> {
                    Globals.instance().objectFactory(of);
                    return impl.run(out, err, args);
                });
            }

            @Override
            public String name() {
                return impl.name();
            }
        };
    }
}
