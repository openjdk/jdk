/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import java.lang.invoke.MethodHandles;
import jdk.internal.module.Modules;
import jdk.jfr.Event;

public final class SecuritySupport {
    private static final Module JFR_MODULE = Event.class.getModule();
    private static final String TRACING_PACKAGE_NAME = "jdk.jfr.tracing";
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    static {
        // ensure module java.base can read module jdk.jfr as early as possible
        addReadEdge(Object.class);
        addInternalEventExport(Object.class);
        addEventsExport(Object.class);
    }

    static void makeVisibleToJFR(Class<?> clazz) {
        Module classModule = clazz.getModule();
        Modules.addReads(JFR_MODULE, classModule);
        if (clazz.getPackage() != null) {
            String packageName = clazz.getPackage().getName();
            Modules.addExports(classModule, packageName, JFR_MODULE);
            Modules.addOpens(classModule, packageName, JFR_MODULE);
        }
    }

    /**
     * Adds a qualified export of the internal.jdk.jfr.internal.event package
     * (for EventConfiguration and EventWriter)
     */
    static void addInternalEventExport(Class<?> clazz) {
        Modules.addExports(JFR_MODULE, "jdk.jfr.internal.event", clazz.getModule());
    }

    static void addEventsExport(Class<?> clazz) {
        Modules.addExports(JFR_MODULE, "jdk.jfr.events", clazz.getModule());
    }

    public static void addTracingExport() {
        Modules.addExports(JFR_MODULE, TRACING_PACKAGE_NAME);
    }

    static void addReadEdge(Class<?> clazz) {
        Modules.addReads(clazz.getModule(), JFR_MODULE);
    }

    public static void ensureClassIsInitialized(Class<?> clazz) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, LOOKUP);
            lookup.ensureInitialized(clazz);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    static Class<?> defineClass(Class<?> lookupClass, byte[] bytes) {
        try {
            return MethodHandles.privateLookupIn(lookupClass, LOOKUP).defineClass(bytes);
        } catch (IllegalAccessException e) {
            throw new InternalError(e);
        }
    }
}
