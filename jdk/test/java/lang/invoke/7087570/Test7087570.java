/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/* @test
 * @bug 7087570
 * @summary REF_invokeSpecial DMHs (which are unusual) get marked explicitly; tweak the MHI to use this bit
 *
 * @run main Test7087570
 */

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodType.*;

public class Test7087570 {
    // XXX may remove the following constant declarations when MethodHandleInfo is made public
    private static final int
            REF_getField                = 1,
            REF_getStatic               = 2,
            REF_putField                = 3,
            REF_putStatic               = 4,
            REF_invokeVirtual           = 5,
            REF_invokeStatic            = 6,
            REF_invokeSpecial           = 7,
            REF_newInvokeSpecial        = 8,
            REF_invokeInterface         = 9,
            REF_LIMIT                  = 10;

    private static final TestMethodData[] TESTS = new TestMethodData[] {
        // field accessors
        data(DummyFieldHolder.class, "instanceField", getterMethodType(String.class), DummyFieldHolder.class, REF_getField),
        data(DummyFieldHolder.class, "instanceField", setterMethodType(String.class), DummyFieldHolder.class, REF_putField),
        data(DummyFieldHolder.class, "staticField", getterMethodType(Integer.class), DummyFieldHolder.class, REF_getStatic),
        data(DummyFieldHolder.class, "staticField", setterMethodType(Integer.class), DummyFieldHolder.class, REF_putStatic),
        data(DummyFieldHolder.class, "instanceByteField", getterMethodType(byte.class), DummyFieldHolder.class, REF_getField),
        data(DummyFieldHolder.class, "instanceByteField", setterMethodType(byte.class), DummyFieldHolder.class, REF_putField),

        // REF_invokeVirtual
        data(Object.class, "hashCode", methodType(int.class), Object.class, REF_invokeVirtual),

        // REF_invokeVirtual strength-reduced to REF_invokeSpecial,
        // test if it normalizes back to REF_invokeVirtual in MethodHandleInfo as expected
        data(String.class, "hashCode", methodType(int.class), String.class, REF_invokeVirtual),

        // REF_invokeStatic
        data(Collections.class, "sort", methodType(void.class, List.class), Collections.class, REF_invokeStatic),
        data(Arrays.class, "asList", methodType(List.class, Object[].class), Arrays.class, REF_invokeStatic), // varargs case

        // REF_invokeSpecial
        data(Object.class, "hashCode", methodType(int.class), Object.class, REF_invokeSpecial),

        // REF_newInvokeSpecial
        data(String.class, "<init>", methodType(void.class, char[].class), String.class, REF_newInvokeSpecial),
        data(DummyFieldHolder.class, "<init>", methodType(void.class, byte.class, Long[].class), DummyFieldHolder.class, REF_newInvokeSpecial), // varargs case

        // REF_invokeInterface
        data(List.class, "size", methodType(int.class), List.class, REF_invokeInterface)
    };

    public static void main(String... args) throws Throwable {
        testWithLookup();
        testWithUnreflect();
    }

    private static void doTest(MethodHandle mh, TestMethodData testMethod) {
        Object mhi = newMethodHandleInfo(mh);

        System.out.printf("%s.%s: %s, nominal refKind: %s, actual refKind: %s\n",
                          testMethod.clazz.getName(), testMethod.name, testMethod.methodType,
                          REF_KIND_NAMES[testMethod.referenceKind],
                          REF_KIND_NAMES[getReferenceKind(mhi)]);
        assertEquals(testMethod.name,           getName(mhi));
        assertEquals(testMethod.methodType,     getMethodType(mhi));
        assertEquals(testMethod.declaringClass, getDeclaringClass(mhi));
        assertEquals(testMethod.referenceKind == REF_invokeSpecial, isInvokeSpecial(mh));
        assertRefKindEquals(testMethod.referenceKind,  getReferenceKind(mhi));
    }

    private static void testWithLookup() throws Throwable {
        for (TestMethodData testMethod : TESTS) {
            MethodHandle mh = lookupFrom(testMethod);
            doTest(mh, testMethod);
        }
    }

