/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jlink.internal.plugins;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Version;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.ClassFileFormatVersion;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jdk.internal.module.Checks;
import jdk.internal.module.DefaultRoots;
import jdk.internal.module.Modules;
import jdk.internal.module.ModuleHashes;
import jdk.internal.module.ModuleInfo.Attributes;
import jdk.internal.module.ModuleInfoExtender;
import jdk.internal.module.ModuleReferenceImpl;
import jdk.internal.module.ModuleResolution;
import jdk.internal.module.ModuleTarget;

import java.lang.classfile.attribute.ModulePackagesAttribute;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import static java.lang.classfile.ClassFile.*;
import java.lang.classfile.CodeBuilder;

import jdk.tools.jlink.internal.ModuleSorter;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Jlink plugin to reconstitute module descriptors and other attributes for system
 * modules. The plugin generates implementations of SystemModules to avoid parsing
 * module-info.class files at startup. It also generates SystemModulesMap to return
 * the SystemModules implementation for a specific initial module.
 *
 * As a side effect, the plugin adds the ModulePackages class file attribute to the
 * module-info.class files that don't have the attribute.
 *
 * @see jdk.internal.module.SystemModuleFinders
 * @see jdk.internal.module.SystemModules
 */

public final class SystemModulesPlugin extends AbstractPlugin {
    private static final int CLASSFILE_VERSION =
            ClassFileFormatVersion.latest().major();
    private static final String SYSTEM_MODULES_MAP_CLASSNAME =
            "jdk/internal/module/SystemModulesMap";
    private static final String SYSTEM_MODULES_CLASS_PREFIX =
            "jdk/internal/module/SystemModules$";

    private static final String ALL_SYSTEM_MODULES_CLASSNAME =
            SYSTEM_MODULES_CLASS_PREFIX + "all";
    private static final String DEFAULT_SYSTEM_MODULES_CLASSNAME =
            SYSTEM_MODULES_CLASS_PREFIX + "default";
    private static final ClassDesc CD_ALL_SYSTEM_MODULES =
            ClassDesc.ofInternalName(ALL_SYSTEM_MODULES_CLASSNAME);
    private static final ClassDesc CD_SYSTEM_MODULES =
            ClassDesc.ofInternalName("jdk/internal/module/SystemModules");
    private static final ClassDesc CD_SYSTEM_MODULES_MAP =
            ClassDesc.ofInternalName(SYSTEM_MODULES_MAP_CLASSNAME);
    private static final MethodTypeDesc MTD_StringArray = MethodTypeDesc.of(CD_String.arrayType());
    private static final MethodTypeDesc MTD_SystemModules = MethodTypeDesc.of(CD_SYSTEM_MODULES);

    private int moduleDescriptorsPerMethod = 75;
    private boolean enabled;

    public SystemModulesPlugin() {
        super("system-modules");
        this.enabled = true;
    }

