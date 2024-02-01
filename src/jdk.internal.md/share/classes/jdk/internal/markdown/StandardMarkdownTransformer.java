/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.regex.Pattern;

import com.sun.source.doctree.DocTree;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.DCTree;
import com.sun.tools.javac.tree.DocTreeMaker;
import jdk.internal.org.commonmark.node.AbstractVisitor;
import jdk.internal.org.commonmark.node.Link;
import jdk.internal.org.commonmark.node.Node;
import jdk.internal.org.commonmark.node.Text;

import static com.sun.tools.javac.util.Position.NOPOS;

/**
 * A class to transform a {@code DocTree} node into a similar one with
 * doc-comment "extensions" translated into equivalent standard {@code DocTree} nodes.
 *
 * <p>The primary extension is to allow references to program elements to be
 * translated to {@code {@link ...}} or {@code {@linkplain ...}} tags.
 */
public class StandardMarkdownTransformer extends MarkdownTransformer {

    /**
     * Public no-args constructor, suitable for use with {@link java.util.ServiceLoader}.
     */
    public StandardMarkdownTransformer() { }

    public String name() {
        return "standard";
    }

    protected DCTransformer createTransformer(JavacTrees t, DCTree.DCDocComment dc) {
        return new DCTransformer(t) {
            @Override
            protected List<DCTree> convert(Node document, String source, List<Object> replacements) {
                /*
                 * Step 3: Analyze the parsed tree, converting it back to a list of DocTree nodes,
                 *         consisting of Markdown text interspersed with any pre-existing
                 *         DocTree nodes, as well as any new nodes created by converting
                 *         parts of the Markdown tree into nodes for old-style javadoc tags.
                 */
                Lower v = new Lower(m, document, source, replacements);
                document.accept(v);

                return v.getTrees();
            }
        };
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
        Iterator<?> replaceIter;

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
         * @param replacements the objects to be substituted when U+FFFC is encountered
         */
        public Lower(DocTreeMaker docTreeMaker, Node document, String source, List<?> replacements) {
            this.m = docTreeMaker;
            this.document = document;
            this.source = source;

            var offsets = new ArrayList<Integer>();
            offsets.add(0);
            var m = lineBreak.matcher(source);
            while (m.find()) {
                offsets.add(m.end());
            }
            sourceLineOffsets = offsets.stream().mapToInt(Integer::intValue).toArray();

            replaceIter = replacements.iterator();

            trees = new ArrayList<>();
            text = new StringBuilder();
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
         * If the destination for the link begins with {@code code:}
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
            if (dest.startsWith(AUTOREF_PREFIX)) {
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
                    String ref = dest.substring(AUTOREF_PREFIX.length());
                    int refPos = sourcePosToTreePos(getRefPos(ref, link));
                    var newRefTree = m.at(refPos).newReferenceTree(ref).setEndPos(refPos + ref.length());

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
        private int getRefPos(String ref, Link link) {
            var spans = link.getSourceSpans();
            var revSpanIter = spans.listIterator(spans.size());
            while (revSpanIter.hasPrevious()) {
                var span = revSpanIter.previous();
                var start = toSourcePos(span.getLineIndex(), span.getColumnIndex());
                var end = toSourcePos(span.getLineIndex(), span.getColumnIndex() + span.getLength());
                var s = source.substring(start, end);
                var index = s.lastIndexOf(ref);
                if (index != -1) {
                    return start + index;
                }
            }
            return NOPOS;
        }

        /**
         * {@return the position in the original comment for a position in {@code source},
         * using {@link #replaceAdjustPos}}.
         *
         * @param pos the position in {@code source}
         */
        private int sourcePosToTreePos(int pos) {
            return pos == NOPOS ? NOPOS : pos + replaceAdjustPos;
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
            int pos;
            while ((pos = source.indexOf(PLACEHOLDER, startPos)) != -1 && pos < endPos) {
                text.append(source, startPos, pos);
                assert replaceIter.hasNext();
                Object r = replaceIter.next();
                if (r instanceof DCTree t) {
                    flushText(startPos);
                    trees.add(t);
                    replaceAdjustPos += t.getEndPosition() - t.getStartPosition() - 1;
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
            flushText(startPos);
        }

        /**
         * Flush any text in the {@code text} buffer, by creating a new
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