    private static void testWithUnreflect() throws Throwable {
        for (TestMethodData testMethod : TESTS) {
            MethodHandle mh = unreflectFrom(testMethod);
            doTest(mh, testMethod);
        }
    }

    private static MethodType getterMethodType(Class<?> clazz) {
        return methodType(clazz);
    }

    private static MethodType setterMethodType(Class<?> clazz) {
        return methodType(void.class, clazz);
    }

    private static final String[] REF_KIND_NAMES = {
        "MH::invokeBasic",
        "REF_getField", "REF_getStatic", "REF_putField", "REF_putStatic",
        "REF_invokeVirtual", "REF_invokeStatic", "REF_invokeSpecial",
        "REF_newInvokeSpecial", "REF_invokeInterface"
    };

    private static final Lookup LOOKUP = lookup();

    // XXX may remove the following reflective logic when MethodHandleInfo is made public
    private static final MethodHandle MH_IS_INVOKESPECIAL;
    private static final MethodHandle MHI_CONSTRUCTOR;
    private static final MethodHandle MHI_GET_NAME;
    private static final MethodHandle MHI_GET_METHOD_TYPE;
    private static final MethodHandle MHI_GET_DECLARING_CLASS;
    private static final MethodHandle MHI_GET_REFERENCE_KIND;

    static {
        try {
            // This is white box testing.  Use reflection to grab private implementation bits.
            String magicName = "IMPL_LOOKUP";
            Field magicLookup = MethodHandles.Lookup.class.getDeclaredField(magicName);
            // This unit test will fail if a security manager is installed.
            magicLookup.setAccessible(true);
            // Forbidden fruit...
            Lookup directInvokeLookup = (Lookup) magicLookup.get(null);
            Class<?> mhiClass = Class.forName("java.lang.invoke.MethodHandleInfo", false, MethodHandle.class.getClassLoader());
            MH_IS_INVOKESPECIAL = directInvokeLookup
                    .findVirtual(MethodHandle.class, "isInvokeSpecial", methodType(boolean.class));
            MHI_CONSTRUCTOR = directInvokeLookup
                    .findConstructor(mhiClass, methodType(void.class, MethodHandle.class));
            MHI_GET_NAME = directInvokeLookup
                    .findVirtual(mhiClass, "getName", methodType(String.class));
            MHI_GET_METHOD_TYPE = directInvokeLookup
                    .findVirtual(mhiClass, "getMethodType", methodType(MethodType.class));
            MHI_GET_DECLARING_CLASS = directInvokeLookup
                    .findVirtual(mhiClass, "getDeclaringClass", methodType(Class.class));
            MHI_GET_REFERENCE_KIND = directInvokeLookup
                    .findVirtual(mhiClass, "getReferenceKind", methodType(int.class));
        } catch (ReflectiveOperationException ex) {
            throw new Error(ex);
        }
    }

    private static class TestMethodData {
        final Class<?> clazz;
        final String name;
        final MethodType methodType;
        final Class<?> declaringClass;
        final int referenceKind; // the nominal refKind

        public TestMethodData(Class<?> clazz, String name,
                        MethodType methodType, Class<?> declaringClass,
                        int referenceKind) {
            this.clazz = clazz;
            this.name = name;
            this.methodType = methodType;
            this.declaringClass = declaringClass;
            this.referenceKind = referenceKind;
        }
    }

    private static TestMethodData data(Class<?> clazz, String name,
                                       MethodType methodType, Class<?> declaringClass,
                                       int referenceKind) {
        return new TestMethodData(clazz, name, methodType, declaringClass, referenceKind);
    }

