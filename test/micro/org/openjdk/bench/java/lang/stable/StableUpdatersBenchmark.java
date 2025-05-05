/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.lang.stable;

import jdk.internal.lang.stable.StableFieldUpdater;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/**
 * Benchmark measuring StableValue performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgs = {"--enable-preview", "--add-exports", "java.base/jdk.internal.lang.stable=ALL-UNNAMED"})
@Threads(Threads.MAX)   // Benchmark under contention
public class StableUpdatersBenchmark {

    private static final String STRING = "https://some.site.com";

    private static final Base BASE = new Base(STRING);
    private static final Updater UPDATER = new Updater(STRING);
    private static final URI U_R_I = URI.create(STRING);

    private final Base base = new Base(STRING);
    private final Updater updater = new Updater(STRING);
    private static final URI uri = URI.create(STRING);

    @Benchmark
    public int baseStatic() {
        return BASE.hashCode();
    }

    @Benchmark
    public int base() {
        return base.hashCode();
    }

    @Benchmark
    public int updaterStatic() {
        return UPDATER.hashCode();
    }

    @Benchmark
    public int updater() {
        return updater.hashCode();
    }

    @Benchmark
    public int uriStatic() {
        return U_R_I.hashCode();
    }

    @Benchmark
    public int uri() {
        return uri.hashCode();
    }

    static final class Base extends Abstract {
        Base(String string) { super(string); }

        @Override
        public int hashCode() {
            int h = hashCode;
            if (h == 0) {
                hashCode = h = string.hashCode();
            }
            return h;
        }
    }

    static final class Updater extends Abstract {

        private static final ToIntFunction<Updater> HASH_CODE_UPDATER =
                StableFieldUpdater.ofInt(Updater.class, "hashCode", new ToIntFunction<>() {
                    @Override
                    public int applyAsInt(Updater updater) {
                        return updater.string.hashCode();
                    }
                }, -1);

        Updater(String string) { super(string); }

        @Override
        public int hashCode() {
            return HASH_CODE_UPDATER.applyAsInt(this);
        }
    }

    static class Abstract {

        final String string;
        int hashCode;

        Abstract(String string) {
            this.string = string;
        }
    }

}
