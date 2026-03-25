/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=serialgc
 * @bug 8321734 8348961 8332406
 * @requires vm.gc.Serial
 * @summary Test that CmpPNode::sub and SubTypeCheckNode::sub correctly identify unrelated classes based on the flat
 *          in array property of the types. Additionally check that the type system properly handles the case of a
 *          super class being flat in array while the sub klass could be flat in array.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestFlatInArraysFolding serial
 */

/*
 * @test
 * @bug 8321734 8348961 8332406
 * @summary Test that CmpPNode::sub and SubTypeCheckNode::sub correctly identify unrelated classes based on the flat
 *          in array property of the types. Additionally check that the type system properly handles the case of a
 *          super class being flat in array while the sub klass could be flat in array.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main compiler.valhalla.inlinetypes.TestFlatInArraysFolding
 */

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;

import jdk.internal.vm.annotation.LooselyConsistentValue;

public class TestFlatInArraysFolding {
    static Object[] oArrArr = new Object[100][100];
    static Object[] oArr = new Object[100];
    static Object o = new Object();
    static Object[] oArrSmall = new Object[2];
    static Object[] vArr = new V[2];
    static {
        oArrSmall[0] = new Object();
        oArrSmall[1] = new Object();
        vArr[0] = new V();
        vArr[1] = new V();
    }
    static int limit = 2;

    // Make sure these are loaded such that A has a flat in array and a not flat in array sub class.
    static FlatInArray flat = new FlatInArray(34);
    static NotFlatInArray notFlat = new NotFlatInArray(34);

    // Make sure PUnique is the unique concrete sub class loaded from AUnique.
    static PUnique pUnique = new PUnique(34);

    static int iFld;

    public static void main(String[] args) {
        // TODO 8350865 Scenarios are equivalent, FlatArrayElementMaxSize does not exist anymore
        Scenario flatArrayElementMaxSize1Scenario = new Scenario(1, "-XX:-UseArrayFlattening");
        Scenario flatArrayElementMaxSize4Scenario = new Scenario(2, "-XX:-UseArrayFlattening");
        Scenario noFlagsScenario = new Scenario(3);
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(0)
                .addFlags("--enable-preview",
                          "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED",
                          "--add-exports", "java.base/jdk.internal.vm.annotation=ALL-UNNAMED")
                .addScenarios(flatArrayElementMaxSize1Scenario,
                              flatArrayElementMaxSize4Scenario, noFlagsScenario);

        if (args.length > 0) {
            // Disable Loop Unrolling for IR matching in testCmpP().
            // Use IgnoreUnrecognizedVMOptions since LoopMaxUnroll is a C2 flag.
            // testSubTypeCheck() only triggers with SerialGC.
            Scenario serialGCScenario = new Scenario(4, "-XX:+UseSerialGC", "-XX:+IgnoreUnrecognizedVMOptions",
                                                     "-XX:LoopMaxUnroll=0", "-XX:+UseArrayFlattening");
            testFramework.addScenarios(serialGCScenario);
        }
        Scenario noMethodTraps = new Scenario(5, "-XX:PerMethodTrapLimit=0", "-Xbatch");
        testFramework.addScenarios(noMethodTraps);
        testFramework.start();
    }

    // SubTypeCheck is not folded while CheckCastPPNode is replaced by top which results in a bad graph (data dies while
    // control does not).
    @Test
    static void testSubTypeCheck() {
        for (int i = 0; i < 100; i++) {
            Object arrayElement = oArrArr[i];
            oArr = (Object[])arrayElement;
        }
    }

    @Test
    @IR(counts = {IRNode.COUNTED_LOOP, "2", // Loop Unswitching done?
                  IRNode.STORE_I, "1"}, // CmpP folded in unswitched loop version with flat in array?
        applyIf = {"LoopMaxUnroll", "0"})
    static void testCmpP() {
        Object[] arr = oArr;
        if (arr == null) {
            // Needed for the 'arrayElement == arr' to fold in
            // the flat array case because both could be null.
            throw new NullPointerException("arr is null");
        }
        for (int i = 0; i < 100; i++) {
            Object arrayElement = oArrArr[i];
            if (arrayElement == arr) {
                iFld = 34;
            }
        }
    }

    // Type system does not correctly handle the case that a super klass is flat in array while the sub klass is
    // maybe flat in array. This leads to a bad graph.
    @Test
    static void testUnswitchingAbstractClass() {
        Object[] arr = oArr;
        for (int i = 0; i < 100; i++) {
            Object arrayElement = arr[i];
            if (arrayElement instanceof A) {
                A a = (A)arrayElement;
                if (a == o) {
                    a.foo();
                }
            }
        }
    }

    // Same as testUnswitchingAbstractClass() but with interfaces. This worked before because I has type Object(I)
    // from which Object is a sub type of.
    @Test
    static void testUnswitchingInterface() {
        Object[] arr = oArr;
        for (int i = 0; i < 100; i++) {
            Object arrayElement = arr[i];
            if (arrayElement instanceof I) {
                I iVar = (I)arrayElement;
                if (iVar == o) {
                    iVar.bar();
                }
            }
        }
    }

