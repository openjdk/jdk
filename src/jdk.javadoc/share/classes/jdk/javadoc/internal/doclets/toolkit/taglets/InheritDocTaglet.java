/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * An inline taglet representing the {@code {@inheritDoc}} tag.
 * It is used to copy documentation from superclass (but not superinterface)
 * declarations and from overridden and implemented methods.
 */
public class InheritDocTaglet extends BaseTaglet {

    /**
     * Construct a new InheritDocTaglet.
     */
    public InheritDocTaglet() {
        super(DocTree.Kind.INHERIT_DOC, true, EnumSet.of(Location.TYPE, Location.METHOD));
    }

    /**
     * Given an element, a {@code DocTree} in the element's doc comment
     * replace all occurrences of {@code {@inheritDoc}} with documentation from its
     * superclass or superinterface.
     *
     * @param writer the writer that is writing the output.
     * @param e the {@link Element} that we are documenting.
     *
     * @param holderTag
     *
     * either the tag that holds the {@code {@inheritDoc}} tag or {@code null},
     * which can mean either of:
     * <ul>
     *     <li>the tag is used on a class {@link jdk.javadoc.doclet.Taglet.Location#TYPE} declaration, or
     *     <li>the tag is used to copy the overall doc comment
     * </ul>
     *
     * @param isFirstSentence true if we only want to inherit the first sentence
     */
    private Content retrieveInheritedDocumentation(TagletWriter writer,
                                                   Element e,
                                                   DocTree holderTag,
                                                   boolean isFirstSentence) {
        Content replacement = writer.getOutputInstance();
        BaseConfiguration configuration = writer.configuration();
        Messages messages = configuration.getMessages();
        Utils utils = configuration.utils;
        CommentHelper ch = utils.getCommentHelper(e);
        Taglet taglet = holderTag == null
                ? null
                : configuration.tagletManager.getTaglet(ch.getTagName(holderTag));
        if (taglet != null && !(taglet instanceof InheritableTaglet)) {
            // This tag does not support inheritance.
            var path = writer.configuration().utils.getCommentHelper(e).getDocTreePath(holderTag);
            messages.warning(path, "doclet.inheritDocWithinInappropriateTag");
            return replacement;
        }
        var input = new DocFinder.Input(utils, e, (InheritableTaglet) taglet,
                new DocFinder.DocTreeInfo(holderTag, e), isFirstSentence, true);
        DocFinder.Output inheritedDoc = DocFinder.search(configuration, input);
        if (inheritedDoc.isValidInheritDocTag) {
            if (!inheritedDoc.inlineTags.isEmpty()) {
                replacement = writer.commentTagsToOutput(inheritedDoc.holder, inheritedDoc.holderTag,
                        inheritedDoc.inlineTags, isFirstSentence);
            }
        } else {
            // This is to assert that we don't reach here for a class declaration.
            // Indeed, every class except for java.lang.Object has a superclass.
            // If we ever reach here, we would need a different warning; because
            // the below warning is about method declarations, not class declarations.
            // Unless @inheritDoc is used inside java.lang.Object itself,
            // which would clearly be an error, we shouldn't reach here.
            assert !(e instanceof TypeElement typeElement)
                    || typeElement.getSuperclass().getKind() == TypeKind.NONE;
            String signature = utils.getSimpleName(e) +
                    ((utils.isExecutableElement(e))
                            ? utils.flatSignature((ExecutableElement) e, writer.getCurrentPageElement())
                            : e.toString());
            messages.warning(e, "doclet.noInheritedDoc", signature);
        }
        return replacement;
    }

    @Override
    public Content getInlineTagOutput(Element e, DocTree tag, TagletWriter tagletWriter) {
        DocTree inheritTag = (tag.getKind() == DocTree.Kind.INHERIT_DOC) ? null : tag;
        return retrieveInheritedDocumentation(tagletWriter, e,
                inheritTag, tagletWriter.isFirstSentence);
    }
}
