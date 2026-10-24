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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.IdentityWrapper;

/**
 * Script of command mocks.
 */
public interface Script {

    /**
     * Returns a command mock for the given command line.
     *
     * @param cmdline the command line for which to look up a command mock
     *
     * @return a command mock matching the given command line
     * @throws ScriptException if an internal script error occures
     */
    CommandMock map(List<String> cmdline) throws ScriptException;

    /**
     * Returns command mocks registered with this object that have not completed yet.
     *
     * @See {@link CompletableCommandMock#completed()}
     *
     * @return the command mocks registered with this object that have not completed yet
     */
    Collection<CompletableCommandMock> incompleteMocks();

    public static Builder build() {
        return new Builder();
    }

    public static <T> Predicate<List<String>> cmdlinePredicate(
            Predicate<T> pred,
            Function<String, T> conv,
            Function<List<String>, Stream<String>> toStream) {

        Objects.requireNonNull(pred);
        Objects.requireNonNull(conv);
        Objects.requireNonNull(toStream);

        return cmdline -> {
            return toStream.apply(cmdline).map(conv).filter(pred).findFirst().isPresent();
        };
    }

    public static Predicate<List<String>> cmdlineContains(String arg) {
        return cmdlinePredicate(Predicate.isEqual(Objects.requireNonNull(arg)), x -> x, List::stream);
    }

    public static Predicate<List<String>> cmdlineContains(Path arg) {
        return cmdlinePredicate(Predicate.<Path>isEqual(Objects.requireNonNull(arg)), Path::of, List::stream);
    }

    public static Predicate<List<String>> cmdlineStartsWith(String arg) {
        return cmdlinePredicate(Predicate.isEqual(Objects.requireNonNull(arg)), x -> x, cmdline -> {
            return cmdline.stream().limit(1);
        });
    }

    public static Predicate<List<String>> cmdlineStartsWith(Path arg) {
        return cmdlinePredicate(Predicate.<Path>isEqual(Objects.requireNonNull(arg)), Path::of, cmdline -> {
            return cmdline.stream().limit(1);
        });
    }

    public final class ScriptException extends RuntimeException {

        ScriptException(RuntimeException cause) {
            super(Objects.requireNonNull(cause));
        }

        ScriptException(String msg) {
            super(Objects.requireNonNull(msg));
        }

        private static final long serialVersionUID = 1L;
    }

    public final class Builder {

        public Script createSequence() {
            return new SequenceScript(List.copyOf(instructions), completableMocks());
        }

        public Script createLoop() {
            return new LoopScript(List.copyOf(instructions), completableMocks());
        }

        public Builder map(Predicate<List<String>> pred, CommandMock mock) {
            Objects.requireNonNull(pred);
            Objects.requireNonNull(mock);
            if (mock instanceof CompletableCommandMock completable) {
                completableMocks.add(new IdentityWrapper<>(completable));
            }
            instruction(cmdline -> {
                if (pred.test(cmdline)) {
                    return new CommandMockResult(Optional.of(mock));
                } else {
                    return new CommandMockResult(Optional.empty());
                }
            });
            return this;
        }

        public Builder map(Predicate<List<String>> pred, CommandMock.Builder mock) {
            Optional.ofNullable(commandMockBuilderMutator).ifPresent(mock::mutate);
            return map(pred, mock.create());
        }

        public Builder map(Predicate<List<String>> pred, CommandMockSpec mock) {
            return map(pred, mock.toCommandMockBuilder());
        }

        public Builder map(CommandMockSpec mock) {
            return map(cmdlineStartsWith(mock.name()), mock.toCommandMockBuilder());
        }

        public Builder use(CommandMock mock) {
            return map(_ -> true, mock);
        }

        public Builder use(Predicate<List<String>> pred, CommandMock.Builder mock) {
            return map(_ -> true, mock);
        }

        public Builder use(Predicate<List<String>> pred, CommandMockSpec mock) {
            return map(_ -> true, mock);
        }

        public Builder branch(Predicate<List<String>> pred, Script script) {
            Objects.requireNonNull(pred);
            Objects.requireNonNull(script);
            instruction(cmdline -> {
                if (pred.test(cmdline)) {
                    return new ScriptResult(script);
                } else {
                    return new CommandMockResult(Optional.empty());
                }
            });
            return this;
        }

        public Builder commandMockBuilderMutator(Consumer<CommandMock.Builder> v) {
            commandMockBuilderMutator = v;
            return this;
        }

        public Builder mutate(Consumer<Script.Builder> mutator) {
            mutator.accept(this);
            return this;
        }

        private Builder instruction(Function<List<String>, Result> instruction) {
            instructions.add(Objects.requireNonNull(instruction));
            return this;
        }

        private Collection<CompletableCommandMock> completableMocks() {
            return completableMocks.stream().map(IdentityWrapper::value).toList();
        }

        private static RuntimeException noMapping(List<String> cmdline) {
            return new ScriptException(String.format("Mapping for %s command line not found", cmdline));
        }

        private sealed interface Result {
        }

        private record CommandMockResult(Optional<CommandMock> value) implements Result {
            CommandMockResult {
                Objects.requireNonNull(value);
            }
        }

        private record ScriptResult(Script value) implements Result {
            ScriptResult {
                Objects.requireNonNull(value);
            }
        }

        private abstract static class AbstractScript implements Script {

            AbstractScript(Collection<CompletableCommandMock> completableMocks) {
                this.completableMocks = Objects.requireNonNull(completableMocks);
            }

            @Override
            public Collection<CompletableCommandMock> incompleteMocks() {
                return completableMocks.stream().filter(Predicate.not(CompletableCommandMock::completed)).toList();
            }

            private final Collection<CompletableCommandMock> completableMocks;
        }

        private static final class LoopScript extends AbstractScript {

            LoopScript(List<Function<List<String>, Result>> instructions,
                    Collection<CompletableCommandMock> completableMocks) {
                super(completableMocks);
                this.instructions = Objects.requireNonNull(instructions);
            }

            @Override
            public CommandMock map(List<String> cmdline) {
                for (var instruction : instructions) {
                    switch (instruction.apply(cmdline)) {
                        case CommandMockResult result -> {
                            var mock = result.value();
                            if (mock.isPresent()) {
                                return mock.get();
                            }
                        }
                        case ScriptResult result -> {
                            return result.value().map(cmdline);
                        }
                    }
                }

                throw noMapping(cmdline);
            }

            private final List<Function<List<String>, Result>> instructions;
        }

        private static final class SequenceScript extends AbstractScript {

            SequenceScript(List<Function<List<String>, Result>> instructions,
                    Collection<CompletableCommandMock> completableMocks) {
                super(completableMocks);
                this.iter = instructions.iterator();
            }

            @Override
            public CommandMock map(List<String> cmdline) {
                if (!iter.hasNext()) {
                    throw new ScriptException("No more mappings");
                } else {
                    switch (iter.next().apply(cmdline)) {
                        case CommandMockResult result -> {
                            var mock = result.value();
                            if (mock.isPresent()) {
                                return mock.get();
                            }
                        }
                        case ScriptResult result -> {
                            return result.value().map(cmdline);
                        }
                    }
                }

                throw noMapping(cmdline);
            }

            private final Iterator<Function<List<String>, Result>> iter;
        }

        private Consumer<CommandMock.Builder> commandMockBuilderMutator = CommandMock.Builder::noRepeats;
        private final List<Function<List<String>, Result>> instructions = new ArrayList<>();
        private final Set<IdentityWrapper<CompletableCommandMock>> completableMocks = new HashSet<>();
    }
}
