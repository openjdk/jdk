package jdk.internal.org.commonmark.renderer.text;

import jdk.internal.org.commonmark.Extension;
import jdk.internal.org.commonmark.internal.renderer.NodeRendererMap;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.renderer.NodeRenderer;
import jdk.internal.org.commonmark.renderer.Renderer;

import java.util.ArrayList;
import java.util.List;

public class TextContentRenderer implements Renderer {

    private final boolean stripNewlines;

    private final List<TextContentNodeRendererFactory> nodeRendererFactories;

    private TextContentRenderer(Builder builder) {
        this.stripNewlines = builder.stripNewlines;

        this.nodeRendererFactories = new ArrayList<>(builder.nodeRendererFactories.size() + 1);
        this.nodeRendererFactories.addAll(builder.nodeRendererFactories);
        // Add as last. This means clients can override the rendering of core nodes if they want.
        this.nodeRendererFactories.add(new TextContentNodeRendererFactory() {
            @Override
            public NodeRenderer create(TextContentNodeRendererContext context) {
                return new CoreTextContentNodeRenderer(context);
            }
        });
    }

    /**
     * Create a new builder for configuring an {@link TextContentRenderer}.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void render(Node node, Appendable output) {
        RendererContext context = new RendererContext(new TextContentWriter(output));
        context.render(node);
    }

    @Override
    public String render(Node node) {
        StringBuilder sb = new StringBuilder();
        render(node, sb);
        return sb.toString();
    }

    /**
     * Builder for configuring an {@link TextContentRenderer}. See methods for default configuration.
     */
    public static class Builder {

        private boolean stripNewlines = false;
        private List<TextContentNodeRendererFactory> nodeRendererFactories = new ArrayList<>();

        /**
         * @return the configured {@link TextContentRenderer}
         */
        public TextContentRenderer build() {
            return new TextContentRenderer(this);
        }

        /**
         * Set the value of flag for stripping new lines.
         *
         * @param stripNewlines true for stripping new lines and render text as "single line",
         *                      false for keeping all line breaks
         * @return {@code this}
         */
        public Builder stripNewlines(boolean stripNewlines) {
            this.stripNewlines = stripNewlines;
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
        public Builder nodeRendererFactory(TextContentNodeRendererFactory nodeRendererFactory) {
            this.nodeRendererFactories.add(nodeRendererFactory);
            return this;
        }

        /**
         * @param extensions extensions to use on this text content renderer
         * @return {@code this}
         */
        public Builder extensions(Iterable<? extends Extension> extensions) {
            for (Extension extension : extensions) {
                if (extension instanceof TextContentRenderer.TextContentRendererExtension) {
                    TextContentRenderer.TextContentRendererExtension textContentRendererExtension =
                            (TextContentRenderer.TextContentRendererExtension) extension;
                    textContentRendererExtension.extend(this);
                }
            }
            return this;
        }
    }

    /**
     * Extension for {@link TextContentRenderer}.
     */
    public interface TextContentRendererExtension extends Extension {
        void extend(TextContentRenderer.Builder rendererBuilder);
    }

    private class RendererContext implements TextContentNodeRendererContext {
        private final TextContentWriter textContentWriter;
        private final NodeRendererMap nodeRendererMap = new NodeRendererMap();

        private RendererContext(TextContentWriter textContentWriter) {
            this.textContentWriter = textContentWriter;

            // The first node renderer for a node type "wins".
            for (int i = nodeRendererFactories.size() - 1; i >= 0; i--) {
                TextContentNodeRendererFactory nodeRendererFactory = nodeRendererFactories.get(i);
                NodeRenderer nodeRenderer = nodeRendererFactory.create(this);
                nodeRendererMap.add(nodeRenderer);
            }
        }

        @Override
        public boolean stripNewlines() {
            return stripNewlines;
        }

        @Override
        public TextContentWriter getWriter() {
            return textContentWriter;
        }

        @Override
        public void render(Node node) {
            nodeRendererMap.render(node);
        }
    }
}
