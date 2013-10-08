/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

package vm;

import java.lang.reflect.*;
import java.util.*;
import java.io.File;
import java.io.IOException;

import org.testng.annotations.Test;
import separate.*;
import separate.Compiler;

import static org.testng.Assert.*;
import static separate.SourceModel.*;
import static separate.SourceModel.Class;

@Test(groups = "vm")
public class DefaultMethodsTest extends TestHarness {
    public DefaultMethodsTest() {
        super(false, false);
    }

    /**
     * class C { public int m() { return 22; } }
     *
     * TEST: C c = new C(); c.m() == 22
     */
    public void testHarnessInvokeVirtual() {
        Class C = new Class("C", ConcreteMethod.std("22"));
        assertInvokeVirtualEquals(22, C);
    }

    /**
     * interface I { int m(); }
     * class C implements I { public int m() { return 33; } }
     *
     * TEST: I i = new C(); i.m() == 33;
     */
    public void testHarnessInvokeInterface() {
        Interface I = new Interface("I", AbstractMethod.std());
        Class C = new Class("C", I, ConcreteMethod.std("33"));
        assertInvokeInterfaceEquals(33, C, I);
    }

    /**
     * class C {}
     *
     * TEST: C c = new C(); c.m() throws NoSuchMethod
     */
    public void testHarnessThrows() {
        Class C = new Class("C");
        assertThrows(NoSuchMethodError.class, C);
    }

    /**
     * interface I { int m() default { return 44; } }
     * class C implements I {}
     *
     * TEST: C c = new C(); c.m() == 44;
     * TEST: I i = new C(); i.m() == 44;
     */
    public void testBasicDefault() {
        Interface I = new Interface("I", DefaultMethod.std("44"));
        Class C = new Class("C", I);

        assertInvokeVirtualEquals(44, C);
        assertInvokeInterfaceEquals(44, C, I);
    }

    /**
     * interface I { default int m() { return 44; } }
     * interface J extends I {}
     * interface K extends J {}
     * class C implements K {}
     *
     * TEST: C c = new C(); c.m() == 44;
     * TEST: I i = new C(); i.m() == 44;
     */
    public void testFarDefault() {
        Interface I = new Interface("I", DefaultMethod.std("44"));
        Interface J = new Interface("J", I);
        Interface K = new Interface("K", J);
        Class C = new Class("C", K);

        assertInvokeVirtualEquals(44, C);
        assertInvokeInterfaceEquals(44, C, K);
    }

    /**
     * interface I { int m(); }
     * interface J extends I { default int m() { return 44; } }
     * interface K extends J {}
     * class C implements K {}
     *
     * TEST: C c = new C(); c.m() == 44;
     * TEST: K k = new C(); k.m() == 44;
     */
    public void testOverrideAbstract() {
        Interface I = new Interface("I", AbstractMethod.std());
        Interface J = new Interface("J", I, DefaultMethod.std("44"));
        Interface K = new Interface("K", J);
        Class C = new Class("C", K);

        assertInvokeVirtualEquals(44, C);
        assertInvokeInterfaceEquals(44, C, K);
    }

