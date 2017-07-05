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
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import sun.dyn.Invokers;
import sun.dyn.MethodTypeImpl;
import sun.reflect.Reflection;
import static sun.dyn.MemberName.newIllegalArgumentException;
import static sun.dyn.MemberName.newNoAccessException;

/**
 * Fundamental operations and utilities for MethodHandle.
 * They fall into several categories:
 * <ul>
 * <li>Reifying methods and fields.  This is subject to access checks.
 * <li>Invoking method handles on dynamically typed arguments and/or varargs arrays.
 * <li>Combining or transforming pre-existing method handles into new ones.
 * <li>Miscellaneous emulation of common JVM operations or control flow patterns.
 * </ul>
 * <p>
 * @author John Rose, JSR 292 EG
 */
public class MethodHandles {

    private MethodHandles() { }  // do not instantiate

    private static final Access IMPL_TOKEN = Access.getToken();
    private static final MemberName.Factory IMPL_NAMES = MemberName.getFactory(IMPL_TOKEN);
    static { MethodHandleImpl.initStatics(); }
    // See IMPL_LOOKUP below.

    //// Method handle creation from ordinary methods.

    /** Create a {@link Lookup} lookup object on the caller.
     *
     */
    public static Lookup lookup() {
        return new Lookup();
    }

    /** Version of lookup which is trusted minimally.
     *  It can only be used to create method handles to
     *  publicly accessible members.
     */
    public static Lookup publicLookup() {
        return Lookup.PUBLIC_LOOKUP;
    }

    /**
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
         *  This value is null if and only if this lookup was produced
         *  by {@link MethodHandles#publicLookup}.
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
            this(IMPL_TOKEN, getCallerClassAtEntryPoint());
        }

        Lookup(Access token, Class<?> lookupClass) {
            // make sure we haven't accidentally picked up a privileged class:
            checkUnprivilegedlookupClass(lookupClass);
            this.lookupClass = lookupClass;
        }

        /**
         * Create a lookup on the specified class.
         * The result is guaranteed to have no more access privileges
         * than the original.
         */
        public Lookup in(Class<?> newLookupClass) {
            if (this == PUBLIC_LOOKUP)  return PUBLIC_LOOKUP;
            if (newLookupClass == null)  return PUBLIC_LOOKUP;
            if (newLookupClass == lookupClass)  return this;
            if (this != IMPL_LOOKUP) {
                if (!VerifyAccess.isSamePackage(lookupClass, newLookupClass))
                    throw newNoAccessException(new MemberName(newLookupClass), this);
                checkUnprivilegedlookupClass(newLookupClass);
            }
            return new Lookup(newLookupClass);
        }

        private Lookup(Class<?> lookupClass) {
            this.lookupClass = lookupClass;
        }

        // Make sure outer class is initialized first.
        static { IMPL_TOKEN.getClass(); }

        private static final Class<?> PUBLIC_ONLY = sun.dyn.empty.Empty.class;

        /** Version of lookup which is trusted minimally.
         *  It can only be used to create method handles to
         *  publicly accessible members.
         */
        static final Lookup PUBLIC_LOOKUP = new Lookup(PUBLIC_ONLY);

        /** Package-private version of lookup which is trusted. */
        static final Lookup IMPL_LOOKUP = new Lookup(null);
        static { MethodHandleImpl.initLookup(IMPL_TOKEN, IMPL_LOOKUP); }

        private static void checkUnprivilegedlookupClass(Class<?> lookupClass) {
            String name = lookupClass.getName();
            if (name.startsWith("java.dyn.") || name.startsWith("sun.dyn."))
                throw newIllegalArgumentException("illegal lookupClass: "+lookupClass);
        }

        @Override
        public String toString() {
            if (lookupClass == PUBLIC_ONLY)
                return "public";
            if (lookupClass == null)
                return "privileged";
            return lookupClass.getName();
        }

        // call this from an entry point method in Lookup with extraFrames=0.
        private static Class<?> getCallerClassAtEntryPoint() {
            final int CALLER_DEPTH = 4;
            // 0: Reflection.getCC, 1: getCallerClassAtEntryPoint,
            // 2: Lookup.<init>, 3: MethodHandles.*, 4: caller
            // Note:  This should be the only use of getCallerClass in this file.
            assert(Reflection.getCallerClass(CALLER_DEPTH-1) == MethodHandles.class);
            return Reflection.getCallerClass(CALLER_DEPTH);
        }

