/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Input;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

import static com.sun.source.doctree.DocTree.Kind.*;

/**
 * A taglet that represents the @see tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *
 * @author Jamie Ho
 */
public class SeeTaglet extends BaseTaglet implements InheritableTaglet {

    public SeeTaglet() {
        name = SEE.tagName;
    }

    /**
     * {@inheritDoc}
     */
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        List<? extends DocTree> tags = input.utils.getSeeTrees(input.element);
        if (!tags.isEmpty()) {
            CommentHelper ch =  input.utils.getCommentHelper(input.element);
            output.holder = input.element;
            output.holderTag = tags.get(0);
            output.inlineTags = input.isFirstSentence
                    ? ch.getFirstSentenceTrees(input.utils.configuration, output.holderTag)
                    : ch.getReference(output.holderTag);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        Utils utils = writer.configuration().utils;
        List<? extends DocTree> tags = utils.getSeeTrees(holder);
        Element e = holder;
        if (tags.isEmpty() && utils.isExecutableElement(holder)) {
            Input input = new DocFinder.Input(utils, holder, this);
            DocFinder.Output inheritedDoc = DocFinder.search(writer.configuration(), input);
            if (inheritedDoc.holder != null) {
                tags = utils.getSeeTrees(inheritedDoc.holder);
                e = inheritedDoc.holder;
            }
        }
        return writer.seeTagOutput(e, tags);
    }
}
