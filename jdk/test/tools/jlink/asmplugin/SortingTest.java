/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * Asm plugin testing.
 * @test
 * @summary Test resource sorting.
 * @author Jean-Francois Denise
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins.asm
 * @build AsmPluginTestBase
 * @run main SortingTest
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class SortingTest extends AsmPluginTestBase {

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new SortingTest().test();
    }

    @Override
    public void test() {
        try {
            classSorting();
            moduleSorting();
        } catch (Exception ex) {
            throw new PluginException(ex);
        }
    }

    private void classSorting() throws Exception {
        List<String> sorted = new ArrayList<>(getResources());
        sorted.sort(null);
        ClassSorterPlugin sorterPlugin = new ClassSorterPlugin(sorted);
        Pool resourcePool = sorterPlugin.visit(getPool());
        sorterPlugin.test(getPool(), resourcePool);
    }

    private String getModuleName(String p) {
        return p.substring(1, p.indexOf('/', 1));
    }

    private void moduleSorting() throws Exception {
        List<String> sorted = new ArrayList<>(getResources());
        sorted.sort((s1, s2) -> -getModuleName(s1).compareTo(getModuleName(s2)));
        ModuleSorterPlugin sorterPlugin = new ModuleSorterPlugin();
        Pool resourcePool = sorterPlugin.visit(getPool());
        sorterPlugin.test(getPool(), resourcePool);
    }

    private class ModuleSorterPlugin extends TestPlugin {

        @Override
        public void visit() {
            for (AsmModulePool modulePool : getPools().getModulePools()) {
                modulePool.setSorter(resources -> {
                    List<String> sort = resources.getContent().stream()
                            .map(ModuleData::getPath)
                            .collect(Collectors.toList());
                    sort.sort(null);
                    return sort;
                });
            }
            getPools().setModuleSorter(modules -> {
                modules.sort((s1, s2) -> -s1.compareTo(s2));
                return modules;
            });
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            if (!isVisitCalled()) {
                throw new AssertionError("Resources not visited");
            }
            List<String> sortedResourcePaths = outResources.getContent().stream()
                    .map(ModuleData::getPath)
                    .collect(Collectors.toList());

            List<String> defaultResourceOrder = new ArrayList<>();
            for (ModuleData r : inResources.getContent()) {
                if (!inResources.getContent().contains(r)) {
                    throw new AssertionError("Resource " + r.getPath() + " not in result pool");
                }
                defaultResourceOrder.add(r.getPath());
            }
            // Check that default sorting is not equal to sorted one
            if (defaultResourceOrder.equals(sortedResourcePaths)) {
                throw new AssertionError("Sorting not applied, default ordering");
            }
            // Check module order.
            for (int i = 0; i < sortedResourcePaths.size() - 1; ++i) {
                String first = sortedResourcePaths.get(i);
                String p1 = getModuleName(first);
                String second = sortedResourcePaths.get(i + 1);
                String p2 = getModuleName(second);
                if (p1.compareTo(p2) < 0 || p1.compareTo(p2) == 0 &&
                        removeModule(first).compareTo(removeModule(second)) >= 0) {
                    throw new AssertionError("Modules are not sorted properly: resources: " + first + " " + second);
                }
            }
        }
    }

    private class ClassSorterPlugin extends TestPlugin {

        private final List<String> expectedClassesOrder;

        private ClassSorterPlugin(List<String> expectedClassesOrder) {
            this.expectedClassesOrder = expectedClassesOrder;
        }

        @Override
        public void visit() {
            getPools().getGlobalPool().setSorter(
                    (resources) -> expectedClassesOrder.stream()
                            .map(resources::get)
                            .map(ModuleData::getPath)
                            .collect(Collectors.toList()));
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            if (!isVisitCalled()) {
                throw new AssertionError("Resources not visited");
            }
            List<String> sortedResourcePaths = outResources.getContent().stream()
                    .map(ModuleData::getPath)
                    .collect(Collectors.toList());

            List<String> defaultResourceOrder = new ArrayList<>();
            for (ModuleData r : getPool().getContent()) {
                if (!getPool().getContent().contains(r)) {
                    throw new AssertionError("Resource " + r.getPath() + " not in result pool");
                }
                defaultResourceOrder.add(r.getPath());
            }
            // Check that default sorting is not equal to sorted one
            if (defaultResourceOrder.equals(sortedResourcePaths)) {
                throw new AssertionError("Sorting not applied, default ordering");
            }
            // Check that sorted is equal to result.
            if (!expectedClassesOrder.equals(sortedResourcePaths)) {
                throw new AssertionError("Sorting not properly applied");
            }
        }
    }
}
