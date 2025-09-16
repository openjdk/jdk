/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.util.concurrent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.invoke.MhUtil;

/**
 * Built-in StructuredTaskScope.Joiner implementations.
 */
class Joiners {
    private Joiners() { }

    /**
     * Throws IllegalArgumentException if the subtask is not in the UNAVAILABLE state.
     */
    private static void ensureUnavailable(Subtask<?> subtask) {
        if (subtask.state() != Subtask.State.UNAVAILABLE) {
            throw new IllegalArgumentException("Subtask not in UNAVAILABLE state");
        }
    }

    /**
     * Throws IllegalArgumentException if the subtask has not completed.
     */
    private static Subtask.State ensureCompleted(Subtask<?> subtask) {
        Subtask.State state = subtask.state();
        if (state == Subtask.State.UNAVAILABLE) {
            throw new IllegalArgumentException("Subtask has not completed");
        }
        return state;
    }

    /**
     * A joiner that returns a stream of all subtasks when all subtasks complete
     * successfully. Cancels the scope if any subtask fails.
     */
    static final class AllSuccessful<T> implements Joiner<T, Stream<Subtask<T>>> {
        private static final VarHandle FIRST_EXCEPTION =
                MhUtil.findVarHandle(MethodHandles.lookup(), "firstException", Throwable.class);

        // list of forked subtasks, only accessed by owner thread
        private final List<Subtask<T>> subtasks = new ArrayList<>();

        private volatile Throwable firstException;

        @Override
        public boolean onFork(Subtask<? extends T> subtask) {
            ensureUnavailable(subtask);
            @SuppressWarnings("unchecked")
            var s = (Subtask<T>) subtask;
            subtasks.add(s);
            return false;
        }

        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            Subtask.State state = ensureCompleted(subtask);
            return (state == Subtask.State.FAILED)
                    && (firstException == null)
                    && FIRST_EXCEPTION.compareAndSet(this, null, subtask.exception());
        }

        @Override
        public Stream<Subtask<T>> result() throws Throwable {
            Throwable ex = firstException;
            if (ex != null) {
                throw ex;
            } else {
                return subtasks.stream();
            }
        }
    }

    /**
     * A joiner that returns the result of the first subtask to complete successfully.
     * Cancels the scope if any subtasks succeeds.
     */
    static final class AnySuccessful<T> implements Joiner<T, T> {
        private static final VarHandle SUBTASK =
                MhUtil.findVarHandle(MethodHandles.lookup(), "subtask", Subtask.class);

        // UNAVAILABLE < FAILED < SUCCESS
        private static final Comparator<Subtask.State> SUBTASK_STATE_COMPARATOR =
                Comparator.comparingInt(AnySuccessful::stateToInt);

        private volatile Subtask<T> subtask;

        /**
         * Maps a Subtask.State to an int that can be compared.
         */
        private static int stateToInt(Subtask.State s) {
            return switch (s) {
                case UNAVAILABLE -> 0;
                case FAILED      -> 1;
                case SUCCESS     -> 2;
            };
        }

        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            Subtask.State state = ensureCompleted(subtask);
            Subtask<T> s;
            while (((s = this.subtask) == null)
                    || SUBTASK_STATE_COMPARATOR.compare(s.state(), state) < 0) {
                if (SUBTASK.compareAndSet(this, s, subtask)) {
                    return (state == Subtask.State.SUCCESS);
                }
            }
            return false;
        }

        @Override
        public T result() throws Throwable {
            Subtask<T> subtask = this.subtask;
            if (subtask == null) {
                throw new NoSuchElementException("No subtasks completed");
            }
            return switch (subtask.state()) {
                case SUCCESS -> subtask.get();
                case FAILED  -> throw subtask.exception();
                default      -> throw new InternalError();
            };
        }
    }

    /**
     * A joiner that that waits for all successful subtasks. Cancels the scope if any
     * subtask fails.
     */
    static final class AwaitSuccessful<T> implements Joiner<T, Void> {
        private static final VarHandle FIRST_EXCEPTION =
                MhUtil.findVarHandle(MethodHandles.lookup(), "firstException", Throwable.class);
        private volatile Throwable firstException;

        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            Subtask.State state = ensureCompleted(subtask);
            return (state == Subtask.State.FAILED)
                    && (firstException == null)
                    && FIRST_EXCEPTION.compareAndSet(this, null, subtask.exception());
        }

        @Override
        public Void result() throws Throwable {
            Throwable ex = firstException;
            if (ex != null) {
                throw ex;
            } else {
                return null;
            }
        }
    }

    /**
     * A joiner that returns a stream of all subtasks.
     */
    static final class AllSubtasks<T> implements Joiner<T, Stream<Subtask<T>>> {
        private final Predicate<Subtask<? extends T>> isDone;

        // list of forked subtasks, only accessed by owner thread
        private final List<Subtask<T>> subtasks = new ArrayList<>();

        AllSubtasks(Predicate<Subtask<? extends T>> isDone) {
            this.isDone = Objects.requireNonNull(isDone);
        }

        @Override
        public boolean onFork(Subtask<? extends T> subtask) {
            ensureUnavailable(subtask);
            @SuppressWarnings("unchecked")
            var s = (Subtask<T>) subtask;
            subtasks.add(s);
            return false;
        }

        @Override
        public boolean onComplete(Subtask<? extends T> subtask) {
            ensureCompleted(subtask);
            return isDone.test(subtask);
        }

        @Override
        public Stream<Subtask<T>> result() {
            return subtasks.stream();
        }
    }
}
