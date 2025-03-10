/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.renderer.html;

import jdk.internal.org.commonmark.Extension;
import jdk.internal.org.commonmark.internal.renderer.NodeRendererMap;
import jdk.internal.org.commonmark.internal.util.Escaping;
import jdk.internal.org.commonmark.node.*;
import jdk.internal.org.commonmark.renderer.NodeRenderer;
import jdk.internal.org.commonmark.renderer.Renderer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a tree of nodes to HTML.
 * <p>
 * Start with the {@link #builder} method to configure the renderer. Example:
 * <pre><code>
 * HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
 * renderer.render(node);
 * </code></pre>
 */
public class HtmlRenderer implements Renderer {

    private final String softbreak;
    private final boolean escapeHtml;
    private final boolean sanitizeUrls;
    private final UrlSanitizer urlSanitizer;
    private final boolean percentEncodeUrls;
    private final List<AttributeProviderFactory> attributeProviderFactories;
    private final List<HtmlNodeRendererFactory> nodeRendererFactories;

    private HtmlRenderer(Builder builder) {
        this.softbreak = builder.softbreak;
        this.escapeHtml = builder.escapeHtml;
        this.sanitizeUrls = builder.sanitizeUrls;
        this.percentEncodeUrls = builder.percentEncodeUrls;
        this.urlSanitizer = builder.urlSanitizer;
        this.attributeProviderFactories = new ArrayList<>(builder.attributeProviderFactories);

        this.nodeRendererFactories = new ArrayList<>(builder.nodeRendererFactories.size() + 1);
        this.nodeRendererFactories.addAll(builder.nodeRendererFactories);
        // Add as last. This means clients can override the rendering of core nodes if they want.
        this.nodeRendererFactories.add(new HtmlNodeRendererFactory() {
            @Override
            public NodeRenderer create(HtmlNodeRendererContext context) {
                return new CoreHtmlNodeRenderer(context);
            }
        });
    }

    /**
     * Create a new builder for configuring an {@link HtmlRenderer}.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(Node node, Appendable output) {
        if (node == null) {
            throw new NullPointerException("node must not be null");
        }
        RendererContext context = new RendererContext(new HtmlWriter(output));
        context.render(node);
    }

    @Override
    public String render(Node node) {
        if (node == null) {
            throw new NullPointerException("node must not be null");
        }
        StringBuilder sb = new StringBuilder();
        render(node, sb);
        return sb.toString();
    }

    /**
     * Builder for configuring an {@link HtmlRenderer}. See methods for default configuration.
     */
    public static class Builder {

        private String softbreak = "\n";
        private boolean escapeHtml = false;
        private boolean sanitizeUrls = false;
        private UrlSanitizer urlSanitizer = new DefaultUrlSanitizer();
        private boolean percentEncodeUrls = false;
        private List<AttributeProviderFactory> attributeProviderFactories = new ArrayList<>();
        private List<HtmlNodeRendererFactory> nodeRendererFactories = new ArrayList<>();

        /**
         * @return the configured {@link HtmlRenderer}
         */
        public HtmlRenderer build() {
            return new HtmlRenderer(this);
        }

        /**
         * The HTML to use for rendering a softbreak, defaults to {@code "\n"} (meaning the rendered result doesn't have
         * a line break).
         * <p>
         * Set it to {@code "<br>"} (or {@code "<br />"} to make them hard breaks.
         * <p>
         * Set it to {@code " "} to ignore line wrapping in the source.
         *
         * @param softbreak HTML for softbreak
         * @return {@code this}
         */
        public Builder softbreak(String softbreak) {
            this.softbreak = softbreak;
            return this;
        }

        /**
         * Whether {@link HtmlInline} and {@link HtmlBlock} should be escaped, defaults to {@code false}.
         * <p>
         * Note that {@link HtmlInline} is only a tag itself, not the text between an opening tag and a closing tag. So
         * markup in the text will be parsed as normal and is not affected by this option.
         *
         * @param escapeHtml true for escaping, false for preserving raw HTML
         * @return {@code this}
         */
        public Builder escapeHtml(boolean escapeHtml) {
            this.escapeHtml = escapeHtml;
            return this;
        }

        /**
         * Whether {@link Image} src and {@link Link} href should be sanitized, defaults to {@code false}.
         *
         * @param sanitizeUrls true for sanitization, false for preserving raw attribute
         * @return {@code this}
         * @since 0.14.0
         */
        public Builder sanitizeUrls(boolean sanitizeUrls) {
            this.sanitizeUrls = sanitizeUrls;
            return this;
        }

        /**
         * {@link UrlSanitizer} used to filter URL's if {@link #sanitizeUrls} is true.
         *
         * @param urlSanitizer Filterer used to filter {@link Image} src and {@link Link}.
         * @return {@code this}
         * @since 0.14.0
         */
        public Builder urlSanitizer(UrlSanitizer urlSanitizer) {
            this.urlSanitizer = urlSanitizer;
            return this;
        }

        /**
         * Whether URLs of link or images should be percent-encoded, defaults to {@code false}.
         * <p>
         * If enabled, the following is done:
         * <ul>
         * <li>Existing percent-encoded parts are preserved (e.g. "%20" is kept as "%20")</li>
         * <li>Reserved characters such as "/" are preserved, except for "[" and "]" (see encodeURI in JS)</li>
         * <li>Unreserved characters such as "a" are preserved</li>
         * <li>Other characters such umlauts are percent-encoded</li>
         * </ul>
         *
         * @param percentEncodeUrls true to percent-encode, false for leaving as-is
         * @return {@code this}
         */
        public Builder percentEncodeUrls(boolean percentEncodeUrls) {
            this.percentEncodeUrls = percentEncodeUrls;
            return this;
        }

        /**
         * Add a factory for an attribute provider for adding/changing HTML attributes to the rendered tags.
         *
         * @param attributeProviderFactory the attribute provider factory to add
         * @return {@code this}
         */
        public Builder attributeProviderFactory(AttributeProviderFactory attributeProviderFactory) {
            if (attributeProviderFactory == null) {
                throw new NullPointerException("attributeProviderFactory must not be null");
            }
            this.attributeProviderFactories.add(attributeProviderFactory);
            return this;
        }

        /**
         * Add a factory for instantiating a node renderer (done when rendering). This allows to override the rendering
         * of node types or define rendering for custom node types.
         * <p>
         * If multiple node renderers for the same node type are created, the one from the factory that was added first
         * "wins". (This is how the rendering for core node types can be overridden; the default rendering comes last.)
         *
         * @param nodeRendererFactory the factory for creating a node renderer
         * @return {@code this}
         */
        public Builder nodeRendererFactory(HtmlNodeRendererFactory nodeRendererFactory) {
            if (nodeRendererFactory == null) {
                throw new NullPointerException("nodeRendererFactory must not be null");
            }
            this.nodeRendererFactories.add(nodeRendererFactory);
            return this;
        }

        /**
         * @param extensions extensions to use on this HTML renderer
         * @return {@code this}
         */
        public Builder extensions(Iterable<? extends Extension> extensions) {
            if (extensions == null) {
                throw new NullPointerException("extensions must not be null");
            }
            for (Extension extension : extensions) {
                if (extension instanceof HtmlRendererExtension) {
                    HtmlRendererExtension htmlRendererExtension = (HtmlRendererExtension) extension;
                    htmlRendererExtension.extend(this);
                }
            }
            return this;
        }
    }

    /**
     * Extension for {@link HtmlRenderer}.
     */
    public interface HtmlRendererExtension extends Extension {
        void extend(Builder rendererBuilder);
    }

    private class RendererContext implements HtmlNodeRendererContext, AttributeProviderContext {

        private final HtmlWriter htmlWriter;
        private final List<AttributeProvider> attributeProviders;
        private final NodeRendererMap nodeRendererMap = new NodeRendererMap();

        private RendererContext(HtmlWriter htmlWriter) {
            this.htmlWriter = htmlWriter;

            attributeProviders = new ArrayList<>(attributeProviderFactories.size());
            for (AttributeProviderFactory attributeProviderFactory : attributeProviderFactories) {
                attributeProviders.add(attributeProviderFactory.create(this));
            }

            // The first node renderer for a node type "wins".
            for (int i = nodeRendererFactories.size() - 1; i >= 0; i--) {
                HtmlNodeRendererFactory nodeRendererFactory = nodeRendererFactories.get(i);
                NodeRenderer nodeRenderer = nodeRendererFactory.create(this);
                nodeRendererMap.add(nodeRenderer);
            }
        }

        @Override
        public boolean shouldEscapeHtml() {
            return escapeHtml;
        }

        @Override
        public boolean shouldSanitizeUrls() {
            return sanitizeUrls;
        }

        @Override
        public UrlSanitizer urlSanitizer() {
            return urlSanitizer;
        }

        @Override
        public String encodeUrl(String url) {
            if (percentEncodeUrls) {
                return Escaping.percentEncodeUrl(url);
            } else {
                return url;
            }
        }

        @Override
        public Map<String, String> extendAttributes(Node node, String tagName, Map<String, String> attributes) {
            Map<String, String> attrs = new LinkedHashMap<>(attributes);
            setCustomAttributes(node, tagName, attrs);
            return attrs;
        }

        @Override
        public HtmlWriter getWriter() {
            return htmlWriter;
        }

        @Override
        public String getSoftbreak() {
            return softbreak;
        }

        @Override
        public void render(Node node) {
            nodeRendererMap.render(node);
        }

        private void setCustomAttributes(Node node, String tagName, Map<String, String> attrs) {
            for (AttributeProvider attributeProvider : attributeProviders) {
                attributeProvider.setAttributes(node, tagName, attrs);
            }
        }
    }
}
