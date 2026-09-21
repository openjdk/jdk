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
import jdk.jpackage.internal.cli.OptionValue;

public final class StandardLogger {

    private StandardLogger() {
    }

    public static final OptionValue<CommandLogger> COMMAND_LOGGER = create(CommandLogger.DISCARDING_LOGGER);

    public static final OptionValue<ErrorLogger> ERROR_LOGGER = create(ErrorLogger.DISCARDING_LOGGER);

    public static final OptionValue<ProgressLogger> PROGRESS_LOGGER = create(ProgressLogger.DISCARDING_LOGGER);

    public static final OptionValue<ResourceLogger> RESOURCE_LOGGER = create(ResourceLogger.DISCARDING_LOGGER);

    public static final OptionValue<SummaryLogger> SUMMARY_LOGGER = create(SummaryLogger.DISCARDING_LOGGER);

    public static final OptionValue<TraceLogger> TRACE_LOGGER = create(TraceLogger.DISCARDING_LOGGER);

    private static <T extends Logger> OptionValue<T> create(T defaultValue) {
        Objects.requireNonNull(defaultValue);
        return OptionValue.<T>build().defaultValue(defaultValue).create();
    }

}
