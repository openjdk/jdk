/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.Checks;
import jdk.internal.module.ModuleHashes;
import jdk.internal.module.ModuleInfo.Attributes;
import jdk.internal.module.ModuleInfoExtender;
import jdk.internal.module.ModuleResolution;
import jdk.internal.module.SystemModules;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

/**
 * Jlink plugin to reconstitute module descriptors for system modules.
 * It will extend module-info.class with Packages attribute,
 * if not present. It also determines the number of packages of
 * the boot layer at link time.
 *
 * This plugin will override jdk.internal.module.SystemModules class
 *
 * @see jdk.internal.module.SystemModuleFinder
 * @see SystemModules
 */
public final class SystemModulesPlugin implements Plugin {
    private static final JavaLangModuleAccess JLMA =
        SharedSecrets.getJavaLangModuleAccess();

    private static final String NAME = "system-modules";
    private static final String DESCRIPTION =
        PluginsResourceBundle.getDescription(NAME);

    private boolean enabled;
    public SystemModulesPlugin() {
        this.enabled = true;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Set<State> getState() {
        return enabled ? EnumSet.of(State.AUTO_ENABLED, State.FUNCTIONAL)
                       : EnumSet.of(State.DISABLED);
    }

    @Override
    public void configure(Map<String, String> config) {
        if (config.containsKey(NAME)) {
            enabled = false;
        }
    }

    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        if (!enabled) {
            throw new PluginException(NAME + " was set");
        }

        SystemModulesClassGenerator generator = new SystemModulesClassGenerator();

        // generate the byte code to create ModuleDescriptors
        // skip parsing module-info.class and skip name check
        in.moduleView().modules().forEach(module -> {

            ResourcePoolEntry data = module.findEntry("module-info.class").orElseThrow(
                // automatic module not supported yet
                () ->  new PluginException("module-info.class not found for " +
                    module.name() + " module")
            );

            assert module.name().equals(data.moduleName());
            try {
                ModuleInfo moduleInfo = new ModuleInfo(data.contentBytes(), module.packages());
                generator.addModule(moduleInfo);

                // link-time validation
                moduleInfo.validateNames();
                // check if any exported or open package is not present
                moduleInfo.validatePackages();

                // Packages attribute needs update
                if (moduleInfo.shouldAddPackagesAttribute()) {
                    // replace with the overridden version
                    data = data.copyWithContent(moduleInfo.getBytes());
                }
                out.add(data);
            } catch (IOException e) {
                throw new PluginException(e);
            }
        });

        // Generate the new class
        ClassWriter cwriter = generator.getClassWriter();
        in.entries().forEach(data -> {
            if (data.path().endsWith("module-info.class"))
                return;
            if (generator.isOverriddenClass(data.path())) {
                byte[] bytes = cwriter.toByteArray();
                ResourcePoolEntry ndata = data.copyWithContent(bytes);
                out.add(ndata);
            } else {
                out.add(data);
            }
        });

        return out.build();
    }

    class ModuleInfo {
        final ModuleDescriptor descriptor;
        final ModuleHashes recordedHashes;
        final ModuleResolution moduleResolution;
        final Set<String> packages;
        final ByteArrayInputStream bain;

        ModuleInfo(byte[] bytes, Set<String> packages) throws IOException {
            this.bain = new ByteArrayInputStream(bytes);
            this.packages = packages;

            Attributes attrs = jdk.internal.module.ModuleInfo.read(bain, null);
            this.descriptor = attrs.descriptor();
            this.recordedHashes = attrs.recordedHashes();
            this.moduleResolution = attrs.moduleResolution();

            if (descriptor.isAutomatic()) {
                throw new InternalError("linking automatic module is not supported");
            }
        }

