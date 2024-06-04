/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.markdown;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTreeVisitor;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.parser.ReferenceParser;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.DocTreeMaker;
import com.sun.tools.javac.util.DefinedBy;

import jdk.internal.org.commonmark.ext.gfm.tables.TablesExtension;
import jdk.internal.org.commonmark.internal.InlineParserImpl;
import jdk.internal.org.commonmark.node.AbstractVisitor;
import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.LinkReferenceDefinition;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.node.Text;
import jdk.internal.org.commonmark.parser.IncludeSourceSpans;
import jdk.internal.org.commonmark.parser.InlineParser;
import jdk.internal.org.commonmark.parser.InlineParserContext;
import jdk.internal.org.commonmark.parser.InlineParserFactory;
import jdk.internal.org.commonmark.parser.Parser;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;

import static com.sun.tools.javac.util.Position.NOPOS;

/**
 * A class to transform a {@code DocTree} node into a similar one with
 * doc-comment "extensions" translated into equivalent standard {@code DocTree} nodes.
 *
 * <p>The primary extension is to allow references to program elements to be
 * translated to {@code {@link ...}} or {@code {@linkplain ...}} tags.
 */
public class MarkdownTransformer implements JavacTrees.DocCommentTreeTransformer {

    /**
     * Public no-args constructor, suitable for use with {@link java.util.ServiceLoader}.
     */
    public MarkdownTransformer() { }

    public String name() {
        return "standard";
    }

    @Override @DefinedBy(DefinedBy.Api.COMPILER_TREE)
    public DocCommentTree transform(DocTrees trees, DocCommentTree tree) {
        if (!(trees instanceof JavacTrees t)) {
            throw new IllegalArgumentException("class not supported: " + trees.getClass());
        }
        if (!(tree instanceof DCTree.DCDocComment dc)) {
            throw new IllegalArgumentException("class not supported: " + tree.getClass());
        }

        return isMarkdown(dc) ? new DCTransformer(t).transform(dc) : dc;
    }

    private boolean isMarkdown(DocCommentTree node) {
        return isMarkdownVisitor.visitDocComment(node, null);
    }

    /**
     * A fast scanner for detecting Markdown nodes in documentation comment nodes.
     * The scanner returns as soon as any Markdown node is found.
     */
    private static final DocTreeVisitor<Boolean, Void> isMarkdownVisitor = new DocTreeScanner<>() {
        @Override
        public Boolean scan(Iterable<? extends DocTree> nodes, Void ignore) {
            if (nodes != null) {
                for (DocTree node : nodes) {
                    Boolean b = scan(node, ignore);
                    if (b == Boolean.TRUE) {
                        return b;
                    }
                }
            }
            return false;
        }

        @Override
        public Boolean scan(DocTree node, Void ignore) {
            return node != null && node.getKind() == DocTree.Kind.MARKDOWN ? Boolean.TRUE : super.scan(node, ignore);
        }

        @Override
        public Boolean reduce(Boolean r1, Boolean r2) {
            return r1 == Boolean.TRUE || r2 == Boolean.TRUE;
        }
    };

    private static final char PLACEHOLDER = '\uFFFC'; // Unicode Object Replacement Character

    private static class DCTransformer {
        private final DocTreeMaker m;
        private final ReferenceParser refParser;

        // a dynamically generated scheme for the URLs of automatically generated references;
        // to allow user-generated code URLs, change this to just "code:"
        private final String autorefScheme = "code-" + Integer.toHexString(hashCode()) + ":";

        DCTransformer(JavacTrees t) {
            m = t.getDocTreeFactory();
            refParser = new ReferenceParser(t.getParserFactory());
        }

