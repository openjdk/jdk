/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.classfile.attribute;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import java.lang.reflect.AccessFlag;
import java.lang.constant.ModuleDesc;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models a single "requires" declaration in the {@link ModuleAttribute}.
 */
public sealed interface ModuleRequireInfo
        permits UnboundAttribute.UnboundModuleRequiresInfo {

    /**
     * {@return The module on which the current module depends}
     */
    ModuleEntry requires();

    /**
     * {@return the flags associated with this require declaration, as a bit mask}
     * Valid flags include {@link jdk.internal.classfile.Classfile#ACC_TRANSITIVE},
     * {@link jdk.internal.classfile.Classfile#ACC_STATIC_PHASE},
     * {@link jdk.internal.classfile.Classfile#ACC_SYNTHETIC} and
     * {@link jdk.internal.classfile.Classfile#ACC_MANDATED}
     */
    int requiresFlagsMask();

    /**
     * {@return the access flags}
     */
    default Set<AccessFlag> requiresFlags() {
        return AccessFlag.maskToAccessFlags(requiresFlagsMask(), AccessFlag.Location.MODULE_REQUIRES);
    }

    /**
     * {@return the required version of the required module, if present}
     */
    Optional<Utf8Entry> requiresVersion();

    /**
     * {@return whether the specific access flag is set}
     * @param flag the access flag
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.MODULE_REQUIRES, requiresFlagsMask(), flag);
    }

    /**
     * {@return a module requirement description}
     * @param requires the required module
     * @param requiresFlags the require-specific flags
     * @param requiresVersion the required version
     */
    static ModuleRequireInfo of(ModuleEntry requires, int requiresFlags, Utf8Entry requiresVersion) {
        return new UnboundAttribute.UnboundModuleRequiresInfo(requires, requiresFlags, Optional.ofNullable(requiresVersion));
    }

    /**
     * {@return a module requirement description}
     * @param requires the required module
     * @param requiresFlags the require-specific flags
     * @param requiresVersion the required version
     */
    static ModuleRequireInfo of(ModuleEntry requires, Collection<AccessFlag> requiresFlags, Utf8Entry requiresVersion) {
        return of(requires, Util.flagsToBits(AccessFlag.Location.MODULE_REQUIRES, requiresFlags), requiresVersion);
    }

    /**
     * {@return a module requirement description}
     * @param requires the required module
     * @param requiresFlags the require-specific flags
     * @param requiresVersion the required version
     */
    static ModuleRequireInfo of(ModuleDesc requires, int requiresFlags, String requiresVersion) {
        return new UnboundAttribute.UnboundModuleRequiresInfo(TemporaryConstantPool.INSTANCE.moduleEntry(TemporaryConstantPool.INSTANCE.utf8Entry(requires.name())), requiresFlags, Optional.ofNullable(requiresVersion).map(s -> TemporaryConstantPool.INSTANCE.utf8Entry(s)));
    }

    /**
     * {@return a module requirement description}
     * @param requires the required module
     * @param requiresFlags the require-specific flags
     * @param requiresVersion the required version
     */
    static ModuleRequireInfo of(ModuleDesc requires, Collection<AccessFlag> requiresFlags, String requiresVersion) {
        return of(requires, Util.flagsToBits(AccessFlag.Location.MODULE_REQUIRES, requiresFlags), requiresVersion);
    }
}