        String moduleName() {
            return descriptor.name();
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

        /**
         * Returns true if the PackagesAttribute should be written
         */
        boolean shouldAddPackagesAttribute() {
            return descriptor.packages().isEmpty() && packages.size() > 0;
        }

        /**
         * Returns the bytes for the module-info.class with PackagesAttribute
         * if it contains at least one package
         */
        byte[] getBytes() throws IOException {
            bain.reset();

            // add Packages attribute if not exist
            if (shouldAddPackagesAttribute()) {
                return new ModuleInfoRewriter(bain, packages).getBytes();
            } else {
                return bain.readAllBytes();
            }
        }

        class ModuleInfoRewriter extends ByteArrayOutputStream {
            final ModuleInfoExtender extender;
            ModuleInfoRewriter(InputStream in, Set<String> packages) throws IOException {
                this.extender = ModuleInfoExtender.newExtender(in);
                // Add Packages attribute
                if (packages.size() > 0) {
                    this.extender.packages(packages);
                }
                this.extender.write(this);
            }

            byte[] getBytes() {
                return buf;
            }
        }
    }

    /**
     * ClassWriter of a new jdk.internal.module.SystemModules class
     * to reconstitute ModuleDescriptor of the system modules.
     */
    static class SystemModulesClassGenerator {
        private static final String CLASSNAME =
            "jdk/internal/module/SystemModules";
        private static final String MODULE_DESCRIPTOR_BUILDER =
            "jdk/internal/module/Builder";
        private static final String MODULE_DESCRIPTOR_ARRAY_SIGNATURE =
            "[Ljava/lang/module/ModuleDescriptor;";
        private static final String REQUIRES_MODIFIER_CLASSNAME =
            "java/lang/module/ModuleDescriptor$Requires$Modifier";
        private static final String EXPORTS_MODIFIER_CLASSNAME =
            "java/lang/module/ModuleDescriptor$Exports$Modifier";
        private static final String OPENS_MODIFIER_CLASSNAME =
            "java/lang/module/ModuleDescriptor$Opens$Modifier";
        private static final String MODULE_HASHES_ARRAY_SIGNATURE  =
            "[Ljdk/internal/module/ModuleHashes;";
        private static final String MODULE_RESOLUTION_CLASSNAME  =
            "jdk/internal/module/ModuleResolution";
        private static final String MODULE_RESOLUTIONS_ARRAY_SIGNATURE  =
            "[Ljdk/internal/module/ModuleResolution;";

        // static variables in SystemModules class
        private static final String MODULE_NAMES = "MODULE_NAMES";
        private static final String PACKAGE_COUNT = "PACKAGES_IN_BOOT_LAYER";

        private static final int MAX_LOCAL_VARS = 256;

        private final int BUILDER_VAR    = 0;
        private final int MD_VAR         = 1;  // variable for ModuleDescriptor
        private final int MH_VAR         = 1;  // variable for ModuleHashes
        private int nextLocalVar         = 2;  // index to next local variable

        private final ClassWriter cw;

        // Method visitor for generating the SystemModules::modules() method
        private MethodVisitor mv;

        // list of all ModuleDescriptorBuilders, invoked in turn when building.
        private final List<ModuleInfo> moduleInfos = new ArrayList<>();

        // A builder to create one single Set instance for a given set of
        // names or modifiers to reduce the footprint
        // e.g. target modules of qualified exports
        private final DedupSetBuilder dedupSetBuilder
            = new DedupSetBuilder(this::getNextLocalVar);

        public SystemModulesClassGenerator() {
            this.cw = new ClassWriter(ClassWriter.COMPUTE_MAXS +
                                      ClassWriter.COMPUTE_FRAMES);
        }

        private int getNextLocalVar() {
            return nextLocalVar++;
        }

