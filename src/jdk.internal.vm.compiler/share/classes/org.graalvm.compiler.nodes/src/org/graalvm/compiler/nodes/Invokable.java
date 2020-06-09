/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;

/**
 * A marker interface for nodes that represent calls to other methods.
 */
public interface Invokable extends DeoptBciSupplier {
    ResolvedJavaMethod getTargetMethod();

    /**
     * Returns the {@linkplain ResolvedJavaMethod method} from which the call is executed.
     *
     * @return the method from which the call is executed.
     */
    ResolvedJavaMethod getContextMethod();

    default boolean isAlive() {
        return asFixedNode().isAlive();
    }

    FixedNode asFixedNode();

    /**
     * Called on a {@link Invokable} node after it is registered with a graph.
     *
     * To override the default functionality, code that creates an {@link Invokable} should set the
     * updating logic by calling {@link InliningLog#openUpdateScope}.
     */
    default void updateInliningLogAfterRegister(StructuredGraph newGraph) {
        InliningLog log = newGraph.getInliningLog();
        if (log.getUpdateScope() != null) {
            log.getUpdateScope().accept(null, this);
        } else {
            assert !log.containsLeafCallsite(this);
            log.trackNewCallsite(this);
        }
    }

    /**
     * Called on a {@link Invokable} node after it was cloned from another node.
     *
     * This call is always preceded with a call to {@link Invokable#updateInliningLogAfterRegister}.
     *
     * To override the default functionality, code that creates an {@link Invokable} should set the
     * updating logic by calling {@link InliningLog#openUpdateScope}.
     */
    default void updateInliningLogAfterClone(Node other) {
        if (GraalOptions.TraceInlining.getValue(asFixedNode().getOptions())) {
            // At this point, the invokable node was already added to the inlining log
            // in the call to updateInliningLogAfterRegister, so we need to remove it.
            InliningLog log = asFixedNode().graph().getInliningLog();
            assert other instanceof Invokable;
            if (log.getUpdateScope() != null) {
                // InliningLog.UpdateScope determines how to update the log.
                log.getUpdateScope().accept((Invokable) other, this);
            } else if (other.graph() == this.asFixedNode().graph()) {
                // This node was cloned as part of duplication.
                // We need to add it as a sibling of the node other.
                assert log.containsLeafCallsite(this) : "Node " + this + " not contained in the log.";
                assert log.containsLeafCallsite((Invokable) other) : "Sibling " + other + " not contained in the log.";
                log.removeLeafCallsite(this);
                log.trackDuplicatedCallsite((Invokable) other, this);
            } else {
                // This node was added from a different graph.
                // The adder is responsible for providing a context.
                throw GraalError.shouldNotReachHere("No InliningLog.Update scope provided.");
            }
        }
    }
}
