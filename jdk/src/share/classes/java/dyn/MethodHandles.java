/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package java.dyn;

import java.lang.reflect.Constructor;
import sun.dyn.Access;
import sun.dyn.MemberName;
import sun.dyn.MethodHandleImpl;
import sun.dyn.util.VerifyAccess;
import sun.dyn.util.Wrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import sun.dyn.Invokers;
import sun.dyn.MethodTypeImpl;
import sun.reflect.Reflection;
import static sun.dyn.MemberName.newIllegalArgumentException;
import static sun.dyn.MemberName.newNoAccessException;

/**
 * Fundamental operations and utilities for MethodHandle.
 * <p>
 * <em>API Note:</em>  The matching of method types in this API cannot
 * be completely checked by Java's generic type system for three reasons:
 * <ol>
 * <li>Method types range over all possible arities,
 * from no arguments to an arbitrary number of arguments.
 * Generics are not variadic, and so cannot represent this.</li>
 * <li>Method types can specify arguments of primitive types,
 * which Java generic types cannot range over.</li>
 * <li>Method types can optionally specify varargs (ellipsis).</li>
 * </ol>
 * @author John Rose, JSR 292 EG
 */
public class MethodHandles {

    private MethodHandles() { }  // do not instantiate

    private static final Access IMPL_TOKEN = Access.getToken();
    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory(IMPL_TOKEN);
    static { MethodHandleImpl.initStatics(); }
    // See IMPL_LOOKUP below.

    //// Method handle creation from ordinary methods.

    public static Lookup lookup() {
        return new Lookup();
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * A factory object for creating method handles, when the creation
     * requires access checking.  Method handles do not perform
     * access checks when they are called; this is a major difference
     * from reflective {@link Method}, which performs access checking
     * against every caller, on every call.  Method handle access
     * restrictions are enforced when a method handle is created.
     * The caller class against which those restrictions are enforced
     * is known as the "lookup class".  {@link Lookup} embodies an
     * authenticated lookup class, and can be used to create any number
     * of access-checked method handles, all checked against a single
     * lookup class.
     * <p>
     * A class which needs to create method handles will call
     * {@code MethodHandles.lookup()} to create a factory for itself.
     * It may then use this factory to create method handles on
     * all of its methods, including private ones.
     * It may also delegate the lookup (e.g., to a metaobject protocol)
     * by passing the {@code Lookup} object to other code.
     * If this other code creates method handles, they will be access
     * checked against the original lookup class, and not with any higher
     * privileges.
     * <p>
     * Note that access checks only apply to named and reflected methods.
     * Other method handle creation methods, such as {@link #convertArguments},
     * do not require any access checks, and can be done independently
     * of any lookup class.
     * <p>
     * <em>A note about error conditions:<em>  A lookup can fail, because
     * the containing class is not accessible to the lookup class, or
     * because the desired class member is missing, or because the
     * desired class member is not accessible to the lookup class.
     * It can also fail if a security manager is installed and refuses
     * access.  In any of these cases, an exception will be
     * thrown from the attempted lookup.
     * In general, the conditions under which a method handle may be
     * created for a method {@code M} are exactly as restrictive as the conditions
     * under which the lookup class could have compiled a call to {@code M}.
     * At least some of these error conditions are likely to be
     * represented by checked exceptions in the final version of this API.
     */
    public static final
    class Lookup {
        private final Class<?> lookupClass;

        /** Which class is performing the lookup?  It is this class against
         *  which checks are performed for visibility and access permissions.
         *  <p>
         *  This value is null if and only if this lookup is {@link #PUBLIC_LOOKUP}.
         */
        public Class<?> lookupClass() {
            return lookupClass;
        }

        /** Embody the current class (the lookupClass) as a lookup class
         * for method handle creation.
         * Must be called by from a method in this package,
         * which in turn is called by a method not in this package.
         * Also, don't make it private, lest javac interpose
         * an access$N method.
         */
        Lookup() {
            Class caller = getCallerClassAtEntryPoint();
            // make sure we haven't accidentally picked up this class:
            checkUnprivilegedlookupClass(caller);
            this.lookupClass = caller;
        }

        private Lookup(Class<?> lookupClass) {
            this.lookupClass = lookupClass;
        }

        /** Version of lookup which is trusted minimally.
         *  It can only be used to create method handles to
         *  publicly accessible members.
         */
        public static final Lookup PUBLIC_LOOKUP = new Lookup(null);

        /** Package-private version of lookup which is trusted. */
        static final Lookup IMPL_LOOKUP = new Lookup(Access.class);
        static { MethodHandleImpl.initLookup(IMPL_TOKEN, IMPL_LOOKUP); }

        private static void checkUnprivilegedlookupClass(Class<?> lookupClass) {
            if (lookupClass == null ||
                lookupClass == Access.class ||
                lookupClass.getName().startsWith("java.dyn."))
                throw newIllegalArgumentException("illegal lookupClass: "+lookupClass);
        }

        @Override
        public String toString() {
            if (lookupClass == null)
                return "public";
            return lookupClass.getName();
        }

        // call this from an entry point method in Lookup with extraFrames=0.
        private static Class<?> getCallerClassAtEntryPoint() {
            final int CALLER_DEPTH = 4;
            // 0: Reflection.getCC, 1: getCallerClassAtEntryPoint,
            // 2: Lookup.<init>, 3: MethodHandles.*, 4: caller
            // Note:  This should be the only use of getCallerClass in this file.
            return Reflection.getCallerClass(CALLER_DEPTH);
        }

        /**
         * Produce a method handle for a static method.
         * The type of the method handle will be that of the method.
         * The method and all its argument types must be accessible to the lookup class.
         * If the method's class has not yet been initialized, that is done
         * immediately, before the method handle is returned.
         * @param defc the class from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method
         * @return the desired method handle
         * @exception SecurityException <em>TBD</em>
         * @exception NoAccessException if the method does not exist or access checking fails
         */
        public
        MethodHandle findStatic(Class<?> defc, String name, MethodType type) throws NoAccessException {
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type, Modifier.STATIC), true, lookupClass);
            checkStatic(true, method, lookupClass);
            //throw NoSuchMethodException
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookupClass);
        }

