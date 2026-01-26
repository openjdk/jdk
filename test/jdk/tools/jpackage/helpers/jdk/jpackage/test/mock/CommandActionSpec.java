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

import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.internal.util.function.ThrowingRunnable;

/**
 * Specification of a {@link CommandAction}.
 * <p>
 * Comprised of a human-readable description and an associated action.
 */
public interface CommandActionSpec {

    String description();
    CommandAction action();

    public static CommandActionSpec create(String description, CommandAction action) {
        return new Internal.DefaultCommandActionSpec(description, action);
    }

    public static CommandActionSpec create(String description, ThrowingSupplier<Integer, Exception> action) {
        Objects.requireNonNull(action);
        return create(description, _ -> {
            return Optional.of(action.get());
        });
    }

    public static CommandActionSpec create(String description, ThrowingRunnable<Exception> action) {
        Objects.requireNonNull(action);
        return create(description, _ -> {
            action.run();
            return Optional.empty();
        });
    }

    @SuppressWarnings("overloads")
    public static CommandActionSpec create(String description, ThrowingConsumer<CommandAction.Context, Exception> action) {
        Objects.requireNonNull(action);
        return create(description, context -> {
            action.accept(context);
            return Optional.empty();
        });
    }

    final class Internal {

        private Internal() {
        }

        private record DefaultCommandActionSpec(String description, CommandAction action) implements CommandActionSpec {
            DefaultCommandActionSpec {
                Objects.requireNonNull(description);
                Objects.requireNonNull(action);
            }

            @Override
            public String toString() {
                return description();
            }
        }
    }
}
