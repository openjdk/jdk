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

package jdk.javadoc.internal.doclets.formats.html.taglets;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SeeTree;

import jdk.javadoc.internal.doclets.formats.html.ClassWriterImpl;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlDocletWriter;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.builders.SerializedFormBuilder;
import jdk.javadoc.internal.doclets.toolkit.taglets.SeeTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocLink;
import jdk.javadoc.internal.doclets.toolkit.util.DocPath;
import jdk.javadoc.internal.doclets.toolkit.util.DocPaths;

public class HtmlSeeTaglet extends SeeTaglet {
    HtmlSeeTaglet(HtmlConfiguration config) {
        super(config);
        contents = config.contents;
    }

    // Threshold for length of @see tag label for switching from inline to block layout.
    private static final int TAG_LIST_ITEM_MAX_INLINE_LENGTH = 30; // FIXME: dup in TagletWriterImpl

    private final Contents contents;
    private HtmlDocletWriter htmlWriter;

    @Override
    public Content seeTagOutput(Element holder, List<? extends SeeTree> seeTags, TagletWriter writer) {
        var w = (TagletWriterImpl) writer;
        htmlWriter = w.getHtmlWriter();

        List<Content> links = new ArrayList<>();
        for (SeeTree dt : seeTags) {
            TagletWriterImpl t = new TagletWriterImpl(htmlWriter, w.getContext().within(dt));
            links.add(seeTagOutput(holder, dt, t));
        }
        if (utils.isVariableElement(holder) && ((VariableElement)holder).getConstantValue() != null &&
                htmlWriter instanceof ClassWriterImpl classWriter) {
            //Automatically add link to constant values page for constant fields.
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    classWriter.getTypeElement().getQualifiedName() + "." +
                            utils.getSimpleName(holder);
            DocLink link = constantsPath.fragment(whichConstant);
            links.add(htmlWriter.links.createLink(link,
                    contents.getContent("doclet.Constants_Summary")));
        }
        if (utils.isClass(holder) && utils.isSerializable((TypeElement)holder)) {
            //Automatically add link to serialized form page for serializable classes.
            if (SerializedFormBuilder.serialInclude(utils, holder) &&
                    SerializedFormBuilder.serialInclude(utils, utils.containingPackage(holder))) {
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(utils.getFullyQualifiedName(holder));
                links.add(htmlWriter.links.createLink(link,
                        contents.getContent("doclet.Serialized_Form")));
            }
        }
        if (links.isEmpty()) {
            return Text.EMPTY;
        }
        // Use a different style if any link label is longer than 30 chars or contains commas.
        boolean hasLongLabels = links.stream().anyMatch(this::isLongOrHasComma);
        var seeList = HtmlTree.UL(hasLongLabels ? HtmlStyle.tagListLong : HtmlStyle.tagList);
        links.stream()
                .filter(Predicate.not(Content::isEmpty))
                .forEach(item -> seeList.add(HtmlTree.LI(item)));

        return new ContentBuilder(
                HtmlTree.DT(contents.seeAlso),
                HtmlTree.DD(seeList));
    }

    private boolean isLongOrHasComma(Content c) {
        String s = c.toString()
                .replaceAll("<.*?>", "")              // ignore HTML
                .replaceAll("&#?[A-Za-z0-9]+;", " ")  // entities count as a single character
                .replaceAll("\\R", "\n");             // normalize newlines
        return s.length() > TAG_LIST_ITEM_MAX_INLINE_LENGTH || s.contains(",");
    }

    /**
     * {@return the output for a single {@code @see} tag}
     *
     * @param element the element that has the documentation comment containing this tag
     * @param seeTag  the tag
     */
    private Content seeTagOutput(Element element, SeeTree seeTag, TagletWriter writer) {

        List<? extends DocTree> ref = seeTag.getReference();
        assert !ref.isEmpty();
        DocTree ref0 = ref.get(0);
        switch (ref0.getKind()) {
            case TEXT, START_ELEMENT -> {
                // @see "Reference"
                // @see <a href="...">...</a>
                return htmlWriter.commentTagsToContent(element, ref, false, false);
            }

            case REFERENCE -> {
                // @see reference label...
                CommentHelper ch = utils.getCommentHelper(element);
                String refSignature = ch.getReferencedSignature(ref0);
                List<? extends DocTree> label = ref.subList(1, ref.size());

                var lt = (HtmlLinkTaglet) config.tagletManager.getTaglet(DocTree.Kind.LINK);
                return lt.linkSeeReferenceOutput(element,
                        seeTag,
                        refSignature,
                        ch.getReferencedElement(seeTag),
                        false,
                        htmlWriter.commentTagsToContent(element, label, writer.getContext().within(seeTag)),
                        (key, args) -> messages.warning(ch.getDocTreePath(seeTag), key, args),
                        writer
                );
            }

            case ERRONEOUS -> {
                return writer.invalidTagOutput(resources.getText("doclet.tag.invalid_input",
                                ref0.toString()),
                        Optional.empty());
            }

            default -> throw new IllegalStateException(ref0.getKind().toString());
        }

    }

}
