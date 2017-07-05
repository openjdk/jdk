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
package jdk.tools.jlink.internal.plugins.asm;

import java.lang.module.ModuleDescriptor;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassReader;

/**
 * A pool for a given module
 */
public interface AsmModulePool extends AsmPool {

    /**
     * Associate a package to this module, useful when adding new classes in new
     * packages. WARNING: In order to properly handle new package and/or new
     * module, module-info class must be added and/or updated.
     *
     * @param pkg The new package, following java binary syntax (/-separated
     * path name).
     * @throws jdk.tools.jlink.plugins.PluginException If a mapping already
     * exist for this package.
     */
    public void addPackage(String pkg);

    /**
     * The module name of this pool.
     * @return The module name;
     */
    public String getModuleName();

    /**
     * Lookup the class in this pool and the required pools. NB: static module
     * readability can be different at execution time.
     *
     * @param binaryName The class to lookup.
     * @return The reader or null if not found
     * @throws jdk.tools.jlink.plugins.PluginException
     */
    public ClassReader getClassReaderInDependencies(String binaryName);

    /**
     * Lookup the class in the exported packages of this module. "public
     * requires" modules are looked up. NB: static module readability can be
     * different at execution time.
     *
     * @param callerModule Name of calling module.
     * @param binaryName The class to lookup.
     * @return The reader or null if not found
     * @throws jdk.tools.jlink.plugins.PluginException
     */
    public ClassReader getExportedClassReader(String callerModule,
            String binaryName);

    /**
     * The module descriptor.
     *
     * @return The module descriptor;
     */
    public ModuleDescriptor getDescriptor();

    /**
     * Retrieve the internal and exported packages.
     *
     * @return
     */
    public Set<String> getAllPackages();
}