        /**
         * Transforms a doc tree node.
         * This node dispatches to a more specific overload, based on the kind of the node.
         * The result may be the same as the argument if no transformations were made.
         *
         * @param tree the tree node
         * @return a tree with any "extensions" converted into tags
         */
        public DCTree transform(DCTree tree) {
            // The following switch statement could eventually be converted to a
            // pattern switch. It is intended to be a total switch, with a default
            // to catch any omissions.
            return switch (tree.getKind()) {
                // The following cannot contain Markdown and so always transform to themselves.
                case ATTRIBUTE,
                        CODE, COMMENT,
                        DOC_ROOT, DOC_TYPE,
                        END_ELEMENT, ENTITY, ERRONEOUS, ESCAPE,
                        IDENTIFIER, INHERIT_DOC,
                        LITERAL,
                        REFERENCE,
                        SNIPPET, START_ELEMENT, SYSTEM_PROPERTY,
                        TEXT,
                        VALUE -> tree;

                // The following may contain Markdown in at least one of their fields.
                case AUTHOR -> transform((DCTree.DCAuthor) tree);
                case DEPRECATED -> transform((DCTree.DCDeprecated) tree);
                case DOC_COMMENT -> transform((DCTree.DCDocComment) tree);
                case EXCEPTION, THROWS -> transform((DCTree.DCThrows) tree);
                case HIDDEN -> transform((DCTree.DCHidden) tree);
                case INDEX -> transform((DCTree.DCIndex) tree);
                case LINK, LINK_PLAIN -> transform((DCTree.DCLink) tree);
                case PARAM -> transform((DCTree.DCParam) tree);
                case PROVIDES -> transform((DCTree.DCProvides) tree);
                case RETURN -> transform((DCTree.DCReturn) tree);
                case SEE -> transform((DCTree.DCSee) tree);
                case SERIAL -> transform((DCTree.DCSerial) tree);
                case SERIAL_DATA -> transform((DCTree.DCSerialData) tree);
                case SERIAL_FIELD -> transform((DCTree.DCSerialField) tree);
                case SINCE -> transform((DCTree.DCSince) tree);
                case SPEC -> transform((DCTree.DCSpec) tree);
                case SUMMARY -> transform((DCTree.DCSummary) tree);
                case UNKNOWN_BLOCK_TAG -> transform((DCTree.DCUnknownBlockTag) tree);
                case UNKNOWN_INLINE_TAG -> transform((DCTree.DCUnknownInlineTag) tree);
                case USES -> transform((DCTree.DCUses) tree);
                case VERSION -> transform((DCTree.DCVersion) tree);

                // This should never be handled directly; instead it should be handled as part of
                //   transform(List<? extends DocTree>);
                // because a Markdown node has the potential to be split into multiple nodes.
                case MARKDOWN -> throw new IllegalArgumentException(tree.getKind().toString());

                // Catch in case new kinds are added
                default -> throw new IllegalArgumentException(tree.getKind().toString());
            };
        }

        /**
         * Transforms a list of doc tree nodes.
         * If any of the nodes contain Markdown, the Markdown source is parsed
         * and analyzed for any transformations.
         * Any non-Markdown nodes are individually transformed.
         *
         * @param trees the list of tree nodes to be transformed
         * @return the transformed list
         */
        private List<? extends DCTree> transform(List<? extends DCTree> trees) {
            boolean hasMarkdown = trees.stream().anyMatch(t -> t.getKind() == DocTree.Kind.MARKDOWN);
            if (hasMarkdown) {
                var sourceBuilder = new StringBuilder();
                var replacements = new ArrayList<>();

                /*
                 * Step 1: Convert the trees into a string containing Markdown text,
                 *         using Unicode Object Replacement characters to mark the positions
                 *         of non-Markdown content.
                 */
                for (DCTree tree : trees) {
                    if (tree instanceof RawTextTree t) {
                        if (t.getKind() != DocTree.Kind.MARKDOWN) {
                            throw new IllegalStateException(t.getKind().toString());
                        }
                        String code = t.getContent();
                        // handle the (unlikely) case of any U+FFFC characters existing in the code
                        int start = 0;
                        int pos;
                        while ((pos = code.indexOf(PLACEHOLDER, start)) != -1) {
                            replacements.add(PLACEHOLDER);
                            start = pos + 1;
                        }
                        sourceBuilder.append(code);
                    } else {
                        replacements.add(transform(tree));
                        sourceBuilder.append(PLACEHOLDER);
                    }
                }

                /*
                 * Step 2: Build a parser, and configure it to accept additional syntactic constructs,
                 *         such as reference-style links to program elements.
                 */
                String source = sourceBuilder.toString();
                Parser parser = Parser.builder()
                        .extensions(List.of(TablesExtension.create()))
                        .inlineParserFactory(new AutoRefInlineParserFactory(refParser, autorefScheme))
                        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                        .build();
                Node document = parser.parse(source);

                /*
                 * Step 3: Analyze the parsed tree, converting it back to a list of DocTree nodes,
                 *         consisting of Markdown text interspersed with any pre-existing
                 *         DocTree nodes, as well as any new nodes created by converting
                 *         parts of the Markdown tree into nodes for old-style javadoc tags.
                 */
                var firstTreePos = trees.getFirst().getStartPosition();
                Lower v = new Lower(m, document, source, firstTreePos, replacements, autorefScheme);
                document.accept(v);

                return v.getTrees();

            } else {
                var list2 = trees.stream()
                        .map(this::transform)
                        .toList();
                return equal(list2, trees) ? trees : list2;
            }
        }

