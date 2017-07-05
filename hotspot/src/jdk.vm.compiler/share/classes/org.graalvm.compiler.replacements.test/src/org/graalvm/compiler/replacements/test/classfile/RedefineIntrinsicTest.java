/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test.classfile;

import static org.junit.Assume.assumeTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that intrinsics (and snippets) are isolated from bytecode instrumentation.
 */
public class RedefineIntrinsicTest extends GraalCompilerTest {

    public static class Original {

        // Intrinsified by Intrinsic.getValue
        public static String getValue() {
            return "original";
        }
    }

    @ClassSubstitution(Original.class)
    private static class Intrinsic {

        @MethodSubstitution
        public static String getValue() {
            return "intrinsic";
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        BytecodeProvider replacementBytecodeProvider = getReplacements().getReplacementBytecodeProvider();
        Registration r = new Registration(invocationPlugins, Original.class, replacementBytecodeProvider);
        r.registerMethodSubstitution(Intrinsic.class, "getValue");
        return super.editGraphBuilderConfiguration(conf);
    }

    public static String callOriginalGetValue() {
        // This call will be intrinsified when compiled by Graal
        return Original.getValue();
    }

    public static String callIntrinsicGetValue() {
        // This call will *not* be intrinsified when compiled by Graal
        return Intrinsic.getValue();
    }

    @Test
    public void test() throws Throwable {
        Object receiver = null;
        Object[] args = {};

        // Prior to redefinition, both Original and Intrinsic
        // should behave as per their Java source code
        Assert.assertEquals("original", Original.getValue());
        Assert.assertEquals("intrinsic", Intrinsic.getValue());

        ResolvedJavaMethod callOriginalGetValue = getResolvedJavaMethod("callOriginalGetValue");
        ResolvedJavaMethod callIntrinsicGetValue = getResolvedJavaMethod("callIntrinsicGetValue");

        // Expect intrinsification to change "original" to "intrinsic"
        testAgainstExpected(callOriginalGetValue, new Result("intrinsic", null), receiver, args);

        // Expect no intrinsification
        testAgainstExpected(callIntrinsicGetValue, new Result("intrinsic", null), receiver, args);

        // Apply redefinition of intrinsic bytecode
        redefineIntrinsic();

        // Expect redefinition to have no effect
        Assert.assertEquals("original", Original.getValue());

        // Expect redefinition to change "intrinsic" to "redefined"
        Assert.assertEquals("redefined", Intrinsic.getValue());

        // Expect redefinition to have no effect on intrinsification (i.e.,
        // "original" is still changed to "intrinsic", not "redefined"
        testAgainstExpected(callOriginalGetValue, new Result("intrinsic", null), receiver, args);
    }

    /**
     * Adds the class file bytes for a given class to a JAR stream.
     */
    static void add(JarOutputStream jar, Class<?> c) throws IOException {
        String name = c.getName();
        String classAsPath = name.replace('.', '/') + ".class";
        jar.putNextEntry(new JarEntry(classAsPath));

        InputStream stream = c.getClassLoader().getResourceAsStream(classAsPath);

        int nRead;
        byte[] buf = new byte[1024];
        while ((nRead = stream.read(buf, 0, buf.length)) != -1) {
            jar.write(buf, 0, nRead);
        }

        jar.closeEntry();
    }

    static void redefineIntrinsic() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.putValue("Agent-Class", RedefinerAgent.class.getName());
        mainAttrs.putValue("Can-Redefine-Classes", "true");
        mainAttrs.putValue("Can-Retransform-Classes", "true");

        Path jar = Files.createTempFile("myagent", ".jar");
        try {
            JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest);
            add(jarStream, RedefinerAgent.class);
            add(jarStream, Redefiner.class);
            jarStream.close();

            loadAgent(jar);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    public static void loadAgent(Path agent) throws Exception {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        int p = vmName.indexOf('@');
        assumeTrue("VM name not in <pid>@<host> format: " + vmName, p != -1);
        String pid = vmName.substring(0, p);
        ClassLoader cl = ToolProvider.getSystemToolClassLoader();
        Class<?> c = Class.forName("com.sun.tools.attach.VirtualMachine", true, cl);
        Method attach = c.getDeclaredMethod("attach", String.class);
        Method loadAgent = c.getDeclaredMethod("loadAgent", String.class, String.class);
        Method detach = c.getDeclaredMethod("detach");
        Object vm = attach.invoke(null, pid);
        loadAgent.invoke(vm, agent.toString(), "");
        detach.invoke(vm);
    }

    public static class RedefinerAgent {

        public static void agentmain(@SuppressWarnings("unused") String args, Instrumentation inst) throws Exception {
            if (inst.isRedefineClassesSupported() && inst.isRetransformClassesSupported()) {
                inst.addTransformer(new Redefiner(), true);
                Class<?>[] allClasses = inst.getAllLoadedClasses();
                for (int i = 0; i < allClasses.length; i++) {
                    Class<?> c = allClasses[i];
                    if (c == Intrinsic.class) {
                        inst.retransformClasses(new Class<?>[]{c});
                    }
                }
            }
        }
    }

    /**
     * This transformer replaces the first instance of the constant "intrinsic" in the class file
     * for {@link Intrinsic} with "redefined".
     */
    static class Redefiner implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader cl, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (Intrinsic.class.equals(classBeingRedefined)) {
                String cf = new String(classfileBuffer);
                int i = cf.indexOf("intrinsic");
                Assert.assertTrue("cannot find \"intrinsic\" constant in " + Intrinsic.class.getSimpleName() + "'s class file", i > 0);
                System.arraycopy("redefined".getBytes(), 0, classfileBuffer, i, "redefined".length());
            }
            return classfileBuffer;
        }
    }
}
