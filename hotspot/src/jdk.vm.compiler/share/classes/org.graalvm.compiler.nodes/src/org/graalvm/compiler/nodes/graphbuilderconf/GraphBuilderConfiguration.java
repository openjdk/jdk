/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.graphbuilderconf;

import java.util.Arrays;

import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.type.StampPair;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

public class GraphBuilderConfiguration {

    public static class Plugins {
        private final InvocationPlugins invocationPlugins;
        private NodePlugin[] nodePlugins;
        private ParameterPlugin[] parameterPlugins;
        private TypePlugin[] typePlugins;
        private InlineInvokePlugin[] inlineInvokePlugins;
        private LoopExplosionPlugin loopExplosionPlugin;
        private ClassInitializationPlugin classInitializationPlugin;
        private ProfilingPlugin profilingPlugin;

        /**
         * Creates a copy of a given set of plugins. The {@link InvocationPlugins} in
         * {@code copyFrom} become the {@linkplain InvocationPlugins#getParent() default}
         * {@linkplain #getInvocationPlugins() invocation plugins} in this object.
         */
        public Plugins(Plugins copyFrom) {
            this.invocationPlugins = new InvocationPlugins(copyFrom.invocationPlugins);
            this.nodePlugins = copyFrom.nodePlugins;
            this.parameterPlugins = copyFrom.parameterPlugins;
            this.typePlugins = copyFrom.typePlugins;
            this.inlineInvokePlugins = copyFrom.inlineInvokePlugins;
            this.loopExplosionPlugin = copyFrom.loopExplosionPlugin;
            this.classInitializationPlugin = copyFrom.classInitializationPlugin;
            this.profilingPlugin = copyFrom.profilingPlugin;
        }

        /**
         * Creates a new set of plugins.
         *
         * @param invocationPlugins the {@linkplain #getInvocationPlugins() invocation plugins} in
         *            this object
         */
        public Plugins(InvocationPlugins invocationPlugins) {
            this.invocationPlugins = invocationPlugins;
            this.nodePlugins = new NodePlugin[0];
            this.parameterPlugins = new ParameterPlugin[0];
            this.typePlugins = new TypePlugin[0];
            this.inlineInvokePlugins = new InlineInvokePlugin[0];
        }

        public InvocationPlugins getInvocationPlugins() {
            return invocationPlugins;
        }

        public NodePlugin[] getNodePlugins() {
            return nodePlugins;
        }

        public void appendNodePlugin(NodePlugin plugin) {
            nodePlugins = Arrays.copyOf(nodePlugins, nodePlugins.length + 1);
            nodePlugins[nodePlugins.length - 1] = plugin;
        }

        public void prependNodePlugin(NodePlugin plugin) {
            NodePlugin[] newPlugins = new NodePlugin[nodePlugins.length + 1];
            System.arraycopy(nodePlugins, 0, newPlugins, 1, nodePlugins.length);
            newPlugins[0] = plugin;
            nodePlugins = newPlugins;
        }

        public void clearNodePlugin() {
            nodePlugins = new NodePlugin[0];
        }

        public ParameterPlugin[] getParameterPlugins() {
            return parameterPlugins;
        }

        public void appendParameterPlugin(ParameterPlugin plugin) {
            parameterPlugins = Arrays.copyOf(parameterPlugins, parameterPlugins.length + 1);
            parameterPlugins[parameterPlugins.length - 1] = plugin;
        }

        public void prependParameterPlugin(ParameterPlugin plugin) {
            ParameterPlugin[] newPlugins = new ParameterPlugin[parameterPlugins.length + 1];
            System.arraycopy(parameterPlugins, 0, newPlugins, 1, parameterPlugins.length);
            newPlugins[0] = plugin;
            parameterPlugins = newPlugins;
        }

        public TypePlugin[] getTypePlugins() {
            return typePlugins;
        }

        public void appendTypePlugin(TypePlugin plugin) {
            typePlugins = Arrays.copyOf(typePlugins, typePlugins.length + 1);
            typePlugins[typePlugins.length - 1] = plugin;
        }

