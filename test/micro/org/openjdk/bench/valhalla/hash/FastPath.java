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

@Fork(value = 3, jvmArgsAppend = {"--enable-preview"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
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
    private static int hash_specialized(Byte obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Short obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Integer obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(Long obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyIntInt obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyLongInt obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyLongLong obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(WithOop obj) {
        return System.identityHashCode(obj);
    }
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int hash_specialized(MyByteShort obj) {
        return System.identityHashCode(obj);
    }

    // Homogeneous cases of null, all go to null path
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

    // Homogeneous cases of empty object, all go to fast path
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int no_hoist() {
        return hash(new Empty());
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_empty() {
        int s = System.identityHashCode(Empty.class);
        for (int i = 0; i < SIZE; i++) {
            s += no_hoist();
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

    // Homogeneous cases of bytes, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_byte() {
        int s = System.identityHashCode(Byte.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new Byte((byte)i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_byte_static() {
        int s = System.identityHashCode(Byte.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new Byte((byte)i));
        }
        return s;
    }

    // Homogeneous cases of shorts, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_short() {
        int s = System.identityHashCode(Short.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new Short((short)(i + 256)));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_short_static() {
        int s = System.identityHashCode(Short.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new Short((short)(i + 256)));
        }
        return s;
    }

    // Homogeneous cases of ints, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int() {
        int s = System.identityHashCode(Integer.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new Integer(i + 256));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_static() {
        int s = System.identityHashCode(Integer.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new Integer(i + 256));
        }
        return s;
    }

    // Homogeneous cases of longs, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_long() {
        int s = System.identityHashCode(Long.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new Long(i + 256));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_long_static() {
        int s = System.identityHashCode(Long.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new Long(i + 256));
        }
        return s;
    }

    // Homogeneous cases of pairs of ints, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_int() {
        int s = System.identityHashCode(MyIntInt.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new MyIntInt(i, 2*i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_int_int_static() {
        int s = System.identityHashCode(MyIntInt.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new MyIntInt(i, 2*i));
        }
        return s;
    }

    // Heterogeneous cases, all go to fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_small() {
        int s = System.identityHashCode(Empty.class);
        s += System.identityHashCode(Byte.class);
        s += System.identityHashCode(Short.class);
        s += System.identityHashCode(Integer.class);
        s += System.identityHashCode(Long.class);
        s += System.identityHashCode(MyIntInt.class);
        for (int i = 0; i < SIZE; i++) {
            Object v = switch (i % 7) {
                case 0 -> new Empty();
                case 1 -> new Byte((byte) i);
                case 2 -> new Short((short) (i + 256));
                case 3 -> new Integer(i + 256);
                case 4 -> new Long(i + 256);
                case 5 -> new MyIntInt(i, 2 * i);
                default -> null;
            };
            s += hash(v);
        }
        return s;
    }

    // Homogeneous cases of pairs of long and int, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_int() {
        int s = System.identityHashCode(MyLongInt.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new MyLongInt(i, 2*i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_int_static() {
        int s = System.identityHashCode(MyLongInt.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new MyLongInt(i, 2*i));
        }
        return s;
    }

    // Homogeneous cases of pairs of long, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_long() {
        int s = System.identityHashCode(MyLongLong.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new MyLongLong(i, 2*i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_too_big_long_long_static() {
        int s = System.identityHashCode(MyLongLong.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new MyLongLong(i, 2*i));
        }
        return s;
    }

    // Heterogeneous cases, too big for fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_too_big() {
        int s = System.identityHashCode(MyLongInt.class);
        s += System.identityHashCode(MyLongLong.class);
        for (int i = 0; i < SIZE; i++) {
            Object v = switch (i % 2) {
                case 0 -> new MyLongInt(i, 2*i);
                default -> new MyLongLong(i, 2*i);
            };
            s += hash(v);
        }
        return s;
    }

    // Homogeneous cases, with oop, so no fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_with_oop() {
        int s = System.identityHashCode(WithOop.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new WithOop(i));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_with_oop_static() {
        int s = System.identityHashCode(WithOop.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new WithOop(i));
        }
        return s;
    }

    // Homogeneous cases, not a nice size, so no fast path
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_weird_size() {
        int s = System.identityHashCode(MyByteShort.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new MyByteShort((byte) i, (short) (2*i)));
        }
        return s;
    }
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_weird_size_static() {
        int s = System.identityHashCode(MyByteShort.class);
        for (int i = 0; i < SIZE; i++) {
            s += hash_specialized(new MyByteShort((byte) i, (short) (2*i)));
        }
        return s;
    }

    // Homogeneous cases of String, so fast path not taken
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_with_obj_string() {
        int s = System.identityHashCode(String.class);
        s += System.identityHashCode(int[].class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(String.valueOf(i));
        }
        return s;
    }

    // Homogeneous cases of arrays, so fast path not taken
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int homogeneous_with_obj_array() {
        int s = System.identityHashCode(String.class);
        s += System.identityHashCode(int[].class);
        for (int i = 0; i < SIZE; i++) {
            s += hash(new int[]{i});
        }
        return s;
    }

    // Heterogeneous array, identity objects, so fast path not taken
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int heterogeneous_with_obj() {
        int s = System.identityHashCode(String.class);
        s += System.identityHashCode(int[].class);
        for (int i = 0; i < SIZE; i++) {
            Object v = switch (i % 2) {
                case 0 -> String.valueOf(i);
                default -> new int[]{i};
            };
            s += hash(v);
        }
        return s;
    }

    // Heterogeneous array, all of the above
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int big_mix() {
        int s = System.identityHashCode(Empty.class);
        s += System.identityHashCode(Byte.class);
        s += System.identityHashCode(Short.class);
        s += System.identityHashCode(Integer.class);
        s += System.identityHashCode(Long.class);
        s += System.identityHashCode(MyIntInt.class);
        s += System.identityHashCode(MyLongInt.class);
        s += System.identityHashCode(MyLongLong.class);
        s += System.identityHashCode(WithOop.class);
        s += System.identityHashCode(MyByteShort.class);
        s += System.identityHashCode(String.class);
        s += System.identityHashCode(int[].class);
        for (int i = 0; i < SIZE; i++) {
            Object v = switch (i % 13) {
                    case 0 -> new Empty();
                    case 1 -> new Byte((byte)i);
                    case 2 -> new Short((short)(i + 256));
                    case 3 -> new Integer((i + 256));
                    case 4 -> new Long((i + 256));
                    case 5 -> new MyIntInt(i, 2*i);
                    case 6 -> new MyLongInt(i, 2*i);
                    case 7 -> new MyLongLong(i, 2*i);
                    case 8 -> new WithOop(i);
                    case 9 -> new MyByteShort((byte) i, (short)(2*i));
                    case 10 -> String.valueOf(i);
                    case 11 -> new int[]{i};
                    default -> null;
            };
            s += hash(v);
        }
        return s;
    }

    // Array of pre-hashed values, should take the cache path.
    @Benchmark
    @OperationsPerInvocation(SIZE)
    @CompilerControl(CompilerControl.Mode.INLINE)
    public int pre_hashed(PreHashedCase st) {
        int s = 0;
        for (int i = 0; i < SIZE; i++) {
            s += hash(st.arr[i]);
        }
        return s;
    }

    static value class Empty {}
    static value class MyIntInt {
        int fst;
        int snd;
        public MyIntInt (int fst, int snd) {this.fst = fst; this.snd = snd;}
    }
    static value class MyLongInt {
        long fst;
        int snd;
        MyLongInt(long fst, int snd) { this.fst = fst; this.snd = snd; }
    }
    static value class MyLongLong {
        long fst;
        long snd;
        MyLongLong(long fst, long snd) { this.fst = fst; this.snd = snd; }
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
    static value class MyByteShort {
        byte fst;
        short snd;
        MyByteShort(byte fst, short snd) { this.fst = fst; this.snd = snd; }
    }

    @State(Scope.Thread)
    public static class PreHashedCase {
        Object[] arr;

        @Setup
        public void setup() {
            arr = new Object[SIZE];

            for (int i = 0; i < SIZE; i++) {
                arr[i] = new MyLongLong(((long)i) << 32, i);
                System.identityHashCode(arr[i]);
            }
        }
    }
}
