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
 * @summary Test resource transformation.
 * @author Andrei Eremeev
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.internal.plugins.asm
 *          jdk.jdeps/com.sun.tools.classfile
 * @build AsmPluginTestBase
 * @run main AddForgetResourcesTest
*/

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.Method;
import java.io.UncheckedIOException;
import java.util.Set;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.tools.jlink.internal.plugins.asm.AsmGlobalPool;
import jdk.tools.jlink.internal.plugins.asm.AsmModulePool;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.ResourceFile;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.WritableClassPool;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.WritableResourcePool;
import jdk.tools.jlink.internal.plugins.asm.AsmPools;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class AddForgetResourcesTest extends AsmPluginTestBase {

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new AddForgetResourcesTest().test();
    }

    @Override
    public void test() throws Exception {
        TestPlugin[] plugins = new TestPlugin[] {
                new AddClassesPlugin(),
                new AddResourcesPlugin(),
                new ReplaceClassesPlugin(),
                new ReplaceResourcesPlugin(),
                new ForgetClassesPlugin(),
                new ForgetResourcesPlugin(),
                new AddForgetClassesPlugin(),
                new AddForgetResourcesPlugin(),
                new ComboPlugin()
        };
        for (TestPlugin p : plugins) {
            Pool out = p.visit(getPool());
            p.test(getPool(), out);
        }
    }

    private static final String SUFFIX = "HELLOWORLD";

    private static class RenameClassVisitor extends ClassVisitor {

        public RenameClassVisitor(ClassWriter cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name + SUFFIX, signature, superName, interfaces);
        }
    }

    private static class AddMethodClassVisitor extends ClassVisitor {

        public AddMethodClassVisitor(ClassWriter cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.visitMethod(0, SUFFIX, "()V", null, null);
            super.visit(version, access, name, signature, superName, interfaces);
        }
    }

    private class AddClassesPlugin extends TestPlugin {

        private int expected = 0;

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            WritableClassPool transformedClasses = globalPool.getTransformedClasses();
            expected = globalPool.getClasses().size();
            for (ModuleData res : globalPool.getClasses()) {
                ClassReader reader = globalPool.getClassReader(res);
                String className = reader.getClassName();
                if (!className.endsWith("module-info")) {
                    ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                    reader.accept(new RenameClassVisitor(writer), ClassReader.EXPAND_FRAMES);
                    transformedClasses.addClass(writer);
                    ++expected;
                }
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) {
            Collection<ModuleData> inClasses = extractClasses(inResources);
            Collection<ModuleData> outClasses = extractClasses(outResources);
            if (expected != outClasses.size()) {
                throw new AssertionError("Classes were not added. Expected: " + expected
                        + ", got: " + outClasses.size());
            }
            for (ModuleData in : inClasses) {
                String path = in.getPath();
                if (!outClasses.contains(in)) {
                    throw new AssertionError("Class not found: " + path);
                }
                if (path.endsWith("module-info.class")) {
                    continue;
                }
                String modifiedPath = path.replace(".class", SUFFIX + ".class");
                if (!outClasses.contains(Pool.newResource(modifiedPath, new byte[0]))) {
                    throw new AssertionError("Class not found: " + modifiedPath);
                }
            }
        }
    }

    private class AddResourcesPlugin extends TestPlugin {

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            for (ModuleData res : globalPool.getResourceFiles()) {
                String path = res.getPath();
                String moduleName = getModule(path);
                AsmModulePool modulePool = pools.getModulePool(moduleName);
                WritableResourcePool resourcePool = modulePool.getTransformedResourceFiles();
                resourcePool.addResourceFile(new ResourceFile(removeModule(res.getPath()) + SUFFIX,
                        res.getBytes()));
            }
        }

        @Override
        public void test(Pool in, Pool out) throws Exception {
            Collection<ModuleData> inResources = extractResources(in);
            Collection<ModuleData> outResources = extractResources(out);
            if (2 * inResources.size() != outResources.size()) {
                throw new AssertionError("Classes were not added. Expected: " + (2 * inResources.size())
                        + ", got: " + outResources.size());
            }
            for (ModuleData r : inResources) {
                String path = r.getPath();
                if (!outResources.contains(r)) {
                    throw new AssertionError("Class not found: " + path);
                }
                String modifiedPath = path + SUFFIX;
                if (!outResources.contains(Pool.newResource(modifiedPath, new byte[0]))) {
                    throw new AssertionError("Class not found: " + modifiedPath);
                }
            }
        }
    }

    private class ReplaceClassesPlugin extends TestPlugin {

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            WritableClassPool transformedClasses = globalPool.getTransformedClasses();
            for (ModuleData res : globalPool.getClasses()) {
                ClassReader reader = globalPool.getClassReader(res);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                reader.accept(new AddMethodClassVisitor(writer), ClassReader.EXPAND_FRAMES);
                transformedClasses.addClass(writer);
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            Collection<ModuleData> inClasses = extractClasses(inResources);
            Collection<ModuleData> outClasses = extractClasses(outResources);
            if (inClasses.size() != outClasses.size()) {
                throw new AssertionError("Number of classes. Expected: " + (inClasses.size())
                        + ", got: " + outClasses.size());
            }
            for (ModuleData out : outClasses) {
                String path = out.getPath();
                if (!inClasses.contains(out)) {
                    throw new AssertionError("Class not found: " + path);
                }
                ClassFile cf = ClassFile.read(new ByteArrayInputStream(out.getBytes()));
                if (path.endsWith("module-info.class")) {
                    continue;
                }
                boolean failed = true;
                for (Method m : cf.methods) {
                    if (m.getName(cf.constant_pool).equals(SUFFIX)) {
                        failed = false;
                    }
                }
                if (failed) {
                    throw new AssertionError("Not found method with name " + SUFFIX + " in class " + path);
                }
            }
        }
    }

    private class ReplaceResourcesPlugin extends TestPlugin {

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            for (ModuleData res : globalPool.getResourceFiles()) {
                String path = res.getPath();
                AsmModulePool modulePool = pools.getModulePool(getModule(path));
                modulePool.getTransformedResourceFiles().addResourceFile(new ResourceFile(removeModule(path),
                        "HUI".getBytes()));
            }
        }

        @Override
        public void test(Pool in, Pool out) throws Exception {
            Collection<ModuleData> inResources = extractResources(in);
            Collection<ModuleData> outResources = extractResources(out);
            if (inResources.size() != outResources.size()) {
                throw new AssertionError("Number of resources. Expected: " + inResources.size()
                        + ", got: " + outResources.size());
            }
            for (ModuleData r : outResources) {
                String path = r.getPath();
                if (!inResources.contains(r)) {
                    throw new AssertionError("Resource not found: " + path);
                }
                String content = new String(r.getBytes());
                if (!"HUI".equals(content)) {
                    throw new AssertionError("Content expected: 'HUI', got: " + content);
                }
            }
        }
    }

    private class ForgetClassesPlugin extends TestPlugin {

        private int expected = 0;

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            WritableClassPool transformedClasses = globalPool.getTransformedClasses();
            int i = 0;
            for (ModuleData res : globalPool.getClasses()) {
                String path = removeModule(res.getPath());
                String className = path.replace(".class", "");
                if ((i & 1) == 0 && !className.endsWith("module-info")) {
                    transformedClasses.forgetClass(className);
                } else {
                    ++expected;
                }
                i ^= 1;
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            Collection<ModuleData> outClasses = extractClasses(outResources);
            if (expected != outClasses.size()) {
                throw new AssertionError("Number of classes. Expected: " + expected +
                        ", got: " + outClasses.size());
            }
        }
    }

    private class ForgetResourcesPlugin extends TestPlugin {

        private int expectedAmount = 0;

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            int i = 0;
            for (ModuleData res : globalPool.getResourceFiles()) {
                String path = res.getPath();
                if (!path.contains("META-INF/services")) {
                    if ((i & 1) == 0) {
                        AsmModulePool modulePool = pools.getModulePool(getModule(path));
                        modulePool.getTransformedResourceFiles().forgetResourceFile(removeModule(res.getPath()));
                    } else {
                        ++expectedAmount;
                    }
                    i ^= 1;
                } else {
                    ++expectedAmount;
                }
            }
        }

        @Override
        public void test(Pool in, Pool out) throws Exception {
            Collection<ModuleData> outResources = extractResources(out);
            if (expectedAmount != outResources.size()) {
                throw new AssertionError("Number of classes. Expected: " + expectedAmount
                        + ", got: " + outResources.size());
            }
        }
    }

    private class AddForgetClassesPlugin extends TestPlugin {

        private int expected = 0;

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            WritableClassPool transformedClasses = globalPool.getTransformedClasses();
            int i = 0;
            for (ModuleData res : globalPool.getClasses()) {
                ClassReader reader = globalPool.getClassReader(res);
                String className = reader.getClassName();
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                if (!className.endsWith("module-info")) {
                    reader.accept(new RenameClassVisitor(writer), ClassReader.EXPAND_FRAMES);
                    transformedClasses.addClass(writer);
                    ++expected;
                }

                if ((i & 1) == 0 && !className.endsWith("module-info")) {
                    transformedClasses.forgetClass(className);
                } else {
                    ++expected;
                }
                i ^= 1;
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            Collection<ModuleData> outClasses = extractClasses(outResources);
            if (expected != outClasses.size()) {
                throw new AssertionError("Number of classes. Expected: " + expected
                        + ", got: " + outClasses.size());
            }
        }
    }

    private class AddForgetResourcesPlugin extends TestPlugin {

        private int expectedAmount = 0;

        @Override
        public void visit() {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            int i = 0;
            for (ModuleData res : globalPool.getResourceFiles()) {
                String path = res.getPath();
                String moduleName = getModule(path);
                if (!path.contains("META-INF")) {
                    AsmModulePool modulePool = pools.getModulePool(moduleName);
                    WritableResourcePool transformedResourceFiles = modulePool.getTransformedResourceFiles();
                    String newPath = removeModule(path) + SUFFIX;
                    transformedResourceFiles.addResourceFile(new ResourceFile(newPath, res.getBytes()));
                    if ((i & 1) == 0) {
                        transformedResourceFiles.forgetResourceFile(newPath);
                    } else {
                        ++expectedAmount;
                    }
                    i ^= 1;
                }
                ++expectedAmount;
            }
        }

        @Override
        public void test(Pool inResources, Pool out) throws Exception {
            Collection<ModuleData> outResources = extractResources(out);
            if (expectedAmount != outResources.size()) {
                throw new AssertionError("Number of classes. Expected: " + expectedAmount
                        + ", got: " + outResources.size());
            }
        }
    }

    private class ComboPlugin extends TestPlugin {

        private class RenameClassVisitor extends ClassVisitor {

            public RenameClassVisitor(ClassWriter cv) {
                super(Opcodes.ASM5, cv);
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name + SUFFIX, signature, superName, interfaces);
            }
        }

        @Override
        public void visit() {
            try {
                renameClasses();
                renameResources();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws Exception {
            if (!isVisitCalled()) {
                throw new AssertionError("Resources not visited");
            }
            AsmGlobalPool globalPool = getPools().getGlobalPool();
            if (globalPool.getTransformedClasses().getClasses().size() != getClasses().size()) {
                throw new AssertionError("Number of transformed classes not equal to expected");
            }
            // Check that only renamed classes and resource files are in the result.
            for (ModuleData r : outResources.getContent()) {
                String resourceName = r.getPath();
                if (resourceName.endsWith(".class") && !resourceName.endsWith("module-info.class")) {
                    if (!resourceName.endsWith(SUFFIX + ".class")) {
                        throw new AssertionError("Class not renamed " + resourceName);
                    }
                } else if (resourceName.contains("META-INF/services/") && MODULES.containsKey(r.getModule())) {
                    String newClassName = new String(r.getBytes());
                    if(!newClassName.endsWith(SUFFIX)) {
                        throw new AssertionError("Resource file not renamed " + resourceName);
                    }
                }
            }
        }

        private void renameResources() throws IOException {
            AsmPools pools = getPools();
            // Rename the resource Files
            for (Map.Entry<String, List<String>> mod : MODULES.entrySet()) {
                String moduleName = mod.getKey();
                AsmModulePool modulePool = pools.getModulePool(moduleName);
                for (ModuleData res : modulePool.getResourceFiles()) {
                    ResourceFile resFile = modulePool.getResourceFile(res);
                    if (resFile.getPath().startsWith("META-INF/services/")) {
                        String newContent = new String(resFile.getContent()) + SUFFIX;
                        ResourceFile newResourceFile = new ResourceFile(resFile.getPath(),
                                newContent.getBytes());
                        modulePool.getTransformedResourceFiles().addResourceFile(newResourceFile);
                    }
                }
            }
        }

        private void renameClasses() throws IOException {
            AsmPools pools = getPools();
            AsmGlobalPool globalPool = pools.getGlobalPool();
            WritableClassPool transformedClasses = globalPool.getTransformedClasses();
            for (ModuleData res : globalPool.getClasses()) {
                if (res.getPath().endsWith("module-info.class")) {
                    continue;
                }
                ClassReader reader = globalPool.getClassReader(res);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                RenameClassVisitor visitor = new RenameClassVisitor(writer);
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);

                transformedClasses.forgetClass(reader.getClassName());
                transformedClasses.addClass(writer);
            }
        }
    }
}
