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

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.SimpleElementVisitor14;

import com.sun.source.doctree.DocTree;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.formats.html.HtmlIds;
import jdk.javadoc.internal.doclets.formats.html.HtmlOptions;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * The taglet writer that writes HTML.
 */
public class TagletWriterImpl extends TagletWriter {

    private final HtmlDocletWriter htmlWriter;
    private final HtmlConfiguration configuration;
    private final HtmlOptions options;
    private final Utils utils;
    private final Resources resources;

    private final Context context;
    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter      the {@code HtmlDocletWriter} for the page
     * @param isFirstSentence {@code true} if this taglet writer is being used for a
     *                        "first sentence" summary
     */
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
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
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence, boolean inSummary) {
        this(htmlWriter, new Context(isFirstSentence, inSummary));
    }

    /**
     * Creates a taglet writer.
     *
     * @param htmlWriter the {@code HtmlDocletWriter} for the page
     * @param context    the enclosing context for any tags
     */
    public TagletWriterImpl(HtmlDocletWriter htmlWriter, Context context) {
        super(context);
        this.htmlWriter = htmlWriter;
        this.context = context;
        configuration = htmlWriter.configuration;
        options = configuration.getOptions();
        utils = configuration.utils;
        resources = configuration.getDocResources();
    }

    @Override
    public Content getOutputInstance() {
        return new ContentBuilder();
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

    @Override
    public Content invalidTagOutput(String summary, Optional<String> detail) {
        return htmlWriter.invalidTagOutput(summary,
                detail.isEmpty() || detail.get().isEmpty()
                        ? Optional.empty()
                        : Optional.of(Text.of(Text.normalizeNewlines(detail.get()))));
    }

    @Override
    public Content commentTagsToOutput(DocTree holder, List<? extends DocTree> tags) {
        return commentTagsToOutput(null, holder, tags, false);
    }

    @Override
    public Content commentTagsToOutput(Element element, List<? extends DocTree> tags) {
        return commentTagsToOutput(element, null, tags, false);
    }

    @Override
    public Content commentTagsToOutput(Element holder,
                                       DocTree holderTag,
                                       List<? extends DocTree> tags,
                                       boolean isFirstSentence)
    {
        return htmlWriter.commentTagsToContent(holder,
                tags, holderTag == null ? context : context.within(holderTag));
    }

    @Override
    public BaseConfiguration configuration() {
        return configuration;
    }

    @Override
    protected TypeElement getCurrentPageElement() {
        return htmlWriter.getCurrentPageElement();
    }

    public HtmlDocletWriter getHtmlWriter() {
        return htmlWriter;
    }

    public Utils getUtils() {
        return utils;
    }

    public Content createAnchorAndSearchIndex(Element element, String tagText, String desc, DocTree tree) {
        return createAnchorAndSearchIndex(element, tagText, Text.of(tagText), desc, tree);
    }

    @SuppressWarnings("preview")
    Content createAnchorAndSearchIndex(Element element, String tagText, Content tagContent, String desc, DocTree tree) {
        Content result;
        if (context.isFirstSentence && context.inSummary || context.inTags.contains(DocTree.Kind.INDEX)) {
            result = tagContent;
        } else {
            HtmlId id = HtmlIds.forText(tagText, htmlWriter.indexAnchorTable);
            result = HtmlTree.SPAN(id, HtmlStyle.searchTagResult, tagContent);
            if (options.createIndex() && !tagText.isEmpty()) {
                String holder = getHolderName(element);
                IndexItem item = IndexItem.of(element, tree, tagText, holder, desc,
                        new DocLink(htmlWriter.path, id.name()));
                configuration.mainIndex.add(item);
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
}
