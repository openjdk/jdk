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
package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

import jdk.vm.ci.meta.JavaKind;

/**
 * Provides a capability for replacing a higher node with one or more lower level nodes.
 */
public interface LoweringProvider {

    void lower(Node n, LoweringTool tool);

    /**
     * Reconstructs the array index from an address node that was created as a lowering of an
     * indexed access to an array.
     *
     * @param elementKind the {@link JavaKind} of the array elements
     * @param address an {@link AddressNode} pointing to an element in an array
     * @return a node that gives the index of the element
     */
    ValueNode reconstructArrayIndex(JavaKind elementKind, AddressNode address);

    /**
     * Indicates the smallest width for comparing an integer value on the target platform.
     */
    default Integer smallestCompareWidth() {
        // most platforms only support 32 and 64 bit compares
        return 32;
    }
}
