/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common.inlining.info;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.info.elem.Inlineable;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents an opportunity for inlining at a given invoke, with the given weight and level. The
 * weight is the amortized weight of the additional code - so smaller is better. The level is the
 * number of nested inlinings that lead to this invoke.
 */
public interface InlineInfo {

    /**
     * The graph containing the {@link #invoke() invocation} that may be inlined.
     */
    StructuredGraph graph();

    /**
     * The invocation that may be inlined.
     */
    Invoke invoke();

    /**
     * Returns the number of methods that may be inlined by the {@link #invoke() invocation}. This
     * may be more than one in the case of a invocation profile showing a number of "hot" concrete
     * methods dispatched to by the invocation.
     */
    int numberOfMethods();

    ResolvedJavaMethod methodAt(int index);

    Inlineable inlineableElementAt(int index);

    double probabilityAt(int index);

    double relevanceAt(int index);

    void setInlinableElement(int index, Inlineable inlineableElement);

    /**
     * Performs the inlining described by this object and returns the node that represents the
     * return value of the inlined method (or null for void methods and methods that have no
     * non-exceptional exit).
     *
     * @return a collection of nodes that need to be canonicalized after the inlining
     */
    EconomicSet<Node> inline(Providers providers, String reason);

    /**
     * Try to make the call static bindable to avoid interface and virtual method calls.
     */
    void tryToDevirtualizeInvoke(Providers providers);

    boolean shouldInline();

    void populateInlinableElements(HighTierContext context, StructuredGraph caller, CanonicalizerPhase canonicalizer, OptionValues optionValues);

    int determineNodeCount();
}
