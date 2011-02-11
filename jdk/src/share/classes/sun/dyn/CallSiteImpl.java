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

package sun.dyn;

import java.dyn.*;
import static sun.dyn.MemberName.uncaughtException;

/**
 * Parts of CallSite known to the JVM.
 * @author jrose
 */
public class CallSiteImpl {
    // this implements the upcall from the JVM, MethodHandleNatives.makeDynamicCallSite:
    static CallSite makeSite(MethodHandle bootstrapMethod,
                             // Callee information:
                             String name, MethodType type,
                             // Extra arguments for BSM, if any:
                             Object info,
                             // Caller information:
                             MemberName callerMethod, int callerBCI) {
        Class<?> callerClass = callerMethod.getDeclaringClass();
        Object caller;
        if (bootstrapMethod.type().parameterType(0) == Class.class && TRANSITIONAL_BEFORE_PFD)
            caller = callerClass;  // remove for PFD
        else
            caller = MethodHandleImpl.IMPL_LOOKUP.in(callerClass);
        if (bootstrapMethod == null && TRANSITIONAL_BEFORE_PFD) {
            // If there is no bootstrap method, throw IncompatibleClassChangeError.
            // This is a valid generic error type for resolution (JLS 12.3.3).
            throw new IncompatibleClassChangeError
                ("Class "+callerClass.getName()+" has not declared a bootstrap method for invokedynamic");
        }
        CallSite site;
        try {
            Object binding;
            info = maybeReBox(info);
            if (info == null) {
                binding = bootstrapMethod.invokeGeneric(caller, name, type);
            } else if (!info.getClass().isArray()) {
                binding = bootstrapMethod.invokeGeneric(caller, name, type, info);
            } else {
                Object[] argv = (Object[]) info;
                if (3 + argv.length > 255)
                    new InvokeDynamicBootstrapError("too many bootstrap method arguments");
                MethodType bsmType = bootstrapMethod.type();
                if (bsmType.parameterCount() == 4 && bsmType.parameterType(3) == Object[].class)
                    binding = bootstrapMethod.invokeGeneric(caller, name, type, argv);
                else
                    binding = MethodHandles.spreadInvoker(bsmType, 3)
                        .invokeGeneric(bootstrapMethod, caller, name, type, argv);
            }
            //System.out.println("BSM for "+name+type+" => "+binding);
            if (binding instanceof CallSite) {
                site = (CallSite) binding;
            } else if (binding instanceof MethodHandle && TRANSITIONAL_BEFORE_PFD) {
                // Transitional!
                MethodHandle target = (MethodHandle) binding;
                site = new ConstantCallSite(target);
            } else {
                throw new ClassCastException("bootstrap method failed to produce a CallSite");
            }
            if (TRANSITIONAL_BEFORE_PFD)
                PRIVATE_INITIALIZE_CALL_SITE.invokeExact(site, name, type,
                                                         callerMethod, callerBCI);
            assert(site.getTarget() != null);
            assert(site.getTarget().type().equals(type));
        } catch (Throwable ex) {
            InvokeDynamicBootstrapError bex;
            if (ex instanceof InvokeDynamicBootstrapError)
                bex = (InvokeDynamicBootstrapError) ex;
            else
                bex = new InvokeDynamicBootstrapError("call site initialization exception", ex);
            throw bex;
        }
        return site;
    }

    private static boolean TRANSITIONAL_BEFORE_PFD = true;  // FIXME: remove for PFD

    private static Object maybeReBox(Object x) {
        if (x instanceof Integer) {
            int xi = (int) x;
            if (xi == (byte) xi)
                x = xi;  // must rebox; see JLS 5.1.7
            return x;
        } else if (x instanceof Object[]) {
            Object[] xa = (Object[]) x;
            for (int i = 0; i < xa.length; i++) {
                if (xa[i] instanceof Integer)
                    xa[i] = maybeReBox(xa[i]);
            }
            return xa;
        } else {
            return x;
        }
    }

    // This method is private in CallSite because it touches private fields in CallSite.
    // These private fields (vmmethod, vmindex) are specific to the JVM.
    private static final MethodHandle PRIVATE_INITIALIZE_CALL_SITE;
    static {
        try {
            PRIVATE_INITIALIZE_CALL_SITE =
            !TRANSITIONAL_BEFORE_PFD ? null :
            MethodHandleImpl.IMPL_LOOKUP.findVirtual(CallSite.class, "initializeFromJVM",
                MethodType.methodType(void.class,
                                      String.class, MethodType.class,
                                      MemberName.class, int.class));
        } catch (ReflectiveOperationException ex) {
            throw uncaughtException(ex);
        }
    }

    public static void setCallSiteTarget(Access token, CallSite site, MethodHandle target) {
        Access.check(token);
        MethodHandleNatives.setCallSiteTarget(site, target);
    }
}
