/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.JavaConstant;

/**
 * {@link GraphBuilderPlugin} interface for static compilation mode, allowing references to dynamic
 * types.
 */
public interface InvokeDynamicPlugin extends GraphBuilderPlugin {

    /**
     * Checks for a resolved dynamic adapter method at the specified index, resulting from either a
     * resolved invokedynamic or invokevirtual on a signature polymorphic MethodHandle method
     * (HotSpot invokehandle).
     *
     * @param builder context for the invoke
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @return {@code true} if a signature polymorphic method reference was found, otherwise
     *         {@code false}
     */
    boolean isResolvedDynamicInvoke(GraphBuilderContext builder, int cpi, int opcode);

    /**
     * Checks if this plugin instance supports the specified dynamic invoke.
     *
     * @param builder context for the invoke
     * @param cpi the constant pool index
     * @param opcode the opcode of the invoke instruction
     * @return {@code true} if this dynamic invoke is supported
     */
    boolean supportsDynamicInvoke(GraphBuilderContext builder, int cpi, int opcode);

    /**
     * Notifies this object of the value and context of the dynamic method target (e.g., A HotSpot
     * adapter method) for a resolved dynamic invoke.
     *
     * @param builder context for the invoke
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @param target dynamic target method to record
     */
    void recordDynamicMethod(GraphBuilderContext builder, int cpi, int opcode, ResolvedJavaMethod target);

    /**
     * Notifies this object of the value and context of the dynamic appendix object for a resolved
     * dynamic invoke.
     *
     * @param builder context for the invoke
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed
     * @return {@link ValueNode} for appendix constant
     */
    ValueNode genAppendixNode(GraphBuilderContext builder, int cpi, int opcode, JavaConstant appendix, FrameState frameState);

}
