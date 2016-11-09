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

import static jdk.nashorn.internal.runtime.ECMAErrors.typeError;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import jdk.dynalink.CallSiteDescriptor;
import jdk.dynalink.StandardNamespace;
import jdk.dynalink.StandardOperation;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;
import jdk.dynalink.linker.TypeBasedGuardingDynamicLinker;
import jdk.nashorn.api.scripting.ClassFilter;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;

/**
 * Check java reflection permission for java reflective and java.lang.invoke access from scripts
 */
final class ReflectionCheckLinker implements TypeBasedGuardingDynamicLinker{
    private static final Class<?> STATEMENT_CLASS  = getBeanClass("Statement");
    private static final Class<?> XMLENCODER_CLASS = getBeanClass("XMLEncoder");
    private static final Class<?> XMLDECODER_CLASS = getBeanClass("XMLDecoder");

    private static Class<?> getBeanClass(final String name) {
        try {
            return Class.forName("java.beans." + name);
        } catch (final ClassNotFoundException cnfe) {
            // Possible to miss this class in other profiles.
            return null;
        }
    }

    @Override
    public boolean canLinkType(final Class<?> type) {
        return isReflectionClass(type);
    }

    private static boolean isReflectionClass(final Class<?> type) {
        // Class or ClassLoader subclasses
        if (type == Class.class || ClassLoader.class.isAssignableFrom(type)) {
            return true;
        }

        // check for bean reflection
        if ((STATEMENT_CLASS != null && STATEMENT_CLASS.isAssignableFrom(type)) ||
            (XMLENCODER_CLASS != null && XMLENCODER_CLASS.isAssignableFrom(type)) ||
            (XMLDECODER_CLASS != null && XMLDECODER_CLASS.isAssignableFrom(type))) {
            return true;
        }

        // package name check
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

    private static boolean isReflectiveCheckNeeded(final Class<?> type, final boolean isStatic) {
         // special handling for Proxy subclasses
         if (Proxy.class.isAssignableFrom(type)) {
            if (Proxy.isProxyClass(type)) {
                // real Proxy class - filter only static access
                return isStatic;
            }

            // fake Proxy subclass - filter it always!
            return true;
        }

        // check for any other reflective Class
        return isReflectionClass(type);
    }

    static void checkReflectionAccess(final Class<?> clazz, final boolean isStatic) {
        final Global global = Context.getGlobal();
        final ClassFilter cf = global.getClassFilter();
        if (cf != null && isReflectiveCheckNeeded(clazz, isStatic)) {
            throw typeError("no.reflection.with.classfilter");
        }

        final SecurityManager sm = System.getSecurityManager();
        if (sm != null && isReflectiveCheckNeeded(clazz, isStatic)) {
            checkReflectionPermission(sm);
        }
    }

    private static void checkLinkRequest(final LinkRequest request) {
        final Global global = Context.getGlobal();
        final ClassFilter cf = global.getClassFilter();
        if (cf != null) {
            throw typeError("no.reflection.with.classfilter");
        }

        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            final Object self = request.getReceiver();
            // allow 'static' access on Class objects representing public classes of non-restricted packages
            if ((self instanceof Class) && Modifier.isPublic(((Class<?>)self).getModifiers())) {
                final CallSiteDescriptor desc = request.getCallSiteDescriptor();
                if ("static".equals(NashornCallSiteDescriptor.getOperand(desc)) && NashornCallSiteDescriptor.contains(desc, StandardOperation.GET, StandardNamespace.PROPERTY)) {
                    if (Context.isAccessibleClass((Class<?>)self) && !isReflectionClass((Class<?>)self)) {
                        // If "GET:PROPERTY:static" passes access checks, allow access.
                        return;
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
