/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.runtime.linker;

import java.lang.reflect.Modifier;
import jdk.internal.dynalink.CallSiteDescriptor;
import jdk.internal.dynalink.linker.GuardedInvocation;
import jdk.internal.dynalink.linker.LinkRequest;
import jdk.internal.dynalink.linker.LinkerServices;
import jdk.internal.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.internal.dynalink.support.CallSiteDescriptorFactory;
import jdk.nashorn.internal.runtime.Context;

/**
 * Check java reflection permission for java reflective and java.lang.invoke access from scripts
 */
final class ReflectionCheckLinker implements TypeBasedGuardingDynamicLinker{
    @Override
    public boolean canLinkType(final Class<?> type) {
        return isReflectionClass(type);
    }

    private static boolean isReflectionClass(final Class<?> type) {
        if (type == Class.class || ClassLoader.class.isAssignableFrom(type)) {
            return true;
        }
        final String name = type.getName();
        return name.startsWith("java.lang.reflect.") || name.startsWith("java.lang.invoke.");
    }

    @Override
    public GuardedInvocation getGuardedInvocation(final LinkRequest origRequest, final LinkerServices linkerServices)
            throws Exception {
        checkLinkRequest(origRequest);
        // let the next linker deal with actual linking
        return null;
    }

    static void checkReflectionAccess(Class<?> clazz) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null && isReflectionClass(clazz)) {
            checkReflectionPermission(sm);
        }
    }

    private static void checkLinkRequest(final LinkRequest origRequest) {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            final LinkRequest requestWithoutContext = origRequest.withoutRuntimeContext(); // Nashorn has no runtime context
            final Object self = requestWithoutContext.getReceiver();
            // allow 'static' access on Class objects representing public classes of non-restricted packages
            if ((self instanceof Class) && Modifier.isPublic(((Class<?>)self).getModifiers())) {
                final CallSiteDescriptor desc = requestWithoutContext.getCallSiteDescriptor();
                if(CallSiteDescriptorFactory.tokenizeOperators(desc).contains("getProp")) {
                    if ("static".equals(desc.getNameToken(CallSiteDescriptor.NAME_OPERAND))) {
                        if (Context.isAccessibleClass((Class<?>)self) && !isReflectionClass((Class<?>)self)) {
                            // If "getProp:static" passes access checks, allow access.
                            return;
                        }
                    }
                }
            }
            checkReflectionPermission(sm);
        }
    }

    private static void checkReflectionPermission(final SecurityManager sm) {
        sm.checkPermission(new RuntimePermission(Context.NASHORN_JAVA_REFLECTION));
    }
}
