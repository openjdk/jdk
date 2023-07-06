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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SeeTree;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.BaseConfiguration;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Result;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that represents the {@code @see} tag.
 */
public abstract class SeeTaglet extends BaseTaglet implements InheritableTaglet {

    protected SeeTaglet(BaseConfiguration config) {
        super(config, DocTree.Kind.SEE, false, EnumSet.allOf(Location.class));
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
        List<? extends SeeTree> tags = utils.getSeeTrees(holder);
        Element e = holder;
        if (utils.isMethod(holder)) {
            var docFinder = utils.docFinder();
            Optional<Documentation> result = docFinder.search((ExecutableElement) holder,
                    m -> Result.fromOptional(extract(utils, m))).toOptional();
            if (result.isPresent()) {
                ExecutableElement m = result.get().method();
                tags = utils.getSeeTrees(m);
                e = m;
            }
        }
        return seeTagOutput(e, tags);
    }

    /**
     * Returns the output for {@code @see} tags.
     *
     * @param element The element that owns the doc comment
     * @param seeTags the list of tags
     *
     * @return the output
     */
    protected abstract Content seeTagOutput(Element element, List<? extends SeeTree> seeTags);

    private record Documentation(List<? extends SeeTree> seeTrees, ExecutableElement method) { }

    private static Optional<Documentation> extract(Utils utils, ExecutableElement method) {
        List<? extends SeeTree> tags = utils.getSeeTrees(method);
        return tags.isEmpty() ? Optional.empty() : Optional.of(new Documentation(tags, method));
    }
}
