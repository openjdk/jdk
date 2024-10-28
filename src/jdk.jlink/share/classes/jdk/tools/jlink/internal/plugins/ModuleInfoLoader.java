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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private final ArrayList<Consumer<ClassBuilder>> amendments = new ArrayList<>();

    ModuleInfoLoader(DedupSetBuilder dedupSetBuilder, ClassDesc ownerClassDesc) {
        this.dedupSetBuilder = dedupSetBuilder;
        this.ownerClassDesc = ownerClassDesc;
    }

    @Override
    public void load(CodeBuilder cob, ModuleInfo moduleInfo, int index) {
        var mdBuilder = new ModuleDescriptorBuilder(moduleInfo.descriptor(), moduleInfo.packages(), index);
        mdBuilder.load(cob);
        if (mdBuilder.doesRequireSetup()) {
            amendments.add(mdBuilder::setup);
        }
    }

    public void finish(ClassBuilder clb) {
        amendments.forEach(a -> a.accept(clb));
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


        final ModuleDescriptor md;
        final Set<String> packages;
        final int index;
        Consumer<ClassBuilder> amendment;

        ModuleDescriptorBuilder(ModuleDescriptor md, Set<String> packages, int index) {
            if (md.isAutomatic()) {
                throw new InternalError("linking automatic module is not supported");
            }


            this.md = md;
            this.packages = packages;
            this.index = index;
        }

        private LoadableArray requiresArray() {
            var requiresArray = LoadableArray.of(
                    CD_REQUIRES,
                    sorted(md.requires()),
                    this::newRequires,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Requires",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(requiresArray);
            return requiresArray;
        }

        private LoadableArray exportArray() {
            var exportArray = LoadableArray.of(
                    CD_EXPORTS,
                    sorted(md.exports()),
                    this::loadExports,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Exports",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(exportArray);
            return exportArray;
        }

        private LoadableArray opensArray() {
            var opensArray = LoadableArray.of(
                    CD_OPENS,
                    sorted(md.opens()),
                    this::newOpens,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Opens",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(opensArray);
            return opensArray;
        }

        private LoadableArray providesArray() {
            var providesArray = LoadableArray.of(
                    CD_PROVIDES,
                    sorted(md.provides()),
                    this::newProvides,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Provides",
                    // number safe for a single page helper under 64K size limit
                    2000);

            setupLoadable(providesArray);
            return providesArray;
        }

        private LoadableSet packagesSet() {
            var packagesSet = LoadableSet.of(
                    sorted(packages),
                    STRING_LOADER,
                    PAGING_THRESHOLD,
                    ownerClassDesc,
                    "module" + index + "Packages",
                    STRING_PAGE_SIZE);

            setupLoadable(packagesSet);
            return packagesSet;
        }

        private void setupLoadable(Loadable loadable) {
            if (amendment == null) {
                amendment = loadable::setup;
            } else {
                amendment = amendment.andThen(loadable::setup);
            }
        }

        boolean doesRequireSetup() {
            return amendment != null;
        }

        void setup(ClassBuilder clb) {
            if (amendment != null) amendment.accept(clb);
        }

        void load(CodeBuilder cob) {
            // new jdk.internal.module.Builder
            cob.new_(CD_MODULE_BUILDER)
               .dup()
               .loadConstant(md.name())
               .invokespecial(CD_MODULE_BUILDER,
                              INIT_NAME,
                              MTD_void_String);
            if (md.isOpen()) {
                setModuleBit(cob, "open", true);
            }
            if (md.modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC)) {
                setModuleBit(cob, "synthetic", true);
            }
            if (md.modifiers().contains(ModuleDescriptor.Modifier.MANDATED)) {
                setModuleBit(cob, "mandated", true);
            }

            // requires
            requiresArray().load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "requires",
                              MTD_REQUIRES_ARRAY);

            // exports
            exportArray().load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                    "exports",
                    MTD_EXPORTS_ARRAY);

            // opens
            opensArray().load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "opens",
                              MTD_OPENS_ARRAY);

            // uses
            dedupSetBuilder.loadStringSet(cob, md.uses());
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "uses",
                              MTD_SET);

            // provides
            providesArray().load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "provides",
                              MTD_PROVIDES_ARRAY);

            // all packages
            packagesSet().load(cob);
            cob.invokevirtual(CD_MODULE_BUILDER,
                              "packages",
                              MTD_SET);

            // version
            md.version().ifPresent(v -> setModuleProperty(cob, "version", v.toString()));

            // main class
            md.mainClass().ifPresent(cn -> setModuleProperty(cob, "mainClass", cn));

            cob.loadConstant(md.hashCode())
               .invokevirtual(CD_MODULE_BUILDER,
                              "build",
                              MTD_ModuleDescriptor_int);
        }

        /*
         * Invoke Builder.<methodName>(boolean value)
         */
        void setModuleBit(CodeBuilder cob, String methodName, boolean value) {
            cob.loadConstant(value ? 1 : 0)
               .invokevirtual(CD_MODULE_BUILDER,
                              methodName,
                              MTD_BOOLEAN);
        }

        void setModuleProperty(CodeBuilder cob, String methodName, String value) {
            cob.loadConstant(value)
               .invokevirtual(CD_MODULE_BUILDER,
                              methodName,
                              MTD_STRING);
        }

        /*
         * Invoke Builder.newRequires(Set<Modifier> mods, String mn, String compiledVersion)
         *
         * Set<Modifier> mods = ...
         * Builder.newRequires(mods, mn, compiledVersion);
         */
        void newRequires(CodeBuilder cob, Requires require, int unused) {
            dedupSetBuilder.loadRequiresModifiers(cob, require.modifiers());
            cob.loadConstant(require.name());
            if (require.compiledVersion().isPresent()) {
                cob.loadConstant(require.compiledVersion().get().toString())
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