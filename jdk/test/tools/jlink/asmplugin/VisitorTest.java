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
 * @summary Test visitors.
 * @author Andrei Eremeev
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins.asm
 * @build AsmPluginTestBase
 * @run main VisitorTest
 */

import java.io.IOException;
import java.util.Collection;
import java.util.function.Function;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.tools.jlink.internal.plugins.asm.AsmPool;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.ClassReaderVisitor;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.ResourceFile;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.ResourceFileVisitor;
import jdk.tools.jlink.internal.plugins.asm.AsmPools;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class VisitorTest extends AsmPluginTestBase {

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new VisitorTest().test();
    }

    @Override
    public void test() throws Exception {
        TestPlugin[] plugins = new TestPlugin[] {
                new ClassVisitorPlugin("Class-global-pool", AsmPools::getGlobalPool),
                new ClassVisitorPlugin("Class-module-pool", pools -> pools.getModulePool("java.base")),
                new ResourceVisitorPlugin("Resource-global-pool", AsmPools::getGlobalPool),
                new ResourceVisitorPlugin("Resource-module-pool", pools -> pools.getModulePool("java.base"))
        };
        for (TestPlugin p : plugins) {
            System.err.println("Testing: " + p.getName());
            Pool out = p.visit(getPool());
            p.test(getPool(), out);
        }
    }

    private static class CustomClassReaderVisitor implements ClassReaderVisitor {
        private int amount = 0;
        private int changed = 0;

        @Override
        public ClassWriter visit(ClassReader reader) {
            if ((amount++ % 2) == 0) {
                String className = reader.getClassName();
                if (className.endsWith("module-info")) {
                    return null;
                }
                ClassWriter cw = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                reader.accept(new ClassVisitor(Opcodes.ASM5, cw) {
                    @Override
                    public void visit(int i, int i1, String s, String s1, String s2, String[] strings) {
                        super.visit(i, i1, s + "Changed", s1, s2, strings);
                    }
                }, ClassReader.EXPAND_FRAMES);
                ++changed;
                return cw;
            } else {
                return null;
            }
        }

        public int getAmount() {
            return amount;
        }

        public int getNumberOfChanged() {
            return changed;
        }
    }

    private static class CustomResourceFileVisitor implements ResourceFileVisitor {
        private int amount = 0;
        private int changed = 0;

        @Override
        public ResourceFile visit(ResourceFile resourceFile) {
            if ((amount++ % 2) == 0) {
                ++changed;
                return new ResourceFile(resourceFile.getPath() + "Changed", resourceFile.getContent());
            } else {
                return null;
            }
        }

        public int getAmount() {
            return amount;
        }

        public int getNumberOfChanged() {
            return changed;
        }
    }

    public class ClassVisitorPlugin extends TestPlugin {

        private final String name;
        private final Function<AsmPools, AsmPool> getPool;
        private final CustomClassReaderVisitor classReaderVisitor = new CustomClassReaderVisitor();

        public ClassVisitorPlugin(String name, Function<AsmPools, AsmPool> getPool) {
            this.name = name;
            this.getPool = getPool;
        }

        @Override
        public void visit() {
            AsmPool pool = getPool.apply(getPools());
            pool.visitClassReaders(classReaderVisitor);
        }

        @Override
        public void test(Pool in, Pool out) throws Exception {
            Collection<ModuleData> inClasses = getPool.apply(getPools()).getClasses();
            if (inClasses.size() != classReaderVisitor.getAmount()) {
                throw new AssertionError("Testing " + name + ". Number of visited classes. Expected: " +
                        inClasses.size() + ", got: " + classReaderVisitor.getAmount());
            }
            Collection<ModuleData> outClasses = extractClasses(out);
            int changedClasses = 0;
            for (ModuleData r : outClasses) {
                if (r.getPath().endsWith("Changed.class")) {
                    ++changedClasses;
                }
            }
            if (changedClasses != classReaderVisitor.getNumberOfChanged()) {
                throw new AssertionError("Testing " + name + ". Changed classes. Expected: " + changedClasses +
                        ", got: " + classReaderVisitor.getNumberOfChanged());
            }
        }

        @Override
        public String getName() {
            return name;
        }
    }

    public class ResourceVisitorPlugin extends TestPlugin {

        private final String name;
        private final Function<AsmPools, AsmPool> getPool;
        private final CustomResourceFileVisitor resourceFileVisitor = new CustomResourceFileVisitor();

        public ResourceVisitorPlugin(String name, Function<AsmPools, AsmPool> getPool) {
            this.name = name;
            this.getPool = getPool;
        }

        @Override
        public void visit() {
            AsmPool pool = getPool.apply(getPools());
            pool.visitResourceFiles(resourceFileVisitor);
        }

        @Override
        public void test(Pool in, Pool out) throws Exception {
            Collection<ModuleData> inResources = getPool.apply(getPools()).getResourceFiles();
            if (inResources.size() != resourceFileVisitor.getAmount()) {
                throw new AssertionError("Testing " + name + ". Number of visited resources. Expected: " +
                        inResources.size() + ", got: " + resourceFileVisitor.getAmount());
            }
            Collection<ModuleData> outResources = extractResources(out);
            int changedClasses = 0;
            for (ModuleData r : outResources) {
                if (r.getPath().endsWith("Changed")) {
                    ++changedClasses;
                }
            }
            if (changedClasses != resourceFileVisitor.getNumberOfChanged()) {
                throw new AssertionError("Testing " + name + ". Changed classes. Expected: " + changedClasses +
                        ", got: " + resourceFileVisitor.getNumberOfChanged());
            }
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
