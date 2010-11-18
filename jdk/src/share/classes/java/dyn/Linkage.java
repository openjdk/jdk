/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

import java.dyn.MethodHandles.Lookup;
import java.util.WeakHashMap;
import sun.dyn.Access;
import sun.dyn.MethodHandleImpl;
import sun.reflect.Reflection;
import static sun.dyn.util.VerifyAccess.checkBootstrapPrivilege;
import static sun.dyn.MemberName.newIllegalArgumentException;

/**
 * This class consists exclusively of static methods that control
 * the linkage of {@code invokedynamic} instructions, and specifically
 * their reification as {@link CallSite} objects.
 * @author John Rose, JSR 292 EG
 */
public class Linkage {
    private static final Access IMPL_TOKEN = Access.getToken();

    private Linkage() {}  // do not instantiate

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Register a <em>bootstrap method</em> to use when linking dynamic call sites within
     * a given caller class.
     * <p>
     * A bootstrap method must be a method handle with a return type of {@link CallSite}
     * and the following arguments:
     * <ul>
     * <li>the class containing the {@code invokedynamic} instruction, for which the bootstrap method was registered
     * <li>the name of the method being invoked (a {@link String})
     * <li>the type of the method being invoked (a {@link MethodType})
     * </ul>
     * The bootstrap method acts as a factory method which accepts the given arguments
     * and returns a {@code CallSite} object (possibly of a subclass of {@code CallSite}).
     * <p>
     * The registration must take place exactly once, either before the class has begun
     * being initialized, or from within the class's static initializer.
     * Registration will fail with an exception if any of the following conditions hold:
     * <ul>
     * <li>The immediate caller of this method is in a different package than the given caller class,
     *     and there is a security manager, and its {@code checkPermission} call throws
     *     when passed {@link LinkagePermission}("registerBootstrapMethod",callerClass).
     * <li>The given caller class already has a bootstrap method registered.
     * <li>The given caller class is already fully initialized.
     * <li>The given caller class is in the process of initialization, in another thread.
     * </ul>
     * Because of these rules, a class may install its own bootstrap method in
     * a static initializer.
     * @param callerClass a class that may have {@code invokedynamic} sites
     * @param bootstrapMethod the method to use to bootstrap all such sites
     * @exception IllegalArgumentException if the class argument is null or
     *            a primitive class, or if the bootstrap method is the wrong type
     * @exception IllegalStateException if the class already has a bootstrap
     *            method, or if the its static initializer has already run
     *            or is already running in another thread
     * @exception SecurityException if there is a security manager installed,
     *            and a {@link LinkagePermission} check fails for "registerBootstrapMethod"
     * @deprecated Use @{@link BootstrapMethod} annotations instead
     */
    public static
    void registerBootstrapMethod(Class callerClass, MethodHandle bootstrapMethod) {
        Class callc = Reflection.getCallerClass(2);
        checkBootstrapPrivilege(callc, callerClass, "registerBootstrapMethod");
        checkBSM(bootstrapMethod);
        MethodHandleImpl.registerBootstrap(IMPL_TOKEN, callerClass, bootstrapMethod);
    }

    static private void checkBSM(MethodHandle mh) {
        if (mh == null)  throw newIllegalArgumentException("null bootstrap method");
        if (mh.type() == BOOTSTRAP_METHOD_TYPE)  return;
        throw new WrongMethodTypeException(mh.toString());
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Simplified version of {@code registerBootstrapMethod} for self-registration,
     * to be called from a static initializer.
     * Finds a static method of the required type in the
     * given runtime class, and installs it on the caller class.
     * @throws NoSuchMethodException if there is no such method
     * @throws IllegalStateException if the caller class's static initializer
     *         has already run, or is already running in another thread
     * @deprecated Use @{@link BootstrapMethod} annotations instead
     */
    public static
    void registerBootstrapMethod(Class<?> runtime, String name) {
        Class callerClass = Reflection.getCallerClass(2);
        registerBootstrapMethodLookup(callerClass, runtime, name);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Simplified version of {@code registerBootstrapMethod} for self-registration,
     * to be called from a static initializer.
     * Finds a static method of the required type in the
     * caller class itself, and installs it on the caller class.
     * @throws IllegalArgumentException if there is no such method
     * @throws IllegalStateException if the caller class's static initializer
     *         has already run, or is already running in another thread
     * @deprecated Use @{@link BootstrapMethod} annotations instead
     */
    public static
    void registerBootstrapMethod(String name) {
        Class callerClass = Reflection.getCallerClass(2);
        registerBootstrapMethodLookup(callerClass, callerClass, name);
    }

    private static
    void registerBootstrapMethodLookup(Class<?> callerClass, Class<?> runtime, String name) {
        Lookup lookup = new Lookup(IMPL_TOKEN, callerClass);
        MethodHandle bootstrapMethod;
        try {
            bootstrapMethod = lookup.findStatic(runtime, name, BOOTSTRAP_METHOD_TYPE);
        } catch (NoAccessException ex) {
            throw new IllegalArgumentException("no such bootstrap method in "+runtime+": "+name, ex);
        }
        checkBSM(bootstrapMethod);
        MethodHandleImpl.registerBootstrap(IMPL_TOKEN, callerClass, bootstrapMethod);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Report the bootstrap method registered for a given caller class.
     * Returns null if the class has never yet registered a bootstrap method.
     * Only callers privileged to set the bootstrap method may inquire
     * about it, because a bootstrap method is potentially a back-door entry
     * point into its class.
     * @exception IllegalArgumentException if the argument is null or
     *            a primitive class
     * @exception SecurityException if there is a security manager installed,
     *            and the immediate caller of this method is not in the same
     *            package as the caller class
     *            and a {@link LinkagePermission} check fails for "getBootstrapMethod"
     * @deprecated
     */
    public static
    MethodHandle getBootstrapMethod(Class callerClass) {
        Class callc = Reflection.getCallerClass(2);
        checkBootstrapPrivilege(callc, callerClass, "getBootstrapMethod");
        return MethodHandleImpl.getBootstrap(IMPL_TOKEN, callerClass);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * The type of any bootstrap method is a three-argument method
     * {@code (Class, String, MethodType)} returning a {@code CallSite}.
     */
    public static final MethodType BOOTSTRAP_METHOD_TYPE
            = MethodType.methodType(CallSite.class,
                                    Class.class, String.class, MethodType.class);

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Invalidate all <code>invokedynamic</code> call sites everywhere.
     * <p>
     * When this method returns, every <code>invokedynamic</code> instruction
     * will invoke its bootstrap method on next call.
     * <p>
     * It is unspecified whether call sites already known to the Java
     * code will continue to be associated with <code>invokedynamic</code>
     * instructions.  If any call site is still so associated, its
     * {@link CallSite#getTarget()} method is guaranteed to return null
     * the invalidation operation completes.
     * <p>
     * Invalidation operations are likely to be slow.  Use them sparingly.
     */
    public static
    Object invalidateAll() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(new LinkagePermission("invalidateAll"));
        }
        throw new UnsupportedOperationException("NYI");
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Invalidate all {@code invokedynamic} call sites in the bytecodes
     * of any methods of the given class.
     * <p>
     * When this method returns, every matching <code>invokedynamic</code>
     * instruction will invoke its bootstrap method on next call.
     * <p>
     * For additional semantics of call site invalidation,
     * see {@link #invalidateAll()}.
     */
    public static
    Object invalidateCallerClass(Class<?> callerClass) {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(new LinkagePermission("invalidateAll", callerClass));
        }
        throw new UnsupportedOperationException("NYI");
    }
}
