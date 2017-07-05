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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import jdk.tools.jlink.internal.ModulePoolImpl;
import jdk.tools.jlink.plugin.ModulePool;
import jdk.tools.jlink.plugin.LinkModule;
import jdk.tools.jlink.plugin.ModuleEntry;
import jdk.tools.jlink.plugin.ModulePool;

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
        ModulePool input = new ModulePoolImpl();
        for (int i = 0; i < 1000; ++i) {
            String module = "/module" + (i / 10);
            String resourcePath = module + "/java/package" + i;
            byte[] bytes = resourcePath.getBytes();
            input.add(ModuleEntry.create(module, resourcePath,
                    ModuleEntry.Type.CLASS_OR_RESOURCE,
                    new ByteArrayInputStream(bytes), bytes.length));
        }
        ModulePool output = new ModulePoolImpl();
        ResourceVisitor visitor = new ResourceVisitor();
        input.transformAndCopy(visitor, output);
        if (visitor.getAmountBefore() == 0) {
            throw new AssertionError("Resources not found");
        }
        if (visitor.getAmountBefore() != input.getEntryCount()) {
            throw new AssertionError("Number of visited resources. Expected: " +
                    visitor.getAmountBefore() + ", got: " + input.getEntryCount());
        }
        if (visitor.getAmountAfter() != output.getEntryCount()) {
            throw new AssertionError("Number of added resources. Expected: " +
                    visitor.getAmountAfter() + ", got: " + output.getEntryCount());
        }
        output.entries().forEach(outResource -> {
            String path = outResource.getPath().replaceAll(SUFFIX + "$", "");
            if (!input.findEntry(path).isPresent()) {
                throw new AssertionError("Unknown resource: " + path);
            }
        });
    }

    private static class ResourceVisitor implements Function<ModuleEntry, ModuleEntry> {

        private int amountBefore;
        private int amountAfter;

        @Override
        public ModuleEntry apply(ModuleEntry resource) {
            int index = ++amountBefore % 3;
            switch (index) {
                case 0:
                    ++amountAfter;
                    return ModuleEntry.create(resource.getModule(), resource.getPath() + SUFFIX,
                            resource.getType(), resource.stream(), resource.getLength());
                case 1:
                    ++amountAfter;
                    return ModuleEntry.create(resource.getModule(), resource.getPath(),
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
                resources.add(ModuleEntry.create(module, path,
                        ModuleEntry.Type.CLASS_OR_RESOURCE,
                        new ByteArrayInputStream(new byte[0]), 0));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        test(samples, (resources, module, path) -> {
            try {
                resources.add(ModulePoolImpl.
                        newCompressedResource(ModuleEntry.create(module, path,
                                ModuleEntry.Type.CLASS_OR_RESOURCE,
                                new ByteArrayInputStream(new byte[0]), 0),
                                ByteBuffer.allocate(99), "bitcruncher", null,
                                ((ModulePoolImpl)resources).getStringTable(), ByteOrder.nativeOrder()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void test(List<String> samples, ResourceAdder adder) {
        if (samples.isEmpty()) {
            throw new AssertionError("No sample to test");
        }
        ModulePool resources = new ModulePoolImpl();
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
            Optional<ModuleEntry> res = resources.findEntry(path);
            if (!res.isPresent()) {
                throw new AssertionError("Resource not found " + path);
            }
            checkModule(resources, res.get());
            if (resources.findEntry(clazz).isPresent()) {
                throw new AssertionError("Resource found " + clazz);
            }
        }
        if (resources.getEntryCount() != samples.size() / 2) {
            throw new AssertionError("Invalid number of resources");
        }
    }

    private void checkModule(ModulePool resources, ModuleEntry res) {
        Optional<LinkModule> optMod = resources.findModule(res.getModule());
        if (!optMod.isPresent()) {
            throw new AssertionError("No module " + res.getModule());
        }
        LinkModule m = optMod.get();
        if (!m.getName().equals(res.getModule())) {
            throw new AssertionError("Not right module name " + res.getModule());
        }
        if (!m.findEntry(res.getPath()).isPresent()) {
            throw new AssertionError("resource " + res.getPath()
                    + " not in module " + m.getName());
        }
    }

    private void checkResourcesAfterCompression() throws Exception {
        ModulePoolImpl resources1 = new ModulePoolImpl();
        ModuleEntry res1 = ModuleEntry.create("module1", "/module1/toto1",
                ModuleEntry.Type.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[0]), 0);
        ModuleEntry res2 = ModuleEntry.create("module2", "/module2/toto1",
                ModuleEntry.Type.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[0]), 0);
        resources1.add(res1);
        resources1.add(res2);

        checkResources(resources1, res1, res2);
        ModulePool resources2 = new ModulePoolImpl();
        ModuleEntry res3 = ModuleEntry.create("module2", "/module2/toto1",
                ModuleEntry.Type.CLASS_OR_RESOURCE,
                new ByteArrayInputStream(new byte[7]), 7);
        resources2.add(res3);
        resources2.add(ModulePoolImpl.newCompressedResource(res1,
                ByteBuffer.allocate(7), "zip", null, resources1.getStringTable(),
                ByteOrder.nativeOrder()));
        checkResources(resources2, res1, res2);
    }

    private void checkResources(ModulePool resources, ModuleEntry... expected) {
        List<String> modules = new ArrayList();
        resources.modules().forEach(m -> {
            modules.add(m.getName());
        });
        for (ModuleEntry res : expected) {
            if (!resources.contains(res)) {
                throw new AssertionError("Resource not found: " + res);
            }

            if (!resources.findEntry(res.getPath()).isPresent()) {
                throw new AssertionError("Resource not found: " + res);
            }

            if (!modules.contains(res.getModule())) {
                throw new AssertionError("Module not found: " + res.getModule());
            }

            if (!resources.contains(res)) {
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

        ((ModulePoolImpl) resources).setReadOnly();
        try {
            resources.add(ModuleEntry.create("module2",  "/module2/toto1",
                    ModuleEntry.Type.CLASS_OR_RESOURCE, new ByteArrayInputStream(new byte[0]), 0));
            throw new AssertionError("ModulePool is read-only, but an exception is not thrown");
        } catch (Exception ex) {
            // Expected
        }
    }

    interface ResourceAdder {
        void add(ModulePool resources, String module, String path);
    }
}
