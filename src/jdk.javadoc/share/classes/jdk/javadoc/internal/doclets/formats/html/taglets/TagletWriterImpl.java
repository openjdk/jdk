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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor14;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.TextTree;

import jdk.javadoc.internal.doclets.formats.html.ClassWriterImpl;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.formats.html.HtmlIds;
import jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo;
import jdk.javadoc.internal.doclets.formats.html.HtmlOptions;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlId;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.DocletElement;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.Resources;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.IndexItem;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;

/**
 * The taglet writer that writes HTML.
 */
public class TagletWriterImpl extends TagletWriter {

    private final HtmlDocletWriter htmlWriter;
    private final HtmlConfiguration configuration;
    private final HtmlOptions options;
    private final Utils utils;
    private final Resources resources;

    private final Contents contents;
    private final Context context;

    // Threshold for length of @see tag label for switching from inline to block layout.
    private static final int TAG_LIST_ITEM_MAX_INLINE_LENGTH = 30;

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
        contents = configuration.getContents();
    }

    @Override
    public Content getOutputInstance() {
        return new ContentBuilder();
    }

    private boolean isLongOrHasComma(Content c) {
        String s = c.toString()
                .replaceAll("<.*?>", "")              // ignore HTML
                .replaceAll("&#?[A-Za-z0-9]+;", " ")  // entities count as a single character
                .replaceAll("\\R", "\n");             // normalize newlines
        return s.length() > TAG_LIST_ITEM_MAX_INLINE_LENGTH || s.contains(",");
    }

    String textOf(List<? extends DocTree> trees) {
        return trees.stream()
                .filter(dt -> dt instanceof TextTree)
                .map(dt -> ((TextTree) dt).getBody().trim())
                .collect(Collectors.joining(" "));
    }

    /**
     * Worker method to generate a link from the information in different kinds of tags,
     * such as {@code {@link ...}} tags, {@code @see ...} tags and the {@code link} markup tag
     * in a {@code {@snippet ...}} tag.
     *
     * @param holder        the element that has the documentation comment containing the information
     * @param refTree       the tree node containing the information, or {@code null} if not available
     * @param refSignature  the normalized signature of the target of the reference
     * @param ref           the target of the reference
     * @param isLinkPlain   {@code true} if the link should be presented in "plain" font,
     *                      or {@code false} for "code" font
     * @param label         the label for the link,
     *                      or an empty item to use a default label derived from the signature
     * @param reportWarning a function to report warnings about issues found in the reference
     *
     * @return the output containing the generated link, or content indicating an error
     */
    Content linkSeeReferenceOutput(Element holder,
                                   DocTree refTree,
                                   String refSignature,
                                   Element ref,
                                   boolean isLinkPlain,
                                   Content label,
                                   BiConsumer<String, Object[]> reportWarning) {
        Content labelContent = plainOrCode(isLinkPlain, label);

        // The signature from the @see tag. We will output this text when a label is not specified.
        Content text = plainOrCode(isLinkPlain,
                Text.of(Objects.requireNonNullElse(refSignature, "")));

        CommentHelper ch = utils.getCommentHelper(holder);
        TypeElement refClass = ch.getReferencedClass(ref);
        Element refMem =       ch.getReferencedMember(ref);
        String refFragment =   ch.getReferencedFragment(refSignature);

        if (refFragment == null && refMem != null) {
            refFragment = refMem.toString();
        } else if (refFragment != null && refFragment.startsWith("#")) {
            if (labelContent.isEmpty()) {
                // A non-empty label is required for fragment links as the
                // reference target does not provide a useful default label.
                htmlWriter.messages.error(ch.getDocTreePath(refTree), "doclet.link.see.no_label");
                return invalidTagOutput(resources.getText("doclet.link.see.no_label"),
                        Optional.of(refSignature));
            }
            refFragment = refFragment.substring(1);
        }
        if (refClass == null) {
            ModuleElement refModule = ch.getReferencedModule(ref);
            if (refModule != null && utils.isIncluded(refModule)) {
                return htmlWriter.getModuleLink(refModule, labelContent.isEmpty() ? text : labelContent, refFragment);
            }
            //@see is not referencing an included class
            PackageElement refPackage = ch.getReferencedPackage(ref);
            if (refPackage != null && utils.isIncluded(refPackage)) {
                //@see is referencing an included package
                if (labelContent.isEmpty()) {
                    labelContent = plainOrCode(isLinkPlain,
                            Text.of(refPackage.getQualifiedName()));
                }
                return htmlWriter.getPackageLink(refPackage, labelContent, refFragment);
            } else {
                // @see is not referencing an included class, module or package. Check for cross-links.
                String refModuleName =  ch.getReferencedModuleName(refSignature);
                DocLink elementCrossLink = (refPackage != null) ? htmlWriter.getCrossPackageLink(refPackage) :
                        (configuration.extern.isModule(refModuleName))
                                ? htmlWriter.getCrossModuleLink(utils.elementUtils.getModuleElement(refModuleName))
                                : null;
                if (elementCrossLink != null) {
                    // Element cross-link found
                    return htmlWriter.links.createExternalLink(elementCrossLink,
                            (labelContent.isEmpty() ? text : labelContent));
                } else {
                    // No cross-link found so print warning
                    if (!configuration.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept(
                                "doclet.link.see.reference_not_found",
                                new Object[] { refSignature});
                    }
                    return htmlWriter.invalidTagOutput(resources.getText("doclet.link.see.reference_invalid"),
                            Optional.of(labelContent.isEmpty() ? text: labelContent));
                }
            }
        } else if (refFragment == null) {
            // Must be a class reference since refClass is not null and refFragment is null.
            if (labelContent.isEmpty() && refTree != null) {
                TypeMirror referencedType = ch.getReferencedType(refTree);
                if (utils.isGenericType(referencedType)) {
                    // This is a generic type link, use the TypeMirror representation.
                    return plainOrCode(isLinkPlain, htmlWriter.getLink(
                            new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, referencedType)));
                }
                labelContent = plainOrCode(isLinkPlain, Text.of(utils.getSimpleName(refClass)));
            }
            return htmlWriter.getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.PLAIN, refClass)
                    .label(labelContent));
        } else if (refMem == null) {
            // This is a fragment reference since refClass and refFragment are not null but refMem is null.
            return htmlWriter.getLink(new HtmlLinkInfo(configuration, HtmlLinkInfo.Kind.PLAIN, refClass)
                    .label(labelContent)
                    .fragment(refFragment)
                    .style(null));
        } else {
            // Must be a member reference since refClass is not null and refMemName is not null.
            // refMem is not null, so this @see tag must be referencing a valid member.
            TypeElement containing = utils.getEnclosingTypeElement(refMem);

            // Find the enclosing type where the method is actually visible
            // in the inheritance hierarchy.
            ExecutableElement overriddenMethod = null;
            if (refMem.getKind() == ElementKind.METHOD) {
                VisibleMemberTable vmt = configuration.getVisibleMemberTable(containing);
                overriddenMethod = vmt.getOverriddenMethod((ExecutableElement)refMem);

                if (overriddenMethod != null) {
                    containing = utils.getEnclosingTypeElement(overriddenMethod);
                }
            }
            if (refSignature.trim().startsWith("#") &&
                    ! (utils.isPublic(containing) || utils.isLinkable(containing))) {
                // Since the link is relative and the holder is not even being
                // documented, this must be an inherited link.  Redirect it.
                // The current class either overrides the referenced member or
                // inherits it automatically.
                if (htmlWriter instanceof ClassWriterImpl writer) {
                    containing = writer.getTypeElement();
                } else if (!utils.isPublic(containing)) {
                    reportWarning.accept("doclet.link.see.reference_not_accessible",
                            new Object[] { utils.getFullyQualifiedName(containing)});
                } else {
                    if (!configuration.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept("doclet.link.see.reference_not_found",
                                new Object[] { refSignature });
                    }
                }
            }
            String refMemName = refFragment;
            if (configuration.currentTypeElement != containing) {
                refMemName = (utils.isConstructor(refMem))
                        ? refMemName
                        : utils.getSimpleName(containing) + "." + refMemName;
            }
            if (utils.isExecutableElement(refMem)) {
                if (refMemName.indexOf('(') < 0) {
                    refMemName += utils.makeSignature((ExecutableElement) refMem, null, true);
                }
                if (overriddenMethod != null) {
                    // The method to actually link.
                    refMem = overriddenMethod;
                }
            }

            return htmlWriter.getDocLink(HtmlLinkInfo.Kind.SHOW_PREVIEW, containing,
                    refMem, (labelContent.isEmpty()
                            ? plainOrCode(isLinkPlain, Text.of(refMemName))
                            : labelContent), null, false);
        }
    }

    private Content plainOrCode(boolean plain, Content body) {
        return (plain || body.isEmpty()) ? body : HtmlTree.CODE(body);
    }

    @Override
    protected Content invalidTagOutput(String summary, Optional<String> detail) {
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
