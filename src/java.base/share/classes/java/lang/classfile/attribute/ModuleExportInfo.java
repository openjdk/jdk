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
package java.lang.classfile.attribute;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.PackageEntry;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import java.lang.reflect.AccessFlag;

import java.lang.classfile.ClassFile;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a single "exports" declaration in the {@link ModuleAttribute}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ModuleExportInfo
        permits UnboundAttribute.UnboundModuleExportInfo {

    /**
     * {@return the exported package}
     */
    PackageEntry exportedPackage();

    /**
     * {@return the flags associated with this export declaration, as a bit mask}
     * Valid flags include {@link ClassFile#ACC_SYNTHETIC} and
     * {@link ClassFile#ACC_MANDATED}.
     */
    int exportsFlagsMask();

    /**
     * {@return the flags associated with this export declaration, as a set of
     * flag values}
     */
    default Set<AccessFlag> exportsFlags() {
        return AccessFlag.maskToAccessFlags(exportsFlagsMask(), AccessFlag.Location.MODULE_EXPORTS);
    }

    /**
     * {@return the list of modules to which this package is exported, if it is a
     * qualified export}
     */
    List<ModuleEntry> exportsTo();

    /**
     * {@return whether the module has the specified access flag set}
     * @param flag the access flag
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.MODULE_EXPORTS, exportsFlagsMask(), flag);
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageEntry exports, int exportFlags,
                               List<ModuleEntry> exportsTo) {
        return new UnboundAttribute.UnboundModuleExportInfo(exports, exportFlags, exportsTo);
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageEntry exports, Collection<AccessFlag> exportFlags,
                               List<ModuleEntry> exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageEntry exports,
                               int exportFlags,
                               ModuleEntry... exportsTo) {
        return of(exports, exportFlags, List.of(exportsTo));
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageEntry exports,
                               Collection<AccessFlag> exportFlags,
                               ModuleEntry... exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageDesc exports, int exportFlags,
                               List<ModuleDesc> exportsTo) {
        return of(TemporaryConstantPool.INSTANCE.packageEntry(TemporaryConstantPool.INSTANCE.utf8Entry(exports.internalName())),
                exportFlags,
                Util.moduleEntryList(exportsTo));
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageDesc exports, Collection<AccessFlag> exportFlags,
                               List<ModuleDesc> exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags, as a bitmask
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageDesc exports,
                               int exportFlags,
                               ModuleDesc... exportsTo) {
        return of(exports, exportFlags, List.of(exportsTo));
    }

    /**
     * {@return a module export description}
     * @param exports the exported package
     * @param exportFlags the export flags
     * @param exportsTo the modules to which this package is exported
     */
    static ModuleExportInfo of(PackageDesc exports,
                               Collection<AccessFlag> exportFlags,
                               ModuleDesc... exportsTo) {
        return of(exports, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportFlags), exportsTo);
    }
}
