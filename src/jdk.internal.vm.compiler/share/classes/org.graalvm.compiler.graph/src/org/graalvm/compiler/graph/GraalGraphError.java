/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.graph;

import org.graalvm.compiler.debug.GraalError;

/**
 * This error is the graph/node aware extension of {@link GraalError}.
 */
public class GraalGraphError extends GraalError {

    private static final long serialVersionUID = -989290015525497919L;
    private Node node;
    private Graph graph;

    /**
     * This constructor creates a {@link GraalGraphError} with a message assembled via
     * {@link String#format(String, Object...)}. It always uses the ENGLISH locale in order to
     * always generate the same output.
     *
     * @param msg the message that will be associated with the error, in String.format syntax
     * @param args parameters to String.format - parameters that implement {@link Iterable} will be
     *            expanded into a [x, x, ...] representation.
     */
    public GraalGraphError(String msg, Object... args) {
        super(msg, args);
    }

    /**
     * This constructor creates a {@link GraalGraphError} for a given causing Throwable instance.
     *
     * @param cause the original exception that contains additional information on this error
     */
    public GraalGraphError(Throwable cause) {
        super(cause);
    }

    /**
     * This constructor creates a {@link GraalGraphError} from a given GraalError instance.
     *
     * @param e the original GraalError
     */
    protected GraalGraphError(GraalError e) {
        super(e);
        if (e instanceof GraalGraphError) {
            node = ((GraalGraphError) e).node;
            graph = ((GraalGraphError) e).graph;
        }
    }

    /**
     * Adds a graph to the context of this VerificationError. The first graph added via this method
     * will be returned by {@link #graph()}.
     *
     * @param newGraph the graph which is in a incorrect state, if the verification error was not
     *            caused by a specific node
     */
    GraalGraphError addContext(Graph newGraph) {
        if (newGraph != this.graph) {
            addContext("graph", newGraph);
            if (this.graph == null) {
                this.graph = newGraph;
            }
        }
        return this;
    }

    /**
     * Adds a node to the context of this VerificationError. The first node added via this method
     * will be returned by {@link #node()}.
     *
     * @param newNode the node which is in a incorrect state, if the verification error was caused
     *            by a node
     */
    public GraalGraphError addContext(Node newNode) {
        if (newNode != this.node) {
            addContext("node", newNode);
            if (this.node == null) {
                this.node = newNode;
            }
        }
        return this;
    }

    /**
     * Transform a GraalError into a GraalGraphInternalError and add a graph to the context.
     *
     * @param e the previous error
     * @param newGraph the graph which is in a incorrect state, if the verification error was not
     *            caused by a specific node
     */
    public static GraalGraphError transformAndAddContext(GraalError e, Graph newGraph) {
        GraalGraphError graphError;
        if (e instanceof GraalGraphError) {
            graphError = (GraalGraphError) e;
        } else {
            graphError = new GraalGraphError(e);
        }
        return graphError.addContext(newGraph);
    }

    /**
     * Transform a GraalError into a GraalGraphInternalError and add a node to the context.
     *
     * @param e the previous error
     * @param newNode the node which is in a incorrect state, if the verification error was caused
     *            by a node
     */
    public static GraalGraphError transformAndAddContext(GraalError e, Node newNode) {
        GraalGraphError graphError;
        if (e instanceof GraalGraphError) {
            graphError = (GraalGraphError) e;
        } else {
            graphError = new GraalGraphError(e);
        }
        return graphError.addContext(newNode);
    }

    public Node node() {
        return node;
    }

    public Graph graph() {
        return graph;
    }
}