    // TODO 8350865 FlatArrayElementMaxSize does not exist anymore
    // PUnique is the unique concrete sub class of AUnique and is not flat in array (with FlatArrayElementMaxSize=4).
    // The CheckCastPP output of the sub type check uses PUnique while the sub type check itself uses AUnique. This leads
    // to a bad graph because the type system determines that the flat in array super klass cannot be met with the
    // not flat in array sub klass. But the sub type check does not fold away because AUnique *could* be flat in array.
    // Fixed with in JDK-8328480 in mainline but not yet merged in. Applied manually to make this work.
    @Test
    static void testSubTypeCheckNotFoldedParsingAbstractClass() {
        Object[] arr = oArr;
        for (int i = 0; i < 100; i++) {
            Object arrayElement = arr[i];
            if (arrayElement instanceof AUnique) {
                AUnique aUnique = (AUnique)arrayElement;
                if (aUnique == o) {
                    aUnique.foo();
                }
            }
        }
    }

    // Same as testSubTypeCheckNotFoldedParsingAbstractClass() but with interfaces. This worked before because IUnique
    // has type Object(IUnique) from which Object is a sub type of.
    @Test
    static void testSubTypeCheckNotFoldedParsingInterface() {
        Object[] arr = oArr;
        for (int i = 0; i < 100; i++) {
            Object arrayElement = arr[i];
            if (arrayElement instanceof IUnique) {
                IUnique iUnique = (IUnique)arrayElement;
                if (iUnique == o) {
                    iUnique.bar();
                }
            }
        }
    }

    @Test
    @Warmup(10000)
    static void testJoinSameKlassDifferentFlatInArray() {
        // Accessing the array: It could be a flat array. We therefore add a Phi to select from the normal vs.
        // flat in array access:
        //     Phi(Object:flat_in_array, Object) -> CheckCastPP[Object:NotNull:exact]
        for (Object o : oArrSmall) {
            // We speculate that we always call Object::hashCode() and thus add a CheckCastPP[Object:NotNull:exact]
            // together with a speculate_class_check trap on the failing path.
            // We decide to unswitch the loop to get a loop version where we only have flat in array accesses. This
            // means we can get rid of the Phi. During IGVN and folding some nodes we eventually end up with:
            //    CheckCastPP[Object:NotNull (flat_in_array)] -> CheckCastPP[Object:NotNull:exact]
            //
            // We know that we have some kind of Value class that needs to be joined with an exact Object that is not
            // a value class. Thus, the result in an empty set. But this is missing in the type system. We fail with
            // an assertion. This is fixed with 8348961.
            //
            // Pre-8332406:
            // To make this type system change work, we require that the TypeInstKlassPtr::not_flat_in_array() takes
            // exactness information into account to also fold the corresponding control path. This requires another
            // follow up fix: The super class of a sub type check is always an exact class, i.e. "o instanceof Super".
            // We need a version of TypeInstKlassPtr::not_flat_in_array() that treats "Super" as inexact. Failing to do
            // so will erroneously fold a sub type check away (covered by testSubTypeCheckForObjectReceiver()).
            //
            // Post-8332406:
            // We now directly cast the klass pointer in SubTypeCheckNode::sub() to inexact which triggers a
            // recomputation of the flat in array property. This will turn an exact TypeInstKlassPtr such as Object,
            // which is not flat in array, into an inexact maybe flat in array TypeInstKlassPtr.
            o.hashCode();
        }
    }

    @Test
    @Warmup(10000)
    static void testSubTypeCheckForObjectReceiver() {
        for (int i = 0; i < limit; i++) {
            // We perform a sub type check that V is a sub type of Object. This is obviously true
            vArr[i].hashCode();
        }
    }

    interface IUnique {
        abstract void bar();
    }

    static abstract value class AUnique implements IUnique {
        abstract void foo();
    }

    @LooselyConsistentValue
    static value class PUnique extends AUnique {
        int x;
        int y;
        PUnique(int x) {
            this.x = x;
            this.y = 34;
        }

        public void foo() {}
        public void bar() {}
    }

    interface I {
        void bar();
    }

    static abstract value class A implements I {
        abstract void foo();
    }

    @LooselyConsistentValue
    static value class FlatInArray extends A implements I {
        int x;
        FlatInArray(int x) {
            this.x = x;
        }

        public void foo() {}
        public void bar() {}
    }

    // TODO 8350865 FlatArrayElementMaxSize does not exist anymore
    // Not flat in array with -XX:FlatArrayElementMaxSize=4
    static value class NotFlatInArray extends A implements I {
        int x;
        int y;
        NotFlatInArray(int x) {
            this.x = x;
            this.y = 34;
        }

        public void foo() {}
        public void bar() {}
    }

    static value class V {}

    static final Object OBJ = new Object();

    @Test
    static Object testEqualMeet1(boolean b, Object[] array, int i) {
        return b ? array[i] : OBJ;
    }

    @Run(test = "testEqualMeet1")
    public void testEqualMeet1_verifier() {
        testEqualMeet1(true, new Object[1], 0);
        testEqualMeet1(false, new Object[1], 0);
    }

    @Test
    static Object testEqualMeet2(boolean b, Object[] array, int i) {
        return b ? OBJ : array[i];
    }

    @Run(test = "testEqualMeet2")
    public void testEqualMeet2_verifier() {
        testEqualMeet2(true, new Object[1], 0);
        testEqualMeet2(false, new Object[1], 0);
    }

    @Test
    public static Object test8332406(boolean b, Object[] array, int i) {
        return b ? array[i] : OBJ;
    }

    @Run(test = "test8332406")
    public static void runTest8332406() {
        test8332406(true, new Object[1], 0);
        test8332406(false, new Object[1], 0);
    }
}