        /*
         * static initializer initializing the static fields
         *
         * static Map<String, ModuleDescriptor> map = new HashMap<>();
         */
        private void clinit(int numModules, int numPackages) {
            cw.visit(Opcodes.V1_8, ACC_PUBLIC+ACC_FINAL+ACC_SUPER, CLASSNAME,
                     null, "java/lang/Object", null);

            // public static String[] MODULE_NAMES = new String[] {....};
            cw.visitField(ACC_PUBLIC+ACC_FINAL+ACC_STATIC, MODULE_NAMES,
                    "[Ljava/lang/String;", null, null)
                    .visitEnd();

            // public static int PACKAGES_IN_BOOT_LAYER;
            cw.visitField(ACC_PUBLIC+ACC_FINAL+ACC_STATIC, PACKAGE_COUNT,
                    "I", null, numPackages)
                    .visitEnd();

            MethodVisitor clinit =
                cw.visitMethod(ACC_STATIC, "<clinit>", "()V",
                               null, null);
            clinit.visitCode();

            // create the MODULE_NAMES array
            pushInt(clinit, numModules);
            clinit.visitTypeInsn(ANEWARRAY, "java/lang/String");

            int index = 0;
            for (ModuleInfo minfo : moduleInfos) {
                clinit.visitInsn(DUP);                  // arrayref
                pushInt(clinit, index++);
                clinit.visitLdcInsn(minfo.moduleName()); // value
                clinit.visitInsn(AASTORE);
            }

            clinit.visitFieldInsn(PUTSTATIC, CLASSNAME, MODULE_NAMES,
                    "[Ljava/lang/String;");

            clinit.visitInsn(RETURN);
            clinit.visitMaxs(0, 0);
            clinit.visitEnd();
        }

        /*
         * Adds the given ModuleDescriptor to the system module list, and
         * prepares mapping from various Sets to SetBuilders to emit an
         * optimized number of sets during build.
         */
        public void addModule(ModuleInfo moduleInfo) {
            ModuleDescriptor md = moduleInfo.descriptor;
            moduleInfos.add(moduleInfo);

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

        /*
         * Generate bytecode for SystemModules
         */
        public ClassWriter getClassWriter() {
            int numModules = moduleInfos.size();
            int numPackages = 0;
            for (ModuleInfo minfo : moduleInfos) {
                numPackages += minfo.packages.size();
            }

            clinit(numModules, numPackages);

            // generate SystemModules::descriptors
            genDescriptorsMethod();
            // generate SystemModules::hashes
            genHashesMethod();
            // generate SystemModules::moduleResolutions
            genModuleResolutionsMethod();

            return cw;
        }

        /*
         * Generate bytecode for SystemModules::descriptors method
         */
        private void genDescriptorsMethod() {
            this.mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC,
                                     "descriptors",
                                     "()" + MODULE_DESCRIPTOR_ARRAY_SIGNATURE,
                                     "()" + MODULE_DESCRIPTOR_ARRAY_SIGNATURE,
                                     null);
            mv.visitCode();
            pushInt(mv, moduleInfos.size());
            mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor");
            mv.visitVarInsn(ASTORE, MD_VAR);

            for (int index = 0; index < moduleInfos.size(); index++) {
                ModuleInfo minfo = moduleInfos.get(index);
                new ModuleDescriptorBuilder(minfo.descriptor,
                                            minfo.packages,
                                            index).build();
            }
            mv.visitVarInsn(ALOAD, MD_VAR);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

        }

        /*
         * Generate bytecode for SystemModules::hashes method
         */
        private void genHashesMethod() {
            MethodVisitor hmv =
                cw.visitMethod(ACC_PUBLIC + ACC_STATIC,
                               "hashes",
                               "()" + MODULE_HASHES_ARRAY_SIGNATURE,
                               "()" + MODULE_HASHES_ARRAY_SIGNATURE,
                               null);
            hmv.visitCode();
            pushInt(hmv, moduleInfos.size());
            hmv.visitTypeInsn(ANEWARRAY, "jdk/internal/module/ModuleHashes");
            hmv.visitVarInsn(ASTORE, MH_VAR);

            for (int index = 0; index < moduleInfos.size(); index++) {
                ModuleInfo minfo = moduleInfos.get(index);
                if (minfo.recordedHashes != null) {
                    new ModuleHashesBuilder(minfo.recordedHashes,
                                            index,
                                            hmv).build();
                }
            }

            hmv.visitVarInsn(ALOAD, MH_VAR);
            hmv.visitInsn(ARETURN);
            hmv.visitMaxs(0, 0);
            hmv.visitEnd();

        }

