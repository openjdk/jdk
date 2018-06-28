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


package org.graalvm.compiler.nodes.extended;

import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.internal.vm.compiler.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Copy a value at a location specified as an offset relative to a source object to another location
 * specified as an offset relative to destination object. No null checks are performed.
 */
public final class UnsafeCopyNode {

    public static boolean intrinsify(GraphBuilderContext b, @SuppressWarnings("unused") ResolvedJavaMethod targetMethod, ValueNode sourceObject, ValueNode sourceOffset, ValueNode destinationObject,
                    ValueNode destinationOffset, JavaKind accessKind, LocationIdentity locationIdentity) {
        RawLoadNode value = b.add(new RawLoadNode(sourceObject, sourceOffset, accessKind, locationIdentity));
        b.add(new RawStoreNode(destinationObject, destinationOffset, value, accessKind, locationIdentity));
        return true;
    }

    @NodeIntrinsic
    public static native void copy(Object srcObject, long srcOffset, Object destObject, long destOffset, @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter LocationIdentity locationIdentity);
}
