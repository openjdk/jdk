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

import javax.lang.model.element.Element;

import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyles;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;

/**
 * A taglet that represents the {@code @deprecated} tag.
 */
public class DeprecatedTaglet extends BaseTaglet {

    DeprecatedTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.DEPRECATED, false,
                EnumSet.of(Taglet.Location.MODULE, Taglet.Location.TYPE, Taglet.Location.CONSTRUCTOR, Taglet.Location.METHOD, Taglet.Location.FIELD));

    }

    @Override
    public Content getAllBlockTagOutput(Element element, TagletWriter tagletWriter) {
        var htmlWriter = tagletWriter.htmlWriter;

        ContentBuilder result = new ContentBuilder();
        CommentHelper ch = utils.getCommentHelper(element);
        List<? extends DeprecatedTree> deprs = utils.getDeprecatedTrees(element);
        if (utils.isTypeElement(element)) {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyles.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> commentTrees = ch.getDescription(deprs.get(0));
                    if (!commentTrees.isEmpty()) {
                        result.add(tagletWriter.commentTagsToOutput(element, null, commentTrees, false));
                    }
                }
            }
        } else {
            if (utils.isDeprecated(element)) {
                result.add(HtmlTree.SPAN(HtmlStyles.deprecatedLabel,
                        htmlWriter.getDeprecatedPhrase(element)));
                if (!deprs.isEmpty()) {
                    List<? extends DocTree> bodyTrees = ch.getBody(deprs.get(0));
                    Content body = tagletWriter.commentTagsToOutput(element, null, bodyTrees, false);
                    if (!body.isEmpty())
                        result.add(HtmlTree.DIV(HtmlStyles.deprecationComment, body));
                }
            } else {
                Element ee = utils.getEnclosingTypeElement(element);
                if (utils.isDeprecated(ee)) {
                    result.add(HtmlTree.SPAN(HtmlStyles.deprecatedLabel,
                            htmlWriter.getDeprecatedPhrase(ee)));
                }
            }
        }
        return result;

    }

}
