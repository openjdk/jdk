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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * A sequence of actions.
 */
public record CommandActionSpecs(List<CommandActionSpec> specs) {

    public CommandActionSpecs {
        Objects.requireNonNull(specs);
    }

    public CommandActionSpecs andThen(CommandActionSpecs other) {
        return build().append(this).append(other).create();
    }

    public Stream<CommandAction> actions() {
        return specs.stream().map(CommandActionSpec::action);
    }

    public CommandMock.Builder toCommandMockBuilder() {
        return new CommandMock.Builder().mutate(builder -> {
            builder.actions().append(this);
        });
    }

    @Override
    public String toString() {
        return specs.toString();
    }

    public static Builder build() {
        return new Builder();
    }

    public static final class Builder {

        public CommandActionSpecs create() {
            return new CommandActionSpecs(List.copyOf(specs));
        }

        public Builder stdout(List<String> content) {
            Objects.requireNonNull(content);
            return action(CommandActionSpec.create(String.format("%s>>1", content), context -> {
                var out = context.out();
                content.forEach(out::println);
            }));
        }

        public Builder stdout(String... str) {
            return stdout(List.of(str));
        }

        public Builder stderr(List<String> content) {
            Objects.requireNonNull(content);
            return action(CommandActionSpec.create(String.format("%s>>2", content), context -> {
                var err = context.err();
                content.forEach(err::println);
            }));
        }

        public Builder stderr(String... str) {
            return stderr(List.of(str));
        }

        public Builder printToStdout(List<String> content) {
            Objects.requireNonNull(content);
            return action(CommandActionSpec.create(String.format("%s(no-eol)>>1", content), context -> {
                var out = context.out();
                content.forEach(out::print);
            }));
        }

        public Builder printToStdout(String... str) {
            return printToStdout(List.of(str));
        }

        public Builder printToStderr(List<String> content) {
            Objects.requireNonNull(content);
            return action(CommandActionSpec.create(String.format("%s(no-eol)>>2", content), context -> {
                var err = context.err();
                content.forEach(err::print);
            }));
        }

        public Builder printToStderr(String... str) {
            return printToStderr(List.of(str));
        }

        public Builder exit(int exitCode) {
            return action(CommandActionSpec.create(String.format("exit(%d)", exitCode), () -> {
                return exitCode;
            }));
        }

        public Builder exit() {
            return exit(0);
        }

        public Builder exit(CommandMockExit exit) {
            switch (exit) {
                case SUCCEED -> {
                    return exit();
                }
                case EXIT_1 -> {
                    return exit(1);
                }
                case THROW_MOCK_IO_EXCEPTION -> {
                    return action(CommandActionSpec.create("<I/O error>", () -> {
                        throw new MockingToolProvider.RethrowableException(new MockIOException("Kaput!"));
                    }));
                }
                default -> {
                    throw ExceptionBox.reachedUnreachable();
                }
            }
        }

        public Builder mutate(Consumer<Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        public Builder append(Builder other) {
            return append(other.specs);
        }

        public Builder append(CommandActionSpecs other) {
            return append(other.specs());
        }

        public Builder append(List<CommandActionSpec> other) {
            specs.addAll(other);
            return this;
        }

        public Builder action(CommandActionSpec v) {
            specs.add(Objects.requireNonNull(v));
            return this;
        }

        public Builder copy() {
            return new Builder().append(this);
        }

        public CommandMock.Builder toCommandMockBuilder() {
            return new CommandMock.Builder().mutate(builder -> {
                builder.actions(this);
            });
        }

        private final List<CommandActionSpec> specs = new ArrayList<>();
    }

    public static final CommandActionSpecs UNREACHABLE = new CommandActionSpecs(List.of());
}

