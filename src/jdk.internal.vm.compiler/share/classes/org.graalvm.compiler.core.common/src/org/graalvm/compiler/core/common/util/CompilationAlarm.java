/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

/**
 * Utility class that allows the compiler to monitor compilations that take a very long time.
 */
public final class CompilationAlarm implements AutoCloseable {

    public static class Options {
        // @formatter:off
        @Option(help = "Time limit in seconds before a compilation expires (0 to disable the limit). " +
                       "The compilation alarm will be implicitly disabled if assertions are enabled.", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationExpirationPeriod = new OptionKey<>(300);
        // @formatter:on
    }

    private CompilationAlarm(long expiration) {
        this.expiration = expiration;
    }

    /**
     * Thread local storage for the active compilation alarm.
     */
    private static final ThreadLocal<CompilationAlarm> currentAlarm = new ThreadLocal<>();

    private static final CompilationAlarm NEVER_EXPIRES = new CompilationAlarm(0);

    /**
     * Gets the current compilation alarm. If there is no current alarm, a non-null value is
     * returned that will always return {@code false} for {@link #hasExpired()}.
     */
    public static CompilationAlarm current() {
        CompilationAlarm alarm = currentAlarm.get();
        return alarm == null ? NEVER_EXPIRES : alarm;
    }

    /**
     * Determines if this alarm has expired. A compilation expires if it takes longer than
     * {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}.
     *
     * @return {@code true} if the current compilation already takes longer than
     *         {@linkplain CompilationAlarm.Options#CompilationExpirationPeriod}, {@code false}
     *         otherwise
     */
    public boolean hasExpired() {
        return this != NEVER_EXPIRES && System.currentTimeMillis() > expiration;
    }

    @Override
    public void close() {
        if (this != NEVER_EXPIRES) {
            currentAlarm.set(null);
        }
    }

    /**
     * The time at which this alarm expires.
     */
    private final long expiration;

    /**
     * Starts an alarm for setting a time limit on a compilation if there isn't already an active
     * alarm, if assertions are disabled and
     * {@link CompilationAlarm.Options#CompilationExpirationPeriod}{@code > 0}. The returned value
     * can be used in a try-with-resource statement to disable the alarm once the compilation is
     * finished.
     *
     * @return a {@link CompilationAlarm} if there was no current alarm for the calling thread
     *         before this call otherwise {@code null}
     */
    public static CompilationAlarm trackCompilationPeriod(OptionValues options) {
        int period = Assertions.assertionsEnabled() ? 0 : Options.CompilationExpirationPeriod.getValue(options);
        if (period > 0) {
            CompilationAlarm current = currentAlarm.get();
            if (current == null) {
                long expiration = System.currentTimeMillis() + period * 1000;
                current = new CompilationAlarm(expiration);
                currentAlarm.set(current);
                return current;
            }
        }
        return null;
    }

}
