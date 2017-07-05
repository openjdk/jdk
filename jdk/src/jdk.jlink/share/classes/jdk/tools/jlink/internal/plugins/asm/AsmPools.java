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
package jdk.tools.jlink.internal.plugins.asm;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Exports;
import java.lang.module.ModuleDescriptor.Requires;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.PUBLIC;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.Sorter;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

/**
 * A container for pools of ClassReader and other resource files. A pool of all
 * the resources or a pool for a given module can be retrieved
 */
public final class AsmPools {

    /**
     * Sort the order in which the modules will be stored in the jimage file.
     */
    public interface ModuleSorter {

        /**
         * Sort the list of modules.
         *
         * @param modules The list of module names. The module will be stored in
         * the jimage following this order.
         * @return A list of module names that expresses the order in which the
         * modules are stored in the jimage.
         */
        public List<String> sort(List<String> modules);
    }

    private class AsmGlobalPoolImpl implements AsmGlobalPool {

        private Sorter sorter = null;

        private class GlobalWritableClassPool implements WritableClassPool {

            @Override
            public void addClass(ClassWriter writer) {
                visitFirstNonFailingPool((AsmModulePool pool) -> {
                    pool.getTransformedClasses().addClass(writer);
                });
            }

            @Override
            public void forgetClass(String className) {
                visitFirstNonFailingPool((AsmModulePool pool) -> {
                    pool.getTransformedClasses().forgetClass(className);
                });
            }

            @Override
            public ClassReader getClassReader(String binaryName) {
                return visitPools((AsmModulePool pool) -> {
                    return pool.getTransformedClasses().getClassReader(binaryName);
                });
            }

            @Override
            public Collection<Pool.ModuleData> getClasses() {
                List<Pool.ModuleData> all = new ArrayList<>();
                visitAllPools((AsmModulePool pool) -> {
                    for (Pool.ModuleData rf : pool.getTransformedClasses().getClasses()) {
                        all.add(rf);
                    }
                });
                return all;
            }

            @Override
            public ClassReader getClassReader(Pool.ModuleData res) {
                return visitPools((AsmModulePool pool) -> {
                    return pool.getTransformedClasses().getClassReader(res);
                });
            }

        }

        private class GlobalWritableResourcePool implements WritableResourcePool {

            @Override
            public void addResourceFile(ResourceFile resFile) {
                visitFirstNonFailingPool((AsmModulePool pool) -> {
                    pool.getTransformedResourceFiles().addResourceFile(resFile);
                });
            }

            @Override
            public void forgetResourceFile(String resourceName) {
                visitFirstNonFailingPool((AsmModulePool pool) -> {
                    pool.getTransformedResourceFiles().forgetResourceFile(resourceName);
                });
            }

            @Override
            public ResourceFile getResourceFile(String name) {
                return visitPools((AsmModulePool pool) -> {
                    return pool.getTransformedResourceFiles().getResourceFile(name);
                });
            }

            @Override
            public Collection<Pool.ModuleData> getResourceFiles() {
                List<Pool.ModuleData> all = new ArrayList<>();
                visitAllPools((AsmModulePool pool) -> {
                    for (Pool.ModuleData rf : pool.getTransformedResourceFiles().getResourceFiles()) {
                        all.add(rf);
                    }
                });
                return all;
            }

            @Override
            public ResourceFile getResourceFile(Pool.ModuleData res) {
                return visitPools((AsmModulePool pool) -> {
                    return pool.getTransformedResourceFiles().getResourceFile(res);
                });
            }

        }

        @Override
        public AsmPool.WritableClassPool getTransformedClasses() {
            return new GlobalWritableClassPool();
        }

        @Override
        public AsmPool.WritableResourcePool getTransformedResourceFiles() {
            return new GlobalWritableResourcePool();
        }

        @Override
        public void setSorter(AsmPool.Sorter sorter) {
            this.sorter = sorter;
        }

        @Override
        public Collection<Pool.ModuleData> getClasses() {
            List<Pool.ModuleData> all = new ArrayList<>();
            visitAllPools((AsmModulePool pool) -> {
                for (Pool.ModuleData rf : pool.getClasses()) {
                    all.add(rf);
                }
            });
            return all;
        }

        @Override
        public Collection<Pool.ModuleData> getResourceFiles() {
            List<Pool.ModuleData> all = new ArrayList<>();
            visitAllPools((AsmModulePool pool) -> {
                for (Pool.ModuleData rf : pool.getResourceFiles()) {
                    all.add(rf);
                }
            });
            return all;
        }

