/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.NonNullParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.bytecode.BridgeMethodUtils;
import org.graalvm.compiler.core.CompilerThreadFactory;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.VerifyPhase;
import org.graalvm.compiler.phases.VerifyPhase.VerificationError;
import org.graalvm.compiler.phases.contract.VerifyNodeCosts;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.phases.verify.VerifyBailoutUsage;
import org.graalvm.compiler.phases.verify.VerifyCallerSensitiveMethods;
import org.graalvm.compiler.phases.verify.VerifyDebugUsage;
import org.graalvm.compiler.phases.verify.VerifyGetOptionsUsage;
import org.graalvm.compiler.phases.verify.VerifyGraphAddUsage;
import org.graalvm.compiler.phases.verify.VerifyInstanceOfUsage;
import org.graalvm.compiler.phases.verify.VerifyUpdateUsages;
import org.graalvm.compiler.phases.verify.VerifyUsageWithEquals;
import org.graalvm.compiler.phases.verify.VerifyVirtualizableUsage;
import org.graalvm.compiler.runtime.RuntimeProvider;
import jdk.internal.vm.compiler.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

/**
 * Checks that all classes in *graal*.jar and *jvmci*.jar entries on the boot class path comply with
 * global invariants such as using {@link Object#equals(Object)} to compare certain types instead of
 * identity comparisons.
 */
public class CheckGraalInvariants extends GraalCompilerTest {

    private static boolean shouldVerifyEquals(ResolvedJavaMethod m) {
        if (m.getName().equals("identityEquals")) {
            ResolvedJavaType c = m.getDeclaringClass();
            if (c.getName().equals("Ljdk/vm/ci/meta/AbstractValue;") || c.getName().equals("jdk/vm/ci/meta/Value")) {
                return false;
            }
        }

        return true;
    }

    public static String relativeFileName(String absolutePath) {
        int lastFileSeparatorIndex = absolutePath.lastIndexOf(File.separator);
        return absolutePath.substring(lastFileSeparatorIndex >= 0 ? lastFileSeparatorIndex : 0);
    }

    public static class InvariantsTool {

        protected boolean shouldProcess(String classpathEntry) {
            if (classpathEntry.endsWith(".jar")) {
                String name = new File(classpathEntry).getName();
                return name.contains("jvmci") || name.contains("graal") || name.contains("jdk.internal.vm.compiler");
            }
            return false;
        }

        protected String getClassPath() {
            String bootclasspath;
            if (Java8OrEarlier) {
                bootclasspath = System.getProperty("sun.boot.class.path");
            } else {
                bootclasspath = System.getProperty("jdk.module.path") + File.pathSeparatorChar + System.getProperty("jdk.module.upgrade.path");
            }
            return bootclasspath;
        }

        protected boolean shouldLoadClass(String className) {
            return !className.equals("module-info") && !className.startsWith("META-INF.versions.");
        }

        protected void handleClassLoadingException(Throwable t) {
            GraalError.shouldNotReachHere(t);
        }

