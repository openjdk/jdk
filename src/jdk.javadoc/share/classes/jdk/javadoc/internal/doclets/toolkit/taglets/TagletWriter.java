/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.Taglet.UnsupportedTagletOperationException;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * The interface for the taglet writer.
 */
public abstract class TagletWriter {

    /**
     * A class that provides the information about the enclosing context for
     * a series of {@code DocTree} nodes.
     * This context may be used to determine the content that should be generated from the tree nodes.
     */
    public static class Context {
        /**
         * Whether the trees are appearing in a context of just the first sentence,
         * such as in the summary table of the enclosing element.
         */
        public final boolean isFirstSentence;
        /**
         * Whether the trees are appearing in the "summary" section of the
         * page for a declaration.
         */
        public final boolean inSummary;
        /**
         * The set of enclosing kinds of tags.
         */
        public final Set<DocTree.Kind> inTags;

        /**
         * Creates an outermost context, with no enclosing tags.
         *
         * @param isFirstSentence {@code true} if the trees are appearing in a context of just the
         *                        first sentence and {@code false} otherwise
         * @param inSummary       {@code true} if the trees are appearing in the "summary" section
         *                        of the page for a declaration and {@code false} otherwise
         */
        public Context(boolean isFirstSentence, boolean inSummary) {
            this(isFirstSentence, inSummary, EnumSet.noneOf(DocTree.Kind.class));
        }

        private Context(boolean isFirstSentence, boolean inSummary, Set<DocTree.Kind> inTags) {
            this.isFirstSentence = isFirstSentence;
            this.inSummary = inSummary;
            this.inTags = inTags;
        }

        /**
         * Creates a new {@code Context} that includes an extra tag kind in the set of enclosing
         * kinds of tags.
         *
         * @param tree the enclosing tree
         *
         * @return the new {@code Context}
         */
        public Context within(DocTree tree) {
            var newInTags = EnumSet.copyOf(inTags);
            newInTags.add(tree.getKind());
            return new Context(isFirstSentence, inSummary, newInTags);
        }
    }

    /**
     * The context in which to generate the output for a series of {@code DocTree} nodes.
     */
    public final Context context;

    protected TagletWriter(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    /**
     * Returns an instance of an output object.
     *
     * @return an instance of an output object
     */
    public abstract Content getOutputInstance();

    /**
     * Returns the output for an invalid tag. The returned content uses special styling to
     * highlight the problem. Depending on the presence of the {@code detail} string the method
     * returns a plain text span or an expandable component.
     *
     * @param summary the single-line summary message
     * @param detail the optional detail message which may contain preformatted text
     * @return the output
     */
    public abstract Content invalidTagOutput(String summary, Optional<String> detail);

    /**
     * Returns the main type element of the current page or null for pages that don't have one.
     *
     * @return the type element of the current page or null.
     */
    protected abstract TypeElement getCurrentPageElement();

    /**
     * Returns the content generated from the block tags for a given element.
     * The content is generated according to the order of the list of taglets.
     * The result is a possibly-empty list of the output generated by each
     * of the given taglets for all of the tags they individually support.
     *
     * @param tagletManager the manager that manages the taglets
     * @param element       the element that we are to write tags for
     * @param taglets       the taglets for the tags to write
     *
     * @return the content
     */
    public Content getBlockTagOutput(TagletManager tagletManager,
                                    Element element,
                                    List<Taglet> taglets) {
        for (Taglet t : taglets) {
            if (!t.isBlockTag()) {
                throw new IllegalArgumentException(t.getName());
            }
        }

        Content output = getOutputInstance();
        Utils utils = configuration().utils;
        tagletManager.checkTags(element, utils.getBlockTags(element));
        tagletManager.checkTags(element, utils.getFullBody(element));
        for (Taglet taglet : taglets) {
            if (utils.isTypeElement(element) && taglet instanceof ParamTaglet) {
                // The type parameters and state components are documented in a special
                // section away from the tag info, so skip here.
                continue;
            }

            if (element.getKind() == ElementKind.MODULE && taglet instanceof BaseTaglet t) {
                switch (t.getTagKind()) {
                    // @uses and @provides are handled separately, so skip here.
                    // See ModuleWriterImpl.computeModulesData
                    case USES:
                    case PROVIDES:
                        continue;
                }
            }

            if (taglet instanceof DeprecatedTaglet) {
                //Deprecated information is documented "inline", not in tag info
                //section.
                continue;
            }

            if (taglet instanceof SimpleTaglet st && !st.enabled) {
                // taglet has been disabled
                continue;
            }

            try {
                Content tagletOutput = taglet.getAllBlockTagOutput(element, this);
                if (tagletOutput != null) {
                    tagletManager.seenTag(taglet.getName());
                    output.add(tagletOutput);
                }
            } catch (UnsupportedTagletOperationException e) {
                // malformed taglet:
                // claims to support block tags (see Taglet.isBlockTag) but does not provide the
                // appropriate method, Taglet.getAllBlockTagOutput.
            }
        }
        return output;
    }

    /**
     * Returns the content generated from an inline tag in the doc comment for a given element,
     * or {@code null} if the tag is not supported or does not return any output.
     *
     * @param holder        the element associated with the doc comment
     * @param inlineTag     the inline tag to be documented
     *
     * @return the content, or {@code null}
     */
    public Content getInlineTagOutput(Element holder,
                                      DocTree inlineTag) {
        var config = configuration();
        var tagletManager = config.tagletManager;
        Map<String, Taglet> inlineTags = tagletManager.getInlineTaglets();
        CommentHelper ch = config.utils.getCommentHelper(holder);
        final String inlineTagName = ch.getTagName(inlineTag);
        Taglet t = inlineTags.get(inlineTagName);
        if (t == null) {
            return null;
        }

        try {
            Content tagletOutput = t.getInlineTagOutput(holder, inlineTag, this);
            tagletManager.seenTag(t.getName());
            return tagletOutput;
        } catch (UnsupportedTagletOperationException e) {
            // malformed taglet:
            // claims to support inline tags (see Taglet.isInlineTag) but does not provide the
            // appropriate method, Taglet.getInlineTagOutput.
            return null;
        }
    }

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param holderTree the tree that holds the documentation
     * @param trees      list of {@code DocTree} nodes containing text and inline tags (often alternating)
     *                   present in the text of interest for this doc
     *
     * @return the generated content
     */
    public abstract Content commentTagsToOutput(DocTree holderTree, List<? extends DocTree> trees);

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to block tags.
     *
     * @param element The element that owns the documentation
     * @param trees  list of {@code DocTree} nodes containing text and inline tags (often alternating)
     *               present in the text of interest for this doc
     *
     * @return the generated content
     */
    public abstract Content commentTagsToOutput(Element element, List<? extends DocTree> trees);

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param element         the element where comment resides
     * @param holder          the tag that holds the documentation
     * @param trees           array of text tags and inline tags (often alternating)
     *                        present in the text of interest for this doc
     * @param isFirstSentence true if this is the first sentence
     *
     * @return the generated content
     */
    public abstract Content commentTagsToOutput(Element element, DocTree holder,
                                                List<? extends DocTree> trees, boolean isFirstSentence);

    /**
     * Returns an instance of the configuration used for this doclet.
     *
     * @return an instance of the configuration used for this doclet
     */
    public abstract BaseConfiguration configuration();
}
