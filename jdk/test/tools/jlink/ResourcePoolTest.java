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
 * @test
 * @summary Test a pool containing jimage resources and classes.
 * @author Jean-Francois Denise
 * @modules jdk.jlink/jdk.tools.jlink.internal
 * @run build ResourcePoolTest
 * @run main ResourcePoolTest
 */

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.tools.jlink.internal.PoolImpl;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.Module;
import jdk.tools.jlink.plugin.Pool.ModuleData;
import jdk.tools.jlink.plugin.Pool.ModuleDataType;
import jdk.tools.jlink.plugin.Pool.Visitor;

public class ResourcePoolTest {

    public static void main(String[] args) throws Exception {
        new ResourcePoolTest().test();
    }

    public void test() throws Exception {
        checkResourceAdding();
        checkResourceVisitor();
        checkResourcesAfterCompression();
    }

    private static final String SUFFIX = "END";

    private void checkResourceVisitor() throws Exception {
        Pool input = new PoolImpl();
        for (int i = 0; i < 1000; ++i) {
            String module = "/module" + (i / 10);
            String resourcePath = module + "/java/package" + i;
            byte[] bytes = resourcePath.getBytes();
            input.add(new ModuleData(module, resourcePath,
                    ModuleDataType.CLASS_OR_RESOURCE,
                    new ByteArrayInputStream(bytes), bytes.length));
        }
        Pool output = new PoolImpl();
        ResourceVisitor visitor = new ResourceVisitor();
        input.visit(visitor, output);
        if (visitor.getAmountBefore() == 0) {
            throw new AssertionError("Resources not found");
        }
        if (visitor.getAmountBefore() != input.getContent().size()) {
            throw new AssertionError("Number of visited resources. Expected: " +
                    visitor.getAmountBefore() + ", got: " + input.getContent().size());
        }
        if (visitor.getAmountAfter() != output.getContent().size()) {
            throw new AssertionError("Number of added resources. Expected: " +
                    visitor.getAmountAfter() + ", got: " + output.getContent().size());
        }
        for (ModuleData outResource : output.getContent()) {
            String path = outResource.getPath().replaceAll(SUFFIX + "$", "");
            ModuleData inResource = input.get(path);
            if (inResource == null) {
                throw new AssertionError("Unknown resource: " + path);
            }
        }
    }

    private static class ResourceVisitor implements Visitor {

        private int amountBefore;
        private int amountAfter;

        @Override
        public ModuleData visit(ModuleData resource) {
            int index = ++amountBefore % 3;
            switch (index) {
                case 0:
                    ++amountAfter;
                    return new ModuleData(resource.getModule(), resource.getPath() + SUFFIX,
                            resource.getType(), resource.stream(), resource.getLength());
                case 1:
                    ++amountAfter;
                    return new ModuleData(resource.getModule(), resource.getPath(),
                            resource.getType(), resource.stream(), resource.getLength());
            }
            return null;
        }

        public int getAmountAfter() {
            return amountAfter;
        }

        public int getAmountBefore() {
            return amountBefore;
        }
    }

