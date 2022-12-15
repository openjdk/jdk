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
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
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

    interface AnInterface {
        public int getInt();
    }

    interface SecondInterface {
        public int get2();
    }

    class FirstClass implements AnInterface, SecondInterface {
        public int getInt() {
            return 1;
        }

        public int get2() {
            return 1;
        }
    }

    class SecondClass implements AnInterface, SecondInterface {
        public int getInt() {
            return 2;
        }

        public int get2() {
            return 1;
        }
    }

    class ThirdClass implements AnInterface, SecondInterface {
        public int getInt() {
            return -3;
        }

        public int get2() {
            return 1;
        }
    }

    class FourthClass implements AnInterface, SecondInterface {
        public int getInt() {
            return -4;
        }

        public int get2() {
            return 1;
        }
    }

    class FifthClass implements AnInterface, SecondInterface {
        public int getInt() {
            return -5;
        }

        public int get2() {
            return 1;
        }
    }

    final int asLength = 5;
    public AnInterface[] as = new AnInterface[asLength];


    @Setup
    public void setupSubclass() {
        as[0] = new FirstClass();
        as[1] = new SecondClass();
        as[2] = new ThirdClass();
        as[3] = new FourthClass();
        as[4] = new FifthClass();
    }

    /**
     * Tests a call where there are multiple implementors but only one of the
     * implementors is every used here so the call-site is monomorphic
     */
    @Benchmark
    public int testMonomorphic() {
        return as[0].getInt();
    }

    int l = 0;

    /**
     * Interface call address computation within loop but the receiver preexists
     * the loop and the ac can be moved outside of the loop
     */
    @Benchmark
    public int test1stInt2Types() {
        AnInterface ai = as[l];
        l = 1 - l;
        return ai.getInt();
    }

    @Benchmark
    public int test1stInt3Types() {
        AnInterface ai = as[l];
        l = ++ l % 3;
        return ai.getInt();
    }

    @Benchmark
    public int test1stInt5Types() {
        AnInterface ai = as[l];
        l = ++ l % asLength;
        return ai.getInt();
    }

    @Benchmark
    public int test2ndInt2Types() {
        SecondInterface ai = (SecondInterface) as[l];
        l = 1 - l;
        return ai.get2();
    }

    @Benchmark
    public int test2ndInt3Types() {
        SecondInterface ai = (SecondInterface) as[l];
        l = ++ l % 3;
        return ai.get2();
    }

    @Benchmark
    public int test2ndInt5Types() {
        SecondInterface ai = (SecondInterface) as[l];
        l = ++ l % asLength;
        return ai.get2();
    }

}
