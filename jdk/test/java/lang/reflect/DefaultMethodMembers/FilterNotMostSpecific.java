/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8029674
 * @summary Verify that the right interface methods are returned by
 *          Class.getMethod() and Class.getMethods()
 * @run testng FilterNotMostSpecific
 */

import java.lang.reflect.*;
import java.lang.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class FilterNotMostSpecific {

    @Test(dataProvider="getCases")
    public void testGetMethod(Class<?> iface) {
        boolean match = false;
        MethodDesc[] expectedMethods = iface.getAnnotationsByType(MethodDesc.class);

        for (MethodDesc expected : expectedMethods) {
            if (expected.isGetMethodReturn()) {
                try {
                    Method m = iface.getMethod(expected.name());
                    if (!assertMatch(expected, m))
                        fail(failMsg(expected, m, iface));
                    else
                        match = true;
                } catch (NoSuchMethodException e) {
                    fail("expected: " + toMethodString(expected), e);
                }
            }
        }
        assert(match);
    }

    @Test(dataProvider="getCases")
    public void testGetMethods(Class<?> iface) {
        List<Method> foundMethods = filterObjectMethods(iface.getMethods());
        MethodDesc[] expectedMethods = iface.getAnnotationsByType(MethodDesc.class);
        Set<Method> used = new HashSet<>();

        for (MethodDesc expected : expectedMethods) {
            boolean found = false;

            for (Method m : foundMethods) {
                if (used.contains(m))
                    continue;

                if(expected.name().equals(m.getName()) &&
                    expected.declaringClass() ==m.getDeclaringClass()) {

                    found = true;
                    assertMatch(expected, m);
                    used.add(m);
                    break;
                }
            }
            if (! found)
                fail("On: "+ iface +"\nDid not find " + toMethodString(expected) + " among " + foundMethods);
        }
        assertEquals(foundMethods.size(), expectedMethods.length,
                "\non: " + iface +
                "\nexpected: " + toMethodStrings(expectedMethods) +
                "\nfound: " + foundMethods + "\n");
    }

    private boolean assertMatch(MethodDesc expected, Method m) {
        if (!expected.name().equals(m.getName()))
            return false;
        if (expected.declaringClass() != m.getDeclaringClass())
            return false;

        if (expected.kind() == MethodKind.ABSTRACT)
            assertTrue(Modifier.isAbstract(m.getModifiers()), m + " should be ABSTRACT");
        else if (expected.kind() == MethodKind.CONCRETE)
            assertTrue(!Modifier.isAbstract(m.getModifiers()) && !m.isDefault(), m + " should be CONCRETE");
        else if (expected.kind() == MethodKind.DEFAULT)
            assertTrue(m.isDefault(), m + " should be DEFAULT");

        return true;
    }

    private String failMsg(MethodDesc expected, Method m, Class<?> iface) {
        return "\nOn interface: " + iface +
            "\nexpected: " + toMethodString(expected) +
            "\nfound: " + m;
    }

    private static List<Method> filterObjectMethods(Method[] in) {
        return Arrays.stream(in).
            filter(m -> (m.getDeclaringClass() != java.lang.Object.class)).
            collect(Collectors.toList());
    }

    private String toMethodString(MethodDesc m) {
        return m.declaringClass().getSimpleName().toString() + "." +
            m.name() + "()";
    }

    private List<String> toMethodStrings(MethodDesc[] m) {
        return Arrays.stream(m).
            map(this::toMethodString)
            .collect(Collectors.toList());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Repeatable(MethodDescs.class)
    public @interface MethodDesc {
        String name();
        Class<?> declaringClass();
        MethodKind kind() default MethodKind.ABSTRACT;
        boolean isGetMethodReturn() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface MethodDescs {
        MethodDesc[] value();
    }

    public static enum MethodKind {
        ABSTRACT,
        CONCRETE,
        DEFAULT,
    }
    // base interfaces
    interface I { void nonDefault(); }
    interface J extends I { void nonDefault(); }

    interface Jprim extends I {}
    interface Jbis extends Jprim { void nonDefault(); }

    // interesting cases

    @MethodDesc(name="nonDefault", declaringClass=Jbis.class,
            isGetMethodReturn=true)
    interface P1 extends Jbis {}

    @MethodDesc(name="nonDefault", declaringClass=Jbis.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=I.class)
    interface P2 extends Jbis, Jprim {}

    @MethodDesc(name="nonDefault", declaringClass=Jbis.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=I.class)
    interface P3 extends Jbis, Jprim, I {}

    @MethodDesc(name="nonDefault", declaringClass=I.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=J.class)
    interface P4 extends I, J {}

    @MethodDesc(name="nonDefault", declaringClass=J.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=I.class)
    interface P5 extends J, I {}

    @MethodDesc(name="nonDefault", declaringClass=J.class,
            isGetMethodReturn=true)
    interface K1 extends J {}

    @MethodDesc(name="nonDefault", declaringClass=K1M.class,
            isGetMethodReturn=true)
    interface K1M extends J { void nonDefault(); }

    @MethodDesc(name="nonDefault", declaringClass=I.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=J.class)
    interface K2 extends I, J {}

    @MethodDesc(name="nonDefault", declaringClass=J.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=I.class)
    interface K2O extends J, I {}

    @MethodDesc(name="nonDefault", declaringClass=K2M.class,
            isGetMethodReturn=true)
    interface K2M extends J, I { void nonDefault(); }

    // base interfaces default methods
    interface L { default void isDefault() {} void nonDefault(); }
    interface M extends L { default void isDefault() {} void nonDefault(); }

    // test cases default methods

    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=M.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface N1 extends M {}

    @MethodDesc(name="isDefault", declaringClass=N1D.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    interface N1D extends M { default void isDefault() {}}

    @MethodDesc(name="nonDefault", declaringClass=N1N.class,
            isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=M.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface N1N extends M { void nonDefault(); }

    @MethodDesc(name="isDefault", declaringClass=N1DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N1DN.class,
            isGetMethodReturn=true)
    interface N1DN extends M { default void isDefault() {} void nonDefault(); }

    @MethodDesc(name="isDefault", declaringClass=M.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    interface N2 extends M, L {}

    @MethodDesc(name="isDefault", declaringClass=M.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    interface N22 extends L, M {}

    @MethodDesc(name="isDefault", declaringClass=N2D.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    interface N2D extends M, L { default void isDefault() {}}

    @MethodDesc(name="isDefault", declaringClass=M.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2N.class,
            isGetMethodReturn=true)
    interface N2N extends M, L { void nonDefault(); }

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    interface N2DN extends M, L { default void isDefault() {} void nonDefault(); }

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface O1 extends L, M, N2DN {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface O2 extends M, N2DN, L {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    interface O3 extends N2DN, L, M {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    abstract class C1 implements L, M, N2DN {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    abstract class C2 implements M, N2DN, L {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    abstract class C3 implements N2DN, L, M {}

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=C4.class,
            kind=MethodKind.CONCRETE, isGetMethodReturn=true)
    class C4 implements L, M, N2DN { public void nonDefault() {} }

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=C5.class,
            kind=MethodKind.CONCRETE, isGetMethodReturn=true)
    class C5 implements M, N2DN, L { public void nonDefault() {} }

    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=C6.class,
            kind=MethodKind.CONCRETE, isGetMethodReturn=true)
    class C6 implements N2DN, L, M { public void nonDefault() {} }

    // reabstraction

    @MethodDesc(name="isDefault", declaringClass=R1.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R1 extends L, M, N2DN { void isDefault(); }

    @MethodDesc(name="isDefault", declaringClass=R2.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R2 extends M, N2DN, L { void isDefault(); }

    @MethodDesc(name="isDefault", declaringClass=R3.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    interface R3 extends N2DN, L, M { void isDefault(); }

    // this one is strange but logical, getMethod finds N2DN first, which is
    // default but not the most specific
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=R1.class)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R4 extends L, M, N2DN, R1 {}

    // this one is strange but logical, getMethod finds N2DN first, which is
    // default but not the most specific
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=R2.class)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R5 extends M, N2DN, R2, L {}

    // this one is strange but logical, getMethod finds N2DN first, which is
    // default but not the most specific
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=R3.class)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    interface R6 extends N2DN, R3, L, M {}

    // the following three finds the "right" one
    @MethodDesc(name="isDefault", declaringClass=R1.class,
            isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="nonDefault", declaringClass=L.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R7 extends L, M, R1, N2DN {}

    @MethodDesc(name="isDefault", declaringClass=R2.class,
            isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class)
    interface R8 extends M, R2, N2DN, L {}

    @MethodDesc(name="isDefault", declaringClass=R3.class,
            isGetMethodReturn=true)
    @MethodDesc(name="isDefault", declaringClass=N2DN.class,
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="nonDefault", declaringClass=L.class)
    @MethodDesc(name="nonDefault", declaringClass=M.class)
    @MethodDesc(name="nonDefault", declaringClass=N2DN.class,
            isGetMethodReturn=true)
    interface R9 extends R3, N2DN, L, M {}

    // More reabstraction
    interface Z1 { void z(); }
    interface Z2 extends Z1 { default void z() {} }

    @MethodDesc(name="z", declaringClass=Z2.class,
            isGetMethodReturn=true, kind=MethodKind.DEFAULT)
    interface Z31 extends Z1, Z2 {}

    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface Z32 extends Z2, Z1 {}

    interface Z3 extends Z2, Z1 { void z(); }

    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z3.class)
    interface Z41 extends Z1, Z2, Z3 { }

    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z3.class)
    interface Z42 extends Z2, Z3, Z1 { }

    @MethodDesc(name="z", declaringClass=Z3.class,
            isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT)
    interface Z43 extends Z3, Z1, Z2 { }

    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z3.class)
    abstract class ZC41 implements Z1, Z2, Z3 { }

    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z3.class)
    abstract class ZC42 implements Z2, Z3, Z1 { }

    @MethodDesc(name="z", declaringClass=Z3.class,
            isGetMethodReturn=true)
    @MethodDesc(name="z", declaringClass=Z2.class,
            kind=MethodKind.DEFAULT)
    abstract class ZC43 implements Z3, Z1, Z2 { }

    // More reabstraction + concretization
    interface X1 { default void x() {} }
    interface X2 extends X1 { void x(); }

    @MethodDesc(name="x", declaringClass=X1.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    @MethodDesc(name="x", declaringClass=X2.class)
    interface X31 extends X1, X2 {}

    @MethodDesc(name="x", declaringClass=X2.class,
            isGetMethodReturn=true)
    @MethodDesc(name="x", declaringClass=X1.class,
            kind=MethodKind.DEFAULT)
    interface X32 extends X2, X1 {}

    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface X3 extends X2, X1 { default void x() {} }

    // order shouldn't matter here
    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface X41 extends X1, X2, X3 { }

    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface X42 extends X2, X3, X1 { }

    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    interface X43 extends X3, X1, X2 { }

    // order shouldn't matter here
    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    abstract class XC41 implements X1, X2, X3 { }

    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    abstract class XC42 implements X2, X3, X1 { }

    @MethodDesc(name="x", declaringClass=X3.class,
            kind=MethodKind.DEFAULT, isGetMethodReturn=true)
    abstract class XC43 implements X3, X1, X2 { }

    interface K extends I, J { void nonDefault(); }

    @MethodDesc(name="nonDefault", declaringClass=I.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=J.class)
    @MethodDesc(name="nonDefault", declaringClass=K.class)
    abstract class ZZ1 implements I, J, K {}

    @MethodDesc(name="nonDefault", declaringClass=I.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=J.class)
    @MethodDesc(name="nonDefault", declaringClass=K.class)
    abstract class ZZ2 extends ZZ1 implements K, I, J {}

    @MethodDesc(name="nonDefault", declaringClass=I.class,
            isGetMethodReturn=true)
    @MethodDesc(name="nonDefault", declaringClass=J.class)
    @MethodDesc(name="nonDefault", declaringClass=K.class)
    abstract class ZZ3 extends ZZ2 implements J, K, I {}

    // bridges
    interface B1A { Object m(); }
    interface B1B extends B1A { Map m(); }

    @MethodDesc(name="m", declaringClass=B1C.class,
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=B1C.class,
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="m", declaringClass=B1C.class,
            kind=MethodKind.DEFAULT)
    interface B1C extends B1B { HashMap m(); }

    @MethodDesc(name="m", declaringClass=B2.class,
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=B2.class,
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="m", declaringClass=B2.class,
            kind=MethodKind.DEFAULT)
    interface B2 extends B1C { HashMap m(); }

    @MethodDesc(name="m", declaringClass=B2.class, //HahsMap
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=B2.class, //Map
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="m", declaringClass=B2.class, //Object
            kind=MethodKind.DEFAULT)
    interface B3A extends B2, B1A {}

    // this one is funny since HashMap isn't a bridge thus not a default
    @MethodDesc(name="m", declaringClass=B2.class, //HashMap
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=B2.class, //Map
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="m", declaringClass=B2.class, //Object
            kind=MethodKind.DEFAULT)
    @MethodDesc(name="m", declaringClass=B1C.class) //HashMap
    interface B3B extends B2, B1C {}

    // same name different params type
    interface A1 { void m(); void m(int i); void m(int i, int j); }
    interface A2A extends A1 { void m(); void m(int i); void m(int i, int j); }
    interface A2B extends A1 { void m(); void m(int i); default void m(int i, int j) {} }

    @MethodDesc(name="m", declaringClass=A1.class,
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=A1.class)
    @MethodDesc(name="m", declaringClass=A1.class)
    @MethodDesc(name="m", declaringClass=A2A.class)
    @MethodDesc(name="m", declaringClass=A2A.class)
    @MethodDesc(name="m", declaringClass=A2A.class)
    interface A3A extends A1, A2A {}

    @MethodDesc(name="m", declaringClass=A1.class,
            isGetMethodReturn=true)
    @MethodDesc(name="m", declaringClass=A1.class)
    @MethodDesc(name="m", declaringClass=A2B.class)
    @MethodDesc(name="m", declaringClass=A2B.class)
    @MethodDesc(name="m", declaringClass=A2B.class,
            kind=MethodKind.DEFAULT)
    interface A3B extends A1, A2B {}

    @DataProvider
    public Object[][] getCases() { return CASES; }
    public static final Class<?>[][] CASES =  {
        { K1.class },
        { K1M.class },
        { K2.class },
        { K2O.class },
        { K2M.class },

        { N1.class },
        { N1D.class },
        { N1N.class },
        { N1DN.class },

        { N2.class },
        { N22.class },
        { N2D.class },
        { N2N.class },
        { N2DN.class },

        { P1.class },
        { P2.class },
        { P3.class },
        { P4.class },
        { P5.class },

        { O1.class },
        { O2.class },
        { O3.class },

        { C1.class },
        { C2.class },
        { C3.class },

        { C4.class },
        { C5.class },
        { C6.class },

        { R1.class },
        { R2.class },
        { R3.class },

        { R4.class },
        { R5.class },
        { R6.class },

        { R7.class },
        { R8.class },
        { R9.class },

        { Z31.class },
        { Z32.class },

        { Z41.class },
        { Z42.class },
        { Z43.class },

        { ZC41.class },
        { ZC42.class },
        { ZC43.class },

        { ZZ1.class },
        { ZZ2.class },
        { ZZ3.class },

        { X3.class },
        { X31.class },
        { X32.class },

        { X41.class },
        { X42.class },
        { X43.class },

        { XC41.class },
        { XC42.class },
        { XC43.class },

        { B1C.class },
        { B2.class },
        { B3A.class },
        { B3B.class },

        { A3A.class },
        { A3B.class },
    };
}
