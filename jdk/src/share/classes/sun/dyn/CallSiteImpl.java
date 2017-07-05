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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parts of CallSite known to the JVM.
 * FIXME: Merge all this into CallSite proper.
 * @author jrose
 */
public class CallSiteImpl {
    // Field used only by the JVM.  Do not use or change.
    private Object vmmethod;

    // Values supplied by the JVM:
    protected int callerMID, callerBCI;

    private MethodHandle target;
    protected final Object caller;  // usually a class
    protected final String name;
    protected final MethodType type;

    /** called only directly from CallSite() */
    protected CallSiteImpl(Access token, Object caller, String name, MethodType type) {
        Access.check(token);
        this.caller = caller;
        this.name = name;
        this.type = type;
    }

    /** native version of setTarget */
    protected void setTarget(MethodHandle mh) {
        //System.out.println("setTarget "+this+" := "+mh);
        // XXX I don't know how to fix this properly.
//         if (false && MethodHandleNatives.JVM_SUPPORT) // FIXME: enable this
//             MethodHandleNatives.linkCallSite(this, mh);
//         else
            this.target = mh;
    }

    protected MethodHandle getTarget() {
        return target;
    }

    private static final MethodHandle PRIVATE_INITIALIZE_CALL_SITE =
            MethodHandleImpl.IMPL_LOOKUP.findStatic(CallSite.class, "privateInitializeCallSite",
                MethodType.methodType(void.class, CallSite.class, int.class, int.class));

    // this is the up-call from the JVM:
    static CallSite makeSite(Class<?> caller, String name, MethodType type,
                             int callerMID, int callerBCI) {
        MethodHandle bsm = Linkage.getBootstrapMethod(caller);
        if (bsm == null)
            throw new InvokeDynamicBootstrapError("class has no bootstrap method: "+caller);
        CallSite site;
        try {
            site = bsm.<CallSite>invoke(caller, name, type);
        } catch (Throwable ex) {
            throw new InvokeDynamicBootstrapError("exception thrown while linking", ex);
        }
        if (site == null)
            throw new InvokeDynamicBootstrapError("class bootstrap method failed to create a call site: "+caller);
        if (site.type() != type)
            throw new InvokeDynamicBootstrapError("call site type not initialized correctly: "+site);
        if (site.callerClass() != caller)
            throw new InvokeDynamicBootstrapError("call site caller not initialized correctly: "+site);
        if ((Object)site.name() != name)
            throw new InvokeDynamicBootstrapError("call site name not initialized correctly: "+site);
        try {
            PRIVATE_INITIALIZE_CALL_SITE.<void>invoke(site, callerMID, callerBCI);
        } catch (Throwable ex) {
            throw new InvokeDynamicBootstrapError("call site initialization exception", ex);
        }
        return site;
    }
}
