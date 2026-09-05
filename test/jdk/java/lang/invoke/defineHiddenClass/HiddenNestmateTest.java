/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @build  HiddenNestmateTest
 * @run junit/othervm HiddenNestmateTest
 */

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.*;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.*;
import static java.lang.invoke.MethodHandles.Lookup.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class HiddenNestmateTest {
    private static final ClassDesc CD_HiddenNestmateTest = HiddenNestmateTest.class.describeConstable().orElseThrow();
    private static final byte[] bytes = classBytes("HiddenInjected");

    private static void assertNestmate(Lookup lookup) {
        assertTrue((lookup.lookupModes() & PRIVATE) != 0);
        assertTrue((lookup.lookupModes() & MODULE) != 0);

        Class<?> hiddenClass = lookup.lookupClass();
        Class<?> nestHost = hiddenClass.getNestHost();
        assertTrue(hiddenClass.isHidden());
        assertSame(MethodHandles.lookup().lookupClass(), nestHost);

        // hidden nestmate is not listed in the return array of getNestMembers
        assertTrue(Stream.of(nestHost.getNestMembers()).noneMatch(k -> k == hiddenClass));
        assertTrue(hiddenClass.isNestmateOf(lookup.lookupClass()));
        assertArrayEquals(nestHost.getNestMembers(), hiddenClass.getNestMembers());
    }

    /*
     * Test a hidden class to have no access to private members of another class
     */
    @Test
    public void hiddenClass() throws Throwable {
        // define a hidden class
        Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, false);
        Class<?> c = lookup.lookupClass();
        assertTrue(lookup.hasFullPrivilegeAccess());
        assertEquals(ORIGINAL, lookup.lookupModes() & ORIGINAL);
        assertSame(c, c.getNestHost());  // host of its own nest
        assertTrue(c.isHidden());

        // invoke int test(HiddenNestmateTest o) via MethodHandle
        MethodHandle ctor = lookup.findConstructor(c, MethodType.methodType(void.class));
        MethodHandle mh = lookup.findVirtual(c, "test", MethodType.methodType(int.class, HiddenNestmateTest.class));
        assertThrows(IllegalAccessError.class, () -> {
            int x = (int) mh.bindTo(ctor.invoke()).invokeExact(this);
        });

        // invoke int test(HiddenNestmateTest o)
        assertThrows(IllegalAccessError.class, () -> testInjectedClass(c));
    }

    /*
     * Test a hidden class to have access to private members of its nestmates
     */
    @Test
    public void hiddenNestmate() throws Throwable {
        // define a hidden nestmate class
        Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, false, NESTMATE);
        Class<?> c = lookup.lookupClass();
        assertNestmate(lookup);

        // invoke int test(HiddenNestmateTest o) via MethodHandle
        MethodHandle ctor = lookup.findConstructor(c, MethodType.methodType(void.class));
        MethodHandle mh = lookup.findVirtual(c, "test", MethodType.methodType(int.class, HiddenNestmateTest.class));
        int x = (int)mh.bindTo(ctor.invoke()).invokeExact( this);
        assertEquals(privMethod(), x);

        // invoke int test(HiddenNestmateTest o)
        int x1 = testInjectedClass(c);
        assertEquals(privMethod(), x1);
    }

    /*
     * Test a hidden class created with NESTMATE and STRONG option is a nestmate
     */
    @Test
    public void hiddenStrongClass() throws Throwable {
        // define a hidden class strongly referenced by the class loader
        Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, false, NESTMATE, STRONG);
        assertNestmate(lookup);
    }

    /*
     * Fail to create a hidden class if dropping PRIVATE lookup mode
     */
    @Test
    public void noPrivateLookupAccess() throws Throwable {
        Lookup lookup = MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);
        assertThrows(IllegalAccessException.class, () -> lookup.defineHiddenClass(bytes, false, NESTMATE));
    }

    public void teleportToNestmate() throws Throwable {
        Lookup lookup = MethodHandles.lookup().defineHiddenClass(bytes, false, NESTMATE);
        assertNestmate(lookup);

        // Teleport to a hidden nestmate
        Lookup lc =  MethodHandles.lookup().in(lookup.lookupClass());
        assertNotEquals(0, lc.lookupModes() & PRIVATE);
        assertEquals(0, lc.lookupModes() & ORIGINAL);

        Lookup lc2 = lc.defineHiddenClass(bytes, false, NESTMATE);
        assertNestmate(lc2);
    }

    /*
     * Fail to create a hidden class in a different package from the lookup class' package
     */
    @Test
    public void notSamePackage() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> MethodHandles.lookup().defineHiddenClass(classBytes("p/HiddenInjected"), false, NESTMATE));
    }

    /*
     * invoke int test(HiddenNestmateTest o) method defined in the injected class
     */
    private int testInjectedClass(Class<?> c) throws Throwable {
        try {
            Method m = c.getMethod("test", HiddenNestmateTest.class);
            return (int) m.invoke(c.newInstance(), this);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static byte[] classBytes(String classname) {
        return ClassFile.of().build(ClassDesc.ofInternalName(classname), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withFlags(AccessFlag.FINAL);
            clb.withMethodBody(INIT_NAME, MTD_void, PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody("test", MethodTypeDesc.of(CD_int, CD_HiddenNestmateTest), PUBLIC, cob -> {
                cob.aload(1);
                cob.invokevirtual(CD_HiddenNestmateTest, "privMethod", MethodTypeDesc.of(CD_int));
                cob.ireturn();
            });
        });
    }

    private int privMethod() { return 1234; }
}
