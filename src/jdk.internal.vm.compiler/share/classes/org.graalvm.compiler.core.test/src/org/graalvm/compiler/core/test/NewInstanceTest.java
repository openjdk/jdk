/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.DeoptimizeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class NewInstanceTest extends GraalCompilerTest {
    public static class Template {
        public Object o = new CCCC();
    }

    /**
     * 1. Concrete class, should produce normal {@link NewInstanceNode}
     */
    public static class CCCC {

    }

    /**
     * 2. The test will replace references to {@link CCCC} in the {@link Template} to {@link AAAA}
     */
    public abstract static class AAAA {

    }

    /**
     * 3. The test will replace references to {@link CCCC} in the {@link Template} to {@link IIII}
     */
    public interface IIII {

    }

    private StructuredGraph parseAndProcess(Class<?> cl) {
        Constructor<?>[] constructors = cl.getConstructors();
        Assert.assertTrue(constructors.length == 1);
        final ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(constructors[0]);
        OptionValues options = getInitialOptions();
        StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(options, null, javaMethod), AllowAssumptions.YES).method(javaMethod).build();

        GraphBuilderConfiguration conf = GraphBuilderConfiguration.getSnippetDefault(getDefaultGraphBuilderPlugins()).withUnresolvedIsError(false);
        new GraphBuilderPhase.Instance(getProviders(), conf, OptimisticOptimizations.ALL, null).apply(graph);
        return graph;
    }

    private void checkGraph(Class<?> cl, int newInstanceNodeCount, int deoptimizeNodeCount) {
        StructuredGraph graph = parseAndProcess(cl);
        Assert.assertEquals(newInstanceNodeCount, graph.getNodes().filter(NewInstanceNode.class).count());
        Assert.assertEquals(deoptimizeNodeCount, graph.getNodes().filter(DeoptimizeNode.class).count());
    }

    /**
     * Use a custom class loader to generate classes, substitute class names at the allocation site.
     */
    @Test
    public void test1() throws ClassNotFoundException {
        checkGraph(Template.class, 1, 0);
        ClassTemplateLoader loader1 = new ClassTemplateLoader("CCCC", "AAAA");
        checkGraph(loader1.findClass("Template"), 0, 1);
        ClassTemplateLoader loader2 = new ClassTemplateLoader("CCCC", "IIII");
        checkGraph(loader2.findClass("Template"), 0, 1);
    }

    private static class ClassTemplateLoader extends ClassLoader {
        private final String find;
        private final String replace;

        ClassTemplateLoader(String find, String replace) {
            assert find.length() == replace.length();
            this.find = find;
            this.replace = replace;
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            // copy classfile to byte array
            byte[] classData = null;
            try {
                InputStream is = NewInstanceTest.class.getResourceAsStream("NewInstanceTest$" + name + ".class");
                assert is != null;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                byte[] buf = new byte[1024];
                int size;
                while ((size = is.read(buf, 0, buf.length)) != -1) {
                    baos.write(buf, 0, size);
                }
                baos.flush();
                classData = baos.toByteArray();
            } catch (IOException e) {
                Assert.fail("can't access class: " + name);
            }

            // replace all occurrences of the template in classfile
            int index = -1;
            while ((index = indexOfTemplate(classData, index + 1, find)) != -1) {
                replaceTemplate(classData, index, replace);
            }

            Class<?> c = defineClass(null, classData, 0, classData.length);
            return c;
        }

        private static int indexOfTemplate(byte[] b, int index, String find) {
            for (int i = index; i < b.length; i++) {
                boolean match = true;
                for (int j = i; j < i + find.length(); j++) {
                    if (b[j] != (byte) find.charAt(j - i)) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return i;
                }
            }
            return -1;
        }

        private static void replaceTemplate(byte[] b, int index, String replace) {
            for (int i = index; i < index + replace.length(); i++) {
                b[i] = (byte) replace.charAt(i - index);
            }
        }
    }
}
