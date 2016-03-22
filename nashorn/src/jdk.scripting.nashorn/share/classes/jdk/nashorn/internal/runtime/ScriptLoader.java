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

package jdk.nashorn.internal.runtime;

import java.lang.reflect.Module;
import java.security.CodeSource;
import java.util.Objects;

/**
 * Responsible for loading script generated classes.
 *
 */
final class ScriptLoader extends NashornLoader {
    private static final String NASHORN_PKG_PREFIX = "jdk.nashorn.internal.";

    private volatile boolean structureAccessAdded;
    private final Module scriptModule;
    private final Context context;

    /*package-private*/ Context getContext() {
        return context;
    }

    /**
     * Constructor.
     */
    ScriptLoader(final ClassLoader parent, final Context context) {
        super(parent);
        this.context = context;

        // new scripts module, it's specific exports and read-edges
        scriptModule = defineModule("jdk.scripting.nashorn.scripts", this);
        addModuleExports(scriptModule, SCRIPTS_PKG, nashornModule);
        addReadsModule(scriptModule, nashornModule);
        addReadsModule(scriptModule, Object.class.getModule());

        // specific exports from nashorn to new scripts module
        nashornModule.addExports(OBJECTS_PKG, scriptModule);
        nashornModule.addExports(RUNTIME_PKG, scriptModule);
        nashornModule.addExports(RUNTIME_ARRAYS_PKG, scriptModule);
        nashornModule.addExports(RUNTIME_LINKER_PKG, scriptModule);
        nashornModule.addExports(SCRIPTS_PKG, scriptModule);

        // nashorn needs to read scripts module methods,fields
        nashornModule.addReads(scriptModule);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        checkPackageAccess(name);
        if (name.startsWith(NASHORN_PKG_PREFIX)) {
            final StructureLoader sharedCl = context.getSharedLoader();
            final Class<?> cl = sharedCl.loadClass(name);
            if (! structureAccessAdded) {
                if (cl.getClassLoader() == sharedCl) {
                    structureAccessAdded = true;
                    final Module structModule = sharedCl.getModule();
                    addModuleExports(structModule, SCRIPTS_PKG, scriptModule);
                    addReadsModule(scriptModule, structModule);
                }
            }
            return cl;
        }
        return super.loadClass(name, resolve);
    }

    // package-private and private stuff below this point

    /**
     * Install a class for use by the Nashorn runtime
     *
     * @param name Binary name of class.
     * @param data Class data bytes.
     * @param cs CodeSource code source of the class bytes.
     *
     * @return Installed class.
     */
    synchronized Class<?> installClass(final String name, final byte[] data, final CodeSource cs) {
        return defineClass(name, data, 0, data.length, Objects.requireNonNull(cs));
    }
}
