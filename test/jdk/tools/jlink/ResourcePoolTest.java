/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import jdk.tools.jlink.internal.ResourcePoolManager;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolModule;
import jdk.tools.jlink.plugin.ResourcePoolEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary Test a pool containing jimage resources and classes.
 * @author Jean-Francois Denise
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 * @run junit ResourcePoolTest
 */
public class ResourcePoolTest {
    private static final String SUFFIX = "END";

    @Test
    public void resourceVisitor() throws Exception {
        ResourcePoolManager input = new ResourcePoolManager();
        for (int i = 0; i < 1000; ++i) {
            String module = "/module" + (i / 10);
            String resourcePath = module + "/java/package" + i;
            byte[] bytes = resourcePath.getBytes();
            input.add(ResourcePoolEntry.create(resourcePath, bytes));
        }
        ResourcePoolManager output = new ResourcePoolManager();
        ResourceVisitor visitor = new ResourceVisitor();
        input.resourcePool().transformAndCopy(visitor, output.resourcePoolBuilder());
        assertNotEquals(0, visitor.getAmountBefore(), "Resources not found");
        assertEquals(visitor.getAmountBefore(), input.entryCount(), "Number of visited resources");
        assertEquals(visitor.getAmountAfter(), output.entryCount(), "Number of added resources");
        output.entries().forEach(outResource -> {
            String path = outResource.path().replaceAll(SUFFIX + "$", "");
            assertTrue(input.findEntry(path).isPresent(), "Unknown resource: " + path);
        });
    }

    private static class ResourceVisitor implements Function<ResourcePoolEntry, ResourcePoolEntry> {

        private int amountBefore;
        private int amountAfter;

