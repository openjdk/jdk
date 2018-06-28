/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.hotspot.meta;

import java.lang.module.ModuleDescriptor.Requires;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

/**
 * Determines if methods in a given class can be intrinsified.
 *
 * Only classes loaded from the module defining the compiler configuration or any of its transitive
 * dependencies can be intrinsified.
 *
 * This version of the class must be used on JDK 9 or later.
 */
public final class IntrinsificationPredicate {
    /**
     * Set of modules composed of the module defining the compiler configuration and its transitive
     * dependencies.
     */
    private final EconomicSet<Module> trustedModules;

    IntrinsificationPredicate(CompilerConfiguration compilerConfiguration) {
        trustedModules = EconomicSet.create();
        Module compilerConfigurationModule = compilerConfiguration.getClass().getModule();
        if (compilerConfigurationModule.getDescriptor().isAutomatic()) {
            throw new IllegalArgumentException(String.format("The module '%s' defining the Graal compiler configuration class '%s' must not be an automatic module",
                            compilerConfigurationModule.getName(), compilerConfiguration.getClass().getName()));
        }
        trustedModules.add(compilerConfigurationModule);
        for (Requires require : compilerConfigurationModule.getDescriptor().requires()) {
            for (Module module : compilerConfigurationModule.getLayer().modules()) {
                if (module.getName().equals(require.name())) {
                    trustedModules.add(module);
                }
            }
        }
    }

    public boolean apply(Class<?> declaringClass) {
        Module module = declaringClass.getModule();
        return trustedModules.contains(module);
    }
}