        //-----------------------------------------------------------------------------
        //
        // The following {@code transform} methods invoke {@code transform} on
        // any children that may contain Markdown. If the transformations on
        // the children are all identity transformations (that is the result
        // of the transformations are the same as the originals) then the
        // result of the overall transform is the original object. But if
        // any transformation on the children is not an identity transformation
        // then the result is a new node containing the transformed values.
        //
        // Thus, we only duplicate the parts of the tree that have changed,
        // and we do not duplicate the parts of the tree that have not changed.

        private DCTree.DCAuthor transform(DCTree.DCAuthor tree) {
            var name2 = transform(tree.name);
            return (equal(name2, tree.name))
                    ? tree
                    : m.at(tree.pos).newAuthorTree(name2);
        }

        private DCTree.DCDeprecated transform(DCTree.DCDeprecated tree) {
            var body2 = transform(tree.body);
            return (equal(body2, tree.body))
                    ? tree
                    : m.at(tree.pos).newDeprecatedTree(body2);
        }

        public DCTree.DCDocComment transform(DCTree.DCDocComment tree) {
            var fullBody2 = transform(tree.fullBody);
            var tags2 = transform(tree.tags);
            // Note: preamble and postamble only appear in HTML files, so should always be
            // null or empty for doc comments and/or Markdown files
            var pre2 = transform(tree.preamble);
            var post2 = transform(tree.postamble);
            return (equal(fullBody2, tree.fullBody) && equal(tags2, tree.tags)
                    && equal(pre2, tree.preamble) && equal(post2, tree.postamble))
                    ? tree
                    : m.at(tree.pos).newDocCommentTree(tree.comment, fullBody2, tags2, pre2, post2);
        }

        private DCTree.DCHidden transform(DCTree.DCHidden tree) {
            var body2 = transform(tree.body);
            return (equal(body2, tree.body))
                    ? tree
                    : m.at(tree.pos).newHiddenTree(body2);
        }