    private void checkResourceAdding() {
        List<String> samples = new ArrayList<>();
        samples.add("java.base");
        samples.add("java/lang/Object");
        samples.add("java.base");
        samples.add("java/lang/String");
        samples.add("java.management");
        samples.add("javax/management/ObjectName");
        test(samples, (resources, module, path) -> {
            try {
                resources.add(new ModuleData(module, path,
                        ModuleDataType.CLASS_OR_RESOURCE,
                        new ByteArrayInputStream(new byte[0]), 0));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        test(samples, (resources, module, path) -> {
            try {
                resources.add(PoolImpl.
                        newCompressedResource(new ModuleData(module, path,
                                ModuleDataType.CLASS_OR_RESOURCE,
                                new ByteArrayInputStream(new byte[0]), 0),
                                ByteBuffer.allocate(99), "bitcruncher", null,
                                ((PoolImpl)resources).getStringTable(), ByteOrder.nativeOrder()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void test(List<String> samples, ResourceAdder adder) {
        if (samples.isEmpty()) {
            throw new AssertionError("No sample to test");
        }
        Pool resources = new PoolImpl();
        Set<String> modules = new HashSet<>();
        for (int i = 0; i < samples.size(); i++) {
            String module = samples.get(i);
            modules.add(module);
            i++;
            String clazz = samples.get(i);
            String path = "/" + module + "/" + clazz + ".class";
            adder.add(resources, module, path);
        }
        for (int i = 0; i < samples.size(); i++) {
            String module = samples.get(i);
            i++;
            String clazz = samples.get(i);
            String path = "/" + module + "/" + clazz + ".class";
            ModuleData res = resources.get(path);
            checkModule(resources, res);
            if (res == null) {
                throw new AssertionError("Resource not found " + path);
            }
            ModuleData res2 = resources.get(clazz);
            if (res2 != null) {
                throw new AssertionError("Resource found " + clazz);
            }
        }
        if (resources.getContent().size() != samples.size() / 2) {
            throw new AssertionError("Invalid number of resources");
        }
    }

    private void checkModule(Pool resources, ModuleData res) {
        Module m = resources.getModule(res.getModule());
        if (m == null) {
            throw new AssertionError("No module " + res.getModule());
        }
        if (!m.getName().equals(res.getModule())) {
            throw new AssertionError("Not right module name " + res.getModule());
        }
        if (m.get(res.getPath()) == null) {
            throw new AssertionError("resource " + res.getPath()
                    + " not in module " + m.getName());
        }
    }

    private void checkResourcesAfterCompression() throws Exception {
        PoolImpl resources1 = new PoolImpl();
        ModuleData res1 = new ModuleData("module1", "/module1/toto1",
                ModuleDataType.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[0]), 0);
        ModuleData res2 = new ModuleData("module2", "/module2/toto1",
                ModuleDataType.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[0]), 0);
        resources1.add(res1);
        resources1.add(res2);

        checkResources(resources1, res1, res2);
        Pool resources2 = new PoolImpl();
        ModuleData res3 = new ModuleData("module2", "/module2/toto1",
                ModuleDataType.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[7]), 7);
        resources2.add(res3);
        resources2.add(PoolImpl.newCompressedResource(res1,
                ByteBuffer.allocate(7), "zip", null, resources1.getStringTable(),
                ByteOrder.nativeOrder()));
        checkResources(resources2, res1, res2);
    }

    private void checkResources(Pool resources, ModuleData... expected) {
        Collection<Module> ms = resources.getModules();
        List<String> modules = new ArrayList();
        for(Module m : ms) {
            modules.add(m.getName());
        }
        for (ModuleData res : expected) {
            if (!resources.contains(res)) {
                throw new AssertionError("Resource not found: " + res);
            }

            if (resources.get(res.getPath()) == null) {
                throw new AssertionError("Resource not found: " + res);
            }

            if (!modules.contains(res.getModule())) {
                throw new AssertionError("Module not found: " + res.getModule());
            }

            if (!resources.getContent().contains(res)) {
                throw new AssertionError("Resources not found: " + res);
            }

            try {
                resources.add(res);
                throw new AssertionError(res + " already present, but an exception is not thrown");
            } catch (Exception ex) {
                // Expected
            }
        }

        if (resources.isReadOnly()) {
            throw new AssertionError("ReadOnly resources");
        }

        ((PoolImpl) resources).setReadOnly();
        try {
            resources.add(new ModuleData("module2",  "/module2/toto1",
                    ModuleDataType.CLASS_OR_RESOURCE, new ByteArrayInputStream(new byte[0]), 0));
            throw new AssertionError("Pool is read-only, but an exception is not thrown");
        } catch (Exception ex) {
            // Expected
        }
    }

    interface ResourceAdder {
        void add(Pool resources, String module, String path);
    }
}
