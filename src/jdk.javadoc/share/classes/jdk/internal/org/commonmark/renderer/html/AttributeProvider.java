package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.node.Node;

import java.util.Map;

/**
 * Extension point for adding/changing attributes on HTML tags for a node.
 */
public interface AttributeProvider {

    /**
     * Set the attributes for a HTML tag of the specified node by modifying the provided map.
     * <p>
     * This allows to change or even remove default attributes. With great power comes great responsibility.
     * <p>
     * The attribute key and values will be escaped (preserving character entities), so don't escape them here,
     * otherwise they will be double-escaped.
     * <p>
     * This method may be called multiple times for the same node, if the node is rendered using multiple nested
     * tags (e.g. code blocks).
     *
     * @param node the node to set attributes for
     * @param tagName the HTML tag name that these attributes are for (e.g. {@code h1}, {@code pre}, {@code code}).
     * @param attributes the attributes, with any default attributes already set in the map
     */
    void setAttributes(Node node, String tagName, Map<String, String> attributes);

}
