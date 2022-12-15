package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.renderer.NodeRenderer;

/**
 * Factory for instantiating new node renderers when rendering is done.
 */
public interface HtmlNodeRendererFactory {

    /**
     * Create a new node renderer for the specified rendering context.
     *
     * @param context the context for rendering (normally passed on to the node renderer)
     * @return a node renderer
     */
    NodeRenderer create(HtmlNodeRendererContext context);
}
