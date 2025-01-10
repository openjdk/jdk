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
import static java.lang.constant.ConstantDescs.*;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.ClassFileFormatVersion;
import java.net.URI;
import java.util.ArrayList;
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
import java.lang.classfile.CodeBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.tools.jlink.internal.ModuleSorter;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import static java.lang.classfile.ClassFile.*;
import static jdk.tools.jlink.internal.Snippets.*;
import static jdk.tools.jlink.internal.Snippets.CollectionSnippetBuilder.ENUM_PAGE_SIZE;
import static jdk.tools.jlink.internal.Snippets.CollectionSnippetBuilder.STRING_PAGE_SIZE;

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
        // Diagnosis help, can be removed
        if (Boolean.parseBoolean(System.getProperty("jlink.dumpSystemModuleClass", "false"))) {
            try {
                var filePath = Path.of(className + ".class").toAbsolutePath();
                System.err.println("Write " + filePath.toString());
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, bytes);
            } catch (IOException ioe) {
                ioe.printStackTrace(System.err);
            }
        }
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

        private static final int MAX_LOCAL_VARS = 256;

        private final int MT_VAR         = 1;  // variable for ModuleTarget
        private final int MH_VAR         = 1;  // variable for ModuleHashes
        private final int BUILDER_VAR    = 2;

        // name of class to generate
        private final ClassDesc classDesc;

        // list of all ModuleInfos
        private final List<ModuleInfo> moduleInfos;

        private final int moduleDescriptorsPerMethod;

        // A builder to create one single Set instance for a given set of
        // names or modifiers to reduce the footprint
        // e.g. target modules of qualified exports
        private final DedupSetBuilder dedupSetBuilder;
        private final ArrayList<Snippet> clinitSnippets = new ArrayList<>();

        public SystemModulesClassGenerator(String className,
                                           List<ModuleInfo> moduleInfos,
                                           int moduleDescriptorsPerMethod) {
            this.classDesc = ClassDesc.ofInternalName(className);
            this.moduleInfos = moduleInfos;
            this.moduleDescriptorsPerMethod = moduleDescriptorsPerMethod;
            this.dedupSetBuilder = new DedupSetBuilder(this.classDesc);
            moduleInfos.forEach(mi -> dedups(mi.descriptor()));
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

                        // generate static initializer
                        genClassInitializer(clb);
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

        private void genClassInitializer(ClassBuilder clb) {
            if (!clinitSnippets.isEmpty()) {
                clb.withMethodBody(
                        CLASS_INIT_NAME,
                        MTD_void,
                        ACC_STATIC,
                        cob -> {
                            clinitSnippets.forEach(s -> s.emit(cob));
                            cob.return_();
                        });
            }
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
            var dedupSets = dedupSetBuilder.build(clb);
            dedupSets.cacheSetupSnippet().ifPresent(clinitSnippets::add);

            var converter = new ModuleDescriptorBuilder(clb, dedupSets, classDesc);
            var elementSnippets = converter.buildAll(moduleInfos);
            var moduleDescriptors = new ArraySnippetBuilder(CD_MODULE_DESCRIPTOR)
                    .classBuilder(clb)
                    .ownerClassDesc(classDesc)
                    .enablePagination("sub", moduleDescriptorsPerMethod)
                    .build(elementSnippets);

            clb.withMethodBody(
                    "moduleDescriptors",
                    MTD_ModuleDescriptorArray,
                    ACC_PUBLIC,
                    cob -> {
                        moduleDescriptors.emit(cob);
                        cob.areturn();
                    });
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
                        int setBuilt = 0;

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
                                genImmutableSet(clb, cob, s, methodName + setBuilt++);
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
                                genImmutableSet(clb, cob, s, methodName + setBuilt++);
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
        private void genImmutableSet(ClassBuilder clb, CodeBuilder cob, Set<String> set, String methodNamePrefix) {
            var snippets = Snippet.buildAll(sorted(set), Snippet::loadConstant);
            new SetSnippetBuilder(CD_String)
                    .classBuilder(clb)
                    .ownerClassDesc(classDesc)
                    .enablePagination(methodNamePrefix, STRING_PAGE_SIZE)
                    .build(snippets)
                    .emit(cob);
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
                   .newarray(TypeKind.BYTE);
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
         * Snippets to load the deduplicated set onto the operand stack.
         * Set referenced more than once will be read from the cache, cacheSetupSnippet contains
         * the bytecode to populate that cache.
         */
        static record DedupSnippets(Map<Set<String>, Snippet> stringSets,
                                    Map<Set<Requires.Modifier>, Snippet> requiresModifiersSets,
                                    Map<Set<Opens.Modifier>, Snippet> opensModifiersSets,
                                    Map<Set<Exports.Modifier>, Snippet> exportsModifiersSets,
                                    Optional<Snippet> cacheSetupSnippet) {};

        /*
         * Wraps set creation, ensuring identical sets are properly deduplicated.
         */
        static class DedupSetBuilder {
            final Map<Set<String>, RefCounter<String>> stringSets = new HashMap<>();
            final Map<Set<Requires.Modifier>, RefCounter<Requires.Modifier>>
                requiresModifiersSets = new HashMap<>();
            final Map<Set<Exports.Modifier>, RefCounter<Exports.Modifier>>
                exportsModifiersSets = new HashMap<>();
            final Map<Set<Opens.Modifier>, RefCounter<Opens.Modifier>>
                opensModifiersSets = new HashMap<>();

            final ClassDesc owner;
            final CacheBuilder cacheBuilder = new CacheBuilder();
            int setBuilt = 0;

            DedupSetBuilder(ClassDesc owner) {
                this.owner = owner;
            }

            /*
             * Add the given set of strings to this builder.
             */
            void stringSet(Set<String> strings) {
                stringSets.computeIfAbsent(strings, RefCounter<String>::new).increment();
            }

            /*
             * Add the given set of Exports.Modifiers
             */
            void exportsModifiers(Set<Exports.Modifier> mods) {
                exportsModifiersSets.computeIfAbsent(mods, RefCounter<Exports.Modifier>::new).increment();
            }

            /*
             * Add the given set of Opens.Modifiers
             */
            void opensModifiers(Set<Opens.Modifier> mods) {
                opensModifiersSets.computeIfAbsent(mods, RefCounter::new).increment();
            }

            /*
             * Add the given set of Requires.Modifiers
             */
            void requiresModifiers(Set<Requires.Modifier> mods) {
                requiresModifiersSets.computeIfAbsent(mods, RefCounter::new).increment();
            }

            /*
             * Generate bytecode to load a set onto the operand stack.
             * Use cache if the set is referenced more than once.
             */
            private Snippet buildStringSet(ClassBuilder clb, RefCounter<String> setRef) {
                return cacheBuilder.transform(setRef,
                        new SetSnippetBuilder(CD_String)
                                .classBuilder(clb)
                                .ownerClassDesc(owner)
                                .enablePagination("dedupSet" + setBuilt++, STRING_PAGE_SIZE)
                                .build(Snippet.buildAll(setRef.sortedList(), Snippet::loadConstant)));
            }

            /*
             * Generate the mapping from a set to the bytecode loading the set onto the operand stack.
             * Ordering the sets to ensure same generated bytecode.
             */
            private Map<Set<String>, Snippet> buildStringSets(ClassBuilder clb, Map<Set<String>, RefCounter<String>> map) {
                Map<Set<String>, Snippet> snippets = new HashMap<>(map.size());
                map.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEach(e -> snippets.put(e.getKey(), buildStringSet(clb, e.getValue())));
                return snippets;
            }

            /*
             * Enum set support
             */
            private <T extends Enum<T>> Snippet buildEnumSet(ClassBuilder clb, RefCounter<T> setRef) {
                return cacheBuilder.transform(setRef,
                        new SetSnippetBuilder(CD_Object)
                                .classBuilder(clb)
                                .ownerClassDesc(owner)
                                .enablePagination("dedupSet" + setBuilt++, ENUM_PAGE_SIZE)
                                .build(Snippet.buildAll(setRef.sortedList(), Snippet::loadEnum)));
            }

            private <T extends Enum<T>> Map<Set<T>, Snippet> buildEnumSets(ClassBuilder clb, Map<Set<T>, RefCounter<T>> map) {
                Map<Set<T>, Snippet> snippets = new HashMap<>(map.size());
                map.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEach(e -> snippets.put(e.getKey(), buildEnumSet(clb, e.getValue())));
                return snippets;
            }

            /*
             * Build snippets for all sets and optionally the cache.
             */
            DedupSnippets build(ClassBuilder clb) {
                return new DedupSnippets(
                    buildStringSets(clb, stringSets),
                    buildEnumSets(clb, requiresModifiersSets),
                    buildEnumSets(clb, opensModifiersSets),
                    buildEnumSets(clb, exportsModifiersSets),
                    cacheBuilder.build(clb)
                );
            }

            /*
             * RefCounter count references to the set, and keeps sorted elements to ensure
             * generate same bytecode for a given set.
             * RefCounter itself needs ordering to ensure generate same bytecode for the cache.
             */
            class RefCounter<T extends Comparable<T>> implements Comparable<RefCounter<T>> {
                // sorted elements of the set to ensure same generated code
                private final List<T> elements;
                private int refCount;

                RefCounter(Set<T> elements) {
                    this.elements = sorted(elements);
                }

                int increment() {
                    return ++refCount;
                }

                int refCount() {
                    return refCount;
                }

                List<T> sortedList() {
                    return elements;
                }

                @Override
                public int compareTo(RefCounter<T> o) {
                    if (o == this) {
                        return 0;
                    }
                    if (elements.size() == o.elements.size()) {
                        var a1 = elements;
                        var a2 = o.elements;
                        for (int i = 0; i < elements.size(); i++) {
                            var r = a1.get(i).compareTo(a2.get(i));
                            if (r != 0) {
                                return r;
                            }
                        }
                        return 0;
                    } else {
                        return elements.size() - o.elements.size();
                    }
                }
            }

            /**
             * Build an array to host sets referenced more than once so a given set will only be constructed once.
             * Transform the bytecode for loading the set onto the operand stack as needed.
             */
            class CacheBuilder {
                private static final String VALUES_ARRAY = "dedupSetValues";
                final ArrayList<Snippet> cachedValues = new ArrayList<>();

                // Load the set from the cache to the operand stack
                //   dedupSetValues[index]
                private Snippet loadFromCache(int index) {
                    assert index >= 0;
                    return cob ->
                        cob.getstatic(owner, VALUES_ARRAY, CD_Set.arrayType())
                           .loadConstant(index)
                           .aaload();
                }

                /**
                 * Transform the bytecode for loading the set onto the operand stack.
                 * @param loadSnippet The origin snippet to load the set onto the operand stack.
                 */
                Snippet transform(RefCounter<?> setRef, Snippet loadSnippet) {
                    if (setRef.refCount() > 1) {
                        cachedValues.add(loadSnippet);
                        return loadFromCache(cachedValues.size() - 1);
                    } else {
                        return loadSnippet;
                    }
                }

                /*
                 * Returns a snippet that populates the cached values in <clinit>.
                 *
                 * The generated cache is essentially as the following:
                 *
                 * static final Set[] dedupSetValues;
                 *
                 * static {
                 *     dedupSetValues = new Set[countOfStoredValues];
                 *     dedupSetValues[0] = Set.of(elements); // for inline set
                 *     dedupSetValues[1] = dedupSet<setIndex>_0(); // for paginated set
                 *     ...
                 *     dedupSetValues[countOfStoredValues - 1] = ...
                 * }
                 */
                Optional<Snippet> build(ClassBuilder clb) {
                    if (cachedValues.isEmpty()) {
                        return Optional.empty();
                    }

                    var cacheValuesArray = new ArraySnippetBuilder(CD_Set)
                            .classBuilder(clb)
                            .ownerClassDesc(owner)
                            .enablePagination(VALUES_ARRAY)
                            .build(cachedValues.toArray(Snippet[]::new));

                    clb.withField(VALUES_ARRAY, CD_Set.arrayType(), ACC_STATIC | ACC_FINAL);

                    return Optional.of(cob -> {
                            cacheValuesArray.emit(cob);
                            cob.putstatic(owner, VALUES_ARRAY, CD_Set.arrayType());
                    });
                }
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