        @Override
        public AsmPool.ResourceFile getResourceFile(String binaryName) {
            return visitPools((AsmModulePool pool) -> {
                return pool.getResourceFile(binaryName);
            });
        }

        @Override
        public ClassReader getClassReader(String binaryName) {
            return visitPoolsEx((AsmModulePool pool) -> {
                return pool.getClassReader(binaryName);
            });
        }

        @Override
        public ResourceFile getResourceFile(Pool.ModuleData res) {
            return visitPools((AsmModulePool pool) -> {
                return pool.getResourceFile(res);
            });
        }

        @Override
        public ClassReader getClassReader(Pool.ModuleData res) {
            return visitPoolsEx((AsmModulePool pool) -> {
                return pool.getClassReader(res);
            });
        }

        @Override
        public void visitClassReaders(AsmPool.ClassReaderVisitor visitor) {
            visitAllPoolsEx((AsmModulePool pool) -> {
                pool.visitClassReaders(visitor);
            });
        }

        @Override
        public void visitResourceFiles(AsmPool.ResourceFileVisitor visitor) {
            visitAllPoolsEx((AsmModulePool pool) -> {
                pool.visitResourceFiles(visitor);
            });
        }

        @Override
        public void fillOutputResources(Pool outputResources) {
            AsmPools.this.fillOutputResources(outputResources);
        }

        @Override
        public void addPackageModuleMapping(String pkg, String module) {
            AsmModulePool p = pools.get(module);
            if (p == null) {
                throw new PluginException("Unknown module " + module);
            }
            p.addPackage(pkg);
        }

        @Override
        public Set<String> getAccessiblePackages(String module) {
            AsmModulePool p = pools.get(module);
            if (p == null) {
                return null;
            }
            ModuleDescriptor desc = p.getDescriptor();
            Set<String> packages = new HashSet<>();
            packages.addAll(p.getAllPackages());

            // Retrieve direct dependencies and indirect ones (public)
            Set<String> modules = new HashSet<>();
            for (Requires req : desc.requires()) {
                modules.add(req.name());
                addAllRequirePublicModules(req.name(), modules);
            }
            // Add exported packages of readable modules
            for (String readable : modules) {
                AsmModulePool mp = pools.get(readable);
                if (mp != null) {
                    for (Exports e : mp.getDescriptor().exports()) {
                        // exported to all or to the targeted module
                        if (e.targets().isEmpty() || e.targets().contains(module)) {
                            packages.add(e.source().replaceAll("\\.", "/"));
                        }
                    }

                }
            }
            return packages;
        }

        private void addAllRequirePublicModules(String module, Set<String> modules) {
            AsmModulePool p = pools.get(module);
            if (p != null) {
                for (Requires req : p.getDescriptor().requires()) {
                    if (req.modifiers().contains(PUBLIC)) {
                        modules.add(req.name());
                        addAllRequirePublicModules(req.name(), modules);
                    }
                }
            }
        }

    }

    private interface VoidPoolVisitor {

        void visit(AsmModulePool pool);
    }

    private interface VoidPoolVisitorEx {

        void visit(AsmModulePool pool);
    }

    private interface RetPoolVisitor<P> {

        P visit(AsmModulePool pool);
    }

    private final Map<String, AsmModulePool> pools = new LinkedHashMap<>();
    private final AsmModulePool[] poolsArray;
    private final AsmGlobalPoolImpl global;

    private ModuleSorter moduleSorter;

    /**
     * A new Asm pools.
     *
     * @param inputResources The raw resources to build the pool from.
     */
    public AsmPools(Pool inputResources) {
        Objects.requireNonNull(inputResources);
        Map<String, Pool> resPools = new LinkedHashMap<>();
        Map<String, ModuleDescriptor> descriptors = new HashMap<>();
        for (Pool.ModuleData res : inputResources.getContent()) {
            Pool p = resPools.get(res.getModule());
            if (p == null) {
                p = new PoolImpl(inputResources.getByteOrder(),
                        ((PoolImpl)inputResources).getStringTable());
                resPools.put(res.getModule(), p);
            }
            if (res.getPath().endsWith("module-info.class")) {
                ByteBuffer bb = ByteBuffer.wrap(res.getBytes());
                ModuleDescriptor descriptor = ModuleDescriptor.read(bb);
                descriptors.put(res.getModule(), descriptor);
            }
            p.add(res);
        }
        poolsArray = new AsmModulePool[resPools.size()];
        int i = 0;

        for (Entry<String, Pool> entry : resPools.entrySet()) {
            ModuleDescriptor descriptor = descriptors.get(entry.getKey());
            if (descriptor == null) {
                throw new PluginException("module-info.class not found for " + entry.getKey() + " module");
            }
            AsmModulePool p = new AsmPoolImpl(entry.getValue(),
                    entry.getKey(), this, descriptor);
            pools.put(entry.getKey(), p);
            poolsArray[i] = p;
            i += 1;
        }
        global = new AsmGlobalPoolImpl();
    }

