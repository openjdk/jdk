/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.util.CompositeProxy;
import jdk.jpackage.internal.util.CompositeProxy.InterfaceConflictResolver;

public interface AllLoggers extends
        Logger,
        ErrorLogger,
        CommandLogger,
        TraceLogger,
        ResourceLogger,
        ProgressLogger,
        SummaryLogger {

    public static AllLoggers create(Collection<Logger> slices) {
        var stub = new Logger() {};

        var loggers = new ArrayList<Logger>(slices);
        loggers.add(stub);

        return CompositeProxy.build().interfaceConflictResolver(new InterfaceConflictResolver() {
            @Override
            public <T> T chooseImplementer(Class<T> iface, T a, T b) {
                if (a == stub) {
                    return a;
                } else {
                    return b;
                }
            }
        }).create(AllLoggers.class, loggers.toArray());
    }

    public static AllLoggers create(Logger... slices) {
        return create(List.of(slices));
    }

    public static AllLoggers teeLogger(List<AllLoggers> loggers) {
        return Utils.teeLogger(AllLoggers.class, loggers);
    }

    public static AllLoggers create(Options logEnv) {
        return create(Stream.of(LoggerRole.values()).map(LoggerRole::logger).map(ov -> {
            return (Logger)ov.getFrom(logEnv);
        }).toList());
    }
}