        /**
         * Produce a method handle for a static method.
         * The type of the method handle will be that of the method.
         * (Since static methods do not take receivers, there is no
         * additional receiver argument inserted into the method handle type,
         * as there would be with {@linkplain #findVirtual} or {@linkplain #findSpecial}.)
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
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type, Modifier.STATIC), true, lookupClass());
            VerifyAccess.checkName(method, this);
            checkStatic(true, method, this);
            //throw NoSuchMethodException
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookupClass());
        }

        /**
         * Produce a method handle for a virtual method.
         * The type of the method handle will be that of the method,
         * with the receiver type ({@code defc}) prepended.
         * The method and all its argument types must be accessible to the lookup class.
         * <p>
         * (<em>BUG NOTE:</em> The type {@code Object} may be prepended instead
         * of the receiver type, if the receiver type is not on the boot class path.
         * This is due to a temporary JVM limitation, in which MethodHandle
         * claims to be unable to access such classes.  To work around this
         * bug, use {@code convertArguments} to normalize the type of the leading
         * argument to a type on the boot class path, such as {@code Object}.)
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
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type), true, lookupClass());
            VerifyAccess.checkName(method, this);
            checkStatic(false, method, this);
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClass());
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
            checkSpecialCaller(specialCaller, this);
            Lookup slookup = this.in(specialCaller);
            MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type), false, slookup.lookupClass());
            VerifyAccess.checkName(method, this);
            checkStatic(false, method, this);
            if (name.equals("<init>")) {
                throw newNoAccessException("cannot directly invoke a constructor", method, null);
            } else if (defc.isInterface() || !defc.isAssignableFrom(specialCaller)) {
                throw newNoAccessException("method must be in a superclass of lookup class", method, slookup.lookupClass());
            }
            return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, slookup.lookupClass());
        }

        /**
         * Produce an early-bound method handle for a non-static method.
         * The receiver must have a supertype {@code defc} in which a method
         * of the given name and type is accessible to the lookup class.
         * The method and all its argument types must be accessible to the lookup class.
         * The type of the method handle will be that of the method,
         * without any insertion of an additional receiver parameter.
         * The given receiver will be bound into the method handle,
         * so that every call to the method handle will invoke the
         * requested method on the given receiver.
         * <p>
         * This is equivalent to the following expression:
         * <code>
         * {@link #insertArguments}({@link #findVirtual}(defc, name, type), receiver)
         * </code>
         * where {@code defc} is either {@code receiver.getClass()} or a super
         * type of that class, in which the requested method is accessible
         * to the lookup class.
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
            MemberName method = IMPL_NAMES.resolveOrFail(reference, true, lookupClass());
            VerifyAccess.checkName(method, this);
            checkStatic(false, method, this);
            MethodHandle dmh = MethodHandleImpl.findMethod(IMPL_TOKEN, method, true, lookupClass());
            MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, dmh, receiver);
            if (bmh == null)
                throw newNoAccessException(method, this);
            return bmh;
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
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
            return unreflectImpl(new MemberName(m), m.isAccessible(), true, false, this);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
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
            checkSpecialCaller(specialCaller, this);
            Lookup slookup = this.in(specialCaller);
            MemberName mname = new MemberName(m);
            checkStatic(false, mname, this);
            return unreflectImpl(mname, m.isAccessible(), false, false, slookup);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
         * Produce a method handle for a reflected constructor.
         * The type of the method handle will be that of the constructor,
         * with the return type changed to the declaring class.
         * The method handle will perform a {@code newInstance} operation,
         * creating a new instance of the constructor's class on the
         * arguments passed to the method handle.
         * <p>
         * If the constructor's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param ctor the reflected constructor
         * @return a method handle which can invoke the reflected constructor
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectConstructor(Constructor ctor) throws NoAccessException {
            MemberName m = new MemberName(ctor);
            return unreflectImpl(m, ctor.isAccessible(), false, false, this);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
         * Produce a method handle giving read access to a reflected field.
         * The type of the method handle will have a return type of the field's
         * value type.
         * If the field is static, the method handle will take no arguments.
         * Otherwise, its single argument will be the instance containing
         * the field.
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can load values from the reflected field
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectGetter(Field f) throws NoAccessException {
            MemberName m = new MemberName(f);
            return unreflectImpl(m, f.isAccessible(), false, false, this);
        }

        /**
         * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
         * Produce a method handle giving write access to a reflected field.
         * The type of the method handle will have a void return type.
         * If the field is static, the method handle will take a single
         * argument, of the field's value type, the value to be stored.
         * Otherwise, the two arguments will be the instance containing
         * the field, and the value to be stored.
         * If the method's {@code accessible} flag is not set,
         * access checking is performed immediately on behalf of the lookup class.
         * @param f the reflected field
         * @return a method handle which can store values into the reflected field
         * @exception NoAccessException if access checking fails
         */
        public MethodHandle unreflectSetter(Field f) throws NoAccessException {
            MemberName m = new MemberName(f);
            return unreflectImpl(m, f.isAccessible(), false, true, this);
        }

    }

    static /*must not be public*/
    MethodHandle findStaticFrom(Lookup lookup,
                                Class<?> defc, String name, MethodType type) throws NoAccessException {
        MemberName method = IMPL_NAMES.resolveOrFail(new MemberName(defc, name, type, Modifier.STATIC), true, lookup.lookupClass());
        VerifyAccess.checkName(method, lookup);
        checkStatic(true, method, lookup);
        return MethodHandleImpl.findMethod(IMPL_TOKEN, method, false, lookup.lookupClass());
    }

    static void checkStatic(boolean wantStatic, MemberName m, Lookup lookup) {
        if (wantStatic != m.isStatic()) {
            String message = wantStatic ? "expected a static method" : "expected a non-static method";
            throw newNoAccessException(message, m, lookup.lookupClass());
        }
    }

    static void checkSpecialCaller(Class<?> specialCaller, Lookup lookup) {
        if (lookup == Lookup.IMPL_LOOKUP)
            return;  // privileged action
        assert(lookup.lookupClass() != null);
        if (!VerifyAccess.isSamePackageMember(specialCaller, lookup.lookupClass()))
            throw newNoAccessException("no private access", new MemberName(specialCaller), lookup.lookupClass());
    }

    // Helper for creating handles on reflected methods and constructors.
    static MethodHandle unreflectImpl(MemberName m, boolean isAccessible,
                                      boolean doDispatch, boolean isSetter, Lookup lookup) {
        MethodType narrowMethodType = null;
        Class<?> defc = m.getDeclaringClass();
        boolean isSpecialInvoke = m.isInvocable() && !doDispatch;
        int mods = m.getModifiers();
        if (m.isStatic()) {
            if (!isAccessible &&
                    VerifyAccess.isAccessible(defc, mods, lookup.lookupClass(), false) == null)
                throw newNoAccessException(m, lookup);
        } else {
            Class<?> constraint;
            if (isAccessible) {
                // abbreviated access check for "unlocked" method
                constraint = doDispatch ? defc : lookup.lookupClass();
            } else {
                constraint = VerifyAccess.isAccessible(defc, mods, lookup.lookupClass(), isSpecialInvoke);
            }
            if (constraint == null) {
                throw newNoAccessException(m, lookup);
            }
            if (constraint != defc && !constraint.isAssignableFrom(defc)) {
                if (!defc.isAssignableFrom(constraint))
                    throw newNoAccessException("receiver must be in caller class", m, lookup.lookupClass());
                if (m.isInvocable())
                    narrowMethodType = m.getInvocationType().changeParameterType(0, constraint);
                else if (m.isField())
                    narrowMethodType = (!isSetter
                                        ? MethodType.methodType(m.getFieldType(), constraint)
                                        : MethodType.methodType(void.class, constraint, m.getFieldType()));
            }
        }
        if (m.isInvocable())
            return MethodHandleImpl.findMethod(IMPL_TOKEN, m, doDispatch, lookup.lookupClass());
        else if (m.isField())
            return MethodHandleImpl.accessField(IMPL_TOKEN, m, isSetter, lookup.lookupClass());
        else
            throw new InternalError();
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
     * @deprecated Alias for MethodHandle.invokeVarargs.
     */
    @Deprecated
    public static
    Object invokeVarargs(MethodHandle target, Object... arguments) throws Throwable {
        return target.invokeVarargs(arguments);
    }

    /**
     * @deprecated Alias for MethodHandle.invokeVarargs.
     */
    @Deprecated
    public static
    Object invoke(MethodHandle target, Object... arguments) throws Throwable {
        return target.invokeVarargs(arguments);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which will invoke any method handle of the
     * given type on a standard set of {@code Object} type arguments.
     * The resulting invoker will be a method handle with the following
     * arguments:
     * <ul>
     * <li>a single {@code MethodHandle} target
     * <li>zero or more {@code Object} values (one for each argument in {@code type})
     * </ul>
     * The invoker will apply reference casts as necessary and unbox primitive arguments,
     * as if by {@link #convertArguments}.
     * The return value of the invoker will be an {@code Object} reference,
     * boxing a primitive value if the original type returns a primitive,
     * and always null if the original type returns void.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
     * MethodHandle invoker = exactInvoker(type);
     * MethodType genericType = type.generic();
     * genericType = genericType.insertParameterType(0, MethodHandle.class);
     * return convertArguments(invoker, genericType);
     * </pre></blockquote>
     * @param type the type of target methods which the invoker will apply to
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle genericInvoker(MethodType type) {
        return invokers(type).genericInvoker();
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which will invoke any method handle of the
     * given type on a standard set of {@code Object} type arguments
     * and a single trailing {@code Object[]} array.
     * The resulting invoker will be a method handle with the following
     * arguments:
     * <ul>
     * <li>a single {@code MethodHandle} target
     * <li>zero or more {@code Object} values (counted by {@code objectArgCount})
     * <li>an {@code Object[]} array containing more arguments
     * </ul>
     * The invoker will spread the varargs array, apply
     * reference casts as necessary, and unbox primitive arguments.
     * The return value of the invoker will be an {@code Object} reference,
     * boxing a primitive value if the original type returns a primitive,
     * and always null if the original type returns void.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
     * MethodHandle invoker = exactInvoker(type);
     * MethodType vaType = MethodType.makeGeneric(objectArgCount, true);
     * vaType = vaType.insertParameterType(0, MethodHandle.class);
     * return spreadArguments(invoker, vaType);
     * </pre></blockquote>
     * @param type the desired target type
     * @param objectArgCount number of fixed (non-varargs) {@code Object} arguments
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle varargsInvoker(MethodType type, int objectArgCount) {
        if (objectArgCount < 0 || objectArgCount > type.parameterCount())
            throw new IllegalArgumentException("bad argument count "+objectArgCount);
        return invokers(type).varargsInvoker(objectArgCount);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which will take a invoke any method handle of the
     * given type.  The resulting invoker will have a type which is
     * exactly equal to the desired type, except that it will accept
     * an additional leading argument of type {@code MethodHandle}.
     * <p>
     * This method is equivalent to the following code (though it may be more efficient):
     * <p><blockquote><pre>
     * lookup().findVirtual(MethodHandle.class, "invoke", type);
     * </pre></blockquote>
     * @param type the desired target type
     * @return a method handle suitable for invoking any method handle of the given type
     */
    static public
    MethodHandle exactInvoker(MethodType type) {
        return invokers(type).exactInvoker();
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle equivalent to an invokedynamic instruction
     * which has been linked to the given call site.
     * Along with {@link Lookup#findVirtual}, {@link Lookup#findStatic},
     * and {@link Lookup#findSpecial}, this completes the emulation
     * of the JVM's {@code invoke} instructions.
     * <p>This method is equivalent to the following code:
     * <p><blockquote><pre>
     * MethodHandle getTarget, invoker, result;
     * getTarget = lookup().bind(site, "getTarget", methodType(MethodHandle.class));
     * invoker = exactInvoker(site.type());
     * result = foldArguments(invoker, getTarget)
     * </pre></blockquote>
     * @return a method handle which always invokes the call site's target
     */
    public static
    MethodHandle dynamicInvoker(CallSite site) {
        MethodHandle getTarget = MethodHandleImpl.bindReceiver(IMPL_TOKEN, CallSite.GET_TARGET, site);
        MethodHandle invoker = exactInvoker(site.type());
        return foldArguments(invoker, getTarget);
    }

    static Invokers invokers(MethodType type) {
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
     * Produce a method handle which adapts the type of the
     * given method handle to a new type by pairwise argument conversion.
     * The original type and new type must have the same number of arguments.
     * The resulting method handle is guaranteed to confess a type
     * which is equal to the desired new type.
     * <p>
     * If the original type and new type are equal, returns target.
     * <p>
     * The following conversions are applied as needed both to
     * arguments and return types.  Let T0 and T1 be the differing
     * new and old parameter types (or old and new return types)
     * for corresponding values passed by the new and old method types.
     * Given those types T0, T1, one of the following conversions is applied
     * if possible:
     * <ul>
     * <li>If T0 and T1 are references, and T1 is not an interface type,
     *     then a cast to T1 is applied.
     *     (The types do not need to be related in any particular way.)
     * <li>If T0 and T1 are references, and T1 is an interface type,
     *     then the value of type T0 is passed as a T1 without a cast.
     *     (This treatment of interfaces follows the usage of the bytecode verifier.)
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
     * <li>If the return type T1 is void, any returned value is discarded
     * <li>If the return type T0 is void and T1 a reference, a null value is introduced.
     * <li>If the return type T0 is void and T1 a primitive, a zero value is introduced.
     * </ul>
     * @param target the method handle to invoke after arguments are retyped
     * @param newType the expected type of the new method handle
     * @return a method handle which delegates to {@code target} after performing
     *           any necessary argument conversions, and arranges for any
     *           necessary return value conversions
     * @throws IllegalArgumentException if the conversion cannot be made
     * @see MethodHandle#asType
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
     * trailing arguments as elements to a single argument array.
     * <p>
     * This method may be used as an inverse to {@link #spreadArguments}.
     * The final parameter type of the old type must be an array type T[],
     * which is the type of what is called the <i>spread</i> argument.
     * The trailing arguments of the new type which correspond to
     * the spread argument are all converted to type T and collected
     * into an array before the original method is called.
     * @param target the method handle to invoke after the argument is prepended
     * @param newType the expected type of the new method handle
     * @return a new method handle which collects some trailing argument
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
        MethodHandle res = MethodHandleImpl.collectArguments(IMPL_TOKEN, target, newType, collectPos, null);
        if (res == null) {
            throw newIllegalArgumentException("cannot collect from "+newType+" to " +oldType);
        }
        return res;
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which calls the original method handle {@code target},
     * after inserting the given argument(s) at the given position.
     * The formal parameters to {@code target} which will be supplied by those
     * arguments are called <em>bound parameters</em>, because the new method
     * will contain bindings for those parameters take from {@code values}.
     * The type of the new method handle will drop the types for the bound
     * parameters from the original target type, since the new method handle
     * will no longer require those arguments to be supplied by its callers.
     * <p>
     * Each given argument object must match the corresponding bound parameter type.
     * If a bound parameter type is a primitive, the argument object
     * must be a wrapper, and will be unboxed to produce the primitive value.
     * <p>
     * The  <i>pos</i> may range between zero and <i>N</i> (inclusively),
     * where <i>N</i> is the number of argument types in resulting method handle
     * (after bound parameter types are dropped).
     * @param target the method handle to invoke after the argument is inserted
     * @param pos where to insert the argument (zero for the first)
     * @param values the series of arguments to insert
     * @return a new method handle which inserts an additional argument,
     *         before calling the original method handle
     */
    public static
    MethodHandle insertArguments(MethodHandle target, int pos, Object... values) {
        int insCount = values.length;
        MethodType oldType = target.type();
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        int outargs = oldType.parameterCount();
        int inargs  = outargs - insCount;
        if (inargs < 0)
            throw newIllegalArgumentException("too many values to insert");
        if (pos < 0 || pos > inargs)
            throw newIllegalArgumentException("no argument type to append");
        MethodHandle result = target;
        for (int i = 0; i < insCount; i++) {
            Object value = values[i];
            Class<?> valueType = oldType.parameterType(pos+i);
            value = checkValue(valueType, value);
            if (pos == 0 && !valueType.isPrimitive()) {
                // At least for now, make bound method handles a special case.
                MethodHandle bmh = MethodHandleImpl.bindReceiver(IMPL_TOKEN, result, value);
                if (bmh != null) {
                    result = bmh;
                    continue;
                }
                // else fall through to general adapter machinery
            }
            result = MethodHandleImpl.bindArgument(IMPL_TOKEN, result, pos, value);
        }
        return result;
    }

    @Deprecated // "use MethodHandles.insertArguments instead"
    public static
    MethodHandle insertArgument(MethodHandle target, int pos, Object value) {
        return insertArguments(target, pos, value);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Produce a method handle which calls the original method handle,
     * after dropping the given argument(s) at the given position.
     * The type of the new method handle will insert the given argument
     * type(s), at that position, into the original handle's type.
     * <p>
     * The <i>pos</i> may range between zero and <i>N</i>,
     * where <i>N</i> is the number of argument types in <i>target</i>,
     * meaning to drop the first or last argument (respectively),
     * or an argument somewhere in between.
     * <p>
     * <b>Example:</b>
     * <p><blockquote><pre>
     *   MethodHandle cat = MethodHandles.lookup().
     *     findVirtual(String.class, "concat", String.class, String.class);
     *   System.out.println(cat.&lt;String&gt;invoke("x", "y")); // xy
     *   MethodHandle d0 = dropArguments(cat, 0, String.class);
     *   System.out.println(d0.&lt;String&gt;invoke("x", "y", "z")); // xy
     *   MethodHandle d1 = dropArguments(cat, 1, String.class);
     *   System.out.println(d1.&lt;String&gt;invoke("x", "y", "z")); // xz
     *   MethodHandle d2 = dropArguments(cat, 2, String.class);
     *   System.out.println(d2.&lt;String&gt;invoke("x", "y", "z")); // yz
     *   MethodHandle d12 = dropArguments(cat, 1, String.class, String.class);
     *   System.out.println(d12.&lt;String&gt;invoke("w", "x", "y", "z")); // wz
     * </pre></blockquote>
     * @param target the method handle to invoke after the argument is dropped
     * @param valueTypes the type(s) of the argument to drop
     * @param pos which argument to drop (zero for the first)
     * @return a new method handle which drops an argument of the given type,
     *         before calling the original method handle
     */
    public static
    MethodHandle dropArguments(MethodHandle target, int pos, List<Class<?>> valueTypes) {
        if (valueTypes.size() == 0)  return target;
        MethodType oldType = target.type();
        int outargs = oldType.parameterCount();
        int inargs  = outargs + valueTypes.size();
        if (pos < 0 || pos >= inargs)
            throw newIllegalArgumentException("no argument type to remove");
        ArrayList<Class<?>> ptypes =
                new ArrayList<Class<?>>(oldType.parameterList());
        ptypes.addAll(pos, valueTypes);
        MethodType newType = MethodType.methodType(oldType.returnType(), ptypes);
        return MethodHandleImpl.dropArguments(IMPL_TOKEN, target, newType, pos);
    }

    public static
    MethodHandle dropArguments(MethodHandle target, int pos, Class<?>... valueTypes) {
        return dropArguments(target, pos, Arrays.asList(valueTypes));
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Adapt a target method handle {@code target} by pre-processing
     * one or more of its arguments, each with its own unary filter function,
     * and then calling the target with each pre-processed argument
     * replaced by the result of its corresponding filter function.
     * <p>
     * The pre-processing is performed by one or more method handles,
     * specified in the non-null elements of the {@code filters} array.
     * (If there are no such elements, the original target is returned.)
     * Each filter (that is, each non-null element of {@code filters})
     * is applied to the corresponding argument of the adapter.
     * <p>
     * If a filter {@code F} applies to the {@code N}th argument of
     * the method handle, then {@code F} must be a method handle which
     * takes exactly one argument.  The type of {@code F}'s sole argument
     * replaces the corresponding argument type of the target
     * in the resulting adapted method handle.
     * The return type of {@code F} must be identical to the corresponding
     * parameter type of the target.
     * <p>
     * It is an error if there are non-null elements of {@code filters}
     * which do not correspond to argument positions in the target.
     * The actual length of the target array may be any number, it need
     * not be the same as the parameter count of the target type.
     * (This provides an easy way to filter just the first argument or two
     * of a target method handle.)
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * // there are N arguments in the A sequence
     * T target(A[N]...);
     * [i&lt;N] V[i] filter[i](B[i]) = filters[i] ?: identity;
     * T adapter(B[N]... b) {
     *   A[N] a...;
     *   [i&lt;N] a[i] = filter[i](b[i]);
     *   return target(a...);
     * }
     * </pre></blockquote>
     * @param target the method handle to invoke after arguments are filtered
     * @param filters method handles to call initially on filtered arguments
     * @return method handle which incorporates the specified argument filtering logic
     * @throws IllegalArgumentException if a non-null element of {@code filters}
     *          does not match a corresponding argument type of {@code target}
     */
    public static
    MethodHandle filterArguments(MethodHandle target, MethodHandle... filters) {
        MethodType targetType = target.type();
        MethodHandle adapter = target;
        MethodType adapterType = targetType;
        int pos = -1, maxPos = targetType.parameterCount();
        for (MethodHandle filter : filters) {
            pos += 1;
            if (filter == null)  continue;
            if (pos >= maxPos)
                throw newIllegalArgumentException("too many filters");
            MethodType filterType = filter.type();
            if (filterType.parameterCount() != 1
                || filterType.returnType() != targetType.parameterType(pos))
                throw newIllegalArgumentException("target and filter types do not match");
            adapterType = adapterType.changeParameterType(pos, filterType.parameterType(0));
            adapter = MethodHandleImpl.filterArgument(IMPL_TOKEN, adapter, pos, filter);
        }
        MethodType midType = adapter.type();
        if (midType != adapterType)
            adapter = MethodHandleImpl.convertArguments(IMPL_TOKEN, adapter, adapterType, midType, null);
        return adapter;
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Adapt a target method handle {@code target} by pre-processing
     * some of its arguments, and then calling the target with
     * the result of the pre-processing, plus all original arguments.
     * <p>
     * The pre-processing is performed by a second method handle, the {@code combiner}.
     * The first {@code N} arguments passed to the adapter,
     * are copied to the combiner, which then produces a result.
     * (Here, {@code N} is defined as the parameter count of the adapter.)
     * After this, control passes to the {@code target}, with both the result
     * of the combiner, and all the original incoming arguments.
     * <p>
     * The first argument type of the target must be identical with the
     * return type of the combiner.
     * The resulting adapter is the same type as the target, except that the
     * initial argument type of the target is dropped.
     * <p>
     * (Note that {@link #dropArguments} can be used to remove any arguments
     * that either the {@code combiner} or {@code target} does not wish to receive.
     * If some of the incoming arguments are destined only for the combiner,
     * consider using {@link #collectArguments} instead, since those
     * arguments will not need to be live on the stack on entry to the
     * target.)
     * <p>
     * The first argument of the target must be identical with the
     * return value of the combiner.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * // there are N arguments in the A sequence
     * T target(V, A[N]..., B...);
     * V combiner(A...);
     * T adapter(A... a, B... b) {
     *   V v = combiner(a...);
     *   return target(v, a..., b...);
     * }
     * </pre></blockquote>
     * @param target the method handle to invoke after arguments are combined
     * @param combiner method handle to call initially on the incoming arguments
     * @return method handle which incorporates the specified argument folding logic
     * @throws IllegalArgumentException if the first argument type of
     *          {@code target} is not the same as {@code combiner}'s return type,
     *          or if the next {@code foldArgs} argument types of {@code target}
     *          are not identical with the argument types of {@code combiner}
     */
    public static
    MethodHandle foldArguments(MethodHandle target, MethodHandle combiner) {
        MethodType targetType = target.type();
        MethodType combinerType = combiner.type();
        int foldArgs = combinerType.parameterCount();
        boolean ok = (targetType.parameterCount() >= 1 + foldArgs);
        if (!ok)
            throw misMatchedTypes("target and combiner types", targetType, combinerType);
        MethodType newType = targetType.dropParameterTypes(0, 1);
        return MethodHandleImpl.foldArguments(IMPL_TOKEN, target, newType, combiner);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Make a method handle which adapts a target method handle,
     * by guarding it with a test, a boolean-valued method handle.
     * If the guard fails, a fallback handle is called instead.
     * All three method handles must have the same corresponding
     * argument and return types, except that the return type
     * of the test must be boolean, and the test is allowed
     * to have fewer arguments than the other two method handles.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * boolean test(A...);
     * T target(A...,B...);
     * T fallback(A...,B...);
     * T adapter(A... a,B... b) {
     *   if (test(a...))
     *     return target(a..., b...);
     *   else
     *     return fallback(a..., b...);
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
        MethodType gtype = test.type();
        MethodType ttype = target.type();
        MethodType ftype = fallback.type();
        if (ttype != ftype)
            throw misMatchedTypes("target and fallback types", ttype, ftype);
        MethodType gtype2 = ttype.changeReturnType(boolean.class);
        if (gtype2 != gtype) {
            if (gtype.returnType() != boolean.class)
                throw newIllegalArgumentException("guard type is not a predicate "+gtype);
            int gpc = gtype.parameterCount(), tpc = ttype.parameterCount();
            if (gpc < tpc) {
                test = dropArguments(test, gpc, ttype.parameterList().subList(gpc, tpc));
                gtype = test.type();
            }
            if (gtype2 != gtype)
                throw misMatchedTypes("target and test types", ttype, gtype);
        }
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
                    MethodType.methodType(boolean.class, MethodHandle.class, MethodHandle.class));
            choose = appendArgument(choose, target);
            choose = appendArgument(choose, fallback);
            MethodHandle dispatch = compose(choose, test);
            // dispatch = \(a...).(test(a...) ? target : fallback)
            return combineArguments(invoke, dispatch, 0);
            // return \(a...).((test(a...) ? target : fallback).invoke(a...))
        } */
        return MethodHandleImpl.makeGuardWithTest(IMPL_TOKEN, test, target, fallback);
    }

    static RuntimeException misMatchedTypes(String what, MethodType t1, MethodType t2) {
        return newIllegalArgumentException(what + " must match: " + t1 + " != " + t2);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Make a method handle which adapts a target method handle,
     * by running it inside an exception handler.
     * If the target returns normally, the adapter returns that value.
     * If an exception matching the specified type is thrown, the fallback
     * handle is called instead on the exception, plus the original arguments.
     * <p>
     * The handler must have leading parameter of {@code exType} or a supertype,
     * followed by arguments which correspond <em>(how? TBD)</em> to
     * all the parameters of the target.
     * The target and handler must return the same type.
     * <p> Here is pseudocode for the resulting adapter:
     * <blockquote><pre>
     * T target(A...);
     * T handler(ExType, A...);
     * T adapter(A... a) {
     *   try {
     *     return target(a...);
     *   } catch (ExType ex) {
     *     return handler(ex, a...);
     *   }
     * }
     * </pre></blockquote>
     * @param target method handle to call
     * @param exType the type of exception which the handler will catch
     * @param handler method handle to call if a matching exception is thrown
     * @return method handle which incorporates the specified try/catch logic
     * @throws IllegalArgumentException if {@code handler} does not accept
     *          the given exception type, or if the method handle types do
     *          not match in their return types and their
     *          corresponding parameters
     */
    public static
    MethodHandle catchException(MethodHandle target,
                                Class<? extends Throwable> exType,
                                MethodHandle handler) {
        MethodType targetType = target.type();
        MethodType handlerType = handler.type();
        boolean ok = (targetType.parameterCount() ==
                      handlerType.parameterCount() - 1);
//        for (int i = 0; ok && i < numExArgs; i++) {
//            if (targetType.parameterType(i) != handlerType.parameterType(1+i))
//                ok = false;
//        }
        if (!ok)
            throw newIllegalArgumentException("target and handler types do not match");
        return MethodHandleImpl.makeGuardWithCatch(IMPL_TOKEN, target, exType, handler);
    }

    /**
     * Produce a method handle which will throw exceptions of the given {@code exType}.
     * The method handle will accept a single argument of {@code exType},
     * and immediately throw it as an exception.
     * The method type will nominally specify a return of {@code returnType}.
     * The return type may be anything convenient:  It doesn't matter to the
     * method handle's behavior, since it will never return normally.
     */
    public static
    MethodHandle throwException(Class<?> returnType, Class<? extends Throwable> exType) {
        return MethodHandleImpl.throwException(IMPL_TOKEN, MethodType.methodType(returnType, exType));
    }

    /** Alias for {@link MethodType#methodType}. */
    @Deprecated // "use MethodType.methodType instead"
    public static MethodType methodType(Class<?> rtype) {
        return MethodType.methodType(rtype);
    }

    /** Alias for {@link MethodType#methodType}. */
    @Deprecated // "use MethodType.methodType instead"
    public static MethodType methodType(Class<?> rtype, Class<?> ptype) {
        return MethodType.methodType(rtype, ptype);
    }

    /** Alias for {@link MethodType#methodType}. */
    @Deprecated // "use MethodType.methodType instead"
    public static MethodType methodType(Class<?> rtype, Class<?> ptype0, Class<?>... ptypes) {
        return MethodType.methodType(rtype, ptype0, ptypes);
    }
}
