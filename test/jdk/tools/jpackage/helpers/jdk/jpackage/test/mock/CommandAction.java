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

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An action.
 */
@FunctionalInterface
public interface CommandAction {

    public record Context(PrintStream out, PrintStream err, List<String> args) {

        public Context {
            Objects.requireNonNull(out);
            Objects.requireNonNull(err);
            args.forEach(Objects::requireNonNull);
        }

        public Optional<String> findOptionValue(String option) {
            Objects.requireNonNull(option);
            var idx = args.indexOf(option);
            if (idx >= 0 && idx + 1 < args.size()) {
                return Optional.of(args.get(idx + 1));
            } else {
                return Optional.empty();
            }
        }

        public String optionValue(String option) {
            return findOptionValue(option).orElseThrow(() -> {
                throw new MockIllegalStateException(String.format("No option %s", option));
            });
        }

        public MockIllegalStateException unexpectedArguments() {
            return new MockIllegalStateException(String.format("Unexpected arguments: %s", args));
        }
    }

    /**
     * Runs the action in the given context.
     *
     * @param context the context
     * @return an {@code Optional} wrapping the exit code, indicating it is the last
     *         action in the sequence or an empty {@code Optional} otherwise
     * @throws Exception                 simulates a failure
     * @throws MockIllegalStateException if error in internal mock logic occurred.
     *                                   E.g.: if the action was called unexpectedly
     */
    Optional<Integer> run(Context context) throws Exception, MockIllegalStateException;
}
