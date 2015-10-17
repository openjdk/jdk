/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import java.lang.reflect.*;

import jdk.vm.ci.meta.*;

/**
 * Implementation of {@link JavaMethod} for resolved HotSpot methods.
 */
public interface HotSpotResolvedJavaMethod extends ResolvedJavaMethod {

    /**
     * Returns true if this method has a {@code CallerSensitive} annotation.
     *
     * @return true if CallerSensitive annotation present, false otherwise
     */
    boolean isCallerSensitive();

    HotSpotResolvedObjectType getDeclaringClass();

    /**
     * Returns true if this method has a {@code ForceInline} annotation.
     *
     * @return true if ForceInline annotation present, false otherwise
     */
    boolean isForceInline();

    /**
     * Returns true if this method has a {@code DontInline} annotation.
     *
     * @return true if DontInline annotation present, false otherwise
     */
    boolean isDontInline();

    /**
     * Manually adds a DontInline annotation to this method.
     */
    void setNotInlineable();

    /**
     * Returns true if this method is one of the special methods that is ignored by security stack
     * walks.
     *
     * @return true if special method ignored by security stack walks, false otherwise
     */
    boolean ignoredBySecurityStackWalk();

    ResolvedJavaMethod uniqueConcreteMethod(HotSpotResolvedObjectType receiver);

    /**
     * Returns whether this method has compiled code.
     *
     * @return true if this method has compiled code, false otherwise
     */
    boolean hasCompiledCode();

    /**
     * @param level
     * @return true if the currently installed code was generated at {@code level}.
     */
    boolean hasCompiledCodeAtLevel(int level);

    default boolean isDefault() {
        if (isConstructor()) {
            return false;
        }
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    /**
     * Returns the offset of this method into the v-table. The method must have a v-table entry as
     * indicated by {@link #isInVirtualMethodTable(ResolvedJavaType)}, otherwise an exception is
     * thrown.
     *
     * @return the offset of this method into the v-table
     */
    int vtableEntryOffset(ResolvedJavaType resolved);

    int intrinsicId();

    /**
     * Allocates a compile id for this method by asking the VM for one.
     *
     * @param entryBCI entry bci
     * @return compile id
     */
    int allocateCompileId(int entryBCI);

    boolean hasCodeAtLevel(int entryBCI, int level);
}
