/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.sun.source.doctree.DocTree;
import jdk.javadoc.doclet.Taglet.Location;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.Messages;
import jdk.javadoc.internal.doclets.toolkit.util.CommentHelper;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder;
import jdk.javadoc.internal.doclets.toolkit.util.DocFinder.Input;
import jdk.javadoc.internal.doclets.toolkit.util.Utils;

/**
 * A taglet that represents the @return tag.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ReturnTaglet extends BaseTaglet implements InheritableTaglet {

    public ReturnTaglet() {
        super(DocTree.Kind.RETURN, false, EnumSet.of(Location.METHOD));
    }

    @Override
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        List<? extends DocTree> tags = input.utils.getBlockTags(input.element, DocTree.Kind.RETURN);
        CommentHelper ch = input.utils.getCommentHelper(input.element);
        if (!tags.isEmpty()) {
            output.holder = input.element;
            output.holderTag = tags.get(0);
            output.inlineTags = input.isFirstSentence
                    ? ch.getFirstSentenceTrees(output.holderTag)
                    : ch.getDescription(output.holderTag);
        }
    }

    @Override
    public Content getTagletOutput(Element holder, TagletWriter writer) {
        Messages messages = writer.configuration().getMessages();
        Utils utils = writer.configuration().utils;
        TypeMirror returnType = utils.getReturnType(writer.getCurrentPageElement(), (ExecutableElement)holder);
        List<? extends DocTree> tags = utils.getBlockTags(holder, DocTree.Kind.RETURN);

        //Make sure we are not using @return tag on method with void return type.
        if (returnType != null && utils.isVoid(returnType)) {
            if (!tags.isEmpty()) {
                messages.warning(holder, "doclet.Return_tag_on_void_method");
            }
            return null;
        }
        if (!tags.isEmpty())
            return writer.returnTagOutput(holder, tags.get(0));
        //Inherit @return tag if necessary.
        List<DocTree> ntags = new ArrayList<>();
        Input input = new DocFinder.Input(utils, holder, this);
        DocFinder.Output inheritedDoc = DocFinder.search(writer.configuration(), input);
        if (inheritedDoc.holderTag != null) {
            CommentHelper ch = utils.getCommentHelper(input.element);
            ch.setOverrideElement(inheritedDoc.holder);
            ntags.add(inheritedDoc.holderTag);
        }
        return !ntags.isEmpty() ? writer.returnTagOutput(holder, ntags.get(0)) : null;
    }
}
