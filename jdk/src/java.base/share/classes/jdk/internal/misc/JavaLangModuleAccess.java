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

package jdk.internal.misc;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Version;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to non-public methods in java.lang.module.
 */

public interface JavaLangModuleAccess {

    /**
     * Returns {@code ModuleDescriptor.Requires} of the given modifier
     * and module name.
     */
    Requires newRequires(Set<Requires.Modifier> ms, String mn);

    /**
     * Returns an unqualified {@code ModuleDescriptor.Exports}
     * of the given package name.
     */
    Exports newExports(String source);

    /**
     * Returns a qualified {@code ModuleDescriptor.Exports}
     * of the given package name and targets.
     */
    Exports newExports(String source, Set<String> targets);

    /**
     * Returns a {@code ModuleDescriptor.Provides}
     * of the given service name and providers.
     */
    Provides newProvides(String service, Set<String> providers);

    /**
     * Returns a {@code ModuleDescriptor.Version} of the given version.
     */
    Version newVersion(String v);

    /**
     * Clones the given module descriptor with an augmented set of packages
     */
    ModuleDescriptor newModuleDescriptor(ModuleDescriptor md, Set<String> pkgs);

    /**
     * Returns a new {@code ModuleDescriptor} instance.
     */
    ModuleDescriptor newModuleDescriptor(String name,
                                         boolean automatic,
                                         boolean synthetic,
                                         Set<Requires> requires,
                                         Set<String> uses,
                                         Set<Exports> exports,
                                         Map<String, Provides> provides,
                                         Version version,
                                         String mainClass,
                                         String osName,
                                         String osArch,
                                         String osVersion,
                                         Set<String> conceals,
                                         Set<String> packages);
}
