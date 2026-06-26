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
package org.openjdk.bench.valhalla.hash;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@Fork(value = 2, jvmArgsAppend = {"--enable-preview", "-XX:+UnlockDiagnosticVMOptions", "-XX:+UseNewCode"})
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 3, time = 1)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class FastPath {
    public static final int SIZE = 100;

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash(Object obj) {
        return System.identityHashCode(obj);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Empty obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Byte[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Short[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Integer[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Long[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyIntInt[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyLongInt[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyLongLong obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyByteShort[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }
    @CompilerControl(CompilerControl.Mode.INLINE)
    private static int hash_inline(Object[] objects) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += System.identityHashCode(objects[i]);
        }
        return s;
    }

    // Homogeneous array of null, all go to null path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_null() {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += hash(null);
        }
        return s;
    }

    // Homogeneous array of empty object, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_empty() {
        int s = System.identityHashCode(Empty.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new Empty());
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_empty_static() {
        int s = System.identityHashCode(Empty.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new Empty());
        }
        return s;
    }
    /*
    // Homogeneous array of bytes, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_byte(NumericCase st) {
        return hash(st.byte_arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_byte_static(NumericCase st) {
        return hash_specialized((Byte[]) st.byte_arr);
    }

    // Homogeneous array of shorts, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_short(NumericCase st) {
        return hash(st.short_arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_short_static(NumericCase st) {
        return hash_specialized((Short[])st.short_arr);
    }

    // Homogeneous array of ints, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int(NumericCase st) {
        return hash(st.int_arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_static(NumericCase st) {
        return hash_specialized((Integer[])st.int_arr);
    }

    // Homogeneous array of longs, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_long(NumericCase st) {
        return hash(st.long_arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_long_static(NumericCase st) {
        return hash_specialized((Long[])st.long_arr);
    }

    // Homogeneous array of pairs of ints, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_int(IntIntCase st) {
        return hash(st.arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_int_static(IntIntCase st) {
        return hash_specialized((MyIntInt[])st.arr);
    }

    // Heterogeneous array, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_small(AllFastPathCase st) {
        return hash(st.arr);
    }

    // Homogeneous array of pairs of long and int, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_int(LongIntCase st) {
        return hash(st.arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_int_static(LongIntCase st) {
        return hash_specialized((MyLongInt[])st.arr);
    }
*/
    // Homogeneous array of pairs of long, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_long() {
        int s = System.identityHashCode(Empty.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new MyLongLong(i, 2*i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_long_static() {
        int s = System.identityHashCode(Empty.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new MyLongLong(i, 2*i));
        }
        return s;
    }
/*
    // Heterogeneous array, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_too_big(TooBigCase st) {
        return hash(st.arr);
    }

    // Homogeneous array, with oop, so no fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_with_oop(WithOopCase st) {
        return hash(st.arr);
    }

    // Homogeneous array, not a nice size, so no fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_weird_size(ByteShortCase st) {
        return hash(st.arr);
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_weird_size_static(ByteShortCase st) {
        return hash_specialized(st.arr);
    }

    // Heterogeneous array, identity objects, so no fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_with_oop(IdentityCase st) {
        return hash(st.arr);
    }

    // Heterogeneous array, all of the above
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int big_mix(BigMixCase st) {
        return hash(st.arr);
    }
*/
    @State(Scope.Thread)
    public static class NullCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];
        }
    }

    static value class Empty {}
    @State(Scope.Thread)
    public static class EmptyCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Empty[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new Empty();
            }
        }
    }

    @State(Scope.Thread)
    public static class NumericCase {
        Object[] byte_arr;
        Object[] short_arr;
        Object[] int_arr;
        Object[] long_arr;

        @Setup
        public void setup() {
            byte_arr = new Byte[SIZE];
            short_arr = new Short[SIZE];
            int_arr = new Integer[SIZE];
            long_arr = new Long[SIZE];

            for (int i = 0; i < SIZE; i++) {
                byte_arr[i] = new Byte((byte)i);
                short_arr[i] = new Short((short)(i + 256));
                int_arr[i] = new Integer(i + 256);
                long_arr[i] = new Long(i + 256);
            }
        }
    }

    static value class MyIntInt {
        int fst;
        int snd;
        public MyIntInt (int fst, int snd) {this.fst = fst; this.snd = snd;}
    }
    @State(Scope.Thread)
    public static class IntIntCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new MyIntInt[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new MyIntInt(i, 2*i);
            }
        }
    }

    @State(Scope.Thread)
    public static class AllFastPathCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                switch (i % 7) {
                    case 0: arr[i] = new Empty(); break;
                    case 1: arr[i] = new Byte((byte)i); break;
                    case 2: arr[i] = new Short((short)(i + 256)); break;
                    case 3: arr[i] = new Integer(i + 256); break;
                    case 4: arr[i] = new Long(i + 256); break;
                    case 5: arr[i] = new MyIntInt(i, 2*i); break;
                    case 6: arr[i] = null; break;
                }
            }
        }
    }

    static value class MyLongInt {
        long fst;
        int snd;
        MyLongInt(long fst, int snd) { this.fst = fst; this.snd = snd; }
    }
    @State(Scope.Thread)
    public static class LongIntCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new MyLongInt[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new MyLongInt(i, 2*i);
            }
        }
    }

    static value class MyLongLong {
        long fst;
        long snd;
        MyLongLong(long fst, long snd) { this.fst = fst; this.snd = snd; }
    }
    @State(Scope.Thread)
    public static class LongLongCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new MyLongLong[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new MyLongLong(i, 2*i);
            }
        }
    }

    @State(Scope.Thread)
    public static class TooBigCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                switch (i % 6) {
                    case 0: arr[i] = new MyLongInt(i, 2*i); break;
                    case 1: arr[i] = new MyLongLong(i, 2*i); break;
                }
            }
        }
    }

    static value class WithOop {
        Integer[] s;
        WithOop(int i) {
            if (i % 4 == 0) {
                this.s = null;
            } else {
                this.s = new Integer[]{i};
            }
        }
    }
    @State(Scope.Thread)
    public static class WithOopCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new WithOop(i);
            }
        }
    }

    static value class MyByteShort {
        byte fst;
        short snd;
        MyByteShort(byte fst, short snd) { this.fst = fst; this.snd = snd; }
    }
    @State(Scope.Thread)
    public static class ByteShortCase {
        MyByteShort[] arr;

        @Setup
        public void setup() {
            arr = new MyByteShort[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new MyByteShort((byte) i, (short) (2*i));
            }
        }
    }

    @State(Scope.Thread)
    public static class IdentityCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                switch (i % 2) {
                    case 0: arr[i] = String.valueOf(i); break;
                    case 1: arr[i] = new int[]{i}; break;
                }
            }
        }
    }

    @State(Scope.Thread)
    public static class BigMixCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                switch (i % 12) {
                    case 0: arr[i] = new Empty(); break;
                    case 1: arr[i] = new Byte((byte)i); break;
                    case 2: arr[i] = new Short((short)(i + 256)); break;
                    case 3: arr[i] = new Integer((i + 256)); break;
                    case 4: arr[i] = new Long((i + 256)); break;
                    case 5: arr[i] = new MyIntInt(i, 2*i); break;
                    case 6: arr[i] = new MyLongInt(i, 2*i); break;
                    case 7: arr[i] = new MyLongLong(i, 2*i); break;
                    case 8: arr[i] = new WithOop(i); break;
                    case 9: arr[i] = new MyByteShort((byte) i, (short)(2*i)); break;
                    case 10: arr[i] = String.valueOf(i); break;
                    case 11: arr[i] = new int[]{i}; break;
                }
            }
        }
    }
}
