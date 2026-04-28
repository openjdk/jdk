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
package jdk.jpackage.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.spi.ToolProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JPackageCommandTest extends JUnitAdapter.TestSrcInitializer {

    @ParameterizedTest
    @MethodSource
    void testUseToolProvider(UseToolProviderTestSpec spec) {
        // Run the test with the new state to avoid UnsupportedOperationException
        // that will be thrown if it attempts to alter global variables in the default R/O state.
        TKit.withNewState(spec::test);
    }

    private static List<UseToolProviderTestSpec> testUseToolProvider() {

        var testCases = new ArrayList<UseToolProviderTestSpec>();

        for (var globalToolProvider : ExecutableSetterType.values()) {
            for (var instanceToolProvider : ExecutableSetterType.values()) {
                testCases.add(new UseToolProviderTestSpec(globalToolProvider, instanceToolProvider));
            }
        }

        return testCases;
    }

    record UseToolProviderTestSpec(ExecutableSetterType globalType, ExecutableSetterType instanceType) {

        UseToolProviderTestSpec {
            Objects.requireNonNull(globalType);
            Objects.requireNonNull(instanceType);
        }

        @Override
        public String toString() {
            return String.format("%s, global=%s", instanceType, globalType);
        }

        void test() {

            final Optional<ToolProvider> global = switch (globalType) {
                case SET_CUSTOM_TOOL_PROVIDER -> {
                    var tp = createNewToolProvider("jpackage-mock-global");
                    JPackageCommand.useToolProviderByDefault(tp);
                    yield Optional.of(tp);
                }
                case SET_DEFAULT_TOOL_PROVIDER -> {
                    JPackageCommand.useToolProviderByDefault();
                    yield Optional.of(JavaTool.JPACKAGE.asToolProvider());
                }
                case SET_PROCESS -> {
                    JPackageCommand.useExecutableByDefault();
                    yield Optional.empty();
                }
                case SET_NONE -> {
                    yield Optional.empty();
                }
            };

            var cmd = new JPackageCommand();

            final Optional<ToolProvider> instance = switch (instanceType) {
                case SET_CUSTOM_TOOL_PROVIDER -> {
                    var tp = createNewToolProvider("jpackage-mock");
                    cmd.useToolProvider(tp);
                    yield Optional.of(tp);
                }
                case SET_DEFAULT_TOOL_PROVIDER -> {
                    cmd.useToolProvider(true);
                    yield Optional.of(JavaTool.JPACKAGE.asToolProvider());
                }
                case SET_PROCESS -> {
                    cmd.useToolProvider(false);
                    yield Optional.empty();
                }
                case SET_NONE -> {
                    yield Optional.empty();
                }
            };

            var actual = cmd.createExecutor().getToolProvider();

            switch (instanceType) {
                case SET_CUSTOM_TOOL_PROVIDER -> {
                    assertSame(actual.get(), instance.get());
                    assertTrue(cmd.isWithToolProvider());
                }
                case SET_DEFAULT_TOOL_PROVIDER -> {
                    global.ifPresentOrElse(expected -> {
                        assertEquals(expected.name(), actual.orElseThrow().name());
                    }, () -> {
                        assertEquals(instance.get().name(), actual.get().name());
                    });
                    assertTrue(cmd.isWithToolProvider());
                }
                case SET_PROCESS -> {
                    assertFalse(actual.isPresent());
                    assertFalse(cmd.isWithToolProvider());
                }
                case SET_NONE -> {
                    switch (globalType) {
                        case SET_CUSTOM_TOOL_PROVIDER -> {
                            assertSame(global.get(), actual.get());
                            assertTrue(cmd.isWithToolProvider());
                        }
                        case SET_DEFAULT_TOOL_PROVIDER -> {
                            assertEquals(global.get().name(), actual.orElseThrow().name());
                            assertTrue(cmd.isWithToolProvider());
                        }
                        case SET_PROCESS, SET_NONE -> {
                            assertFalse(actual.isPresent());
                            assertFalse(cmd.isWithToolProvider());
                        }
                    }
                }
            }
        }

        private static ToolProvider createNewToolProvider(String name) {
            return new ToolProvider() {
                @Override
                public int run(PrintWriter out, PrintWriter err, String... args) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String name() {
                    return name;
                }
            };
        }
    }

    enum ExecutableSetterType {
        SET_DEFAULT_TOOL_PROVIDER,
        SET_CUSTOM_TOOL_PROVIDER,
        SET_PROCESS,
        SET_NONE,
        ;
    }
}
