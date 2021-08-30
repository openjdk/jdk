/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Hidden;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.invoke.MethodType.genericMethodType;
import static jdk.internal.reflect.MethodHandleAccessorFactory.SPECIALIZED_PARAM_COUNT;
import static jdk.internal.reflect.MethodHandleAccessorFactory.newMethodHandleInvoker;

class DirectMethodHandleAccessor extends MethodAccessorImpl {
    /**
     * Creates a MethodAccessorImpl for a non-native and non-caller-sensitive method.
     */
    static MethodAccessorImpl methodAccessor(Method method, MethodHandle target) {
        assert !Modifier.isNative(method.getModifiers()) && !Reflection.isCallerSensitive(method);

        if (ReflectionFactory.noInflation()) {
            // fast invoker
            var mhInvoker = newMethodHandleInvoker(method, target, false);
            return new DirectMethodHandleAccessor(method, target, mhInvoker, false);
        } else {
            // Default is the adaptive accessor method.
            return new AdaptiveMethodHandleAccessor(method, target);
        }
    }

    /**
     * Creates a MethodAccessorImpl for a caller-sensitive method.
     */
    static MethodAccessorImpl callerSensitiveMethodAccessor(Method method, MethodHandle dmh) {
        assert Reflection.isCallerSensitive(method);
        return new CallerSensitiveWithInvoker(method, dmh);
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
        return new AdapterMethodHandleAccessorWithCaller(original, target);
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

    protected final Method method;
    protected final int paramCount;
    protected final boolean hasCallerParameter;
    protected final boolean isStatic;
    @Stable protected final MethodHandle target;
    @Stable protected final MHInvoker invoker;

    DirectMethodHandleAccessor(Method method, MethodHandle target, MHInvoker invoker, boolean hasCallerParameter) {
        this.method = method;
        this.paramCount = method.getParameterCount();
        this.hasCallerParameter = hasCallerParameter;
        this.target = target;
        this.invoker = invoker;
        this.isStatic = Modifier.isStatic(method.getModifiers());
    }

    DirectMethodHandleAccessor(Method method, MethodHandle target, boolean hasCallerParameter) {
        this(method, target, new MHInvokerDelegate(target), hasCallerParameter);
    }

    @ForceInline
    MHInvoker mhInvoker() {
        return invoker;
    }

    /**
     * Returns a MHInvoker that invokes the given target method handle
     * from a hidden class.
     */
    MHInvoker spinMethodHandleInvoker() {
        return newMethodHandleInvoker(method, target, hasCallerParameter);
    }

    @Override
    @ForceInline
    public Object invoke(Object obj, Object[] args) throws InvocationTargetException {
        if (!isStatic) {
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
        if (!isStatic) {
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

    // implemented by AdapterMethodHandleAccessorWithCaller and CallerSensitiveWithInvoker
    Object invokeImpl(Object obj, Object[] args, Class<?> caller) throws Throwable {
        throw new InternalError("caller-sensitive adapter method only" + method);
    }

    @Hidden
    @ForceInline
    Object invokeImpl(Object obj, Object[] args) throws Throwable {
        var mhInvoker = mhInvoker();
        return switch (paramCount) {
            case 0 -> mhInvoker.invoke(obj);
            case 1 -> mhInvoker.invoke(obj, args[0]);
            case 2 -> mhInvoker.invoke(obj, args[0], args[1]);
            case 3 -> mhInvoker.invoke(obj, args[0], args[1], args[2]);
            default -> mhInvoker.invoke(obj, args);
        };
    }

    boolean isIllegalArgument(RuntimeException ex) {
        return AccessorUtils.isIllegalArgument(DirectMethodHandleAccessor.class, ex);
    }

    void checkReceiver(Object o) {
        // NOTE: will throw NullPointerException, as specified, if o is null
        if (!method.getDeclaringClass().isAssignableFrom(o.getClass())) {
            throw new IllegalArgumentException("object is not an instance of declaring class");
        }
    }

    static class AdaptiveMethodHandleAccessor extends DirectMethodHandleAccessor {
        private @Stable MHInvoker fastInvoker;
        private int numInvocations;

        AdaptiveMethodHandleAccessor(Method method, MethodHandle target) {
            this(method, target, false);
        }
        AdaptiveMethodHandleAccessor(Method method, MethodHandle target, boolean hasCallerParameter) {
            super(method, target, hasCallerParameter);
        }

        @ForceInline
        MHInvoker mhInvoker() {
            var invoker = fastInvoker;
            if (invoker != null) {
                return invoker;
            }
            return slowInvoker();
        }

        @DontInline
        private MHInvoker slowInvoker() {
            var invoker = this.invoker;
            if (++numInvocations > ReflectionFactory.inflationThreshold()) {
                fastInvoker = invoker = spinMethodHandleInvoker();
            }
            return invoker;
        }
    }

    static class AdapterMethodHandleAccessorWithCaller extends AdaptiveMethodHandleAccessor {
        AdapterMethodHandleAccessorWithCaller(Method method, MethodHandle target) {
            this(method, target, true);
        }
        AdapterMethodHandleAccessorWithCaller(Method method, MethodHandle target, boolean hasCallerParameter) {
            super(method, target, hasCallerParameter);
        }

        @Override
        public Object invoke(Object obj, Object[] args) throws InvocationTargetException {
            throw new InternalError("caller sensitive method invoked without explicit caller: " + method);
        }

        @Hidden
        @ForceInline
        Object invokeImpl(Object obj, Object[] args, Class<?> caller) throws Throwable {
            var mhInvoker = mhInvoker();
            return switch (paramCount) {
                case 0 -> mhInvoker.invoke(obj, caller);
                case 1 -> mhInvoker.invoke(obj, args[0], caller);
                case 2 -> mhInvoker.invoke(obj, args[0], args[1], caller);
                case 3 -> mhInvoker.invoke(obj, args[0], args[1], args[2], caller);
                default -> mhInvoker.invoke(obj, args, caller);
            };
        }
    }

    /**
     * MethodAccessor class to invoke caller-sensitive methods via a reflective invoker
     * injected as a caller class to invoke a method handle.
     *
     * This uses the simple form of direct method handle with the same method type
     * as Method::invoke.
     *
     * To use specialized target method handles (see MethodHandleAccessorFactory::makeSpecializedTarget)
     * it needs support in the injected invoker::reflect_invoke_V for different specialized forms.
     */
    static class CallerSensitiveWithInvoker extends DirectMethodHandleAccessor {
        private static final JavaLangInvokeAccess JLIA = SharedSecrets.getJavaLangInvokeAccess();
        private CallerSensitiveWithInvoker(Method method, MethodHandle target) {
            super(method, target, null, false);
        }

        @Override
        public Object invoke(Object obj, Object[] args) throws InvocationTargetException {
            throw new InternalError("caller-sensitive method invoked without explicit caller: " + target);
        }

        @Hidden
        @ForceInline
        Object invokeImpl(Object obj, Object[] args, Class<?> caller) throws Throwable {
            // caller-sensitive method is invoked through a per-caller invoker
            var invoker = JLIA.reflectiveInvoker(caller);
            try {
                // invoke the target method handle via an invoker
                return invoker.invokeExact(target, obj, args);
            } catch (IllegalArgumentException e) {
                throw new InvocationTargetException(e);
            }
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
        // only check argument count for specialized forms
        if (paramCount > SPECIALIZED_PARAM_COUNT) return;

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

