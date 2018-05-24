/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package vm.runtime.defmeth;

import nsk.share.test.TestBase;
import vm.runtime.defmeth.shared.DefMethTest;
import vm.runtime.defmeth.shared.annotation.Crash;
import vm.runtime.defmeth.shared.data.*;
import vm.runtime.defmeth.shared.builder.TestBuilder;
import vm.runtime.defmeth.shared.annotation.NotApplicableFor;
import static vm.runtime.defmeth.shared.data.method.body.CallMethod.Invoke.*;
import static vm.runtime.defmeth.shared.data.method.body.CallMethod.IndexbyteOp.*;
import static vm.runtime.defmeth.shared.ExecutionMode.*;

/*
 * Scenarios on static methods in interfaces.
 */
public class StaticMethodsTest extends DefMethTest {

    public static void main(String[] args) {
        TestBase.runTest(new StaticMethodsTest(), args);
    }

    // static method in interface
    /*
     * testStaticMethod
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * class C implements I {}
     */
    public void testStaticMethod() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().staticCallSite(I, "m", "()I").returns(1).done()

        .run();
    }

    // invoke[virtual|interface|special] from same/subintf
    /*
     * testInvokeVirtual
     *
     * interface I {
     *  default static public int staticM() { return 1; }
     *  default public int m() { return ((I)this).staticM(); }
     * }
     *
     * class C implements I {}
     */
    public void testInvokeVirtual() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("staticM", "()I")
                    .static_().public_().returns(1).build()

                // force an invokevirtual MR of staticM()
                .defaultMethod("m", "()I")
                    .invoke(VIRTUAL, b.intfByName("I"), null, "staticM", "()I", METHODREF).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().staticCallSite(I, "staticM", "()I").returns(1).done()

         .test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
         .test().callSite(C, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()

        .run();
    }

    /*
     * testInvokeIntf
     *
     * interface I {
     *  default static public int staticM() { return 1; }
     *  default public int m() { return ((I)this).staticM(); }
     * }
     *
     * class C implements I {}
     */
    public void testInvokeIntf() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("staticM", "()I")
                    .static_().public_().returns(1).build()

                .defaultMethod("m", "()I")
                    .invoke(INTERFACE, b.intfByName("I"), null, "staticM", "()I", CALLSITE).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().staticCallSite(I, "staticM", "()I").returns(1).done()

         .test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
         .test().callSite(C, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()

        .run();
    }

    /*
     * testInvokeSpecial
     *
     * interface I {
     *  default static public int staticM() { return 1; }
     *  default public int m() { return I.super.staticM(); }
     * }
     *
     * class C implements I {}
     */
    public void testInvokeSpecial() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("staticM", "()I")
                    .static_().public_().returns(1).build()

                .defaultMethod("m", "()I")
                    .invoke(SPECIAL, b.intfByName("I"), null, "staticM", "()I", CALLSITE).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().staticCallSite(I, "staticM", "()I").returns(1).done()

         .test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
         .test().callSite(C, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()

        .run();
    }

    /*
     * testStaticVsDefault
     *
     * interface I {
     *  default static public int m() { return 1; }
     *  default public int m() { return 2; }
     * }
     *
     * class C implements I {}
     */
    public void testStaticVsDefault() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
                .defaultMethod("m", "()I")
                    .public_().returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        b.test().staticCallSite(I, "m", "()I").throws_(ClassFormatError.class).done()

         // FIXME: throws exception during an attempt to lookup Test2.test() method

         // Invalid test. ClassFormatError is thrown at verification time, rather
         // than execution time.
         // .test().callSite(I, C, "m", "()I").throws_(ClassFormatError.class).done()
         .test().callSite(C, C, "m", "()I").throws_(ClassFormatError.class).done()

        .run();
    }

    // call static method from default method
    /*
     * testInvokeFromDefaultMethod
     *
     * interface I {
     *  default static public int staticPublicM() { return 1; }
     *  default public int invokePublic() { return I.staticPublicM(); }
     *  default static private int staticPrivateM() { return 1; }
     *  default public int invokePrivate() { return I.staticPrivateM(); }
     * }
     *
     * class C implements I {}
     */
    public void testInvokeFromDefaultMethod() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("staticPublicM", "()I")
                    .static_().public_().returns(1).build()
                .defaultMethod("invokePublic", "()I")
                    .invokeStatic(b.intfByName("I"), "staticPublicM", "()I").build()

                .defaultMethod("staticPrivateM", "()I")
                    .static_().private_().returns(1).build()
                .defaultMethod("invokePrivate", "()I")
                    .invokeStatic(b.intfByName("I"), "staticPrivateM", "()I").build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        Class expectedClass;
        if (factory.getExecutionMode().equals("REFLECTION")) {
            expectedClass = NoSuchMethodException.class;
        } else {
            expectedClass = IllegalAccessError.class;
        }

        // call static method from another class
        b.test().staticCallSite(I, "staticPublicM", "()I").returns(1).done()
         .test().staticCallSite(I, "staticPrivateM", "()I").throws_(expectedClass).done()

         // call public static method from default method
         .test().callSite(I, C, "invokePublic", "()I").returns(1).done()
         .test().callSite(C, C, "invokePublic", "()I").returns(1).done()

         // call private static method from default method
         .test().callSite(I, C, "invokePrivate", "()I").returns(1).done()
         .test().callSite(C, C, "invokePrivate", "()I").returns(1).done()

        .run();
    }

    // call static method from implementing subclass
    /*
     * testInvokeFromSubclass
     *
     * interface I {
     *  default static public int staticPublicM() { return 1; }
     *  default static private int staticPrivateM() { return 1; }
     * }
     *
     * class C implements I {
     *  public int invokePublic() { return I.staticPublicM(); }
     *  public int invokePrivate() { return I.staticPublicM(); }
     *
     * I.staticPublicM();  ==> returns 1;
     * I.staticPrivateM(); ==> Either NSME or IAE depending on execution mode
     * C c = new C(); c.invokePublic(); ==> returns 1 or if -ver < 52 IAE or VerifyError
     * C c = new C(); c.invokePrivate() ==> IAE or if -ver < 52, IAE or VerifyError
     * }
     */

    @NotApplicableFor(modes = { REDEFINITION }) // Can't redefine a class that gets error during loading
    public void testInvokeFromSubclass() throws Exception {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("staticPublicM", "()I")
                    .static_().public_().returns(1).build()

                .defaultMethod("staticPrivateM", "()I")
                    .static_().private_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I)
                .concreteMethod("invokePublic", "()I")
                    .invokeStatic(b.intfByName("I"), "staticPublicM", "()I").build()
                .concreteMethod("invokePrivate", "()I")
                    .invokeStatic(b.intfByName("I"), "staticPrivateM", "()I").build()
            .build();

        Class expectedError1;
        if (factory.getExecutionMode().equals("REFLECTION")) {
            expectedError1 = NoSuchMethodException.class;
        } else {
            expectedError1 = IllegalAccessError.class;
        }

        // Adjust for -ver < 52
        if (factory.getVer() >=52) {
            // call static method from another class
            b.test().staticCallSite(I, "staticPublicM", "()I").returns(1).done()
             .test().staticCallSite(I, "staticPrivateM", "()I").throws_(expectedError1).done()

            // call static method from implementing subclass
             .test().callSite(C, C, "invokePublic", "()I").returns(1).done()
             .test().callSite(C, C, "invokePrivate", "()I").throws_(IllegalAccessError.class).done()

            .run();
        } else {
            // call static method from another class
            b.test().staticCallSite(I, "staticPublicM", "()I").returns(1).done()
             .test().staticCallSite(I, "staticPrivateM", "()I").throws_(expectedError1).done()

            // call static method from implementing subclass
            // invokestatic IMR - not supported for ver < 52
             .test().callSite(C, C, "invokePublic",  "()I").throws_(VerifyError.class).done()
             .test().callSite(C, C, "invokePrivate", "()I").throws_(VerifyError.class).done()

            .run();
        }
    }

    // static method doesn't participate in default method analysis:
    //   method overriding
    /*
     * testNotInherited
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * class C implements I {}
     */
    public void testNotInherited() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().staticCallSite(I, "m", "()I").returns(1).done()
              // invokeinterface to static method ==> ICCE
              .test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(C, C, "m", "()I").throws_(NoSuchMethodError.class).done()
            .run();
        } else {
            b.test().staticCallSite(I, "m", "()I").returns(1).done()
             .test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(C, C, "m", "()I").throws_(NoSuchMethodError.class).done()
            .run();
        }
    }

    /*
     * testDefaultVsConcrete
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * class C implements I {
     *  public int m() { return 2; }
     * }
     * TEST: I o = new C(); o.m()I throws ICCE
     * TEST: C o = new C(); o.m()I == 2
     */
    public void testDefaultVsConcrete() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I)
                .concreteMethod("m", "()I").returns(2).build()
            .build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            // invokeinterface to static method ==> ICCE
            b.test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(C, C, "m", "()I").returns(2).done().run();
        } else {
            b.test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(C, C, "m", "()I").returns(2).done().run();
        }

    }

    /*
     * TEST: StaticMethodsTest.testOverrideStatic
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * interface J extends I {
     *  default public int m() { return 2; }
     * }
     *
     * class C implements J {
     * }
     */
    public void testOverrideStatic() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        Interface J = b.intf("J").extend(I)
                .defaultMethod("m", "()I")
                    .returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(J).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().staticCallSite(I, "m", "()I").returns(1).done()
             .test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(J, C, "m", "()I").returns(2).done()
             .test().callSite(C, C, "m", "()I").returns(2).done()
            .run();
        } else {
            b.test().staticCallSite(I, "m", "()I").returns(1).done()
             .test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(J, C, "m", "()I").returns(2).done()
             .test().callSite(C, C, "m", "()I").returns(2).done()
            .run();
        }

    }

    /*
     * testOverrideDefault
     *
     * interface I {
     *  default public int m() { return 1; }
     * }
     *
     * interface J extends I {
     *  default static public int m() { return 2; }
     * }
     *
     * class C implements J {}
     *
     * TEST: I o = new C(); o.m()I == 1
     * TEST: J o = new C(); o.m()I == ICCE
     * TEST: C o = new C(); o.m()I == 1
     */
    public void testOverrideDefault() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .returns(1).build()
            .build();

        Interface J = b.intf("J").extend(I)
                .defaultMethod("m", "()I")
                    .static_().public_().returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(J).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(J, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(C, C, "m", "()I").returns(1).done()

            .run();

        } else {
            // Reflection correctly finds the static method defined in J and
            // calls it with invokestatic.

            b.test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(J, C, "m", "()I").returns(2).done()
             .test().callSite(C, C, "m", "()I").returns(1).done()

            .run();
        }
    }

    /*
     * testReabstract
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * interface J extends I {
     *  abstract public int m();
     * }
     *
     * class C implements J {}
     *
     * TEST: I o = new C(); o.m()I throws ICCE
     *                             -mode reflect returns 1
     * TEST: J o = new C(); o.m()I throws AME
     * TEST: C o = new C(); o.m()I throws AME
     */
    public void testReabstract() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        Interface J = b.intf("J").extend(I)
                .abstractMethod("m", "()I").build()
            .build();

        ConcreteClass C = b.clazz("C").implement(J).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(J, C, "m", "()I").throws_(AbstractMethodError.class).done()
             .test().callSite(C, C, "m", "()I").throws_(AbstractMethodError.class).done()
            .run();
        } else {
            b.test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(J, C, "m", "()I").throws_(AbstractMethodError.class).done()
             .test().callSite(C, C, "m", "()I").throws_(AbstractMethodError.class).done()
            .run();
        }
    }

    /*
     * testOverrideAbstract
     *
     * interface I {
     *  abstract public int m();
     * }
     *
     * interface J extends I {
     *  default static public int m() { return 1; }
     * }
     *
     * class C implements J {}
     *
     * TEST: I o = new C(); o.m()I throws AME
     * TEST: J o = new C(); o.m()I throws ICCE
     *                             -mode reflect returns 1
     * TEST: C o = new C(); o.m()I throws AME
     */
    public void testOverrideAbstract() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .abstractMethod("m", "()I").build()
            .build();

        Interface J = b.intf("J").extend(I)
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(J).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(I, C, "m", "()I").throws_(AbstractMethodError.class).done()
             .test().callSite(J, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(C, C, "m", "()I").throws_(AbstractMethodError.class).done()

            .run();
        } else {
            b.test().callSite(I, C, "m", "()I").throws_(AbstractMethodError.class).done()
             .test().callSite(J, C, "m", "()I").returns(1).done()
             .test().callSite(C, C, "m", "()I").throws_(AbstractMethodError.class).done()

            .run();
        }
    }

    /*
     * testInheritedDefault
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * class B implements I {}
     *
     * class C extends B {}
     *
     * TEST: I o = new C(); o.m()I throws IncompatibleClassChangeError
     *                             -mode reflect returns 1
     * TEST: B o = new C(); o.m()I throws NoSuchMethodError
     * TEST: C o = new C(); o.m()I throws NoSuchMethodError
     */
    public void testInheritedDefault() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass B = b.clazz("B").implement(I).build();
        ConcreteClass C = b.clazz("C").extend(B).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(I, C, "m","()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(B, C, "m","()I").throws_(NoSuchMethodError.class).done()
             .test().callSite(C, C, "m","()I").throws_(NoSuchMethodError.class).done()
            .run();
        } else {
            b.test().callSite(I, C, "m","()I").returns(1).done()
             .test().callSite(B, C, "m","()I").throws_(NoSuchMethodError.class).done()
             .test().callSite(C, C, "m","()I").throws_(NoSuchMethodError.class).done()
            .run();
        }

    }

    /*
     * testDefaultVsConcreteInherited
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * class B {
     *  public int m() { return 2; }
     * }
     *
     * class C extends B implements I {}
     *
     */
    public void testDefaultVsConcreteInherited() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        ConcreteClass B = b.clazz("B")
                .concreteMethod("m", "()I").returns(2).build()
                .build();

        ConcreteClass C = b.clazz("C").extend(B).implement(I).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().staticCallSite(I, "m","()I").returns(1).done()
             .test().callSite(I, C, "m","()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(B, C, "m","()I").returns(2).done()
             .test().callSite(C, C, "m","()I").returns(2).done()
            .run();
        } else {
            b.test().staticCallSite(I, "m","()I").returns(1).done()
             .test().callSite(I, C, "m","()I").returns(1).done()
             .test().callSite(B, C, "m","()I").returns(2).done()
             .test().callSite(C, C, "m","()I").returns(2).done()
            .run();
        }

    }

    /*
     * testDefaultVsStaticConflict
     *
     * interface I {
     *  default static public int m() { return 1; }
     * }
     *
     * interface J {
     *  default public int m() { return 2; }
     * }
     *
     * class C implements I, J {}
     *
     * TEST: I o = new C(); o.m()I throws ICCE
     *                             -mode reflect returns 1
     * TEST: J o = new C(); o.m()I == 2
     * TEST: C o = new C(); o.m()I == 2
     */
    public void testDefaultVsStaticConflict() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .static_().public_().returns(1).build()
            .build();

        Interface J = b.intf("J")
                .defaultMethod("m", "()I").returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").implement(I,J).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(I, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(J, C, "m", "()I").returns(2).done()
             .test().callSite(C, C, "m", "()I").returns(2).done()
            .run();
        } else {
            b.test().callSite(I, C, "m", "()I").returns(1).done()
             .test().callSite(J, C, "m", "()I").returns(2).done()
             .test().callSite(C, C, "m", "()I").returns(2).done()
            .run();
        }

    }
    /*
     * testStaticSuperClassVsDefaultSuperInterface
     *
     * interface I {
     *  default public int m() { return 1; }
     * }
     *
     * class A {
     *  public static int m() { return 2; }
     * }
     *
     * class C extends A implements I {}
     *
     * TEST: C o = new C(); o.m()I throws ICCE
     *                             -mode reflect returns 2
     * TEST: I o = new C(); o.m()I == 1
     */
    public void testStaticSuperClassVsDefaultSuperInterface() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .public_().returns(1).build()
            .build();

        ConcreteClass A = b.clazz("A")
                .concreteMethod("m", "()I")
                    .static_().public_().returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").extend(A).implement(I).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(C, C, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(I, C, "m", "()I").returns(1).done()
            .run();
        } else {
            b.test().callSite(C, C, "m", "()I").returns(2).done()
             .test().callSite(I, C, "m", "()I").returns(1).done()
            .run();
        }
    }
    /*
     * testStaticLocalVsDefaultSuperInterface
     *
     * interface I {
     *  default public int m() { return 1; }
     * }
     *
     * class A implements I {
     *  public static int m() { return 2; }
     * }
     *
     * class C extends A implements I {}
     *
     * TEST: A o = new A(); o.m()I throws ICCE
     *                             -mode reflect returns 2
     * TEST: I o = new A(); o.m()I == 1
     */
    public void testStaticLocalVsDefaultSuperInterface() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .public_().returns(1).build()
            .build();

        ConcreteClass A = b.clazz("A").implement(I)
                .concreteMethod("m", "()I")
                    .static_().public_().returns(2).build()
            .build();

        ConcreteClass C = b.clazz("C").extend(A).implement(I).build();

        if (!factory.getExecutionMode().equals("REFLECTION")) {
            b.test().callSite(A, A, "m", "()I").throws_(IncompatibleClassChangeError.class).done()
             .test().callSite(I, A, "m", "()I").returns(1).done()
            .run();
        } else {
            b.test().callSite(A, A, "m", "()I").returns(2).done()
             .test().callSite(I, A, "m", "()I").returns(1).done()
            .run();
        }
    }
    /*
     * testConflictingDefaultsandStaticMethod
     * @bug 8033150
     *
     * interface I {
     *  default public int m() { return 1; }
     * }
     *
     * interface J {
     *  default public int m() { return 2; }
     * }
     *
     * class A implements I, J {
     *  public static int m() { return 3; }
     * }
     *
     * class C extends A {}
     *
     * TEST: C.m(); should call A.m, return value = 3
     */
    public void testConflictingDefaultsandStaticMethod() {
        TestBuilder b = factory.getBuilder();

        Interface I = b.intf("I")
                .defaultMethod("m", "()I")
                    .public_().returns(1).build()
            .build();

        Interface J = b.intf("J")
                .defaultMethod("m", "()I")
                    .public_().returns(2).build()
            .build();

        ConcreteClass A = b.clazz("A").implement(I,J)
                .concreteMethod("m", "()I")
                    .static_().public_().returns(3).build()
            .build();

        ConcreteClass C = b.clazz("C").extend(A).build();

        b.test().staticCallSite(C, "m", "()I").returns(3).done()
         .run();
    }
}
