/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor14;

import com.sun.source.doctree.DocTree;

import com.sun.source.doctree.InlineTagTree;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.formats.html.HtmlIds;
import jdk.javadoc.internal.doclets.formats.html.HtmlOptions;
import jdk.javadoc.internal.doclets.formats.html.IndexWriter;
import jdk.javadoc.internal.doclets.formats.html.SummaryListWriter;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.formats.html.Content;
import jdk.javadoc.internal.doclets.formats.html.taglets.Taglet.UnsupportedTagletOperationException;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * Context and utility methods for taglet classes.
 */
public class TagletWriter {

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

    public final HtmlDocletWriter htmlWriter;
    public final HtmlConfiguration configuration;
    public final HtmlOptions options;
    public final Utils utils;
    public final Resources resources;

    /**
     * The context in which to generate the output for a series of {@code DocTree} nodes.
     */
    public final Context context;
    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter      the {@code HtmlDocletWriter} for the page
     * @param isFirstSentence {@code true} if this taglet writer is being used for a
     *                        "first sentence" summary
     */
    public TagletWriter(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        this(htmlWriter, isFirstSentence, false);
    }

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter      the {@code HtmlDocletWriter} for the page
     * @param isFirstSentence {@code true} if this taglet writer is being used for a
     *                        "first sentence" summary, and {@code false} otherwise
     * @param inSummary       {@code true} if this taglet writer is being used for the content
     *                        of a {@code {@summary ...}} tag, and {@code false} otherwise
     */
    public TagletWriter(HtmlDocletWriter htmlWriter, boolean isFirstSentence, boolean inSummary) {
        this(htmlWriter, new Context(isFirstSentence, inSummary));
    }

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter the {@code HtmlDocletWriter} for the page
     * @param context    the enclosing context for any tags
     */
    public TagletWriter(HtmlDocletWriter htmlWriter, Context context) {
        this.htmlWriter = Objects.requireNonNull(htmlWriter);
        this.context = Objects.requireNonNull(context);
        configuration = htmlWriter.configuration;
        options = configuration.getOptions();
        utils = configuration.utils;
        resources = configuration.getDocResources();
    }

    public Context getContext() {
        return context;
    }

    /**
     * Returns an instance of an output object.
     *
     * @return an instance of an output object
     */
    public Content getOutputInstance() {
        return new ContentBuilder();
    }

    /**
     * Returns the output for an invalid tag. The returned content uses special styling to
     * highlight the problem. Depending on the presence of the {@code detail} string the method
     * returns a plain text span or an expandable component.
     *
     * @param summary the single-line summary message
     * @param detail the optional detail message which may contain preformatted text
     * @return the output
     */
    public Content invalidTagOutput(String summary, Optional<String> detail) {
        return htmlWriter.invalidTagOutput(summary,
                detail.isEmpty() || detail.get().isEmpty()
                        ? Optional.empty()
                        : Optional.of(Text.of(Text.normalizeNewlines(detail.get()))));
    }

    /**
     * Returns the main type element of the current page or null for pages that don't have one.
     *
     * @return the type element of the current page or null.
     */
    public TypeElement getCurrentPageElement() {
        return htmlWriter.getCurrentPageElement();
    }

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

