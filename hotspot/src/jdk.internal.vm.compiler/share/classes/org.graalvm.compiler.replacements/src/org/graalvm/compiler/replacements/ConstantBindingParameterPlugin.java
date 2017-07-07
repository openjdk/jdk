/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A {@link ParameterPlugin} that binds constant values to some parameters.
 */
public class ConstantBindingParameterPlugin implements ParameterPlugin {
    private final Object[] constantArgs;
    private final MetaAccessProvider metaAccess;
    private final SnippetReflectionProvider snippetReflection;

    /**
     * Creates a plugin that will create {@link ConstantNode}s for each parameter with an index
     * equal to that of a non-null object in {@code constantArgs} (from which the
     * {@link ConstantNode} is created if it isn't already a {@link ConstantNode}).
     */
    public ConstantBindingParameterPlugin(Object[] constantArgs, MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection) {
        this.constantArgs = constantArgs;
        this.metaAccess = metaAccess;
        this.snippetReflection = snippetReflection;
    }

    @Override
    public FloatingNode interceptParameter(GraphBuilderTool b, int index, StampPair stamp) {
        Object arg = constantArgs[index];
        if (arg != null) {
            ConstantNode constantNode;
            if (arg instanceof ConstantNode) {
                constantNode = (ConstantNode) arg;
            } else if (arg instanceof Constant) {
                constantNode = ConstantNode.forConstant(stamp.getTrustedStamp(), (Constant) arg, metaAccess);
            } else {
                constantNode = ConstantNode.forConstant(snippetReflection.forBoxed(stamp.getTrustedStamp().getStackKind(), arg), metaAccess);
            }
            return constantNode;
        }
        return null;
    }
}
