/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8010117
 * @summary Test if the VM enforces Reflection.getCallerClass
 *          be called by system methods annotated with CallerSensitive plus
 *          test reflective and method handle based invocation of caller-sensitive
 *          methods with or without the CSM adapter method
 * @modules java.base/jdk.internal.reflect
 * @build SetupGetCallerClass boot.GetCallerClass
 * @run driver SetupGetCallerClass
 * @run main/othervm -Xbootclasspath/a:bcp -Djdk.reflect.useDirectMethodHandle=true GetCallerClassTest
 */

/*
 * @test
 * @summary Verify the new NativeAccessor
 * @modules java.base/jdk.internal.reflect
 * @build SetupGetCallerClass boot.GetCallerClass
 * @run driver SetupGetCallerClass
 * @run main/othervm -Xbootclasspath/a:bcp -Djdk.reflect.useDirectMethodHandle=true -Djdk.reflect.useNativeAccessorOnly=true GetCallerClassTest
 */

/*
 * @test
 * @summary Verify NativeMethodAccessorImpl
 * @modules java.base/jdk.internal.reflect
 * @build SetupGetCallerClass boot.GetCallerClass
 * @run driver SetupGetCallerClass
 * @run main/othervm -Xbootclasspath/a:bcp -Djdk.reflect.useDirectMethodHandle=false -Dsun.reflect.noInflation=false GetCallerClassTest
 */

/*
 * @test
 * @summary Verify the old generated MethodAccessor
 * @modules java.base/jdk.internal.reflect
 * @build SetupGetCallerClass boot.GetCallerClass
 * @run driver SetupGetCallerClass
 * @run main/othervm -Xbootclasspath/a:bcp -Djdk.reflect.useDirectMethodHandle=false -Dsun.reflect.noInflation=true GetCallerClassTest
 */

import boot.GetCallerClass;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

public class GetCallerClassTest {
    // boot.GetCallerClass is in bootclasspath
    private static final Class<GetCallerClass> gccCl = GetCallerClass.class;
    private final GetCallerClass gcc = new GetCallerClass();

    public static void main(String[] args) throws Exception {
        GetCallerClassTest gcct = new GetCallerClassTest();
        // ensure methods are annotated with @CallerSensitive and verify Reflection.isCallerSensitive()
        ensureAnnotationPresent(GetCallerClassTest.class, "testNonSystemMethod", false);

        ensureAnnotationPresent(gccCl, "getCallerClass", true);
        ensureAnnotationPresent(gccCl, "getCallerClassStatic", true);
        ensureAnnotationPresent(gccCl, "getCallerClassNoAlt", true);
        ensureAnnotationPresent(gccCl, "getCallerClassStaticNoAlt", true);

        // call Reflection.getCallerClass from bootclasspath without @CS
        gcct.testMissingCallerSensitiveAnnotation();
        // call Reflection.getCallerClass from classpath with @CS
        gcct.testNonSystemMethod();
        // call Reflection.getCallerClass from bootclasspath with @CS
        gcct.testCallerSensitiveMethods();
        // call @CS methods using reflection
        gcct.testCallerSensitiveMethodsUsingReflection();
        // call @CS methods using method handles
        gcct.testCallerSensitiveMethodsUsingMethodHandles();
        // call @CS methods using reflection but call Method.invoke with a method handle
        gcct.testCallerSensitiveMethodsUsingMethodHandlesAndReflection();
    }

    private static void ensureAnnotationPresent(Class<?> c, String name, boolean cs)
        throws NoSuchMethodException
    {
        Method m = c.getDeclaredMethod(name);
        if (!m.isAnnotationPresent(CallerSensitive.class)) {
            throw new RuntimeException("@CallerSensitive not present in method " + m);
        }
        if (Reflection.isCallerSensitive(m) != cs) {
            throw new RuntimeException("Unexpected: isCallerSensitive returns " +
                Reflection.isCallerSensitive(m));
        }
    }

