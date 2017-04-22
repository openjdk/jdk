/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.module;

import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.security.AccessController;
import java.security.PrivilegedAction;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.misc.JavaLangAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * A helper class for creating and updating modules. This class is intended to
 * support command-line options, tests, and the instrumentation API. It is also
 * used by the VM to add read edges when agents are instrumenting code that
 * need to link to supporting classes.
 *
 * The parameters that are package names in this API are the fully-qualified
 * names of the packages as defined in section 6.5.3 of <cite>The Java&trade;
 * Language Specification </cite>, for example, {@code "java.lang"}.
 */

public class Modules {
    private Modules() { }

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /**
     * Creates a new Module. The module has the given ModuleDescriptor and
     * is defined to the given class loader.
     *
     * The resulting Module is in a larval state in that it does not not read
     * any other module and does not have any exports.
     *
     * The URI is for information purposes only.
     */
    public static Module defineModule(ClassLoader loader,
                                      ModuleDescriptor descriptor,
                                      URI uri)
    {
        return JLA.defineModule(loader, descriptor, uri);
    }

    /**
     * Updates m1 to read m2.
     * Same as m1.addReads(m2) but without a caller check.
     */
    public static void addReads(Module m1, Module m2) {
        JLA.addReads(m1, m2);
    }

    /**
     * Update module m to read all unnamed modules.
     */
    public static void addReadsAllUnnamed(Module m) {
        JLA.addReadsAllUnnamed(m);
    }

    /**
     * Updates module m1 to export a package to module m2.
     * Same as m1.addExports(pn, m2) but without a caller check
     */
    public static void addExports(Module m1, String pn, Module m2) {
        JLA.addExports(m1, pn, m2);
    }

    /**
     * Updates module m to export a package to all unnamed modules.
     */
    public static void addExportsToAllUnnamed(Module m, String pn) {
        JLA.addExportsToAllUnnamed(m, pn);
    }

    /**
     * Updates module m1 to open a package to module m2.
     * Same as m1.addOpens(pn, m2) but without a caller check.
     */
    public static void addOpens(Module m1, String pn, Module m2) {
        JLA.addOpens(m1, pn, m2);
    }

    /**
     * Updates module m to open a package to all unnamed modules.
     */
    public static void addOpensToAllUnnamed(Module m, String pn) {
        JLA.addOpensToAllUnnamed(m, pn);
    }

    /**
     * Updates module m to use a service.
     * Same as m2.addUses(service) but without a caller check.
     */
    public static void addUses(Module m, Class<?> service) {
        JLA.addUses(m, service);
    }

    /**
     * Updates module m to provide a service
     */
    public static void addProvides(Module m, Class<?> service, Class<?> impl) {
        ModuleLayer layer = m.getLayer();

        if (layer == null || layer == ModuleLayer.boot()) {
            // update ClassLoader catalog
            PrivilegedAction<ClassLoader> pa = m::getClassLoader;
            ClassLoader loader = AccessController.doPrivileged(pa);
            ServicesCatalog catalog;
            if (loader == null) {
                catalog = BootLoader.getServicesCatalog();
            } else {
                catalog = ServicesCatalog.getServicesCatalog(loader);
            }
            catalog.addProvider(m, service, impl);
        }

        if (layer != null) {
            // update Layer catalog
            JLA.getServicesCatalog(layer).addProvider(m, service, impl);
        }
    }

    /**
     * Called by the VM when code in the given Module has been transformed by
     * an agent and so may have been instrumented to call into supporting
     * classes on the boot class path or application class path.
     */
    public static void transformedByAgent(Module m) {
        addReads(m, BootLoader.getUnnamedModule());
        addReads(m, ClassLoaders.appClassLoader().getUnnamedModule());
    }
}
