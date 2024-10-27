/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import static java.lang.constant.ConstantDescs.*;

import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Opens;
import java.lang.module.ModuleDescriptor.Provides;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleDescriptor.Version;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import jdk.tools.jlink.internal.Snippets.ElementLoader;
import jdk.tools.jlink.internal.Snippets.Loadable;
import jdk.tools.jlink.internal.Snippets.LoadableArray;
import jdk.tools.jlink.internal.Snippets.LoadableSet;
import static jdk.tools.jlink.internal.Snippets.STRING_LOADER;
import static jdk.tools.jlink.internal.Snippets.STRING_PAGE_SIZE;

import jdk.tools.jlink.internal.plugins.SystemModulesPlugin.ModuleInfo;
import jdk.tools.jlink.internal.plugins.SystemModulesPlugin.SystemModulesClassGenerator.DedupSetBuilder;

class ModuleInfoLoader implements ElementLoader<ModuleInfo> {
    private static final ClassDesc CD_MODULE_DESCRIPTOR =
        ClassDesc.ofInternalName("java/lang/module/ModuleDescriptor");
    private static final ClassDesc CD_MODULE_BUILDER =
        ClassDesc.ofInternalName("jdk/internal/module/Builder");

    private static final int PAGING_THRESHOLD = 512;
    private final DedupSetBuilder dedupSetBuilder;
    private final ClassDesc ownerClassDesc;

    ModuleInfoLoader(DedupSetBuilder dedupSetBuilder, ClassDesc ownerClassDesc) {
        this.dedupSetBuilder = dedupSetBuilder;
        this.ownerClassDesc = ownerClassDesc;
    }

