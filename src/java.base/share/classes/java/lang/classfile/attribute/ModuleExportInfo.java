/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.attribute;

import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.AccessFlag;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models a single "exports" declaration in the {@link ModuleAttribute}.
 *
 * @see ModuleAttribute#exports()
 * @see ModuleDescriptor.Exports
 * @jvms 4.7.25 The {@code Module} Attribute
 * @since 24
 */
public sealed interface ModuleExportInfo
        permits UnboundAttribute.UnboundModuleExportInfo {

    /**
     * {@return the exported package}
     *
     * @see ModuleDescriptor.Exports#source()
     */
    PackageEntry exportedPackage();

    /**
     * {@return the flags associated with this export declaration, as a bit mask}
     * It is in the range of unsigned short, {@code [0, 0xFFFF]}.
     *
     * @see ModuleDescriptor.Exports#modifiers()
     * @see AccessFlag.Location#MODULE_EXPORTS
     */
    int exportsFlagsMask();

    /**
     * {@return the flags associated with this export declaration, as a set of
     * flag enums}
     *
     * @throws IllegalArgumentException if the flags mask has any undefined bit set
     * @see ModuleDescriptor.Exports#accessFlags()
     * @see AccessFlag.Location#MODULE_EXPORTS
     */
    default Set<AccessFlag> exportsFlags() {
        return AccessFlag.maskToAccessFlags(exportsFlagsMask(), AccessFlag.Location.MODULE_EXPORTS);
    }

    /**
     * {@return whether the export declaration has the specified access flag set}
     *
     * @param flag the access flag
     * @see AccessFlag.Location#MODULE_EXPORTS
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.MODULE_EXPORTS, exportsFlagsMask(), flag);
    }

    /**
     * {@return the list of modules to which this package is exported, or empty
     * if this is an unqualified export}
     *
     * @see ModuleDescriptor.Exports#isQualified()
     * @see ModuleDescriptor.Exports#targets()
     */
    List<ModuleEntry> exportsTo();

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     */
    static ModuleExportInfo of(PackageEntry exports, int exportFlags,
                               List<ModuleEntry> exportsTo) {
        return new UnboundAttribute.UnboundModuleExportInfo(exports, exportFlags, exportsTo);
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#MODULE_EXPORTS} location
     */
    static ModuleExportInfo of(PackageEntry exports, Collection<AccessFlag> exportFlags,
                               List<ModuleEntry> exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     */
    static ModuleExportInfo of(PackageEntry exports,
                               int exportFlags,
                               ModuleEntry... exportsTo) {
        return of(exports, exportFlags, List.of(exportsTo));
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#MODULE_EXPORTS} location
     */
    static ModuleExportInfo of(PackageEntry exports,
                               Collection<AccessFlag> exportFlags,
                               ModuleEntry... exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     */
    static ModuleExportInfo of(PackageDesc exports, int exportFlags,
                               List<ModuleDesc> exportsTo) {
        return of(TemporaryConstantPool.INSTANCE.packageEntry(TemporaryConstantPool.INSTANCE.utf8Entry(exports.internalName())),
                exportFlags,
                Util.moduleEntryList(exportsTo));
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#MODULE_EXPORTS} location
     */
    static ModuleExportInfo of(PackageDesc exports, Collection<AccessFlag> exportFlags,
                               List<ModuleDesc> exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     */
    static ModuleExportInfo of(PackageDesc exports,
                               int exportFlags,
                               ModuleDesc... exportsTo) {
        return of(exports, exportFlags, List.of(exportsTo));
    }

    /**
     * {@return a module export description}
     *
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported, or empty
     *        if this is an unqualified export
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#MODULE_EXPORTS} location
     */
    static ModuleExportInfo of(PackageDesc exports,
                               Collection<AccessFlag> exportFlags,
                               ModuleDesc... exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }
}