    @Override
    public Set<State> getState() {
        return enabled ? EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL)
                       : EnumSet.of(State.DISABLED);
    }

    @Override
    public boolean hasArguments() {
        return true;
    }

    @Override
    public void configure(Map<String, String> config) {
        String arg = config.get(getName());
        if (arg != null) {
            String[] split = arg.split("=");
            if (split.length != 2) {
                throw new IllegalArgumentException(getName() + ": " + arg);
            }
            if (!split[0].equals("batch-size")) {
                throw new IllegalArgumentException(getName() + ": " + arg);
            }
            this.moduleDescriptorsPerMethod = Integer.parseInt(split[1]);
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        if (!enabled) {
            throw new PluginException(getName() + " was set");
        }

        // validate, transform (if needed), and add the module-info.class files
        List<ModuleInfo> moduleInfos = transformModuleInfos(in, out);

        // generate and add the SystemModuleMap and SystemModules classes
        Set<String> generated = genSystemModulesClasses(moduleInfos, out);

        // pass through all other resources
        in.entries()
            .filter(data -> !data.path().endsWith("/module-info.class")
                    && !generated.contains(data.path()))
            .forEach(data -> out.add(data));

        return out.build();
    }

    /**
     * Validates and transforms the module-info.class files in the modules, adding
     * the ModulePackages class file attribute if needed.
     *
     * @return the list of ModuleInfo objects, the first element is java.base
     */
    List<ModuleInfo> transformModuleInfos(ResourcePool in, ResourcePoolBuilder out) {
        List<ModuleInfo> moduleInfos = new ArrayList<>();

        // Sort modules in the topological order so that java.base is always first.
        new ModuleSorter(in.moduleView()).sorted().forEach(module -> {
            ResourcePoolEntry data = module.findEntry("module-info.class").orElseThrow(
                // automatic modules not supported
                () ->  new PluginException("module-info.class not found for " +
                        module.name() + " module")
            );

            assert module.name().equals(data.moduleName());

            try {
                byte[] content = data.contentBytes();
                Set<String> packages = module.packages();
                ModuleInfo moduleInfo = new ModuleInfo(content, packages);

                // link-time validation
                moduleInfo.validateNames();

                // check if any exported or open package is not present
                moduleInfo.validatePackages();

                // module-info.class may be overridden to add ModulePackages
                if (moduleInfo.shouldRewrite()) {
                    data = data.copyWithContent(moduleInfo.getBytes());
                }
                moduleInfos.add(moduleInfo);

                // add resource pool entry
                out.add(data);
            } catch (IOException e) {
                throw new PluginException(e);
            }
        });

        return moduleInfos;
    }

    /**
     * Generates the SystemModules classes (at least one) and the SystemModulesMap
     * class to map initial modules to a SystemModules class.
     *
     * @return the resource names of the resources added to the pool
     */
    private Set<String> genSystemModulesClasses(List<ModuleInfo> moduleInfos,
                                                ResourcePoolBuilder out) {
        int moduleCount = moduleInfos.size();
        ModuleFinder finder = finderOf(moduleInfos);
        assert finder.findAll().size() == moduleCount;

        // map of initial module name to SystemModules class name
        Map<String, String> map = new LinkedHashMap<>();

        // the names of resources written to the pool
        Set<String> generated = new HashSet<>();

        // generate the SystemModules implementation to reconstitute all modules
        Set<String> allModuleNames = moduleInfos.stream()
                .map(ModuleInfo::moduleName)
                .collect(Collectors.toSet());
        String rn = genSystemModulesClass(moduleInfos,
                                          resolve(finder, allModuleNames),
                                          ALL_SYSTEM_MODULES_CLASSNAME,
                                          out);
        generated.add(rn);

        // generate, if needed, a SystemModules class to reconstitute the modules
        // needed for the case that the initial module is the unnamed module.
        String defaultSystemModulesClassName;
        Configuration cf = resolve(finder, DefaultRoots.compute(finder));
        if (cf.modules().size() == moduleCount) {
            // all modules are resolved so no need to generate a class
            defaultSystemModulesClassName = ALL_SYSTEM_MODULES_CLASSNAME;
        } else {
            defaultSystemModulesClassName = DEFAULT_SYSTEM_MODULES_CLASSNAME;
            rn = genSystemModulesClass(sublist(moduleInfos, cf),
                                       cf,
                                       defaultSystemModulesClassName,
                                       out);
            generated.add(rn);
        }

        // Generate a SystemModules class for each module with a main class
        int suffix = 0;
        for (ModuleInfo mi : moduleInfos) {
            if (mi.descriptor().mainClass().isPresent()) {
                String moduleName = mi.moduleName();
                cf = resolve(finder, Set.of(moduleName));
                if (cf.modules().size() == moduleCount) {
                    // resolves all modules so no need to generate a class
                    map.put(moduleName, ALL_SYSTEM_MODULES_CLASSNAME);
                } else {
                    String cn = SYSTEM_MODULES_CLASS_PREFIX + (suffix++);
                    rn = genSystemModulesClass(sublist(moduleInfos, cf), cf, cn, out);
                    map.put(moduleName, cn);
                    generated.add(rn);
                }
            }
        }

        // generate SystemModulesMap
        rn = genSystemModulesMapClass(CD_ALL_SYSTEM_MODULES,
                                      ClassDesc.ofInternalName(defaultSystemModulesClassName),
                                      map,
                                      out);
        generated.add(rn);

        // return the resource names of the generated classes
        return generated;
    }

    /**
     * Resolves a collection of root modules, with service binding, to create
     * a Configuration for the boot layer.
     */
    private Configuration resolve(ModuleFinder finder, Set<String> roots) {
        return Modules.newBootLayerConfiguration(finder, roots, null);
    }

    /**
     * Returns the list of ModuleInfo objects that correspond to the modules in
     * the given configuration.
     */
    private List<ModuleInfo> sublist(List<ModuleInfo> moduleInfos, Configuration cf) {
        Set<String> names = cf.modules()
                .stream()
                .map(ResolvedModule::name)
                .collect(Collectors.toSet());
        return moduleInfos.stream()
                .filter(mi -> names.contains(mi.moduleName()))
                .toList();
    }

    /**
     * Generate a SystemModules implementation class and add it as a resource.
     *
     * @return the name of the class resource added to the pool
     */
    private String genSystemModulesClass(List<ModuleInfo> moduleInfos,
                                         Configuration cf,
                                         String className,
                                         ResourcePoolBuilder out) {
        SystemModulesClassGenerator generator
            = new SystemModulesClassGenerator(className, moduleInfos, moduleDescriptorsPerMethod);
        byte[] bytes = generator.genClassBytes(cf);
        String rn = "/java.base/" + className + ".class";
        ResourcePoolEntry e = ResourcePoolEntry.create(rn, bytes);
        out.add(e);
        return rn;
    }

    static class ModuleInfo {
        private final ByteArrayInputStream bais;
        private final Attributes attrs;
        private final Set<String> packages;
        private final boolean addModulePackages;
        private ModuleDescriptor descriptor;  // may be different that the original one

        ModuleInfo(byte[] bytes, Set<String> packages) throws IOException {
            this.bais = new ByteArrayInputStream(bytes);
            this.packages = packages;
            this.attrs = jdk.internal.module.ModuleInfo.read(bais, null);

            // If ModulePackages attribute is present, the packages from this
            // module descriptor returns the packages in that attribute.
            // If it's not present, ModuleDescriptor::packages only contains
            // the exported and open packages from module-info.class
            this.descriptor = attrs.descriptor();
            if (descriptor.isAutomatic()) {
                throw new InternalError("linking automatic module is not supported");
            }

            // add ModulePackages attribute if this module contains some packages
            // and ModulePackages is not present
            this.addModulePackages = packages.size() > 0 && !hasModulePackages();
        }

        String moduleName() {
            return attrs.descriptor().name();
        }

        ModuleDescriptor descriptor() {
            return descriptor;
        }

        Set<String> packages() {
            return packages;
        }

        ModuleTarget target() {
            return attrs.target();
        }

        ModuleHashes recordedHashes() {
            return attrs.recordedHashes();
        }

        ModuleResolution moduleResolution() {
            return attrs.moduleResolution();
        }

        /**
         * Validates names in ModuleDescriptor
         */
        void validateNames() {
            Checks.requireModuleName(descriptor.name());
            for (Requires req : descriptor.requires()) {
                Checks.requireModuleName(req.name());
            }
            for (Exports e : descriptor.exports()) {
                Checks.requirePackageName(e.source());
                if (e.isQualified())
                    e.targets().forEach(Checks::requireModuleName);
            }
            for (Opens opens : descriptor.opens()) {
                Checks.requirePackageName(opens.source());
                if (opens.isQualified())
                    opens.targets().forEach(Checks::requireModuleName);
            }
            for (Provides provides : descriptor.provides()) {
                Checks.requireServiceTypeName(provides.service());
                provides.providers().forEach(Checks::requireServiceProviderName);
            }
            for (String service : descriptor.uses()) {
                Checks.requireServiceTypeName(service);
            }
            for (String pn : descriptor.packages()) {
                Checks.requirePackageName(pn);
            }
            for (String pn : packages) {
                Checks.requirePackageName(pn);
            }
        }

        /**
         * Validates if exported and open packages are present
         */
        void validatePackages() {
            Set<String> nonExistPackages = new TreeSet<>();
            descriptor.exports().stream()
                .map(Exports::source)
                .filter(pn -> !packages.contains(pn))
                .forEach(nonExistPackages::add);

            descriptor.opens().stream()
                .map(Opens::source)
                .filter(pn -> !packages.contains(pn))
                .forEach(nonExistPackages::add);

            if (!nonExistPackages.isEmpty()) {
                throw new PluginException("Packages that are exported or open in "
                    + descriptor.name() + " are not present: " + nonExistPackages);
            }
        }

        boolean hasModulePackages() throws IOException {
            try (InputStream in = getInputStream()) {
                // parse module-info.class
                return ClassFile.of().parse(in.readAllBytes()).elementStream()
                        .anyMatch(e -> e instanceof ModulePackagesAttribute mpa
                                    && !mpa.packages().isEmpty());
            }
        }

        /**
         * Returns true if module-info.class should be rewritten to add the
         * ModulePackages attribute.
         */
        boolean shouldRewrite() {
            return addModulePackages;
        }

        /**
         * Returns the bytes for the (possibly updated) module-info.class.
         */
        byte[] getBytes() throws IOException {
            try (InputStream in = getInputStream()) {
                if (shouldRewrite()) {
                    ModuleInfoRewriter rewriter = new ModuleInfoRewriter(in);
                    if (addModulePackages) {
                        rewriter.addModulePackages(packages);
                    }
                    // rewritten module descriptor
                    byte[] bytes = rewriter.getBytes();
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                        this.descriptor = ModuleDescriptor.read(bais);
                    }
                    return bytes;
                } else {
                    return in.readAllBytes();
                }
            }
        }

        /*
         * Returns the input stream of the module-info.class
         */
        InputStream getInputStream() {
            bais.reset();
            return bais;
        }

        static class ModuleInfoRewriter extends ByteArrayOutputStream {
            final ModuleInfoExtender extender;
            ModuleInfoRewriter(InputStream in) {
                this.extender = ModuleInfoExtender.newExtender(in);
            }

            void addModulePackages(Set<String> packages) {
                // Add ModulePackages attribute
                if (packages.size() > 0) {
                    extender.packages(packages);
                }
            }

            byte[] getBytes() throws IOException {
                extender.write(this);
                return buf;
            }
        }
    }

    /**
     * Generates a SystemModules class to reconstitute the ModuleDescriptor
     * and other attributes of system modules.
     */
    static class SystemModulesClassGenerator {
        private static final ClassDesc CD_MODULE_DESCRIPTOR =
            ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor");
        private static final ClassDesc CD_MODULE_BUILDER =
            ClassDesc.ofInternalName("jdk/internal/module/Builder");
        private static final ClassDesc CD_REQUIRES_MODIFIER =
            ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Requires$Modifier");
        private static final ClassDesc CD_EXPORTS_MODIFIER =
            ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Exports$Modifier");
        private static final ClassDesc CD_OPENS_MODIFIER =
            ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Opens$Modifier");
        private static final ClassDesc CD_MODULE_TARGET =
            ClassDesc.ofInternalName("jdk/internal/module/ModuleTarget");
        private static final ClassDesc CD_MODULE_HASHES =
            ClassDesc.ofInternalName("jdk/internal/module/ModuleHashes");
        private static final ClassDesc CD_MODULE_RESOLUTION =
            ClassDesc.ofInternalName("jdk/internal/module/ModuleResolution");
        private static final ClassDesc CD_Map_Entry = ClassDesc.ofInternalName("java/util/Map$Entry");
        private static final MethodTypeDesc MTD_boolean = MethodTypeDesc.of(CD_boolean);
        private static final MethodTypeDesc MTD_ModuleDescriptorArray = MethodTypeDesc.of(CD_MODULE_DESCRIPTOR.arrayType());
        private static final MethodTypeDesc MTD_ModuleTargetArray = MethodTypeDesc.of(CD_MODULE_TARGET.arrayType());
        private static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);
        private static final MethodTypeDesc MTD_void_int = MethodTypeDesc.of(CD_void, CD_int);
        private static final MethodTypeDesc MTD_ModuleHashesArray = MethodTypeDesc.of(CD_MODULE_HASHES.arrayType());
        private static final MethodTypeDesc MTD_ModuleResolutionArray = MethodTypeDesc.of(CD_MODULE_RESOLUTION.arrayType());
        private static final MethodTypeDesc MTD_Map = MethodTypeDesc.of(CD_Map);
        private static final MethodTypeDesc MTD_MapEntry_Object_Object = MethodTypeDesc.of(CD_Map_Entry, CD_Object, CD_Object);
        private static final MethodTypeDesc MTD_Map_MapEntryArray = MethodTypeDesc.of(CD_Map, CD_Map_Entry.arrayType());
        private static final MethodTypeDesc MTD_Set_ObjectArray = MethodTypeDesc.of(CD_Set, CD_Object.arrayType());

        private static final int MAX_LOCAL_VARS = 256;

        private final int MD_VAR         = 1;  // variable for ModuleDescriptor
        private final int MT_VAR         = 1;  // variable for ModuleTarget
        private final int MH_VAR         = 1;  // variable for ModuleHashes
        private final int DEDUP_LIST_VAR = 2;
        private final int BUILDER_VAR    = 3;
        private int nextLocalVar         = 4;  // index to next local variable

        // name of class to generate
        private final ClassDesc classDesc;

        // list of all ModuleInfos
        private final List<ModuleInfo> moduleInfos;

        private final int moduleDescriptorsPerMethod;

        // A builder to create one single Set instance for a given set of
        // names or modifiers to reduce the footprint
        // e.g. target modules of qualified exports
        private final DedupSetBuilder dedupSetBuilder
            = new DedupSetBuilder(this::getNextLocalVar);

        public SystemModulesClassGenerator(String className,
                                           List<ModuleInfo> moduleInfos,
                                           int moduleDescriptorsPerMethod) {
            this.classDesc = ClassDesc.ofInternalName(className);
            this.moduleInfos = moduleInfos;
            this.moduleDescriptorsPerMethod = moduleDescriptorsPerMethod;
            moduleInfos.forEach(mi -> dedups(mi.descriptor()));
        }

        private int getNextLocalVar() {
            return nextLocalVar++;
        }

        /*
         * Adds the given ModuleDescriptor to the system module list.
         * It performs link-time validation and prepares mapping from various
         * Sets to SetBuilders to emit an optimized number of sets during build.
         */
        private void dedups(ModuleDescriptor md) {
            // exports
            for (Exports e : md.exports()) {
                dedupSetBuilder.stringSet(e.targets());
                dedupSetBuilder.exportsModifiers(e.modifiers());
            }

            // opens
            for (Opens opens : md.opens()) {
                dedupSetBuilder.stringSet(opens.targets());
                dedupSetBuilder.opensModifiers(opens.modifiers());
            }

            // requires
            for (Requires r : md.requires()) {
                dedupSetBuilder.requiresModifiers(r.modifiers());
            }

            // uses
            dedupSetBuilder.stringSet(md.uses());
        }

        /**
         * Generate SystemModules class
         */
        public byte[] genClassBytes(Configuration cf) {
            return ClassFile.of().build(classDesc,
                    clb -> {
                        clb.withFlags(ACC_FINAL + ACC_SUPER)
                           .withInterfaceSymbols(List.of(CD_SYSTEM_MODULES))
                           .withVersion(CLASSFILE_VERSION, 0);

                        // generate <init>
                        genConstructor(clb);

                        // generate hasSplitPackages
                        genHasSplitPackages(clb);

                        // generate hasIncubatorModules
                        genIncubatorModules(clb);

                        // generate moduleDescriptors
                        genModuleDescriptorsMethod(clb);

                        // generate moduleTargets
                        genModuleTargetsMethod(clb);

                        // generate moduleHashes
                        genModuleHashesMethod(clb);

                        // generate moduleResolutions
                        genModuleResolutionsMethod(clb);

                        // generate moduleReads
                        genModuleReads(clb, cf);
                    });
        }

        /**
         * Generate bytecode for no-arg constructor
         */
        private void genConstructor(ClassBuilder clb) {
            clb.withMethodBody(
                    INIT_NAME,
                    MTD_void,
                    ACC_PUBLIC,
                    cob -> cob.aload(0)
                              .invokespecial(CD_Object,
                                             INIT_NAME,
                                             MTD_void)
                              .return_());
        }

        /**
         * Generate bytecode for hasSplitPackages method
         */
        private void genHasSplitPackages(ClassBuilder clb) {
            boolean distinct = moduleInfos.stream()
                    .map(ModuleInfo::packages)
                    .flatMap(Set::stream)
                    .allMatch(new HashSet<>()::add);
            boolean hasSplitPackages = !distinct;

            clb.withMethodBody(
                    "hasSplitPackages",
                    MTD_boolean,
                    ACC_PUBLIC,
                    cob -> cob.loadConstant(hasSplitPackages ? 1 : 0)
                              .ireturn());
        }

        /**
         * Generate bytecode for hasIncubatorModules method
         */
        private void genIncubatorModules(ClassBuilder clb) {
            boolean hasIncubatorModules = moduleInfos.stream()
                    .map(ModuleInfo::moduleResolution)
                    .filter(mres -> (mres != null && mres.hasIncubatingWarning()))
                    .findFirst()
                    .isPresent();

            clb.withMethodBody(
                    "hasIncubatorModules",
                    MTD_boolean,
                    ACC_PUBLIC,
                    cob -> cob.loadConstant(hasIncubatorModules ? 1 : 0)
                              .ireturn());
        }

        /**
         * Generate bytecode for moduleDescriptors method
         */
        private void genModuleDescriptorsMethod(ClassBuilder clb) {
            if (moduleInfos.size() <= moduleDescriptorsPerMethod) {
                clb.withMethodBody(
                        "moduleDescriptors",
                        MTD_ModuleDescriptorArray,
                        ACC_PUBLIC,
                        cob -> {
                            cob.loadConstant(moduleInfos.size())
                               .anewarray(CD_MODULE_DESCRIPTOR)
                               .astore(MD_VAR);

                            for (int index = 0; index < moduleInfos.size(); index++) {
                                ModuleInfo minfo = moduleInfos.get(index);
                                new ModuleDescriptorBuilder(cob,
                                                            minfo.descriptor(),
                                                            minfo.packages(),
                                                            index).build();
                            }
                            cob.aload(MD_VAR)
                               .areturn();
                        });
                return;
            }


            // Split the module descriptors be created by multiple helper methods.
            // Each helper method "subi" creates the maximum N number of module descriptors
            //     mi, m{i+1} ...
            // to avoid exceeding the 64kb limit of method length.  Then it will call
            // "sub{i+1}" to creates the next batch of module descriptors m{i+n}, m{i+n+1}...
            // and so on.  During the construction of the module descriptors, the string sets and
            // modifier sets are deduplicated (see SystemModulesClassGenerator.DedupSetBuilder)
            // and cached in the locals. These locals are saved in an array list so
            // that the helper method can restore the local variables that may be
            // referenced by the bytecode generated for creating module descriptors.
            // Pseudo code looks like this:
            //
            // void subi(ModuleDescriptor[] mdescs, ArrayList<Object> localvars) {
            //      // assign localvars to local variables
            //      var l3 = localvars.get(0);
            //      var l4 = localvars.get(1);
            //        :
            //      // fill mdescs[i] to mdescs[i+n-1]
            //      mdescs[i] = ...
            //      mdescs[i+1] = ...
            //        :
            //      // save new local variables added
            //      localvars.add(lx)
            //      localvars.add(l{x+1})
            //        :
            //      sub{i+i}(mdescs, localvars);
            // }

            List<List<ModuleInfo>> splitModuleInfos = new ArrayList<>();
            List<ModuleInfo> currentModuleInfos = null;
            for (int index = 0; index < moduleInfos.size(); index++) {
                if (index % moduleDescriptorsPerMethod == 0) {
                    currentModuleInfos = new ArrayList<>();
                    splitModuleInfos.add(currentModuleInfos);
                }
                currentModuleInfos.add(moduleInfos.get(index));
            }

            String helperMethodNamePrefix = "sub";
            ClassDesc arrayListClassDesc = ClassDesc.ofInternalName("java/util/ArrayList");

            clb.withMethodBody(
                    "moduleDescriptors",
                    MTD_ModuleDescriptorArray,
                    ACC_PUBLIC,
                    cob -> {
                        cob.loadConstant(moduleInfos.size())
                           .anewarray(CD_MODULE_DESCRIPTOR)
                           .dup()
                           .astore(MD_VAR);
                        cob.new_(arrayListClassDesc)
                           .dup()
                           .loadConstant(moduleInfos.size())
                           .invokespecial(arrayListClassDesc, INIT_NAME, MethodTypeDesc.of(CD_void, CD_int))
                           .astore(DEDUP_LIST_VAR);
                        cob.aload(0)
                           .aload(MD_VAR)
                           .aload(DEDUP_LIST_VAR)
                           .invokevirtual(
                                   this.classDesc,
                                   helperMethodNamePrefix + "0",
                                   MethodTypeDesc.of(CD_void, CD_MODULE_DESCRIPTOR.arrayType(), arrayListClassDesc)
                           )
                           .areturn();
                    });

            int dedupVarStart = nextLocalVar;
            for (int n = 0, count = 0; n < splitModuleInfos.size(); count += splitModuleInfos.get(n).size(), n++) {
                int index = n;       // the index of which ModuleInfo being processed in the current batch
                int start = count;   // the start index to the return ModuleDescriptor array for the current batch
                int curDedupVar = nextLocalVar;
                clb.withMethodBody(
                        helperMethodNamePrefix + index,
                        MethodTypeDesc.of(CD_void, CD_MODULE_DESCRIPTOR.arrayType(), arrayListClassDesc),
                        ACC_PUBLIC,
                        cob -> {
                            if (curDedupVar > dedupVarStart) {
                                for (int i = dedupVarStart; i < curDedupVar; i++) {
                                    cob.aload(DEDUP_LIST_VAR)
                                       .loadConstant(i - dedupVarStart)
                                       .invokevirtual(arrayListClassDesc, "get", MethodTypeDesc.of(CD_Object, CD_int))
                                       .astore(i);
                                }
                            }

                            List<ModuleInfo> currentBatch = splitModuleInfos.get(index);
                            for (int j = 0; j < currentBatch.size(); j++) {
                                ModuleInfo minfo = currentBatch.get(j);
                                new ModuleDescriptorBuilder(cob,
                                                            minfo.descriptor(),
                                                            minfo.packages(),
                                                            start + j).build();
                            }

                            if (index < splitModuleInfos.size() - 1) {
                                if (nextLocalVar > curDedupVar) {
                                    for (int i = curDedupVar; i < nextLocalVar; i++) {
                                        cob.aload(DEDUP_LIST_VAR)
                                           .aload(i)
                                           .invokevirtual(arrayListClassDesc, "add", MethodTypeDesc.of(CD_boolean, CD_Object))
                                           .pop();
                                    }
                                }
                                cob.aload(0)
                                   .aload(MD_VAR)
                                   .aload(DEDUP_LIST_VAR)
                                   .invokevirtual(
                                           this.classDesc,
                                           helperMethodNamePrefix + (index+1),
                                           MethodTypeDesc.of(CD_void, CD_MODULE_DESCRIPTOR.arrayType(), arrayListClassDesc)
                                   );
                            }

                            cob.return_();
                        });
            }
        }

        /**
         * Generate bytecode for moduleTargets method
         */
        private void genModuleTargetsMethod(ClassBuilder clb) {
            clb.withMethodBody(
                    "moduleTargets",
                    MTD_ModuleTargetArray,
                    ACC_PUBLIC,
                    cob -> {
                        cob.loadConstant(moduleInfos.size())
                           .anewarray(CD_MODULE_TARGET)
                           .astore(MT_VAR);

                        // if java.base has a ModuleTarget attribute then generate the array
                        // with one element, all other elements will be null.

                        ModuleInfo base = moduleInfos.get(0);
                        if (!base.moduleName().equals("java.base"))
                            throw new InternalError("java.base should be first module in list");
                        ModuleTarget target = base.target();

                        int count;
                        if (target != null && target.targetPlatform() != null) {
                            count = 1;
                        } else {
                            count = moduleInfos.size();
                        }

                        for (int index = 0; index < count; index++) {
                            ModuleInfo minfo = moduleInfos.get(index);
                            if (minfo.target() != null) {
                                cob.aload(MT_VAR)
                                   .loadConstant(index);

                                // new ModuleTarget(String)
                                cob.new_(CD_MODULE_TARGET)
                                   .dup()
                                   .loadConstant(minfo.target().targetPlatform())
                                   .invokespecial(CD_MODULE_TARGET,
                                                  INIT_NAME,
                                                  MTD_void_String);

                                cob.aastore();
                            }
                        }

                        cob.aload(MT_VAR)
                           .areturn();
                    });
        }

        /**
         * Generate bytecode for moduleHashes method
         */
        private void genModuleHashesMethod(ClassBuilder clb) {
            clb.withMethodBody(
                    "moduleHashes",
                    MTD_ModuleHashesArray,
                    ACC_PUBLIC,
                    cob -> {
                        cob.loadConstant(moduleInfos.size())
                           .anewarray(CD_MODULE_HASHES)
                           .astore(MH_VAR);

                        for (int index = 0; index < moduleInfos.size(); index++) {
                            ModuleInfo minfo = moduleInfos.get(index);
                            if (minfo.recordedHashes() != null) {
                                new ModuleHashesBuilder(minfo.recordedHashes(),
                                                        index,
                                                        cob).build();
                            }
                        }

                        cob.aload(MH_VAR)
                           .areturn();
                    });
        }

        /**
         * Generate bytecode for moduleResolutions method
         */
        private void genModuleResolutionsMethod(ClassBuilder clb) {
            clb.withMethodBody(
                    "moduleResolutions",
                    MTD_ModuleResolutionArray,
                    ACC_PUBLIC,
                    cob -> {
                        cob.loadConstant(moduleInfos.size())
                           .anewarray(CD_MODULE_RESOLUTION)
                           .astore(0);

                        for (int index=0; index < moduleInfos.size(); index++) {
                            ModuleInfo minfo = moduleInfos.get(index);
                            if (minfo.moduleResolution() != null) {
                                cob.aload(0)
                                   .loadConstant(index)
                                   .new_(CD_MODULE_RESOLUTION)
                                   .dup()
                                   .loadConstant(minfo.moduleResolution().value())
                                   .invokespecial(CD_MODULE_RESOLUTION,
                                                  INIT_NAME,
                                                  MTD_void_int)
                                   .aastore();
                            }
                        }
                        cob.aload(0)
                           .areturn();
                    });
        }

        /**
         * Generate bytecode for moduleReads method
         */
        private void genModuleReads(ClassBuilder clb, Configuration cf) {
            // module name -> names of modules that it reads
            Map<String, Set<String>> map = cf.modules().stream()
                    .collect(Collectors.toMap(
                            ResolvedModule::name,
                            m -> m.reads().stream()
                                    .map(ResolvedModule::name)
                                    .collect(Collectors.toSet())));
            generate(clb, "moduleReads", map, true);
        }

        /**
         * Generate method to return {@code Map<String, Set<String>>}.
         *
         * If {@code dedup} is true then the values are de-duplicated.
         */
        private void generate(ClassBuilder clb,
                              String methodName,
                              Map<String, Set<String>> map,
                              boolean dedup) {
            clb.withMethodBody(
                    methodName,
                    MTD_Map,
                    ACC_PUBLIC,
                    cob -> {

                        // map of Set -> local
                        Map<Set<String>, Integer> locals;

                        // generate code to create the sets that are duplicated
                        if (dedup) {
                            Collection<Set<String>> values = map.values();
                            Set<Set<String>> duplicateSets = values.stream()
                                    .distinct()
                                    .filter(s -> Collections.frequency(values, s) > 1)
                                    .collect(Collectors.toSet());
                            locals = new HashMap<>();
                            int index = 1;
                            for (Set<String> s : duplicateSets) {
                                genImmutableSet(cob, s);
                                cob.astore(index);
                                locals.put(s, index);
                                if (++index >= MAX_LOCAL_VARS) {
                                    break;
                                }
                            }
                        } else {
                            locals = Map.of();
                        }

                        // new Map$Entry[size]
                        cob.loadConstant(map.size())
                           .anewarray(CD_Map_Entry);

                        int index = 0;
                        for (var e : new TreeMap<>(map).entrySet()) {
                            String name = e.getKey();
                            Set<String> s = e.getValue();

                            cob.dup()
                               .loadConstant(index)
                               .loadConstant(name);

                            // if de-duplicated then load the local, otherwise generate code
                            Integer varIndex = locals.get(s);
                            if (varIndex == null) {
                                genImmutableSet(cob, s);
                            } else {
                                cob.aload(varIndex);
                            }

                            cob.invokestatic(CD_Map,
                                             "entry",
                                             MTD_MapEntry_Object_Object,
                                             true)
                               .aastore();
                            index++;
                        }

                        // invoke Map.ofEntries(Map$Entry[])
                        cob.invokestatic(CD_Map,
                                         "ofEntries",
                                         MTD_Map_MapEntryArray,
                                         true)
                           .areturn();
                    });
        }

        /**
         * Generate code to generate an immutable set.
         */
        private void genImmutableSet(CodeBuilder cob, Set<String> set) {
            int size = set.size();

            // use Set.of(Object[]) when there are more than 2 elements
            // use Set.of(Object) or Set.of(Object, Object) when fewer
            if (size > 2) {
                cob.loadConstant(size)
                   .anewarray(CD_String);
                int i = 0;
                for (String element : sorted(set)) {
                    cob.dup()
                       .loadConstant(i)
                       .loadConstant(element)
                       .aastore();
                    i++;
                }
                cob.invokestatic(CD_Set,
                                 "of",
                                 MTD_Set_ObjectArray,
                                 true);
            } else {
                for (String element : sorted(set)) {
                    cob.loadConstant(element);
                }
                var mtdArgs = new ClassDesc[size];
                Arrays.fill(mtdArgs, CD_Object);
                cob.invokestatic(CD_Set,
                                 "of",
                                 MethodTypeDesc.of(CD_Set, mtdArgs),
                                 true);
            }
        }

        class ModuleDescriptorBuilder {
            static final ClassDesc CD_EXPORTS =
                ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Exports");
            static final ClassDesc CD_OPENS =
                ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Opens");
            static final ClassDesc CD_PROVIDES =
                ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Provides");
            static final ClassDesc CD_REQUIRES =
                ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor$Requires");

            // method signature for static Builder::newExports, newOpens,
            // newProvides, newRequires methods
            static final MethodTypeDesc MTD_EXPORTS_MODIFIER_SET_STRING_SET =
                MethodTypeDesc.of(CD_EXPORTS, CD_Set, CD_String, CD_Set);
            static final MethodTypeDesc MTD_EXPORTS_MODIFIER_SET_STRING =
                MethodTypeDesc.of(CD_EXPORTS, CD_Set, CD_String);
            static final MethodTypeDesc MTD_OPENS_MODIFIER_SET_STRING_SET =
                MethodTypeDesc.of(CD_OPENS, CD_Set, CD_String, CD_Set);
            static final MethodTypeDesc MTD_OPENS_MODIFIER_SET_STRING =
                MethodTypeDesc.of(CD_OPENS, CD_Set, CD_String);
            static final MethodTypeDesc MTD_PROVIDES_STRING_LIST =
                MethodTypeDesc.of(CD_PROVIDES, CD_String, CD_List);
            static final MethodTypeDesc MTD_REQUIRES_SET_STRING =
                MethodTypeDesc.of(CD_REQUIRES, CD_Set, CD_String);
            static final MethodTypeDesc MTD_REQUIRES_SET_STRING_STRING =
                MethodTypeDesc.of(CD_REQUIRES, CD_Set, CD_String, CD_String);

            // method signature for Builder instance methods that
            // return this Builder instance
            static final MethodTypeDesc MTD_EXPORTS_ARRAY =
                MethodTypeDesc.of(CD_MODULE_BUILDER, CD_EXPORTS.arrayType());
            static final MethodTypeDesc MTD_OPENS_ARRAY =
                MethodTypeDesc.of(CD_MODULE_BUILDER, CD_OPENS.arrayType());
            static final MethodTypeDesc MTD_PROVIDES_ARRAY =
                MethodTypeDesc.of(CD_MODULE_BUILDER, CD_PROVIDES.arrayType());
            static final MethodTypeDesc MTD_REQUIRES_ARRAY =
                MethodTypeDesc.of(CD_MODULE_BUILDER, CD_REQUIRES.arrayType());
            static final MethodTypeDesc MTD_SET = MethodTypeDesc.of(CD_MODULE_BUILDER, CD_Set);
            static final MethodTypeDesc MTD_STRING = MethodTypeDesc.of(CD_MODULE_BUILDER, CD_String);
            static final MethodTypeDesc MTD_BOOLEAN = MethodTypeDesc.of(CD_MODULE_BUILDER, CD_boolean);
            static final MethodTypeDesc MTD_void_String = MethodTypeDesc.of(CD_void, CD_String);
            static final MethodTypeDesc MTD_ModuleDescriptor_int = MethodTypeDesc.of(CD_MODULE_DESCRIPTOR, CD_int);
            static final MethodTypeDesc MTD_List_ObjectArray = MethodTypeDesc.of(CD_List, CD_Object.arrayType());

            final CodeBuilder cob;
            final ModuleDescriptor md;
            final Set<String> packages;
            final int index;

            ModuleDescriptorBuilder(CodeBuilder cob, ModuleDescriptor md, Set<String> packages, int index) {
                if (md.isAutomatic()) {
                    throw new InternalError("linking automatic module is not supported");
                }
                this.cob = cob;
                this.md = md;
                this.packages = packages;
                this.index = index;
            }

            void build() {
                // new jdk.internal.module.Builder
                newBuilder();

                // requires
                requires(md.requires());

                // exports
                exports(md.exports());

                // opens
                opens(md.opens());

                // uses
                uses(md.uses());

                // provides
                provides(md.provides());

                // all packages
                packages(packages);

                // version
                md.version().ifPresent(this::version);

                // main class
                md.mainClass().ifPresent(this::mainClass);

                putModuleDescriptor();
            }

            void newBuilder() {
                cob.new_(CD_MODULE_BUILDER)
                   .dup()
                   .loadConstant(md.name())
                   .invokespecial(CD_MODULE_BUILDER,
                                  INIT_NAME,
                                  MTD_void_String)
                   .astore(BUILDER_VAR);

                if (md.isOpen()) {
                    setModuleBit("open", true);
                }
                if (md.modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC)) {
                    setModuleBit("synthetic", true);
                }
                if (md.modifiers().contains(ModuleDescriptor.Modifier.MANDATED)) {
                    setModuleBit("mandated", true);
                }
            }

            /*
             * Invoke Builder.<methodName>(boolean value)
             */
            void setModuleBit(String methodName, boolean value) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(value ? 1 : 0)
                   .invokevirtual(CD_MODULE_BUILDER,
                                  methodName,
                                  MTD_BOOLEAN)
                   .pop();
            }

            /*
             * Put ModuleDescriptor into the modules array
             */
            void putModuleDescriptor() {
                cob.aload(MD_VAR)
                   .loadConstant(index)
                   .aload(BUILDER_VAR)
                   .loadConstant(md.hashCode())
                   .invokevirtual(CD_MODULE_BUILDER,
                                  "build",
                                  MTD_ModuleDescriptor_int)
                   .aastore();
            }

            /*
             * Call Builder::newRequires to create Requires instances and
             * then pass it to the builder by calling:
             *      Builder.requires(Requires[])
             *
             */
            void requires(Set<Requires> requires) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(requires.size())
                   .anewarray(CD_REQUIRES);
                int arrayIndex = 0;
                for (Requires require : sorted(requires)) {
                    String compiledVersion = null;
                    if (require.compiledVersion().isPresent()) {
                        compiledVersion = require.compiledVersion().get().toString();
                    }

                    cob.dup()               // arrayref
                       .loadConstant(arrayIndex++);
                    newRequires(require.modifiers(), require.name(), compiledVersion);
                    cob.aastore();
                }
                cob.invokevirtual(CD_MODULE_BUILDER,
                                  "requires",
                                  MTD_REQUIRES_ARRAY)
                    .pop();
            }

            /*
             * Invoke Builder.newRequires(Set<Modifier> mods, String mn, String compiledVersion)
             *
             * Set<Modifier> mods = ...
             * Builder.newRequires(mods, mn, compiledVersion);
             */
            void newRequires(Set<Requires.Modifier> mods, String name, String compiledVersion) {
                int varIndex = dedupSetBuilder.indexOfRequiresModifiers(cob, mods);
                cob.aload(varIndex)
                   .loadConstant(name);
                if (compiledVersion != null) {
                    cob.loadConstant(compiledVersion)
                       .invokestatic(CD_MODULE_BUILDER,
                                     "newRequires",
                                     MTD_REQUIRES_SET_STRING_STRING);
                } else {
                    cob.invokestatic(CD_MODULE_BUILDER,
                                     "newRequires",
                                     MTD_REQUIRES_SET_STRING);
                }
            }

            /*
             * Call Builder::newExports to create Exports instances and
             * then pass it to the builder by calling:
             *      Builder.exports(Exports[])
             *
             */
            void exports(Set<Exports> exports) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(exports.size())
                   .anewarray(CD_EXPORTS);
                int arrayIndex = 0;
                for (Exports export : sorted(exports)) {
                    cob.dup()    // arrayref
                       .loadConstant(arrayIndex++);
                    newExports(export.modifiers(), export.source(), export.targets());
                    cob.aastore();
                }
                cob.invokevirtual(CD_MODULE_BUILDER,
                                  "exports",
                                  MTD_EXPORTS_ARRAY)
                    .pop();
            }

            /*
             * Invoke
             *     Builder.newExports(Set<Exports.Modifier> ms, String pn,
             *                        Set<String> targets)
             * or
             *     Builder.newExports(Set<Exports.Modifier> ms, String pn)
             *
             * Set<String> targets = new HashSet<>();
             * targets.add(t);
             * :
             * :
             *
             * Set<Modifier> mods = ...
             * Builder.newExports(mods, pn, targets);
             */
            void newExports(Set<Exports.Modifier> ms, String pn, Set<String> targets) {
                int modifiersSetIndex = dedupSetBuilder.indexOfExportsModifiers(cob, ms);
                if (!targets.isEmpty()) {
                    int stringSetIndex = dedupSetBuilder.indexOfStringSet(cob, targets);
                    cob.aload(modifiersSetIndex)
                       .loadConstant(pn)
                       .aload(stringSetIndex)
                       .invokestatic(CD_MODULE_BUILDER,
                                     "newExports",
                                     MTD_EXPORTS_MODIFIER_SET_STRING_SET);
                } else {
                    cob.aload(modifiersSetIndex)
                       .loadConstant(pn)
                       .invokestatic(CD_MODULE_BUILDER,
                                     "newExports",
                                     MTD_EXPORTS_MODIFIER_SET_STRING);
                }
            }


            /**
             * Call Builder::newOpens to create Opens instances and
             * then pass it to the builder by calling:
             * Builder.opens(Opens[])
             */
            void opens(Set<Opens> opens) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(opens.size())
                   .anewarray(CD_OPENS);
                int arrayIndex = 0;
                for (Opens open : sorted(opens)) {
                    cob.dup()    // arrayref
                       .loadConstant(arrayIndex++);
                    newOpens(open.modifiers(), open.source(), open.targets());
                    cob.aastore();
                }
                cob.invokevirtual(CD_MODULE_BUILDER,
                                  "opens",
                                  MTD_OPENS_ARRAY)
                    .pop();
            }

            /*
             * Invoke
             *     Builder.newOpens(Set<Opens.Modifier> ms, String pn,
             *                        Set<String> targets)
             * or
             *     Builder.newOpens(Set<Opens.Modifier> ms, String pn)
             *
             * Set<String> targets = new HashSet<>();
             * targets.add(t);
             * :
             * :
             *
             * Set<Modifier> mods = ...
             * Builder.newOpens(mods, pn, targets);
             */
            void newOpens(Set<Opens.Modifier> ms, String pn, Set<String> targets) {
                int modifiersSetIndex = dedupSetBuilder.indexOfOpensModifiers(cob, ms);
                if (!targets.isEmpty()) {
                    int stringSetIndex = dedupSetBuilder.indexOfStringSet(cob, targets);
                    cob.aload(modifiersSetIndex)
                       .loadConstant(pn)
                       .aload(stringSetIndex)
                       .invokestatic(CD_MODULE_BUILDER,
                                     "newOpens",
                                     MTD_OPENS_MODIFIER_SET_STRING_SET);
                } else {
                    cob.aload(modifiersSetIndex)
                       .loadConstant(pn)
                       .invokestatic(CD_MODULE_BUILDER,
                                     "newOpens",
                                     MTD_OPENS_MODIFIER_SET_STRING);
                }
            }

            /*
             * Invoke Builder.uses(Set<String> uses)
             */
            void uses(Set<String> uses) {
                int varIndex = dedupSetBuilder.indexOfStringSet(cob, uses);
                cob.aload(BUILDER_VAR)
                   .aload(varIndex)
                   .invokevirtual(CD_MODULE_BUILDER,
                                  "uses",
                                  MTD_SET)
                   .pop();
            }

            /*
            * Call Builder::newProvides to create Provides instances and
            * then pass it to the builder by calling:
            *      Builder.provides(Provides[] provides)
            *
            */
            void provides(Collection<Provides> provides) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(provides.size())
                   .anewarray(CD_PROVIDES);
                int arrayIndex = 0;
                for (Provides provide : sorted(provides)) {
                    cob.dup()    // arrayref
                       .loadConstant(arrayIndex++);
                    newProvides(provide.service(), provide.providers());
                    cob.aastore();
                }
                cob.invokevirtual(CD_MODULE_BUILDER,
                                  "provides",
                                  MTD_PROVIDES_ARRAY)
                    .pop();
            }

            /*
             * Invoke Builder.newProvides(String service, Set<String> providers)
             *
             * Set<String> providers = new HashSet<>();
             * providers.add(impl);
             * :
             * :
             * Builder.newProvides(service, providers);
             */
            void newProvides(String service, List<String> providers) {
                cob.loadConstant(service)
                   .loadConstant(providers.size())
                   .anewarray(CD_String);
                int arrayIndex = 0;
                for (String provider : providers) {
                    cob.dup()    // arrayref
                       .loadConstant(arrayIndex++)
                       .loadConstant(provider)
                       .aastore();
                }
                cob.invokestatic(CD_List,
                                 "of",
                                 MTD_List_ObjectArray,
                                 true)
                   .invokestatic(CD_MODULE_BUILDER,
                                 "newProvides",
                                 MTD_PROVIDES_STRING_LIST);
            }

            /*
             * Invoke Builder.packages(String pn)
             */
            void packages(Set<String> packages) {
                int varIndex = dedupSetBuilder.newStringSet(cob, packages);
                cob.aload(BUILDER_VAR)
                   .aload(varIndex)
                   .invokevirtual(CD_MODULE_BUILDER,
                                  "packages",
                                  MTD_SET)
                   .pop();
            }

            /*
             * Invoke Builder.mainClass(String cn)
             */
            void mainClass(String cn) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(cn)
                   .invokevirtual(CD_MODULE_BUILDER,
                                  "mainClass",
                                  MTD_STRING)
                   .pop();
            }

            /*
             * Invoke Builder.version(Version v);
             */
            void version(Version v) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(v.toString())
                   .invokevirtual(CD_MODULE_BUILDER,
                                  "version",
                                  MTD_STRING)
                   .pop();
            }

            void invokeBuilderMethod(String methodName, String value) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(value)
                   .invokevirtual(CD_MODULE_BUILDER,
                                  methodName,
                                  MTD_STRING)
                   .pop();
            }
        }

        class ModuleHashesBuilder {
            private static final ClassDesc MODULE_HASHES_BUILDER =
                ClassDesc.ofInternalName("jdk/internal/module/ModuleHashes$Builder");
            static final MethodTypeDesc STRING_BYTE_ARRAY_SIG =
                MethodTypeDesc.of(MODULE_HASHES_BUILDER, CD_String, CD_byte.arrayType());
            static final MethodTypeDesc MTD_void_String_int = MethodTypeDesc.of(CD_void, CD_String, CD_int);
            static final MethodTypeDesc MTD_ModuleHashes = MethodTypeDesc.of(CD_MODULE_HASHES);

            final ModuleHashes recordedHashes;
            final CodeBuilder cob;
            final int index;

            ModuleHashesBuilder(ModuleHashes hashes, int index, CodeBuilder cob) {
                this.recordedHashes = hashes;
                this.cob = cob;
                this.index = index;
            }

            /**
             * Build ModuleHashes
             */
            void build() {
                if (recordedHashes == null)
                    return;

                // new jdk.internal.module.ModuleHashes.Builder
                newModuleHashesBuilder();

                // Invoke ModuleHashes.Builder::hashForModule
                recordedHashes
                    .names()
                    .stream()
                    .sorted()
                    .forEach(mn -> hashForModule(mn, recordedHashes.hashFor(mn)));

                // Put ModuleHashes into the hashes array
                pushModuleHashes();
            }


            /*
             * Create ModuleHashes.Builder instance
             */
            void newModuleHashesBuilder() {
                cob.new_(MODULE_HASHES_BUILDER)
                   .dup()
                   .loadConstant(recordedHashes.algorithm())
                   .loadConstant(((4 * recordedHashes.names().size()) / 3) + 1)
                   .invokespecial(MODULE_HASHES_BUILDER,
                                  INIT_NAME,
                                  MTD_void_String_int)
                   .astore(BUILDER_VAR)
                   .aload(BUILDER_VAR);
            }


            /*
             * Invoke ModuleHashes.Builder::build and put the returned
             * ModuleHashes to the hashes array
             */
            void pushModuleHashes() {
                cob.aload(MH_VAR)
                   .loadConstant(index)
                   .aload(BUILDER_VAR)
                   .invokevirtual(MODULE_HASHES_BUILDER,
                                  "build",
                                  MTD_ModuleHashes)
                   .aastore();
            }

            /*
             * Invoke ModuleHashes.Builder.hashForModule(String name, byte[] hash);
             */
            void hashForModule(String name, byte[] hash) {
                cob.aload(BUILDER_VAR)
                   .loadConstant(name)
                   .loadConstant(hash.length)
                   .newarray(TypeKind.ByteType);
                for (int i = 0; i < hash.length; i++) {
                    cob.dup()              // arrayref
                       .loadConstant(i)
                       .loadConstant((int)hash[i])
                       .bastore();
                }

                cob.invokevirtual(MODULE_HASHES_BUILDER,
                                  "hashForModule",
                                  STRING_BYTE_ARRAY_SIG)
                   .pop();
            }
        }

        /*
         * Wraps set creation, ensuring identical sets are properly deduplicated.
         */
        static class DedupSetBuilder {
            // map Set<String> to a specialized builder to allow them to be
            // deduplicated as they are requested
            final Map<Set<String>, SetBuilder<String>> stringSets = new HashMap<>();

            // map Set<Requires.Modifier> to a specialized builder to allow them to be
            // deduplicated as they are requested
            final Map<Set<Requires.Modifier>, EnumSetBuilder<Requires.Modifier>>
                requiresModifiersSets = new HashMap<>();

            // map Set<Exports.Modifier> to a specialized builder to allow them to be
            // deduplicated as they are requested
            final Map<Set<Exports.Modifier>, EnumSetBuilder<Exports.Modifier>>
                exportsModifiersSets = new HashMap<>();

            // map Set<Opens.Modifier> to a specialized builder to allow them to be
            // deduplicated as they are requested
            final Map<Set<Opens.Modifier>, EnumSetBuilder<Opens.Modifier>>
                opensModifiersSets = new HashMap<>();

            private final int stringSetVar;
            private final int enumSetVar;
            private final IntSupplier localVarSupplier;

            DedupSetBuilder(IntSupplier localVarSupplier) {
                this.stringSetVar = localVarSupplier.getAsInt();
                this.enumSetVar = localVarSupplier.getAsInt();
                this.localVarSupplier = localVarSupplier;
            }

            /*
             * Add the given set of strings to this builder.
             */
            void stringSet(Set<String> strings) {
                stringSets.computeIfAbsent(strings,
                    s -> new SetBuilder<>(s, stringSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Add the given set of Exports.Modifiers
             */
            void exportsModifiers(Set<Exports.Modifier> mods) {
                exportsModifiersSets.computeIfAbsent(mods, s ->
                                new EnumSetBuilder<>(s, CD_EXPORTS_MODIFIER,
                                        enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Add the given set of Opens.Modifiers
             */
            void opensModifiers(Set<Opens.Modifier> mods) {
                opensModifiersSets.computeIfAbsent(mods, s ->
                                new EnumSetBuilder<>(s, CD_OPENS_MODIFIER,
                                        enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Add the given set of Requires.Modifiers
             */
            void requiresModifiers(Set<Requires.Modifier> mods) {
                requiresModifiersSets.computeIfAbsent(mods, s ->
                    new EnumSetBuilder<>(s, CD_REQUIRES_MODIFIER,
                                         enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Retrieve the index to the given set of Strings. Emit code to
             * generate it when SetBuilder::build is called.
             */
            int indexOfStringSet(CodeBuilder cob, Set<String> names) {
                return stringSets.get(names).build(cob);
            }

            /*
             * Retrieve the index to the given set of Exports.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfExportsModifiers(CodeBuilder cob, Set<Exports.Modifier> mods) {
                return exportsModifiersSets.get(mods).build(cob);
            }

            /**
             * Retrieve the index to the given set of Opens.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfOpensModifiers(CodeBuilder cob, Set<Opens.Modifier> mods) {
                return opensModifiersSets.get(mods).build(cob);
            }


            /*
             * Retrieve the index to the given set of Requires.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfRequiresModifiers(CodeBuilder cob, Set<Requires.Modifier> mods) {
                return requiresModifiersSets.get(mods).build(cob);
            }

            /*
             * Build a new string set without any attempt to deduplicate it.
             */
            int newStringSet(CodeBuilder cob, Set<String> names) {
                int index = new SetBuilder<>(names, stringSetVar, localVarSupplier).build(cob);
                assert index == stringSetVar;
                return index;
            }
        }

        /*
         * SetBuilder generates bytecode to create one single instance of Set
         * for a given set of elements and assign to a local variable slot.
         * When there is only one single reference to a Set<T>,
         * it will reuse defaultVarIndex.  For a Set with multiple references,
         * it will use a new local variable retrieved from the nextLocalVar
         */
        static class SetBuilder<T extends Comparable<T>> {
            private static final MethodTypeDesc MTD_Set_ObjectArray = MethodTypeDesc.of(
                    CD_Set, CD_Object.arrayType());

            private final Set<T> elements;
            private final int defaultVarIndex;
            private final IntSupplier nextLocalVar;
            private int refCount;
            private int localVarIndex;

            SetBuilder(Set<T> elements,
                       int defaultVarIndex,
                       IntSupplier nextLocalVar) {
                this.elements = elements;
                this.defaultVarIndex = defaultVarIndex;
                this.nextLocalVar = nextLocalVar;
            }

            /*
             * Increments the number of references to this particular set.
             */
            final void increment() {
                refCount++;
            }

            /**
             * Generate the appropriate instructions to load an object reference
             * to the element onto the stack.
             */
            void visitElement(T element, CodeBuilder cob) {
                cob.loadConstant((ConstantDesc)element);
            }

            /*
             * Build bytecode for the Set represented by this builder,
             * or get the local variable index of a previously generated set
             * (in the local scope).
             *
             * @return local variable index of the generated set.
             */
            final int build(CodeBuilder cob) {
                int index = localVarIndex;
                if (localVarIndex == 0) {
                    // if non-empty and more than one set reference this builder,
                    // emit to a unique local
                    index = refCount <= 1 ? defaultVarIndex
                                          : nextLocalVar.getAsInt();
                    if (index < MAX_LOCAL_VARS) {
                        localVarIndex = index;
                    } else {
                        // overflow: disable optimization by using localVarIndex = 0
                        index = defaultVarIndex;
                    }

                    generateSetOf(cob, index);
                }
                return index;
            }

            private void generateSetOf(CodeBuilder cob, int index) {
                if (elements.size() <= 10) {
                    // call Set.of(e1, e2, ...)
                    for (T t : sorted(elements)) {
                        visitElement(t, cob);
                    }
                    var mtdArgs = new ClassDesc[elements.size()];
                    Arrays.fill(mtdArgs, CD_Object);
                    cob.invokestatic(CD_Set,
                                     "of",
                                     MethodTypeDesc.of(CD_Set, mtdArgs),
                                     true);
                } else {
                    // call Set.of(E... elements)
                    cob.loadConstant(elements.size())
                       .anewarray(CD_String);
                    int arrayIndex = 0;
                    for (T t : sorted(elements)) {
                        cob.dup()    // arrayref
                           .loadConstant(arrayIndex);
                        visitElement(t, cob);  // value
                        cob.aastore();
                        arrayIndex++;
                    }
                    cob.invokestatic(CD_Set,
                                     "of",
                                     MTD_Set_ObjectArray,
                                     true);
                }
                cob.astore(index);
            }
        }

        /*
         * Generates bytecode to create one single instance of EnumSet
         * for a given set of modifiers and assign to a local variable slot.
         */
        static class EnumSetBuilder<T extends Comparable<T>> extends SetBuilder<T> {
            private final ClassDesc classDesc;

            EnumSetBuilder(Set<T> modifiers, ClassDesc classDesc,
                           int defaultVarIndex,
                           IntSupplier nextLocalVar) {
                super(modifiers, defaultVarIndex, nextLocalVar);
                this.classDesc = classDesc;
            }

            /**
             * Loads an Enum field.
             */
            @Override
            void visitElement(T t, CodeBuilder cob) {
                cob.getstatic(classDesc, t.toString(), classDesc);
            }
        }
    }

    /**
     * Generate SystemModulesMap and add it as a resource.
     *
     * @return the name of the class resource added to the pool
     */
    private String genSystemModulesMapClass(ClassDesc allSystemModules,
                                            ClassDesc defaultSystemModules,
                                            Map<String, String> map,
                                            ResourcePoolBuilder out) {

        // write the class file to the pool as a resource
        String rn = "/java.base/" + SYSTEM_MODULES_MAP_CLASSNAME + ".class";
        // sort the map of module name to the class name of the generated SystemModules class
        List<Map.Entry<String, String>> systemModulesMap = map.entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).toList();
        ResourcePoolEntry e = ResourcePoolEntry.create(rn, ClassFile.of().build(
                CD_SYSTEM_MODULES_MAP,
                clb -> clb.withFlags(ACC_FINAL + ACC_SUPER)
                          .withVersion(52, 0)

                          // <init>
                          .withMethodBody(
                                  INIT_NAME,
                                  MTD_void,
                                  0,
                                  cob -> cob.aload(0)
                                            .invokespecial(CD_Object,
                                                           INIT_NAME,
                                                           MTD_void)
                                            .return_())

                          // allSystemModules()
                          .withMethodBody(
                                  "allSystemModules",
                                  MTD_SystemModules,
                                  ACC_STATIC,
                                  cob -> cob.new_(allSystemModules)
                                            .dup()
                                            .invokespecial(allSystemModules,
                                                           INIT_NAME,
                                                           MTD_void)
                                            .areturn())

                          // defaultSystemModules()
                          .withMethodBody(
                                  "defaultSystemModules",
                                   MTD_SystemModules,
                                   ACC_STATIC,
                                   cob -> cob.new_(defaultSystemModules)
                                             .dup()
                                             .invokespecial(defaultSystemModules,
                                                            INIT_NAME,
                                                            MTD_void)
                                             .areturn())

                          // moduleNames()
                          .withMethodBody(
                                  "moduleNames",
                                  MTD_StringArray,
                                  ACC_STATIC,
                                  cob -> {
                                      cob.loadConstant(map.size());
                                      cob.anewarray(CD_String);

                                      int index = 0;
                                      for (Map.Entry<String,String> entry : systemModulesMap) {
                                          cob.dup() // arrayref
                                             .loadConstant(index)
                                             .loadConstant(entry.getKey())
                                             .aastore();
                                          index++;
                                      }

                                      cob.areturn();
                                  })

                          // classNames()
                          .withMethodBody(
                                  "classNames",
                                  MTD_StringArray,
                                  ACC_STATIC,
                                  cob -> {
                                      cob.loadConstant(map.size())
                                         .anewarray(CD_String);

                                      int index = 0;
                                      for (Map.Entry<String,String> entry : systemModulesMap) {
                                          cob.dup() // arrayref
                                             .loadConstant(index)
                                             .loadConstant(entry.getValue().replace('/', '.'))
                                             .aastore();
                                          index++;
                                      }

                                      cob.areturn();
                                  })));

        out.add(e);

        return rn;
    }

    /**
     * Returns a sorted copy of a collection.
     *
     * This is useful to ensure a deterministic iteration order.
     *
     * @return a sorted copy of the given collection.
     */
    private static <T extends Comparable<T>> List<T> sorted(Collection<T> c) {
        var l = new ArrayList<T>(c);
        Collections.sort(l);
        return l;
    }

    /**
     * Returns a module finder that finds all modules in the given list
     */
    private static ModuleFinder finderOf(Collection<ModuleInfo> moduleInfos) {
        Supplier<ModuleReader> readerSupplier = () -> null;
        Map<String, ModuleReference> namesToReference = new HashMap<>();
        for (ModuleInfo mi : moduleInfos) {
            String name = mi.moduleName();
            ModuleReference mref
                = new ModuleReferenceImpl(mi.descriptor(),
                                          URI.create("jrt:/" + name),
                                          readerSupplier,
                                          null,
                                          mi.target(),
                                          null,
                                          null,
                                          mi.moduleResolution());
            namesToReference.put(name, mref);
        }

        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                Objects.requireNonNull(name);
                return Optional.ofNullable(namesToReference.get(name));
            }
            @Override
            public Set<ModuleReference> findAll() {
                return new HashSet<>(namesToReference.values());
            }
        };
    }
}
