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

package jdk.internal.org.commonmark.parser;

import jdk.internal.org.commonmark.Extension;
import jdk.internal.org.commonmark.internal.DocumentParser;
import jdk.internal.org.commonmark.internal.InlineParserContextImpl;
import jdk.internal.org.commonmark.internal.InlineParserImpl;
import jdk.internal.org.commonmark.internal.LinkReferenceDefinitions;
import jdk.internal.org.commonmark.node.*;
import jdk.internal.org.commonmark.parser.block.BlockParserFactory;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Parses input text to a tree of nodes.
 * <p>
 * Start with the {@link #builder} method, configure the parser and build it. Example:
 * <pre><code>
 * Parser parser = Parser.builder().build();
 * Node document = parser.parse("input text");
 * </code></pre>
 */
public class Parser {

    private final List<BlockParserFactory> blockParserFactories;
    private final List<DelimiterProcessor> delimiterProcessors;
    private final InlineParserFactory inlineParserFactory;
    private final List<PostProcessor> postProcessors;
    private final IncludeSourceSpans includeSourceSpans;

    private Parser(Builder builder) {
        this.blockParserFactories = DocumentParser.calculateBlockParserFactories(builder.blockParserFactories, builder.enabledBlockTypes);
        this.inlineParserFactory = builder.getInlineParserFactory();
        this.postProcessors = builder.postProcessors;
        this.delimiterProcessors = builder.delimiterProcessors;
        this.includeSourceSpans = builder.includeSourceSpans;

        // Try to construct an inline parser. Invalid configuration might result in an exception, which we want to
        // detect as soon as possible.
        this.inlineParserFactory.create(new InlineParserContextImpl(delimiterProcessors, new LinkReferenceDefinitions()));
    }

    /**
     * Create a new builder for configuring a {@link Parser}.
     *
     * @return a builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse the specified input text into a tree of nodes.
     * <p>
     * This method is thread-safe (a new parser state is used for each invocation).
     *
     * @param input the text to parse - must not be null
     * @return the root node
     */
    public Node parse(String input) {
        if (input == null) {
            throw new NullPointerException("input must not be null");
        }
        DocumentParser documentParser = createDocumentParser();
        Node document = documentParser.parse(input);
        return postProcess(document);
    }

    /**
     * Parse the specified reader into a tree of nodes. The caller is responsible for closing the reader.
     * <pre><code>
     * Parser parser = Parser.builder().build();
     * try (InputStreamReader reader = new InputStreamReader(new FileInputStream("file.md"), StandardCharsets.UTF_8)) {
     *     Node document = parser.parseReader(reader);
     *     // ...
     * }
     * </code></pre>
     * Note that if you have a file with a byte order mark (BOM), you need to skip it before handing the reader to this
     * library. There's existing classes that do that, e.g. see {@code BOMInputStream} in Commons IO.
     * <p>
     * This method is thread-safe (a new parser state is used for each invocation).
     *
     * @param input the reader to parse - must not be null
     * @return the root node
     * @throws IOException when reading throws an exception
     */
    public Node parseReader(Reader input) throws IOException {
        if (input == null) {
            throw new NullPointerException("input must not be null");
        }

        DocumentParser documentParser = createDocumentParser();
        Node document = documentParser.parse(input);
        return postProcess(document);
    }

    private DocumentParser createDocumentParser() {
        return new DocumentParser(blockParserFactories, inlineParserFactory, delimiterProcessors, includeSourceSpans);
    }

    private Node postProcess(Node document) {
        for (PostProcessor postProcessor : postProcessors) {
            document = postProcessor.process(document);
        }
        return document;
    }

    /**
     * Builder for configuring a {@link Parser}.
     */
    public static class Builder {
        private final List<BlockParserFactory> blockParserFactories = new ArrayList<>();
        private final List<DelimiterProcessor> delimiterProcessors = new ArrayList<>();
        private final List<PostProcessor> postProcessors = new ArrayList<>();
        private Set<Class<? extends Block>> enabledBlockTypes = DocumentParser.getDefaultBlockParserTypes();
        private InlineParserFactory inlineParserFactory;
        private IncludeSourceSpans includeSourceSpans = IncludeSourceSpans.NONE;

        /**
         * @return the configured {@link Parser}
         */
        public Parser build() {
            return new Parser(this);
        }

        /**
         * @param extensions extensions to use on this parser
         * @return {@code this}
         */
        public Builder extensions(Iterable<? extends Extension> extensions) {
            if (extensions == null) {
                throw new NullPointerException("extensions must not be null");
            }
            for (Extension extension : extensions) {
                if (extension instanceof ParserExtension) {
                    ParserExtension parserExtension = (ParserExtension) extension;
                    parserExtension.extend(this);
                }
            }
            return this;
        }

        /**
         * Describe the list of markdown features the parser will recognize and parse.
         * <p>
         * By default, CommonMark will recognize and parse the following set of "block" elements:
         * <ul>
         * <li>{@link Heading} ({@code #})
         * <li>{@link HtmlBlock} ({@code <html></html>})
         * <li>{@link ThematicBreak} (Horizontal Rule) ({@code ---})
         * <li>{@link FencedCodeBlock} ({@code ```})
         * <li>{@link IndentedCodeBlock}
         * <li>{@link BlockQuote} ({@code >})
         * <li>{@link ListBlock} (Ordered / Unordered List) ({@code 1. / *})
         * </ul>
         * <p>
         * To parse only a subset of the features listed above, pass a list of each feature's associated {@link Block} class.
         * <p>
         * E.g., to only parse headings and lists:
         * <pre>
         *     {@code
         *     Parser.builder().enabledBlockTypes(new HashSet<>(Arrays.asList(Heading.class, ListBlock.class)));
         *     }
         * </pre>
         *
         * @param enabledBlockTypes A list of block nodes the parser will parse.
         * If this list is empty, the parser will not recognize any CommonMark core features.
         * @return {@code this}
         */
        public Builder enabledBlockTypes(Set<Class<? extends Block>> enabledBlockTypes) {
            if (enabledBlockTypes == null) {
                throw new NullPointerException("enabledBlockTypes must not be null");
            }
            DocumentParser.checkEnabledBlockTypes(enabledBlockTypes);
            this.enabledBlockTypes = enabledBlockTypes;
            return this;
        }

        /**
         * Whether to calculate {@link org.commonmark.node.SourceSpan} for {@link Node}.
         * <p>
         * By default, source spans are disabled.
         *
         * @param includeSourceSpans which kind of source spans should be included
         * @return {@code this}
         * @since 0.16.0
         */
        public Builder includeSourceSpans(IncludeSourceSpans includeSourceSpans) {
            this.includeSourceSpans = includeSourceSpans;
            return this;
        }

        /**
         * Adds a custom block parser factory.
         * <p>
         * Note that custom factories are applied <em>before</em> the built-in factories. This is so that
         * extensions can change how some syntax is parsed that would otherwise be handled by built-in factories.
         * "With great power comes great responsibility."
         *
         * @param blockParserFactory a block parser factory implementation
         * @return {@code this}
         */
        public Builder customBlockParserFactory(BlockParserFactory blockParserFactory) {
            if (blockParserFactory == null) {
                throw new NullPointerException("blockParserFactory must not be null");
            }
            blockParserFactories.add(blockParserFactory);
            return this;
        }

        /**
         * Adds a custom delimiter processor.
         * <p>
         * Note that multiple delimiter processors with the same characters can be added, as long as they have a
         * different minimum length. In that case, the processor with the shortest matching length is used. Adding more
         * than one delimiter processor with the same character and minimum length is invalid.
         *
         * @param delimiterProcessor a delimiter processor implementation
         * @return {@code this}
         */
        public Builder customDelimiterProcessor(DelimiterProcessor delimiterProcessor) {
            if (delimiterProcessor == null) {
                throw new NullPointerException("delimiterProcessor must not be null");
            }
            delimiterProcessors.add(delimiterProcessor);
            return this;
        }

        public Builder postProcessor(PostProcessor postProcessor) {
            if (postProcessor == null) {
                throw new NullPointerException("postProcessor must not be null");
            }
            postProcessors.add(postProcessor);
            return this;
        }

        /**
         * Overrides the parser used for inline markdown processing.
         * <p>
         * Provide an implementation of InlineParserFactory which provides a custom inline parser
         * to modify how the following are parsed:
         * bold (**)
         * italic (*)
         * strikethrough (~~)
         * backtick quote (`)
         * link ([title](http://))
         * image (![alt](http://))
         * <p>
         * Note that if this method is not called or the inline parser factory is set to null, then the default
         * implementation will be used.
         *
         * @param inlineParserFactory an inline parser factory implementation
         * @return {@code this}
         */
        public Builder inlineParserFactory(InlineParserFactory inlineParserFactory) {
            this.inlineParserFactory = inlineParserFactory;
            return this;
        }

        private InlineParserFactory getInlineParserFactory() {
            if (inlineParserFactory != null) {
                return inlineParserFactory;
            }
            return new InlineParserFactory() {
                @Override
                public InlineParser create(InlineParserContext inlineParserContext) {
                    return new InlineParserImpl(inlineParserContext);
                }
            };
        }
    }

    /**
     * Extension for {@link Parser}.
     */
    public interface ParserExtension extends Extension {
        void extend(Builder parserBuilder);
    }
}