        /*
         * Generate bytecode for SystemModules::methodResoultions method
         */
        private void genModuleResolutionsMethod() {
            MethodVisitor mresmv =
                cw.visitMethod(ACC_PUBLIC+ACC_STATIC,
                               "moduleResolutions",
                               "()" + MODULE_RESOLUTIONS_ARRAY_SIGNATURE,
                               "()" + MODULE_RESOLUTIONS_ARRAY_SIGNATURE,
                               null);
            mresmv.visitCode();
            pushInt(mresmv, moduleInfos.size());
            mresmv.visitTypeInsn(ANEWARRAY, MODULE_RESOLUTION_CLASSNAME);
            mresmv.visitVarInsn(ASTORE, 0);

            for (int index=0; index < moduleInfos.size(); index++) {
                ModuleInfo minfo = moduleInfos.get(index);
                if (minfo.moduleResolution != null) {
                    mresmv.visitVarInsn(ALOAD, 0);
                    pushInt(mresmv, index);
                    mresmv.visitTypeInsn(NEW, MODULE_RESOLUTION_CLASSNAME);
                    mresmv.visitInsn(DUP);
                    mresmv.visitLdcInsn(minfo.moduleResolution.value());
                    mresmv.visitMethodInsn(INVOKESPECIAL,
                                           MODULE_RESOLUTION_CLASSNAME,
                                           "<init>",
                                           "(I)V", false);
                    mresmv.visitInsn(AASTORE);
                }
            }
            mresmv.visitVarInsn(ALOAD, 0);
            mresmv.visitInsn(ARETURN);
            mresmv.visitMaxs(0, 0);
            mresmv.visitEnd();
        }

        public boolean isOverriddenClass(String path) {
            return path.equals("/java.base/" + CLASSNAME + ".class");
        }

        void pushInt(MethodVisitor mv, int num) {
            if (num <= 5) {
                mv.visitInsn(ICONST_0 + num);
            } else if (num < Byte.MAX_VALUE) {
                mv.visitIntInsn(BIPUSH, num);
            } else if (num < Short.MAX_VALUE) {
                mv.visitIntInsn(SIPUSH, num);
            } else {
                throw new IllegalArgumentException("exceed limit: " + num);
            }
        }

        class ModuleDescriptorBuilder {
            static final String BUILDER_TYPE = "Ljdk/internal/module/Builder;";
            static final String EXPORTS_TYPE =
                "Ljava/lang/module/ModuleDescriptor$Exports;";
            static final String OPENS_TYPE =
                "Ljava/lang/module/ModuleDescriptor$Opens;";
            static final String PROVIDES_TYPE =
                "Ljava/lang/module/ModuleDescriptor$Provides;";
            static final String REQUIRES_TYPE =
                "Ljava/lang/module/ModuleDescriptor$Requires;";

            // method signature for static Builder::newExports, newOpens,
            // newProvides, newRequires methods
            static final String EXPORTS_MODIFIER_SET_STRING_SET_SIG =
                "(Ljava/util/Set;Ljava/lang/String;Ljava/util/Set;)"
                    + EXPORTS_TYPE;
            static final String EXPORTS_MODIFIER_SET_STRING_SIG =
                "(Ljava/util/Set;Ljava/lang/String;)" + EXPORTS_TYPE;
            static final String OPENS_MODIFIER_SET_STRING_SET_SIG =
                "(Ljava/util/Set;Ljava/lang/String;Ljava/util/Set;)"
                    + OPENS_TYPE;
            static final String OPENS_MODIFIER_SET_STRING_SIG =
                "(Ljava/util/Set;Ljava/lang/String;)" + OPENS_TYPE;
            static final String PROVIDES_STRING_LIST_SIG =
                "(Ljava/lang/String;Ljava/util/List;)" + PROVIDES_TYPE;
            static final String REQUIRES_SET_STRING_SIG =
                "(Ljava/util/Set;Ljava/lang/String;)" + REQUIRES_TYPE;
            static final String REQUIRES_SET_STRING_STRING_SIG =
                "(Ljava/util/Set;Ljava/lang/String;Ljava/lang/String;)" + REQUIRES_TYPE;