        @Override
        public ResourcePoolEntry apply(ResourcePoolEntry resource) {
            int index = ++amountBefore % 3;
            switch (index) {
                case 0:
                    ++amountAfter;
                    return ResourcePoolEntry.create(resource.path() + SUFFIX,
                            resource.type(), resource.contentBytes());
                case 1:
                    ++amountAfter;
                    return resource.copyWithContent(resource.contentBytes());
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

    @Test
    public void resourceAdding() {
        Map<String, List<String>> samples = Map.of(
                "java.base", List.of("java/lang/Object", "java/lang/String"),
                "java.management", List.of("javax/management/ObjectName"));
        test(samples, (resources, module, path) -> {
            try {
                resources.add(ResourcePoolEntry.create(path, new byte[0]));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        test(samples, (resources, module, path) -> {
            try {
                resources.add(ResourcePoolManager.
                        newCompressedResource(ResourcePoolEntry.create(path, new byte[0]),
                                ByteBuffer.allocate(99), "bitcruncher",
                                ((ResourcePoolManager)resources).getStringTable(), ByteOrder.nativeOrder()));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    public void packageInference() {
        Map<String, List<String>> samples = Map.of(
                "java.base", List.of("NoPackage", "java/lang/String", "java/util/List"));
        ResourcePoolManager manager = test(samples, (resources, module, path) -> {
            try {
                resources.add(ResourcePoolEntry.create(path, new byte[0]));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Optional<ResourcePoolModule> modBase = manager.moduleView().findModule("java.base");
        assertTrue(modBase.isPresent());
        // Empty packages are not included (and should normally not exist).
        assertEquals(Set.of("java.lang", "java.util"), modBase.get().packages());
    }

    @Test
    public void packageInference_previewOnly() {
        Map<String, List<String>> samples = Map.of(
                "java.base", List.of(
                        "java/lang/Object",
                        "java/lang/String",
                        "java/util/List",
                        "META-INF/preview/java/lang/String",
                        "META-INF/preview/java/extra/PreviewOnly"));
        ResourcePoolManager manager = test(samples, (resources, module, path) -> {
            try {
                resources.add(ResourcePoolEntry.create(path, new byte[0]));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Optional<ResourcePoolModule> modBase = manager.moduleView().findModule("java.base");
        assertTrue(modBase.isPresent());
        // Preview only package is included, and no packages start with 'META-INF'.
        assertEquals(Set.of("java.lang", "java.util", "java.extra"), modBase.get().packages());
        // But the preview resources exist in the META-INF/preview namespace.
        assertTrue(modBase.get().findEntry("/java.base/META-INF/preview/java/extra/PreviewOnly.class").isPresent());
    }

    private ResourcePoolManager test(Map<String, List<String>> samples, ResourceAdder adder) {
        assertFalse(samples.isEmpty(), "No sample to test");
        ResourcePoolManager resources = new ResourcePoolManager();
        samples.forEach((module, clazzes) -> {
            clazzes.forEach(clazz -> {
                String path = "/" + module + "/" + clazz + ".class";
                adder.add(resources, module, path);
            });
        });
        samples.forEach((module, clazzes) -> {
            clazzes.forEach(clazz -> {
                String path = "/" + module + "/" + clazz + ".class";
                Optional<ResourcePoolEntry> res = resources.findEntry(path);
                assertTrue(res.isPresent(), "Resource not found " + path);
                checkModule(resources.resourcePool(), res.get());
                assertTrue(resources.findEntry(clazz).isEmpty(), "Resource found " + clazz);
            });
        });
        long resourcesCount = samples.values().stream().mapToInt(List::size).sum();
        assertEquals(resourcesCount, resources.entryCount(), "Invalid number of resources");
        assertEquals(samples.size(), resources.moduleCount(), "Invalid number of modules");
        return resources;
    }

    private void checkModule(ResourcePool resources, ResourcePoolEntry res) {
        Optional<ResourcePoolModule> optMod = resources.moduleView().findModule(res.moduleName());
        assertTrue(optMod.isPresent(), "No module " + res.moduleName());
        ResourcePoolModule m = optMod.get();
        assertEquals(res.moduleName(), m.name(), "Not right module name " + res.moduleName());
        assertTrue(m.findEntry(res.path()).isPresent(),
                "resource " + res.path() + " not in module " + m.name());
    }

    @Test
    public void resourcesAfterCompression() throws Exception {
        ResourcePoolManager resources1 = new ResourcePoolManager();
        ResourcePoolEntry res1 = ResourcePoolEntry.create("/module1/toto1", new byte[0]);
        ResourcePoolEntry res2 = ResourcePoolEntry.create("/module2/toto1", new byte[0]);
        resources1.add(res1);
        resources1.add(res2);

        checkResources(resources1, res1, res2);
        ResourcePoolManager resources2 = new ResourcePoolManager();
        ResourcePoolEntry res3 = ResourcePoolEntry.create("/module2/toto1", new byte[7]);
        resources2.add(res3);
        resources2.add(ResourcePoolManager.newCompressedResource(res1,
                ByteBuffer.allocate(7), "zip", resources1.getStringTable(),
                ByteOrder.nativeOrder()));
        checkResources(resources2, res1, res2);
    }

    private void checkResources(ResourcePoolManager resources, ResourcePoolEntry... expected) {
        List<String> modules = new ArrayList();
        resources.modules().forEach(m -> {
            modules.add(m.name());
        });
        for (ResourcePoolEntry res : expected) {
            assertTrue(resources.contains(res), "Resource not found: " + res);
            assertTrue(resources.findEntry(res.path()).isPresent(), "Resource not found: " + res);
            assertTrue(modules.contains(res.moduleName()), "Module not found: " + res.moduleName());
            assertThrows(RuntimeException.class, () -> resources.add(res),
                    res + " already present, but an exception is not thrown");
        }

        ResourcePoolEntry toAdd = ResourcePoolEntry.create("/module2/toto1", new byte[0]);
        assertThrows(RuntimeException.class, () -> resources.add(toAdd),
                "ResourcePool is read-only, but an exception is not thrown");
    }

    interface ResourceAdder {
        void add(ResourcePoolManager resources, String module, String path);
    }
}
