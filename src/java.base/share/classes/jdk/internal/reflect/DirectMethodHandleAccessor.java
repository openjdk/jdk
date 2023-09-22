/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.reflect;

import jdk.internal.access.JavaLangInvokeAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Hidden;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.invoke.MethodType.genericMethodType;
import static jdk.internal.reflect.MethodHandleAccessorFactory.LazyStaticHolder.JLIA;

class DirectMethodHandleAccessor extends MethodAccessorImpl {
    /**
     * Creates a MethodAccessorImpl for a non-native method.
     */
    static MethodAccessorImpl methodAccessor(Method method, MethodHandle target) {
        assert !Modifier.isNative(method.getModifiers());

        return new DirectMethodHandleAccessor(method, target, false);
    }

    /**
     * Creates MethodAccessorImpl for the adapter method for a caller-sensitive method.
     * The given target method handle is the adapter method with the additional caller class
     * parameter.
     */
    static MethodAccessorImpl callerSensitiveAdapter(Method original, MethodHandle target) {
        assert Reflection.isCallerSensitive(original);

        // for CSM adapter method with the additional caller class parameter
        // creates the adaptive method accessor only.
        return new DirectMethodHandleAccessor(original, target, true);
    }

    /**
     * Creates MethodAccessorImpl that invokes the given method via VM native reflection
     * support.  This is used for native methods.  It can be used for java methods
     * during early VM startup.
     */
    static MethodAccessorImpl nativeAccessor(Method method, boolean callerSensitive) {
        return callerSensitive ? new NativeAccessor(method, findCSMethodAdapter(method))
                               : new NativeAccessor(method);
    }

    private static final int PARAM_COUNT_MASK = 0x00FF;
    private static final int HAS_CALLER_PARAM_BIT = 0x0100;
    private static final int IS_STATIC_BIT = 0x0200;
    private static final int NONZERO_BIT = 0x8000_0000;

    private final Class<?> declaringClass;
    private final int paramCount;
    private final int flags;
    private final MethodHandle target;

    DirectMethodHandleAccessor(Method method, MethodHandle target, boolean hasCallerParameter) {
        this.declaringClass = method.getDeclaringClass();
        this.paramCount = method.getParameterCount();
        this.flags = (hasCallerParameter ? HAS_CALLER_PARAM_BIT : 0) |
                     (Modifier.isStatic(method.getModifiers()) ? IS_STATIC_BIT : 0);
        this.target = target;
    }

    @Override
    @ForceInline
    public Object invoke(Object obj, Object[] args) throws InvocationTargetException {
        if (!isStatic()) {
            checkReceiver(obj);
        }
        checkArgumentCount(paramCount, args);
        try {
            return invokeImpl(obj, args);
        } catch (ClassCastException | WrongMethodTypeException e) {
            if (isIllegalArgument(e)) {
                // No cause in IAE to be consistent with the old behavior
                throw new IllegalArgumentException("argument type mismatch");
            } else {
                throw new InvocationTargetException(e);
            }
        } catch (NullPointerException e) {
            if (isIllegalArgument(e)) {
                throw new IllegalArgumentException(e);
            } else {
                throw new InvocationTargetException(e);
            }
        } catch (Throwable e) {
            throw new InvocationTargetException(e);
        }
    }

    @Override
    @ForceInline
    public Object invoke(Object obj, Object[] args, Class<?> caller) throws InvocationTargetException {
        if (!isStatic()) {
            checkReceiver(obj);
        }
        checkArgumentCount(paramCount, args);
        try {
            return invokeImpl(obj, args, caller);
        } catch (ClassCastException | WrongMethodTypeException e) {
            if (isIllegalArgument(e)) {
                // No cause in IAE to be consistent with the old behavior
                throw new IllegalArgumentException("argument type mismatch");
            } else {
                throw new InvocationTargetException(e);
            }
        } catch (NullPointerException e) {
            if (isIllegalArgument(e)) {
                throw new IllegalArgumentException(e);
            } else {
                throw new InvocationTargetException(e);
            }
        } catch (Throwable e) {
            throw new InvocationTargetException(e);
        }
    }

    @Hidden
    @ForceInline
    private Object invokeImpl(Object obj, Object[] args) throws Throwable {
        return switch (paramCount) {
            case 0 -> target.invokeExact(obj);
            case 1 -> target.invokeExact(obj, args[0]);
            case 2 -> target.invokeExact(obj, args[0], args[1]);
            case 3 -> target.invokeExact(obj, args[0], args[1], args[2]);
            default -> target.invokeExact(obj, args);
        };
    }

    @Hidden
    @ForceInline
    private Object invokeImpl(Object obj, Object[] args, Class<?> caller) throws Throwable {
        if (hasCallerParameter()) {
            // caller-sensitive method is invoked through method with caller parameter
            return switch (paramCount) {
                case 0 -> target.invokeExact(obj, caller);
                case 1 -> target.invokeExact(obj, args[0], caller);
                case 2 -> target.invokeExact(obj, args[0], args[1], caller);
                case 3 -> target.invokeExact(obj, args[0], args[1], args[2], caller);
                default -> target.invokeExact(obj, args, caller);
            };
        } else {
            // caller-sensitive method is invoked through a per-caller invoker while
            // the target MH is always spreading the args
            var invoker = JLIA.reflectiveInvoker(caller);
            // invoke the target method handle via an invoker
            return invoker.invokeExact(target, obj, args);
        }
    }

    private boolean isStatic() {
        return (flags & IS_STATIC_BIT) == IS_STATIC_BIT;
    }

