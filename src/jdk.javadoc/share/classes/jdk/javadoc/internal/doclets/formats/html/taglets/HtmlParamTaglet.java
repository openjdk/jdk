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

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ParamTree;

import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.HtmlIds;
import jdk.javadoc.internal.doclets.formats.html.markup.ContentBuilder;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlTree;
import jdk.javadoc.internal.doclets.formats.html.markup.Text;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.ParamTaglet;
import jdk.javadoc.internal.doclets.toolkit.taglets.TagletWriter;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;

public class HtmlParamTaglet extends ParamTaglet {
    private final Contents contents;

    HtmlParamTaglet(HtmlConfiguration config) {
        super(config);
        contents = config.contents;
    }

    @Override
    public Content getParamHeader(ParamTaglet.ParamKind kind) {
        var header = switch (kind) {
            case PARAMETER -> contents.parameters;
            case TYPE_PARAMETER -> contents.typeParameters;
            case RECORD_COMPONENT -> contents.recordComponents;
        };
        return HtmlTree.DT(header);
    }

    @Override
    public Content paramTagOutput(Element element, ParamTree paramTag, String paramName, TagletWriter writer) {
        var body = new ContentBuilder();
        var w = (TagletWriterImpl) writer;
        CommentHelper ch = w.getUtils().getCommentHelper(element);
        // define id attributes for state components so that generated descriptions may refer to them
        boolean defineID = (element.getKind() == ElementKind.RECORD)
                && !paramTag.isTypeParameter();
        Content nameContent = Text.of(paramName);
        body.add(HtmlTree.CODE(defineID ? HtmlTree.SPAN_ID(HtmlIds.forParam(paramName), nameContent) : nameContent));
        body.add(" - ");
        List<? extends DocTree> description = ch.getDescription(paramTag);
        body.add(w.getHtmlWriter().commentTagsToContent(element, description, w.getContext().within(paramTag)));
        return HtmlTree.DD(body);
    }
}
