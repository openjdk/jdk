/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
import sun.dyn.util.VerifyAccess;
import sun.reflect.Reflection;
import static sun.dyn.MemberName.newIllegalArgumentException;

/**
 * <em>CLASS WILL BE REMOVED FOR PFD:</em>
 * Static routines for controlling invokedynamic behavior.
 * Replaced by non-static APIs.
 * @author John Rose, JSR 292 EG
 * @deprecated This class will be removed in the Public Final Draft.
 */
public class Linkage {
    private static final Access IMPL_TOKEN = Access.getToken();

    private Linkage() {}  // do not instantiate

    /**
     * <em>METHOD WILL BE REMOVED FOR PFD:</em>
     * Register a <em>bootstrap method</em> to use when linking dynamic call sites within
     * a given caller class.
     * @deprecated Use @{@link BootstrapMethod} annotations instead.
     */
    public static
    void registerBootstrapMethod(Class callerClass, MethodHandle bootstrapMethod) {
        Class callc = Reflection.getCallerClass(2);
        if (callc != null && !VerifyAccess.isSamePackage(callerClass, callc))
            throw new IllegalArgumentException("cannot set bootstrap method on "+callerClass);
        MethodHandleImpl.registerBootstrap(IMPL_TOKEN, callerClass, bootstrapMethod);
    }

    /**
     * <em>METHOD WILL BE REMOVED FOR PFD:</em>
     * Simplified version of {@code registerBootstrapMethod} for self-registration,
     * to be called from a static initializer.
     * @deprecated Use @{@link BootstrapMethod} annotations instead.
     */
    public static
    void registerBootstrapMethod(Class<?> runtime, String name) {
        Class callerClass = Reflection.getCallerClass(2);
        registerBootstrapMethodLookup(callerClass, runtime, name);
    }

    /**
     * <em>METHOD WILL BE REMOVED FOR PFD:</em>
     * Simplified version of {@code registerBootstrapMethod} for self-registration,
     * @deprecated Use @{@link BootstrapMethod} annotations instead.
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
        MethodHandleImpl.registerBootstrap(IMPL_TOKEN, callerClass, bootstrapMethod);
    }

    private static final MethodType BOOTSTRAP_METHOD_TYPE
            = MethodType.methodType(CallSite.class,
                                    Class.class, String.class, MethodType.class);

    /**
     * <em>METHOD WILL BE REMOVED FOR PFD:</em>
     * Invalidate all <code>invokedynamic</code> call sites everywhere.
     * @deprecated Use {@linkplain CallSite#setTarget call site target setting}
     * and {@link VolatileCallSite#invalidateAll call site invalidation} instead.
     */
    public static
    Object invalidateAll() {
        throw new UnsupportedOperationException();
    }

    /**
     * <em>METHOD WILL BE REMOVED FOR PFD:</em>
     * Invalidate all {@code invokedynamic} call sites in the bytecodes
     * of any methods of the given class.
     * @deprecated Use {@linkplain CallSite#setTarget call site target setting}
     * and {@link VolatileCallSite#invalidateAll call site invalidation} instead.
     */
    public static
    Object invalidateCallerClass(Class<?> callerClass) {
        throw new UnsupportedOperationException();
    }
}