    /**
     * interface I { int m() default { return 44; } }
     * class C implements I { public int m() { return 55; } }
     *
     * TEST: C c = new C(); c.m() == 55;
     * TEST: I i = new C(); i.m() == 55;
     */
    public void testExisting() {
        Interface I = new Interface("I", DefaultMethod.std("44"));
        Class C = new Class("C", I, ConcreteMethod.std("55"));

        assertInvokeVirtualEquals(55, C);
        assertInvokeInterfaceEquals(55, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * class B implements I {}
     * class C extends B {}
     *
     * TEST: C c = new C(); c.m() == 99;
     * TEST: I i = new C(); i.m() == 99;
     */
    public void testInherited() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Class B = new Class("B", I);
        Class C = new Class("C", B);

        assertInvokeVirtualEquals(99, C);
        assertInvokeInterfaceEquals(99, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * class C { public int m() { return 11; } }
     * class D extends C implements I {}
     *
     * TEST: D d = new D(); d.m() == 11;
     * TEST: I i = new D(); i.m() == 11;
     */
    public void testExistingInherited() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Class C = new Class("C", ConcreteMethod.std("11"));
        Class D = new Class("D", C, I);

        assertInvokeVirtualEquals(11, D);
        assertInvokeInterfaceEquals(11, D, I);
    }

    /**
     * interface I { default int m() { return 44; } }
     * class C implements I { public int m() { return 11; } }
     * class D extends C { public int m() { return 22; } }
     *
     * TEST: D d = new D(); d.m() == 22;
     * TEST: I i = new D(); i.m() == 22;
     */
    void testExistingInheritedOverride() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Class C = new Class("C", I, ConcreteMethod.std("11"));
        Class D = new Class("D", C, ConcreteMethod.std("22"));

        assertInvokeVirtualEquals(22, D);
        assertInvokeInterfaceEquals(22, D, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * interface J { defaultint m() { return 88; } }
     * class C implements I { public int m() { return 11; } }
     * class D extends C { public int m() { return 22; } }
     * class E extends D implements J {}
     *
     * TEST: E e = new E(); e.m() == 22;
     * TEST: J j = new E(); j.m() == 22;
     */
    public void testExistingInheritedPlusDefault() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", DefaultMethod.std("88"));
        Class C = new Class("C", I, ConcreteMethod.std("11"));
        Class D = new Class("D", C, ConcreteMethod.std("22"));
        Class E = new Class("E", D, J);

        assertInvokeVirtualEquals(22, E);
        assertInvokeInterfaceEquals(22, E, J);
    }

    /**
     * interface I { default int m() { return 99; } }
     * class B implements I {}
     * class C extends B { public int m() { return 77; } }
     *
     * TEST: C c = new C(); c.m() == 77;
     * TEST: I i = new C(); i.m() == 77;
     */
    public void testInheritedWithConcrete() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Class B = new Class("B", I);
        Class C = new Class("C", B, ConcreteMethod.std("77"));

        assertInvokeVirtualEquals(77, C);
        assertInvokeInterfaceEquals(77, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * class B implements I {}
     * class C extends B implements I { public int m() { return 66; } }
     *
     * TEST: C c = new C(); c.m() == 66;
     * TEST: I i = new C(); i.m() == 66;
     */
    public void testInheritedWithConcreteAndImpl() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Class B = new Class("B", I);
        Class C = new Class("C", B, I, ConcreteMethod.std("66"));

        assertInvokeVirtualEquals(66, C);
        assertInvokeInterfaceEquals(66, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * interface J { default int m() { return 88; } }
     * class C implements I, J {}
     *
     * TEST: C c = new C(); c.m() throws AME
     */
    public void testConflict() {
        // debugTest();
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", DefaultMethod.std("88"));
        Class C = new Class("C", I, J);

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface I { int m(); }
     * interface J { default int m() { return 88; } }
     * class C implements I, J {}
     *
     * TEST: C c = new C(); c.m() throws AME
     */
    public void testAmbiguousReabstract() {
        Interface I = new Interface("I", AbstractMethod.std());
        Interface J = new Interface("J", DefaultMethod.std("88"));
        Class C = new Class("C", I, J);

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface I { default int m() { return 99; } }
     * interface J extends I { }
     * interface K extends I { }
     * class C implements J, K {}
     *
     * TEST: C c = new C(); c.m() == 99
     * TEST: J j = new C(); j.m() == 99
     * TEST: K k = new C(); k.m() == 99
     * TEST: I i = new C(); i.m() == 99
     */
    public void testDiamond() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", I);
        Interface K = new Interface("K", I);
        Class C = new Class("C", J, K);

        assertInvokeVirtualEquals(99, C);
        assertInvokeInterfaceEquals(99, C, J);
        assertInvokeInterfaceEquals(99, C, K);
        assertInvokeInterfaceEquals(99, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * interface J extends I { }
     * interface K extends I { }
     * interface L extends I { }
     * interface M extends I { }
     * class C implements I, J, K, L, M {}
     *
     * TEST: C c = new C(); c.m() == 99
     * TEST: J j = new C(); j.m() == 99
     * TEST: K k = new C(); k.m() == 99
     * TEST: I i = new C(); i.m() == 99
     * TEST: L l = new C(); l.m() == 99
     * TEST: M m = new C(); m.m() == 99
     */
    public void testExpandedDiamond() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", I);
        Interface K = new Interface("K", I);
        Interface L = new Interface("L", I);
        Interface M = new Interface("M", L);
        Class C = new Class("C", I, J, K, L, M);

        assertInvokeVirtualEquals(99, C);
        assertInvokeInterfaceEquals(99, C, J);
        assertInvokeInterfaceEquals(99, C, K);
        assertInvokeInterfaceEquals(99, C, I);
        assertInvokeInterfaceEquals(99, C, L);
        assertInvokeInterfaceEquals(99, C, M);
    }

    /**
     * interface I { int m() default { return 99; } }
     * interface J extends I { int m(); }
     * class C implements J {}
     *
     * TEST: C c = new C(); c.m() throws AME
     */
    public void testReabstract() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", I, AbstractMethod.std());
        Class C = new Class("C", J);

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface I { default int m() { return 88; } }
     * interface J extends I { default int m() { return 99; } }
     * class C implements J {}
     *
     * TEST: C c = new C(); c.m() == 99;
     * TEST: J j = new C(); j.m() == 99;
     * TEST: I i = new C(); i.m() == 99;
     */
    public void testShadow() {
        Interface I = new Interface("I", DefaultMethod.std("88"));
        Interface J = new Interface("J", I, DefaultMethod.std("99"));
        Class C = new Class("C", J);

        assertInvokeVirtualEquals(99, C);
        assertInvokeInterfaceEquals(99, C, J);
        assertInvokeInterfaceEquals(99, C, I);
    }

    /**
     * interface I { default int m() { return 88; } }
     * interface J extends I { default int m() { return 99; } }
     * class C implements I, J {}
     *
     * TEST: C c = new C(); c.m() == 99;
     * TEST: J j = new C(); j.m() == 99;
     * TEST: I i = new C(); i.m() == 99;
     */
    public void testDisqualified() {
        Interface I = new Interface("I", DefaultMethod.std("88"));
        Interface J = new Interface("J", I, DefaultMethod.std("99"));
        Class C = new Class("C", I, J);

        assertInvokeVirtualEquals(99, C);
        assertInvokeInterfaceEquals(99, C, J);
        assertInvokeInterfaceEquals(99, C, I);
    }

    /**
     * interface I { default int m() { return 99; } }
     * class C implements I {}
     *
     * TEST: C.class.getMethod("m").invoke(new C()) == 99
     */
    public void testReflectCall() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        //workaround accessibility issue when loading C with DirectedClassLoader
        I.addAccessFlag(AccessFlag.PUBLIC);
        Class C = new Class("C", I);

        Compiler.Flags[] flags = this.verbose ?
            new Compiler.Flags[] { Compiler.Flags.VERBOSE } :
            new Compiler.Flags[] {};
        Compiler compiler = new Compiler(flags);
        java.lang.Class<?> cls = null;
        try {
            cls = compiler.compileAndLoad(C);
        } catch (ClassNotFoundException e) {
            fail("Could not load class");
        }

        java.lang.reflect.Method method = null;
        try {
            method = cls.getMethod(stdMethodName);
        } catch (NoSuchMethodException e) {
            fail("Could not find method in class");
        }
        assertNotNull(method);

        Object c = null;
        try {
            c = cls.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            fail("Could not create instance of class");
        }
        assertNotNull(c);

        Integer res = null;
        try {
            res = (Integer)method.invoke(c);
        } catch (IllegalAccessException |
                 java.lang.reflect.InvocationTargetException e) {
            fail("Could not invoke default instance method");
        }
        assertNotNull(res);

        assertEquals(res.intValue(), 99);

        compiler.cleanup();
    }


    /**
     * interface J { default int m() { return 88; } }
     * interface I extends J { default int m() { return J.super.m(); } }
     * class C implements I {}
     *
     * TEST: C c = new C(); c.m() == 88;
     * TEST: I i = new C(); i.m() == 88;
     */
    public void testSuperBasic() {
        // debugTest();

        Interface J = new Interface("J", DefaultMethod.std("88"));
        Interface I = new Interface("I", J, new DefaultMethod(
            "int", stdMethodName, "return J.super.m();"));
        I.addCompilationDependency(J.findMethod(stdMethodName));
        Class C = new Class("C", I);

        assertInvokeVirtualEquals(88, C);
        assertInvokeInterfaceEquals(88, C, I);
    }

    /**
     * interface K { int m() default { return 99; } }
     * interface L { int m() default { return 101; } }
     * interface J extends K, L {}
     * interface I extends J, K { int m() default { J.super.m(); } }
     * class C implements I {}
     *
     * TEST: C c = new C(); c.m() throws AME
     * TODO: add case for K k = new C(); k.m() throws AME
     */
    public void testSuperConflict() {
        // debugTest();

        Interface K = new Interface("K", DefaultMethod.std("99"));
        Interface L = new Interface("L", DefaultMethod.std("101"));
        Interface J = new Interface("J", K, L);
        Interface I = new Interface("I", J, K, new DefaultMethod(
            "int", stdMethodName, "return J.super.m();"));
        Interface Jstub = new Interface("J", DefaultMethod.std("-1"));
        I.addCompilationDependency(Jstub);
        I.addCompilationDependency(Jstub.findMethod(stdMethodName));
        Class C = new Class("C", I);

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface I { default int m() { return 99; } }
     * interface J extends I { default int m() { return 55; } }
     * class C implements I, J { public int m() { return I.super.m(); } }
     *
     * TEST: C c = new C(); c.m() throws AME
     * TODO: add case for J j = new C(); j.m() throws AME
     */
    public void testSuperDisqual() {
        Interface I = new Interface("I", DefaultMethod.std("99"));
        Interface J = new Interface("J", I, DefaultMethod.std("55"));
        Class C = new Class("C", I, J,
            new ConcreteMethod("int", stdMethodName, "return I.super.m();",
                AccessFlag.PUBLIC));
        C.addCompilationDependency(I.findMethod(stdMethodName));

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface J { int m(); }
     * interface I extends J { default int m() { return J.super.m(); } }
     * class C implements I {}
     *
     * TEST: C c = new C(); c.m() throws AME
     * TODO: add case for I i = new C(); i.m() throws AME
     */
    public void testSuperNull() {
        Interface J = new Interface("J", AbstractMethod.std());
        Interface I = new Interface("I", J, new DefaultMethod(
            "int", stdMethodName, "return J.super.m();"));
        Interface Jstub = new Interface("J", DefaultMethod.std("99"));
        I.addCompilationDependency(Jstub);
        I.addCompilationDependency(Jstub.findMethod(stdMethodName));
        Class C = new Class("C", I);

        assertThrows(AbstractMethodError.class, C);
    }

    /**
     * interface J<T> { default int m(T t) { return 88; } }
     * interface I extends J<String> {
     *     int m(String s) default { return J.super.m(); }
     * }
     * class C implements I {}
     *
     * TEST: I i = new C(); i.m("") == 88;
     */
    public void testSuperGeneric() {
        Interface J = new Interface("J", new TypeParameter("T"),
            new DefaultMethod("int", stdMethodName, "return 88;",
                new MethodParameter("T", "t")));
        Interface I = new Interface("I", J.with("String"),
            new DefaultMethod("int", stdMethodName, "return J.super.m(s);",
                new MethodParameter("String", "s")));
        I.addCompilationDependency(J.findMethod(stdMethodName));
        Class C = new Class("C", I);

        AbstractMethod pm = new AbstractMethod("int", stdMethodName,
            new MethodParameter("String", "s"));

        assertInvokeInterfaceEquals(
            new Integer(88), C, new Extends(I), pm, "\"\"");
    }

    /**
     * interface I<T> { int m(T t) default { return 44; } }
     * interface J extends I<String> { int m(String s) default { return 55; } }
     * class C implements I<String>, J {
     *     public int m(String s) { return I.super.m(s); }
     * }
     *
     * TEST: C c = new C(); c.m("string") throws AME
     */
    public void testSuperGenericDisqual() {
        MethodParameter t = new MethodParameter("T", "t");
        MethodParameter s = new MethodParameter("String", "s");

        Interface I = new Interface("I", new TypeParameter("T"),
            new DefaultMethod("int", stdMethodName, "return 44;", t));
        Interface J = new Interface("J", I.with("String"),
            new DefaultMethod("int", stdMethodName, "return 55;", s));
        Class C = new Class("C", I.with("String"), J,
            new ConcreteMethod("int", stdMethodName,
                "return I.super.m(s);", AccessFlag.PUBLIC, s));
        C.addCompilationDependency(I.findMethod(stdMethodName));

        assertThrows(AbstractMethodError.class, C,
            new ConcreteMethod(
                "int", stdMethodName, "return -1;", AccessFlag.PUBLIC, s),
            "-1", "\"string\"");
    }

    /**
     * interface I { default Integer m() { return new Integer(88); } }
     * class C { int m() { return 99; } }
     * class D extends C implements I {}
     * class S { Object foo() { return (new D()).m(); } // link sig: ()LInteger;
     * TEST: S s = new S(); s.foo() == new Integer(88)
     */
    public void testNoCovarNoBridge() {
        Interface I = new Interface("I", new DefaultMethod(
            "Integer", "m", "return new Integer(88);"));
        Class C = new Class("C", new ConcreteMethod(
            "int", "m", "return 99;", AccessFlag.PUBLIC));
        Class D = new Class("D", I, C);

        ConcreteMethod DstubMethod = new ConcreteMethod(
            "Integer", "m", "return null;", AccessFlag.PUBLIC);
        Class Dstub = new Class("D", DstubMethod);

        ConcreteMethod toCall = new ConcreteMethod(
            "Object", "foo", "return (new D()).m();", AccessFlag.PUBLIC);
        Class S = new Class("S", D, toCall);
        S.addCompilationDependency(Dstub);
        S.addCompilationDependency(DstubMethod);

        assertInvokeVirtualEquals(new Integer(88), S, toCall, "null");
    }

    /**
     * interface J { int m(); }
     * interface I extends J { default int m() { return 99; } }
     * class B implements J {}
     * class C extends B implements I {}
     * TEST: C c = new C(); c.m() == 99
     *
     * The point of this test is that B does not get default method analysis,
     * and C does not generate any new miranda methods in the vtable.
     * It verifies that default method analysis occurs when mirandas have been
     * inherited and the supertypes don't have any overpass methods.
     */
    public void testNoNewMiranda() {
        Interface J = new Interface("J", AbstractMethod.std());
        Interface I = new Interface("I", J, DefaultMethod.std("99"));
        Class B = new Class("B", J);
        Class C = new Class("C", B, I);
        assertInvokeVirtualEquals(99, C);
    }

    public void testStrictfpDefault() {
        try {
            java.lang.Class.forName("vm.StrictfpDefault");
        } catch (Exception e) {
            fail("Could not load class", e);
        }
    }
}