    private static MethodHandle lookupFrom(TestMethodData testMethod)
            throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        switch (testMethod.referenceKind) {
        case REF_getField:
            return LOOKUP.findGetter(testMethod.clazz, testMethod.name, testMethod.methodType.returnType());
        case REF_putField:
            return LOOKUP.findSetter(testMethod.clazz, testMethod.name, testMethod.methodType.parameterType(0));
        case REF_getStatic:
            return LOOKUP.findStaticGetter(testMethod.clazz, testMethod.name, testMethod.methodType.returnType());
        case REF_putStatic:
            return LOOKUP.findStaticSetter(testMethod.clazz, testMethod.name, testMethod.methodType.parameterType(0));
        case REF_invokeVirtual:
        case REF_invokeInterface:
            return LOOKUP.findVirtual(testMethod.clazz, testMethod.name, testMethod.methodType);
        case REF_invokeStatic:
            return LOOKUP.findStatic(testMethod.clazz, testMethod.name, testMethod.methodType);
        case REF_invokeSpecial:
            Class<?> thisClass = LOOKUP.lookupClass();
            return LOOKUP.findSpecial(testMethod.clazz, testMethod.name, testMethod.methodType, thisClass);
        case REF_newInvokeSpecial:
            return LOOKUP.findConstructor(testMethod.clazz, testMethod.methodType);
        default:
            throw new Error("ERROR: unexpected referenceKind in test data");
        }
    }

    private static MethodHandle unreflectFrom(TestMethodData testMethod)
            throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException {
        switch (testMethod.referenceKind) {
        case REF_getField:
        case REF_getStatic: {
                Field f = testMethod.clazz.getDeclaredField(testMethod.name);
                return LOOKUP.unreflectGetter(f);
            }
        case REF_putField:
        case REF_putStatic: {
                Field f = testMethod.clazz.getDeclaredField(testMethod.name);
                return LOOKUP.unreflectSetter(f);
            }
        case REF_invokeVirtual:
        case REF_invokeStatic:
        case REF_invokeInterface: {
                Method m = testMethod.clazz.getDeclaredMethod(testMethod.name, testMethod.methodType.parameterArray());
                return LOOKUP.unreflect(m);
            }
        case REF_invokeSpecial: {
                Method m = testMethod.clazz.getDeclaredMethod(testMethod.name, testMethod.methodType.parameterArray());
                Class<?> thisClass = LOOKUP.lookupClass();
                return LOOKUP.unreflectSpecial(m, thisClass);
            }
        case REF_newInvokeSpecial: {
                Constructor c = testMethod.clazz.getDeclaredConstructor(testMethod.methodType.parameterArray());
                return LOOKUP.unreflectConstructor(c);
            }
        default:
            throw new Error("ERROR: unexpected referenceKind in test data");
        }
    }

    private static Object newMethodHandleInfo(MethodHandle mh) {
        try {
            return MHI_CONSTRUCTOR.invoke(mh);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static boolean isInvokeSpecial(MethodHandle mh) {
        try {
            return (boolean) MH_IS_INVOKESPECIAL.invokeExact(mh);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static String getName(Object mhi) {
        try {
            return (String) MHI_GET_NAME.invoke(mhi);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static MethodType getMethodType(Object mhi) {
        try {
            return (MethodType) MHI_GET_METHOD_TYPE.invoke(mhi);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static Class<?> getDeclaringClass(Object mhi) {
        try {
            return (Class<?>) MHI_GET_DECLARING_CLASS.invoke(mhi);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static int getReferenceKind(Object mhi) {
        try {
            return (int) MHI_GET_REFERENCE_KIND.invoke(mhi);
        } catch (Throwable ex) {
            throw new Error(ex);
        }
    }

    private static void assertRefKindEquals(int expect, int observed) {
        if (expect == observed) return;

        String msg = "expected " + REF_KIND_NAMES[(int) expect] +
                     " but observed " + REF_KIND_NAMES[(int) observed];
        System.out.println("FAILED: " + msg);
        throw new AssertionError(msg);
    }

    private static void assertEquals(Object expect, Object observed) {
        if (java.util.Objects.equals(expect, observed)) return;

        String msg = "expected " + expect + " but observed " + observed;
        System.out.println("FAILED: " + msg);
        throw new AssertionError(msg);
    }
}

class DummyFieldHolder {
    public static Integer staticField;
    public String instanceField;
    public byte instanceByteField;

    public DummyFieldHolder(byte unused1, Long... unused2) {
    }
}