        private DCTree.DCIndex transform(DCTree.DCIndex tree) {
            // The public API permits a DocTree, although in the implementation, it is always a TextTree.
            var term2 = transform(tree.term);
            var desc2 = transform(tree.description);
            return (equal(term2, tree.term) && equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newIndexTree(term2, desc2).setEndPos(tree.getEndPos());
        }

        private DCTree.DCLink transform(DCTree.DCLink tree) {
            var label2 = transform(tree.label);
            return (equal(label2, tree.label))
                    ? tree
                    : switch (tree.getKind()) {
                case LINK -> m.at(tree.pos).newLinkTree(tree.ref, label2).setEndPos(tree.getEndPos());
                case LINK_PLAIN -> m.at(tree.pos).newLinkPlainTree(tree.ref, label2).setEndPos(tree.getEndPos());
                default -> throw new IllegalArgumentException(tree.getKind().toString());
            };
        }

        private DCTree.DCParam transform(DCTree.DCParam tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newParamTree(tree.isTypeParameter, tree.name, desc2);
        }

        private DCTree.DCProvides transform(DCTree.DCProvides tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newProvidesTree(tree.serviceType, desc2);
        }

        private DCTree.DCReturn transform(DCTree.DCReturn tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newReturnTree(tree.inline, desc2).setEndPos(tree.getEndPos());
        }

        private DCTree.DCSee transform(DCTree.DCSee tree) {
            List<? extends DocTree> ref2 = transform(tree.reference);
            return (equal(ref2, tree.getReference()))
                    ? tree
                    : m.at(tree.pos).newSeeTree(ref2);
        }

        private DCTree.DCSerial transform(DCTree.DCSerial tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newSerialTree(desc2);
        }

        private DCTree.DCSerialData transform(DCTree.DCSerialData tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newSerialDataTree(desc2);
        }

        private DCTree.DCSerialField transform(DCTree.DCSerialField tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newSerialFieldTree(tree.name, tree.type, desc2);
        }

        DCTree.DCSince transform(DCTree.DCSince tree) {
            var body2 = transform(tree.body);
            return (equal(body2, tree.body))
                    ? tree
                    : m.at(tree.pos).newSinceTree(body2);
        }

        private DCTree.DCSpec transform(DCTree.DCSpec tree) {
            var title2 = transform(tree.title);
            return (equal(title2, tree.title))
                    ? tree
                    : m.at(tree.pos).newSpecTree(tree.uri, title2);
        }

        private DCTree.DCSummary transform(DCTree.DCSummary tree) {
            var summ2 = transform(tree.summary);
            return (equal(summ2, tree.summary))
                    ? tree
                    : m.at(tree.pos).newSummaryTree(summ2).setEndPos(tree.getEndPos());
        }

        private DCTree.DCThrows transform(DCTree.DCThrows tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : switch (tree.getKind()) {
                case EXCEPTION -> m.at(tree.pos).newExceptionTree(tree.name, desc2);
                case THROWS -> m.at(tree.pos).newThrowsTree(tree.name, desc2);
                default -> throw new IllegalArgumentException(tree.getKind().toString());
            };
        }

        private DCTree.DCUnknownBlockTag transform(DCTree.DCUnknownBlockTag tree) {
            var cont2 = transform(tree.content);
            return (equal(cont2, tree.content))
                    ? tree
                    : m.at(tree.pos).newUnknownBlockTagTree(tree.name, cont2);
        }

        private DCTree.DCUnknownInlineTag transform(DCTree.DCUnknownInlineTag tree) {
            var cont2 = transform(tree.content);
            return (equal(cont2, tree.content))
                    ? tree
                    : m.at(tree.pos).newUnknownInlineTagTree(tree.name, cont2).setEndPos(tree.getEndPos());
        }

        private DCTree.DCUses transform(DCTree.DCUses tree) {
            var desc2 = transform(tree.description);
            return (equal(desc2, tree.description))
                    ? tree
                    : m.at(tree.pos).newUsesTree(tree.serviceType, desc2);
        }

        private DCTree.DCVersion transform(DCTree.DCVersion tree) {
            var body2 = transform(tree.body);
            return (equal(body2, tree.body))
                    ? tree
                    : m.at(tree.pos).newVersionTree(body2);
        }

        /**
         * Shallow "equals" for two doc tree nodes.
         *
         * @param <T> the type of the items
         * @param item1 the first item
         * @param item2 the second item
         * @return {@code true} if the items are reference-equal, and {@code false} otherwise
         */
        private static <T extends DocTree> boolean equal(T item1, T item2) {
            return item1 == item2;
        }

        /**
         * Shallow "equals" for two lists of doc tree nodes.
         *
         * @param <T> the type of the items
         * @param list1 the first item
         * @param list2 the second item
         * @return {@code true} if the items are reference-equal, and {@code false} otherwise
         */
        private static <T extends DocTree> boolean equal(List<? extends T> list1, List<? extends T> list2) {
            if (list1 == null || list2 == null) {
                return (list1 == list2);
            }

            if (list1.size() != list2.size()) {
                return false;
            }

            var iter1 = list1.iterator();
            var iter2 = list2.iterator();
            while (iter1.hasNext()) {
                var item1 = iter1.next();
                var item2 = iter2.next();
                if (item1 != item2) {
                    return false;
                }
            }

            return true;
        }
    }