            if (taglet instanceof SimpleTaglet st && !st.isEnabled()) {
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
                                      InlineTagTree inlineTag) {
        var tagletManager = configuration.tagletManager;
        Map<String, Taglet> inlineTags = tagletManager.getInlineTaglets();
        CommentHelper ch = configuration.utils.getCommentHelper(holder);
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
    public Content commentTagsToOutput(DocTree holderTree, List<? extends DocTree> trees) {
        return commentTagsToOutput(null, holderTree, trees, false);
    }

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
    public Content commentTagsToOutput(Element element, List<? extends DocTree> trees) {
        return commentTagsToOutput(element, null, trees, false);
    }

    /**
     * Converts inline tags and text to content, expanding the
     * inline tags along the way.  Called wherever text can contain
     * an inline tag, such as in comments or in free-form text arguments
     * to non-inline tags.
     *
     * @param element          the element where comment resides
     * @param holder       the tag that holds the documentation
     * @param trees           array of text tags and inline tags (often alternating)
     *                        present in the text of interest for this doc
     * @param isFirstSentence true if this is the first sentence
     *
     * @return the generated content
     */
    public Content commentTagsToOutput(Element element,
                                       DocTree holder,
                                       List<? extends DocTree> trees,
                                       boolean isFirstSentence)
    {
        return htmlWriter.commentTagsToContent(element,
                trees, holder == null ? context : context.within(holder));
    }

    public Content createAnchorAndSearchIndex(Element element, String tagText, String desc, DocTree tree) {
        return createAnchorAndSearchIndex(element, tagText, Text.of(tagText), desc, tree);
    }

    @SuppressWarnings("preview")
    Content createAnchorAndSearchIndex(Element element, String tagText, Content tagContent, String desc, DocTree tree) {
        Content result;
        if (context.isFirstSentence && context.inSummary || context.inTags.contains(DocTree.Kind.INDEX)
                || !htmlWriter.isIndexable()) {
            result = tagContent;
        } else {
            HtmlId id = HtmlIds.forText(tagText, htmlWriter.indexAnchorTable);
            result = HtmlTree.SPAN(id, HtmlStyle.searchTagResult, tagContent);
            if (options.createIndex() && !tagText.isEmpty()) {
                String holder = getHolderName(element);
                IndexItem item = IndexItem.of(element, tree, tagText, holder, desc,
                        new DocLink(htmlWriter.path, id.name()));
                configuration.indexBuilder.add(item);
            }
        }
        return result;
    }

    public String getHolderName(Element element) {
        return new SimpleElementVisitor14<String, Void>() {

            @Override
            public String visitModule(ModuleElement e, Void p) {
                return resources.getText("doclet.module")
                        + " " + utils.getFullyQualifiedName(e);
            }

            @Override
            public String visitPackage(PackageElement e, Void p) {
                return resources.getText("doclet.package")
                        + " " + utils.getFullyQualifiedName(e);
            }

            @Override
            public String visitType(TypeElement e, Void p) {
                return utils.getTypeElementKindName(e, true)
                        + " " + utils.getFullyQualifiedName(e);
            }

            @Override
            public String visitExecutable(ExecutableElement e, Void p) {
                return utils.getFullyQualifiedName(utils.getEnclosingTypeElement(e))
                        + "." + utils.getSimpleName(e)
                        + utils.flatSignature(e, htmlWriter.getCurrentPageElement());
            }

            @Override
            public String visitVariable(VariableElement e, Void p) {
                return utils.getFullyQualifiedName(utils.getEnclosingTypeElement(e))
                        + "." + utils.getSimpleName(e);
            }

            @Override
            public String visitUnknown(Element e, Void p) {
                if (e instanceof DocletElement de) {
                    return switch (de.getSubKind()) {
                        case OVERVIEW -> resources.getText("doclet.Overview");
                        case DOCFILE -> getHolderName(de);
                    };
                } else {
                    return super.visitUnknown(e, p);
                }
            }

            @Override
            protected String defaultAction(Element e, Void p) {
                return utils.getFullyQualifiedName(e);
            }
        }.visit(element);
    }

    private String getHolderName(DocletElement de) {
        PackageElement pe = de.getPackageElement();
        if (pe.isUnnamed()) {
            // if package is unnamed use enclosing module only if it is named
            Element ee = pe.getEnclosingElement();
            if (ee instanceof ModuleElement && !((ModuleElement)ee).isUnnamed()) {
                return resources.getText("doclet.module") + " " + utils.getFullyQualifiedName(ee);
            }
            return pe.toString(); // "Unnamed package" or similar
        }
        return resources.getText("doclet.package") + " " + utils.getFullyQualifiedName(pe);
    }

    Content tagList(List<Content> items) {
        // Use a different style if any list item is longer than 30 chars or contains commas.
        boolean hasLongLabels = items.stream().anyMatch(this::isLongOrHasComma);
        var list = HtmlTree.UL(hasLongLabels ? HtmlStyle.tagListLong : HtmlStyle.tagList);
        items.stream()
                .filter(Predicate.not(Content::isEmpty))
                .forEach(item -> list.add(HtmlTree.LI(item)));
        return list;
    }

    // Threshold for length of list item for switching from inline to block layout.
    private static final int TAG_LIST_ITEM_MAX_INLINE_LENGTH = 30;

    private boolean isLongOrHasComma(Content c) {
        String s = c.toString()
                .replaceAll("<.*?>", "")              // ignore HTML
                .replaceAll("&#?[A-Za-z0-9]+;", " ")  // entities count as a single character
                .replaceAll("\\R", "\n");             // normalize newlines
        return s.length() > TAG_LIST_ITEM_MAX_INLINE_LENGTH || s.contains(",");
    }
}
