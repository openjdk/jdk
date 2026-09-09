/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test scalarization in returns with unloaded return types.
 * @library /test/lib /compiler/whitebox /
 * @enablePreview
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=dontinline,*::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedReturnTypes
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses
 *                   -Xbatch -XX:CompileCommand=dontinline,*::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedReturnTypes
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-PreloadClasses -XX:+AlwaysIncrementalInline
 *                   compiler.valhalla.inlinetypes.TestUnloadedReturnTypes
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Method;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

value class MyValue1UnloadedRetTypes {
    int x;

    public MyValue1UnloadedRetTypes(int x) {
        this.x = x;
    }
}

class MyHolder1 {
    static MyValue1UnloadedRetTypes test1(boolean b) {
        return b ? new MyValue1UnloadedRetTypes(42) : null;
    }
}

// Uses all registers available for scalarized return on x64
value class MyValue2UnloadedRetTypes {
    int i1 = 42;
    int i2 = 43;
    int i3 = 44;
    int i4 = 45;
    int i5 = 46;
    double d1 = 47;
    double d2 = 48;
    double d3 = 49;
    double d4 = 50;
    double d5 = 51;
    double d6 = 52;
    double d7 = 53;
    double d8 = 54;
}

class MyHolder2Super {
    public MyValue2UnloadedRetTypes test2Virtual(boolean loadIt) {
        if (loadIt) {
            return new MyValue2UnloadedRetTypes();
        }
        return null;
    }
}

class MyHolder2 extends MyHolder2Super {
    public MyValue2UnloadedRetTypes test2(boolean loadIt) {
        if (loadIt) {
            return new MyValue2UnloadedRetTypes();
        }
        return null;
    }

    @Override
    public MyValue2UnloadedRetTypes test2Virtual(boolean loadIt) {
        if (loadIt) {
            return new MyValue2UnloadedRetTypes();
        }
        return null;
    }
}

// Uses all registers available for scalarized return on AArch64
value class MyValue3UnloadedRetTypes {
    int i1 = 42;
    int i2 = 43;
    int i3 = 44;
    int i4 = 45;
    int i5 = 46;
    int i6 = 47;
    int i7 = 48;
    double d1 = 49;
    double d2 = 50;
    double d3 = 51;
    double d4 = 52;
    double d5 = 53;
    double d6 = 54;
    double d7 = 55;
    double d8 = 56;
}

class MyHolder3Super {
    public MyValue3UnloadedRetTypes test3Virtual(boolean loadIt) {
        if (loadIt) {
            return new MyValue3UnloadedRetTypes();
        }
        return null;
    }
}

class MyHolder3 extends MyHolder3Super {
    public MyValue3UnloadedRetTypes test3(boolean loadIt) {
        if (loadIt) {
            return new MyValue3UnloadedRetTypes();
        }
        return null;
    }

    @Override
    public MyValue3UnloadedRetTypes test3Virtual(boolean loadIt) {
        if (loadIt) {
            return new MyValue3UnloadedRetTypes();
        }
        return null;
    }
}

public class TestUnloadedReturnTypes {
    public static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    static Object res = null;

    public static void test1(boolean b) {
        res = MyHolder1.test1(b);
    }

    public static Object test2(MyHolder2 h, boolean loadIt) {
        return h.test2(loadIt);
    }

    public static Object test2Virtual(MyHolder2Super h, boolean loadIt) {
        return h.test2Virtual(loadIt);
    }

    public static Object test3(MyHolder3 h, boolean loadIt) {
        return h.test3(loadIt);
    }

    public static Object test3Virtual(MyHolder3Super h, boolean loadIt) {
        return h.test3Virtual(loadIt);
    }

    public static void main(String[] args) throws Exception {
        // C1 compile caller method
        Method m = TestUnloadedReturnTypes.class.getMethod("test1", boolean.class);
        WHITE_BOX.enqueueMethodForCompilation(m, 3);

        MyHolder2 h2 = new MyHolder2();
        MyHolder2Super h2Super = new MyHolder2Super();
        MyHolder3 h3 = new MyHolder3();
        MyHolder3Super h3Super = new MyHolder3Super();

        // Warmup
        for (int i = 0; i < 100_000; ++i) {
            MyHolder1.test1((i % 2) == 0);
            Asserts.assertEquals(test2(h2, false), null);
            Asserts.assertEquals(test2Virtual(h2, false), null);
            Asserts.assertEquals(test2Virtual(h2Super, false), null);
            Asserts.assertEquals(test3(h3, false), null);
            Asserts.assertEquals(test3Virtual(h3, false), null);
            Asserts.assertEquals(test3Virtual(h3Super, false), null);
        }

        test1(true);
        Asserts.assertEquals(((MyValue1UnloadedRetTypes)res).x, 42);
        test1(false);
        Asserts.assertEquals(res, null);

        // Deopt and re-compile callee at C2 so it returns scalarized, then deopt again
        for (int i = 0; i < 100_000; ++i) {
            Asserts.assertEquals(h2.test2(true), new MyValue2UnloadedRetTypes());
            Asserts.assertEquals(h2Super.test2Virtual(true), new MyValue2UnloadedRetTypes());
            Asserts.assertEquals(h3.test3(true), new MyValue3UnloadedRetTypes());
            Asserts.assertEquals(h3Super.test3Virtual(true), new MyValue3UnloadedRetTypes());
        }
        Asserts.assertEquals(test2(h2, true), new MyValue2UnloadedRetTypes());
        Asserts.assertEquals(test2Virtual(h2, true), new MyValue2UnloadedRetTypes());
        Asserts.assertEquals(test2Virtual(h2Super, true), new MyValue2UnloadedRetTypes());
        Asserts.assertEquals(test3(h3, true), new MyValue3UnloadedRetTypes());
        Asserts.assertEquals(test3Virtual(h3, true), new MyValue3UnloadedRetTypes());
        Asserts.assertEquals(test3Virtual(h3Super, true), new MyValue3UnloadedRetTypes());
    }
}
