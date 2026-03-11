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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public interface TraceLogger extends Logger {

    void trace(String msg);

    default void trace(String format, Object... args) {
        if (enabled()) {
            trace(String.format(format, args));
        }
    }

    void trace(Throwable t, String msg);

    default void trace(Throwable t, String format, Object... args) {
        if (enabled()) {
            trace(t, String.format(format, args));
        }
    }

    void trace(Throwable t);

    static TraceLogger create(ConsoleLogger sink) {
        return new Details.Console(sink.addTimestampsToOut().addTimestampsToErr());
    }

    static TraceLogger create(System.Logger sink) {
        return new Details.SystemLogger(sink);
    }

    final static class Details {

        private Details() {
        }

        private record Console(ConsoleLogger sink) implements TraceLogger {

            Console {
                Objects.requireNonNull(sink);
            }

            @Override
            public void trace(String msg) {
                Objects.requireNonNull(msg);
                sink.out().accept(MSG_PREFIX + msg);
            }

            @Override
            public void trace(Throwable t, String msg) {
                Objects.requireNonNull(t);
                Objects.requireNonNull(msg);

                var buf = new StringWriter();
                buf.write(MSG_PREFIX);
                buf.write(msg);
                buf.write(": ");
                t.printStackTrace(new PrintWriter(buf));

                Utils.writeWithoutTrailingLineSeparator(buf, sink.err());
            }

            @Override
            public void trace(Throwable t) {
                Objects.requireNonNull(t);

                var buf = new StringWriter();
                buf.write(MSG_PREFIX);
                t.printStackTrace(new PrintWriter(buf));

                Utils.writeWithoutTrailingLineSeparator(buf, sink.err());
            }

            private final static String MSG_PREFIX = "TRACE: ";
        }

        private record SystemLogger(System.Logger sink) implements TraceLogger {

            SystemLogger {
                Objects.requireNonNull(sink);
            }

            @Override
            public void trace(String msg) {
                sink.log(System.Logger.Level.TRACE, msg);
            }

            @Override
            public void trace(Throwable t, String msg) {
                sink.log(System.Logger.Level.ERROR, msg, t);
            }

            @Override
            public void trace(Throwable t) {
                sink.log(System.Logger.Level.ERROR, "Ignored error", t);
            }
        }
    }

    static final TraceLogger DISCARDING_LOGGER = Utils.discardingLogger(TraceLogger.class);
}
