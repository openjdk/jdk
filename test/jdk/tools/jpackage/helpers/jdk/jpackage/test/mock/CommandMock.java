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
package jdk.jpackage.test.mock;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Command mock.
 */
public sealed interface CommandMock permits ToolProviderCommandMock, VerbatimCommandMock, CompletableCommandMock {

    public static CommandMock ioerror(String name) {
        return CommandActionSpecs.build()
                .exit(CommandMockExit.THROW_MOCK_IO_EXCEPTION)
                .toCommandMockBuilder().name(Objects.requireNonNull(name)).create();
    }

    public static CommandMock fail(String name) {
        return CommandActionSpecs.build()
                .exit(CommandMockExit.EXIT_1)
                .toCommandMockBuilder().name(Objects.requireNonNull(name)).create();
    }

    public static CommandMock succeed(String name) {
        return CommandActionSpecs.build()
                .exit(CommandMockExit.SUCCEED)
                .toCommandMockBuilder().name(Objects.requireNonNull(name)).create();
    }

    public static CommandMock unreachable() {
        return MockingToolProvider.UNREACHABLE;
    }

    public final class Builder {

        public ToolProviderCommandMock create() {

            var actionSpecs = Optional.ofNullable(scriptBuilder)
                    .map(CommandActionSpecs.Builder::create)
                    .orElse(CommandActionSpecs.UNREACHABLE);
            if (actionSpecs.equals(CommandActionSpecs.UNREACHABLE)) {
                return (ToolProviderCommandMock)unreachable();
            }

            var theName = Optional.ofNullable(name).orElse("mock");
            var script = actionSpecs.actions().toList();
            switch (repeat) {
                case 0 -> {
                    return MockingToolProvider.create(theName, script);
                }
                case -1 -> {
                    return MockingToolProvider.createLoop(theName, script);
                }
                default -> {
                    var repeatedScript = IntStream.rangeClosed(0, repeat)
                            .mapToObj(i -> script)
                            .flatMap(List::stream)
                            .toList();
                    return MockingToolProvider.create(theName, repeatedScript);
                }
            }
        }

        public Builder name(String v) {
            name = v;
            return this;
        }

        public Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        public Builder repeat(int v) {
            repeat = Integer.max(-1, v);
            return this;
        }

        public Builder noRepeats() {
            return repeat(0);
        }

        public Builder repeatInfinitely() {
            return repeat(-1);
        }

        public Builder actions(CommandActionSpecs.Builder v) {
            scriptBuilder = Optional.ofNullable(v).orElseGet(CommandActionSpecs::build);
            return this;
        }

        public CommandActionSpecs.Builder actions() {
            if (scriptBuilder == null) {
                scriptBuilder = CommandActionSpecs.build();
            }
            return scriptBuilder;
        }

        private String name;
        private int repeat = -1;
        private CommandActionSpecs.Builder scriptBuilder;
    }

}
