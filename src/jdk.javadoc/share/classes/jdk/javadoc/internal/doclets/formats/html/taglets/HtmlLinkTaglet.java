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

import java.util.Optional;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;

import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.LinkTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;

import static com.sun.source.doctree.DocTree.Kind.LINK_PLAIN;

public class HtmlLinkTaglet extends LinkTaglet {
    HtmlLinkTaglet(HtmlConfiguration config, DocTree.Kind tagKind) {
        super(config, tagKind);
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter writer) {
        var linkTree = (LinkTree) tag;
        var w = (TagletWriterImpl) writer;

        CommentHelper ch = utils.getCommentHelper(element);

        var linkRef = linkTree.getReference();
        if (linkRef == null) {
            messages.warning(ch.getDocTreePath(tag), "doclet.link.no_reference");
            return w.invalidTagOutput(resources.getText("doclet.tag.invalid_input", tag.toString()),
                    Optional.empty());
        }

        DocTree.Kind kind = tag.getKind();
        String refSignature = ch.getReferencedSignature(linkRef);

        return w.linkSeeReferenceOutput(element,
                tag,
                refSignature,
                ch.getReferencedElement(tag),
                (kind == LINK_PLAIN),
                w.getHtmlWriter().commentTagsToContent(element, linkTree.getLabel(), w.getContext()),
                (key, args) -> messages.warning(ch.getDocTreePath(tag), key, args)
        );
    }
}
