/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.valhalla.inlinetypes;

import java.util.Random;
import jdk.test.lib.Asserts;

/**
 * @test
 * @key randomness
 * @bug 8209009
 * @summary Test bimorphic inlining with value object receivers.
 * @library /testlibrary /test/lib
 * @enablePreview
 * @run main/othervm -Xbatch -XX:TypeProfileLevel=222
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestBimorphicInlining::test*
 *                   -XX:CompileCommand=quiet -XX:CompileCommand=print,compiler.valhalla.inlinetypes.TestBimorphicInlining::test*
 *                   compiler.valhalla.inlinetypes.TestBimorphicInlining
 * @run main/othervm -Xbatch -XX:TypeProfileLevel=222
 *                   -XX:+UnlockExperimentalVMOptions -XX:PerMethodTrapLimit=0 -XX:PerMethodSpecTrapLimit=0
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestBimorphicInlining::test*
 *                   -XX:CompileCommand=quiet -XX:CompileCommand=print,compiler.valhalla.inlinetypes.TestBimorphicInlining::test*
 *                   compiler.valhalla.inlinetypes.TestBimorphicInlining
 */

interface MyInterfaceBimorphicInlining {
    public MyInterfaceBimorphicInlining hash(MyInterfaceBimorphicInlining arg);
}

value class TestValue1BimorphicInlining implements MyInterfaceBimorphicInlining {
    int x;

    public TestValue1BimorphicInlining(int x) {
        this.x = x;
    }

    public TestValue1BimorphicInlining hash(MyInterfaceBimorphicInlining arg) {
        return new TestValue1BimorphicInlining(x + ((TestValue1BimorphicInlining)arg).x);
    }
}

value class TestValue2BimorphicInlining implements MyInterfaceBimorphicInlining {
    int x;

    public TestValue2BimorphicInlining(int x) {
        this.x = x;
    }

    public TestValue2BimorphicInlining hash(MyInterfaceBimorphicInlining arg) {
        return new TestValue2BimorphicInlining(x + ((TestValue2BimorphicInlining)arg).x);
    }
}

class TestClassBimorphicInlining implements MyInterfaceBimorphicInlining {
    int x;

    public TestClassBimorphicInlining(int x) {
        this.x = x;
    }

    public MyInterfaceBimorphicInlining hash(MyInterfaceBimorphicInlining arg) {
        return new TestClassBimorphicInlining(x + ((TestClassBimorphicInlining)arg).x);
    }
}

public class TestBimorphicInlining {

    public static MyInterfaceBimorphicInlining test1(MyInterfaceBimorphicInlining i1, MyInterfaceBimorphicInlining i2) {
        MyInterfaceBimorphicInlining result = i1.hash(i2);
        i1.hash(i2);
        return result;
    }

    public static MyInterfaceBimorphicInlining test2(MyInterfaceBimorphicInlining i1, MyInterfaceBimorphicInlining i2) {
        MyInterfaceBimorphicInlining result = i1.hash(i2);
        i1.hash(i2);
        return result;
    }

    public static MyInterfaceBimorphicInlining test3(MyInterfaceBimorphicInlining i1, MyInterfaceBimorphicInlining i2) {
        MyInterfaceBimorphicInlining result = i1.hash(i2);
        i1.hash(i2);
        return result;
    }

    public static MyInterfaceBimorphicInlining test4(MyInterfaceBimorphicInlining i1, MyInterfaceBimorphicInlining i2) {
        MyInterfaceBimorphicInlining result = i1.hash(i2);
        i1.hash(i2);
        return result;
    }

    static public void main(String[] args) {
        Random rand = new Random();
        TestClassBimorphicInlining  testObject = new TestClassBimorphicInlining(rand.nextInt());
        TestValue1BimorphicInlining TestValue1BimorphicInlining = new TestValue1BimorphicInlining(rand.nextInt());
        TestValue2BimorphicInlining TestValue2BimorphicInlining = new TestValue2BimorphicInlining(rand.nextInt());

        for (int i = 0; i < 10_000; ++i) {
            // Trigger bimorphic inlining by calling test methods with different arguments
            MyInterfaceBimorphicInlining arg, res;
            boolean rare = (i % 10 == 0);

            arg = rare ? TestValue1BimorphicInlining : testObject;
            res = test1(arg, arg);
            Asserts.assertEQ(rare ? ((TestValue1BimorphicInlining)res).x : ((TestClassBimorphicInlining)res).x, 2 * (rare ? TestValue1BimorphicInlining.x : testObject.x), "test1 failed");

            arg = rare ? testObject : TestValue1BimorphicInlining;
            res = test2(arg, arg);
            Asserts.assertEQ(rare ? ((TestClassBimorphicInlining)res).x : ((TestValue1BimorphicInlining)res).x, 2 * (rare ? testObject.x : TestValue1BimorphicInlining.x), "test2 failed");

            arg = rare ? TestValue1BimorphicInlining : TestValue2BimorphicInlining;
            res = test3(arg, arg);
            Asserts.assertEQ(rare ? ((TestValue1BimorphicInlining)res).x : ((TestValue2BimorphicInlining)res).x, 2 * (rare ? TestValue1BimorphicInlining.x : TestValue2BimorphicInlining.x), "test3 failed");

            arg = rare ? TestValue2BimorphicInlining : TestValue1BimorphicInlining;
            res = test4(arg, arg);
            Asserts.assertEQ(rare ? ((TestValue2BimorphicInlining)res).x : ((TestValue1BimorphicInlining)res).x, 2 * (rare ? TestValue2BimorphicInlining.x : TestValue1BimorphicInlining.x), "test4 failed");
        }
    }
}