        public void prependTypePlugin(TypePlugin plugin) {
            TypePlugin[] newPlugins = new TypePlugin[typePlugins.length + 1];
            System.arraycopy(typePlugins, 0, newPlugins, 1, typePlugins.length);
            newPlugins[0] = plugin;
            typePlugins = newPlugins;
        }

        public void clearParameterPlugin() {
            parameterPlugins = new ParameterPlugin[0];
        }

        public InlineInvokePlugin[] getInlineInvokePlugins() {
            return inlineInvokePlugins;
        }

        public void appendInlineInvokePlugin(InlineInvokePlugin plugin) {
            inlineInvokePlugins = Arrays.copyOf(inlineInvokePlugins, inlineInvokePlugins.length + 1);
            inlineInvokePlugins[inlineInvokePlugins.length - 1] = plugin;
        }

        public void prependInlineInvokePlugin(InlineInvokePlugin plugin) {
            InlineInvokePlugin[] newPlugins = new InlineInvokePlugin[inlineInvokePlugins.length + 1];
            System.arraycopy(inlineInvokePlugins, 0, newPlugins, 1, inlineInvokePlugins.length);
            newPlugins[0] = plugin;
            inlineInvokePlugins = newPlugins;
        }

        public void clearInlineInvokePlugins() {
            inlineInvokePlugins = new InlineInvokePlugin[0];
        }

        public LoopExplosionPlugin getLoopExplosionPlugin() {
            return loopExplosionPlugin;
        }

        public void setLoopExplosionPlugin(LoopExplosionPlugin plugin) {
            this.loopExplosionPlugin = plugin;
        }

        public ClassInitializationPlugin getClassInitializationPlugin() {
            return classInitializationPlugin;
        }

        public void setClassInitializationPlugin(ClassInitializationPlugin plugin) {
            this.classInitializationPlugin = plugin;
        }

        public ProfilingPlugin getProfilingPlugin() {
            return profilingPlugin;
        }

        public void setProfilingPlugin(ProfilingPlugin plugin) {
            this.profilingPlugin = plugin;
        }

        public StampPair getOverridingStamp(GraphBuilderTool b, JavaType type, boolean nonNull) {
            for (TypePlugin plugin : getTypePlugins()) {
                StampPair stamp = plugin.interceptType(b, type, nonNull);
                if (stamp != null) {
                    return stamp;
                }
            }
            return null;
        }
    }

    private static final ResolvedJavaType[] EMPTY = new ResolvedJavaType[]{};

    private final boolean eagerResolving;
    private final BytecodeExceptionMode bytecodeExceptionMode;
    private final boolean omitAssertions;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final boolean insertFullInfopoints;
    private final boolean trackNodeSourcePosition;
    private final boolean clearNonLiveLocals;
    private final Plugins plugins;

    public enum BytecodeExceptionMode {
        /**
         * This mode always explicitly checks for exceptions.
         */
        CheckAll,
        /**
         * This mode omits all explicit exception edges.
         */
        OmitAll,
        /**
         * This mode uses profiling information to decide whether to use explicit exception edges.
         */
        Profile
    }

    protected GraphBuilderConfiguration(boolean eagerResolving, BytecodeExceptionMode bytecodeExceptionMode, boolean omitAssertions, boolean insertFullInfopoints,
                    boolean trackNodeSourcePosition, ResolvedJavaType[] skippedExceptionTypes,
                    boolean clearNonLiveLocals, Plugins plugins) {
        this.eagerResolving = eagerResolving;
        this.bytecodeExceptionMode = bytecodeExceptionMode;
        this.omitAssertions = omitAssertions;
        this.insertFullInfopoints = insertFullInfopoints;
        this.trackNodeSourcePosition = trackNodeSourcePosition;
        this.skippedExceptionTypes = skippedExceptionTypes;
        this.clearNonLiveLocals = clearNonLiveLocals;
        this.plugins = plugins;
    }