    /**
     * The pool containing all classes and other resources.
     *
     * @return The global pool
     */
    public AsmGlobalPool getGlobalPool() {
        return global;
    }

    /**
     * A pool for a given module
     *
     * @param name The module name
     * @return The pool that contains content of the passed module or null if
     * the module doesn't exist.
     */
    public AsmModulePool getModulePool(String name) {
        Objects.requireNonNull(name);
        return pools.get(name);
    }

    /**
     * The array of module pools.
     * @return The module pool array.
     */
    public AsmModulePool[] getModulePools() {
        return poolsArray.clone();
    }

    /**
     * Set a module sorter. Sorter is used when computing the output resources.
     *
     * @param moduleSorter The module sorter
     */
    public void setModuleSorter(ModuleSorter moduleSorter) {
        Objects.requireNonNull(moduleSorter);
        this.moduleSorter = moduleSorter;
    }

    /**
     * Returns the pool of all the resources (transformed and unmodified). The
     * input resources are replaced by the transformed ones. If a sorter has
     * been set, it is used to sort in modules.
     *
     * @param outputResources The pool used to fill the jimage.
     */
    public void fillOutputResources(Pool outputResources) {
        // First sort modules
        List<String> modules = new ArrayList<>();
        for (String k : pools.keySet()) {
            modules.add(k);
        }
        if (moduleSorter != null) {
            modules = moduleSorter.sort(modules);
        }
        Pool output = new PoolImpl(outputResources.getByteOrder(),
                ((PoolImpl)outputResources).getStringTable());
        for (String mn : modules) {
            AsmPool pool = pools.get(mn);
            pool.fillOutputResources(output);
        }
        sort(outputResources, output, global.sorter);
    }

    static void sort(Pool outputResources,
            Pool transientOutput, Sorter sorter) {
        if (sorter != null) {
            List<String> order = sorter.sort(transientOutput);
            for (String s : order) {
                outputResources.add(transientOutput.get(s));
            }
        } else {
            for (ModuleData res : transientOutput.getContent()) {
                outputResources.add(res);
            }
        }
    }

    private void visitFirstNonFailingPool(VoidPoolVisitorEx pv) {
        boolean found = false;
        for (Entry<String, AsmModulePool> entry : pools.entrySet()) {
            try {
                pv.visit(entry.getValue());
                found = true;
                break;
            } catch (Exception ex) {
                // XXX OK, try  another one.
            }
        }
        if (!found) {
            throw new PluginException("No module found");
        }
    }

    private void visitAllPools(VoidPoolVisitor pv) {
        for (Entry<String, AsmModulePool> entry : pools.entrySet()) {
            pv.visit(entry.getValue());
        }
    }

    private void visitAllPoolsEx(VoidPoolVisitorEx pv) {
        for (Entry<String, AsmModulePool> entry : pools.entrySet()) {
            pv.visit(entry.getValue());
        }
    }

    private <P> P visitPoolsEx(RetPoolVisitor<P> pv) {
        P p = null;
        for (Entry<String, AsmModulePool> entry : pools.entrySet()) {
            try {
                p = pv.visit(entry.getValue());
                if (p != null) {
                    break;
                }
            } catch (Exception ex) {
                // XXX OK, try  another one.
            }
        }
        return p;
    }

    private <P> P visitPools(RetPoolVisitor<P> pv) {
        P p = null;
        for (Entry<String, AsmModulePool> entry : pools.entrySet()) {
            try {
                p = pv.visit(entry.getValue());
                if (p != null) {
                    break;
                }
            } catch (Exception ex) {
                // XXX OK, try  another one.
            }
        }
        return p;
    }
}