            // method signature for Builder instance methods that
            // return this Builder instance
            static final String EXPORTS_ARRAY_SIG =
                "([" + EXPORTS_TYPE + ")" + BUILDER_TYPE;
            static final String OPENS_ARRAY_SIG =
                "([" + OPENS_TYPE + ")" + BUILDER_TYPE;
            static final String PROVIDES_ARRAY_SIG =
                "([" + PROVIDES_TYPE + ")" + BUILDER_TYPE;
            static final String REQUIRES_ARRAY_SIG =
                "([" + REQUIRES_TYPE + ")" + BUILDER_TYPE;
            static final String SET_SIG = "(Ljava/util/Set;)" + BUILDER_TYPE;
            static final String STRING_SIG = "(Ljava/lang/String;)" + BUILDER_TYPE;
            static final String BOOLEAN_SIG = "(Z)" + BUILDER_TYPE;

            final ModuleDescriptor md;
            final Set<String> packages;
            final int index;
            ModuleDescriptorBuilder(ModuleDescriptor md, Set<String> packages, int index) {
                if (md.isAutomatic()) {
                    throw new InternalError("linking automatic module is not supported");
                }
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
                mv.visitTypeInsn(NEW, MODULE_DESCRIPTOR_BUILDER);
                mv.visitInsn(DUP);
                mv.visitLdcInsn(md.name());
                mv.visitMethodInsn(INVOKESPECIAL, MODULE_DESCRIPTOR_BUILDER,
                    "<init>", "(Ljava/lang/String;)V", false);
                mv.visitVarInsn(ASTORE, BUILDER_VAR);
                mv.visitVarInsn(ALOAD, BUILDER_VAR);

                if (md.isOpen()) {
                    setModuleBit("open", true);
                }
                if (md.isSynthetic()) {
                    setModuleBit("synthetic", true);
                }
            }

            /*
             * Invoke Builder.<methodName>(boolean value)
             */
            void setModuleBit(String methodName, boolean value) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                if (value) {
                    mv.visitInsn(ICONST_1);
                } else {
                    mv.visitInsn(ICONST_0);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    methodName, BOOLEAN_SIG, false);
                mv.visitInsn(POP);
            }

            /*
             * Put ModuleDescriptor into the modules array
             */
            void putModuleDescriptor() {
                mv.visitVarInsn(ALOAD, MD_VAR);
                pushInt(mv, index);
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitLdcInsn(md.hashCode());
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "build", "(I)Ljava/lang/module/ModuleDescriptor;",
                    false);
                mv.visitInsn(AASTORE);
            }

            /*
             * Call Builder::newRequires to create Requires instances and
             * then pass it to the builder by calling:
             *      Builder.requires(Requires[])
             *
             */
            void requires(Set<Requires> requires) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(mv, requires.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Requires");
                int arrayIndex = 0;
                for (Requires require : requires) {
                    String compiledVersion = null;
                    if (require.compiledVersion().isPresent()) {
                        compiledVersion = require.compiledVersion().get().toString();
                    }

                    mv.visitInsn(DUP);               // arrayref
                    pushInt(mv, arrayIndex++);
                    newRequires(require.modifiers(), require.name(), compiledVersion);
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "requires", REQUIRES_ARRAY_SIG, false);
            }

