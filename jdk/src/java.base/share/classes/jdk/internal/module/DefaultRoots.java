/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines methods to compute the default set of root modules for the unnamed
 * module.
 */

public final class DefaultRoots {
    private static final String JAVA_SE = "java.se";

    private DefaultRoots() { }

    /**
     * Returns the default set of root modules for the unnamed module computed from
     * the system modules observable with the given module finder.
     */
    static Set<String> compute(ModuleFinder systemModuleFinder, ModuleFinder finder) {
        Set<String> roots = new HashSet<>();

        boolean hasJava = false;
        if (systemModuleFinder.find(JAVA_SE).isPresent()) {
            if (finder == systemModuleFinder || finder.find(JAVA_SE).isPresent()) {
                // java.se is a system module
                hasJava = true;
                roots.add(JAVA_SE);
            }
        }

        for (ModuleReference mref : systemModuleFinder.findAll()) {
            String mn = mref.descriptor().name();
            if (hasJava && mn.startsWith("java.")) {
                // not a root
                continue;
            }

            if (ModuleResolution.doNotResolveByDefault(mref)) {
                // not a root
                continue;
            }

            if ((finder == systemModuleFinder || finder.find(mn).isPresent())) {
                // add as root if exports at least one package to all modules
                ModuleDescriptor descriptor = mref.descriptor();
                for (ModuleDescriptor.Exports e : descriptor.exports()) {
                    if (!e.isQualified()) {
                        roots.add(mn);
                        break;
                    }
                }
            }
        }

        return roots;
    }

    /**
     * Returns the default set of root modules for the unnamed module from the
     * modules observable with the given module finder.
     */
    public static Set<String> compute(ModuleFinder finder) {
        return compute(finder, finder);
    }
}
