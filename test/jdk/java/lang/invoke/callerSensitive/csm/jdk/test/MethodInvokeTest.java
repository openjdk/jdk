/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test;

import java.lang.StackWalker.StackFrame;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.CSM;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.function.Supplier;

/**
 * This test invokes caller-sensitive methods via static reference,
 * reflection, MethodHandle, lambda.  If there is no alternate implementation
 * of a CSM with a trailing caller class parameter, when a CSM is invoked
 * via method handle, an invoker class is injected as the caller class
 * which is defined by the same defining class loader, in the same runtime
 * package, and protection domain as the lookup class.
 */
public class MethodInvokeTest {
    static final Policy DEFAULT_POLICY = Policy.getPolicy();
    private static final String CALLER_METHOD = "caller";
    private static final String CALLER_NO_ALT_METHOD = "callerNoAlternateImpl";

    public static void main(String... args) throws Throwable {
        boolean sm = args.length > 0 && args[0].equals("sm");
        System.err.format("Test %s security manager.%n",
                          sm ? "with" : "without");
        if (sm) {
            setupSecurityManager();
        }

        MethodInvokeTest test = new MethodInvokeTest();
        // test static call to java.util.CSM::caller
        test.staticMethodCall();
        // test java.lang.reflect.Method call
        test.reflectMethodCall();
        // test java.lang.invoke.MethodHandle
        test.invokeMethodHandle();
        // test method ref
        test.lambda();
    }

    static void setupSecurityManager() {
        PermissionCollection perms = new Permissions();
        perms.add(new RuntimePermission("getStackWalkerWithClassReference"));
        Policy.setPolicy(new Policy() {
            @Override
            public boolean implies(ProtectionDomain domain, Permission p) {
                return perms.implies(p) || DEFAULT_POLICY.implies(domain, p);
            }
        });
        System.setSecurityManager(new SecurityManager());
    }

    void staticMethodCall() {
        checkCaller(java.util.CSM.caller(), MethodInvokeTest.class, true);
        checkCaller(java.util.CSM.callerNoAlternateImpl(), MethodInvokeTest.class, false);
    }

    void reflectMethodCall() throws Throwable {
        // zero-arg caller method
        checkCaller(Caller1.invoke(CSM.class.getMethod(CALLER_METHOD)), Caller1.class, true);
        checkCaller(Caller2.invoke(CSM.class.getMethod(CALLER_METHOD)), Caller2.class, true);
        // 4-arg caller method
        checkCaller(Caller1.invoke(CSM.class.getMethod(CALLER_METHOD, Object.class, Object.class, Object.class, Object.class),
                                   new Object[] { null, null, null, null}), Caller1.class, true);
        checkCaller(Caller2.invoke(CSM.class.getMethod(CALLER_METHOD, Object.class, Object.class, Object.class, Object.class),
                                   new Object[] { null, null, null, null}), Caller2.class, true);

        // Reflection::getCallerClass will return the injected invoker class as the caller
        checkInjectedInvoker(Caller1.invoke(CSM.class.getMethod(CALLER_NO_ALT_METHOD)), Caller1.class);
        checkInjectedInvoker(Caller2.invoke(CSM.class.getMethod(CALLER_NO_ALT_METHOD)), Caller2.class);
    }

    void invokeMethodHandle() throws Throwable {
        checkCaller(Caller1.invokeExact(CALLER_METHOD), Caller1.class, true);
        checkCaller(Caller2.invokeExact(CALLER_METHOD), Caller2.class, true);

        checkInjectedInvoker(Caller1.invokeExact(CALLER_NO_ALT_METHOD), Caller1.class);
        checkInjectedInvoker(Caller2.invokeExact(CALLER_NO_ALT_METHOD), Caller2.class);
    }

    void lambda() {
        CSM caller = LambdaTest.caller.get();
        LambdaTest.checkLambdaProxyClass(caller);

        caller = LambdaTest.caller();
        LambdaTest.checkLambdaProxyClass(caller);
    }

    static class Caller1 {
        static CSM invoke(Method csm) throws ReflectiveOperationException {
            return (CSM)csm.invoke(null);
        }
        static CSM invoke(Method csm, Object[] args) throws ReflectiveOperationException {
            return (CSM)csm.invoke(null, args);
        }
        static CSM invokeExact(String methodName) throws Throwable {
            MethodHandle mh = MethodHandles.lookup().findStatic(java.util.CSM.class,
                    methodName, MethodType.methodType(CSM.class));
            return (CSM)mh.invokeExact();
        }
    }

    static class Caller2 {
        static CSM invoke(Method csm) throws ReflectiveOperationException {
            return (CSM)csm.invoke(null);
        }
        static CSM invoke(Method csm, Object[] args) throws ReflectiveOperationException {
            return (CSM)csm.invoke(null, args);
        }
        static CSM invokeExact(String methodName) throws Throwable {
            MethodHandle mh = MethodHandles.lookup().findStatic(java.util.CSM.class,
                    methodName, MethodType.methodType(CSM.class));
            return (CSM)mh.invokeExact();
        }
    }

    static class LambdaTest {
        static Supplier<CSM> caller = java.util.CSM::caller;

        static CSM caller() {
            return caller.get();
        }

        /*
         * The class calling the caller-sensitive method is the lambda proxy class
         * generated for LambdaTest.
         */
        static void checkLambdaProxyClass(CSM csm) {
            Class<?> caller = csm.caller;
            assertTrue(caller.isHidden(), caller + " should be a hidden class");
            assertEquals(caller.getModule(), LambdaTest.class.getModule());

            int index = caller.getName().indexOf('/');
            String cn = caller.getName().substring(0, index);
            assertTrue(cn.startsWith(LambdaTest.class.getName() + "$$Lambda$"), caller + " should be a lambda proxy class");
        }
    }
    static void checkCaller(CSM csm, Class<?> expected, boolean adapter) {
        assertEquals(csm.caller, expected);
        assertEquals(csm.adapter, adapter);
        // verify no invoker class injected
        for (StackFrame frame : csm.stackFrames) {
            Class<?> c = frame.getDeclaringClass();
            if (c == expected) break;

            if (c.getName().startsWith(expected.getName() + "$$InjectedInvoker"))
                throw new RuntimeException("should not have any invoker class injected");
        }
    }

    /*
     * The class calling the direct method handle of the caller-sensitive class
     * is the InjectedInvoker class generated for the given caller.
     */
    static void checkInjectedInvoker(CSM csm, Class<?> expected) {
        Class<?> invoker = csm.caller;
        assertTrue(invoker.isHidden(), invoker + " should be a hidden class");
        assertEquals(invoker.getModule(), expected.getModule());
        assertEquals(csm.adapter, false);

        int index = invoker.getName().indexOf('/');
        String cn = invoker.getName().substring(0, index);
        assertEquals(cn, expected.getName() + "$$InjectedInvoker");

        // check the invoker class on the stack
        for (StackFrame frame : csm.stackFrames) {
            Class<?> c = frame.getDeclaringClass();
            if (c.getName().startsWith(expected.getName() + "$$InjectedInvoker"))
                break;

            if (c == expected)
                throw new RuntimeException("no invoker class found before the expected caller class");
        }
    }

    static void assertTrue(boolean value, String msg) {
        if (!value) {
            throw new RuntimeException(msg);
        }
    }
    static void assertEquals(Object o1, Object o2) {
        if (!o1.equals(o2)) {
            throw new RuntimeException(o1 + " != " + o2);
        }
    }
}