    /**
     * An {@code InlineParserFactory} that checks any unresolved link references in
     * reference links. If an unresolved link reference appears to be a reference to a
     * program element, such as may be used in {@code @see ...} or {@code {@link ...}}
     * tags, it generates a synthetic {@code LinkReferenceDefinition}.
     *
     * The reference is not validated to ensure it refers to any existing element.
     *
     * The primary function of this {@code InlineParserFactory} is to provide
     * a custom implementation of an {@code InlineParserContext} via the
     * {@code create(InlineParserContext inlineParserContext)} method.
     * It is this {@code InlineParserContext} that actually manages the set
     * of link reference definitions.
     *
     * This {@code InlineParserFactory} is intended to be registered with a
     * {@code Parser.Builder} using {@code setInlineParserFactory}.
     */
    private static class AutoRefInlineParserFactory implements InlineParserFactory {
        private final ReferenceParser refParser;
        private final String autorefScheme;

        AutoRefInlineParserFactory(ReferenceParser refParser,
                                   String autorefScheme) {
            this.refParser = refParser;
            this.autorefScheme = autorefScheme;
        }

        /**
         * Creates a parser with a modified {@code InlineParserContext} that
         * delegates to the standard {@code InlineParserContext} and also
         * checks unresolved link references.
         *
         * @param inlineParserContext the standard {@code InlineParserContext}
         * @return the {@code InlineParser}
         */
        @Override
        public InlineParser create(InlineParserContext inlineParserContext) {
            return new InlineParserImpl(new InlineParserContext() {

                @Override
                public List<DelimiterProcessor> getCustomDelimiterProcessors() {
                    return inlineParserContext.getCustomDelimiterProcessors();
                }

                /**
                 * {@inheritDoc}
                 *
                 * If the given label does not match any explicitly defined
                 * link reference definition, but does match a reference
                 * to a program element, a synthetic link reference definition
                 * is returned, with the label prefixed by the {@code autorefScheme}.
                 *
                 * @param label the link label to look up
                 * @return the link reference definition for the label, or {@code null}
                 */
                @Override
                public LinkReferenceDefinition getLinkReferenceDefinition(String label) {
                    // In CommonMark, square brackets characters need to be escaped within a link label.
                    // See https://spec.commonmark.org/0.30/#link-label
                    //   Unescaped square bracket characters are not allowed inside the opening
                    //   and closing square brackets of link labels.
                    // The escape characters are still present here in the label,
                    // so we remove them before creating the autoref URL.
                    // Note that the characters always appear together as a pair in API references.
                    var l = label.replace("\\[\\]", "[]");
                    var d = inlineParserContext.getLinkReferenceDefinition(l);
                    return d == null && isReference(l)
                            ? new LinkReferenceDefinition("", autorefScheme + l, "")
                            : d;
                }
            });
        }

        /**
         * {@return whether a string appears to be a reference to a program element}
         *
         * @param s the string
         */
        private boolean isReference(String s) {
            try {
                refParser.parse(s, ReferenceParser.Mode.MEMBER_OPTIONAL);
                return true;
            } catch (ReferenceParser.ParseException e) {
                return false;
            }
        }
    }

    /**
     * A visitor to scan a Markdown document looking for any constructs that
     * should be mapped to doc comment nodes.
     *
     * The intended use is as follows:
     * {@snippet
     *     Lower v = new Lower(document, source, replacements);
     *     document.accept(v);
     *     var result = v.getTrees();
     * }
     *
     * On coordinates ...
     *
     * There are various position/coordinate systems in play in this code:
     *
     * 1. Tree positions, which correspond to positions in the original comment,
     *    represented as a 0-based character offset within the content of the comment.
     * 2. Positions in the source string given to the Markdown parser,
     *    represented as a 0-based character offset within the source string.
     *    These differ from positions in the original comment if the source string
     *    contains U+FFFC characters representing embedded tree nodes.
     * 3. Positions in the source string given to the Markdown parser,
     *    represented as (line, column) values within SourceSpan nodes.
     *    Both line and column are 0-based.
     *    Note: SourceSpan objects never include newline characters.
     *
     * See {@link Lower#toSourcePos(int, int)} to convert from (line, column)
     * coordinates to a source position.
     * See {@link Lower#sourcePosToTreePos(int)} to convert from a source
     * position to a position in the original comment, for use in tree nodes.
     */

