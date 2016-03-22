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
 * @summary Test plugins
 * @author Andrei Eremeev
 * @modules jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins.asm
 * @run main PackageMappingTest
 */
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jdk.tools.jlink.internal.plugins.asm.AsmGlobalPool;
import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.ResourceFile;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.WritableResourcePool;
import jdk.tools.jlink.plugin.PluginException;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class PackageMappingTest extends AsmPluginTestBase {

    private final List<String> newFiles = Arrays.asList(
            "/java.base/a1/bbb/c",
            "/" + TEST_MODULE + "/a2/bbb/d"
    );

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new PackageMappingTest().test();
    }

    public void test() throws Exception {
        TestPlugin[] plugins = new TestPlugin[]{
            new PackageMappingPlugin(newFiles, false),
            new PackageMappingPlugin(newFiles, true)
        };
        for (TestPlugin p : plugins) {
            Pool pool = p.visit(getPool());
            p.test(getPool(), pool);
        }
    }

    public class PackageMappingPlugin extends TestPlugin {

        private final Map<String, List<ResourceFile>> newFiles;
        private final boolean testGlobal;

        private String getModuleName(String res) {
            return res.substring(1, res.indexOf("/", 1));
        }

        private PackageMappingPlugin(List<String> files, boolean testGlobal) {
            this.newFiles = new HashMap<>();
            this.testGlobal = testGlobal;
            for (String file : files) {
                String moduleName = getModuleName(file);
                String path = file.substring(1 + moduleName.length() + 1);
                newFiles.computeIfAbsent(moduleName, $ -> new ArrayList<>()).add(
                        new ResourceFile(path, new byte[0]));
            }
        }

        @Override
        public void visit() {
            testMapToUnknownModule();
            testMapPackageTwice();
            testPackageMapping();
        }

        @Override
        public void test(Pool inResources, Pool outResources) {
            Set<String> in = getPools().getGlobalPool().getResourceFiles().stream()
                    .map(ModuleData::getPath)
                    .collect(Collectors.toSet());
            Set<String> out = extractResources(outResources).stream()
                    .map(ModuleData::getPath)
                    .collect(Collectors.toSet());
            in.addAll(PackageMappingTest.this.newFiles);
            if (!Objects.equals(in, out)) {
                throw new AssertionError("Expected: " + in + ", got: " + outResources);
            }
        }

        private void testPackageMapping() {
            AsmGlobalPool globalPool = getPools().getGlobalPool();
            try {
                Map<String, Set<String>> mappedPackages = new HashMap<>();
                Function<String, Set<String>> produceSet = $ -> new HashSet<>();
                for (Map.Entry<String, List<ResourceFile>> entry : newFiles.entrySet()) {
                    String moduleName = entry.getKey();
                    Set<String> module = mappedPackages.computeIfAbsent(moduleName, produceSet);
                    AsmModulePool modulePool = getPools().getModulePool(moduleName);
                    for (ResourceFile r : entry.getValue()) {
                        String name = r.getPath();
                        String packageName = name.substring(0, name.lastIndexOf('/'));
                        if (module.add(packageName)) {
                            globalPool.addPackageModuleMapping(packageName, moduleName);
                        }
                        WritableResourcePool transformedResourceFiles = testGlobal
                                ? globalPool.getTransformedResourceFiles()
                                : modulePool.getTransformedResourceFiles();
                        transformedResourceFiles.addResourceFile(r);
                    }
                    try {
                        modulePool.getTransformedResourceFiles().addResourceFile(
                                new ResourceFile("a3/bbb", new byte[0]));
                        throw new AssertionError("Exception expected");
                    } catch (Exception ex) {
                        // expected
                    }
                }
                try {
                    globalPool.getTransformedResourceFiles().addResourceFile(
                            new ResourceFile("a3/bbb", new byte[0]));
                    throw new AssertionError("Exception expected");
                } catch (Exception ex) {
                    // expected
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void testMapPackageTwice() {
            try {
                AsmGlobalPool globalPool = getPools().getGlobalPool();
                globalPool.addPackageModuleMapping("a/p1", TEST_MODULE);
                globalPool.addPackageModuleMapping("a/p1", TEST_MODULE);
                throw new AssertionError("Exception expected after mapping a package twice to the same module");
            } catch (Exception e) {
                if (e instanceof PluginException) {
                    // expected
                    String message = e.getMessage();
                    if (!(TEST_MODULE + " module already contains package a.p1").equals(message)) {
                        throw new AssertionError(e);
                    }
                } else {
                    throw new AssertionError(e);
                }
            }
        }

        private void testMapToUnknownModule() {
            AsmModulePool unknownModule = getPools().getModulePool("UNKNOWN");
            if (unknownModule != null) {
                throw new AssertionError("getModulePool returned not null value: " + unknownModule.getModuleName());
            }
            try {
                AsmGlobalPool globalPool = getPools().getGlobalPool();
                globalPool.addPackageModuleMapping("a/b", "UNKNOWN");
                throw new AssertionError("Exception expected after mapping a package to unknown module");
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || !message.startsWith("Unknown module UNKNOWN")) {
                    throw new AssertionError(e);
                }
            }
        }
    }
}
