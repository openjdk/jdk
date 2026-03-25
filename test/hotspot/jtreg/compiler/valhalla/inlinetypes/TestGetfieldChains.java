/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;

import jdk.test.lib.Asserts;

/*
 * @test
 * @key randomness
 * @summary Verify that chains of getfields on flat fields are correctly optimized.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile GetfieldChains.jcod
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestGetfieldChains
 */

@LooselyConsistentValue
value class Point {
    int x = 4;
    int y = 7;
}

@LooselyConsistentValue
value class Rectangle {
    @NullRestricted
    Point p0 = new Point();
    @NullRestricted
    Point p1 = new Point();
}

class NamedRectangle {
    @NullRestricted
    Rectangle rect;
    String name = "";

    NamedRectangle() {
        rect = new Rectangle();
        super();
    }

    static int getP1X(NamedRectangle nr) {
        return nr.rect
            .p1
            .x;
    }

    static Point getP1(NamedRectangle nr) {
        return nr.rect
            .p1;
    }
}

public class TestGetfieldChains {

    public static void main(String[] args) {

        final Scenario[] scenarios = {
                new Scenario(0,
                        // C1 only
                        "-XX:TieredStopAtLevel=1",
                        "-XX:+TieredCompilation"),
                new Scenario(1,
                        // C2 only. (Make sure the tests are correctly written)
                        "-XX:TieredStopAtLevel=4",
                        "-XX:-TieredCompilation",
                        "-XX:-OmitStackTraceInFastThrow"),
                new Scenario(2,
                        // interpreter only
                        "-Xint"),
                new Scenario(3,
                        // Xcomp Only C1
                        "-XX:TieredStopAtLevel=1",
                        "-XX:+TieredCompilation",
                        "-Xcomp"),
                new Scenario(4,
                        // Xcomp Only C2
                        "-XX:TieredStopAtLevel=4",
                        "-XX:-TieredCompilation",
                        "-XX:-OmitStackTraceInFastThrow",
                        "-Xcomp")
        };

        InlineTypes.getFramework()
                   .addScenarios(scenarios)
                   .addFlags("--enable-preview",
                             "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED",
                             "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED")
                   .start();
    }


    // Simple chain of getfields ending with value type field
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public int test1() {
        return NamedRectangle.getP1X(new NamedRectangle());
    }

    @Run(test = "test1")
    public void test1_verifier() {
        int res = test1();
        Asserts.assertEQ(res, 4);
    }

    // Simple chain of getfields ending with a flat field
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public Point test2() {
        return NamedRectangle.getP1(new NamedRectangle());
    }

    @Run(test = "test2")
    public void test2_verifier() {
        Point p = test2();
        Asserts.assertEQ(p.x, 4);
        Asserts.assertEQ(p.y, 7);
    }

    // Chain of getfields but the initial receiver is null
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public NullPointerException test3() {
        NullPointerException npe = null;
        try {
            NamedRectangle.getP1X(null);
            throw new RuntimeException("No NullPointerException thrown");
        } catch (NullPointerException e) {
            npe = e;
        }
        return npe;
    }

    @Run(test = "test3")
    public void test3_verifier() {
        NullPointerException npe = test3();
        Asserts.assertNE(npe, null);
        StackTraceElement st = npe.getStackTrace()[0];
        Asserts.assertEQ(st.getMethodName(), "getP1X");
    }

    // Chain of getfields but one getfield in the middle of the chain triggers an illegal access
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public IllegalAccessError test4() {
        IllegalAccessError iae = null;
        try {
            int i = NamedRectangleP.getP1Y(new NamedRectangleP());
            throw new RuntimeException("No IllegalAccessError thrown");
        } catch (IllegalAccessError e) {
            iae = e;
        }
        return iae;
    }

    @Run(test = "test4")
    public void test4_verifier() {
        IllegalAccessError iae = test4();
        Asserts.assertNE(iae, null);
        StackTraceElement st = iae.getStackTrace()[0];
        Asserts.assertEQ(st.getMethodName(), "getP1Y");
        Asserts.assertTrue(iae.getMessage().contains("class compiler.valhalla.inlinetypes.NamedRectangleP tried to access private field compiler.valhalla.inlinetypes.RectangleP.p1"));
    }

    // Chain of getfields but the last getfield triggers a NoSuchFieldError
    @Test(compLevel = CompLevel.C1_SIMPLE)
    public NoSuchFieldError test5() {
        NoSuchFieldError nsfe = null;
        try {
            int i = NamedRectangleN.getP1X(new NamedRectangleN());
            throw new RuntimeException("No NoSuchFieldError thrown");
        } catch (NoSuchFieldError e) {
            nsfe = e;
        }
        return nsfe;
    }

    @Run(test = "test5")
    public void test5_verifier() {
        NoSuchFieldError nsfe = test5();
        Asserts.assertNE(nsfe, null);
        StackTraceElement st = nsfe.getStackTrace()[0];
        Asserts.assertEQ(st.getMethodName(), "getP1X");
        Asserts.assertEQ(nsfe.getMessage(), "Class compiler.valhalla.inlinetypes.PointN does not have member field 'int x'");
    }

    @LooselyConsistentValue
    static value class EmptyType1 { }

    @LooselyConsistentValue
    static value class EmptyContainer1 {
        int i = 0;
        @NullRestricted
        EmptyType1 et = new EmptyType1();
    }

    @LooselyConsistentValue
    static value class Container1 {
        @NullRestricted
        EmptyContainer1 container0 = new EmptyContainer1();
        @NullRestricted
        EmptyContainer1 container1 = new EmptyContainer1();
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    public EmptyType1 test6() {
        Container1 c = new Container1();
        return c.container1.et;
    }

    @Run(test = "test6")
    public void test6_verifier() {
        EmptyType1 et = test6();
        Asserts.assertEQ(et, new EmptyType1());
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    public EmptyType1 test7() {
        Container1[] ca = (Container1[])ValueClass.newNullRestrictedNonAtomicArray(Container1.class, 10, new Container1());
        return ca[3].container0.et;
    }

    @Run(test = "test7")
    public void test7_verifier() {
        EmptyType1 et = test7();
        Asserts.assertEQ(et, new EmptyType1());
    }

    // Same as test6/test7 but not null-free and EmptyContainer2 with only one field

    static value class EmptyType2 { }

    // TODO 8376254: C1 bailouts if the type of the nullable flat field is uninitialized
    static final EmptyType2 LOAD_EMPTY_TYPE_2 = new EmptyType2();
    static final EmptyContainer2 LOAD_EMPTY_CONTAINER_2 = new EmptyContainer2();

    static value class EmptyContainer2 {
        EmptyType2 et = null;
    }

    static value class Container2 {
        EmptyContainer2 container0 = new EmptyContainer2();
        EmptyContainer2 container1 = new EmptyContainer2();
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    public EmptyType2 test8() {
        Container2 c = new Container2();
        return c.container1.et;
    }

    @Run(test = "test8")
    public void test8_verifier() {
        EmptyType2 et = test8();
        Asserts.assertEQ(et, null);
    }

    @Test(compLevel = CompLevel.C1_SIMPLE)
    public EmptyType2 test9() {
        Container2[] ca = new Container2[10];
        ca[3] = new Container2();
        return ca[3].container0.et;
    }

    @Run(test = "test9")
    public void test9_verifier() {
        EmptyType2 et = test9();
        Asserts.assertEQ(et, null);
    }
}
