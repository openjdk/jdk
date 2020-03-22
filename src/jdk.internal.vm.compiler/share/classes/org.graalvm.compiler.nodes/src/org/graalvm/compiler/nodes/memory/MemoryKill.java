/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.memory;

import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNodeInterface;
import jdk.internal.vm.compiler.word.LocationIdentity;

/**
 * This interface marks nodes that kill a set of memory locations represented by
 * {@linkplain LocationIdentity} (i.e. change a value at one or more locations that belong to these
 * location identities). This does not only include real memory kills like subclasses of
 * {@linkplain FixedNode} that, e.g., write a memory location, but also conceptual memory kills,
 * i.e., nodes in the memory graph that mark the last accesses to such a location, like a
 * {@linkplain MemoryPhiNode} node.
 */
public interface MemoryKill extends ValueNodeInterface {

}