    /**
     * Creates a copy of this configuration with all its plugins. The {@link InvocationPlugins} in
     * this configuration become the {@linkplain InvocationPlugins#getParent() parent} of the
     * {@link InvocationPlugins} in the copy.
     */
    public GraphBuilderConfiguration copy() {
        Plugins newPlugins = new Plugins(plugins);
        GraphBuilderConfiguration result = new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, omitAssertions, insertFullInfopoints, trackNodeSourcePosition, skippedExceptionTypes,
                        clearNonLiveLocals, newPlugins);
        return result;
    }

    public GraphBuilderConfiguration withEagerResolving(boolean newEagerResolving) {
        return new GraphBuilderConfiguration(newEagerResolving, bytecodeExceptionMode, omitAssertions, insertFullInfopoints, trackNodeSourcePosition, skippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withSkippedExceptionTypes(ResolvedJavaType[] newSkippedExceptionTypes) {
        return new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, omitAssertions, insertFullInfopoints, trackNodeSourcePosition, newSkippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withBytecodeExceptionMode(BytecodeExceptionMode newBytecodeExceptionMode) {
        return new GraphBuilderConfiguration(eagerResolving, newBytecodeExceptionMode, omitAssertions, insertFullInfopoints, trackNodeSourcePosition, skippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withOmitAssertions(boolean newOmitAssertions) {
        return new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, newOmitAssertions, insertFullInfopoints, trackNodeSourcePosition, skippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withFullInfopoints(boolean newInsertFullInfopoints) {
        ResolvedJavaType[] newSkippedExceptionTypes = skippedExceptionTypes == EMPTY ? EMPTY : Arrays.copyOf(skippedExceptionTypes, skippedExceptionTypes.length);
        return new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, omitAssertions, newInsertFullInfopoints, trackNodeSourcePosition, newSkippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withNodeSourcePosition(boolean newTrackNodeSourcePosition) {
        ResolvedJavaType[] newSkippedExceptionTypes = skippedExceptionTypes == EMPTY ? EMPTY : Arrays.copyOf(skippedExceptionTypes, skippedExceptionTypes.length);
        return new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, omitAssertions, insertFullInfopoints, newTrackNodeSourcePosition, newSkippedExceptionTypes, clearNonLiveLocals,
                        plugins);
    }

    public GraphBuilderConfiguration withClearNonLiveLocals(boolean newClearNonLiveLocals) {
        return new GraphBuilderConfiguration(eagerResolving, bytecodeExceptionMode, omitAssertions, insertFullInfopoints, trackNodeSourcePosition, skippedExceptionTypes, newClearNonLiveLocals,
                        plugins);
    }

    public ResolvedJavaType[] getSkippedExceptionTypes() {
        return skippedExceptionTypes;
    }

    public boolean eagerResolving() {
        return eagerResolving;
    }

    public BytecodeExceptionMode getBytecodeExceptionMode() {
        return bytecodeExceptionMode;
    }

    public boolean omitAssertions() {
        return omitAssertions;
    }

    public boolean trackNodeSourcePosition() {
        return trackNodeSourcePosition;
    }

    public boolean insertFullInfopoints() {
        return insertFullInfopoints;
    }

    public boolean clearNonLiveLocals() {
        return clearNonLiveLocals;
    }

    public static GraphBuilderConfiguration getDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(false, BytecodeExceptionMode.Profile, false, false, false, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    public static GraphBuilderConfiguration getSnippetDefault(Plugins plugins) {
        return new GraphBuilderConfiguration(true, BytecodeExceptionMode.OmitAll, false, false, false, EMPTY, GraalOptions.OptClearNonLiveLocals.getValue(), plugins);
    }

    /**
     * Returns {@code true} if it is an error for a class/field/method resolution to fail. The
     * default is the same result as returned by {@link #eagerResolving()}. However, it may be
     * overridden to allow failure even when {@link #eagerResolving} is {@code true}.
     */
    public boolean unresolvedIsError() {
        return eagerResolving;
    }

    public Plugins getPlugins() {
        return plugins;
    }
}
