/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.java.util;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Microbenchmark for Arrays.sort() and Arrays.parallelSort().
 *
 * @author Vladimir Yaroslavskiy
 *
 * @version 2022.06.14
 *
 * @since 20
 */
@Fork(1)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 3, timeUnit = TimeUnit.SECONDS)
public class ArraysSort {

    @Param({ "100", "1000", "10000", "100000", "1000000" })
    int size;

    Random random;

    @Setup(Level.Iteration)
    public void start() {
        random = new Random(0x777);
    }

    public static class Int extends ArraysSort {

        @Param
        private Type type;

        int[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(int[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = random.nextInt();
                    }
                }
            },

            REPEATED {
                @Override
                void build(int[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = i % 7;
                    }
                }
            },

            STAGGER {
                @Override
                void build(int[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (i * 5) % a.length;
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(int[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = random.nextInt(6) > 0 ? (j += 2) : (k += 2);
                    }
                }
            };

            abstract void build(int[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new int[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Long extends ArraysSort {

        @Param
        private Type type;

        long[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(long[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = random.nextLong();
                    }
                }
            },

            REPEATED {
                @Override
                void build(long[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = i % 7;
                    }
                }
            },

            STAGGER {
                @Override
                void build(long[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (i * 5L) % a.length;
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(long[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = random.nextInt(6) > 0 ? (j += 2) : (k += 2);
                    }
                }
            };

            abstract void build(long[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new long[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Byte extends ArraysSort {

        @Param
        private Type type;

        byte[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(byte[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) random.nextInt();
                    }
                }
            },

            REPEATED {
                @Override
                void build(byte[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) (i % 7);
                    }
                }
            },

            STAGGER {
                @Override
                void build(byte[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (byte) ((i * 5) % a.length);
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(byte[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (byte) (random.nextInt(6) > 0 ? (j += 2) : (k += 2));
                    }
                }
            };

            abstract void build(byte[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new byte[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Char extends ArraysSort {

        @Param
        private Type type;

        char[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(char[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) random.nextInt();
                    }
                }
            },

            REPEATED {
                @Override
                void build(char[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) (i % 7);
                    }
                }
            },

            STAGGER {
                @Override
                void build(char[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (char) ((i * 5) % a.length);
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(char[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (char) (random.nextInt(6) > 0 ? (j += 2) : (k += 2));
                    }
                }
            };

            abstract void build(char[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new char[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Short extends ArraysSort {

        @Param
        private Type type;

        short[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(short[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) random.nextInt();
                    }
                }
            },

            REPEATED {
                @Override
                void build(short[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) (i % 7);
                    }
                }
            },

            STAGGER {
                @Override
                void build(short[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (short) ((i * 5) % a.length);
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(short[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = (short) (random.nextInt(6) > 0 ? (j += 2) : (k += 2));
                    }
                }
            };

            abstract void build(short[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new short[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Float extends ArraysSort {

        @Param
        private Type type;

        float[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(float[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = random.nextFloat();
                    }
                }
            },

            REPEATED {
                @Override
                void build(float[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = i % 7;
                    }
                }
            },

            STAGGER {
                @Override
                void build(float[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (i * 5) % a.length;
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(float[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = random.nextInt(6) > 0 ? (j += 2) : (k += 2);
                    }
                }
            };

            abstract void build(float[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new float[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }

    public static class Double extends ArraysSort {

        @Param
        private Type type;

        double[] gold;

        public enum Type {

            RANDOM {
                @Override
                void build(double[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = random.nextDouble();
                    }
                }
            },

            REPEATED {
                @Override
                void build(double[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = i % 7;
                    }
                }
            },

            STAGGER {
                @Override
                void build(double[] a, Random random) {
                    for (int i = 0; i < a.length; ++i) {
                        a[i] = (i * 5) % a.length;
                    }
                }
            },

            SHUFFLE {
                @Override
                void build(double[] a, Random random) {
                    for (int i = 0, j = 0, k = 1; i < a.length; ++i) {
                        a[i] = random.nextInt(6) > 0 ? (j += 2) : (k += 2);
                    }
                }
            };

            abstract void build(double[] a, Random random);
        }

        @Setup
        public void setup() {
            gold = new double[size];
        }

        @Setup(Level.Invocation)
        public void init() {
            type.build(gold, random);
        }

        @Benchmark
        public void testSort() {
            Arrays.sort(gold);
        }

        @Benchmark
        public void testParallelSort() {
            Arrays.parallelSort(gold);
        }
    }
}
