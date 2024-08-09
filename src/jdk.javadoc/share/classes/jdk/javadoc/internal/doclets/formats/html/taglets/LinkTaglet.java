/*
 * Copyright (c) 2003, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.util.DocTreePath;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.ClassWriter;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlLinkInfo;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.VisibleMemberTable;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.Text;

import static com.sun.source.doctree.DocTree.Kind.LINK;
import static com.sun.source.doctree.DocTree.Kind.LINK_PLAIN;
import static com.sun.source.doctree.DocTree.Kind.SEE;

/**
 * A taglet that represents the {@code {@link ...}} and {@linkplain ...} tags,
 * with support for links to program elements in {@code @see} and
 * {@code {@snippet ...}} tags.
 */
public class LinkTaglet extends BaseTaglet {
    LinkTaglet(HtmlConfiguration config, DocTree.Kind tagKind) {
        super(config, tagKind, true, EnumSet.allOf(Taglet.Location.class));
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        var linkTree = (LinkTree) tag;
        var ch = utils.getCommentHelper(element);
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;

        var inTags = context.inTags;
        if (inTags.contains(LINK) || inTags.contains(LINK_PLAIN) || inTags.contains(SEE)) {
            DocTreePath dtp = ch.getDocTreePath(linkTree);
            if (dtp != null) {
                messages.warning(dtp, "doclet.see.nested_link", "{@" + linkTree.getTagName() + "}");
            }
            Content label = htmlWriter.commentTagsToContent(element, linkTree.getLabel(), context.within(linkTree));
            if (label.isEmpty()) {
                label = Text.of(linkTree.getReference().getSignature());
            }
            return label;
        }

        var linkRef = linkTree.getReference();
        if (linkRef == null) {
            messages.warning(ch.getDocTreePath(tag), "doclet.link.no_reference");
            return tagletWriter.invalidTagOutput(resources.getText("doclet.tag.invalid_input", tag.toString()),
                    Optional.empty());
        }

        DocTree.Kind kind = tag.getKind();
        String refSignature = ch.getReferencedSignature(linkRef);

        return linkSeeReferenceOutput(element,
                tag,
                refSignature,
                ch.getReferencedElement(tag),
                (kind == LINK_PLAIN),
                htmlWriter.commentTagsToContent(element, linkTree.getLabel(), context.within(linkTree)),
                (key, args) -> messages.warning(ch.getDocTreePath(tag), key, args),
                tagletWriter);
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
     * @param isPlain       {@code true} if the link should be presented in "plain" font,
     *                      or {@code false} for "code" font
     * @param label         the label for the link,
     *                      or an empty item to use a default label derived from the signature
     * @param reportWarning a function to report warnings about issues found in the reference
     * @param tagletWriter  the writer providing the context for this call
     *
     * @return the output containing the generated link, or content indicating an error
     */
    Content linkSeeReferenceOutput(Element holder,
                                   DocTree refTree,
                                   String refSignature,
                                   Element ref,
                                   boolean isPlain,
                                   Content label,
                                   BiConsumer<String, Object[]> reportWarning,
                                   TagletWriter tagletWriter) {
        var config = tagletWriter.configuration;
        var htmlWriter = tagletWriter.htmlWriter;

        Content labelContent = plainOrCode(isPlain, label);

        // The signature from the @see tag. We will output this text when a label is not specified.
        Content text = plainOrCode(isPlain,
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
                return tagletWriter.invalidTagOutput(resources.getText("doclet.link.see.no_label"),
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
                    labelContent = plainOrCode(isPlain,
                            Text.of(refPackage.getQualifiedName()));
                }
                return htmlWriter.getPackageLink(refPackage, labelContent, refFragment);
            } else {
                // @see is not referencing an included class, module or package. Check for cross-links.
                String refModuleName = ch.getReferencedModuleName(refSignature);
                DocLink elementCrossLink = (refPackage != null) ? htmlWriter.getCrossPackageLink(refPackage) :
                        (config.extern.isModule(refModuleName))
                                ? htmlWriter.getCrossModuleLink(utils.elementUtils.getModuleElement(refModuleName))
                                : null;
                if (elementCrossLink != null) {
                    // Element cross-link found
                    return htmlWriter.links.createExternalLink(elementCrossLink,
                            (labelContent.isEmpty() ? text : labelContent));
                } else {
                    // No cross-link found so print warning
                    if (!config.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept(
                                "doclet.link.see.reference_not_found",
                                new Object[] {refSignature});
                    }
                    return htmlWriter.invalidTagOutput(resources.getText("doclet.link.see.reference_invalid"),
                            Optional.of(labelContent.isEmpty() ? text : labelContent));
                }
            }
        } else if (utils.isTypeParameterElement(ref)) {
            // This is a type parameter of a generic class, method or constructor
            if (labelContent.isEmpty()) {
                labelContent = plainOrCode(isPlain, Text.of(utils.getSimpleName(ref)));
            }
            if (refMem == null) {
                return htmlWriter.getLink(
                        new HtmlLinkInfo(config, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS, ref.asType())
                                .label(labelContent));
            } else {
                // HtmlLinkFactory does not render type parameters of generic methods as links, so instead of
                // teaching it how to do it (making the code even more complex) just create the link directly.
                return htmlWriter.getLink(new HtmlLinkInfo(config, HtmlLinkInfo.Kind.PLAIN, refClass)
                        .fragment(config.htmlIds.forTypeParam(ref.getSimpleName().toString(), refMem).name())
                        .label((labelContent)));
            }
        } else if (refFragment == null) {
            // Must be a class reference since refClass is not null and refFragment is null.
            if (labelContent.isEmpty() && refTree != null) {
                TypeMirror referencedType = ch.getReferencedType(refTree);
                if (utils.isGenericType(referencedType)) {
                    // This is a generic type link, use the TypeMirror representation.
                    return plainOrCode(isPlain, htmlWriter.getLink(
                            new HtmlLinkInfo(config, HtmlLinkInfo.Kind.LINK_TYPE_PARAMS_AND_BOUNDS, referencedType)));
                }
                labelContent = plainOrCode(isPlain, Text.of(utils.getSimpleName(refClass)));
            }
            return htmlWriter.getLink(new HtmlLinkInfo(config, HtmlLinkInfo.Kind.PLAIN, refClass)
                    .label(labelContent));
        } else if (refMem == null) {
            // This is a fragment reference since refClass and refFragment are not null but refMem is null.
            return htmlWriter.getLink(new HtmlLinkInfo(config, HtmlLinkInfo.Kind.PLAIN, refClass)
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
                VisibleMemberTable vmt = config.getVisibleMemberTable(containing);
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
                if (htmlWriter instanceof ClassWriter cw) {
                    containing = cw.getTypeElement();
                } else if (!utils.isPublic(containing)) {
                    reportWarning.accept("doclet.link.see.reference_not_accessible",
                            new Object[] { utils.getFullyQualifiedName(containing)});
                } else {
                    if (!config.isDocLintReferenceGroupEnabled()) {
                        reportWarning.accept("doclet.link.see.reference_not_found",
                                new Object[] { refSignature });
                    }
                }
            }
            String refMemName = refFragment;
            if (config.currentTypeElement != containing) {
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
                            ? plainOrCode(isPlain, Text.of(refMemName))
                            : labelContent), null, false);
        }
    }

    private Content plainOrCode(boolean plain, Content body) {
        return (plain || body.isEmpty()) ? body : HtmlTree.CODE(body);
    }
}
