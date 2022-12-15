package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.node.Image;
import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.Node;

import java.util.Map;

public interface HtmlNodeRendererContext {

    /**
     * @param url to be encoded
     * @return an encoded URL (depending on the configuration)
     */
    String encodeUrl(String url);

    /**
     * Let extensions modify the HTML tag attributes.
     *
     * @param node the node for which the attributes are applied
     * @param tagName the HTML tag name that these attributes are for (e.g. {@code h1}, {@code pre}, {@code code}).
     * @param attributes the attributes that were calculated by the renderer
     * @return the extended attributes with added/updated/removed entries
     */
    Map<String, String> extendAttributes(Node node, String tagName, Map<String, String> attributes);

    /**
     * @return the HTML writer to use
     */
    HtmlWriter getWriter();

    /**
     * @return HTML that should be rendered for a soft line break
     */
    String getSoftbreak();

    /**
     * Render the specified node and its children using the configured renderers. This should be used to render child
     * nodes; be careful not to pass the node that is being rendered, that would result in an endless loop.
     *
     * @param node the node to render
     */
    void render(Node node);

    /**
     * @return whether HTML blocks and tags should be escaped or not
     */
    boolean shouldEscapeHtml();

    /**
     * @return true if the {@link UrlSanitizer} should be used.
     * @since 0.14.0
     */
    boolean shouldSanitizeUrls();

    /**
     * @return Sanitizer to use for securing {@link Link} href and {@link Image} src if {@link #shouldSanitizeUrls()} is true.
     * @since 0.14.0
     */
    UrlSanitizer urlSanitizer();
}
