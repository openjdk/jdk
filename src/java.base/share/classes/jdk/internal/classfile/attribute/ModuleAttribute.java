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

import java.lang.constant.ClassDesc;
import java.util.Collection;
import jdk.internal.classfile.Attribute;
import jdk.internal.classfile.ClassElement;
import jdk.internal.classfile.constantpool.ClassEntry;
import jdk.internal.classfile.constantpool.ModuleEntry;
import jdk.internal.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.lang.reflect.AccessFlag;
import jdk.internal.classfile.java.lang.constant.ModuleDesc;
import jdk.internal.classfile.java.lang.constant.PackageDesc;
import jdk.internal.classfile.impl.ModuleAttributeBuilderImpl;
import jdk.internal.classfile.impl.Util;

/**
 * Models the {@code Module} attribute {@jvms 4.7.25}, which can
 * appear on classes that represent module descriptors.
 * Delivered as a {@link jdk.internal.classfile.ClassElement} when
 * traversing the elements of a {@link jdk.internal.classfile.ClassModel}.
 */

public sealed interface ModuleAttribute
        extends Attribute<ModuleAttribute>, ClassElement
        permits BoundAttribute.BoundModuleAttribute, UnboundAttribute.UnboundModuleAttribute {

    /**
     * {@return the name of the module}
     */
    ModuleEntry moduleName();

    /**
     * {@return the the module flags of the module, as a bit mask}
     */
    int moduleFlagsMask();

    /**
     * {@return the the module flags of the module, as a set of enum constants}
     */
    default Set<AccessFlag> moduleFlags() {
        return AccessFlag.maskToAccessFlags(moduleFlagsMask(), AccessFlag.Location.MODULE);
    }

    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.MODULE, moduleFlagsMask(), flag);
    }

    /**
     * {@return version of the module, if present}
     */
    Optional<Utf8Entry> moduleVersion();

    /**
     * {@return the modules required by this module}
     */
    List<ModuleRequireInfo> requires();

    /**
     * {@return the packages exported by this module}
     */
    List<ModuleExportInfo> exports();

    /**
     * {@return the packages opened by this module}
     */
    List<ModuleOpenInfo> opens();

    /**
     * {@return the services used by this module}  Services may be discovered via
     * {@link java.util.ServiceLoader}.
     */
    List<ClassEntry> uses();

    /**
     * {@return the service implementations provided by this module}
     */
    List<ModuleProvideInfo> provides();

    /**
     * {@return a {@code Module} attribute}
     *
     * @param moduleName the module name
     * @param moduleFlags the module flags
     * @param moduleVersion the module version
     * @param requires the required packages
     * @param exports the exported packages
     * @param opens the opened packages
     * @param uses the consumed services
     * @param provides the provided services
     */
    static ModuleAttribute of(ModuleEntry moduleName, int moduleFlags,
                              Utf8Entry moduleVersion,
                              Collection<ModuleRequireInfo> requires,
                              Collection<ModuleExportInfo> exports,
                              Collection<ModuleOpenInfo> opens,
                              Collection<ClassEntry> uses,
                              Collection<ModuleProvideInfo> provides) {
        return new UnboundAttribute.UnboundModuleAttribute(moduleName, moduleFlags, moduleVersion, requires, exports, opens, uses, provides);
    }

    /**
     * {@return a {@code Module} attribute}
     *
     * @param moduleName the module name
     * @param attrHandler a handler that receives a {@link ModuleAttributeBuilder}
     */
    static ModuleAttribute of(ModuleDesc moduleName,
                              Consumer<ModuleAttributeBuilder> attrHandler) {
        var mb = new ModuleAttributeBuilderImpl(moduleName);
        attrHandler.accept(mb);
        return  mb.build();
    }

    /**
     * {@return a {@code Module} attribute}
     *
     * @param moduleName the module name
     * @param attrHandler a handler that receives a {@link ModuleAttributeBuilder}
     */
    static ModuleAttribute of(ModuleEntry moduleName,
                              Consumer<ModuleAttributeBuilder> attrHandler) {
        var mb = new ModuleAttributeBuilderImpl(moduleName);
        attrHandler.accept(mb);
        return  mb.build();
    }

    public sealed interface ModuleAttributeBuilder
            permits ModuleAttributeBuilderImpl {

        ModuleAttributeBuilder moduleName(ModuleDesc moduleName);
        ModuleAttributeBuilder moduleFlags(int flagsMask);
        default ModuleAttributeBuilder moduleFlags(AccessFlag... moduleFlags) {
            return moduleFlags(Util.flagsToBits(AccessFlag.Location.MODULE, moduleFlags));
        }
        ModuleAttributeBuilder moduleVersion(String version);

        ModuleAttributeBuilder requires(ModuleDesc module, int requiresFlagsMask, String version);
        default ModuleAttributeBuilder requires(ModuleDesc module, Collection<AccessFlag> requiresFlags, String version) {
            return requires(module, Util.flagsToBits(AccessFlag.Location.MODULE_REQUIRES, requiresFlags), version);
        }
        ModuleAttributeBuilder requires(ModuleRequireInfo requires);

        ModuleAttributeBuilder exports(PackageDesc pkge, int exportsFlagsMask, ModuleDesc... exportsToModules);
        default ModuleAttributeBuilder exports(PackageDesc pkge, Collection<AccessFlag> exportsFlags, ModuleDesc... exportsToModules) {
            return exports(pkge, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportsFlags), exportsToModules);
        }
        ModuleAttributeBuilder exports(ModuleExportInfo exports);

        ModuleAttributeBuilder opens(PackageDesc pkge, int opensFlagsMask, ModuleDesc... opensToModules);
        default ModuleAttributeBuilder opens(PackageDesc pkge, Collection<AccessFlag> opensFlags, ModuleDesc... opensToModules) {
            return opens(pkge, Util.flagsToBits(AccessFlag.Location.MODULE_OPENS, opensFlags), opensToModules);
        }
        ModuleAttributeBuilder opens(ModuleOpenInfo opens);

        ModuleAttributeBuilder uses(ClassDesc service);
        ModuleAttributeBuilder uses(ClassEntry uses);

        ModuleAttributeBuilder provides(ClassDesc service, ClassDesc... implClasses);
        ModuleAttributeBuilder provides(ModuleProvideInfo provides);

        ModuleAttribute build();
    }
}
