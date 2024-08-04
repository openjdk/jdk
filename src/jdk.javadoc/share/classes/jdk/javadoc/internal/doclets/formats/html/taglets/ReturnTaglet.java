/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;
import java.util.stream.Stream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ReturnTree;

import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.formats.html.Contents;
import jdk.javadoc.internal.doclets.formats.html.HtmlConfiguration;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;
import jdk.javadoc.internal.html.Content;
import jdk.javadoc.internal.html.ContentBuilder;
import jdk.javadoc.internal.html.HtmlTree;

/**
 * A taglet that represents the {@code @return} and {@code {@return }} tags.
 */
public class ReturnTaglet extends BaseTaglet implements InheritableTaglet {
    private final Contents contents;

    ReturnTaglet(HtmlConfiguration config) {
        super(config, DocTree.Kind.RETURN, true, EnumSet.of(Taglet.Location.METHOD));
        contents = config.contents;
    }

    @Override
    public boolean isBlockTag() {
        return true;
    }

    @Override
    public Output inherit(Element dst, Element src, DocTree tag, boolean isFirstSentence) {
        try {
            var docFinder = utils.docFinder();
            Optional<Documentation> r;
            if (src == null) {
                r = docFinder.find((ExecutableElement) dst, m -> DocFinder.Result.fromOptional(extract(utils, m))).toOptional();
            } else {
                r = docFinder.search((ExecutableElement) src, m -> DocFinder.Result.fromOptional(extract(utils, m))).toOptional();
            }
            return r.map(result -> new Output(result.returnTree, result.method, result.returnTree.getDescription(), true))
                    .orElseGet(() -> new Output(null, null, List.of(), true));
        } catch (DocFinder.NoOverriddenMethodFound e) {
            return new Output(null, null, List.of(), false);
        }
    }

    @Override
    public Content getInlineTagOutput(Element element, DocTree tag, TagletWriter tagletWriter) {
        this.tagletWriter = tagletWriter;
        return returnTagOutput(element, (ReturnTree) tag, true);
    }

    @Override
    public Content getAllBlockTagOutput(Element holder, TagletWriter tagletWriter) {
        assert holder.getKind() == ElementKind.METHOD : holder.getKind();
        var method = (ExecutableElement) holder;
        this.tagletWriter = tagletWriter;
        List<? extends ReturnTree> tags = utils.getReturnTrees(holder);

        // make sure we are not using @return on a method with the void return type
        TypeMirror returnType = utils.getReturnType(tagletWriter.getCurrentPageElement(), method);
        if (returnType != null && utils.isVoid(returnType)) {
            if (!tags.isEmpty() && !config.isDocLintReferenceGroupEnabled()) {
                messages.warning(holder, "doclet.Return_tag_on_void_method");
            }
            return null;
        }

        // it would also be good to check if there are more than one @return
        // tags and produce a warning or error similarly to how it's done
        // above for a case where @return is used for void

        var docFinder = utils.docFinder();
        return docFinder.search(method, m -> DocFinder.Result.fromOptional(extract(utils, m))).toOptional()
                .map(r -> returnTagOutput(r.method, r.returnTree, false))
                .orElse(null);
    }

    /**
     * Returns the output for a {@code @return} tag.
     *
     * @param element   the element that owns the doc comment
     * @param returnTag the return tag to document
     * @param inline    whether this should be written as an inline instance or block instance
     *
     * @return the output
     */
    public Content returnTagOutput(Element element, ReturnTree returnTag, boolean inline) {
        var context = tagletWriter.context;
        var htmlWriter = tagletWriter.htmlWriter;
        var ch = utils.getCommentHelper(element);
        List<? extends DocTree> desc = ch.getDescription(returnTag);
        Content content = htmlWriter.commentTagsToContent(element, desc, context.within(returnTag));
        return inline
                ? new ContentBuilder(contents.getContent("doclet.Returns_0", content))
                : new ContentBuilder(HtmlTree.DT(contents.returns), HtmlTree.DD(content));
    }

    private record Documentation(ReturnTree returnTree, ExecutableElement method) { }

    private static Optional<Documentation> extract(Utils utils, ExecutableElement method) {
        // TODO
        //  Using getBlockTags(..., Kind.RETURN) for clarity. Since @return has become a bimodal tag,
        //  Utils.getReturnTrees is now a misnomer: it returns only block returns, not all returns.
        //  We could revisit this later.
        Stream<? extends ReturnTree> blockTags = utils.getBlockTags(method, DocTree.Kind.RETURN, ReturnTree.class).stream();
        Stream<? extends ReturnTree> mainDescriptionTags = utils.getFirstSentenceTrees(method).stream()
                .mapMulti((t, c) -> {
                    if (t.getKind() == DocTree.Kind.RETURN) c.accept((ReturnTree) t);
                });
        // this method should not check validity of @return tags, hence findAny and not findFirst or what have you
        return Stream.concat(blockTags, mainDescriptionTags)
                .map(t -> new Documentation(t, method)).findAny();
    }
}
