/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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

/*
 * @test
 * @bug 8275202
 * @summary C2: optimize out more redundant conditions
 * @run main/othervm -XX:-BackgroundCompilation -XX:-TieredCompilation -XX:-UseOnStackReplacement
 *                   -XX:CompileCommand=dontinline,compiler.loopconditionalpropagation.TestLoopPredicateAndConditionalElimination::notInlined1
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+LoopConditionalPropagationALot compiler.loopconditionalpropagation.TestLoopPredicateAndConditionalElimination
 */

package compiler.loopconditionalpropagation;

public class TestLoopPredicateAndConditionalElimination {
    private static volatile int volatileBarrier;
    private static float floatField;

    public static void main(String[] args) {
        float[] array = new float[1000];
        for (int i = 0; i < 20_000; i++) {
//            test1(false, 0);
//            inlined1(0, array, 42, true, 0);
//            inlined1(0, array, 2, true, 0);
//            test2(false, 0, 1000, 1000);
//            inlined2(0, array, 42, true, 0, 1000, 1000);
//            inlined2(0, array, 2, true, 0, 1000, 1000);
//            test3(false, 0);
//            inlined3(0, array, -42, true, 0);
//            inlined3(0, array, 2, true, 0);
            test4(false, 0);
            inlined4(0, array, 42, true, 0);
            inlined4(0, array, 2, true, 0);
        }
    }

    private static float test1(boolean flag, int other) {
        float[] array = new float[1000];
        notInlined1(array);
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        final float v = inlined1(k - 3, array, j, flag, other);
        return v;
    }

    private static float inlined1(int start, float[] array, int j, boolean flag, int other) {
        float v = 0;
        if (flag) {
            if (other < 0) {
            }
            volatileBarrier = 42;
            if (start < other) {
            }
            for (int i = start; i < 1000; i++) {
                v = array[i];
                if (j == 2) {
                    break;
                }
            }
        } else {
            volatileBarrier = 42;
            v = floatField;
        }
        return v;
    }
    private static float test2(boolean flag, int other, int stop, int other2) {
        float[] array = new float[1000];
        notInlined1(array);
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        final float v = inlined2(k * 1000, array, j, flag, other, stop, other2);
        return v;
    }

    private static float inlined2(int start, float[] array, int j, boolean flag, int other, int stop, int other2) {
        float v = 0;
        if (flag) {
            if (other < 0) {
            }
            if (start < other) {
            }
            if (other2 > 1000) {
            }
            if (stop > other2) {
            }
            if (start < stop) {
                int i = start;
                do {
                    synchronized (new Object()) {
                    }
                    v = array[i];
                    if (j == 2) {
                        break;
                    }
                    i++;
                } while (i < stop);
            } else {
                volatileBarrier = 42;
                v = floatField;
            }
        } else {
            volatileBarrier = 42;
            v = floatField;
        }
        return v;
    }

    private static float test3(boolean flag, int other) {
        float[] array = new float[1000];
        notInlined1(array);
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        final float v = inlined3(k - 3, array, j, flag, other);
        return v;
    }

    private static int[] intArray = new int[10];
    private static int intField;
    private static int otherIntField;
    private static boolean[] boolArray = {true, false, true, false, true, false, true, false, true, false};

    static class C {
        int intField;
    }

    private static C c = new C();

    private static float inlined3(int start, float[] array, int j, boolean flag, int other) {
        float v = 0;
        if (flag) {
            int k;
            int idx = 0;
            for (k = 0; k < 10; k++) {
//                if (boolArray[k]) {
//                    if (boolArray[(k + 1) % 10]) {
//                        otherIntField = 42;
//                    }
//                } else {
//                    otherIntField = 42;
//                }
                final boolean[] localBoolArray = boolArray;
                if (localBoolArray == null) {
                }
                if (localBoolArray[k]) {
                    idx = c.intField;
                } else {
                    idx = c.intField;
                }
                if (localBoolArray[(k + 1) %10]) {
                        otherIntField = 42;
                }
                idx = idx - c.intField;
            }
            if (other < 0) {
            }
            volatileBarrier = 42;
            if (start < other) {
            }
            if (start >= 1000) {
            }
//            final float unused = array[start + idx];
            if (Long.compareUnsigned(((long) start) + idx, array.length) >= 0) {

            }
            for (int i = start; i < 1000; i++) {
                v = array[i + idx];
                if (j == 2) {
                    break;
                }
            }
        } else {
            volatileBarrier = 42;
            v = floatField;
        }
        return v;
    }

    private static float test4(boolean flag, int other) {
        float[] array = new float[1000];
        notInlined1(array);
        int j = 1;
        for (; j < 2; j *= 2) {
        }
        int k = 1;
        for (; k < 2; k *= 2) {
        }
        int l;
        for (l = 0; l < 10; l += k) {

        }
        final float v = inlined4(l - 11, array, j, flag, other);
        return v;
    }

    private static float inlined4(int start, float[] array, int j, boolean flag, int other) {
        float v = 0;
        if (flag) {
            if (other < 0) {
            }
            volatileBarrier = 42;
            if (start < other) {
            }
            for (int i = start; i < 1000; i++) {
                v = array[i];
                if (j == 2) {
                    break;
                }
            }
        } else {
            volatileBarrier = 42;
            v = floatField;
        }
        return v;
    }

    private static void notInlined1(float[] array) {
    }
}
