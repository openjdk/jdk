/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8317636
 * @summary The test verifies heap walking API (FollowReferences) reports
 *     field indeces in correct order (as described by jvmtiHeapReferenceInfoField spec).
 *     For simplification only primitive field callback is tested
 *     and all fields in the test classes are 'int'.
 *     Field IDs are not reported to the callback, so the test uses field values
 *     to distinguish between fields, so all field values in the test classes should be unique.
 * @run main/othervm/native -agentlib:FieldIndicesTest FieldIndicesTest
 */

import java.lang.ref.Reference;

// Test class hierarchy is based on the example described in the spec.
// Extra fields added to improve coverage.
interface I0 {
    int p = 10;
    // extra fields
    public int p5 = 11;
    int p6 = 12;
    public int p1 = 13;
}

interface I1 extends I0 {
    int x = 20;
    // extra fields
    int x1 = 21;
    public int x2 = 22;
}

interface I2 extends I0 {
    int y = 30;
    // extra fields
    int y9 = 31;
    public int y4 = 32;
    public int y3 = 33;
}

class C1 implements I1 {
    public static int a = 40;
    private int b = 41;
    // extra fields
    private int a1 = 42;
    protected static int b7 = 43;
    static int b2 = 44;
    final protected int a3 = 45;
    static int a2 = 46;
    public int b1 = 47;
}

class C2 extends C1 implements I2 {
    static int q = 60;
    final int r = 61;
    // extra fields
    private int q11 = 61;
    final static int q9 = 62;
    static int q2 = 63;
    final protected int r3 = 64;
    public int r7 = 65;
}

public class FieldIndicesTest {
    static {
        System.loadLibrary("FieldIndicesTest");
    }

    private static native void prepare(Object testObject);

    private static native void test(Object rootObject);

    private static native boolean testFailed();

    private static void prepare(String name, Object testObject) {
        System.out.println(">>prepare(" + name + ")");
        prepare(testObject);
        System.out.println("<<prepare(" + name + ")");
        System.out.println();
    }

    private static void test(String name, Object rootObject) {
        System.out.println(">>test(" + name + ")");
        test(rootObject);
        System.out.println("<<test(" + name + ")");
        System.out.println();
    }

    public static void main(String argv[]) {
        C1 obj1 = new C1();
        C2 obj2 = new C2();

        prepare("obj1", obj1);
        prepare("obj2", obj2);

        test("obj1", obj1);
        test("obj2", obj2);

        Reference.reachabilityFence(obj1);
        Reference.reachabilityFence(obj2);

        if (testFailed()) {
            throw new RuntimeException("Test failed. See log for details");
        }
    }
}