        protected void handleParsingException(Throwable t) {
            GraalError.shouldNotReachHere(t);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test() {
        runTest(new InvariantsTool());
    }

    @SuppressWarnings("try")
    public static void runTest(InvariantsTool tool) {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        Plugins plugins = new Plugins(new InvocationPlugins());
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        String bootclasspath = tool.getClassPath();
        Assert.assertNotNull("Cannot find boot class path", bootclasspath);

        final List<String> classNames = new ArrayList<>();
        for (String path : bootclasspath.split(File.pathSeparator)) {
            if (tool.shouldProcess(path)) {
                try {
                    final ZipFile zipFile = new ZipFile(new File(path));
                    for (final Enumeration<? extends ZipEntry> entry = zipFile.entries(); entry.hasMoreElements();) {
                        final ZipEntry zipEntry = entry.nextElement();
                        String name = zipEntry.getName();
                        if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                            String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                            if (isInNativeImage(className)) {
                                /*
                                 * Native Image is an external tool and does not need to follow the
                                 * Graal invariants.
                                 */
                                continue;
                            }
                            classNames.add(className);
                        }
                    }
                } catch (IOException ex) {
                    Assert.fail(ex.toString());
                }
            }
        }
        Assert.assertFalse("Could not find graal jars on boot class path: " + bootclasspath, classNames.isEmpty());

        // Allows a subset of methods to be checked through use of a system property
        String property = System.getProperty(CheckGraalInvariants.class.getName() + ".filters");
        String[] filters = property == null ? null : property.split(",");

        OptionValues options = getInitialOptions();
        CompilerThreadFactory factory = new CompilerThreadFactory("CheckInvariantsThread");
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(availableProcessors, availableProcessors, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);

        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        for (Method m : BadUsageWithEquals.class.getDeclaredMethods()) {
            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
            try (DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER)) {
                StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(method).build();
                try (DebugCloseable s = debug.disableIntercept(); DebugContext.Scope ds = debug.scope("CheckingGraph", graph, method)) {
                    graphBuilderSuite.apply(graph, context);
                    // update phi stamps
                    graph.getNodes().filter(PhiNode.class).forEach(PhiNode::inferStamp);
                    checkGraph(context, graph);
                    errors.add(String.format("Expected error while checking %s", m));
                } catch (VerificationError e) {
                    // expected!
                } catch (Throwable e) {
                    errors.add(String.format("Error while checking %s:%n%s", m, printStackTraceToString(e)));
                }
            }
        }
        if (errors.isEmpty()) {
            // Order outer classes before the inner classes
            classNames.sort((String a, String b) -> a.compareTo(b));
            // Initialize classes in single thread to avoid deadlocking issues during initialization
            List<Class<?>> classes = initializeClasses(tool, classNames);
            for (Class<?> c : classes) {
                String className = c.getName();
                executor.execute(() -> {
                    try {
                        checkClass(c, metaAccess);
                    } catch (Throwable e) {
                        errors.add(String.format("Error while checking %s:%n%s", className, printStackTraceToString(e)));
                    }
                });

                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isNative(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                        // ignore
                    } else {
                        String methodName = className + "." + m.getName();
                        if (matches(filters, methodName)) {
                            executor.execute(() -> {
                                try (DebugContext debug = DebugContext.create(options, DebugHandlersFactory.LOADER)) {
                                    ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                                    StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(method).build();
                                    try (DebugCloseable s = debug.disableIntercept(); DebugContext.Scope ds = debug.scope("CheckingGraph", graph, method)) {
                                        checkMethod(method);
                                        graphBuilderSuite.apply(graph, context);
                                        // update phi stamps
                                        graph.getNodes().filter(PhiNode.class).forEach(PhiNode::inferStamp);
                                        checkGraph(context, graph);
                                    } catch (VerificationError e) {
                                        errors.add(e.getMessage());
                                    } catch (LinkageError e) {
                                        // suppress linkages errors resulting from eager resolution
                                    } catch (BailoutException e) {
                                        // Graal bail outs on certain patterns in Java bytecode
                                        // (e.g.,
                                        // unbalanced monitors introduced by jacoco).
                                    } catch (Throwable e) {
                                        try {
                                            tool.handleParsingException(e);
                                        } catch (Throwable t) {
                                            errors.add(String.format("Error while checking %s:%n%s", methodName, printStackTraceToString(e)));
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            }
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }
        }
        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            String nl = String.format("%n");
            for (String e : errors) {
                if (msg.length() != 0) {
                    msg.append(nl);
                }
                msg.append(e);
            }
            Assert.fail(msg.toString());
        }
    }

    private static boolean isInNativeImage(String className) {
        return className.startsWith("org.graalvm.nativeimage");
    }

    private static List<Class<?>> initializeClasses(InvariantsTool tool, List<String> classNames) {
        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            if (!tool.shouldLoadClass(className)) {
                continue;
            }
            try {
                Class<?> c = Class.forName(className, true, CheckGraalInvariants.class.getClassLoader());
                classes.add(c);
            } catch (Throwable t) {
                tool.handleClassLoadingException(t);
            }
        }
        return classes;
    }

    /**
     * @param metaAccess
     */
    private static void checkClass(Class<?> c, MetaAccessProvider metaAccess) {
        if (Node.class.isAssignableFrom(c)) {
            if (c.getAnnotation(NodeInfo.class) == null) {
                throw new AssertionError(String.format("Node subclass %s requires %s annotation", c.getName(), NodeClass.class.getSimpleName()));
            }
            VerifyNodeCosts.verifyNodeClass(c);
        }
    }

    private static void checkMethod(ResolvedJavaMethod method) {
        if (method.getAnnotation(Snippet.class) == null) {
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();
            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a : parameterAnnotations[i]) {
                    Class<? extends Annotation> annotationType = a.annotationType();
                    if (annotationType == ConstantParameter.class || annotationType == VarargsParameter.class || annotationType == NonNullParameter.class) {
                        VerificationError verificationError = new VerificationError("Parameter %d of %s is annotated with %s but the method is not annotated with %s", i, method,
                                        annotationType.getSimpleName(),
                                        Snippet.class.getSimpleName());
                        throw verificationError;
                    }
                }
            }
        }
    }

    /**
     * Checks the invariants for a single graph.
     */
    private static void checkGraph(HighTierContext context, StructuredGraph graph) {
        if (shouldVerifyEquals(graph.method())) {
            // If you add a new type to test here, be sure to add appropriate
            // methods to the BadUsageWithEquals class below
            new VerifyUsageWithEquals(Value.class).apply(graph, context);
            new VerifyUsageWithEquals(Register.class).apply(graph, context);
            new VerifyUsageWithEquals(RegisterCategory.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaType.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaMethod.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaField.class).apply(graph, context);
            new VerifyUsageWithEquals(LocationIdentity.class).apply(graph, context);
            new VerifyUsageWithEquals(LIRKind.class).apply(graph, context);
            new VerifyUsageWithEquals(ArithmeticOpTable.class).apply(graph, context);
            new VerifyUsageWithEquals(ArithmeticOpTable.Op.class).apply(graph, context);
        }
        new VerifyDebugUsage().apply(graph, context);
        new VerifyCallerSensitiveMethods().apply(graph, context);
        new VerifyVirtualizableUsage().apply(graph, context);
        new VerifyUpdateUsages().apply(graph, context);
        new VerifyBailoutUsage().apply(graph, context);
        new VerifyInstanceOfUsage().apply(graph, context);
        new VerifyGraphAddUsage().apply(graph, context);
        new VerifyGetOptionsUsage().apply(graph, context);
        if (graph.method().isBridge()) {
            BridgeMethodUtils.getBridgedMethod(graph.method());
        }
    }

    private static boolean matches(String[] filters, String s) {
        if (filters == null || filters.length == 0) {
            return true;
        }
        for (String filter : filters) {
            if (s.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    private static String printStackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static class BadUsageWithEquals {
        Value aValue;
        Register aRegister;
        RegisterCategory aRegisterCategory;
        JavaType aJavaType;
        JavaField aJavaField;
        JavaMethod aJavaMethod;
        LocationIdentity aLocationIdentity;
        LIRKind aLIRKind;
        ArithmeticOpTable anArithmeticOpTable;
        ArithmeticOpTable.Op anArithmeticOpTableOp;

        static Value aStaticValue;
        static Register aStaticRegister;
        static RegisterCategory aStaticRegisterCategory;
        static JavaType aStaticJavaType;
        static JavaField aStaticJavaField;
        static JavaMethod aStaticJavaMethod;
        static LocationIdentity aStaticLocationIdentity;
        static LIRKind aStaticLIRKind;
        static ArithmeticOpTable aStaticArithmeticOpTable;
        static ArithmeticOpTable.Op aStaticArithmeticOpTableOp;

        boolean test01(Value f) {
            return aValue == f;
        }

        boolean test02(Register f) {
            return aRegister == f;
        }

        boolean test03(RegisterCategory f) {
            return aRegisterCategory == f;
        }

        boolean test04(JavaType f) {
            return aJavaType == f;
        }

        boolean test05(JavaField f) {
            return aJavaField == f;
        }

        boolean test06(JavaMethod f) {
            return aJavaMethod == f;
        }

        boolean test07(LocationIdentity f) {
            return aLocationIdentity == f;
        }

        boolean test08(LIRKind f) {
            return aLIRKind == f;
        }

        boolean test09(ArithmeticOpTable f) {
            return anArithmeticOpTable == f;
        }

        boolean test10(ArithmeticOpTable.Op f) {
            return anArithmeticOpTableOp == f;
        }

        boolean test12(Value f) {
            return aStaticValue == f;
        }

        boolean test13(Register f) {
            return aStaticRegister == f;
        }

        boolean test14(RegisterCategory f) {
            return aStaticRegisterCategory == f;
        }

        boolean test15(JavaType f) {
            return aStaticJavaType == f;
        }

        boolean test16(JavaField f) {
            return aStaticJavaField == f;
        }

        boolean test17(JavaMethod f) {
            return aStaticJavaMethod == f;
        }

        boolean test18(LocationIdentity f) {
            return aStaticLocationIdentity == f;
        }

        boolean test19(LIRKind f) {
            return aStaticLIRKind == f;
        }

        boolean test20(ArithmeticOpTable f) {
            return aStaticArithmeticOpTable == f;
        }

        boolean test21(ArithmeticOpTable.Op f) {
            return aStaticArithmeticOpTableOp == f;
        }
    }
}
