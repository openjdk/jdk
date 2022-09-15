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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that represents the {@code {@inheritDoc}} tag.
 */
public class InheritDocTaglet extends BaseTaglet {

    /**
     * Construct a new InheritDocTaglet.
     */
    public InheritDocTaglet() {
        super(DocTree.Kind.INHERIT_DOC, true, EnumSet.of(Location.METHOD));
    }

    /**
     * Given an element and {@code @inheritDoc} tag in that element's doc comment,
     * returns the (recursive) expansion of that tag.
     *
     * <p>This method does not expand all {@code {@inheritDoc}} tags in the given
     * element's doc comment. To do this, the method must be called for every
     * such tag.</p>
     *
     * @param writer the writer that is writing the output.
     * @param e the {@link Element} that we are documenting.
     * @param inheritDoc the {@code {@inheritDoc}} tag
     * @param isFirstSentence true if we only want to inherit the first sentence
     */
    private Content retrieveInheritedDocumentation(TagletWriter writer,
                                                   Element e,
                                                   DocTree inheritDoc,
                                                   boolean isFirstSentence) {
        Content replacement = writer.getOutputInstance();
        BaseConfiguration configuration = writer.configuration();
        Messages messages = configuration.getMessages();
        Utils utils = configuration.utils;
        CommentHelper ch = utils.getCommentHelper(e);
        var path = ch.getDocTreePath(inheritDoc).getParentPath();
        DocTree holderTag = path.getLeaf();
        Taglet taglet = holderTag.getKind() == DocTree.Kind.DOC_COMMENT
                ? null
                : configuration.tagletManager.getTaglet(ch.getTagName(holderTag));
        if (taglet != null && !(taglet instanceof InheritableTaglet)) {
            // This tag does not support inheritance.
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
            String signature = utils.getSimpleName(e) +
                    ((utils.isExecutableElement(e))
                            ? utils.flatSignature((ExecutableElement) e, writer.getCurrentPageElement())
                            : e.toString());
            messages.warning(e, "doclet.noInheritedDoc", signature);
        }
        return replacement;
    }

    @Override
    public Content getInlineTagOutput(Element e, DocTree inheritDoc, TagletWriter tagletWriter) {
        if (e.getKind() != ElementKind.METHOD) {
            return tagletWriter.getOutputInstance();
        }
        return retrieveInheritedDocumentation(tagletWriter, e, inheritDoc, tagletWriter.isFirstSentence);
    }
}
