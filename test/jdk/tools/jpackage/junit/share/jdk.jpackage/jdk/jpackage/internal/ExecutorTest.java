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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.CommandOutputControl.UnexpectedResultException;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMockExit;
import jdk.jpackage.test.mock.CompletableCommandMock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ExecutorTest {

    @ParameterizedTest
    @MethodSource
    public void test_retryOnKnownErrorMessage(RetryOnKnownErrorMessageTestSpec test) {
        test.run();
    }

    private static Stream<RetryOnKnownErrorMessageTestSpec> test_retryOnKnownErrorMessage() {
        var data = new ArrayList<RetryOnKnownErrorMessageTestSpec.Builder>();

        final var subject = "French fries";

        Supplier<RetryOnKnownErrorMessageTestSpec.Builder> build = () -> {
            return RetryOnKnownErrorMessageTestSpec.build().subject(subject);
        };

        for (var exit : Stream.of(CommandMockExit.values()).filter(CommandMockExit::exitNormally).toList()) {
            // These should succeed as there is no "French fries" in stderr.
            Stream.of(
                    build.get().mock(CommandActionSpecs.build().stderr("Coleslaw").exit(exit)),
                    build.get().mock(CommandActionSpecs.build().stdout(subject).exit(exit)),
                    build.get()
                            // Fail in the first attempt (triggering text in the stderr)
                            .mock(CommandActionSpecs.build().stderr(subject).exit())
                            // Fail in the second attempt (same reason)
                            .repeatLastMoc()
                            // Pass in the next attempt (no triggering text in the stderr)
                            .mock(CommandActionSpecs.build().stderr("Coleslaw").exit(exit)),
                   build.get()
                           // Fail in the first attempt (triggering text in the stderr)
                           .mock(CommandActionSpecs.build().stderr(subject))
                           // Fail in the second attempt (error running the command)
                           .mock(CommandActionSpecs.build().exit(CommandMockExit.THROW_MOCK_IO_EXCEPTION))
                           // Pass in the next attempt (no triggering text in the stderr)
                           .mock(CommandActionSpecs.build().exit(exit))
            ).map(RetryOnKnownErrorMessageTestSpec.Builder::success).forEach(data::add);
        }

        // These should fail as there is "French fries" in stderr.
        data.addAll(List.of(
                // Try once and fail.
                build.get().mock(CommandActionSpecs.build().stderr(subject).exit()),
                // Try twice and fail.
                build.get().mock(CommandActionSpecs.build().stderr(subject).exit()).repeatLastMoc()
        ));

        return data.stream().map(RetryOnKnownErrorMessageTestSpec.Builder::create);
    }

    record RetryOnKnownErrorMessageTestSpec(List<CommandActionSpecs> mockSpecs, String subject, boolean success) {

        RetryOnKnownErrorMessageTestSpec {
            Objects.requireNonNull(mockSpecs);
            Objects.requireNonNull(subject);

            if (mockSpecs.isEmpty()) {
                throw new IllegalArgumentException();
            }
        }

        void run() {
            var mock = mockSpecs.stream()
                    .reduce(CommandActionSpecs::andThen)
                    .orElseThrow().toCommandMockBuilder()
                    // Ensure attempts to run the command more times than expected will fail.
                    .noRepeats().create();

            var retry = new Executor().toolProvider(mock).retryOnKnownErrorMessage(subject)
                    .setAttemptTimeout(null)
                    .setMaxAttemptsCount(mockSpecs.size());

            if (success) {
                assertDoesNotThrow(retry::execute);
            } else {
                assertThrowsExactly(UnexpectedResultException.class, retry::execute);
            }

            assertTrue(((CompletableCommandMock)mock).completed());
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            RetryOnKnownErrorMessageTestSpec create() {
                return new RetryOnKnownErrorMessageTestSpec(mockSpecs, subject, success);
            }

            public Builder mock(CommandActionSpecs v) {
                mockSpecs.add(Objects.requireNonNull(v));
                return this;
            }

            public Builder mock(CommandActionSpecs.Builder v) {
                return mock(v.create());
            }

            public Builder repeatLastMoc() {
                return mock(mockSpecs.getLast());
            }

            public Builder subject(String v) {
                subject = v;
                return this;
            }

            public Builder success(boolean v) {
                success = v;
                return this;
            }

            public Builder success() {
                return success(true);
            }

            private final List<CommandActionSpecs> mockSpecs = new ArrayList<>();
            private String subject;
            private boolean success;
        }
    }
}
