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
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import jdk.jpackage.internal.util.function.ExceptionBox;

/**
 * A command simulator implementing {@code ToolProvider}.
 * <p>
 * Iterates over actions and runs them. Each action is write to stdout/stderr, create a file, etc.
 */
abstract sealed class MockingToolProvider implements ToolProviderCommandMock {

    MockingToolProvider(String name, Iterator<CommandAction> actionIter) {
        this.name = Objects.requireNonNull(name);
        this.actionIter = Objects.requireNonNull(actionIter);
    }

    static ToolProviderCommandMock createLoop(String name, Iterable<CommandAction> actions) {
        return new MockingToolProvider.NonCompletable(name, actions);
    }

    static MockingToolProvider create(String name, Iterable<CommandAction> actions) {
        return new MockingToolProvider.Completable(name, actions);
    }

    public boolean completed() {
        return !actionIter.hasNext();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int run(PrintStream out, PrintStream err, String... args) {
        var context = new CommandAction.Context(out, err, List.of(args));
        try {
            while (actionIter.hasNext()) {
                var action = actionIter.next();
                var reply = action.run(context);
                if (reply.isPresent()) {
                    return reply.get();
                }
            }
        } catch (RethrowableException ex) {
            // Let the checked exception out.
            throwAny(ex.getCause());
            // Unreachable
            return 0;
        } catch (Exception ex) {
            throw ExceptionBox.toUnchecked(ex);
        }

        // No more actions to execute, but still expect it to keep going.
        throw new MockIllegalStateException("No more actions to execute");
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        throw new UnsupportedOperationException();
    }

    static final class RethrowableException extends Exception {

        RethrowableException(Exception ex) {
            super(Objects.requireNonNull(ex));
        }

        private static final long serialVersionUID = 1L;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwAny(Throwable e) throws E {
        throw (E)e;
    }

    private static final class LoopIterator<T> implements Iterator<T> {

        LoopIterator(Iterable<T> iterable) {
            this.iterable = Objects.requireNonNull(iterable);
            rewind();
        }

        @Override
        public boolean hasNext() {
            return iter != null;
        }

        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            } else if (iter.hasNext()) {
                return iter.next();
            } else {
                rewind();
                if (!hasNext()) {
                    throw new NoSuchElementException();
                } else {
                    return iter.next();
                }
            }
        }

        private void rewind() {
            iter = Objects.requireNonNull(iterable.iterator());
            if (!iter.hasNext()) {
                iter = null;
            }
        }

        private final Iterable<T> iterable;
        private Iterator<T> iter;
    }

    static final class NonCompletable extends MockingToolProvider {

        NonCompletable(String name, Iterable<CommandAction> actions) {
            super(name, new LoopIterator<>(actions));
        }

    }

    static final class Completable extends MockingToolProvider implements ToolProviderCompletableCommandMock {

        Completable(String name, Iterable<CommandAction> actions) {
            super(name, actions.iterator());
        }

    }

    private final String name;
    private final Iterator<CommandAction> actionIter;

    static ToolProviderCommandMock UNREACHABLE = new MockingToolProvider.NonCompletable("<unreachable>", List.of());
}
