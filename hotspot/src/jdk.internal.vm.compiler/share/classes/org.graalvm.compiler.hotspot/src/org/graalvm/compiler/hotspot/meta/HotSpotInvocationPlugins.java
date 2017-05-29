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
package org.graalvm.compiler.hotspot.meta;

import static org.graalvm.compiler.serviceprovider.JDK9Method.Java8OrEarlier;

import java.lang.reflect.Type;
import java.util.Set;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.phases.AheadOfTimeVerificationPhase;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.nodes.MacroNode;
import org.graalvm.compiler.serviceprovider.JDK9Method;
import org.graalvm.util.EconomicSet;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Extension of {@link InvocationPlugins} that disables plugins based on runtime configuration.
 */
final class HotSpotInvocationPlugins extends InvocationPlugins {
    private final GraalHotSpotVMConfig config;
    private final EconomicSet<Object> trustedModules;
    private final ClassLoader extLoader;

    HotSpotInvocationPlugins(GraalHotSpotVMConfig config, CompilerConfiguration compilerConfiguration) {
        this.config = config;
        if (Java8OrEarlier) {
            extLoader = getExtLoader();
            trustedModules = null;
        } else {
            extLoader = null;
            trustedModules = initTrustedModules(compilerConfiguration);
        }
    }

    @Override
    public void register(InvocationPlugin plugin, Type declaringClass, String name, Type... argumentTypes) {
        if (!config.usePopCountInstruction) {
            if (name.equals("bitCount")) {
                assert declaringClass.equals(Integer.class) || declaringClass.equals(Long.class);
                return;
            }
        }
        super.register(plugin, declaringClass, name, argumentTypes);
    }

    @Override
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        for (Node node : newNodes) {
            if (node instanceof MacroNode) {
                // MacroNode based plugins can only be used for inlining since they
                // require a valid bci should they need to replace themselves with
                // an InvokeNode during lowering.
                assert plugin.inlineOnly() : String.format("plugin that creates a %s (%s) must return true for inlineOnly(): %s", MacroNode.class.getSimpleName(), node, plugin);
            }
        }
        if (GraalOptions.ImmutableCode.getValue(b.getOptions())) {
            for (Node node : newNodes) {
                if (node.hasUsages() && node instanceof ConstantNode) {
                    ConstantNode c = (ConstantNode) node;
                    if (c.getStackKind() == JavaKind.Object && AheadOfTimeVerificationPhase.isIllegalObjectConstant(c)) {
                        if (isClass(c)) {
                            // This will be handled later by LoadJavaMirrorWithKlassPhase
                        } else {
                            // Tolerate uses in unused FrameStates
                            if (node.usages().filter((n) -> !(n instanceof FrameState) || n.hasUsages()).isNotEmpty()) {
                                throw new AssertionError("illegal constant node in AOT: " + node);
                            }
                        }
                    }
                }
            }
        }
        super.checkNewNodes(b, plugin, newNodes);
    }

    private static boolean isClass(ConstantNode node) {
        ResolvedJavaType type = StampTool.typeOrNull(node);
        return type != null && "Ljava/lang/Class;".equals(type.getName());
    }

    /**
     * {@inheritDoc}
     *
     * On JDK 8, only classes loaded by the boot, JVMCI or extension class loaders are trusted.
     *
     * On JDK 9 and later, only classes in the {@link CompilerConfiguration} defining module or any
     * of its module dependencies are trusted.
     */
    @Override
    protected boolean canBeIntrinsified(ResolvedJavaType declaringClass) {
        if (declaringClass instanceof HotSpotResolvedJavaType) {
            Class<?> javaClass = ((HotSpotResolvedJavaType) declaringClass).mirror();
            if (Java8OrEarlier) {
                ClassLoader cl = javaClass.getClassLoader();
                return cl == null || cl == getClass().getClassLoader() || cl == extLoader;
            } else {
                Object module = JDK9Method.getModule.invoke(javaClass);
                return trustedModules.contains(module);
            }
        }
        return false;
    }

    private static ClassLoader getExtLoader() {
        try {
            Object launcher = Class.forName("sun.misc.Launcher").getMethod("getLauncher").invoke(null);
            ClassLoader appLoader = (ClassLoader) launcher.getClass().getMethod("getClassLoader").invoke(launcher);
            ClassLoader extLoader = appLoader.getParent();
            assert extLoader.getClass().getName().equals("sun.misc.Launcher$ExtClassLoader") : extLoader;
            return extLoader;
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }

    private static EconomicSet<Object> initTrustedModules(CompilerConfiguration compilerConfiguration) throws GraalError {
        try {
            EconomicSet<Object> res = EconomicSet.create();
            Object compilerConfigurationModule = JDK9Method.getModule.invoke(compilerConfiguration.getClass());
            res.add(compilerConfigurationModule);
            Class<?> moduleClass = compilerConfigurationModule.getClass();
            Object layer = new JDK9Method(moduleClass, "getLayer").invoke(compilerConfigurationModule);
            Class<? extends Object> layerClass = layer.getClass();
            JDK9Method getName = new JDK9Method(moduleClass, "getName");
            Set<Object> modules = new JDK9Method(layerClass, "modules").invoke(layer);
            Object descriptor = new JDK9Method(moduleClass, "getDescriptor").invoke(compilerConfigurationModule);
            Class<?> moduleDescriptorClass = descriptor.getClass();
            Set<Object> requires = new JDK9Method(moduleDescriptorClass, "requires").invoke(descriptor);
            JDK9Method requireNameGetter = null;
            for (Object require : requires) {
                if (requireNameGetter == null) {
                    requireNameGetter = new JDK9Method(require.getClass(), "name");
                }
                String name = requireNameGetter.invoke(require);
                for (Object module : modules) {
                    String moduleName = getName.invoke(module);
                    if (moduleName.equals(name)) {
                        res.add(module);
                    }
                }
            }
            return res;
        } catch (Exception e) {
            throw new GraalError(e);
        }
    }
}
