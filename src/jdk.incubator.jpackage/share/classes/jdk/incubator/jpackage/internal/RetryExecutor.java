/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.incubator.jpackage.internal;

import java.io.IOException;
import java.util.function.Consumer;

public final class RetryExecutor {
    RetryExecutor() {
        setMaxAttemptsCount(5);
        setAttemptTimeoutMillis(2 * 1000);
    }

    RetryExecutor setMaxAttemptsCount(int v) {
        attempts = v;
        return this;
    }

    RetryExecutor setAttemptTimeoutMillis(int v) {
        timeoutMillis = v;
        return this;
    }

    RetryExecutor setExecutorInitializer(Consumer<Executor> v) {
        executorInitializer = v;
        return this;
    }

    void abort() {
        aborted = true;
    }

    static RetryExecutor retryOnKnownErrorMessage(String v) {
        RetryExecutor result = new RetryExecutor();
        return result.setExecutorInitializer(exec -> {
            exec.setOutputConsumer(output -> {
                if (!output.anyMatch(v::equals)) {
                    result.abort();
                }
            });
        });
    }

    void execute(String cmdline[]) throws IOException {
        aborted = false;
        for (;;) {
            try {
                Executor exec = Executor.of(cmdline);
                if (executorInitializer != null) {
                    executorInitializer.accept(exec);
                }
                exec.executeExpectSuccess();
                break;
            } catch (IOException ex) {
                if (aborted || (--attempts) <= 0) {
                    throw ex;
                }
            }

            try {
                Thread.sleep(timeoutMillis);
            } catch (InterruptedException ex) {
                Log.verbose(ex);
                throw new RuntimeException(ex);
            }
        }
    }

    private Consumer<Executor> executorInitializer;
    private boolean aborted;
    private int attempts;
    private int timeoutMillis;
}
