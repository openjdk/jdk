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

package sun.dyn;

import java.dyn.*;

/**
 * Parts of CallSite known to the JVM.
 * @author jrose
 */
public class CallSiteImpl {
    // this implements the upcall from the JVM, MethodHandleNatives.makeDynamicCallSite:
    static CallSite makeSite(MethodHandle bootstrapMethod,
                             // Callee information:
                             String name, MethodType type,
                             // Call-site attributes, if any:
                             Object info,
                             // Caller information:
                             MemberName callerMethod, int callerBCI) {
        Class<?> caller = callerMethod.getDeclaringClass();
        if (bootstrapMethod == null) {
            // If there is no bootstrap method, throw IncompatibleClassChangeError.
            // This is a valid generic error type for resolution (JLS 12.3.3).
            throw new IncompatibleClassChangeError
                ("Class "+caller.getName()+" has not declared a bootstrap method for invokedynamic");
        }
        CallSite site;
        try {
            if (bootstrapMethod.type().parameterCount() == 3)
                site = bootstrapMethod.<CallSite>invokeExact(caller, name, type);
            else if (bootstrapMethod.type().parameterCount() == 4)
                site = bootstrapMethod.<CallSite>invokeExact(caller, name, type,
                                                             !(info instanceof java.lang.annotation.Annotation[]) ? null
                                                             : (java.lang.annotation.Annotation[]) info);
            else
                throw new InternalError("bad BSM: "+bootstrapMethod);
            if (!(site instanceof CallSite))
                throw new InvokeDynamicBootstrapError("class bootstrap method failed to create a call site: "+caller);
            PRIVATE_INITIALIZE_CALL_SITE.<void>invokeExact(site,
                                                           name, type,
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

    // This method is private in CallSite because it touches private fields in CallSite.
    // These private fields (vmmethod, vmindex) are specific to the JVM.
    private static final MethodHandle PRIVATE_INITIALIZE_CALL_SITE =
            MethodHandleImpl.IMPL_LOOKUP.findVirtual(CallSite.class, "initializeFromJVM",
                MethodType.methodType(void.class,
                                      String.class, MethodType.class,
                                      MemberName.class, int.class));

    public static void setCallSiteTarget(Access token, CallSite site, MethodHandle target) {
        Access.check(token);
        MethodHandleNatives.setCallSiteTarget(site, target);
    }
}
