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

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import jdk.jpackage.internal.cli.OptionValue;

public enum LoggerRole {

    COMMAND_LOGGER(StandardLogger.COMMAND_LOGGER, LogEnvironment::createCommandLogger),

    ERROR_LOGGER(StandardLogger.ERROR_LOGGER, LogEnvironment::createErrorLogger),

    PROGRESS_LOGGER(StandardLogger.PROGRESS_LOGGER, LogEnvironment::createProgressLogger),

    RESOURCE_LOGGER(StandardLogger.RESOURCE_LOGGER, LogEnvironment::createResourceLogger),

    SUMMARY_LOGGER(StandardLogger.SUMMARY_LOGGER, LogEnvironment::createSummaryLogger),

    TRACE_LOGGER(StandardLogger.TRACE_LOGGER, LogEnvironment::createTraceLogger),

    ;

    LoggerRole(OptionValue<? extends Logger> logger, Function<Collection<LoggerTrait>, ? extends Logger> loggerCtor) {
        this.logger = Objects.requireNonNull(logger);
        this.loggerCtor = Objects.requireNonNull(loggerCtor);
    }

    public OptionValue<? extends Logger> logger() {
        return logger;
    }

    Logger createLogger(Collection<LoggerTrait> traits) {
        return loggerCtor.apply(traits);
    }

    private final OptionValue<? extends Logger> logger;
    private final Function<Collection<LoggerTrait>, ? extends Logger> loggerCtor;
}
