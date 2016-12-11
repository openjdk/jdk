/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

/**
 * Utility class that allows the compiler to monitor compilations that take a very long time.
 */
public final class CompilationAlarm implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit).", type = OptionType.Debug)
        public static final OptionValue<Integer> CompilationExpirationPeriod = new OptionValue<>(300);
        // @formatter:on
    }

    private CompilationAlarm() {
    }

    private static boolean enabled() {
        return Options.CompilationExpirationPeriod.getValue() > 0;
    }

    /**
     * Thread local storage for compilation start timestamps. Everytime a compiler thread calls
     * {@link #trackCompilationPeriod()} it will save the start timestamp of the compilation.
     */
    private static final ThreadLocal<Long> compilationStartedTimeStamps = new ThreadLocal<>();

    private static boolean compilationStarted() {
        if (enabled()) {
            Long start = compilationStartedTimeStamps.get();
            if (start == null) {
                compilationStartedTimeStamps.set(System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    private static void compilationFinished() {
        if (enabled()) {
            assert compilationStartedTimeStamps.get() != null;
            compilationStartedTimeStamps.set(null);
        }
    }

    /**
     * Determines if the current compilation is expired. A compilation expires if it takes longer
     * than {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}.
     *
     * @return {@code true} if the current compilation already takes longer than
     *         {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}, {@code false}
     *         otherwise
     */
    public static boolean hasExpired() {
        if (enabled()) {
            Long start = compilationStartedTimeStamps.get();
            if (start != null) {
                long time = System.currentTimeMillis();
                assert time >= start;
                return time - start > Options.CompilationExpirationPeriod.getValue() * 1000;
            }
        }
        return false;
    }

    @Override
    public void close() {
        compilationFinished();
    }

    private static final CompilationAlarm INSTANCE = enabled() ? new CompilationAlarm() : null;

    /**
     * Gets an object that can be used in a try-with-resource statement to set an time limit based
     * alarm for a compilation.
     *
     * @return a {@link CompilationAlarm} instance if there is no current alarm for the calling
     *         thread otherwise {@code null}
     */
    public static CompilationAlarm trackCompilationPeriod() {
        if (compilationStarted()) {
            return INSTANCE;
        }
        return null;
    }

}
