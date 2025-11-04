/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public abstract class WriteBarrier {

    // For array references
    public static final int NUM_REFERENCES_SMALL = 32;
    public static final int NUM_REFERENCES_LARGE = 2048;

    // For array update tests
    private Object[] theArraySmall;
    private int[] indicesSmall;

    private Object[] theArrayLarge;
    private int[] indicesLarge;

    private Object[] youngArraySmall;
    private Object[] youngArrayLarge;

    private Object nullRef;
    private Object realRef;
    private Object youngRef;

    // For field update tests
    public Referencer head = null;
    public Referencer tail = null;
    public Referencer youngHead = null;
    public Referencer youngTail = null;

    // For random number generation
    private int m_w;
    private int m_z;

    // For field references
    public class Referencer {
        Referencer next = null;
        Referencer() {
            this.next = null;
        }
        void append(Referencer r) {
            this.next = r;
        }
        void clear() {
            this.next = null;
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        theArraySmall = new Object[NUM_REFERENCES_SMALL];
        indicesSmall = new int[NUM_REFERENCES_SMALL];

        theArrayLarge = new Object[NUM_REFERENCES_LARGE];
        indicesLarge = new int[NUM_REFERENCES_LARGE];

        m_w = (int) System.currentTimeMillis();
        Random random = new Random();
        m_z = random.nextInt(10000) + 1;

        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            indicesSmall[i] = get_random() % (NUM_REFERENCES_SMALL - 1);
        }

        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            indicesLarge[i] = get_random() % (NUM_REFERENCES_LARGE - 1);
        }

        realRef = new Object();

        // Build a small linked structure
        this.head = new Referencer();
        this.tail = new Referencer();
        this.head.append(this.tail);

        // This will (hopefully) promote objects to old space
        // Run with -XX:+DisableExplicitGC to keep
        // objects in young space
        System.gc();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Reallocate target objects each iteration to ensure they are in young gen.
        youngArraySmall = new Object[NUM_REFERENCES_SMALL];
        youngArrayLarge = new Object[NUM_REFERENCES_LARGE];
        youngRef = new Object();
        this.youngHead = new Referencer();
        this.youngTail = new Referencer();
    }

    private int get_random() {
        m_z = 36969 * (m_z & 65535) + (m_z >> 16);
        m_w = 18000 * (m_w & 65535) + (m_w >> 16);
        return Math.abs((m_z << 16) + m_w);  /* 32-bit result */
    }

    // This and the other testArrayWriteBarrierFast benchmarks below should not
    // be inlined into the JMH-generated harness method. If the methods were
    // inlined, we might spill in the main loop (on x64) depending on very
    // subtle conditions (such as whether LinuxPerfAsmProfiler is enabled!),
    // which could distort the results.
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathRealSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            theArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = realRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathOldToYoungSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            youngArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = realRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathYoungToOldSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            theArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = youngRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathYoungToYoungSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            youngArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = youngRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathNullYoungSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            youngArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = nullRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathOldToYoungLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            youngArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = realRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathYoungToYoungLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            youngArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = youngRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathNullYoungLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            youngArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = nullRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathYoungToOldLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            theArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = youngRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathNullSmall() {
        for (int i = 0; i < NUM_REFERENCES_SMALL; i++) {
            theArraySmall[indicesSmall[NUM_REFERENCES_SMALL - i - 1]] = nullRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathRealLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            theArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = realRef;
        }
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void testArrayWriteBarrierFastPathNullLarge() {
        for (int i = 0; i < NUM_REFERENCES_LARGE; i++) {
            theArrayLarge[indicesLarge[NUM_REFERENCES_LARGE - i - 1]] = nullRef;
        }
    }

    @Benchmark()
    public void testFieldWriteBarrierFastPath() {
        // Shuffle everything around
        this.tail.append(this.head);
        this.head.clear();
        this.head.append(this.tail);
        this.tail.clear();
    }

    @Benchmark()
    public void testFieldWriteBarrierFastPathYoungRef() {
        // Shuffle everything around
        this.tail.append(this.youngHead);
        this.head.clear();
        this.head.append(this.youngTail);
        this.tail.clear();
    }

    // This run is useful to compare different GC barrier models without being
    // affected by C2 unrolling the main loop differently for each model.
    @Fork(value = 3, jvmArgs = {"-XX:LoopUnrollLimit=1"})
    public static class WithoutUnrolling extends WriteBarrier {}

    // This run is useful to study the interaction of GC barriers and loop
    // unrolling. Check that the main loop in the testArray benchmarks is
    // unrolled (or not) as expected for the studied GC barrier model.
    @Fork(value = 3)
    public static class WithDefaultUnrolling extends WriteBarrier {}
}
