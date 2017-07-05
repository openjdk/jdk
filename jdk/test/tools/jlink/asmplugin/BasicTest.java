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
 * @summary Test basic functionality.
 * @author Jean-Francois Denise
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins.asm
 * @build AsmPluginTestBase
 * @run main BasicTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.internal.plugins.asm.AsmPool;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class BasicTest extends AsmPluginTestBase {

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new BasicTest().test();
    }

    @Override
    public void test() throws Exception {
        BasicPlugin basicPlugin = new BasicPlugin(getClasses());
        Pool res = basicPlugin.visit(getPool());
        basicPlugin.test(getPool(), res);
    }

    private class BasicPlugin extends TestPlugin {

        private final List<String> classes;

        public BasicPlugin(List<String> classes) {
            this.classes = classes;
        }

        @Override
        public void visit() {
            for (String m : MODULES.keySet()) {
                AsmModulePool pool = getPools().getModulePool(m);
                if (pool == null) {
                    throw new AssertionError(m + " pool not found");
                }
                if(!pool.getModuleName().equals(m)) {
                    throw new AssertionError("Invalid module name " +
                            pool.getModuleName() + " should be "+ m);
                }
                if (pool.getClasses().size() == 0 && !m.equals(TEST_MODULE)) {
                    throw new AssertionError("Empty pool " + m);
                }
                pool.addPackage("toto");
                if (!pool.getTransformedClasses().getClasses().isEmpty()) {
                    throw new AssertionError("Should be empty");
                }
                for(String res : MODULES.get(m)) {
                    AsmPool.ResourceFile resFile = pool.getResourceFile(res);
                    if(resFile == null) {
                        throw new AssertionError("No resource file for " + res);
                    }
                }
            }
            try {
                testPools();
                testVisitor();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            if (!isVisitCalled()) {
                throw new AssertionError("Resources not visited");
            }
            if (inResources.getContent().size() != outResources.getContent().size()) {
                throw new AssertionError("Input size " + inResources.getContent().size() +
                        " != to " + outResources.getContent().size());
            }
        }

        private void testVisitor() throws IOException {
            List<String> seen = new ArrayList<>();
            getPools().getGlobalPool().visitClassReaders((reader) -> {
                String className = reader.getClassName();
                // Wrong naming of module-info.class in ASM
                if (className.endsWith("module-info")) {
                    return null;
                }
                if (!classes.contains(className)) {
                    throw new AssertionError("Class is not expected " + className);
                }
                if (getPools().getGlobalPool().getClassReader(className) == null) {
                    throw new AssertionError("Class not found in pool " + className);
                }
                seen.add(className);
                return null;
            });

            if (!seen.equals(classes)) {
                throw new AssertionError("Expected and seen are not equal");
            }
        }

        private void testPools() throws IOException {
            Set<String> remain = new HashSet<>(classes);
            for (ModuleData res : getPools().getGlobalPool().getClasses()) {
                ClassReader reader = getPools().getGlobalPool().getClassReader(res);
                String className = reader.getClassName();
                // Wrong naming of module-info.class in ASM
                if (className.endsWith("module-info")) {
                    continue;
                }
                if (!classes.contains(className)) {
                    throw new AssertionError("Class is not expected " + className);
                }
                if (getPools().getGlobalPool().getClassReader(className) == null) {
                    throw new AssertionError("Class " + className + " not found in pool ");
                }
                // Check the module pool
                boolean found = false;
                for(AsmModulePool mp : getPools().getModulePools()) {
                    if(mp.getClassReader(className) != null) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    throw new AssertionError("No modular pool for " +
                            className);
                }
                remain.remove(className);
            }
            if (!remain.isEmpty()) {
                throw new AssertionError("Remaining classes " + remain);
            }
        }
    }
}
