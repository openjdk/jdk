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
 * The CallSite privately created by the JVM at every invokedynamic instruction.
 * @author jrose
 */
class CallSiteImpl extends CallSite {
    // Fields used only by the JVM.  Do not use or change.
    private Object vmmethod;

    // Values supplied by the JVM:
    int callerMID, callerBCI;

    private CallSiteImpl(Class<?> caller, String name, MethodType type) {
        super(caller, name, type);
    }

    @Override
    public void setTarget(MethodHandle mh) {
        checkTarget(mh);
        if (MethodHandleNatives.JVM_SUPPORT)
            MethodHandleNatives.linkCallSite(this, (MethodHandle) mh);
        else
            super.setTarget(mh);
    }

    private static final MethodHandle PRIVATE_INITIALIZE_CALL_SITE =
            MethodHandleImpl.IMPL_LOOKUP.findStatic(CallSite.class, "privateInitializeCallSite",
                MethodType.make(void.class, CallSite.class, int.class, int.class));

    // this is the up-call from the JVM:
    static CallSite makeSite(Class<?> caller, String name, MethodType type,
                             int callerMID, int callerBCI) {
        MethodHandle bsm = Linkage.getBootstrapMethod(caller);
        if (bsm == null)
            throw new InvokeDynamicBootstrapError("class has no bootstrap method: "+caller);
        CallSite site = bsm.<CallSite>invoke(caller, name, type);
        if (site == null)
            throw new InvokeDynamicBootstrapError("class bootstrap method failed to create a call site: "+caller);
        PRIVATE_INITIALIZE_CALL_SITE.<void>invoke(site, callerMID, callerBCI);
        return site;
    }
}
