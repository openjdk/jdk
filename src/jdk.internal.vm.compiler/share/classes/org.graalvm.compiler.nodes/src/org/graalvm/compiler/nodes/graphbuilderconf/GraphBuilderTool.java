/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Used by a {@link GraphBuilderPlugin} to interface with an object that builds a graph.
 */
public interface GraphBuilderTool {

    /**
     * Adds the given node to the graph and also adds recursively all referenced inputs.
     *
     * @param value the node to be added to the graph
     * @return either the node added or an equivalent node
     */
    <T extends ValueNode> T append(T value);

    StampProvider getStampProvider();

    MetaAccessProvider getMetaAccess();

    Replacements getReplacements();

    default Assumptions getAssumptions() {
        return getGraph().getAssumptions();
    }

    ConstantReflectionProvider getConstantReflection();

    ConstantFieldProvider getConstantFieldProvider();

    /**
     * Gets the graph being constructed.
     */
    StructuredGraph getGraph();

    default OptionValues getOptions() {
        return getGraph().getOptions();
    }

    default DebugContext getDebug() {
        return getGraph().getDebug();
    }

    /**
     * Determines if this parsing context is within the bytecode of an intrinsic or a method inlined
     * by an intrinsic.
     */
    boolean parsingIntrinsic();

    @SuppressWarnings("unused")
    default boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
        // By default generated plugins must be completely processed during parsing.
        return false;
    }
}