        /**
         * Produce a method handle for a virtual method.
         * The type of the method handle will be that of the method,
         * with the receiver type ({@code defc}) prepended.
         * The method and all its argument types must be accessible to the lookup class.
         * <p>
         * When called, the handle will treat the first argument as a receiver
         * and dispatch on the receiver's type to determine which method
         * implementation to enter.
         * (The dispatching action is identical with that performed by an
         * {@code invokevirtual} or {@code invokeinterface} instruction.)
         * @param defc the class or interface from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @exception SecurityException <em>TBD</em>
         * @exception NoAccessException if the method does not exist or access checking fails
         */
        public MethodHandle findVirtual(Class<?> defc, String name, MethodType type) throws NoAccessException {
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type), true, lookupClass);
            checkStatic(false, method, lookupClass);
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClass);
        }

        /**
         * Produce an early-bound method handle for a virtual method,
         * as if called from an {@code invokespecial}
         * instruction from {@code caller}.
         * The type of the method handle will be that of the method,
         * with a suitably restricted receiver type (such as {@code caller}) prepended.
         * The method and all its argument types must be accessible
         * to the caller.
         * <p>
         * When called, the handle will treat the first argument as a receiver,
         * but will not dispatch on the receiver's type.
         * (This direct invocation action is identical with that performed by an
         * {@code invokespecial} instruction.)
         * <p>
         * If the explicitly specified caller class is not identical with the
         * lookup class, a security check TBD is performed.
         * @param defc the class or interface from which the method is accessed
         * @param name the name of the method, or "<init>" for a constructor
         * @param type the type of the method, with the receiver argument omitted
         * @param specialCaller the proposed calling class to perform the {@code invokespecial}
         * @return the desired method handle
         * @exception SecurityException <em>TBD</em>
         * @exception NoAccessException if the method does not exist or access checking fails
         */
        public MethodHandle findSpecial(Class<?> defc, String name, MethodType type,
                                        Class<?> specialCaller) throws NoAccessException {
            checkSpecialCaller(specialCaller, lookupClass);
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type), false, specialCaller);
            checkStatic(false, method, lookupClass);
            if (name.equals("<init>")) {
                if (defc != specialCaller)
                    throw newNoAccessException("constructor must be local to lookup class", method, lookupClass);
            } else if (defc.isInterface() || !defc.isAssignableFrom(specialCaller)) {
                throw newNoAccessException("method must be in a superclass of lookup class", method, lookupClass);
            }
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, specialCaller);
        }

        /**
         * Produce an early-bound method handle for a non-static method.
         * The receiver must have a supertype {@code defc} in which a method
         * of the given name and type is accessible to the lookup class.
         * The method and all its argument types must be accessible to the lookup class.
         * The type of the method handle will be that of the method.
         * The given receiver will be bound into the method handle.
         * <p>
         * Equivalent to the following expression:
         * <code>
         * {@link #insertArgument}({@link #findVirtual}(defc, name, type), receiver)
         * </code>
         * @param receiver the object from which the method is accessed
         * @param name the name of the method
         * @param type the type of the method, with the receiver argument omitted
         * @return the desired method handle
         * @exception SecurityException <em>TBD</em>
         * @exception NoAccessException if the method does not exist or access checking fails
         */
        public MethodHandle bind(Object receiver, String name, MethodType type) throws NoAccessException {
            Class<? extends Object> rcvc = receiver.getClass(); // may get NPE
            MemberName reference = new MemberName(rcvc, name, type);
            MemberName method = IMPL_NAMES.resolveOrFail(reference, true, lookupClass);
            checkStatic(false, method, lookupClass);
            MethodHandle dmh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClass);
            MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, dmh, receiver);
            if (bmh == null)
                throw newNoAccessException(method, lookupClass);
            return bmh;
        }

        /**
         * Make a direct method handle to <i>m</i>, if the lookup class has permission.
         * If <i>m</i> is non-static, the receiver argument is treated as an initial argument.
         * If <i>m</i> is virtual, overriding is respected on every call.
         * Unlike the Core Reflection API, exceptions are <em>not</em> wrapped.
         * The type of the method handle will be that of the method,
         * with the receiver type prepended (but only if it is non-static).
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * If <i>m</i> is not public, do not share the resulting handle with untrusted parties.
         * @param m the reflected method
         * @return a method handle which can invoke the reflected method
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflect(Method m) throws NoAccessException {
            return unreflectImpl(new MemberName(m), m.isAccessible(), true, lookupClass);
        }

        /**
         * Produce a method handle for a reflected method.
         * It will bypass checks for overriding methods on the receiver,
         * as if by the {@code invokespecial} instruction.
         * The type of the method handle will be that of the method,
         * with the receiver type prepended.
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class,
         * as if {@code invokespecial} instruction were being linked.
         * @param m the reflected method
         * @return a method handle which can invoke the reflected method
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectSpecial(Method m, Class<?> specialCaller) throws NoAccessException {
            checkSpecialCaller(specialCaller, lookupClass);
            MemberName mname = new MemberName(m);
            checkStatic(false, mname, lookupClass);
            return unreflectImpl(mname, m.isAccessible(), false, specialCaller);
        }

        /**
         * Produce a method handle for a reflected constructor.
         * The type of the method handle will be that of the constructor.
         * The method handle will perform a {@code newInstance} operation,
         * creating a new instance of the constructor's class on the
         * arguments passed to the method handle.
         * <p>
         * If the constructor's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class,
         * as if {@code invokespecial} instruction were being linked.
         * @param ctor the reflected constructor
         * @return a method handle which can invoke the reflected constructor
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectConstructor(Constructor ctor) throws NoAccessException {
            MemberName m = new MemberName(ctor);
            return unreflectImpl(m, ctor.isAccessible(), false, lookupClass);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
         * Produce a method handle giving read access to a reflected field.
         * The type of the method handle will have a return type of the field's
         * value type.  Its sole argument will be the field's containing class
         * (but only if it is non-static).
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can load values from the reflected field
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectGetter(Field f) throws NoAccessException {
            return MethodHandleImpl.accessField(IMPL_TOKEN, new MemberName(f), false, lookupClass);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
         * Produce a method handle giving write access to a reflected field.
         * The type of the method handle will have a void return type.
         * Its last argument will be the field's value type.
         * Its other argument will be the field's containing class
         * (but only if it is non-static).
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can store values into the reflected field
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectSetter(Field f) throws NoAccessException {
            return MethodHandleImpl.accessField(IMPL_TOKEN, new MemberName(f), true, lookupClass);
        }

    }

    static /*must not be public*/
    MethodHandle findStaticFrom(Class<?> lookupClass,
                                Class<?> defc, String name, MethodType type) throws NoAccessException {
        MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type, Modifier.STATIC), true, lookupClass);
        checkStatic(true, method, lookupClass);
        return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookupClass);
    }

    static void checkStatic(boolean wantStatic, MemberName m, Class<?> lookupClass) {
        if (wantStatic != m.isStatic()) {
            String message = wantStatic ? "expected a static method" : "expected a non-static method";
            throw newNoAccessException(message, m, lookupClass);
        }
    }

    static void checkSpecialCaller(Class<?> specialCaller, Class<?> lookupClass) {
        if (lookupClass == Lookup.IMPL_LOOKUP.lookupClass())
            return;  // privileged action
        if (lookupClass == null ||  // public-only access
            !VerifyAccess.isSamePackageMember(specialCaller, lookupClass))
            throw newNoAccessException("no private access", new MemberName(specialCaller), lookupClass);
    }

    // Helper for creating handles on reflected methods and constructors.
    static MethodHandle unreflectImpl(MemberName m, boolean isAccessible,
                                      boolean doDispatch, Class<?> lookupClass) {
        MethodType mtype = m.getInvocationType();
        Class<?> defc = m.getDeclaringClass();
        int mods = m.getModifiers();
        if (m.isStatic()) {
            if (!isAccessible &&
                    VerifyAccess.isAccessible(defc, mods, false, lookupClass) == null)
                throw newNoAccessException(m, lookupClass);
        } else {
            Class<?> constraint;
            if (isAccessible) {
                // abbreviated access check for "unlocked" method
                constraint = doDispatch ? defc : lookupClass;
            } else {
                constraint = VerifyAccess.isAccessible(defc, mods, doDispatch, lookupClass);
            }
            if (constraint != defc && !constraint.isAssignableFrom(defc)) {
                if (!defc.isAssignableFrom(constraint))
                    throw newNoAccessException("receiver must be in caller class", m, lookupClass);
                mtype = mtype.changeParameterType(0, constraint);
            }
        }
        return MethodHandleImpl.findMethod(IMPL_TOKEN, m, doDispatch, lookupClass);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle giving read access to elements of an array.
     * The type of the method handle will have a return type of the array's
     * element type.  Its first argument will be the array type,
     * and the second will be {@code int}.
     * @param arrayClass an array type
     * @return a method handle which can load values from the given array type
     * @throws  IllegalArgumentException if arrayClass is not an array type
     */
    public static
    MethodHandle arrayElementGetter(Class<?> arrayClass) throws IllegalArgumentException {
        return MethodHandleImpl.accessArrayElement(IMPL_TOKEN, arrayClass, false);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle giving write access to elements of an array.
     * The type of the method handle will have a void return type.
     * Its last argument will be the array's element type.
     * The first and second arguments will be the array type and int.
     * @return a method handle which can store values into the array type
     * @throws IllegalArgumentException if arrayClass is not an array type
     */
    public static
    MethodHandle arrayElementSetter(Class<?> arrayClass) throws IllegalArgumentException {
        return MethodHandleImpl.accessArrayElement(IMPL_TOKEN, arrayClass, true);
    }


    /// method handle invocation (reflective style)

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Call the {@code invoke} method of a given method handle,
     * with arguments that exactly match the parameter types of the method handle.
     * The length of the arguments array must equal the parameter count
     * of the target's type.
     * The arguments array is spread into separate arguments, and
     * basic reference and unboxing conversions are applied.
     * <p>
     * In order to match the type of the target, the following argument
     * conversions are applied as necessary:
     * <ul>
     * <li>reference casting
     * <li>unboxing
     * </ul>
     * The following conversions are not applied:
     * <ul>
     * <li>primitive conversions (e.g., {@code byte} to {@code int}
     * <li>varargs conversions other than the initial spread
     * <li>any application-specific conversions (e.g., string to number)
     * </ul>
     * The result returned by the call is boxed if it is a primitive,
     * or forced to null if the return type is void.
     * <p>
     * This call is a convenience method for the following code:
     * <pre>
     *   MethodHandle invoker = MethodHandles.genericInvoker(target.type(), 0, true);
     *   Object result = invoker.invoke(arguments);
     * </pre>
     * @param target the method handle to invoke
     * @param arguments the arguments to pass to the target
     * @return the result returned by the target
     */
    public static
    Object invoke(MethodHandle target, Object... arguments) {
        int argc = arguments == null ? 0 : arguments.length;
        MethodType type = target.type();
        if (argc <= 4) {
            MethodHandle invoker = invokers(type).genericInvoker();
            switch (argc) {
                case 0:  return invoker.<Object>invoke(target);
                case 1:  return invoker.<Object>invoke(target,
                                    arguments[0]);
                case 2:  return invoker.<Object>invoke(target,
                                    arguments[0], arguments[1]);
                case 3:  return invoker.<Object>invoke(target,
                                    arguments[0], arguments[1], arguments[2]);
                case 4:  return invoker.<Object>invoke(target,
                                    arguments[0], arguments[1], arguments[2], arguments[3]);
            }
        }
        MethodHandle invoker = invokers(type).varargsInvoker();
        return invoker.<Object>invoke(target, arguments);
    }

    public static
    Object invoke_0(MethodHandle target) {
        MethodHandle invoker = invokers(target.type()).genericInvoker();
        return invoker.<Object>invoke(target);
    }
    public static
    Object invoke_1(MethodHandle target, Object a0) {
        MethodHandle invoker = invokers(target.type()).genericInvoker();
        return invoker.<Object>invoke(target, a0);
    }
    public static
    Object invoke_2(MethodHandle target, Object a0, Object a1) {
        MethodHandle invoker = invokers(target.type()).genericInvoker();
        return invoker.<Object>invoke(target, a0, a1);
    }
    public static
    Object invoke_3(MethodHandle target, Object a0, Object a1, Object a2) {
        MethodHandle invoker = invokers(target.type()).genericInvoker();
        return invoker.<Object>invoke(target, a0, a1, a2);
    }
    public static
    Object invoke_4(MethodHandle target, Object a0, Object a1, Object a2, Object a3) {
        MethodHandle invoker = invokers(target.type()).genericInvoker();
        return invoker.<Object>invoke(target, a0, a1, a2, a3);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Give a method handle which will invoke any method handle of the
     * given type on a standard set of {@code Object} type arguments.
     * The the resulting invoker will be a method handle with the following
     * arguments:
     * <ul>
     * <li>a single {@code MethodHandle} target
     * <li>zero or more {@code Object} values
     * <li>an optional {@code Object[]} array containing more arguments
     * </ul>
     * The invoker will spread the varargs array (if present), apply
     * reference casts as necessary, and unbox primitive arguments.
     * The return value of the invoker will be an {@code Object} reference,
     * boxing a primitive value if the original type returns a primitive,
     * and always null if the original type returns void.
     * <p>
     * This is a convenience method equivalent to the following code:
     * <pre>
     * MethodHandle invoker = exactInvoker(type);
     * MethodType genericType = MethodType.makeGeneric(objectArgCount, varargs);
     * genericType = genericType.insertParameterType(0, MethodHandle.class);
     * if (!varargs)
     *     return convertArguments(invoker, genericType);
     * else
     *     return spreadArguments(invoker, genericType);
     * </pre>
     * @param type the desired target type
     * @param objectArgCount number of fixed (non-varargs) {@code Object} arguments
     * @param varargs if true, the invoker will accept a final {@code Object[]} argument
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle genericInvoker(MethodType type, int objectArgCount, boolean varargs) {
        return invokers(type).genericInvoker();
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Give a method handle which will take a invoke any method handle of the
     * given type.  The resulting invoker will have a type which is
     * exactly equal to the desired type, except that it will accept
     * an additional leading argument of type {@code MethodHandle}.
     * <p>
     * This is a convenience method equivalent to the following code:
     * <pre>
     *     MethodHandles.lookup().findVirtual(MethodHandle.class, "invoke", type);
     * </pre>
     * @param type the desired target type
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle exactInvoker(MethodType type) {
        return invokers(type).exactInvoker();
    }

    static private Invokers invokers(MethodType type) {
        return MethodTypeImpl.invokers(IMPL_TOKEN, type);
    }

    /**
     * <em>WORK IN PROGRESS:</em>
     * Perform value checking, exactly as if for an adapted method handle.
     * It is assumed that the given value is either null, of type T0,
     * or (if T0 is primitive) of the wrapper type corresponding to T0.
     * The following checks and conversions are made:
     * <ul>
     * <li>If T0 and T1 are references, then a cast to T1 is applied.
     *     (The types do not need to be related in any particular way.)
     * <li>If T0 and T1 are primitives, then a widening or narrowing
     *     conversion is applied, if one exists.
     * <li>If T0 is a primitive and T1 a reference, and
     *     T0 has a wrapper type TW, a boxing conversion to TW is applied,
     *     possibly followed by a reference conversion.
     *     T1 must be TW or a supertype.
     * <li>If T0 is a reference and T1 a primitive, and
     *     T1 has a wrapper type TW, an unboxing conversion is applied,
     *     possibly preceded by a reference conversion.
     *     T0 must be TW or a supertype.
     * <li>If T1 is void, the return value is discarded
     * <li>If T0 is void and T1 a reference, a null value is introduced.
     * <li>If T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * If the value is discarded, null will be returned.
     * @param valueType
     * @param value
     * @return the value, converted if necessary
     * @throws java.lang.ClassCastException if a cast fails
     */
    static
    <T0, T1> T1 checkValue(Class<T0> t0, Class<T1> t1, Object value)
       throws ClassCastException
    {
        if (t0 == t1) {
            // no conversion needed; just reassert the same type
            if (t0.isPrimitive())
                return Wrapper.asPrimitiveType(t1).cast(value);
            else
                return Wrapper.OBJECT.cast(value, t1);
        }
        boolean prim0 = t0.isPrimitive(), prim1 = t1.isPrimitive();
        if (!prim0) {
            // check contract with caller
            Wrapper.OBJECT.cast(value, t0);
            if (!prim1) {
                return Wrapper.OBJECT.cast(value, t1);
            }
            // convert reference to primitive by unboxing
            Wrapper w1 = Wrapper.forPrimitiveType(t1);
            return w1.cast(value, t1);
        }
        // check contract with caller:
        Wrapper.asWrapperType(t0).cast(value);
        Wrapper w1 = Wrapper.forPrimitiveType(t1);
        return w1.cast(value, t1);
    }

    static
    Object checkValue(Class<?> T1, Object value)
       throws ClassCastException
    {
        Class<?> T0;
        if (value == null)
            T0 = Object.class;
        else
            T0 = value.getClass();
        return checkValue(T0, T1, value);
    }

    /// method handle modification (creation from other method handles)

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts the type of the
     * given method handle to a new type, by pairwise argument conversion,
     * and/or varargs conversion.
     * The original type and new type must have the same number of
     * arguments, or else one or both them the must be varargs types.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type, with any varargs property erased.
     * <p>
     * If the original type and new type are equal, returns target.
     * <p>
     * The following conversions are applied as needed both to
     * arguments and return types.  Let T0 and T1 be the differing
     * new and old parameter types (or old and new return types)
     * for corresponding values passed by the new and old method types.
     * <p>
     * If an ordinary (non-varargs) parameter of the new type is
     * to be boxed in a varargs parameter of the old type of type T1[],
     * then T1 is the element type of the varargs array.
     * Otherwise, if a varargs parameter of the new type of type T0[]
     * is to be spread into one or more outgoing old type parameters,
     * then T0 is the element type of the
     * If the new type is varargs and the old type is not, the varargs
     * argument will be checked and must be a non-null array of exactly
     * the right length.  If there are no parameters in the old type
     * corresponding to the new varargs parameter, the varargs argument
     * is also allowed to be null.
     * <p>
     * Given those types T0, T1, one of the following conversions is applied
     * if possible:
     * <ul>
     * <li>If T0 and T1 are references, then a cast to T2 is applied,
     *     where T2 is Object if T1 is an interface, else T1.
     *     (The types do not need to be related in any particular way.
     *     The treatment of interfaces follows the usage of the bytecode verifier.)
     * <li>If T0 and T1 are primitives, then a Java casting
     *     conversion (JLS 5.5) is applied, if one exists.
     * <li>If T0 and T1 are primitives and one is boolean,
     *     the boolean is treated as a one-bit unsigned integer.
     *     (This treatment follows the usage of the bytecode verifier.)
     *     A conversion from another primitive type behaves as if
     *     it first converts to byte, and then masks all but the low bit.
     * <li>If T0 is a primitive and T1 a reference, a boxing
     *     conversion is applied if one exists, possibly followed by
     *     an reference conversion to a superclass.
     *     T1 must be a wrapper class or a supertype of one.
     *     If T1 is a wrapper class, T0 is converted if necessary
     *     to T1's primitive type by one of the preceding conversions.
     *     Otherwise, T0 is boxed, and its wrapper converted to T1.
     * <li>If T0 is a reference and T1 a primitive, an unboxing
     *     conversion is applied if one exists, possibly preceded by
     *     a reference conversion to a wrapper class.
     *     T0 must be a wrapper class or a supertype of one.
     *     If T0 is a wrapper class, its primitive value is converted
     *     if necessary to T1 by one of the preceding conversions.
     *     Otherwise, T0 is converted directly to the wrapper type for T1,
     *     which is then unboxed.
     * <li>If T1 is void, any returned value is discarded
     * <li>If T0 is void and T1 a reference, a null value is introduced.
     * <li>If T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * @param target the method handle to invoke after arguments are retyped
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code target} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws WrongMethodTypeException if the conversion cannot be made
     */
    public static
    MethodHandle convertArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        if (oldType.equals(newType))
            return target;
        MethodHandle res = MethodHandleImpl.convertArguments(IMPL_TOKEN, target,
                                                 newType, oldType, null);
        if (res == null)
            throw newIllegalArgumentException("cannot convert to "+newType+": "+target);
        return res;
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts the calling sequence of the
     * given method handle to a new type, by reordering the arguments.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * The given array controls the reordering.
     * Call {@code #I} the number of incoming parameters (the value
     * {@code newType.parameterCount()}, and call {@code #O} the number
     * of outgoing parameters (the value {@code target.type().parameterCount()}).
     * Then the length of the reordering array must be {@code #O},
     * and each element must be a non-negative number less than {@code #I}.
     * For every {@code N} less than {@code #O}, the {@code N}-th
     * outgoing argument will be taken from the {@code I}-th incoming
     * argument, where {@code I} is {@code reorder[N]}.
     * <p>
     * The reordering array need not specify an actual permutation.
     * An incoming argument will be duplicated if its index appears
     * more than once in the array, and an incoming argument will be dropped
     * if its index does not appear in the array.
     * <p>
     * Pairwise conversions are applied as needed to arguments and return
     * values, as with {@link #convertArguments}.
     * @param target the method handle to invoke after arguments are reordered
     * @param newType the expected type of the new method handle
     * @param reorder a string which controls the reordering
     * @return a method handle which delegates to {@code target} after performing
     *           any necessary argument motion and conversions, and arranges for any
     *           necessary return value conversions
     */
    public static
    MethodHandle permuteArguments(MethodHandle target, MethodType newType, int[] reorder) {
        MethodType oldType = target.type();
        checkReorder(reorder, newType, oldType);
        return MethodHandleImpl.convertArguments(IMPL_TOKEN, target,
                                                 newType, oldType,
                                                 reorder);
    }

    private static void checkReorder(int[] reorder, MethodType newType, MethodType oldType) {
        if (reorder.length == oldType.parameterCount()) {
            int limit = newType.parameterCount();
            boolean bad = false;
            for (int i : reorder) {
                if (i < 0 || i >= limit) {
                    bad = true; break;
                }
            }
            if (!bad)  return;
        }
        throw newIllegalArgumentException("bad reorder array");
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts the type of the
     * given method handle to a new type, by spreading the final argument.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * The final parameter type of the new type must be an array type T[].
     * This is the type of what is called the <i>spread</i> argument.
     * All other arguments of the new type are called <i>ordinary</i> arguments.
     * <p>
     * The ordinary arguments of the new type are pairwise converted
     * to the initial parameter types of the old type, according to the
     * rules in {@link #convertArguments}.
     * Any additional arguments in the old type
     * are converted from the array element type T,
     * again according to the rules in {@link #convertArguments}.
     * The return value is converted according likewise.
     * <p>
     * The call verifies that the spread argument is in fact an array
     * of exactly the type length, i.e., the excess number of
     * arguments in the old type over the ordinary arguments in the new type.
     * If there are no excess arguments, the spread argument is also
     * allowed to be null.
     * @param target the method handle to invoke after the argument is prepended
     * @param newType the expected type of the new method handle
     * @return a new method handle which spreads its final argument,
     *         before calling the original method handle
     */
    public static
    MethodHandle spreadArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        int inargs  = newType.parameterCount();
        int outargs = oldType.parameterCount();
        int spreadPos = inargs - 1;
        int numSpread = (outargs - spreadPos);
        MethodHandle res = null;
        if (spreadPos >= 0 && numSpread >= 0) {
            res = MethodHandleImpl.spreadArguments(IMPL_TOKEN, target, newType, spreadPos);
        }
        if (res == null) {
            throw newIllegalArgumentException("cannot spread "+newType+" to " +oldType);
        }
        return res;
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which adapts the type of the
     * given method handle to a new type, by collecting a series of
     * trailing arguments into an array.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * This method is inverse to {@link #spreadArguments}.
     * The final parameter type of the old type must be an array type T[],
     * which is the type of what is called the <i>spread</i> argument.
     * The trailing arguments of the new type which correspond to
     * the spread argument are all converted to type T and collected
     * into an array before the original method is called.
     * <p>
     * ISSUE: Unify this with combineArguments.  CollectArguments
     * is combineArguments with (a) new Object[]{...} as a combiner,
     * and (b) the combined arguments dropped, in favor of the combined result.
     * @param target the method handle to invoke after the argument is prepended
     * @param newType the expected type of the new method handle
     * @return a new method handle which collects some trailings argument
     *         into an array, before calling the original method handle
     */
    public static
    MethodHandle collectArguments(MethodHandle target, MethodType newType) {
        MethodType oldType = target.type();
        int inargs  = newType.parameterCount();
        int outargs = oldType.parameterCount();
        int collectPos = outargs - 1;
        int numCollect = (inargs - collectPos);
        if (collectPos < 0 || numCollect < 0)
            throw newIllegalArgumentException("wrong number of arguments");
        return MethodHandleImpl.collectArguments(IMPL_TOKEN, target, newType, collectPos);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which calls the original method handle,
     * after inserting the given argument at the given position.
     * The type of the new method handle will drop the corresponding argument
     * type from the original handle's type.
     * <p>
     * The given argument object must match the dropped argument type.
     * If the dropped argument type is a primitive, the argument object
     * must be a wrapper, and is unboxed to produce the primitive.
     * <p>
     * The  <i>pos</i> may range between zero and <i>N</i> (inclusively),
     * where <i>N</i> is the number of argument types in <i>target</i>,
     * meaning to insert the new argument as the first or last (respectively),
     * or somewhere in between.
     * @param target the method handle to invoke after the argument is inserted
     * @param pos where to insert the argument (zero for the first)
     * @param value the argument to insert
     * @return a new method handle which inserts an additional argument,
     *         before calling the original method handle
     */
    public static
    MethodHandle insertArgument(MethodHandle target, int pos, Object value) {
        MethodType oldType = target.type();
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        int outargs = oldType.parameterCount();
        int inargs  = outargs - 1;
        if (pos < 0 || pos >= outargs)
            throw newIllegalArgumentException("no argument type to append");
        Class<?> valueType = ptypes.remove(pos);
        value = checkValue(valueType, value);
        if (pos == 0 && !valueType.isPrimitive()) {
            // At least for now, make bound method handles a special case.
            // This lets us get by with minimal JVM support, at the expense
            // of generating signature-specific adapters as Java bytecodes.
            MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, target, value);
            if (bmh != null)  return bmh;
            // else fall through to general adapter machinery
        }
        return MethodHandleImpl.bindArgument(IMPL_TOKEN, target, pos, value);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which calls the original method handle,
     * after dropping the given argument(s) at the given position.
     * The type of the new method handle will insert the given argument
     * type(s), at that position, into the original handle's type.
     * <p>
     * The <i>pos</i> may range between zero and <i>N-1</i>,
     * where <i>N</i> is the number of argument types in <i>target</i>,
     * meaning to drop the first or last argument (respectively),
     * or an argument somewhere in between.
     * @param target the method handle to invoke after the argument is dropped
     * @param valueTypes the type(s) of the argument to drop
     * @param pos which argument to drop (zero for the first)
     * @return a new method handle which drops an argument of the given type,
     *         before calling the original method handle
     */
    public static
    MethodHandle dropArguments(MethodHandle target, int pos, Class<?>... valueTypes) {
        if (valueTypes.length == 0)  return target;
        MethodType oldType = target.type();
        int outargs = oldType.parameterCount();
        int inargs  = outargs + valueTypes.length;
        if (pos < 0 || pos >= inargs)
            throw newIllegalArgumentException("no argument type to remove");
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        ptypes.addAll(pos, Arrays.asList(valueTypes));
        MethodType newType = MethodType.make(oldType.returnType(), ptypes);
        return MethodHandleImpl.dropArguments(IMPL_TOKEN, target, newType, pos);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Make a method handle which adapts a target method handle,
     * by guarding it with a test, a boolean-valued method handle.
     * If the guard fails, a fallback handle is called instead.
     * All three method handles must have the same corresponding
     * argument and return types, except that the return type
     * of the test must be boolean.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * signature T(A...);
     * boolean test(A...);
     * T target(A...);
     * T fallback(A...);
     * T adapter(A... a) {
     *   if (test(a...))
     *     return target(a...);
     *   else
     *     return fallback(a...);
     * }
     * </pre></blockquote>
     * @param test method handle used for test, must return boolean
     * @param target method handle to call if test passes
     * @param fallback method handle to call if test fails
     * @return method handle which incorporates the specified if/then/else logic
     * @throws IllegalArgumentException if {@code test} does not return boolean,
     *          or if all three method types do not match (with the return
     *          type of {@code test} changed to match that of {@code target}).
     */
    public static
    MethodHandle guardWithTest(MethodHandle test,
                               MethodHandle target,
                               MethodHandle fallback) {
        if (target.type() != fallback.type())
            throw newIllegalArgumentException("target and fallback types do not match");
        if (target.type().changeReturnType(boolean.class) != test.type())
            throw newIllegalArgumentException("target and test types do not match");
        /* {
            MethodHandle invoke = findVirtual(MethodHandle.class, "invoke", target.type());
            static MethodHandle choose(boolean z, MethodHandle t, MethodHandle f) {
                return z ? t : f;
            }
            static MethodHandle compose(MethodHandle f, MethodHandle g) {
                Class<?> initargs = g.type().parameterArray();
                f = dropArguments(f, 1, initargs);  // ignore 2nd copy of args
                return combineArguments(f, g);
            }
            // choose = \z.(z ? target : fallback)
            MethodHandle choose = findVirtual(MethodHandles.class, "choose",
                    MethodType.make(boolean.class, MethodHandle.class, MethodHandle.class));
            choose = appendArgument(choose, target);
            choose = appendArgument(choose, fallback);
            MethodHandle dispatch = compose(choose, test);
            // dispatch = \(a...).(test(a...) ? target : fallback)
            return combineArguments(invoke, dispatch, 0);
            // return \(a...).((test(a...) ? target : fallback).invoke(a...))
        } */
        return MethodHandleImpl.makeGuardWithTest(IMPL_TOKEN, test, target, fallback);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Adapt a target method handle {@code target} by first processing
     * its arguments, and then calling the target.
     * The initial processing is performed by a second method handle, the {@code combiner}.
     * After this, control passes to the {@code target}, with the same arguments.
     * <p>
     * The return value of the {@code combiner} is inserted into the argument list
     * for the {@code target} at the indicated position {@code pos}, if it is non-negative.
     * Except for this inserted argument (if any), the argument types of
     * the target {@code target} and the {@code combiner} must be identical.
     * <p>
     * (Note that {@link #dropArguments} can be used to remove any arguments
     * that either the {@code combiner} or {@code target} does not wish to receive.)
     * <p>
     * The combiner handle must have the same argument types as the
     * target handle, but must return {@link MethodHandle} instead of
     * the ultimate return type.  The returned method handle, in turn,
     * is required to have exactly the given final method type.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * signature V(A[pos]..., B...);
     * signature T(A[pos]..., V, B...);
     * T target(A... a, V v, B... b);
     * V combiner(A..., B...);
     * T adapter(A... a, B... b) {
     *   V v = combiner(a..., b...);
     *   return target(a..., v, b...);
     * }
     * </pre></blockquote>
     * @param target the method handle to invoke after arguments are combined
     * @param pos where the return value of {@code combiner} is to
     *          be inserted as an argument to {@code target}
     * @param combiner method handle to call initially on the incoming arguments
     * @return method handle which incorporates the specified dispatch logic
     * @throws IllegalArgumentException if {@code combiner} does not itself
     *          return either void or the {@code pos}-th argument of {@code target},
     *          or does not have the same argument types as {@code target}
     *          (minus the inserted argument)
     */
    public static
    MethodHandle combineArguments(MethodHandle target, int pos, MethodHandle combiner) {
        MethodType mhType = target.type();
        Class<?> combineType = combiner.type().returnType();
        MethodType incomingArgs;
        if (pos < 0) {
            // No inserted argument; target & combiner must have same argument types.
            incomingArgs = mhType;
            if (!incomingArgs.changeReturnType(combineType).equals(combiner.type()))
                throw newIllegalArgumentException("target and combiner types do not match");
        } else {
            // Inserted argument.
            if (pos >= mhType.parameterCount()
                || mhType.parameterType(pos) != combineType)
                throw newIllegalArgumentException("inserted combiner argument does not match target");
            incomingArgs = mhType.dropParameterType(pos);
        }
        if (!incomingArgs.changeReturnType(combineType).equals(combiner.type())) {
            throw newIllegalArgumentException("target and combiner types do not match");
        }
        return MethodHandleImpl.combineArguments(IMPL_TOKEN, target, combiner, pos);
    }

}
