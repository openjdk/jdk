/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common.inlining.walker;

import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Information about a graph that will potentially be inlined. This includes tracking the
 * invocations in graph that will subject to inlining themselves.
 */
public abstract class CallsiteHolder {

    /**
     * Gets the method associated with the {@linkplain #graph() graph} represented by this object.
     */
    public abstract ResolvedJavaMethod method();

    /**
     * The stack realized by {@link InliningData} grows upon {@link InliningData#moveForward()}
     * deciding to explore (depth-first) a callsite of the graph associated to this
     * {@link CallsiteHolder}. The list of not-yet-considered callsites is managed by
     * {@link CallsiteHolderExplorable}, and this method reports whether any such candidates remain.
     */
    public abstract boolean hasRemainingInvokes();

    /**
     * The graph about which this object contains inlining information.
     */
    public abstract StructuredGraph graph();

}
