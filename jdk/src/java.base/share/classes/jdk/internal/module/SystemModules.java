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

/*
 * SystemModules class will be generated at link time to create
 * ModuleDescriptor for the installed modules directly to improve
 * the module descriptor reconstitution time.
 *
 * This will skip parsing of module-info.class file and validating
 * names such as module name, package name, service and provider type names.
 * It also avoids taking a defensive copy of any collection.
 *
 * @see jdk.tools.jlink.internal.plugins.SystemModuleDescriptorPlugin
 */
public final class SystemModules {
    /**
     * Name of the installed modules.
     *
     * This array provides a way for InstalledModuleFinder to fallback
     * and read module-info.class from the run-time image instead of
     * the fastpath.
     */
    public static final String[] MODULE_NAMES = new String[1];

    /**
     * Number of packages in the boot layer from the installed modules.
     *
     * Don't make it final to avoid inlining during compile time as
     * the value will be changed at jlink time.
     */
    public static final int PACKAGES_IN_BOOT_LAYER = 1024;

    /**
     * Returns a non-empty array of ModuleDescriptors in the run-time image.
     *
     * When running an exploded image it returns an empty array.
     */
    public static ModuleDescriptor[] modules() {
        return new ModuleDescriptor[0];
    }
}