package jdk.internal.org.commonmark.renderer;

import jdk.internal.org.commonmark.node.Node;

import java.util.Set;

/**
 * A renderer for a set of node types.
 */
public interface NodeRenderer {

    /**
     * @return the types of nodes that this renderer handles
     */
    Set<Class<? extends Node>> getNodeTypes();

    /**
     * Render the specified node.
     *
     * @param node the node to render, will be an instance of one of {@link #getNodeTypes()}
     */
    void render(Node node);
}
