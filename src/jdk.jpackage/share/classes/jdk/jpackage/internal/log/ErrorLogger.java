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
import jdk.jpackage.internal.cli.Main.ErrorReporter;

public interface ErrorLogger extends Logger {

    void reportError(Throwable t);

    enum ErrorLoggerTrait implements LoggerTrait {
        PRINT_STACK_TRACE_ALWAYS,
        PRINT_FAILED_COMMAND_OUTPUT,
    }

    static ErrorLogger create(ConsoleLogger sink, boolean alwaysPrintStackTrace, boolean printCommandOutput) {
        Objects.requireNonNull(sink);
        return new ErrorReporter(
                t -> {
                    var buf = new StringWriter();
                    t.printStackTrace(new PrintWriter(buf));
                    Utils.writeWithoutTrailingLineSeparator(buf, sink.err());
                },
                sink.err(),
                alwaysPrintStackTrace,
                printCommandOutput);
    }

    static ErrorLogger create(System.Logger sink) {
        Objects.requireNonNull(sink);
        return new ErrorLogger() {

            @Override
            public void reportError(Throwable t) {
                var buf = new StringWriter();

                new ErrorReporter(t2 -> {
                    t2.printStackTrace(new PrintWriter(buf));
                }, str -> {
                    buf.write(str);
                    buf.write(System.lineSeparator());
                }, true, true).reportError(t);

                Utils.writeWithoutTrailingLineSeparator(buf, str -> {
                    sink.log(System.Logger.Level.ERROR, str);
                });
            }

        };
    }

    static final ErrorLogger DISCARDING_LOGGER = Utils.discardingLogger(ErrorLogger.class);
}