    // Future opportunity:
    //
    // It would be possible to override {@code visit(Heading)} and
    // check for certain recognized headings, and for those headings,
    // check the content that follows up to but not including
    // the next heading at the same or higher level, or end of text.
    // When a match occurs, the heading and the content could be translated
    // into one or more block tags. Note that any such block tags should
    // probably be stored separately from {@code trees}, and handled
    // appropriately by the caller of this method, either by accepting
    // such tags, or by reporting an error if they are not applicable.
    //
    // A variant would be to pass in a boolean parameter to indicate
    // whether such tags would be acceptable if found, and to ignore
    // the heading and content if the block tag would not be acceptable.
    //
    // If a heading and any following content is converted to a block tag,
    // the content should probably be removed from the tree, so that
    // it is not handled by the call of {@code visitChildren} that
    // invoked {@code visit(Heading)}.
    //
    // Overall, this would allow Markdown constructs such as the following:
    //
    // /// Method description.
    // ///
    // /// # Parameters
    // /// * args
    // ///
    // /// # Returns
    // ///   a result
    // ///
    // /// # Throws
    // /// * IOException if an error occurs
    //
    // That being said, is it so much better than using standard block tags?
    // It is somewhat more concise for any repeatable block tag, if we
    // leverage the idea of a heading followed by a list.
    private static class Lower extends AbstractVisitor {
        private final DocTreeMaker m;
        private final String autorefScheme;

        /**
         * The Markdown document being processed.
         */
        private final Node document;

        /**
         * The source for the Markdown document being processed.
         * The document has "source spans" that indirectly point into this string
         * using {@code (line, column)} coordinates.
         * @see #toSourcePos(int, int)
         */
        private final String source;

        /**
         * An array giving the position of the first character after each newline
         * in the source.
         * Used to convert {@code (line, column)} coordinates to a character offset
         * in {@code source}.
         */
        private final int[] sourceLineOffsets;

        /**
         * An iterator containing the values to be substituted into the generated
         * tree when U+FFFC characters are encountered in the document.
         * There is a 1-1 correspondence between the values returned by the
         * iterator and the U+FFFC characters in the document.
         * The replacement objects may be either {@code DCTree} objects or
         * U+FFFC characters that were found in the original document.
         */
        private final Iterator<?> replaceIter;

        /**
         * The list of trees being built.
         * It may be temporarily replaced while visiting the children of
         * {@code Link} codes.
         */
        private List<DCTree> trees;

        /**
         * The source text being accumulated, prior to being placed in a
         * Markdown source node ({@code RawTextTree} with kind {@code MARKDOWN}).
         */
        private final StringBuilder text;

        /**
         * The initial position in the enclosing comment of the source
         * being transformed.
         */
        private int mainStartPos;

        /**
         * The start of source text to be copied literally when required.
         */
        private int copyStartPos;

        /**
         * The current adjustment from positions in {@code source} to positions
         * in the original comment, as used in doc tree nodes.
         * The difference arises because of the use of U+FFFC characters for
         * embedded objects. As the document (and source) are scanned,
         * this offset is updated when U+FFFC characters are encountered.
         */
        private int replaceAdjustPos;

        /**
         * The pattern for a line break.
         * This is equivalent to the detection in the Markdown parser, so that
         * {@code (line, column)} coordinates can be accurately converted back
         * into source positions.
         *
         * @see jdk.internal.org.commonmark.internal.DocumentParser#parse(String)
         * @see jdk.internal.org.commonmark.internal.util.Parsing#findLineBreak(CharSequence, int)
         */
        private static final Pattern lineBreak = Pattern.compile("\n|\r\n?");

