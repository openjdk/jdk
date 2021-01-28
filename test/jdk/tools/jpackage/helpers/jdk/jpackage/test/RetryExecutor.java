/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    boolean isAborted() {
        return aborted;
    }

    void execute(String cmdline[]) throws IOException {
        executeLoop(() -> Executor.of(cmdline));
    }

    private void executeLoop(Supplier<Executor> execSupplier) throws IOException {
        aborted = false;
        for (;;) {
            if (aborted) {
                break;
            }

            Executor exec = execSupplier.get();
            if (executorInitializer != null) {
                executorInitializer.accept(exec);
            }
            Executor.Result result = exec.executeWithoutExitCodeCheck();
            if (result.getExitCode() == 0) {
                break;
            } else {
                if (aborted || (--attempts) <= 0) {
                    throw new IOException(
                            String.format("Command %s exited with %d code",
                                    exec.getPrintableCommandLine(),
                                    result.getExitCode()));
                }
            }

            try {
                Thread.sleep(timeoutMillis);
            } catch (InterruptedException ex) {
                TKit.trace(ex.getMessage());
                throw new RuntimeException(ex);
            }
        }
    }

    private Consumer<Executor> executorInitializer;
    private boolean aborted;
    private int attempts;
    private int timeoutMillis;
}
