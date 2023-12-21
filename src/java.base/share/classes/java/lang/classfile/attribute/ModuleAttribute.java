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

import java.lang.constant.ClassDesc;
import java.util.Collection;
import java.lang.classfile.Attribute;
import java.lang.classfile.ClassElement;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ModuleEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import jdk.internal.classfile.impl.BoundAttribute;
import jdk.internal.classfile.impl.UnboundAttribute;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.lang.reflect.AccessFlag;
import java.lang.constant.ModuleDesc;
import java.lang.constant.PackageDesc;
import jdk.internal.classfile.impl.ModuleAttributeBuilderImpl;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the {@code Module} attribute {@jvms 4.7.25}, which can
 * appear on classes that represent module descriptors.
 * Delivered as a {@link java.lang.classfile.ClassElement} when
 * traversing the elements of a {@link java.lang.classfile.ClassModel}.
 * <p>
 * The attribute does not permit multiple instances in a given location.
 * Subsequent occurrence of the attribute takes precedence during the attributed
 * element build or transformation.
 * <p>
 * The attribute was introduced in the Java SE Platform version 9.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
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

    /**
     * Tests presence of module flag
     * @param flag the module flag
     * @return true if the flag is set
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.MODULE, moduleFlagsMask(), flag);
    }

    /**
     * {@return the version of the module, if present}
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

    /**
     * A builder for module attributes.
     *
     * @since 22
     */
    @PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
    public sealed interface ModuleAttributeBuilder
            permits ModuleAttributeBuilderImpl {

        /**
         * Sets the module name
         * @param moduleName the module name
         * @return this builder
         */
        ModuleAttributeBuilder moduleName(ModuleDesc moduleName);

        /**
         * Sets the module flags
         * @param flagsMask the module flags
         * @return this builder
         */
        ModuleAttributeBuilder moduleFlags(int flagsMask);

        /**
         * Sets the module flags
         * @param moduleFlags the module flags
         * @return this builder
         */
        default ModuleAttributeBuilder moduleFlags(AccessFlag... moduleFlags) {
            return moduleFlags(Util.flagsToBits(AccessFlag.Location.MODULE, moduleFlags));
        }

        /**
         * Sets the module version
         * @param version the module version
         * @return this builder
         */
        ModuleAttributeBuilder moduleVersion(String version);

        /**
         * Adds module requirement
         * @param module the required module
         * @param requiresFlagsMask the requires flags
         * @param version the required module version
         * @return this builder
         */
        ModuleAttributeBuilder requires(ModuleDesc module, int requiresFlagsMask, String version);

        /**
         * Adds module requirement
         * @param module the required module
         * @param requiresFlags the requires flags
         * @param version the required module version
         * @return this builder
         */
        default ModuleAttributeBuilder requires(ModuleDesc module, Collection<AccessFlag> requiresFlags, String version) {
            return requires(module, Util.flagsToBits(AccessFlag.Location.MODULE_REQUIRES, requiresFlags), version);
        }

        /**
         * Adds module requirement
         * @param requires the module require info
         * @return this builder
         */
        ModuleAttributeBuilder requires(ModuleRequireInfo requires);

        /**
         * Adds exported package
         * @param pkge the exported package
         * @param exportsFlagsMask the export flags
         * @param exportsToModules the modules to export to
         * @return this builder
         */
        ModuleAttributeBuilder exports(PackageDesc pkge, int exportsFlagsMask, ModuleDesc... exportsToModules);

        /**
         * Adds exported package
         * @param pkge the exported package
         * @param exportsFlags the export flags
         * @param exportsToModules the modules to export to
         * @return this builder
         */
        default ModuleAttributeBuilder exports(PackageDesc pkge, Collection<AccessFlag> exportsFlags, ModuleDesc... exportsToModules) {
            return exports(pkge, Util.flagsToBits(AccessFlag.Location.MODULE_EXPORTS, exportsFlags), exportsToModules);
        }

        /**
         * Adds exported package
         * @param exports the module export info
         * @return this builder
         */
        ModuleAttributeBuilder exports(ModuleExportInfo exports);

        /**
         * Opens package
         * @param pkge the opened package
         * @param opensFlagsMask the open package flags
         * @param opensToModules the modules to open to
         * @return this builder
         */
        ModuleAttributeBuilder opens(PackageDesc pkge, int opensFlagsMask, ModuleDesc... opensToModules);

        /**
         * Opens package
         * @param pkge the opened package
         * @param opensFlags the open package flags
         * @param opensToModules the modules to open to
         * @return this builder
         */
        default ModuleAttributeBuilder opens(PackageDesc pkge, Collection<AccessFlag> opensFlags, ModuleDesc... opensToModules) {
            return opens(pkge, Util.flagsToBits(AccessFlag.Location.MODULE_OPENS, opensFlags), opensToModules);
        }

        /**
         * Opens package
         * @param opens the module open info
         * @return this builder
         */
        ModuleAttributeBuilder opens(ModuleOpenInfo opens);

        /**
         * Declares use of a service
         * @param service the service class used
         * @return this builder
         * @throws IllegalArgumentException if {@code service} represents a primitive type
         */
        ModuleAttributeBuilder uses(ClassDesc service);

        /**
         * Declares use of a service
         * @param uses the service class used
         * @return this builder
         */
        ModuleAttributeBuilder uses(ClassEntry uses);

        /**
         * Declares provision of a service
         * @param service the service class provided
         * @param implClasses the implementation classes
         * @return this builder
         * @throws IllegalArgumentException if {@code service} or any of the {@code implClasses} represents a primitive type
         */
        ModuleAttributeBuilder provides(ClassDesc service, ClassDesc... implClasses);

        /**
         * Declares provision of a service
         * @param provides the module provides info
         * @return this builder
         */
        ModuleAttributeBuilder provides(ModuleProvideInfo provides);

        /**
         * Builds module attribute.
         * @return the module attribute
         */
        ModuleAttribute build();
    }
}