            /*
             * Invoke Builder.newRequires(Set<Modifier> mods, String mn, String compiledVersion)
             *
             * Set<Modifier> mods = ...
             * Builder.newRequires(mods, mn, compiledVersion);
             */
            void newRequires(Set<Requires.Modifier> mods, String name, String compiledVersion) {
                int varIndex = dedupSetBuilder.indexOfRequiresModifiers(mods);
                mv.visitVarInsn(ALOAD, varIndex);
                mv.visitLdcInsn(name);
                if (compiledVersion != null) {
                    mv.visitLdcInsn(compiledVersion);
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newRequires", REQUIRES_SET_STRING_STRING_SIG, false);
                } else {
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newRequires", REQUIRES_SET_STRING_SIG, false);
                }
            }

            /*
             * Call Builder::newExports to create Exports instances and
             * then pass it to the builder by calling:
             *      Builder.exports(Exports[])
             *
             */
            void exports(Set<Exports> exports) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(mv, exports.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Exports");
                int arrayIndex = 0;
                for (Exports export : exports) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(mv, arrayIndex++);
                    newExports(export.modifiers(), export.source(), export.targets());
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "exports", EXPORTS_ARRAY_SIG, false);
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
                int modifiersSetIndex = dedupSetBuilder.indexOfExportsModifiers(ms);
                if (!targets.isEmpty()) {
                    int stringSetIndex = dedupSetBuilder.indexOfStringSet(targets);
                    mv.visitVarInsn(ALOAD, modifiersSetIndex);
                    mv.visitLdcInsn(pn);
                    mv.visitVarInsn(ALOAD, stringSetIndex);
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newExports", EXPORTS_MODIFIER_SET_STRING_SET_SIG, false);
                } else {
                    mv.visitVarInsn(ALOAD, modifiersSetIndex);
                    mv.visitLdcInsn(pn);
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newExports", EXPORTS_MODIFIER_SET_STRING_SIG, false);
                }
            }


