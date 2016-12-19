/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import jdk.internal.module.Checks;
import jdk.internal.module.ModuleInfoExtender;
import jdk.internal.module.SystemModules;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

import static jdk.internal.org.objectweb.asm.Opcodes.*;

import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.internal.plugins.SystemModuleDescriptorPlugin.SystemModulesClassGenerator.*;
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
 * @see java.lang.module.SystemModuleFinder
 * @see SystemModules
 */
public final class SystemModuleDescriptorPlugin implements Plugin {
    private static final JavaLangModuleAccess JLMA =
        SharedSecrets.getJavaLangModuleAccess();

    private static final String NAME = "system-modules";
    private static final String DESCRIPTION =
        PluginsResourceBundle.getDescription(NAME);

    private boolean enabled;
    public SystemModuleDescriptorPlugin() {
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
                ByteArrayInputStream bain = new ByteArrayInputStream(data.contentBytes());
                ModuleDescriptor md = ModuleDescriptor.read(bain);
                validateNames(md);

                Set<String> packages = module.packages();
                generator.addModule(md, module.packages());

                // add Packages attribute if not exist
                if (md.packages().isEmpty() && packages.size() > 0) {
                    bain.reset();
                    ModuleInfoRewriter minfoWriter =
                        new ModuleInfoRewriter(bain, module.packages());
                    // replace with the overridden version
                    data = data.copyWithContent(minfoWriter.getBytes());
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

    /*
     * Add Packages attribute
     */
    class ModuleInfoRewriter extends ByteArrayOutputStream {
        final ModuleInfoExtender extender;
        ModuleInfoRewriter(InputStream in, Set<String> packages) throws IOException {
            this.extender = ModuleInfoExtender.newExtender(in);
            // Add Packages attribute
            this.extender.packages(packages);
            this.extender.write(this);
        }

        byte[] getBytes() {
            return buf;
        }
    }

    void validateNames(ModuleDescriptor md) {
        Checks.requireModuleName(md.name());
        for (Requires req : md.requires()) {
            Checks.requireModuleName(req.name());
        }
        for (Exports e : md.exports()) {
            Checks.requirePackageName(e.source());
            if (e.isQualified())
                e.targets().forEach(Checks::requireModuleName);
        }
        for (Opens opens : md.opens()) {
            Checks.requirePackageName(opens.source());
            if (opens.isQualified())
                opens.targets().forEach(Checks::requireModuleName);
        }
        for (Provides provides : md.provides()) {
            Checks.requireServiceTypeName(provides.service());
            provides.providers().forEach(Checks::requireServiceProviderName);
        }
        for (String service : md.uses()) {
            Checks.requireServiceTypeName(service);
        }
        for (String pn : md.packages()) {
            Checks.requirePackageName(pn);
        }
    }

    /*
     * Returns the initial capacity for a new Set or Map of the given size
     * to avoid resizing.
     */
    static final int initialCapacity(int size) {
        if (size == 0) {
            return 0;
        } else {
            // Adjust to try and get size/capacity as close to the
            // HashSet/HashMap default load factor without going over.
            return (int)(Math.ceil((double)size / 0.75));
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

        // static variables in SystemModules class
        private static final String MODULE_NAMES = "MODULE_NAMES";
        private static final String MODULES_TO_HASH = "MODULES_TO_HASH";
        private static final String PACKAGE_COUNT = "PACKAGES_IN_BOOT_LAYER";

        private static final int MAX_LOCAL_VARS = 256;

        private final int BUILDER_VAR    = 0;
        private final int MD_VAR         = 1;  // variable for ModuleDescriptor
        private int nextLocalVar         = 2;  // index to next local variable

        private final ClassWriter cw;
        private MethodVisitor mv;
        private int nextModulesIndex = 0;

        // list of all ModuleDescriptorBuilders, invoked in turn when building.
        private final List<ModuleDescriptorBuilder> builders = new ArrayList<>();

        // module name to hash
        private final Map<String, byte[]> modulesToHash = new HashMap<>();

        // module name to index in MODULES_TO_HASH
        private final Map<String, Integer> modulesToHashIndex = new HashMap<>();

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

            // public static byte[][] MODULES_TO_HASH
            cw.visitField(ACC_PUBLIC+ACC_FINAL+ACC_STATIC, MODULES_TO_HASH,
                "[[B", null, null)
                .visitEnd();

            // public static int PACKAGES_IN_BOOT_LAYER;
            cw.visitField(ACC_PUBLIC+ACC_FINAL+ACC_STATIC, PACKAGE_COUNT,
                    "I", null, numPackages)
                    .visitEnd();

            this.mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V",
                    null, null);
            mv.visitCode();

            // create the MODULE_NAMES array
            pushInt(numModules);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String");

            int index = 0;
            for (ModuleDescriptorBuilder builder : builders) {
                mv.visitInsn(DUP);                  // arrayref
                pushInt(index++);
                mv.visitLdcInsn(builder.md.name()); // value
                mv.visitInsn(AASTORE);
            }

            mv.visitFieldInsn(PUTSTATIC, CLASSNAME, MODULE_NAMES,
                    "[Ljava/lang/String;");

            // create the MODULES_TO_HASH array
            pushInt(numModules);
            mv.visitTypeInsn(ANEWARRAY, "[B");

            index = 0;
            for (ModuleDescriptorBuilder builder : builders) {
                String mn = builder.md.name();
                byte[] recordedHash = modulesToHash.get(mn);
                if (recordedHash != null) {
                    mv.visitInsn(DUP);              // arrayref
                    pushInt(index);
                    pushInt(recordedHash.length);
                    mv.visitIntInsn(NEWARRAY, T_BYTE);
                    for (int i = 0; i < recordedHash.length; i++) {
                        mv.visitInsn(DUP);              // arrayref
                        pushInt(i);
                        mv.visitIntInsn(BIPUSH, recordedHash[i]);
                        mv.visitInsn(BASTORE);
                    }
                    mv.visitInsn(AASTORE);
                    modulesToHashIndex.put(mn, index);
                }
                index++;
            }

            mv.visitFieldInsn(PUTSTATIC, CLASSNAME, MODULES_TO_HASH, "[[B");

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

        }

        /*
         * Adds the given ModuleDescriptor to the system module list, and
         * prepares mapping from various Sets to SetBuilders to emit an
         * optimized number of sets during build.
         */
        public void addModule(ModuleDescriptor md, Set<String> packages) {
            ModuleDescriptorBuilder builder = new ModuleDescriptorBuilder(md, packages);
            builders.add(builder);

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

            // hashes
            JLMA.hashes(md).ifPresent(mh -> modulesToHash.putAll(mh.hashes()));
        }

        /*
         * Generate bytecode for SystemModules
         */
        public ClassWriter getClassWriter() {
            int numModules = builders.size();
            int numPackages = 0;
            for (ModuleDescriptorBuilder builder : builders) {
                numPackages += builder.md.packages().size();
            }

            this.clinit(numModules, numPackages);
            this.mv = cw.visitMethod(ACC_PUBLIC+ACC_STATIC,
                                     "modules", "()" + MODULE_DESCRIPTOR_ARRAY_SIGNATURE,
                                     "()" + MODULE_DESCRIPTOR_ARRAY_SIGNATURE, null);
            mv.visitCode();
            pushInt(numModules);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor");
            mv.visitVarInsn(ASTORE, MD_VAR);

            for (ModuleDescriptorBuilder builder : builders) {
                builder.build();
            }
            mv.visitVarInsn(ALOAD, MD_VAR);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return cw;
        }

        public boolean isOverriddenClass(String path) {
            return path.equals("/java.base/" + CLASSNAME + ".class");
        }

        void pushInt(int num) {
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
            static final String STRING_BYTE_ARRAY_SIG =
                "(Ljava/lang/String;[B)" + BUILDER_TYPE;
            static final String BOOLEAN_SIG = "(Z)" + BUILDER_TYPE;


            final ModuleDescriptor md;
            final Set<String> packages;

            ModuleDescriptorBuilder(ModuleDescriptor md, Set<String> packages) {
                if (md.isAutomatic()) {
                    throw new InternalError("linking automatic module is not supported");
                }
                this.md = md;
                this.packages = packages;
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

                // hashes
                JLMA.hashes(md).ifPresent(mh -> {
                    algorithm(mh.algorithm());
                    mh.names().forEach(mn -> moduleHash(mn, mh.hashFor(mn)));
                });

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
                pushInt(nextModulesIndex++);
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
                pushInt(requires.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Requires");
                int arrayIndex = 0;
                for (Requires require : requires) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(arrayIndex++);
                    newRequires(require.modifiers(), require.name());
                    mv.visitInsn(AASTORE);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                                   "requires", REQUIRES_ARRAY_SIG, false);
            }

            /*
             * Invoke Builder.newRequires(Set<Modifier> mods, String mn)
             *
             * Set<Modifier> mods = ...
             * Builder.newRequires(mods, mn);
             */
            void newRequires(Set<Requires.Modifier> mods, String name) {
                int varIndex = dedupSetBuilder.indexOfRequiresModifiers(mods);
                mv.visitVarInsn(ALOAD, varIndex);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(INVOKESTATIC, MODULE_DESCRIPTOR_BUILDER,
                                   "newRequires", REQUIRES_SET_STRING_SIG, false);
            }

            /*
             * Call Builder::newExports to create Exports instances and
             * then pass it to the builder by calling:
             *      Builder.exports(Exports[])
             *
             */
            void exports(Set<Exports> exports) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(exports.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Exports");
                int arrayIndex = 0;
                for (Exports export : exports) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(arrayIndex++);
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
             *      Builder.opens(Opens[])
             *
             */
            void opens(Set<Opens> opens) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                pushInt(opens.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Opens");
                int arrayIndex = 0;
                for (Opens open : opens) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(arrayIndex++);
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
                pushInt(provides.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/module/ModuleDescriptor$Provides");
                int arrayIndex = 0;
                for (Provides provide : provides) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(arrayIndex++);
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
                pushInt(providers.size());
                mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                int arrayIndex = 0;
                for (String provider : providers) {
                    mv.visitInsn(DUP);    // arrayref
                    pushInt(arrayIndex++);
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

            /*
             * Invoke Builder.algorithm(String a);
             */
            void algorithm(String alg) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitLdcInsn(alg);
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                                   "algorithm", STRING_SIG, false);
                mv.visitInsn(POP);
            }

            /*
             * Invoke Builder.moduleHash(String name, byte[] hash);
             */
            void moduleHash(String name, byte[] hash) {
                mv.visitVarInsn(ALOAD, BUILDER_VAR);
                mv.visitLdcInsn(name);

                // must exist
                Integer index = modulesToHashIndex.get(name);
                if (index != null) {
                    mv.visitFieldInsn(GETSTATIC, CLASSNAME, MODULES_TO_HASH, "[[B");
                    pushInt(index);
                    mv.visitInsn(AALOAD);
                    assert(Objects.equals(hash, modulesToHash.get(name)));
                } else {
                    pushInt(hash.length);
                    mv.visitIntInsn(NEWARRAY, T_BYTE);
                    for (int i = 0; i < hash.length; i++) {
                        mv.visitInsn(DUP);              // arrayref
                        pushInt(i);
                        mv.visitIntInsn(BIPUSH, hash[i]);
                        mv.visitInsn(BASTORE);
                    }
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, MODULE_DESCRIPTOR_BUILDER,
                                   "moduleHash", STRING_BYTE_ARRAY_SIG, false);
                mv.visitInsn(POP);
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
                    pushInt(elements.size());
                    mv.visitTypeInsn(ANEWARRAY, "java/lang/String");
                    int arrayIndex = 0;
                    for (T t : elements) {
                        mv.visitInsn(DUP);    // arrayref
                        pushInt(arrayIndex);
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
