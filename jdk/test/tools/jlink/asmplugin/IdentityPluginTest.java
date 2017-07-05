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
 * @run main IdentityPluginTest
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.tools.jlink.internal.plugins.asm.AsmPool.WritableClassPool;
import jdk.tools.jlink.plugin.Pool;
import jdk.tools.jlink.plugin.Pool.ModuleData;

public class IdentityPluginTest extends AsmPluginTestBase {

    public static void main(String[] args) throws Exception {
        if (!isImageBuild()) {
            System.err.println("Test not run. Not image build.");
            return;
        }
        new IdentityPluginTest().test();
    }

    public void test() throws Exception {
        IdentityPlugin asm = new IdentityPlugin();
        Pool resourcePool = asm.visit(getPool());
        asm.test(getPool(), resourcePool);
    }

    private class IdentityPlugin extends TestPlugin {

        @Override
        public void visit() {
            for (ModuleData res : getPools().getGlobalPool().getClasses()) {
                if (res.getPath().endsWith("module-info.class")) {
                    continue;
                }
                ClassReader reader = getPools().getGlobalPool().getClassReader(res);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                IdentityClassVisitor visitor = new IdentityClassVisitor(writer);
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                getPools().getGlobalPool().getTransformedClasses().addClass(writer);
            }
        }

        @Override
        public void test(Pool inResources, Pool outResources) throws IOException {
            if (outResources.isEmpty()) {
                throw new AssertionError("Empty result");
            }
            if (!isVisitCalled()) {
                throw new AssertionError("Resources not visited");
            }
            WritableClassPool transformedClasses = getPools().getGlobalPool().getTransformedClasses();
            if (transformedClasses.getClasses().size() != getClasses().size()) {
                throw new AssertionError("Number of transformed classes not equal to expected");
            }
            for (String className : getClasses()) {
                if (transformedClasses.getClassReader(className) == null) {
                    throw new AssertionError("Class not transformed " + className);
                }
            }
            for (ModuleData r : outResources.getContent()) {
                if (r.getPath().endsWith(".class") && !r.getPath().endsWith("module-info.class")) {
                    ClassReader reader = new ClassReader(new ByteArrayInputStream(r.getBytes()));
                    ClassWriter w = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
                    reader.accept(w, ClassReader.EXPAND_FRAMES);
                }
            }
        }

        @Override
        public String getName() {
            return "identity-plugin";
        }
    }

    private static class IdentityClassVisitor extends ClassVisitor {
        public IdentityClassVisitor(ClassWriter cv) {
            super(Opcodes.ASM5, cv);
        }
    }
}
