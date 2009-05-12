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

import java.util.WeakHashMap;
import sun.reflect.Reflection;
import static sun.dyn.util.VerifyAccess.checkBootstrapPrivilege;

/**
 * Static methods which control the linkage of invokedynamic call sites.
 * @author John Rose, JSR 292 EG
 */
public class Linkage {
    private Linkage() {}  // do not instantiate

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Register a <em>bootstrap method</em> to use when linking a given caller class.
     * It must be a method handle of a type equivalent to {@link CallSite#CallSite}.
     * In other words, it must act as a factory method which accepts the arguments
     * to {@code CallSite}'s constructor (a class, a string, and a method type),
     * and returns a {@code CallSite} object (possibly of a subclass of {@code CallSite}).
     * <p>
     * The registration will fail with an {@code IllegalStateException} if any of the following conditions hold:
     * <ul>
     * <li>The caller of this method is in a different package than the {@code callerClass},
     *     and there is a security manager, and its {@code checkPermission} call throws
     *     when passed {@link LinkagePermission}("registerBootstrapMethod",callerClass).
     * <li>The given class already has a bootstrap method from a previous
     *     call to this method.
     * <li>The given class is already fully initialized.
     * <li>The given class is in the process of initialization, in another thread.
     * </ul>
     * Because of these rules, a class may install its own bootstrap method in
     * a static initializer.
     */
    public static
    void registerBootstrapMethod(Class callerClass, MethodHandle mh) {
        Class callc = Reflection.getCallerClass(2);
        checkBootstrapPrivilege(callc, callerClass, "registerBootstrapMethod");
        checkBSM(mh);
        synchronized (bootstrapMethods) {
            if (bootstrapMethods.containsKey(callerClass))
                throw new IllegalStateException("bootstrap method already declared in "+callerClass);
            bootstrapMethods.put(callerClass, mh);
        }
    }

    static void checkBSM(MethodHandle mh) {
        if (mh == null)  throw new IllegalArgumentException("null bootstrap method");
        if (mh.type() == OLD_BOOTSTRAP_METHOD_TYPE) // FIXME: delete at EDR/PFD
            throw new WrongMethodTypeException("bootstrap method must be a CallSite factory");
        if (mh.type() != BOOTSTRAP_METHOD_TYPE)
            throw new WrongMethodTypeException(mh.toString());
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Simplified version of registerBootstrapMethod for self-registration,
     * to be called from a static initializer.
     * Finds a static method of the required type in the
     * given class, and installs it on the caller.
     * @throws IllegalArgumentException if there is no such method
     */
    public static
    void registerBootstrapMethod(Class<?> runtime, String name) {
        Class callc = Reflection.getCallerClass(2);
        MethodHandle bootstrapMethod =
            MethodHandles.findStaticFrom(callc, runtime, name, BOOTSTRAP_METHOD_TYPE);
        // FIXME: exception processing wrong here
        checkBSM(bootstrapMethod);
        Linkage.registerBootstrapMethod(callc, bootstrapMethod);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Simplified version of registerBootstrapMethod for self-registration,
     * to be called from a static initializer.
     * Finds a static method of the required type in the
     * caller's class, and installs it on the caller.
     * @throws IllegalArgumentException if there is no such method
     */
    public static
    void registerBootstrapMethod(String name) {
        Class callc = Reflection.getCallerClass(2);
        MethodHandle bootstrapMethod =
            MethodHandles.findStaticFrom(callc, callc, name, BOOTSTRAP_METHOD_TYPE);
        // FIXME: exception processing wrong here
        checkBSM(bootstrapMethod);
        Linkage.registerBootstrapMethod(callc, bootstrapMethod);
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * Report the bootstrap method registered for a given class.
     * Returns null if the class has never yet registered a bootstrap method,
     * or if the class has explicitly registered a null bootstrap method.
     * Only callers privileged to set the bootstrap method may inquire
     * about it, because a bootstrap method is potentially a back-door entry
     * point into its class.
     */
    public static
    MethodHandle getBootstrapMethod(Class callerClass) {
        Class callc = Reflection.getCallerClass(2);
        checkBootstrapPrivilege(callc, callerClass, "registerBootstrapMethod");
        synchronized (bootstrapMethods) {
            return bootstrapMethods.get(callerClass);
        }
    }

    /**
     * <em>PROVISIONAL API, WORK IN PROGRESS:</em>
     * The type of any bootstrap method is a three-argument method
     * {@code (Class, String, MethodType)} returning a {@code CallSite}.
     */
    public static final MethodType BOOTSTRAP_METHOD_TYPE
            = MethodType.make(CallSite.class,
                              Class.class, String.class, MethodType.class);

    private static final MethodType OLD_BOOTSTRAP_METHOD_TYPE
            = MethodType.make(Object.class,
                              CallSite.class, Object[].class);

    private static final WeakHashMap<Class, MethodHandle> bootstrapMethods =
            new WeakHashMap<Class, MethodHandle>();

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
     * Invalidate all <code>invokedynamic</code> call sites associated
     * with the given class.
     * (These are exactly those sites which report the given class
     * via the {@link CallSite#callerClass()} method.)
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

    private static Object doNotBootstrap(CallSite site, Object... arguments) {
        throw new UnsupportedOperationException("call site must not have null target: "+site);
    }

    private static final MethodHandle DO_NOT_BOOTSTRAP =
            MethodHandles.Lookup.IMPL_LOOKUP.findStatic(Linkage.class, "doNotBootstrap",
                OLD_BOOTSTRAP_METHOD_TYPE);

    // Up-call from the JVM.  Obsolete.  FIXME: Delete from VM then from here.
    static
    MethodHandle findBootstrapMethod(Class callerClass, Class searchBootstrapClass) {
        return DO_NOT_BOOTSTRAP;
    }
}