    private boolean hasCallerParameter() {
        return (flags & HAS_CALLER_PARAM_BIT) == HAS_CALLER_PARAM_BIT;
    }

    private boolean isIllegalArgument(RuntimeException ex) {
        return AccessorUtils.isIllegalArgument(DirectMethodHandleAccessor.class, ex);
    }

    private void checkReceiver(Object o) {
        // NOTE: will throw NullPointerException, as specified, if o is null
        if (!declaringClass.isAssignableFrom(o.getClass())) {
            throw new IllegalArgumentException("object of type " + o.getClass().getName()
                    + " is not an instance of " + declaringClass.getName());
        }
    }

    /**
     * Invoke the method via native VM reflection
     */
    static class NativeAccessor extends MethodAccessorImpl {
        private final Method method;
        private final Method csmAdapter;
        private final boolean callerSensitive;
        NativeAccessor(Method method) {
            assert !Reflection.isCallerSensitive(method);
            this.method = method;
            this.csmAdapter = null;
            this.callerSensitive = false;
        }

        NativeAccessor(Method method, Method csmAdapter) {
            assert Reflection.isCallerSensitive(method);
            this.method = method;
            this.csmAdapter = csmAdapter;
            this.callerSensitive = true;
        }

        @Override
        public Object invoke(Object obj, Object[] args) throws InvocationTargetException {
            assert csmAdapter == null;
            return invoke0(method, obj, args);
        }

        @Override
        public Object invoke(Object obj, Object[] args, Class<?> caller) throws InvocationTargetException {
            assert callerSensitive;

            if (csmAdapter != null) {
                Object[] newArgs = new Object[csmAdapter.getParameterCount()];
                newArgs[0] = caller;
                if (args != null) {
                    System.arraycopy(args, 0, newArgs, 1, args.length);
                }
                return invoke0(csmAdapter, obj, newArgs);
            } else {
                assert VM.isJavaLangInvokeInited();
                try {
                    return ReflectiveInvoker.invoke(methodAccessorInvoker(), caller, obj, args);
                } catch (InvocationTargetException|RuntimeException|Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }
        }

        public Object invokeViaReflectiveInvoker(Object obj, Object[] args) throws InvocationTargetException {
            return invoke0(method, obj, args);
        }

        /*
         * A method handle to invoke Reflective::Invoker
         */
        private MethodHandle maInvoker;
        private MethodHandle methodAccessorInvoker() {
            MethodHandle invoker = maInvoker;
            if (invoker == null) {
                maInvoker = invoker = ReflectiveInvoker.bindTo(this);
            }
            return invoker;
        }

        private static native Object invoke0(Method m, Object obj, Object[] args);

        static class ReflectiveInvoker {
            /**
             * Return a method handle for NativeAccessor::invoke bound to the given accessor object
             */
            static MethodHandle bindTo(NativeAccessor accessor) {
                return NATIVE_ACCESSOR_INVOKE.bindTo(accessor);
            }

            /*
             * When Method::invoke on a caller-sensitive method is to be invoked
             * and no adapter method with an additional caller class argument is defined,
             * the caller-sensitive method must be invoked via an invoker injected
             * which has the following signature:
             *     reflect_invoke_V(MethodHandle mh, Object target, Object[] args)
             *
             * The stack frames calling the method `csm` through reflection will
             * look like this:
             *     obj.csm(args)
             *     NativeAccessor::invoke(obj, args)
             *     InjectedInvoker::reflect_invoke_V(vamh, obj, args);
             *     method::invoke(obj, args)
             *     p.Foo::m
             *
             * An injected invoker class is a hidden class which has the same
             * defining class loader, runtime package, and protection domain
             * as the given caller class.
             *
             * The caller-sensitive method will call Reflection::getCallerClass
             * to get the caller class.
             */
            static Object invoke(MethodHandle target, Class<?> caller, Object obj, Object[] args)
                    throws InvocationTargetException
            {
                var reflectInvoker = JLIA.reflectiveInvoker(caller);
                try {
                    return reflectInvoker.invokeExact(target, obj, args);
                } catch (InvocationTargetException | RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new InternalError(e);
                }
            }

            static final JavaLangInvokeAccess JLIA;
            static final MethodHandle NATIVE_ACCESSOR_INVOKE;
            static {
                try {
                    JLIA = SharedSecrets.getJavaLangInvokeAccess();
                    NATIVE_ACCESSOR_INVOKE = MethodHandles.lookup().findVirtual(NativeAccessor.class, "invoke",
                            genericMethodType(1, true));
                } catch (NoSuchMethodException|IllegalAccessException e) {
                    throw new InternalError(e);
                }
            }
        }
    }

    private static void checkArgumentCount(int paramCount, Object[] args) {
        int argc = args != null ? args.length : 0;
        if (argc != paramCount) {
            throw new IllegalArgumentException("wrong number of arguments: " + argc + " expected: " + paramCount);
        }
    }

    /**
     * Returns an adapter for caller-sensitive method if present.
     * Otherwise, null.
     *
     * A trusted method can define an adapter method for a caller-sensitive method `foo`
     * with an additional caller class argument that will be invoked reflectively.
     */
    private static Method findCSMethodAdapter(Method method) {
        if (!Reflection.isCallerSensitive(method)) return null;

        int paramCount = method.getParameterCount();
        Class<?>[] ptypes = new Class<?>[paramCount+1];
        ptypes[paramCount] = Class.class;
        System.arraycopy(method.getParameterTypes(), 0, ptypes, 0, paramCount);
        try {
            return method.getDeclaringClass().getDeclaredMethod(method.getName(), ptypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }
}
