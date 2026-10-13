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

/*
 * @test
 * @bug 8381362
 * @summary Verify no crash for vector late-inline queue draining when MH/virtual late inlining is disabled
 * @modules jdk.incubator.vector
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-IncrementalInlineVirtual -XX:-IncrementalInlineMH -XX:-UseInlineCaches
 *                   ${test.main.class} vector
 */

/*
 * @test
 * @bug 8381362
 * @summary Verify no crash for non-vector late-inline queue draining when MH/virtual late inlining is disabled
 * @modules jdk.incubator.vector
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions
 *                   -XX:-IncrementalInlineVirtual -XX:-IncrementalInlineMH -XX:-UseInlineCaches
 *                   -XX:LiveNodeCountInliningCutoff=50
 *                   -XX:CompileCommand=compileonly,${test.main.class}::nonVector*
 *                   -XX:CompileCommand=delayinline,${test.main.class}::lateInline*
 *                   ${test.main.class} nonvector
 */

package compiler.inlining;

import jdk.incubator.vector.*;
import java.lang.invoke.VarHandle;

public class LateInlineQueueDrainTest {
    private static final int SIZE = 60_000;
    private static int sink;

    public static void main(String[] args) {
        sink = 0;
        if (args.length != 1) {
            throw new RuntimeException("Expected one argument: vector|nonvector");
        }
        switch (args[0]) {
            case "vector":
                vectorWorkload();
                break;
            case "nonvector":
                nonVectorWorkload();
                break;
            default:
                throw new RuntimeException("Unknown mode: " + args[0]);
        }
        System.out.println("PASS " + args[0] + " " + sink);
    }

    private static void vectorWorkload() {
        char[] a = new char[SIZE];
        char[] b = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = (char) i;
            b[i] = (char) i;
        }
        vectorKernel(a);
    }

    private static void vectorKernel(char[] arr) {
        FloatVector lcFloatVec2 = null;
        FloatVector lcFloatVec1 = null;
        float[] lcFloatArr1 = new float[100];
        float[] lcFloatArr2 = new float[100];
        float[] lcFloatArr3 = new float[100];
        Object Obj = new Object();
        for (int i = 0; i < lcFloatArr1.length; i++) {
            lcFloatArr1[i] = (((float) i) * 1.5F) + 7.89F;
        }
        for (int i = 0; i < lcFloatArr2.length; i++) {
            lcFloatArr2[i] = (((float) i) * 1.5F) + 7.89F;
        }
        for (int i = 0; i < lcFloatArr3.length; i++) {
            lcFloatArr3[i] = (((float) i) * 1.5F) + 7.89F;
        }
        for (int i = 0; i < 50; i++) {
            VarHandle.fullFence();
            synchronized (Obj) {
                try {
                    Obj.wait(1);
                } catch (InterruptedException ex) {
                }
            }
            VarHandle.fullFence();
            if ((((int) (lcFloatArr1[0])) % 3) < 1) {
                lcFloatVec1 = ((FloatVector) (VectorShuffle.iota(FloatVector.SPECIES_PREFERRED, 0, 14, true).toVector()));
            } else {
                lcFloatVec1 = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, lcFloatArr2, 14);
            }
            lcFloatVec1 = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, lcFloatArr3, 14);
            lcFloatVec2 = lcFloatVec1.add(lcFloatVec1);
            lcFloatVec2.intoArray(lcFloatArr1, 14);
            synchronized (Obj) {
                try {
                    Obj.wait(1);
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    private static void nonVectorWorkload() {
        int r = 0;
        for (int i = 0; i < 200_000; i++) {
            r += nonVectorKernel(100);
        }
        sink += r;
    }

    private static int nonVectorKernel(int n) {
        int s = 0;
        for (int i = 0; i < n; i++) {
            lateInline(i);
            s += i;
        }
        return s;
    }

    private static void lateInline(int x) {
        sink += x;
    }
}
