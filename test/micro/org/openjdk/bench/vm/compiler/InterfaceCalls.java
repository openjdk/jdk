/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
public class InterfaceCalls {

    // Whether to step iteratively through the list of interfaces, or
    // to select one in an unpredictable way.
    @Param({"false", "true"})
    private boolean randomized;

    interface FirstInterface {
        public int getIntFirst();
    }

    interface SecondInterface {
        public int getIntSecond();
    }

    interface FirstInterfaceExt extends FirstInterface {
        default int getIntFirst() { return 44; }
    }

    interface FirstInterfaceExtExt extends FirstInterfaceExt {
        default int getIntFirst() { return 45; }
    }

    class FirstClass implements FirstInterface, SecondInterface {
        public int getIntFirst() {
            return 1;
        }

        public int getIntSecond() {
            return 1;
        }
    }

    class SecondClass implements FirstInterface, SecondInterface {
        public int getIntFirst() {
            return 2;
        }

        public int getIntSecond() {
            return 1;
        }
    }

    class ThirdClass implements FirstInterface, SecondInterface {
        public int getIntFirst() {
            return -3;
        }

        public int getIntSecond() {
            return 1;
        }
    }

    class FourthClass implements FirstInterface, SecondInterface {
        public int getIntFirst() {
            return -4;
        }

        public int getIntSecond() {
            return 1;
        }
    }

    class FifthClass implements FirstInterface, SecondInterface {
        public int getIntFirst() {
            return -5;
        }

        public int getIntSecond() {
            return 1;
        }
    }

    class FirstClassDontInline implements FirstInterface {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -1;
        }
    }

    class SecondClassDontInline implements FirstInterface {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -2;
        }
    }

    class ThirdClassDontInline implements FirstInterface {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -3;
        }
    }

    class FourthClassDontInline implements FirstInterface {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -4;
        }
    }

    class FifthClassDontInline implements FirstInterface {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -5;
        }
    }

    class FirstClassDontInlineExtExt implements FirstInterfaceExtExt {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -1;
        }
    }

    class SecondClassDontInlineExtExt implements FirstInterfaceExtExt {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -2;
        }
    }

    class ThirdClassDontInlineExtExt implements FirstInterfaceExtExt {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -3;
        }
    }

    class FourthClassDontInlineExtExt implements FirstInterfaceExtExt {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -4;
        }
    }

    class FifthClassDontInlineExtExt implements FirstInterfaceExtExt {
        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        public int getIntFirst() {
            return -5;
        }
    }

    final int asLength = 5;
    public FirstInterface[] as = new FirstInterface[asLength];
    public FirstInterface[] noninlined = new FirstInterface[asLength];
    public FirstInterfaceExtExt[] noninlinedextext = new FirstInterfaceExtExt[asLength];


    @Setup
    public void setupSubclass() {
        as[0] = new FirstClass();
        as[1] = new SecondClass();
        as[2] = new ThirdClass();
        as[3] = new FourthClass();
        as[4] = new FifthClass();

        noninlined[0] = new FirstClassDontInline();
        noninlined[1] = new SecondClassDontInline();
        noninlined[2] = new ThirdClassDontInline();
        noninlined[3] = new FourthClassDontInline();
        noninlined[4] = new FifthClassDontInline();

        noninlinedextext[0] = new FirstClassDontInlineExtExt();
        noninlinedextext[1] = new SecondClassDontInlineExtExt();
        noninlinedextext[2] = new ThirdClassDontInlineExtExt();
        noninlinedextext[3] = new FourthClassDontInlineExtExt();
        noninlinedextext[4] = new FifthClassDontInlineExtExt();
    }

    /**
     * Tests a call where there are multiple implementors but only one of the
     * implementors is every used here so the call-site is monomorphic
     */
    @Benchmark
    public int testMonomorphic() {
        return as[0].getIntFirst();
    }

    int l = 0;

    /** Tests single base interface method call */
    @Benchmark
    public int testIfaceCall(Blackhole bh) {
        FirstInterface ai = noninlined[l];
        l = ++ l % asLength;
        return ai.getIntFirst();
    }

    /** Tests extended interface method call */
    @Benchmark
    public int testIfaceExtCall(Blackhole bh) {
        FirstInterfaceExtExt ai = noninlinedextext[l];
        l = ++ l % asLength;
        return ai.getIntFirst();
    }

    /**
     * Interface call address computation within loop but the receiver preexists
     * the loop and the ac can be moved outside of the loop
     */
    @Benchmark
    public int test1stInt2Types() {
        FirstInterface ai = as[step(2)];
        return ai.getIntFirst();
    }

    @Benchmark
    public int test1stInt3Types() {
        FirstInterface ai = as[step(3)];
        return ai.getIntFirst();
    }

    @Benchmark
    public int test1stInt5Types() {
        FirstInterface ai = as[step(asLength)];
        return ai.getIntFirst();
    }

    @Benchmark
    public int test2ndInt2Types() {
        SecondInterface ai = (SecondInterface) as[step(2)];
        return ai.getIntSecond();
    }

    @Benchmark
    public int test2ndInt3Types() {
        SecondInterface ai = (SecondInterface) as[step(3)];
        return ai.getIntSecond();
    }

    @Benchmark
    public int test2ndInt5Types() {
        SecondInterface ai = (SecondInterface) as[step(asLength)];
        return ai.getIntSecond();
    }

    int step(int range) {
        if (randomized) {
            l = scramble(l);
        } else {
            l++;
        }
        return (l & Integer.MAX_VALUE) % range;
    }

    static final int scramble(int n) {
        int x = n;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return x == 0 ? 1 : x;
    }
}
