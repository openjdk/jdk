/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot.services;

import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.runtime.services.JVMCICompilerFactory;

/**
 * HotSpot extensions to {@link JVMCICompilerFactory}.
 */
public abstract class HotSpotJVMCICompilerFactory extends JVMCICompilerFactory {

    /**
     * Gets 0 or more prefixes identifying classes that should by compiled by C1 in simple mode
     * (i.e., {@code CompLevel_simple}) when HotSpot is running with tiered compilation. The
     * prefixes should be class or package names using "/" as the separator, e.g. "jdk/vm/ci".
     *
     * @return 0 or more Strings identifying packages that should by compiled by the first tier only
     *         or null if no redirection to C1 should be performed.
     */
    public String[] getTrivialPrefixes() {
        return null;
    }

    /**
     * Determines if this object may want to adjust the compilation level for a method that is being
     * scheduled by the VM for compilation. The legal return values and their meanings are:
     * <ul>
     * <li>0 - no adjustment</li>
     * <li>1 - adjust based on declaring class of method</li>
     * <li>2 - adjust based on declaring class, name and signature of method</li>
     * </ul>
     */
    public int getCompilationLevelAdjustment(HotSpotVMConfig config) {
        return config.compLevelAdjustmentNone;
    }

    /**
     * Potentially modifies the compilation level currently selected by the VM compilation policy
     * for a method.
     *
     * @param config object for reading HotSpot {@code CompLevel} enum values
     * @param declaringClass the class in which the method is declared
     * @param name the name of the method or {@code null} depending on the value that was returned
     *            by {@link #getCompilationLevelAdjustment(HotSpotVMConfig)}
     * @param signature the signature of the method or {@code null} depending on the value that was
     *            returned by {@link #getCompilationLevelAdjustment(HotSpotVMConfig)}
     * @param isOsr specifies if the compilation being scheduled in an OSR compilation
     * @param level the compilation level currently selected by the VM compilation policy
     * @return the compilation level to use for the compilation being scheduled (must be a valid
     *         {@code CompLevel} enum value)
     */
    public int adjustCompilationLevel(HotSpotVMConfig config, Class<?> declaringClass, String name, String signature, boolean isOsr, int level) {
        throw new InternalError("Should not reach here");
    }
}
