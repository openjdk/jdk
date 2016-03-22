/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Module;
import java.net.URI;
import java.util.Set;

import jdk.internal.loader.BootLoader;
import jdk.internal.loader.ClassLoaders;
import jdk.internal.misc.JavaLangReflectModuleAccess;
import jdk.internal.misc.SharedSecrets;


/**
 * A helper class to allow JDK classes create dynamic modules and to update
 * modules, exports and the readability graph. It is also invoked by the VM
 * to add read edges when agents are instrumenting code that need to link
 * to supporting classes.
 *
 * The parameters that are package names in this API are the fully-qualified
 * names of the packages as defined in section 6.5.3 of <cite>The Java&trade;
 * Language Specification </cite>, for example, {@code "java.lang"}.
 */

public class Modules {
    private Modules() { }

    private static final JavaLangReflectModuleAccess JLRMA
        = SharedSecrets.getJavaLangReflectModuleAccess();


    /**
     * Creates a new Module. The module has the given ModuleDescriptor and
     * is defined to the given class loader.
     *
     * The resulting Module is in a larva state in that it does not not read
     * any other module and does not have any exports.
     *
     * The URI is for information purposes only.
     */
    public static Module defineModule(ClassLoader loader,
                                      ModuleDescriptor descriptor,
                                      URI uri)
    {
        return JLRMA.defineModule(loader, descriptor, uri);
    }

    /**
     * Define a new module to the VM. The module has the given set of
     * concealed packages and is defined to the given class loader.
     *
     * The resulting Module is in a larva state in that it does not not read
     * any other module and does not have any exports.
     */
    public static Module defineModule(ClassLoader loader,
                                      String name,
                                      Set<String> packages)
    {
        ModuleDescriptor descriptor
            = new ModuleDescriptor.Builder(name).conceals(packages).build();

        return JLRMA.defineModule(loader, descriptor, null);
    }

    /**
     * Adds a read-edge so that module {@code m1} reads module {@code m1}.
     * Same as m1.addReads(m2) but without a caller check.
     */
    public static void addReads(Module m1, Module m2) {
        JLRMA.addReads(m1, m2);
    }

    /**
     * Updates module m1 to export a package to module m2.
     * Same as m1.addExports(pkg, m2) but without a caller check.
     */
    public static void addExports(Module m1, String pn, Module m2) {
        JLRMA.addExports(m1, pn, m2);
    }

    /**
     * Updates a module m to export a package to all modules.
     */
    public static void addExportsToAll(Module m, String pn) {
        JLRMA.addExportsToAll(m, pn);
    }

    /**
     * Updates module m to export a package to all unnamed modules.
     */
    public static void addExportsToAllUnnamed(Module m, String pn) {
        JLRMA.addExportsToAllUnnamed(m, pn);
    }

    /**
     * Adds a package to a module's content.
     *
     * This method is a no-op if the module already contains the package.
     */
    public static void addPackage(Module m, String pn) {
        JLRMA.addPackage(m, pn);
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
