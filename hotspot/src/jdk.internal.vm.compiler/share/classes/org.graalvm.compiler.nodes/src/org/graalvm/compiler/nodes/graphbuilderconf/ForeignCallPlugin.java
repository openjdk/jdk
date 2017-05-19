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
package org.graalvm.compiler.nodes.graphbuilderconf;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * {@link InvocationPlugin} for converting a method call directly to a foreign call.
 */
public final class ForeignCallPlugin implements InvocationPlugin {
    private final ForeignCallsProvider foreignCalls;
    private final ForeignCallDescriptor descriptor;

    public ForeignCallPlugin(ForeignCallsProvider foreignCalls, ForeignCallDescriptor descriptor) {
        this.foreignCalls = foreignCalls;
        this.descriptor = descriptor;
    }

    @Override
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {
        ForeignCallNode foreignCall = new ForeignCallNode(foreignCalls, descriptor, args);
        foreignCall.setBci(b.bci());
        b.addPush(targetMethod.getSignature().getReturnKind(), foreignCall);
        return true;
    }
}
