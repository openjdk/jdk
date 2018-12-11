/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.replacements;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;

// JaCoCo Exclude

/**
 * Substitutions for {@link java.lang.Object} methods.
 */
@ClassSubstitution(Object.class)
public class ObjectSubstitutions {

    @MethodSubstitution(isStatic = false)
    public static int hashCode(final Object thisObj) {
        return IdentityHashCodeNode.identityHashCode(thisObj);
    }

    @MethodSubstitution(isStatic = false)
    public static void notify(final Object thisObj) {
        if (!fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY, thisObj)) {
            notify(thisObj);
        }
    }

    @MethodSubstitution(isStatic = false)
    public static void notifyAll(final Object thisObj) {
        if (!fastNotifyStub(HotSpotHostForeignCallsProvider.NOTIFY_ALL, thisObj)) {
            notifyAll(thisObj);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    public static native boolean fastNotifyStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object o);
}
