/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.log;

import java.util.Objects;

public interface ProgressLogger extends Logger {

    void progress(String localizedMsg);

    void progressWarning(Exception cause);

    void progressWarning(Exception cause, String localizedMsg);

    void progressWarning(String localizedMsg);

    enum ProgressLoggerTrait implements LoggerTrait {
        PRINT_INFO,
        PRINT_WARNINGS,
    }

    static ProgressLogger create(ConsoleLogger sink, boolean printInfo, boolean printWarnings) {
        if (!printInfo) {
            sink = sink.discardOut();
        }
        if (!printWarnings) {
            sink = sink.discardErr();
        }
        return new Details.Console(sink.addTimestampsToOut());
    }

    static ProgressLogger create(System.Logger sink) {
        return new Details.SystemLogger(sink);
    }

    final static class Details {

        private Details() {
        }

        private record Console(ConsoleLogger sink) implements ProgressLogger {

            Console {
                Objects.requireNonNull(sink);
            }

            @Override
            public void progress(String localizedMsg) {
                Objects.requireNonNull(localizedMsg);
                sink.out().accept(localizedMsg);
            }

            @Override
            public void progressWarning(Exception cause) {
                Objects.requireNonNull(cause);
                sink.err().accept(I18N.format("progress.warning-header", Utils.toString(cause)));
            }

            @Override
            public void progressWarning(Exception cause, String localizedMsg) {
                Objects.requireNonNull(cause);
                Objects.requireNonNull(localizedMsg);
                sink.err().accept(I18N.format("progress.warning-header2", localizedMsg, Utils.toString(cause)));
            }

            @Override
            public void progressWarning(String localizedMsg) {
                Objects.requireNonNull(localizedMsg);
                sink.err().accept(I18N.format("progress.warning-header", localizedMsg));
            }

        }

        private record SystemLogger(System.Logger sink) implements ProgressLogger {

            SystemLogger {
                Objects.requireNonNull(sink);
            }

            @Override
            public void progress(String localizedMsg) {
                sink.log(System.Logger.Level.INFO, localizedMsg);
            }

            @Override
            public void progressWarning(Exception cause) {
                sink.log(System.Logger.Level.WARNING, "Ignore error", cause);
            }

            @Override
            public void progressWarning(Exception cause, String localizedMsg) {
                sink.log(System.Logger.Level.WARNING, localizedMsg, cause);
            }

            @Override
            public void progressWarning(String localizedMsg) {
                sink.log(System.Logger.Level.WARNING, localizedMsg);
            }

        }
    }

    static final ProgressLogger DISCARDING_LOGGER = Utils.discardingLogger(ProgressLogger.class);
}