        /**
         * Creates an instance of the visitor, given a document,
         * the source text for the document and objects to be substituted
         * when U+FFFC characters are encountered.
         *
         * Note the document does not itself contain any source text:
         * it just contains {@code SourceSpan} objects that point into
         * the source text using line and column indices.
         *
         * @param document the document to be converted
         * @param source the source of the document to be converted
         * @param sourcePos the position in the enclosing comment of the source to be converted
         * @param replacements the objects to be substituted when U+FFFC is encountered
         * @param autorefScheme the scheme used for auto-generated references
         */
        public Lower(DocTreeMaker docTreeMaker,
                     Node document,
                     String source, int sourcePos,
                     List<?> replacements,
                     String autorefScheme) {
            this.m = docTreeMaker;
            this.document = document;
            this.source = source;
            this.autorefScheme = autorefScheme;

            sourceLineOffsets = Stream.concat(
                            Stream.of(0),
                            lineBreak.matcher(source).results().map(MatchResult::end))
                    .mapToInt(Integer::intValue)
                    .toArray();

            replaceIter = replacements.iterator();

            trees = new ArrayList<>();
            text = new StringBuilder();
            mainStartPos = sourcePos;
            copyStartPos = 0;
            replaceAdjustPos = 0;
        }

        /**
         * {@return the trees that were built after using the visitor}
         */
        public List<DCTree> getTrees() {
            return trees;
        }

        /**
         * Visits a CommonMark {@code Link} node.
         *
         * If the destination for the link begins with the {@code autorefScheme}
         * convert it to {@code {@link ...}} or {@code {@linkplain ...}} DocTree node.
         * {@code {@link ...}} will be used if the content (label) for
         * the link is the same as the reference found after the {@code code:};
         * otherwise, {@code {@linkplain ...}} will be used.
         *
         * The label will be left blank for {@code {@link ...}} nodes,
         * implying that a default label should be used, based on the
         * program element that was referenced.
         *
         * @param link the link node
         */
        @Override
        public void visit(Link link) {
            String dest = link.getDestination();
            if (dest.startsWith(autorefScheme)) {
                // copy the source text up to the start of the node
                copyTo(getStartPos(link));
                // push temporary value for {@code trees} while handling the content of the node
                var saveTrees = trees;
                trees = new ArrayList<>();
                try {
                    copyStartPos = getStartPos(link.getFirstChild());
                    visitChildren(link);
                    copyTo(getEndPos(link.getLastChild()));

                    // determine whether to use {@link ... } or {@linkplain ...}
                    // based on whether the "link text" is the same as the "link destination"
                    String ref = dest.substring(autorefScheme.length());
                    int[] span = getRefSpan(ref, link);
                    int refPos = sourcePosToTreePos(span[0]);
                    var newRefTree = m.at(refPos).newReferenceTree(ref).setEndPos(sourcePosToTreePos(span[1]));

                    Node child = link.getFirstChild();
                    DocTree.Kind linkKind = child.getNext() == null
                            && child instanceof Text t
                            && t.getLiteral().equals(ref) ? DocTree.Kind.LINK : DocTree.Kind.LINK_PLAIN;

                    DCTree newLinkTree = linkKind == DocTree.Kind.LINK
                            ? m.at(NOPOS).newLinkTree(newRefTree, List.of()) // ignore the child trees
                            : m.at(NOPOS).newLinkPlainTree(newRefTree, trees);

                    saveTrees.add(newLinkTree);
                } finally {
                    // start any subsequent copy after the end of the link node
                    copyStartPos = getEndPos(link);
                    trees = saveTrees;
                }
            } else {
                visitChildren(link);
            }
        }

        /**
         * {@return the position in the source for the reference in a link}
         * Many syntactic forms may yield a {@code Link} object, so scan the
         * source looking for a match. Since the reference typically comes
         * after any text (when they are different), scan the source backwards.
         *
         * @param ref the reference to find
         * @param link the link containing the reference
         */
        private int[] getRefSpan(String ref, Link link) {
            var spans = link.getSourceSpans();
            var revSpanIter = spans.listIterator(spans.size());
            while (revSpanIter.hasPrevious()) {
                var span = revSpanIter.previous();
                var start = toSourcePos(span.getLineIndex(), span.getColumnIndex());
                var end = toSourcePos(span.getLineIndex(), span.getColumnIndex() + span.getLength());
                var s = source.substring(start, end);
                var index = s.lastIndexOf(ref);
                if (index != -1) {
                    return new int[] {start + index, start + index + ref.length()};
                } else {
                    String escapedRef = ref.replace("[]", "\\[\\]");
                    var escapedIndex = s.lastIndexOf(escapedRef);
                    if (escapedIndex != -1) {
                        return new int[] {start + escapedIndex,
                                          start + escapedIndex + escapedRef.length()};
                    }
                }
            }
            return NOSPAN;
        }
            private static final int[] NOSPAN = new int[] {NOPOS, NOPOS};

