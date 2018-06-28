/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.java.RegisterFinalizerNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Assumptions.Assumption;
import jdk.vm.ci.meta.Assumptions.LeafType;
import jdk.vm.ci.meta.Assumptions.NoFinalizableSubclass;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FinalizableSubclassTest extends GraalCompilerTest {

    /**
     * used as template to generate class files at runtime.
     */
    public static class NoFinalizerEverAAAA {
    }

    public static class NoFinalizerYetAAAA {
    }

    public static final class WithFinalizerAAAA extends NoFinalizerYetAAAA {

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    private StructuredGraph parseAndProcess(Class<?> cl, AllowAssumptions allowAssumptions) {
        Constructor<?>[] constructors = cl.getConstructors();
        Assert.assertTrue(constructors.length == 1);
        final ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaMethod(constructors[0]);
        OptionValues options = getInitialOptions();
        StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(options, null, javaMethod), allowAssumptions).method(javaMethod).build();

        GraphBuilderConfiguration conf = GraphBuilderConfiguration.getSnippetDefault(getDefaultGraphBuilderPlugins());
        new GraphBuilderPhase.Instance(getMetaAccess(), getProviders().getStampProvider(), getProviders().getConstantReflection(), getProviders().getConstantFieldProvider(), conf,
                        OptimisticOptimizations.ALL, null).apply(graph);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, context);
        return graph;
    }

    private void checkForRegisterFinalizeNode(Class<?> cl, boolean shouldContainFinalizer, AllowAssumptions allowAssumptions) {
        StructuredGraph graph = parseAndProcess(cl, allowAssumptions);
        Assert.assertTrue(graph.getNodes().filter(RegisterFinalizerNode.class).count() == (shouldContainFinalizer ? 1 : 0));
        int noFinalizerAssumption = 0;
        Assumptions assumptions = graph.getAssumptions();
        if (assumptions != null) {
            for (Assumption a : assumptions) {
                if (a instanceof NoFinalizableSubclass) {
                    noFinalizerAssumption++;
                } else if (a instanceof LeafType) {
                    // Need to also allow leaf type assumption instead of no finalizable subclass
                    // assumption.
                    noFinalizerAssumption++;
                }
            }
        }
        Assert.assertTrue(noFinalizerAssumption == (shouldContainFinalizer ? 0 : 1));
    }

    /**
     * Use a custom class loader to generate classes, to make sure the given classes are loaded in
     * correct order.
     */
    @Test
    public void test1() throws ClassNotFoundException {
        DebugContext debug = getDebugContext();
        for (int i = 0; i < 2; i++) {
            ClassTemplateLoader loader = new ClassTemplateLoader(debug);
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerEverAAAA"), true, AllowAssumptions.NO);
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerEverAAAA"), false, AllowAssumptions.YES);

            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerYetAAAA"), false, AllowAssumptions.YES);

            checkForRegisterFinalizeNode(loader.findClass("WithFinalizerAAAA"), true, AllowAssumptions.YES);
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerYetAAAA"), true, AllowAssumptions.YES);
        }
    }

    private static class ClassTemplateLoader extends ClassLoader {

        private static int loaderInstance = 0;

        private final String replaceTo;
        private HashMap<String, Class<?>> cache = new HashMap<>();

        private final DebugContext debug;

        ClassTemplateLoader(DebugContext debug) {
            loaderInstance++;
            this.debug = debug;
            replaceTo = String.format("%04d", loaderInstance);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            String nameReplaced = name.replaceAll("AAAA", replaceTo);
            if (cache.containsKey(nameReplaced)) {
                return cache.get(nameReplaced);
            }

            // copy classfile to byte array
            byte[] classData = null;
            try {
                InputStream is = FinalizableSubclassTest.class.getResourceAsStream("FinalizableSubclassTest$" + name + ".class");
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

            dumpStringsInByteArray(debug, classData);

            // replace all occurrences of "AAAA" in classfile
            int index = -1;
            while ((index = indexOfAAAA(classData, index + 1)) != -1) {
                replaceAAAA(classData, index, replaceTo);
            }
            dumpStringsInByteArray(debug, classData);

            Class<?> c = defineClass(null, classData, 0, classData.length);
            cache.put(nameReplaced, c);
            return c;
        }

        private static int indexOfAAAA(byte[] b, int index) {
            for (int i = index; i < b.length; i++) {
                boolean match = true;
                for (int j = i; j < i + 4; j++) {
                    if (b[j] != (byte) 'A') {
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

        private static void replaceAAAA(byte[] b, int index, String replacer) {
            assert replacer.length() == 4;
            for (int i = index; i < index + 4; i++) {
                b[i] = (byte) replacer.charAt(i - index);
            }
        }

        private static void dumpStringsInByteArray(DebugContext debug, byte[] b) {
            boolean wasChar = true;
            StringBuilder sb = new StringBuilder();
            for (Byte x : b) {
                // check for [a-zA-Z0-9]
                if ((x >= 0x41 && x <= 0x7a) || (x >= 0x30 && x <= 0x39)) {
                    if (!wasChar) {
                        debug.log(sb + "");
                        sb.setLength(0);
                    }
                    sb.append(String.format("%c", x));
                    wasChar = true;
                } else {
                    wasChar = false;
                }
            }
            debug.log(sb + "");
        }
    }
}
