/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgsAppend = {"-Xms512m", "-Xmx512m", "-XX:+AlwaysPreTouch", "-XX:+UseParallelGC"})
public class ConstructorBarriers {

    // Checks the barrier coalescing/optimization around field initializations.
    // Uses long fields to avoid store merging.

    public static class PlainPlain {
        long f1;
        long f2;
        public PlainPlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalPlain {
        final long f1;
        long f2;
        public FinalPlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class PlainFinal {
        long f1;
        final long f2;
        public PlainFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalFinal {
        final long f1;
        final long f2;
        public FinalFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class PlainVolatile {
        long f1;
        volatile long f2;
        public PlainVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatilePlain {
        volatile long f1;
        long f2;
        public VolatilePlain(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class FinalVolatile {
        final long f1;
        volatile long f2;
        public FinalVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatileFinal {
        volatile long f1;
        final long f2;
        public VolatileFinal(long i) {
            f1 = i;
            f2 = i;
        }
    }

    private static class VolatileVolatile {
        volatile long f1;
        volatile long f2;
        public VolatileVolatile(long i) {
            f1 = i;
            f2 = i;
        }
    }

    long l = 42;

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_plainPlain(Blackhole bh) {
        PlainPlain c = new PlainPlain(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_plainFinal(Blackhole bh) {
        PlainFinal c = new PlainFinal(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_finalPlain(Blackhole bh) {
        FinalPlain c = new FinalPlain(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_finalFinal(Blackhole bh) {
        FinalFinal c = new FinalFinal(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_plainVolatile(Blackhole bh) {
        PlainVolatile c = new PlainVolatile(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_volatilePlain(Blackhole bh) {
        VolatilePlain c = new VolatilePlain(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_volatileVolatile(Blackhole bh) {
        VolatileVolatile c = new VolatileVolatile(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_finalVolatile(Blackhole bh) {
        FinalVolatile c = new FinalVolatile(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long escaping_volatileFinal(Blackhole bh) {
        VolatileFinal c = new VolatileFinal(l);
        bh.consume(c);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_plainPlain() {
        PlainPlain c = new PlainPlain(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_plainFinal() {
        PlainFinal c = new PlainFinal(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_finalPlain() {
        FinalPlain c = new FinalPlain(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_finalFinal() {
        FinalFinal c = new FinalFinal(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_plainVolatile() {
        PlainVolatile c = new PlainVolatile(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_volatilePlain() {
        VolatilePlain c = new VolatilePlain(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_volatileVolatile() {
        VolatileVolatile c = new VolatileVolatile(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_finalVolatile() {
        FinalVolatile c = new FinalVolatile(l);
        return c.f1 + c.f2;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public long non_escaping_volatileFinal() {
        VolatileFinal c = new VolatileFinal(l);
        return c.f1 + c.f2;
    }

}

