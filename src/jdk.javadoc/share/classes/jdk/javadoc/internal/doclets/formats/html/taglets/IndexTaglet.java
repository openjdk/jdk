/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.IndexTree;
import com.sun.source.doctree.TextTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.html.Content;

/**
 * An inline taglet used to index a word or a phrase.
 * The enclosed text is interpreted as not containing HTML markup or
 * nested javadoc tags.
 */
public class IndexTaglet extends BaseTaglet {

    IndexTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.INDEX, true, EnumSet.allOf(Taglet.Location.class));
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        var context = tagletWriter.context;
        var indexTree = (IndexTree) tag;

        DocTree searchTerm = indexTree.getSearchTerm();
        String tagText = (searchTerm instanceof TextTree tt) ? tt.getBody() : "";
        if (tagText.charAt(0) == '"' && tagText.charAt(tagText.length() - 1) == '"') {
            tagText = tagText.substring(1, tagText.length() - 1);
        }
        tagText = utils.normalizeWhitespace(tagText);

        Content desc = tagletWriter.htmlWriter.commentTagsToContent(element, indexTree.getDescription(), context.within(indexTree));
        String descText = utils.normalizeWhitespace(extractText(desc));

        return tagletWriter.createAnchorAndSearchIndex(element, tagText, descText, tag);
    }

    // ugly but simple;
    // alternatives would be to walk the Content's tree structure, or to add new functionality to Content
    private String extractText(Content c) {
        return c.toString().replaceAll("<[^>]+>", "");
    }
}
