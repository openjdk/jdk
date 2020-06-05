/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.java;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class LambdaUtils {
    private static final Pattern LAMBDA_PATTERN = Pattern.compile("\\$\\$Lambda\\$\\d+[/\\.][^/]+;");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private static GraphBuilderConfiguration buildLambdaParserConfig(ClassInitializationPlugin cip) {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(cip);
        return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
    }

    private LambdaUtils() {
    }

    /**
     * Creates a stable name for a lambda by hashing all the invokes in the lambda. Lambda class
     * names are typically created based on an increasing atomic counter (e.g.
     * {@code Test$$Lambda$23}). A stable name is created by replacing the substring after
     * {@code "$$Lambda$"} with a hash of the method descriptor for each method invoked by the
     * lambda.
     *
     * @param cip plugin to
     *            {@link ClassInitializationPlugin#loadReferencedType(org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext, jdk.vm.ci.meta.ConstantPool, int, int)
     *            load} new types
     * @param providers providers to use when processing the lambda code
     * @param lambdaType the lambda type to analyze
     * @param options options to use when analyzing the lamda code
     * @param debug debug context to nest the analysis into
     * @param ctx context to use for the
     *            {@link DebugContext#scope(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object)}
     * @return stable name for the lambda class
     */
    @SuppressWarnings("try")
    public static String findStableLambdaName(ClassInitializationPlugin cip, Providers providers, ResolvedJavaType lambdaType, OptionValues options, DebugContext debug, Object ctx)
                    throws RuntimeException {
        ResolvedJavaMethod[] lambdaProxyMethods = Arrays.stream(lambdaType.getDeclaredMethods()).filter(m -> !m.isBridge() && m.isPublic()).toArray(ResolvedJavaMethod[]::new);
        assert lambdaProxyMethods.length == 1 : "There must be only one method calling the target.";
        StructuredGraph graph = new StructuredGraph.Builder(options, debug).method(lambdaProxyMethods[0]).build();
        try (DebugContext.Scope ignored = debug.scope("Lambda target method analysis", graph, lambdaType, ctx)) {
            GraphBuilderPhase lambdaParserPhase = new GraphBuilderPhase(buildLambdaParserConfig(cip));
            HighTierContext context = new HighTierContext(providers, null, OptimisticOptimizations.NONE);
            lambdaParserPhase.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        List<ResolvedJavaMethod> invokedMethods = StreamSupport.stream(graph.getInvokes().spliterator(), false).map((inv) -> inv.getTargetMethod()).collect(Collectors.toList());
        if (invokedMethods.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Lambda without a target invoke: ").append(lambdaType.toClassName());
            for (ResolvedJavaMethod m : lambdaType.getDeclaredMethods()) {
                sb.append("\n  Method: ").append(m);
            }
            throw new JVMCIError(sb.toString());
        }
        String lambdaTargetName = createStableLambdaName(lambdaType, invokedMethods);
        return lambdaTargetName;
    }

    public static boolean isLambdaType(ResolvedJavaType type) {
        String typeName = type.getName();
        return type.isFinalFlagSet() && typeName.contains("/") && typeName.contains("$$Lambda$") && lambdaMatcher(type.getName()).find();
    }

    private static String createStableLambdaName(ResolvedJavaType lambdaType, List<ResolvedJavaMethod> targetMethods) {
        final String lambdaName = lambdaType.getName();
        assert lambdaMatcher(lambdaName).find() : "Stable name should be created only for lambda types: " + lambdaName;

        Matcher m = lambdaMatcher(lambdaName);
        StringBuilder sb = new StringBuilder();
        targetMethods.forEach((targetMethod) -> {
            sb.append(targetMethod.format("%H.%n(%P)%R"));
        });
        return m.replaceFirst(Matcher.quoteReplacement("$$Lambda$" + digest(sb.toString()) + ";"));
    }

    private static Matcher lambdaMatcher(String value) {
        return LAMBDA_PATTERN.matcher(value);
    }

    public static String toHex(byte[] data) {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data) {
            r.append(HEX[(b >> 4) & 0xf]);
            r.append(HEX[b & 0xf]);
        }
        return r.toString();
    }

    public static String digest(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(value.getBytes("UTF-8"));
            return toHex(md.digest());
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
            throw new JVMCIError(ex);
        }
    }
}
