/*
 * Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Stream;

import compiler.whitebox.CompilerWhiteBoxTest;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8361837
 * @summary C2: investigate intrinsification of Preconditions.checkFromToIndex() and Preconditions.checkFromIndexSize()
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @modules java.base/jdk.internal.util
 * @build jdk.test.whitebox.WhiteBox
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -ea -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-BackgroundCompilation ${test.main.class}
 */
public class TestCheckIndexIntrinsics {
    private static final Random RNG = Utils.getRandomInstance();
    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=0");
        testCorrectness();
    }

    // Calling intrinsified functions and having them inlined.
    public static int checkIndex(int index, int length) {
        return Objects.checkIndex(index, length);
    }

    public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
        return Objects.checkFromToIndex(fromIndex, toIndex, length);
    }

    public static int checkFromIndexSize(int fromIndex, int size, int length) {
        return Objects.checkFromIndexSize(fromIndex, size, length);
    }

    public static long checkIndexL(long index, long length) {
        return Objects.checkIndex(index, length);
    }

    public static long checkFromToIndexL(long fromIndex, long toIndex, long length) {
        return Objects.checkFromToIndex(fromIndex, toIndex, length);
    }

    public static long checkFromIndexSizeL(long fromIndex, long size, long length) {
        return Objects.checkFromIndexSize(fromIndex, size, length);
    }

    // Unintrinsified bytecode functions, as in jdk.internal.util.Preconditions
    public static int unintrinsifiedCheckIndex(int index, int length) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException("oob");
        return index;
    }

    public static int unintrinsifiedCheckFromToIndex(int fromIndex, int toIndex, int length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

    public static int unintrinsifiedCheckFromIndexSize(int fromIndex, int size, int length) {
        if ((length | fromIndex | size) < 0 || size > length - fromIndex)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

    // Corresponding long versions
    public static long unintrinsifiedCheckIndexL(long index, long length) {
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException("oob");
        return index;
    }

    public static long unintrinsifiedCheckFromToIndexL(long fromIndex, long toIndex, long length) {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

    public static long unintrinsifiedCheckFromIndexSizeL(long fromIndex, long size, long length) {
        if ((length | fromIndex | size) < 0 || size > length - fromIndex)
            throw new IndexOutOfBoundsException("oob");
        return fromIndex;
    }

    // Controlled test without intrinsics, should not have range checks (and traps) to begin with.
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">= 1"})
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedCheckIndex(int start, int stop, int length, int offset) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            unintrinsifiedCheckIndex(scale * i + offset, length);
        }
    }

    // Same but for longs
    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, ">= 1"})
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedCheckIndexL(long start, long stop, long length, long offset) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            unintrinsifiedCheckIndexL(scale * i + offset, length);
        }
    }

    // Test range check (and trap) successfully eliminated
    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP}) // phase = CompilePhase.BEFORE_MATCHING
    public static void testCheckIndex(int start, int stop, int length, int offset) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            checkIndex(scale * i + offset, length);
        }
    }

    // Same but for longs
    @Test
    @IR(failOn = {IRNode.COUNTED_LOOP})
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP}) // phase = CompilePhase.BEFORE_MATCHING
    public static void testCheckIndexL(long start, long stop, long length, long offset) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            checkIndexL(scale * i + offset, length);
        }
    }

    @Run(test = {
            "testUnintrinsifiedCheckIndex",
            "testUnintrinsifiedCheckIndexL",
            "testCheckIndex",
            "testCheckIndexL"
    })
    private void testCheckIndex_runner() {
        testUnintrinsifiedCheckIndex(0, 100, 200, 0);
        testUnintrinsifiedCheckIndexL(0, 100, 200, 0);
        testCheckIndex(0, 100, 200, 0);
        testCheckIndexL(0, 100, 200, 0);
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3"}) // pre/main/post loops
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedCheckFromToIndex(int start, int stop, int length, int offset, int size) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            int from = scale * i + offset;
            int to = from + size;

            unintrinsifiedCheckFromToIndex(from, to, length);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1"}) // inner counted loop of the strip mined
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedCheckFromToIndexL(long start, long stop, long length, long offset, long size) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            long from = scale * i + offset;
            long to = from + size;

            unintrinsifiedCheckFromToIndexL(from, to, length);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "2"}) // range check in main loop hoisted and main loop is eliminated
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "3"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testCheckFromToIndex(int start, int stop, int length, int offset, int size) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            int from = scale * i + offset;
            int to = from + size;

            checkFromToIndex(from, to, length);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1"}) // inner counted loop of the strip mined
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "3"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testCheckFromToIndexL(long start, long stop, long length, long offset, long size) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            long from = scale * i + offset;
            long to = from + size;

            checkFromToIndexL(from, to, length);
        }
    }

    @Run(test = {
            "testUnintrinsifiedCheckFromToIndex",
            "testUnintrinsifiedCheckFromToIndexL",
            "testCheckFromToIndex",
            "testCheckFromToIndexL"
    })
    private void testCheckFromToIndex_runner() {
        testUnintrinsifiedCheckFromToIndex(0, 100, 210, 0, 10);
        testUnintrinsifiedCheckFromToIndexL(0, 100, 210, 0, 10);
        testCheckFromToIndex(0, 100, 210, 0, 10);
        testCheckFromToIndexL(0, 100, 210, 0, 10);
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "3"}) // pre/main/post loops
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedFromIndexSize(int start, int stop, int length, int offset, int size) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            int from = scale * i + offset;

            unintrinsifiedCheckFromIndexSize(from, size, length);
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "1"}) // pre/main/post loops
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void testUnintrinsifiedFromIndexSizeL(long start, long stop, long length, long offset, long size) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            long from = scale * i + offset;

            unintrinsifiedCheckFromIndexSizeL(from, size, length);
        }
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.RANGE_CHECK_TRAP})
    public static void testCheckFromIndexSize(int start, int stop, int length, int offset, int size) {
        final int scale = 2;
        final int stride = 1;

        for (int i = start; i < stop; i += stride) {
            int from = scale * i + offset;

            checkFromIndexSize(from, size, length);
        }
    }

    @Test
    @IR(counts = {IRNode.RANGE_CHECK_TRAP, "2"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.COUNTED_LOOP, IRNode.LOOP, IRNode.RANGE_CHECK_TRAP})
    public static void testCheckFromIndexSizeL(long start, long stop, long length, long offset, long size) {
        final long scale = 2;
        final long stride = 1;

        for (long i = start; i < stop; i += stride) {
            long from = scale * i + offset;

            checkFromIndexSizeL(from, size, length);
        }
    }

    @Run(test = {
            "testUnintrinsifiedFromIndexSize",
            "testUnintrinsifiedFromIndexSizeL",
            "testCheckFromIndexSize",
            "testCheckFromIndexSizeL"
    })
    private void testCheckFromIndexSize_runner() {
        testUnintrinsifiedFromIndexSize(0, 100, 210, 0, 10);
        testUnintrinsifiedFromIndexSizeL(0, 100, 210, 0, 10);
        testCheckFromIndexSize(0, 100, 210, 0, 10);
        testCheckFromIndexSizeL(0, 100, 210, 0, 10);
    }

    private static void assertIsCompiled(Method m) {
        if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should still be compiled");
        }
    }

    private static void assertIsNotCompiled(Method m) {
        if (WHITE_BOX.isMethodCompiled(m) && WHITE_BOX.getMethodCompilationLevel(m) == CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
            throw new AssertionError("should have been deoptimized");
        }
    }

    private static void compile(Method m) {
        WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
        assertIsCompiled(m);
    }

    public static ClassLoader newClassLoader() {
        try {
            return new URLClassLoader(new URL[]{
                    Paths.get(System.getProperty("test.classes", ".")).toUri().toURL(),
            }, null);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Unexpected URL conversion failure", e);
        }
    }

    private static void testShouldThrow(Method method, Object[] args) throws Exception {
        Class<?> c = newClassLoader().loadClass(TestCheckIndexIntrinsics.class.getName());
        String methodName = method.getName();
        Class<?> type = method.getReturnType();

        Method m = method.getParameterCount() == 2
                ? c.getDeclaredMethod(methodName, type, type)
                : c.getDeclaredMethod(methodName, type, type, type);
        Object[] compileArgs = method.getParameterCount() == 2
                ? new Object[]{1, 2}
                : new Object[]{1, 2, 3};

        if (type == long.class) {
            compileArgs = Stream.of(compileArgs).map(i -> ((Integer) i).longValue()).toArray(Object[]::new);
        }

        assertIsNotCompiled(m);

        // run and compile with known "good" values
        m.invoke(null, compileArgs); // run once so all classes are loaded
        compile(m);

        m.invoke(null, compileArgs);
        assertIsCompiled(m);

        try {
            m.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IndexOutOfBoundsException) {
                return;
            }

            throw new AssertionError("unexpected exception", e);
        }

        throw new AssertionError(String.format("%s(%s): should have thrown", method, Arrays.toString(args)));
    }

    private static void assertEqual(Method groundTruth, Method intrinsified, Object[] args)
            throws Exception {
        boolean oob = false;
        Object expected = null;
        try {
            expected = groundTruth.invoke(null, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IndexOutOfBoundsException) {
                oob = true;
            } else {
                throw new AssertionError("unexpected exception", e);
            }
        }

        if (oob) {
            testShouldThrow(intrinsified, args);
        } else {
            Object observed = intrinsified.invoke(null, args);
            if (!expected.equals(observed)) {
                throw new AssertionError(String.format("%s(%s): expected %s, got %s",
                        intrinsified.getName(), Arrays.toString(args), expected, observed));
            }
        }
    }


    private static void testCorrectness() throws Exception {
        Method checkIndex = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkIndex", int.class, int.class);
        Method checkFromToIndex = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkFromToIndex", int.class, int.class, int.class);
        Method checkFromIndexSize = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkFromIndexSize", int.class, int.class, int.class);

        Method checkIndexL = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkIndexL", long.class, long.class);
        Method checkFromToIndexL = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkFromToIndexL", long.class, long.class, long.class);
        Method checkFromIndexSizeL = TestCheckIndexIntrinsics.class.getDeclaredMethod("checkFromIndexSizeL", long.class, long.class, long.class);

        Method unintrinsifiedCheckIndex = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckIndex", int.class, int.class);
        Method unintrinsifiedCheckFromToIndex = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckFromToIndex", int.class, int.class, int.class);
        Method unintrinsifiedCheckFromIndexSize = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckFromIndexSize", int.class, int.class, int.class);

        Method unintrinsifiedCheckIndexL = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckIndexL", long.class, long.class);
        Method unintrinsifiedCheckFromToIndexL = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckFromToIndexL", long.class, long.class, long.class);
        Method unintrinsifiedCheckFromIndexSizeL = TestCheckIndexIntrinsics.class.getDeclaredMethod("unintrinsifiedCheckFromIndexSizeL", long.class, long.class, long.class);

        checkIndex.invoke(null, 0, 42);
        checkFromToIndex.invoke(null, 1, 16, 42);
        checkFromIndexSize.invoke(null, 32, 42, 123);

        checkIndexL.invoke(null, 0, 42);
        checkFromToIndexL.invoke(null, 1, 16, 42);
        checkFromIndexSizeL.invoke(null, 32, 42, 123);

        compile(checkIndex);
        compile(checkFromToIndex);
        compile(checkFromIndexSize);

        compile(checkIndexL);
        compile(checkFromToIndexL);
        compile(checkFromIndexSizeL);

        // 0 for int, 1 for long
        for (int type : new int[]{0, 1}) {
            long min = type == 0 ? Integer.MIN_VALUE : Long.MIN_VALUE;
            long max = type == 0 ? Integer.MAX_VALUE : Long.MAX_VALUE;

            long[][] inputs;
            inputs = new long[][]{
                    {0, 1},
                    {2, 5},
                    {9, 10},
                    {0, max},
                    {max - 1, max},

                    // should throw:
                    {-1, 5}, // index < 0
                    {5, 5},  // index == length
                    {6, 5},  // index > length
                    {0, 0},  // length = 0, no valid index
                    {2, -1}, // length < 0
                    {min, 5},
                    {5, min},
                    {max, max},
            };
            for (long[] input : inputs) {
                if (type == 0) {
                    assertEqual(unintrinsifiedCheckIndex, checkIndex, new Object[]{(int) input[0], (int) input[1]});
                } else {
                    assertEqual(unintrinsifiedCheckIndexL, checkIndexL, new Object[]{input[0], input[1]});
                }
            }

            inputs = new long[][]{
                    {0, 0, 5},
                    {0, 5, 5},
                    {2, 4, 5},
                    {5, 5, 5},
                    {0, 0, 0},
                    {0, max, max},
                    {max, max, max},
                    {max - 1, max, max},

                    // should throw:
                    {-1, 2, 5},  // fromIndex < 0
                    {3, 2, 5},   // fromIndex > toIndex (range inverted)
                    {2, 6, 5},   // toIndex > length (out of bounds)
                    {2, 4, -1},  // length < 0
                    {1, 0, 0},   // Out of bounds for zero length
                    {min, 5, 10},
                    {max, 0, max}
            };
            for (long[] input : inputs) {
                if (type == 0) {
                    assertEqual(unintrinsifiedCheckFromToIndex, checkFromToIndex, new Object[]{(int) input[0], (int) input[1], (int) input[2]});
                } else {
                    assertEqual(unintrinsifiedCheckFromToIndexL, checkFromToIndexL, new Object[]{input[0], input[1], input[2]});
                }
            }

            inputs = new long[][]{
                    {0, 0, 5},
                    {0, 5, 5},
                    {2, 2, 5},
                    {5, 0, 5},
                    {0, 0, 0},
                    {0, max, max},
                    {max, 0, max},
                    {max - 1, 1, max},

                    // should throw:
                    {-1, 2, 5},  // fromIndex < 0
                    {2, -1, 5},  // size < 0
                    {2, 4, 5},   // fromIndex + size > length (2 + 4 = 6 > 5)
                    {6, 0, 5},   // fromIndex > length
                    {2, 2, -1},   // length < 0
                    {1, max, max},
                    {max, 1, max},
                    {max, max, max},
                    {max / 2 + 1, max / 2 + 1, max}
            };
            for (long[] input : inputs) {
                if (type == 0) {
                    assertEqual(unintrinsifiedCheckFromIndexSize, checkFromIndexSize, new Object[]{(int) input[0], (int) input[1], (int) input[2]});
                } else {
                    assertEqual(unintrinsifiedCheckFromIndexSizeL, checkFromIndexSizeL, new Object[]{input[0], input[1], input[2]});
                }
            }

            // a couple of randoms for good measure
            long[] values = {
                    -1024, -1, 0, 42, 0xC0FFEE,
                    RNG.nextLong(), RNG.nextLong(), RNG.nextLong()
            };

            for (long i : values) {
                for (long j : values) {
                    for (long k : values) {
                        if (type == 0) {
                            assertEqual(unintrinsifiedCheckIndex, checkIndex, new Object[]{(int) i, (int) j});
                            assertEqual(unintrinsifiedCheckFromToIndex, checkFromToIndex, new Object[]{(int) i, (int) j, (int) k});
                            assertEqual(unintrinsifiedCheckFromIndexSize, checkFromIndexSize, new Object[]{(int) i, (int) j, (int) k});
                        } else {
                            assertEqual(unintrinsifiedCheckIndexL, checkIndexL, new Object[]{i, j});
                            assertEqual(unintrinsifiedCheckFromToIndexL, checkFromToIndexL, new Object[]{i, j, k});
                            assertEqual(unintrinsifiedCheckFromIndexSizeL, checkFromIndexSizeL, new Object[]{i, j, k});
                        }
                    }
                }
            }
        }
    }
}