            /**
             * Call Builder::newOpens to create Opens instances and
             * then pass it to the builder by calling:
             * Builder.opens(Opens[])
             */
            void opens(Set<Opens> opens) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(mv, opens.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Opens");
                int arrayIndex = 0;
                for (Opens open : opens) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(mv, arrayIndex++);
                    newOpens(open.modifiers(), open.source(), open.targets());
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "opens", OPENS_ARRAY_SIG, false);
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
                int modifiersSetIndex = dedupSetBuilder.indexOfOpensModifiers(ms);
                if (!targets.isEmpty()) {
                    int stringSetIndex = dedupSetBuilder.indexOfStringSet(targets);
                    mv.visitVarInsn(ALOAD, modifiersSetIndex);
                    mv.visitLdcInsn(pn);
                    mv.visitVarInsn(ALOAD, stringSetIndex);
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newOpens", OPENS_MODIFIER_SET_STRING_SET_SIG, false);
                } else {
                    mv.visitVarInsn(ALOAD, modifiersSetIndex);
                    mv.visitLdcInsn(pn);
                    mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                        "newOpens", OPENS_MODIFIER_SET_STRING_SIG, false);
                }
            }

            /*
             * Invoke Builder.uses(Set<String> uses)
             */
            void uses(Set<String> uses) {
                int varIndex = dedupSetBuilder.indexOfStringSet(uses);
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitVarInsn(ALOAD, varIndex);
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "uses", SET_SIG, false);
                mv.visitInsn(POP);
            }

            /*
            * Call Builder::newProvides to create Provides instances and
            * then pass it to the builder by calling:
            *      Builder.provides(Provides[] provides)
            *
            */
            void provides(Collection<Provides> provides) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(mv, provides.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Provides");
                int arrayIndex = 0;
                for (Provides provide : provides) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(mv, arrayIndex++);
                    newProvides(provide.service(), provide.providers());
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "provides", PROVIDES_ARRAY_SIG, false);
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
                mv.visitLdcInsn(service);
                pushInt(mv, providers.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                int arrayIndex = 0;
                for (String provider : providers) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(mv, arrayIndex++);
                    mv.visitLdcInsn(provider);
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKESTATIC, "java/util/List",
                    "of", "([Ljava/lang/Object;)Ljava/util/List;", true);
                mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                    "newProvides", PROVIDES_STRING_LIST_SIG, false);
            }

            /*
             * Invoke Builder.packages(String pn)
             */
            void packages(Set<String> packages) {
                int varIndex = dedupSetBuilder.newStringSet(packages);
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitVarInsn(ALOAD, varIndex);
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "packages", SET_SIG, false);
                mv.visitInsn(POP);
            }

            /*
             * Invoke Builder.mainClass(String cn)
             */
            void mainClass(String cn) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitLdcInsn(cn);
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "mainClass", STRING_SIG, false);
                mv.visitInsn(POP);
            }

            /*
             * Invoke Builder.version(Version v);
             */
            void version(Version v) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitLdcInsn(v.toString());
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                    "version", STRING_SIG, false);
                mv.visitInsn(POP);
            }
        }

        class ModuleHashesBuilder {
            private static final String MODULE_HASHES_BUILDER =
                "jdk/internal/module/ModuleHashes$Builder";
            private static final String MODULE_HASHES_BUILDER_TYPE =
                "L" + MODULE_HASHES_BUILDER + ";";
            static final String STRING_BYTE_ARRAY_SIG =
                "(Ljava/lang/String;[B)" + MODULE_HASHES_BUILDER_TYPE;

            final ModuleHashes recordedHashes;
            final MethodVisitor hmv;
            final int index;

            ModuleHashesBuilder(ModuleHashes hashes, int index, MethodVisitor hmv) {
                this.recordedHashes = hashes;
                this.hmv = hmv;
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
                    .forEach(mn -> hashForModule(mn, recordedHashes.hashFor(mn)));

                // Put ModuleHashes into the hashes array
                pushModuleHashes();
            }


            /*
             * Create ModuleHashes.Builder instance
             */
            void newModuleHashesBuilder() {
                hmv.visitTypeInsn(NEW, MODULE_HASHES_BUILDER);
                hmv.visitInsn(DUP);
                hmv.visitLdcInsn(recordedHashes.algorithm());
                hmv.visitMethodInsn(INVOKESPECIAL, MODULE_HASHES_BUILDER,
                    "<init>", "(Ljava/lang/String;)V", false);
                hmv.visitVarInsn(ASTORE, BUILDER_VAR);
                hmv.visitVarInsn(ALOAD, BUILDER_VAR);
            }


            /*
             * Invoke ModuleHashes.Builder::build and put the returned
             * ModuleHashes to the hashes array
             */
            void pushModuleHashes() {
                hmv.visitVarInsn(ALOAD, MH_VAR);
                pushInt(hmv, index);
                hmv.visitVarInsn(ALOAD, BUILDER_VAR);
                hmv.visitMethodInsn(INVOKEVIRTUAL, MODULE_HASHES_BUILDER,
                    "build", "()Ljdk/internal/module/ModuleHashes;",
                    false);
                hmv.visitInsn(AASTORE);
            }

            /*
             * Invoke ModuleHashes.Builder.hashForModule(String name, byte[] hash);
             */
            void hashForModule(String name, byte[] hash) {
                hmv.visitVarInsn(ALOAD, BUILDER_VAR);
                hmv.visitLdcInsn(name);

                pushInt(hmv, hash.length);
                hmv.visitIntInsn(NEWARRAY, T_BYTE);
                for (int i = 0; i < hash.length; i++) {
                    hmv.visitInsn(DUP);              // arrayref
                    pushInt(hmv, i);
                    hmv.visitIntInsn(BIPUSH, hash[i]);
                    hmv.visitInsn(BASTORE);
                }

                hmv.visitMethodInsn(INVOKEVIRTUAL, MODULE_HASHES_BUILDER,
                    "hashForModule", STRING_BYTE_ARRAY_SIG, false);
                hmv.visitInsn(POP);
            }
        }

        /*
         * Wraps set creation, ensuring identical sets are properly deduplicated.
         */
        class DedupSetBuilder {
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
                                new EnumSetBuilder<>(s, EXPORTS_MODIFIER_CLASSNAME,
                                        enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Add the given set of Opens.Modifiers
             */
            void opensModifiers(Set<Opens.Modifier> mods) {
                opensModifiersSets.computeIfAbsent(mods, s ->
                                new EnumSetBuilder<>(s, OPENS_MODIFIER_CLASSNAME,
                                        enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Add the given set of Requires.Modifiers
             */
            void requiresModifiers(Set<Requires.Modifier> mods) {
                requiresModifiersSets.computeIfAbsent(mods, s ->
                    new EnumSetBuilder<>(s, REQUIRES_MODIFIER_CLASSNAME,
                                         enumSetVar, localVarSupplier)
                ).increment();
            }

            /*
             * Retrieve the index to the given set of Strings. Emit code to
             * generate it when SetBuilder::build is called.
             */
            int indexOfStringSet(Set<String> names) {
                return stringSets.get(names).build();
            }

            /*
             * Retrieve the index to the given set of Exports.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfExportsModifiers(Set<Exports.Modifier> mods) {
                return exportsModifiersSets.get(mods).build();
            }

            /**
             * Retrieve the index to the given set of Opens.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfOpensModifiers(Set<Opens.Modifier> mods) {
                return opensModifiersSets.get(mods).build();
            }


            /*
             * Retrieve the index to the given set of Requires.Modifier.
             * Emit code to generate it when EnumSetBuilder::build is called.
             */
            int indexOfRequiresModifiers(Set<Requires.Modifier> mods) {
                return requiresModifiersSets.get(mods).build();
            }

            /*
             * Build a new string set without any attempt to deduplicate it.
             */
            int newStringSet(Set<String> names) {
                int index = new SetBuilder<>(names, stringSetVar, localVarSupplier).build();
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
        class SetBuilder<T> {
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
            void visitElement(T element, MethodVisitor mv) {
                mv.visitLdcInsn(element);
            }

            /*
             * Build bytecode for the Set represented by this builder,
             * or get the local variable index of a previously generated set
             * (in the local scope).
             *
             * @return local variable index of the generated set.
             */
            final int build() {
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

                    generateSetOf(index);
                }
                return index;
            }

            private void generateSetOf(int index) {
                if (elements.size() <= 10) {
                    // call Set.of(e1, e2, ...)
                    StringBuilder sb = new StringBuilder("(");
                    for (T t : elements) {
                        sb.append("Ljava/lang/Object;");
                        visitElement(t, mv);
                    }
                    sb.append(")Ljava/util/Set;");
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Set",
                            "of", sb.toString(), true);
                } else {
                    // call Set.of(E... elements)
                    pushInt(mv, elements.size());
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                    int arrayIndex = 0;
                    for (T t : elements) {
                        mv.visitInsn(DUP);    // arrayref
                        pushInt(mv, arrayIndex);
                        visitElement(t, mv);  // value
                        mv.visitInsn(AASTORE);
                        arrayIndex++;
                    }
                    mv.visitMethodInsn(INVOKESTATIC, "java/util/Set",
                            "of", "([Ljava/lang/Object;)Ljava/util/Set;", true);
                }
                mv.visitVarInsn(ASTORE, index);
            }
        }

        /*
         * Generates bytecode to create one single instance of EnumSet
         * for a given set of modifiers and assign to a local variable slot.
         */
        class EnumSetBuilder<T> extends SetBuilder<T> {

            private final String className;

            EnumSetBuilder(Set<T> modifiers, String className,
                           int defaultVarIndex,
                           IntSupplier nextLocalVar) {
                super(modifiers, defaultVarIndex, nextLocalVar);
                this.className = className;
            }

            /**
             * Loads an Enum field.
             */
            void visitElement(T t, MethodVisitor mv) {
                mv.visitFieldInsn(GETSTATIC, className, t.toString(),
                                  "L" + className + ";");
            }
        }
    }
}
