/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.time.Duration;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import jdk.jpackage.internal.util.function.ExceptionBox;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.internal.util.function.ThrowingSupplier;

public class RetryExecutor<T, E extends Exception> {

    public RetryExecutor(Class<? extends E> exceptionType) {
        this.exceptionType = Objects.requireNonNull(exceptionType);
        setMaxAttemptsCount(5);
        setAttemptTimeout(2, TimeUnit.SECONDS);
    }

    final public Class<? extends E> exceptionType() {
        return exceptionType;
    }

    public RetryExecutor<T, E> setExecutable(ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> v) {
        executable = v;
        return this;
    }

    final public RetryExecutor<T, E> setExecutable(ThrowingSupplier<T, E> v) {
        if (v != null) {
            setExecutable(_ -> {
                return v.get();
            });
        } else {
            executable = null;
        }
        return this;
    }

    public RetryExecutor<T, E> setMaxAttemptsCount(int v) {
        attempts = v;
        return this;
    }

    final public RetryExecutor<T, E> setAttemptTimeout(long v, TimeUnit unit) {
        return setAttemptTimeout(Duration.of(v, unit.toChronoUnit()));
    }

    public RetryExecutor<T, E> setAttemptTimeout(Duration v) {
        timeout = v;
        return this;
    }

    public RetryExecutor<T, E> setExceptionMapper(Function<E, RuntimeException> v) {
        toUnchecked = v;
        return this;
    }

    public RetryExecutor<T, E> setSleepFunction(Consumer<Duration> v) {
        sleepFunction = v;
        return this;
    }

    final public RetryExecutor<T, E> mutate(Consumer<RetryExecutor<T, E>> mutator) {
        mutator.accept(this);
        return this;
    }

    public T execute() throws E {
        var curExecutable = executable();
        T result = null;
        var attemptIter = new DefaultContext();
        while (attemptIter.hasNext()) {
            attemptIter.next();
            try {
                result = curExecutable.apply(attemptIter);
                break;
            } catch (Exception ex) {
                if (!exceptionType.isInstance(ex)) {
                    throw ExceptionBox.toUnchecked(ex);
                } else if (attemptIter.isLastAttempt()) {
                    // No more attempts left. This is fatal.
                    throw exceptionType.cast(ex);
                } else {
                    curExecutable = executable();
                }
            }

            sleep();
        }

        return result;
    }

    final public T executeUnchecked() {
        try {
            return execute();
        } catch (Error | RuntimeException t) {
            throw t;
        } catch (Exception ex) {
            if (exceptionType.isInstance(ex)) {
                throw Optional.ofNullable(toUnchecked).orElse(ExceptionBox::toUnchecked).apply(exceptionType.cast(ex));
            } else {
                // Unreachable unless it is a direct subclass of Throwable,
                // which is not Error or Exception which should not happen.
                throw ExceptionBox.reachedUnreachable();
            }
        }
    }

    public interface Context<T> {
        boolean isLastAttempt();
        int attempt();
        T executor();
    }

    private final class DefaultContext implements Context<RetryExecutor<T, E>>, Iterator<Void> {

        @Override
        public boolean isLastAttempt() {
            return !hasNext();
        }

        @Override
        public int attempt() {
            return attempt;
        }

        @Override
        public boolean hasNext() {
            return (attempts - attempt) > 1;
        }

        @Override
        public Void next() {
            attempt++;
            return null;
        }

        @Override
        public RetryExecutor<T, E> executor() {
            return RetryExecutor.this;
        }

        private int attempt = -1;
    }

    private ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> executable() {
        return Optional.ofNullable(executable).orElseThrow(() -> {
            return new IllegalStateException("No executable");
        });
    }

    private void sleep() {
        Optional.ofNullable(timeout).ifPresent(Optional.ofNullable(sleepFunction).orElseGet(() -> {
            return toConsumer(Thread::sleep);
        }));
    }

    private final Class<? extends E> exceptionType;
    private ThrowingFunction<Context<RetryExecutor<T, E>>, T, E> executable;
    private int attempts;
    private Duration timeout;
    private Function<E, RuntimeException> toUnchecked;
    private Consumer<Duration> sleepFunction;
}
