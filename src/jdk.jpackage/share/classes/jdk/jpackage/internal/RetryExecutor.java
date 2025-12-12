/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import jdk.jpackage.internal.util.CommandOutputControl.Result;

public final class RetryExecutor {
    public RetryExecutor() {
        setMaxAttemptsCount(5);
        setAttemptTimeout(2, TimeUnit.SECONDS);
    }

    public RetryExecutor setMaxAttemptsCount(int v) {
        attempts = v;
        return this;
    }

    public RetryExecutor setAttemptTimeout(long v, TimeUnit unit) {
        timeout = Duration.of(v, unit.toChronoUnit());
        return this;
    }

    public Result getResult() {
        return Objects.requireNonNull(result);
    }

    public RetryExecutor setIterationCallback(Consumer<RetryExecutor> v) {
        iterationCallback = v;
        return this;
    }

    public void abort() {
        aborted = true;
    }

    public boolean isAborted() {
        return aborted;
    }

    static RetryExecutor retryOnKnownErrorMessage(String v) {
        return new RetryExecutor().setIterationCallback(exec -> {
            var stderr = exec.getResult().stderr();
            if (!stderr.stream().anyMatch(v::equals)) {
                exec.abort();
            }
        });
    }

    public Result execute(Executor exec) throws IOException {
        return execute(exec::executeExpectSuccess);
    }

    public Result execute(Callable<Result> exec) throws IOException {
        Objects.requireNonNull(exec);
        for (; attempts > 0 && !aborted; --attempts) {
            boolean resultUpdated = false;
            try {
                result = exec.call();
                resultUpdated = true;
            } catch (Exception ex) {
                if (attempts <= 1) {
                    // No more attempts left. This is fatal.
                    if (ex instanceof IOException ioex) {
                        throw ioex;
                    } else {
                        throw new IOException(ex);
                    }
                }
            }

            if (resultUpdated) {
                Objects.requireNonNull(result);

                if (iterationCallback == null) {
                    break;
                } else {
                    iterationCallback.accept(this);
                    if (aborted) {
                        break;
                    }
                }
            }

            Optional.ofNullable(timeout).map(Duration::toMillis).ifPresent(toConsumer(Thread::sleep));
        }

        return getResult();
    }

    private Consumer<RetryExecutor> iterationCallback;
    private boolean aborted;
    private int attempts;
    private Duration timeout;
    private Result result;
}
