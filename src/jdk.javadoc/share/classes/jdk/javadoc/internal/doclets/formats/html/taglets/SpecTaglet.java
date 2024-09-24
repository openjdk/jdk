/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SpecTree;
import com.sun.source.util.DocTreePath;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.Entity;
import jdk.javadoc.internal.html.HtmlTree;
import jdk.javadoc.internal.html.RawHtml;
import jdk.javadoc.internal.html.Text;
import jdk.javadoc.internal.html.TextBuilder;

/**
 * A taglet that represents the {@code @spec} tag.
 */
public class SpecTaglet extends BaseTaglet implements InheritableTaglet {
    private final Contents contents;

    SpecTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.SPEC, false, EnumSet.allOf(Taglet.Location.class));
        this.contents = config.contents;
    }

    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence) {
        CommentHelper ch = utils.getCommentHelper(dst);
        var path = ch.getDocTreePath(tag);
        messages.warning(path, "doclet.inheritDocWithinInappropriateTag");
        return new Output(null, null, List.of(), true /* true, otherwise there will be an exception up the stack */);
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        List<? extends SpecTree> tags = utils.getSpecTrees(holder);
        Element e = holder;
        if (utils.isMethod(holder)) {
            var docFinder = utils.docFinder();
            Optional<Documentation> result = docFinder.search((ExecutableElement) holder,
                    m -> DocFinder.Result.fromOptional(extract(utils, m))).toOptional();
            if (result.isPresent()) {
                e = result.get().method();
                tags = result.get().specTrees();
            }
        }
        return specTagOutput(e, tags);
    }

    /**
     * Returns the output for one or more {@code @spec} tags.
     *
     * @param holder  the element that owns the doc comment
     * @param specTags the array of @spec tags.
     *
     * @return the output
     */
    public Content specTagOutput(Element holder, List<? extends SpecTree> specTags) {
        if (specTags.isEmpty()) {
            return Text.EMPTY;
        }

        var links = specTags.stream()
                .map(st -> specTagToContent(holder, st)).toList();

        var specList = tagletWriter.tagList(links);
        return new ContentBuilder(
                HtmlTree.DT(contents.externalSpecifications),
                HtmlTree.DD(specList));
    }

    private record Documentation(List<? extends SpecTree> specTrees, ExecutableElement method) { }

    private static Optional<Documentation> extract(Utils utils, ExecutableElement method) {
        List<? extends SpecTree> tags = utils.getSpecTrees(method);
        return tags.isEmpty() ? Optional.empty() : Optional.of(new Documentation(tags, method));
    }

    private Content specTagToContent(Element holder, SpecTree specTree) {
        var htmlWriter = tagletWriter.htmlWriter;
        String specTreeURL = specTree.getURL().getBody();
        List<? extends DocTree> specTreeLabel = specTree.getTitle();
        Content label = htmlWriter.commentTagsToContent(holder, specTreeLabel, tagletWriter.context.isFirstSentence);
        return getExternalSpecContent(holder, specTree, specTreeURL,
                textOf(label).replaceAll("\\s+", " "), label);
    }

    // this is here, for now, but might be a useful addition elsewhere,
    // perhaps as a method on Content
    private String textOf(Content c) {
        return appendText(new StringBuilder(), c).toString();
    }

    private StringBuilder appendText(StringBuilder sb, Content c) {
        if (c instanceof ContentBuilder cb) {
            appendText(sb, cb.getContents());
        } else if (c instanceof HtmlTree ht) {
            appendText(sb, ht.getContents());
        } else if (c instanceof RawHtml rh) {
            sb.append(rh.toString().replaceAll("<[^>]*>", ""));
        } else if (c instanceof TextBuilder tb) {
            sb.append(tb.toString());
        } else if (c instanceof Text t) {
            sb.append(t.toString());
        } else if (c instanceof Entity e) {
            sb.append(e.toString());
        }
        return sb;
    }

    private StringBuilder appendText(StringBuilder sb, List<? extends Content> contents) {
        contents.forEach(c -> appendText(sb, c));
        return sb;
    }

    Content getExternalSpecContent(Element holder,
                                   DocTree docTree,
                                   String url,
                                   String searchText,
                                   Content title) {
        URI specURI;
        try {
            // Use the canonical title of the spec if one is available
            specURI = new URI(url);
        } catch (URISyntaxException e) {
            CommentHelper ch = utils.getCommentHelper(holder);
            DocTreePath dtp = ch.getDocTreePath(docTree);
            tagletWriter.htmlWriter.messages.error(dtp, "doclet.Invalid_URL", e.getMessage());
            specURI = null;
        }

        Content titleWithAnchor = tagletWriter.createAnchorAndSearchIndex(holder,
                searchText,
                title,
                resources.getText("doclet.External_Specification"),
                docTree);

        if (specURI == null) {
            return titleWithAnchor;
        } else {
            var htmlWriter = tagletWriter.htmlWriter;
            return HtmlTree.A(htmlWriter.resolveExternalSpecURI(specURI), titleWithAnchor);
        }

    }
}