    private void testMissingCallerSensitiveAnnotation() {
        System.out.println("\ntestMissingCallerSensitiveAnnotation...");
        try {
            gcc.missingCallerSensitiveAnnotation();
            throw new RuntimeException("shouldn't have succeeded");
        } catch (InternalError e) {
            if (e.getMessage().startsWith("CallerSensitive annotation expected")) {
                System.out.println("Expected error: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    @CallerSensitive
    private void testNonSystemMethod() {
        System.out.println("\ntestNonSystemMethod...");
        try {
            Class<?> c = Reflection.getCallerClass();
            throw new RuntimeException("shouldn't have succeeded");
        } catch (InternalError e) {
            if (e.getMessage().startsWith("CallerSensitive annotation expected")) {
                System.out.println("Expected error: " + e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private void testCallerSensitiveMethods() {
        System.out.println();
        Class<?> caller;

        caller = gcc.getCallerClass();
        if (caller != GetCallerClassTest.class) {
            throw new RuntimeException("mismatched caller: " + caller);
        }

        caller = GetCallerClass.getCallerClassStatic();
        if (caller != GetCallerClassTest.class) {
            throw new RuntimeException("mismatched caller: " + caller);
        }
    }

    private void testCallerSensitiveMethodsUsingReflection() {
        System.out.println();

        try {
            Class<?> caller;

            caller = (Class<?>) gccCl.getDeclaredMethod("getCallerClass").invoke(gcc);
            if (caller != GetCallerClassTest.class) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) gccCl.getDeclaredMethod("getCallerClassStatic").invoke(null);
            if (caller != GetCallerClassTest.class) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) gccCl.getDeclaredMethod("getCallerClassNoAlt").invoke(gcc);
            if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) gccCl.getDeclaredMethod("getCallerClassStaticNoAlt").invoke(null);
            if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                throw new RuntimeException("mismatched caller: " + caller);
            }
        } catch (ReflectiveOperationException|SecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private void testCallerSensitiveMethodsUsingMethodHandles() {
        System.out.println();

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(Class.class);
            Class<?> caller;

            caller = (Class<?>) lookup.findVirtual(gccCl, "getCallerClass", mt).invokeExact(gcc);
            if (caller != GetCallerClassTest.class) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) lookup.findStatic(gccCl, "getCallerClassStatic", mt).invokeExact();
            if (caller != GetCallerClassTest.class) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) lookup.findVirtual(gccCl, "getCallerClassNoAlt", mt).invokeExact(gcc);
            if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) lookup.findStatic(gccCl, "getCallerClassStaticNoAlt", mt).invokeExact();
            if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                throw new RuntimeException("mismatched caller: " + caller);
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];

    private void testCallerSensitiveMethodsUsingMethodHandlesAndReflection() {
        // In the old implementation, the caller returned is java.lang.invoke.Method
        // since it looks up the caller through stack walking.
        // The new implementation uses the special calling sequence and Method::invoke
        // defines an adapter method such that the stack walking is done only once
        // using the same caller class.
        String s = System.getProperty("jdk.reflect.useDirectMethodHandle", "true");
        boolean newImpl = Boolean.valueOf(s);
        Class<?> expectedCaller = newImpl ? GetCallerClassTest.class : Method.class;

        System.out.println();
        try {
            MethodHandle methodInvokeMh = MethodHandles
                .lookup()
                .findVirtual(Method.class, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class));

            Class<?> caller;

            caller = (Class<?>) methodInvokeMh.invoke(gccCl.getDeclaredMethod("getCallerClass"), gcc, EMPTY_ARRAY);
            if (caller != expectedCaller) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) methodInvokeMh.invoke(gccCl.getDeclaredMethod("getCallerClassStatic"), null, EMPTY_ARRAY);
            if (caller != expectedCaller) {
                throw new RuntimeException("mismatched caller: " + caller);
            }

            caller = (Class<?>) methodInvokeMh.invoke(gccCl.getDeclaredMethod("getCallerClassNoAlt"), gcc, EMPTY_ARRAY);
            if (newImpl) {
                if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                    throw new RuntimeException("mismatched caller: " + caller);
                }
            } else {
                if (caller != expectedCaller) {
                    throw new RuntimeException("mismatched caller: " + caller);
                }
            }

            caller = (Class<?>) methodInvokeMh.invoke(gccCl.getDeclaredMethod("getCallerClassStaticNoAlt"), null, EMPTY_ARRAY);
            if (newImpl) {
                if (!caller.isNestmateOf(GetCallerClassTest.class)) {
                    throw new RuntimeException("mismatched caller: " + caller);
                }
            } else {
                if (caller != expectedCaller) {
                    throw new RuntimeException("mismatched caller: " + caller);
                }
            }
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