        /**
         * {@return the position in the original comment for a position in {@code source},
         * using {@link #replaceAdjustPos}}
         *
         * @param pos the position in {@code source}
         */
        private int sourcePosToTreePos(int pos) {
            return pos == NOPOS ? NOPOS : mainStartPos + pos + replaceAdjustPos;
        }

        /**
         * Processes a node and any children.
         *
         * If the node has children, the children are each visited by
         * calling their {@code accept} method, and then finally, if this
         * is the top-level {@code document} node, any pending text is
         * flushed.
         *
         * If the node does not have children, the source spans for
         * the node are processed instead.
         *
         * Note that unlike the default implementation of {@code visitChildren},
         * the next child is not accessed until after the current child
         * has been visited.  This allows a child to peek at and possibly remove
         * any child nodes that may follow it.
         *
         * @param node the node whose children should be visited
         */
        @Override
        protected void visitChildren(Node node) {
            Node child = node.getFirstChild();
            if (child != null) {
                while (child != null) {
                    // defer getting the next child until after this node has
                    // been processed, in case any following nodes were handled
                    // by this node, and removed from the document
                    child.accept(this);
                    child = child.getNext();
                }
                if (node == document) {
                    // the top level document has no spans of its own, so use the last child
                    copyTo(getEndPos(document.getLastChild()));
                }
            }
        }

        /**
         * {@return the start of this node, from the start of the first span}
         *
         * @param node the node
         */
        private int getStartPos(Node node) {
            var spans = node.getSourceSpans();
            var firstSpan = spans.getFirst();
            return toSourcePos(firstSpan.getLineIndex(), firstSpan.getColumnIndex());
        }

        /**
         * {@return the end of this node, from the end of the last span}
         * The end points to the first character not included in the span.
         *
         * @param node the node
         */
        private int getEndPos(Node node) {
            var spans = node.getSourceSpans();
            var lastSpan = spans.getLast();
            return toSourcePos(lastSpan.getLineIndex(), lastSpan.getColumnIndex() + lastSpan.getLength());
        }

        /**
         * {@return the position in the {@code source} string for a given {@code (line, column}}
         *
         * @param lineIndex the line index
         * @param columnIndex the column index
         */
        private int toSourcePos(int lineIndex, int columnIndex) {
            return sourceLineOffsets[lineIndex] + columnIndex;
        }

        /**
         * Copies source text from the saved copy-start position to the given end position
         * using the saved {@code source}, to the list of {@code trees}.
         *
         * @param endPos the end position
         */
        private void copyTo(int endPos) {
            int startPos = copyStartPos;
            int rawTextStartPos = copyStartPos;
            int pos;
            while ((pos = source.indexOf(PLACEHOLDER, startPos)) != -1 && pos < endPos) {
                text.append(source, startPos, pos);
                assert replaceIter.hasNext();
                Object r = replaceIter.next();
                if (r instanceof DCTree t) {
                    flushText(rawTextStartPos);
                    trees.add(t);
                    replaceAdjustPos += t.getEndPosition() - t.getStartPosition() - 1;
                    rawTextStartPos = pos + 1;
                } else if (r.equals(PLACEHOLDER)) {
                    text.append(PLACEHOLDER);
                } else {
                    throw new IllegalStateException(r.getClass().toString());
                }
                startPos = pos + 1;
            }
            if (startPos < endPos) {
                text.append(source, startPos, endPos);
            }
            flushText(rawTextStartPos);
        }

        /**
         * Flushes any text in the {@code text} buffer, by creating a new
         * Markdown source text node and adding it to the list of trees.
         */
        private void flushText(int pos) {
            if (!text.isEmpty()) {
                trees.add(m.at(sourcePosToTreePos(pos)).newRawTextTree(DocTree.Kind.MARKDOWN, text.toString()));
                text.setLength(0);
            }
        }
    }
}
