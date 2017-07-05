/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.jimage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static jdk.internal.jimage.PackageModuleMap.*;

public class ImageModules {
    protected final Map<Loader, LoaderModuleData> loaders = new LinkedHashMap<>();
    protected final Map<String, Set<String>> localPkgs = new HashMap<>();

    protected ImageModules() {}

    public ImageModules(Set<String> bootModules,
                        Set<String> extModules,
                        Set<String> appModules) throws IOException {
        mapModulesToLoader(Loader.BOOT_LOADER, bootModules);
        mapModulesToLoader(Loader.EXT_LOADER, extModules);
        mapModulesToLoader(Loader.APP_LOADER, appModules);
    }

    public Map<String, Set<String>> packages() {
        return localPkgs;
    }

    // ## FIXME: should be package-private
    // When jlink legacy format support is removed, it should
    // use the package table in the jimage.
    public void setPackages(String mn, Set<String> pkgs) {
        localPkgs.put(mn, pkgs);
    }

    /*
     * Returns the name of modules mapped to a given class loader in the image
     */
    public Set<String> getModules(Loader type) {
        if (loaders.containsKey(type)) {
            return loaders.get(type).modules();
        } else {
            return Collections.emptySet();
        }
    }

    private void mapModulesToLoader(Loader loader, Set<String> modules) {
        if (modules.isEmpty())
            return;

        // put java.base first
        Set<String> mods = new LinkedHashSet<>();
        modules.stream()
               .filter(m -> m.equals("java.base"))
               .forEach(mods::add);
        modules.stream().sorted()
               .filter(m -> !m.equals("java.base"))
               .forEach(mods::add);
        loaders.put(loader, new LoaderModuleData(loader, mods));
    }

    enum Loader {
        BOOT_LOADER(0, "bootmodules"),
        EXT_LOADER(1, "extmodules"),
        APP_LOADER(2, "appmodules");  // ## may be more than 1 loader

        final int id;
        final String name;
        Loader(int id, String name) {
            this.id = id;
            this.name = name;
        }

        String getName() {
            return name;
        }
        static Loader get(int id) {
            switch (id) {
                case 0: return BOOT_LOADER;
                case 1: return EXT_LOADER;
                case 2: return APP_LOADER;
                default:
                    throw new IllegalArgumentException("invalid loader id: " + id);
            }
        }
        public int id() { return id; }
    }

    public class LoaderModuleData {
        private final Loader loader;
        private final Set<String> modules;
        LoaderModuleData(Loader loader, Set<String> modules) {
            this.loader = loader;
            this.modules = Collections.unmodifiableSet(modules);
        }

        Set<String> modules() {
            return modules;
        }
        Loader loader() { return loader; }
    }

    ModuleIndex buildModuleIndex(Loader type, BasicImageWriter writer) {
        return new ModuleIndex(getModules(type), writer);
    }

    /*
     * Generate module name table and the package map as resources
     * in the modular image
     */
    public class ModuleIndex {
        final Map<String, Integer> moduleOffsets = new LinkedHashMap<>();
        final Map<String, List<Integer>> packageOffsets = new HashMap<>();
        final int size;
        public ModuleIndex(Set<String> mods, BasicImageWriter writer) {
            // module name offsets
            writer.addLocation(MODULES_ENTRY, 0, 0, mods.size() * 4);
            long offset = mods.size() * 4;
            for (String mn : mods) {
                moduleOffsets.put(mn, writer.addString(mn));
                List<Integer> poffsets = localPkgs.get(mn).stream()
                        .map(pn -> pn.replace('.', '/'))
                        .map(writer::addString)
                        .collect(Collectors.toList());
                // package name offsets per module
                String entry = mn + "/" + PACKAGES_ENTRY;
                int bytes = poffsets.size() * 4;
                writer.addLocation(entry, offset, 0, bytes);
                offset += bytes;
                packageOffsets.put(mn, poffsets);
            }
            this.size = (int) offset;
        }

        void writeTo(DataOutputStream out) throws IOException {
            for (int moffset : moduleOffsets.values()) {
                out.writeInt(moffset);
            }
            for (String mn : moduleOffsets.keySet()) {
                for (int poffset : packageOffsets.get(mn)) {
                    out.writeInt(poffset);
                }
            }
        }

        int size() {
            return size;
        }
    }
}