    @Override
    public void load(CodeBuilder cob, ModuleInfo moduleInfo, int index) {
        var mdBuilder = new ModuleDescriptorBuilder(cob, moduleInfo.descriptor(), moduleInfo.packages(), index);
        mdBuilder.load();
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
        Consumer<ClassBuilder> amendment;

        ModuleDescriptorBuilder(CodeBuilder cob, ModuleDescriptor md, Set<String> packages, int index) {
            if (md.isAutomatic()) {
                throw new InternalError("linking automatic module is not supported");
            }
            this.cob = cob;
            this.md = md;
            this.packages = packages;
            this.index = index;
        }

        private void setupLoadable(Loadable loadable) {
            if (amendment == null) {
                amendment = loadable::setup;
            } else {
                amendment = amendment.andThen(loadable::setup);
            }
        }

        void setup(ClassBuilder clb) {
            amendment.accept(clb);
        }

        void load() {
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

            loadModuleDescriptor();
        }

        void newBuilder() {
            cob.new_(CD_MODULE_BUILDER)
               .dup()
               .loadConstant(md.name())
               .invokespecial(CD_MODULE_BUILDER,
                              INIT_NAME,
                              MTD_void_String);

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
            cob.dup()
               .loadConstant(value ? 1 : 0)
               .invokevirtual(CD_MODULE_BUILDER,
                              methodName,
                              MTD_BOOLEAN)
               .pop();
        }

        /*
         * Put ModuleDescriptor into the modules array
         */
        void loadModuleDescriptor() {
            cob
               .loadConstant(md.hashCode())
               .invokevirtual(CD_MODULE_BUILDER,
                              "build",
                              MTD_ModuleDescriptor_int);
        }

        /*
         * Call Builder::newRequires to create Requires instances and
         * then pass it to the builder by calling:
         *      Builder.requires(Requires[])
         *
         */
        void requires(Set<Requires> requires) {
            cob.dup()
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
            dedupSetBuilder.loadRequiresModifiers(cob, mods);
            cob.loadConstant(name);
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
            var exportArray = LoadableArray.of(
                    CD_EXPORTS,
                    sorted(exports),
                    this::loadExports,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Exports",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(exportArray);

            cob.dup();
            exportArray.load(cob);
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
         * ms = export.modifiers()
         * pn = export.source()
         * targets = export.targets()
         */
        void loadExports(CodeBuilder cb, Exports export, int unused) {
            dedupSetBuilder.loadExportsModifiers(cb, export.modifiers());
            cb.loadConstant(export.source());
            var targets = export.targets();
            if (!targets.isEmpty()) {
                dedupSetBuilder.loadStringSet(cb, targets);
                cb.invokestatic(CD_MODULE_BUILDER,
                                "newExports",
                                MTD_EXPORTS_MODIFIER_SET_STRING_SET);
            } else {
                cb.invokestatic(CD_MODULE_BUILDER,
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
            var opensArray = LoadableArray.of(
                    CD_OPENS,
                    sorted(opens),
                    this::newOpens,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Opens",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(opensArray);

            cob.dup();
            opensArray.load(cob);
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
         * ms = open.modifiers()
         * pn = open.source()
         * targets = open.targets()
         * Builder.newOpens(mods, pn, targets);
         */
        void newOpens(CodeBuilder cb, Opens open, int unused) {
            dedupSetBuilder.loadOpensModifiers(cb, open.modifiers());
            cb.loadConstant(open.source());
            var targets = open.targets();
            if (!targets.isEmpty()) {
                dedupSetBuilder.loadStringSet(cb, targets);
                cb.invokestatic(CD_MODULE_BUILDER,
                                 "newOpens",
                                 MTD_OPENS_MODIFIER_SET_STRING_SET);
            } else {
                cb.invokestatic(CD_MODULE_BUILDER,
                                 "newOpens",
                                 MTD_OPENS_MODIFIER_SET_STRING);
            }
        }

        /*
         * Invoke Builder.uses(Set<String> uses)
         */
        void uses(Set<String> uses) {
            cob.dup();
            dedupSetBuilder.loadStringSet(cob, uses);
            cob.invokevirtual(CD_MODULE_BUILDER,
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
            var providesArray = LoadableArray.of(
                    CD_PROVIDES,
                    sorted(provides),
                    this::newProvides,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Provides",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(providesArray);

            cob.dup();
            providesArray.load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "provides",
                              MTD_PROVIDES_ARRAY)
                .pop();
        }

        /*
         * Invoke Builder.newProvides(String service, List<String> providers)
         *
         * service = provide.service()
         * providers = List.of(new String[] { provide.providers() }
         * Builder.newProvides(service, providers);
         */
        void newProvides(CodeBuilder cb, Provides provide, int offset) {
            var providersArray = LoadableArray.of(
                    CD_String,
                    provide.providers(),
                    STRING_LOADER,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Provider" + offset,
                    STRING_PAGE_SIZE);


            setupLoadable(providersArray);

            cb.loadConstant(provide.service());
            providersArray.load(cb);
            cb.invokestatic(CD_List,
                             "of",
                             MTD_List_ObjectArray,
                             true)
               .invokestatic(CD_MODULE_BUILDER,
                             "newProvides",
                             MTD_PROVIDES_STRING_LIST);
        }

        /*
         * Invoke Builder.packages(Set<String> packages)
         * with packages either from invoke provider method
         *   module<index>Packages()
         * or construct inline with
         *   Set.of(packages)
         */
        void packages(Set<String> packages) {
            var packagesArray = LoadableSet.of(
                    sorted(packages),
                    STRING_LOADER,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Packages",
                    STRING_PAGE_SIZE);

            setupLoadable(packagesArray);

            cob.dup();
            packagesArray.load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "packages",
                              MTD_SET)
               .pop();
        }

        /*
         * Invoke Builder.mainClass(String cn)
         */
        void mainClass(String cn) {
            cob.dup()
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
            cob.dup()
               .loadConstant(v.toString())
               .invokevirtual(CD_MODULE_BUILDER,
                              "version",
                              MTD_STRING)
               .pop();
        }
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
}